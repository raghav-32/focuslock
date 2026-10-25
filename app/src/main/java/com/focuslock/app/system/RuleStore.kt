package com.focuslock.app.system

import com.focuslock.app.domain.ActiveSession
import com.focuslock.app.domain.AppGroup
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.BlockedApp
import com.focuslock.app.domain.FeedRule
import com.focuslock.app.domain.FocusSession
import com.focuslock.app.domain.RuleSnapshot
import com.focuslock.app.domain.RulesRepository
import com.focuslock.app.domain.Schedule
import com.focuslock.app.domain.SessionRepository
import com.focuslock.app.domain.SettingsRepository
import com.focuslock.app.domain.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private data class RuleParts(
    val blocked: List<BlockedApp>,
    val feeds: List<FeedRule>,
    val schedules: List<Schedule>,
)

private data class StateParts(
    val groups: List<AppGroup>,
    val session: FocusSession?,
    val settings: AppSettings,
)

/**
 * Keeps one in-memory [RuleSnapshot] so the accessibility service decides on every event without
 * touching the database. The snapshot is rebuilt whenever Room or DataStore emit a change.
 */
class RuleStore(
    rules: RulesRepository,
    sessions: SessionRepository,
    settings: SettingsRepository,
    private val discovery: AppDiscovery,
    private val time: TimeSource,
    scope: CoroutineScope,
) {
    private val essentials = MutableStateFlow(discovery.essentials())
    private val focus = MutableStateFlow<FocusSession?>(null)

    val snapshot: StateFlow<RuleSnapshot> = combine(
        combine(
            rules.observeBlockedApps(),
            rules.observeFeedRules(),
            rules.observeSchedules(),
        ) { b, f, s -> RuleParts(b, f, s) },
        combine(
            rules.observeGroups(),
            sessions.observeActive(),
            settings.settings,
        ) { g, a, st -> StateParts(g, a, st) },
        essentials,
    ) { r, s, e ->
        focus.value = s.session
        RuleSnapshot(
            blockedApps = r.blocked.associateBy { it.packageName },
            feedRules = r.feeds,
            schedules = r.schedules,
            groupMembers = s.groups.associate { it.id to it.members },
            session = null, // filled in by current() using the monotonic clock
            masterEnabled = s.settings.masterEnabled,
            neverBlock = e.neverBlock,
            ignoredForForeground = e.ignoredForForeground,
            emergencyAllow = s.settings.emergencyApps,
            bypassUntil = s.settings.bypassGrants,
        )
    }
        .catch { /* a failing flow must not kill enforcement; keep the last snapshot */ }
        .stateIn(scope, SharingStarted.Eagerly, RuleSnapshot())

    /** Snapshot with the focus session's end time derived from the monotonic clock. */
    fun current(): RuleSnapshot {
        val base = snapshot.value
        val session = focus.value ?: return base
        val remaining = session.remainingMillis(time)
        if (remaining <= 0L) return base
        return base.copy(
            session = ActiveSession(
                id = session.id,
                strict = session.strict,
                endsAtWall = time.wallMillis() + remaining,
                groupId = session.groupId,
            ),
        )
    }

    /** Re-read launcher / keyboard / dialer (they can change while we run). */
    fun refreshEssentials() {
        essentials.value = discovery.essentials()
    }
}
