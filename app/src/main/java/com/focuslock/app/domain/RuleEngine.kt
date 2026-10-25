package com.focuslock.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class ActiveSession(
    val id: Long,
    val strict: Boolean,
    val endsAtWall: Long,
    /** null = the whole blocked-apps list; otherwise only members of this group. */
    val groupId: Long?,
)

/** Immutable view of every rule, rebuilt whenever the database or settings change. */
data class RuleSnapshot(
    val blockedApps: Map<String, BlockedApp> = emptyMap(),
    val feedRules: List<FeedRule> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val groupMembers: Map<Long, Set<String>> = emptyMap(),
    val session: ActiveSession? = null,
    val masterEnabled: Boolean = true,
    /** Launcher, dialer, settings, keyboard, FocusLock itself... never blocked (anti-trap). */
    val neverBlock: Set<String> = emptySet(),
    /** Packages whose window events must not change "which app is in the foreground". */
    val ignoredForForeground: Set<String> = emptySet(),
    val emergencyAllow: Set<String> = emptySet(),
    val bypassUntil: Map<String, Long> = emptyMap(),
) {
    val feedRulesByPackage: Map<String, List<FeedRule>> =
        feedRules.filter { it.enabled }.groupBy { it.packageName }
}

data class BlockDecision(
    val kind: RuleKind,
    val packageName: String,
    val ruleId: String,
    /** Human label for FEED rules; empty for APP (the UI resolves the app name). */
    val label: String,
    val source: BlockSource,
    val sourceName: String,
    val endsAtWall: Long?,
    val strict: Boolean,
)

/**
 * Rule priority (documented in README and the About screen):
 *  0. Exemptions always win: master switch off, never-block apps, emergency-access apps, and an
 *     unexpired emergency bypass (bypass is ignored during a STRICT focus session).
 *  1. Whole-app rules beat feed rules for the same app.
 *  2. Among sources, the reason shown is: focus session > schedule > always-blocked list.
 *  3. Within schedules, the first matching schedule in list order is reported.
 */
object RuleEngine {

    fun decideApp(
        s: RuleSnapshot,
        pkg: String,
        nowWall: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BlockDecision? {
        val session = activeSession(s, nowWall)
        if (isExempt(s, pkg, nowWall, session)) return null
        val now = Instant.ofEpochMilli(nowWall).atZone(zone)
        val strict = session?.strict == true

        if (session != null) {
            val groupId = session.groupId
            val applies = if (groupId == null) {
                s.blockedApps[pkg]?.enabled == true
            } else {
                pkg in s.groupMembers[groupId].orEmpty()
            }
            if (applies) {
                return BlockDecision(
                    RuleKind.APP, pkg, pkg, "", BlockSource.FOCUS_SESSION,
                    "Focus session", session.endsAtWall, strict,
                )
            }
        }

        val schedule = activeSchedules(s, now, session != null).firstOrNull { appliesTo(s, it, pkg) }
        if (schedule != null) {
            return BlockDecision(
                RuleKind.APP, pkg, pkg, "", BlockSource.SCHEDULE,
                schedule.name, endMillis(schedule, now), strict,
            )
        }

        val rule = s.blockedApps[pkg]
        if (rule != null && rule.enabled && rule.scope == BlockScope.ALWAYS) {
            return BlockDecision(
                RuleKind.APP, pkg, pkg, "", BlockSource.ALWAYS_ON,
                "Always-blocked list", null, strict,
            )
        }

        // Fallback: a feed rule flagged "block the whole app instead" when the feed cannot be
        // identified reliably.
        for (fr in s.feedRules) {
            if (fr.packageName == pkg && fr.enabled && fr.fallbackWholeApp) {
                val d = decideFeed(s, fr, nowWall, zone)
                if (d != null) return d.copy(kind = RuleKind.APP, ruleId = pkg, label = "")
            }
        }
        return null
    }

    /** Is [rule] in force right now (independent of whether the feed is currently on screen)? */
    fun decideFeed(
        s: RuleSnapshot,
        rule: FeedRule,
        nowWall: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BlockDecision? {
        if (!rule.enabled) return null
        val session = activeSession(s, nowWall)
        if (isExempt(s, rule.packageName, nowWall, session)) return null
        val now = Instant.ofEpochMilli(nowWall).atZone(zone)
        val strict = session?.strict == true

        fun decision(source: BlockSource, name: String, ends: Long?) =
            BlockDecision(RuleKind.FEED, rule.packageName, rule.id, rule.name, source, name, ends, strict)

        if (session != null) return decision(BlockSource.FOCUS_SESSION, "Focus session", session.endsAtWall)

        val schedule = activeSchedules(s, now, false).firstOrNull {
            rule.id in it.feedRuleIds && rule.packageName !in it.exceptionPackages
        }
        if (schedule != null) return decision(BlockSource.SCHEDULE, schedule.name, endMillis(schedule, now))

        if (rule.scope == BlockScope.ALWAYS) return decision(BlockSource.ALWAYS_ON, "Always-blocked list", null)
        return null
    }

    private fun activeSession(s: RuleSnapshot, nowWall: Long): ActiveSession? =
        s.session?.takeIf { it.endsAtWall > nowWall }

    private fun isExempt(s: RuleSnapshot, pkg: String, nowWall: Long, session: ActiveSession?): Boolean {
        if (!s.masterEnabled) return true
        if (pkg in s.neverBlock || pkg in s.emergencyAllow) return true
        if (session?.strict != true) {
            val until = s.bypassUntil[pkg]
            if (until != null && until > nowWall) return true
        }
        return false
    }

    private fun activeSchedules(s: RuleSnapshot, now: ZonedDateTime, sessionRunning: Boolean): List<Schedule> =
        s.schedules.filter { ScheduleEvaluator.isActive(it, now) && (!it.requireFocusSession || sessionRunning) }

    private fun appliesTo(s: RuleSnapshot, schedule: Schedule, pkg: String): Boolean {
        if (pkg in schedule.exceptionPackages) return false
        if (pkg in schedule.packages) return true
        return schedule.groupIds.any { pkg in s.groupMembers[it].orEmpty() }
    }

    private fun endMillis(schedule: Schedule, now: ZonedDateTime): Long? =
        ScheduleEvaluator.currentEnd(schedule, now)?.toInstant()?.toEpochMilli()
}
