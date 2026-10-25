package com.focuslock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FeedDetectorTest {
    private val rule = FeedRule("yt", "com.yt", "Shorts", enabled = true, idTokens = listOf("reel_player", "shorts_container"))

    @Test fun matchesOnViewIdCaseInsensitively() {
        val nodes = listOf(NodeSample("com.yt:id/Reel_Player_Page", null, null))
        assertTrue(FeedDetector.matches(rule, nodes))
    }

    @Test fun ignoresUnrelatedScreens() {
        val nodes = listOf(NodeSample("com.yt:id/home_feed", "Home", "Subscriptions"), NodeSample(null, "Shorts", null))
        assertFalse(FeedDetector.matches(rule, nodes)) // a "Shorts" tab label alone is not a match
    }

    @Test fun unsupportedFeedWithNoTokensNeverMatches() {
        val empty = rule.copy(idTokens = emptyList(), textTokens = emptyList())
        assertFalse(FeedDetector.matches(empty, listOf(NodeSample("anything", "Reels", "Reels"))))
    }

    @Test fun textTokensMatchTextOrDescription() {
        val r = rule.copy(idTokens = emptyList(), textTokens = listOf("reels"))
        assertTrue(FeedDetector.matches(r, listOf(NodeSample(null, null, "Reels tab selected"))))
        assertTrue(FeedDetector.matches(r, listOf(NodeSample(null, "REELS", null))))
        assertFalse(FeedDetector.matches(r, listOf(NodeSample(null, "Stories", null))))
    }

    @Test fun missingAccessibilityInformationDoesNotMatch() {
        assertFalse(FeedDetector.matches(rule, emptyList()))
        assertFalse(FeedDetector.matches(rule, listOf(NodeSample(null, null, null))))
    }

    @Test fun blankTokensAreIgnored() {
        val r = rule.copy(idTokens = listOf("  ", ""))
        assertFalse(FeedDetector.matches(r, listOf(NodeSample("any:id/x", null, null))))
    }

    @Test fun defaultRulesShipDisabledAndForTheExpectedApps() {
        assertEquals(setOf("com.google.android.youtube", "com.instagram.android", "com.facebook.katana"), DefaultFeedRules.all.map { it.packageName }.toSet())
        assertTrue(DefaultFeedRules.all.none { it.enabled })
        assertTrue(DefaultFeedRules.all.all { it.idTokens.isNotEmpty() })
    }
}

class PolicyAndStatsTest {
    private val now = 1_000_000_000L

    @Test fun bypassModes() {
        val none = AppSettings(bypassMode = BypassMode.NONE)
        val confirm = AppSettings(bypassMode = BypassMode.CONFIRM, reopenCooldownMinutes = 10)
        assertFalse(BypassPolicy.canRequest(none, false, now))
        assertTrue(BypassPolicy.canRequest(confirm, false, now))
        assertFalse(BypassPolicy.canRequest(confirm, strictSession = true, nowWall = now))
    }

    @Test fun cooldownBlocksNextBypass() {
        val s = AppSettings(bypassMode = BypassMode.CONFIRM, reopenCooldownMinutes = 10, lastBypassEndWall = now)
        assertFalse(BypassPolicy.canRequest(s, false, now + 5 * 60_000))
        assertEquals(5 * 60_000L, BypassPolicy.cooldownRemainingMillis(s, now + 5 * 60_000))
        assertTrue(BypassPolicy.canRequest(s, false, now + 10 * 60_000))
    }

    @Test fun bypassGrantLength() {
        assertEquals(now + 5 * 60_000, BypassPolicy.grantUntil(now, 5))
        assertEquals(now + 60_000, BypassPolicy.grantUntil(now, 0)) // never zero-length
    }

    @Test fun lockPolicy() {
        assertTrue(LockPolicy.canEditRules(false))
        assertFalse(LockPolicy.canEditRules(true))
    }

    @Test fun percentChange() {
        assertNull(StatsCalculator.percentChange(0.0, 5.0))
        assertEquals(50.0, StatsCalculator.percentChange(10.0, 15.0)!!, 0.0001)
        assertEquals(-100.0, StatsCalculator.percentChange(10.0, 0.0)!!, 0.0001)
    }

    @Test fun timeSavedIsAnExplicitProduct() {
        assertEquals(35, StatsCalculator.timeSavedMinutes(7, 5))
        assertEquals(0, StatsCalculator.timeSavedMinutes(7, -3))
    }

    @Test fun dailyCountsFillMissingDaysWithZero() {
        val zone = ZoneId.of("UTC")
        val end = LocalDate.of(2026, 10, 21)
        val ts = listOf(
            end.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            end.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
            end.minusDays(2).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        val out = StatsCalculator.dailyCounts(ts, zone, end, 4)
        assertEquals(listOf(0, 1, 0, 2), out.map { it.second })
        assertEquals(end, out.last().first)
    }

    @Test fun hourlyCounts() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 10, 21)
        val ts = listOf(day.atTime(7, 5).atZone(zone).toInstant().toEpochMilli(), day.atTime(7, 55).atZone(zone).toInstant().toEpochMilli())
        val h = StatsCalculator.hourlyCounts(ts, zone, day)
        assertEquals(2, h[7]); assertEquals(24, h.size)
    }

    @Test fun mostFrequent() {
        assertEquals("a" to 3, StatsCalculator.mostFrequent(listOf("a", "b", "a", "a")))
        assertNull(StatsCalculator.mostFrequent(emptyList()))
    }

    @Test fun sessionMillisIsCappedAtPlannedLength() {
        val s = FocusSession(1, 0L, 0L, 1, 10 * 60_000L, false, null, SessionState.COMPLETED, endedWall = 60 * 60_000L)
        assertEquals(10 * 60_000L, StatsCalculator.sessionMillis(s, 99 * 60_000L))
        val running = s.copy(state = SessionState.ACTIVE, endedWall = null)
        assertEquals(4 * 60_000L, StatsCalculator.sessionMillis(running, 4 * 60_000L))
    }

    @Test fun formatDuration() {
        assertEquals("30s", formatDuration(30_000))
        assertEquals("5m", formatDuration(5 * 60_000))
        assertEquals("1h 05m", formatDuration(65 * 60_000))
    }

    @Test fun presetsListOnlyInstalledAppsAndNeverTouchAnything() {
        val apps = listOf(
            InstalledApp("com.instagram.android", "Instagram", false, -1),
            InstalledApp("com.some.game", "Cool Game", false, 0),
            InstalledApp("com.notes", "Notes", false, -1),
        )
        val social = PresetCatalog.all.first { it.key == "social" }
        assertEquals(listOf("Instagram"), PresetCatalog.matching(social, apps).map { it.label })
        val games = PresetCatalog.all.first { it.key == "games" }
        assertEquals(listOf("Cool Game"), PresetCatalog.matching(games, apps).map { it.label })
        assertNotNull(PresetCatalog.all.firstOrNull { it.key == "shopping" })
    }

    @Test fun daysMaskHelpers() {
        assertTrue(Days.contains(Days.WEEKDAYS, java.time.DayOfWeek.FRIDAY))
        assertFalse(Days.contains(Days.WEEKDAYS, java.time.DayOfWeek.SATURDAY))
        assertTrue(Days.contains(Days.WEEKEND, java.time.DayOfWeek.SUNDAY))
        assertEquals(127, Days.WEEKDAYS or Days.WEEKEND)
    }
}
