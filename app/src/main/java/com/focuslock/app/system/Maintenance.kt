package com.focuslock.app.system

import com.focuslock.app.domain.PermissionState
import com.focuslock.app.domain.RulesRepository
import com.focuslock.app.domain.ScheduleEvaluator
import com.focuslock.app.domain.ServiceHealth
import com.focuslock.app.domain.SessionRepository
import com.focuslock.app.domain.SettingsRepository
import com.focuslock.app.domain.TimeSource
import com.focuslock.app.domain.current
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Idempotent housekeeping run from: alarm ticks, boot/time-change broadcasts, the periodic worker,
 * and app start. Safe to call at any time and from several places at once.
 */
class Maintenance(
    private val rules: RulesRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val scheduler: Scheduler,
    private val permissions: PermissionChecker,
    private val time: TimeSource,
) {
    private val mutex = Mutex()

    suspend fun run() = mutex.withLock {
        // 1. Close a finished session (survives reboot and clock changes; see SessionClock).
        val result = sessions.reconcile()
        if (result.justCompleted != null) notifier.sessionCompleted()

        // 2. Schedule start/end notifications by diffing against what we last announced.
        val now = ZonedDateTime.now()
        val schedules = rules.getSchedules()
        val activeNow = schedules.filter { ScheduleEvaluator.isActive(it, now) }
        val activeIds = activeNow.map { it.id }.toSet()
        val s = settings.current()
        val previous = s.activeScheduleIds
        if (activeIds != previous) {
            activeNow.filter { it.id !in previous }.forEach { notifier.scheduleStarted(it.name) }
            val byId = schedules.associateBy { it.id }
            previous.filter { it !in activeIds }.forEach { id ->
                byId[id]?.let { sc -> notifier.scheduleEnded(sc.name) }
            }
        }

        // 3. Drop expired emergency-bypass grants and remember what is active.
        val wall = time.wallMillis()
        settings.update { st ->
            st.copy(
                activeScheduleIds = activeIds,
                bypassGrants = st.bypassGrants.filterValues { it > wall },
            )
        }

        // 4. Tell the user once in a while if blocking cannot work.
        warnIfBlockingBroken(permissions.state())

        // 5. Plan the next wake-up.
        scheduler.replan()
    }

    private suspend fun warnIfBlockingBroken(state: PermissionState) {
        val st = settings.current()
        if (!st.onboardingDone || !st.masterEnabled) return
        if (state.accessibility == ServiceHealth.CONNECTED) return
        val hasRules = rules.getBlockedApps().isNotEmpty() ||
            rules.getFeedRules().any { it.enabled } ||
            rules.getSchedules().any { it.enabled } ||
            sessions.observeActive().first() != null
        if (!hasRules) return
        val wall = time.wallMillis()
        if (wall - st.lastFailureNotifiedWall < FAILURE_NOTIFY_INTERVAL_MS) return
        settings.update { it.copy(lastFailureNotifiedWall = wall) }
        notifier.serviceFailure()
    }

    private companion object {
        const val FAILURE_NOTIFY_INTERVAL_MS = 12L * 60 * 60 * 1000
    }
}
