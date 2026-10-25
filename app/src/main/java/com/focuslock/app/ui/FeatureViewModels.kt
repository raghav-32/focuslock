package com.focuslock.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.AppContainer
import com.focuslock.app.domain.AppGroup
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.BlockEvent
import com.focuslock.app.domain.BlockedApp
import com.focuslock.app.domain.DailyUsage
import com.focuslock.app.domain.FeedRule
import com.focuslock.app.domain.FocusSession
import com.focuslock.app.domain.InstalledApp
import com.focuslock.app.domain.LockPolicy
import com.focuslock.app.domain.Preset
import com.focuslock.app.domain.PresetCatalog
import com.focuslock.app.domain.Schedule
import com.focuslock.app.domain.ScheduleEvaluator
import com.focuslock.app.domain.SessionState
import com.focuslock.app.domain.StatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

data class HomeUi(
    val blockedAppCount: Int = 0,
    val activeFeedRules: Int = 0,
    val blocksToday: Int = 0,
    val focusMillisToday: Long = 0L,
    val usageTodayMs: Long? = null, // null = usage access not granted
    val nextBoundary: ScheduleEvaluator.Boundary? = null,
    val weekly: List<Pair<LocalDate, Int>> = emptyList(),
    val activeScheduleNames: List<String> = emptyList(),
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val usageToday = MutableStateFlow<Long?>(null)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val ui: StateFlow<HomeUi> = combine(
        c.rules.observeBlockedApps(),
        c.rules.observeFeedRules(),
        c.rules.observeSchedules(),
        c.stats.observeEventsSince(StatsCalculator.startOfDayMillis(LocalDate.now().minusDays(6), ZoneId.systemDefault())),
        c.sessions.observeSince(StatsCalculator.startOfDayMillis(LocalDate.now(), ZoneId.systemDefault())),
    ) { blocked, feeds, schedules, events, sessions ->
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val startToday = StatsCalculator.startOfDayMillis(today, zone)
        val shown = events.filter { it.action == com.focuslock.app.domain.BlockAction.SHOWN }
        HomeUi(
            blockedAppCount = blocked.count { it.enabled },
            activeFeedRules = feeds.count { it.enabled },
            blocksToday = shown.count { it.timestamp >= startToday },
            focusMillisToday = sessions.sumOf { StatsCalculator.sessionMillis(it, System.currentTimeMillis()) },
            usageTodayMs = null,
            nextBoundary = ScheduleEvaluator.nextBoundary(schedules, now),
            weekly = StatsCalculator.dailyCounts(shown.map { it.timestamp }, zone, today, 7),
            activeScheduleNames = schedules.filter { ScheduleEvaluator.isActive(it, now) }.map { it.name },
        )
    }.combine(usageToday) { ui, u -> ui.copy(usageTodayMs = u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    init {
        refreshUsage()
    }

    fun refreshUsage() {
        viewModelScope.launch {
            if (!c.permissions.usageAccessGranted()) {
                usageToday.value = null
                return@launch
            }
            val tracked = HashSet<String>()
            c.rules.getBlockedApps().forEach { tracked += it.packageName }
            c.rules.getFeedRules().filter { it.enabled }.forEach { tracked += it.packageName }
            c.rules.getGroups().forEach { tracked += it.members }
            usageToday.value = withContext(Dispatchers.IO) {
                val start = StatsCalculator.startOfDayMillis(LocalDate.now(zone), zone)
                c.usageReader.foregroundMillis(start, System.currentTimeMillis())
                    .filterKeys { it in tracked }.values.sum()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Focus sessions
// ---------------------------------------------------------------------------------------------

class FocusViewModel(private val c: AppContainer) : ViewModel() {
    val active: StateFlow<FocusSession?> = c.sessions.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val groups: StateFlow<List<AppGroup>> = c.rules.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val blockedCount: StateFlow<Int> = c.rules.observeBlockedApps()
        .combine(c.rules.observeFeedRules()) { a, f -> a.count { it.enabled } + f.count { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val message = MutableStateFlow<String?>(null)

    fun timeSource() = c.time

    fun start(minutes: Int, strict: Boolean, groupId: Long?) {
        viewModelScope.launch {
            c.sessions.start(minutes * 60_000L, strict, groupId)
            c.notifier.sessionStarted(minutes)
            c.scheduler.replan()
        }
    }

    fun cancel() {
        val s = active.value ?: return
        viewModelScope.launch {
            val ok = c.sessions.cancel(s.id)
            if (!ok) message.value = "This is a strict session. It cannot be cancelled from the app until it ends."
            c.scheduler.replan()
        }
    }

    /** Called by the UI when the countdown reaches zero while the screen is open. */
    fun reconcile() {
        viewModelScope.launch { runCatching { c.maintenance.run() } }
    }

    fun clearMessage() { message.value = null }
}

// ---------------------------------------------------------------------------------------------
// Schedules
// ---------------------------------------------------------------------------------------------

class SchedulesViewModel(private val c: AppContainer) : ViewModel() {
    val schedules: StateFlow<List<Schedule>> = c.rules.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups: StateFlow<List<AppGroup>> = c.rules.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val feedRules: StateFlow<List<FeedRule>> = c.rules.observeFeedRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val installed = MutableStateFlow<List<InstalledApp>>(emptyList())

    init {
        viewModelScope.launch { installed.value = runCatching { c.discovery.loadLaunchableApps() }.getOrDefault(emptyList()) }
    }

    suspend fun load(id: Long): Schedule? = c.rules.getSchedule(id)

    fun save(schedule: Schedule, onDone: () -> Unit) {
        viewModelScope.launch {
            c.rules.saveSchedule(schedule)
            c.maintenance.run() // re-plans the alarm and refreshes start/end announcements
            onDone()
        }
    }

    fun setEnabled(s: Schedule, enabled: Boolean) {
        viewModelScope.launch {
            c.rules.saveSchedule(s.copy(enabled = enabled))
            c.maintenance.run()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            c.rules.deleteSchedule(id)
            c.maintenance.run()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Groups and presets
// ---------------------------------------------------------------------------------------------

class GroupsViewModel(private val c: AppContainer) : ViewModel() {
    val groups: StateFlow<List<AppGroup>> = c.rules.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val presets: List<Preset> = PresetCatalog.all

    init {
        viewModelScope.launch { installed.value = runCatching { c.discovery.loadLaunchableApps() }.getOrDefault(emptyList()) }
    }

    fun previewPreset(p: Preset): List<InstalledApp> = PresetCatalog.matching(p, installed.value)

    fun createGroup(name: String, members: Set<String>) {
        if (name.isBlank()) return
        viewModelScope.launch { c.rules.createGroup(name, members) }
    }

    /** Called only after the user saw the affected apps and confirmed. */
    fun applyPreset(p: Preset, alsoBlock: Boolean) {
        val apps = previewPreset(p).map { it.packageName }.toSet()
        viewModelScope.launch {
            c.rules.createGroup(p.title, apps, p.key)
            if (alsoBlock) c.rules.setBlocked(apps, true)
        }
    }

    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { c.rules.renameGroup(id, name) }
    }

    fun setMembers(id: Long, members: Set<String>) {
        viewModelScope.launch { c.rules.setGroupMembers(id, members) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { c.rules.deleteGroup(id) }
    }

    fun blockGroup(g: AppGroup) {
        viewModelScope.launch { c.rules.setBlocked(g.members, true) }
    }
}

// ---------------------------------------------------------------------------------------------
// Feed rules
// ---------------------------------------------------------------------------------------------

class FeedsViewModel(private val c: AppContainer) : ViewModel() {
    val rules: StateFlow<List<FeedRule>> = c.rules.observeFeedRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val serviceReady: Boolean get() = c.permissions.accessibilityHealth() == com.focuslock.app.domain.ServiceHealth.CONNECTED

    private var strict = false

    init {
        viewModelScope.launch {
            c.sessions.observeActive().collect { s -> strict = s != null && s.strict && s.remainingMillis(c.time) > 0L }
        }
    }

    val message = MutableStateFlow<String?>(null)

    fun installedVersion(pkg: String): Long = c.discovery.versionCode(pkg)

    fun isInstalled(pkg: String): Boolean = c.discovery.launchIntentFor(pkg) != null

    fun save(rule: FeedRule) {
        val previous = rules.value.firstOrNull { it.id == rule.id }
        val loosening = previous != null && previous.enabled && !rule.enabled
        if (loosening && !LockPolicy.canEditRules(strict)) {
            message.value = "A strict focus session is running. Rules can be changed when it ends."
            return
        }
        viewModelScope.launch { c.rules.upsertFeedRule(rule) }
    }

    fun delete(id: String) {
        if (!LockPolicy.canEditRules(strict)) {
            message.value = "A strict focus session is running. Rules can be changed when it ends."
            return
        }
        viewModelScope.launch { c.rules.deleteFeedRule(id) }
    }

    fun resetDefaults() {
        if (!LockPolicy.canEditRules(strict)) {
            message.value = "A strict focus session is running. Rules can be changed when it ends."
            return
        }
        viewModelScope.launch { c.rules.resetFeedRulesToDefault() }
    }

    fun clearMessage() { message.value = null }
}

// ---------------------------------------------------------------------------------------------
// Statistics
// ---------------------------------------------------------------------------------------------

enum class StatsRange(val days: Int, val label: String) { DAY(1, "Day"), WEEK(7, "Week"), MONTH(30, "Month") }

data class AppUsageRow(val packageName: String, val label: String, val millis: Long)

data class StatsUi(
    val range: StatsRange = StatsRange.WEEK,
    val blocks: Int = 0,
    val previousBlocks: Int = 0,
    val changePercent: Double? = null,
    val bypasses: Int = 0,
    val focusMillis: Long = 0L,
    val previousFocusMillis: Long = 0L,
    val sessionsCompleted: Int = 0,
    val topBlocked: Pair<String, Int>? = null,
    val savedMinutesEstimate: Int = 0,
    val minutesPerBlock: Int = 5,
    val dailyBlocks: List<Pair<LocalDate, Int>> = emptyList(),
    val dailyFocusMinutes: List<Pair<LocalDate, Int>> = emptyList(),
    val hourlyBlocks: List<Int> = emptyList(),
    val usageAvailable: Boolean = false,
    val usageTotalMs: Long = 0L,
    val previousUsageTotalMs: Long = 0L,
    val usageByApp: List<AppUsageRow> = emptyList(),
    val hasAnyData: Boolean = false,
)

class StatsViewModel(private val c: AppContainer) : ViewModel() {
    val range = MutableStateFlow(StatsRange.WEEK)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ui: StateFlow<StatsUi> = range.flatMapLatest { r ->
        val today = LocalDate.now(zone)
        val windowStart = today.minusDays(r.days.toLong() - 1)
        val prevStart = windowStart.minusDays(r.days.toLong())
        val startMs = StatsCalculator.startOfDayMillis(windowStart, zone)
        val prevStartMs = StatsCalculator.startOfDayMillis(prevStart, zone)
        combine(
            c.stats.observeEventsSince(prevStartMs),
            c.sessions.observeSince(prevStartMs),
            c.stats.observeUsageBetween(prevStart.toEpochDay(), today.toEpochDay()),
            c.settings.settings,
        ) { events, sessions, usage, settings ->
            build(r, today, startMs, prevStartMs, events, sessions, usage, settings)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())

    fun setRange(r: StatsRange) { range.value = r }

    private fun build(
        r: StatsRange,
        today: LocalDate,
        startMs: Long,
        prevStartMs: Long,
        events: List<BlockEvent>,
        sessions: List<FocusSession>,
        usage: List<DailyUsage>,
        settings: AppSettings,
    ): StatsUi {
        val shown = events.filter { it.action == com.focuslock.app.domain.BlockAction.SHOWN }
        val cur = shown.filter { it.timestamp >= startMs }
        val prev = shown.filter { it.timestamp in prevStartMs until startMs }
        val now = System.currentTimeMillis()
        val curSessions = sessions.filter { it.startWall >= startMs }
        val prevSessions = sessions.filter { it.startWall in prevStartMs until startMs }
        val focusMs = curSessions.sumOf { StatsCalculator.sessionMillis(it, now) }
        val prevFocusMs = prevSessions.sumOf { StatsCalculator.sessionMillis(it, now) }

        val days = if (r == StatsRange.DAY) 1 else r.days
        val focusByDay = HashMap<LocalDate, Long>()
        for (s in curSessions) {
            val d = java.time.Instant.ofEpochMilli(s.startWall).atZone(zone).toLocalDate()
            focusByDay[d] = (focusByDay[d] ?: 0L) + StatsCalculator.sessionMillis(s, now)
        }
        val startDay = today.toEpochDay() - r.days + 1
        val usageCur = usage.filter { it.epochDay >= startDay }
        val usagePrev = usage.filter { it.epochDay < startDay }
        val byApp = usageCur.groupBy { it.packageName }
            .map { (pkg, rows) -> AppUsageRow(pkg, c.discovery.labelOf(pkg), rows.sumOf { it.foregroundMillis }) }
            .sortedByDescending { it.millis }
            .take(8)

        val top = StatsCalculator.mostFrequent(cur.map { it.ruleLabel.ifBlank { it.packageName } })
        return StatsUi(
            range = r,
            blocks = cur.size,
            previousBlocks = prev.size,
            changePercent = StatsCalculator.percentChange(prev.size.toDouble(), cur.size.toDouble()),
            bypasses = events.count { it.action == com.focuslock.app.domain.BlockAction.BYPASSED && it.timestamp >= startMs },
            focusMillis = focusMs,
            previousFocusMillis = prevFocusMs,
            sessionsCompleted = curSessions.count { it.state == SessionState.COMPLETED },
            topBlocked = top,
            savedMinutesEstimate = StatsCalculator.timeSavedMinutes(cur.size, settings.minutesSavedPerBlock),
            minutesPerBlock = settings.minutesSavedPerBlock,
            dailyBlocks = StatsCalculator.dailyCounts(cur.map { it.timestamp }, zone, today, days),
            dailyFocusMinutes = (days - 1 downTo 0).map { off ->
                val d = today.minusDays(off.toLong())
                d to ((focusByDay[d] ?: 0L) / 60_000L).toInt()
            },
            hourlyBlocks = StatsCalculator.hourlyCounts(cur.map { it.timestamp }, zone, today),
            usageAvailable = c.permissions.usageAccessGranted(),
            usageTotalMs = usageCur.sumOf { it.foregroundMillis },
            previousUsageTotalMs = usagePrev.sumOf { it.foregroundMillis },
            usageByApp = byApp,
            hasAnyData = cur.isNotEmpty() || curSessions.isNotEmpty() || usageCur.isNotEmpty(),
        )
    }
}
