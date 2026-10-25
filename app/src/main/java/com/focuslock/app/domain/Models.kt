package com.focuslock.app.domain

import java.time.DayOfWeek

enum class BlockScope { ALWAYS, SESSIONS_ONLY }
enum class BlockSource { FOCUS_SESSION, SCHEDULE, ALWAYS_ON }
enum class RuleKind { APP, FEED }
enum class BypassMode { NONE, CONFIRM, COUNTDOWN }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SessionState { ACTIVE, COMPLETED, CANCELLED }
enum class BlockAction { SHOWN, BYPASSED }
enum class ServiceHealth { CONNECTED, ENABLED_NOT_CONNECTED, DISABLED }

/** A launchable app discovered through PackageManager. [category] is ApplicationInfo.category or -1. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val category: Int,
)

data class BlockedApp(
    val packageName: String,
    val enabled: Boolean = true,
    val scope: BlockScope = BlockScope.ALWAYS,
)

/**
 * A rule that blocks a distracting area INSIDE an app (e.g. YouTube Shorts).
 * Detection is heuristic: [idTokens] are matched against accessibility view-id resource names,
 * [textTokens] against node text / content descriptions. Version bounds of 0 mean "any version".
 */
data class FeedRule(
    val id: String,
    val packageName: String,
    val name: String,
    val enabled: Boolean = false,
    val scope: BlockScope = BlockScope.ALWAYS,
    val idTokens: List<String> = emptyList(),
    val textTokens: List<String> = emptyList(),
    val fallbackWholeApp: Boolean = false,
    val minVersion: Long = 0L,
    val maxVersion: Long = 0L,
) {
    fun appliesToVersion(versionCode: Long): Boolean {
        if (versionCode <= 0L) return true // unknown version: do not disable the rule
        if (minVersion > 0L && versionCode < minVersion) return false
        if (maxVersion > 0L && versionCode > maxVersion) return false
        return true
    }
}

data class AppGroup(
    val id: Long,
    val name: String,
    val members: Set<String>,
    val presetKey: String? = null,
)

data class Schedule(
    val id: Long = 0L,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val enabled: Boolean = true,
    val packages: Set<String> = emptySet(),
    val groupIds: Set<Long> = emptySet(),
    val feedRuleIds: Set<String> = emptySet(),
    val exceptionPackages: Set<String> = emptySet(),
    /** When true the schedule only blocks while a focus session is also running. */
    val requireFocusSession: Boolean = false,
)

data class FocusSession(
    val id: Long,
    val startWall: Long,
    val startElapsed: Long,
    val startBoot: Int,
    val plannedMillis: Long,
    val strict: Boolean,
    val groupId: Long?,
    val state: SessionState,
    val endedWall: Long?,
) {
    fun remainingMillis(time: TimeSource): Long =
        SessionClock.remainingMillis(
            plannedMillis, startWall, startElapsed, startBoot,
            time.wallMillis(), time.elapsedMillis(), time.bootCount(),
        )
}

data class BlockEvent(
    val id: Long = 0L,
    val timestamp: Long,
    val packageName: String,
    val kind: RuleKind,
    val ruleId: String,
    val ruleLabel: String,
    val source: BlockSource,
    val action: BlockAction,
)

/** Measured foreground time (UsageStatsManager) for one app on one local day. */
data class DailyUsage(
    val epochDay: Long,
    val packageName: String,
    val foregroundMillis: Long,
)

data class PermissionState(
    val accessibility: ServiceHealth = ServiceHealth.DISABLED,
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val batteryUnrestricted: Boolean = false,
) {
    val blockingReady: Boolean get() = accessibility == ServiceHealth.CONNECTED
}

object Days {
    const val ALL = 127
    const val WEEKDAYS = 31 // Mon..Fri
    const val WEEKEND = 96 // Sat, Sun

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun contains(mask: Int, day: DayOfWeek): Boolean = (mask and bit(day)) != 0
}

/** Everything the user can configure. One DataStore-backed object; see SettingsRepository. */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: String = "", // "" = follow system
    val haptics: Boolean = true,
    val blockMessage: String = DEFAULT_BLOCK_MESSAGE,
    val notifySessionStart: Boolean = true,
    val notifySessionEnd: Boolean = true,
    val notifyScheduleStart: Boolean = true,
    val notifyScheduleEnd: Boolean = true,
    val notifyUsageWarning: Boolean = true,
    val notifyServiceFailure: Boolean = true,
    val usageWarnMinutes: Int = 120,
    val defaultFocusMinutes: Int = 25,
    val defaultGroupId: Long = -1L,
    val bypassMode: BypassMode = BypassMode.CONFIRM,
    val bypassMinutes: Int = 5,
    val countdownSeconds: Int = 15,
    val reopenCooldownMinutes: Int = 10,
    val emergencyApps: Set<String> = emptySet(),
    val bypassGrants: Map<String, Long> = emptyMap(),
    val lastBypassEndWall: Long = 0L,
    val masterEnabled: Boolean = true,
    val minutesSavedPerBlock: Int = 5,
    val lastUsageWarnDay: Long = -1L,
    val lastFailureNotifiedWall: Long = 0L,
    val activeScheduleIds: Set<Long> = emptySet(),
) {
    companion object {
        const val DEFAULT_BLOCK_MESSAGE =
            "You chose to protect your focus during this session. " +
                "Stay with your original intention and return when your session ends."
    }
}
