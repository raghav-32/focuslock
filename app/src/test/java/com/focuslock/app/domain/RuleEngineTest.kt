package com.focuslock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngineTest {
    private val zone = ZoneId.of("UTC")

    // Wednesday 21 Oct 2026
    private fun at(h: Int, m: Int = 0, day: Int = 21) =
        ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun snap(
        blocked: List<BlockedApp> = emptyList(),
        feeds: List<FeedRule> = emptyList(),
        schedules: List<Schedule> = emptyList(),
        groups: Map<Long, Set<String>> = emptyMap(),
        session: ActiveSession? = null,
        master: Boolean = true,
        bypass: Map<String, Long> = emptyMap(),
        emergency: Set<String> = emptySet(),
    ) = RuleSnapshot(
        blockedApps = blocked.associateBy { it.packageName },
        feedRules = feeds,
        schedules = schedules,
        groupMembers = groups,
        session = session,
        masterEnabled = master,
        neverBlock = setOf("com.android.launcher", "com.focuslock.app"),
        emergencyAllow = emergency,
        bypassUntil = bypass,
    )

    private fun decide(s: RuleSnapshot, pkg: String, now: Long) = RuleEngine.decideApp(s, pkg, now, zone)

    @Test fun oneAppBlocked() {
        val d = decide(snap(blocked = listOf(BlockedApp("a.app"))), "a.app", at(12))
        assertNotNull(d)
        assertEquals(BlockSource.ALWAYS_ON, d!!.source)
        assertEquals(RuleKind.APP, d.kind)
    }

    @Test fun multipleAppsBlockedOthersAllowed() {
        val s = snap(blocked = listOf(BlockedApp("a.one"), BlockedApp("b.two"), BlockedApp("c.three")))
        assertNotNull(decide(s, "a.one", at(12)))
        assertNotNull(decide(s, "b.two", at(12)))
        assertNotNull(decide(s, "c.three", at(12)))
        assertNull(decide(s, "d.four", at(12)))
    }

    @Test fun unblockedOrDisabledAppIsAllowed() {
        assertNull(decide(snap(), "a.app", at(12)))
        assertNull(decide(snap(blocked = listOf(BlockedApp("a.app", enabled = false))), "a.app", at(12)))
    }

    @Test fun sessionsOnlyScopeBlocksOnlyWhileSessionRuns() {
        val app = BlockedApp("a.app", scope = BlockScope.SESSIONS_ONLY)
        assertNull(decide(snap(blocked = listOf(app)), "a.app", at(12)))
        val session = ActiveSession(1, strict = false, endsAtWall = at(13), groupId = null)
        val d = decide(snap(blocked = listOf(app), session = session), "a.app", at(12))
        assertEquals(BlockSource.FOCUS_SESSION, d!!.source)
        assertEquals(at(13), d.endsAtWall)
    }

    @Test fun expiredSessionDoesNotBlock() {
        val app = BlockedApp("a.app", scope = BlockScope.SESSIONS_ONLY)
        val session = ActiveSession(1, false, endsAtWall = at(11), groupId = null)
        assertNull(decide(snap(blocked = listOf(app), session = session), "a.app", at(12)))
    }

    @Test fun groupSessionBlocksOnlyGroupMembers() {
        val s = snap(
            blocked = listOf(BlockedApp("x.other", scope = BlockScope.SESSIONS_ONLY)),
            groups = mapOf(7L to setOf("g.member")),
            session = ActiveSession(1, false, at(13), groupId = 7L),
        )
        assertNotNull(decide(s, "g.member", at(12)))
        assertNull(decide(s, "x.other", at(12)))
    }

    @Test fun scheduleBlocksInsideWindowOnly() {
        val sched = Schedule(1, "Study", 9 * 60, 17 * 60, Days.WEEKDAYS, packages = setOf("a.app"))
        val s = snap(schedules = listOf(sched))
        val d = decide(s, "a.app", at(12))
        assertEquals(BlockSource.SCHEDULE, d!!.source)
        assertEquals("Study", d.sourceName)
        assertEquals(at(17), d.endsAtWall)
        assertNull(decide(s, "a.app", at(8, 59)))
        assertNull(decide(s, "a.app", at(17)))          // end is exclusive
        assertNotNull(decide(s, "a.app", at(9)))         // start is inclusive
        assertNull(decide(s, "a.app", at(12, day = 24))) // Saturday
    }

    @Test fun overnightScheduleSpansMidnight() {
        val sched = Schedule(1, "Night", 22 * 60, 6 * 60, Days.bit(java.time.DayOfWeek.WEDNESDAY), packages = setOf("a.app"))
        val s = snap(schedules = listOf(sched))
        assertNotNull(decide(s, "a.app", at(23)))                 // Wed night
        assertNotNull(decide(s, "a.app", at(3, day = 22)))        // Thu 03:00 belongs to Wed's schedule
        assertNull(decide(s, "a.app", at(7, day = 22)))
        assertNull(decide(s, "a.app", at(3)))                     // Wed 03:00: Tuesday is not selected
        assertEquals(at(6, day = 22), decide(s, "a.app", at(23))!!.endsAtWall)
    }

    @Test fun scheduleExceptionsAndGroupsAreRespected() {
        val sched = Schedule(
            1, "S", 0, 23 * 60 + 59, Days.ALL,
            packages = setOf("a.app"), groupIds = setOf(5L), exceptionPackages = setOf("a.app"),
        )
        val s = snap(schedules = listOf(sched), groups = mapOf(5L to setOf("g.app", "a.app")))
        assertNull(decide(s, "a.app", at(12)))     // exception wins
        assertNotNull(decide(s, "g.app", at(12)))  // via group
    }

    @Test fun scheduleRequiringFocusSessionWaitsForSession() {
        val sched = Schedule(1, "Combo", 9 * 60, 17 * 60, Days.ALL, packages = setOf("a.app"), requireFocusSession = true)
        assertNull(decide(snap(schedules = listOf(sched)), "a.app", at(12)))
        val session = ActiveSession(1, false, at(13), groupId = 99L) // session group does not include a.app
        val d = decide(snap(schedules = listOf(sched), session = session), "a.app", at(12))
        assertEquals(BlockSource.SCHEDULE, d!!.source)
    }

    @Test fun disabledScheduleDoesNothing() {
        val sched = Schedule(1, "Off", 0, 12 * 60, Days.ALL, enabled = false, packages = setOf("a.app"))
        assertNull(decide(snap(schedules = listOf(sched)), "a.app", at(6)))
    }

    @Test fun priorityIsSessionThenScheduleThenAlwaysOn() {
        val sched = Schedule(1, "S", 0, 23 * 60, Days.ALL, packages = setOf("a.app"))
        val app = BlockedApp("a.app")
        val session = ActiveSession(1, false, at(13), null)
        assertEquals(BlockSource.FOCUS_SESSION, decide(snap(listOf(app), schedules = listOf(sched), session = session), "a.app", at(12))!!.source)
        assertEquals(BlockSource.SCHEDULE, decide(snap(listOf(app), schedules = listOf(sched)), "a.app", at(12))!!.source)
        assertEquals(BlockSource.ALWAYS_ON, decide(snap(listOf(app)), "a.app", at(12))!!.source)
    }

    @Test fun neverBlockEmergencyAndMasterSwitchWin() {
        val all = listOf(BlockedApp("com.android.launcher"), BlockedApp("com.focuslock.app"), BlockedApp("bank.app"), BlockedApp("a.app"))
        val s = snap(blocked = all, emergency = setOf("bank.app"))
        assertNull(decide(s, "com.android.launcher", at(12)))
        assertNull(decide(s, "com.focuslock.app", at(12)))
        assertNull(decide(s, "bank.app", at(12)))
        assertNotNull(decide(s, "a.app", at(12)))
        assertNull(decide(snap(blocked = all, master = false), "a.app", at(12)))
    }

    @Test fun emergencyBypassAllowsUntilExpiryButNotInStrictSession() {
        val app = listOf(BlockedApp("a.app"))
        val s = snap(blocked = app, bypass = mapOf("a.app" to at(12, 5)))
        assertNull(decide(s, "a.app", at(12)))
        assertNotNull(decide(s, "a.app", at(12, 6)))   // expired
        val strict = snap(blocked = app, bypass = mapOf("a.app" to at(12, 5)), session = ActiveSession(1, true, at(13), null))
        assertNotNull(decide(strict, "a.app", at(12)))
        assertEquals(true, decide(strict, "a.app", at(12))!!.strict)
    }

    // ---- feeds ----------------------------------------------------------------------------------

    private val shorts = FeedRule("yt", "com.yt", "YouTube Shorts", enabled = true, idTokens = listOf("reel"))

    @Test fun enabledFeedRuleBlocksFeedButNotWholeApp() {
        val s = snap(feeds = listOf(shorts))
        assertNull(decide(s, "com.yt", at(12)))
        val d = RuleEngine.decideFeed(s, shorts, at(12), zone)
        assertEquals(RuleKind.FEED, d!!.kind)
        assertEquals("YouTube Shorts", d.label)
    }

    @Test fun disabledFeedRuleDoesNothing() {
        val off = shorts.copy(enabled = false)
        assertNull(RuleEngine.decideFeed(snap(feeds = listOf(off)), off, at(12), zone))
    }

    @Test fun sessionsOnlyFeedRuleWaitsForSession() {
        val rule = shorts.copy(scope = BlockScope.SESSIONS_ONLY)
        assertNull(RuleEngine.decideFeed(snap(feeds = listOf(rule)), rule, at(12), zone))
        val withSession = snap(feeds = listOf(rule), session = ActiveSession(1, false, at(13), null))
        assertEquals(BlockSource.FOCUS_SESSION, RuleEngine.decideFeed(withSession, rule, at(12), zone)!!.source)
    }

    @Test fun feedFallbackBlocksTheWholeApp() {
        val rule = shorts.copy(fallbackWholeApp = true)
        val d = decide(snap(feeds = listOf(rule)), "com.yt", at(12))
        assertEquals(RuleKind.APP, d!!.kind)
        assertEquals("com.yt", d.ruleId)
    }

    @Test fun feedRuleVersionBounds() {
        val rule = shorts.copy(minVersion = 100, maxVersion = 200)
        assertEquals(true, rule.appliesToVersion(150))
        assertEquals(false, rule.appliesToVersion(99))
        assertEquals(false, rule.appliesToVersion(201))
        assertEquals(true, rule.appliesToVersion(0)) // unknown version keeps the rule on
    }
}
