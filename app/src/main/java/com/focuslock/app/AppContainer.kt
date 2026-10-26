package com.focuslock.app

import android.app.Application
import com.focuslock.app.data.db.FocusLockDatabase
import com.focuslock.app.data.repo.DataStoreSettingsRepository
import com.focuslock.app.data.repo.RoomRulesRepository
import com.focuslock.app.data.repo.RoomSessionRepository
import com.focuslock.app.data.repo.RoomStatsRepository
import com.focuslock.app.domain.RulesRepository
import com.focuslock.app.domain.SessionRepository
import com.focuslock.app.domain.SettingsRepository
import com.focuslock.app.domain.StatsRepository
import com.focuslock.app.domain.TimeSource
import com.focuslock.app.system.AppDiscovery
import com.focuslock.app.system.Maintenance
import com.focuslock.app.system.Notifier
import com.focuslock.app.system.PermissionChecker
import com.focuslock.app.system.RuleStore
import com.focuslock.app.system.Scheduler
import com.focuslock.app.system.SystemTimeSource
import com.focuslock.app.system.UsageStatsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-written dependency container (a deliberate choice instead of Hilt: fewer moving parts in an
 * offline-buildable project, and the graph is small). Everything is lazy so process start is cheap,
 * and everything survives only as long as the process; all durable state is in Room / DataStore.
 */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
                // A failed background job must never take the whole process (and the blocking service) down.
                com.focuslock.app.system.Diagnostics.logCrash(app, "background job", t)
            },
    )

    val time: TimeSource = SystemTimeSource(app)
    val db: FocusLockDatabase by lazy { FocusLockDatabase.build(app) }
    val rules: RulesRepository by lazy { RoomRulesRepository(db) }
    val sessions: SessionRepository by lazy { RoomSessionRepository(db.focusSessionDao(), time) }
    val stats: StatsRepository by lazy { RoomStatsRepository(db) { sessions.clearHistory() } }
    val settings: SettingsRepository by lazy { DataStoreSettingsRepository(app) }

    val discovery by lazy { AppDiscovery(app) }
    val permissions by lazy { PermissionChecker(app) }
    val usageReader by lazy { UsageStatsReader(app) }
    val notifier by lazy { Notifier(app, settings) }
    val scheduler by lazy { Scheduler(app, rules, sessions, time) }
    val ruleStore by lazy { RuleStore(rules, sessions, settings, discovery, time, scope) }
    val maintenance by lazy {
        Maintenance(rules, sessions, settings, notifier, scheduler, permissions, time)
    }
}
