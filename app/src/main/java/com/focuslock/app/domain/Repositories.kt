package com.focuslock.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Persistence-facing interfaces. Implementations (Room / DataStore) live in data.repo. */
interface RulesRepository {
    fun observeBlockedApps(): Flow<List<BlockedApp>>
    fun observeFeedRules(): Flow<List<FeedRule>>
    fun observeSchedules(): Flow<List<Schedule>>
    fun observeGroups(): Flow<List<AppGroup>>

    suspend fun getBlockedApps(): List<BlockedApp>
    suspend fun getFeedRules(): List<FeedRule>
    suspend fun getSchedules(): List<Schedule>
    suspend fun getSchedule(id: Long): Schedule?
    suspend fun getGroups(): List<AppGroup>

    suspend fun setBlocked(packages: Collection<String>, blocked: Boolean)
    suspend fun updateBlockedApp(app: BlockedApp)

    suspend fun upsertFeedRule(rule: FeedRule)
    suspend fun deleteFeedRule(id: String)
    suspend fun seedDefaultFeedRulesIfEmpty()
    suspend fun resetFeedRulesToDefault()

    suspend fun saveSchedule(schedule: Schedule): Long
    suspend fun deleteSchedule(id: Long)

    suspend fun createGroup(name: String, members: Set<String>, presetKey: String? = null): Long
    suspend fun renameGroup(id: Long, name: String)
    suspend fun setGroupMembers(id: Long, members: Set<String>)
    suspend fun deleteGroup(id: Long)

    /** Atomically replaces blocked apps, feed rules, groups and schedules (backup restore). */
    suspend fun replaceAll(
        blocked: List<BlockedApp>,
        feeds: List<FeedRule>,
        groups: List<BackupGroup>,
        schedules: List<BackupSchedule>,
    )
}

interface SessionRepository {
    fun observeActive(): Flow<FocusSession?>
    fun observeSince(sinceWall: Long): Flow<List<FocusSession>>

    /** Starts a session, or returns the one already running. */
    suspend fun start(plannedMillis: Long, strict: Boolean, groupId: Long?): FocusSession

    /** Returns false (and changes nothing) when the session is strict and still running. */
    suspend fun cancel(id: Long): Boolean

    /**
     * Marks an expired ACTIVE session COMPLETED. Returns the still-running session (if any) and
     * the session that transitioned to COMPLETED in THIS call (so exactly one caller notifies).
     */
    suspend fun reconcile(): ReconcileResult

    suspend fun clearHistory()
}

data class ReconcileResult(val active: FocusSession?, val justCompleted: FocusSession?)

interface StatsRepository {
    fun observeEventsSince(sinceWall: Long): Flow<List<BlockEvent>>
    fun observeUsageBetween(fromDay: Long, toDay: Long): Flow<List<DailyUsage>>
    suspend fun recordBlock(event: BlockEvent)
    suspend fun upsertUsage(rows: List<DailyUsage>)
    suspend fun getAllEvents(): List<BlockEvent>
    suspend fun getAllUsage(): List<DailyUsage>
    suspend fun replaceHistory(events: List<BlockEvent>, usage: List<DailyUsage>)
    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Single read-modify-write path; safe against concurrent writers (DataStore serializes). */
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun resetAll()
}

suspend fun SettingsRepository.current(): AppSettings = settings.first()

// ---- Backup model -------------------------------------------------------------------------

data class BackupGroup(val name: String, val members: Set<String>, val presetKey: String? = null)

/** Group references are by NAME so ids can be re-created on restore. */
data class BackupSchedule(val schedule: Schedule, val groupNames: Set<String>)

data class BackupData(
    val exportedAt: Long,
    val blockedApps: List<BlockedApp>,
    val feedRules: List<FeedRule>,
    val groups: List<BackupGroup>,
    val schedules: List<BackupSchedule>,
    val blockEvents: List<BlockEvent>,
    val dailyUsage: List<DailyUsage>,
)
