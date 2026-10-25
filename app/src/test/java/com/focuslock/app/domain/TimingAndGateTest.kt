package com.focuslock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionClockTest {
    private val min = 60_000L

    @Test fun sameBootUsesMonotonicClockAndIgnoresWallClockChanges() {
        // 25 min session, 10 min elapsed on the monotonic clock, but the user moved the wall clock +5 hours.
        val remaining = SessionClock.remainingMillis(
            plannedMillis = 25 * min, startWall = 1_000_000, startElapsed = 500_000, startBoot = 7,
            nowWall = 1_000_000 + 5 * 60 * min, nowElapsed = 500_000 + 10 * min, nowBoot = 7,
        )
        assertEquals(15 * min, remaining)
    }

    @Test fun wallClockMovedBackDoesNotExtendSession() {
        val remaining = SessionClock.remainingMillis(
            25 * min, 1_000_000, 500_000, 7,
            nowWall = 1_000_000 - 60 * min, nowElapsed = 500_000 + 20 * min, nowBoot = 7,
        )
        assertEquals(5 * min, remaining)
    }

    @Test fun afterRebootFallsBackToWallClock() {
        val remaining = SessionClock.remainingMillis(
            60 * min, startWall = 1_000_000, startElapsed = 9_000_000, startBoot = 7,
            nowWall = 1_000_000 + 20 * min, nowElapsed = 30_000, nowBoot = 8, // elapsed counter restarted
        )
        assertEquals(40 * min, remaining)
    }

    @Test fun rebootWithWallClockBehindCountsZeroElapsed() {
        val elapsed = SessionClock.elapsedMillis(1_000_000, 9_000_000, 7, nowWall = 500_000, nowElapsed = 1_000, nowBoot = 8)
        assertEquals(0L, elapsed)
    }

    @Test fun unknownBootCountTreatedAsSameBootOnlyIfElapsedMonotonic() {
        // boot count -1 on both sides, monotonic clock moved forward: use it.
        assertEquals(5 * min, SessionClock.elapsedMillis(0, 100, -1, 10, 100 + 5 * min, -1))
        // monotonic clock went backwards (reboot we could not detect): use wall clock.
        assertEquals(7 * min, SessionClock.elapsedMillis(0, 10 * min, -1, 7 * min, 1_000, -1))
    }

    @Test fun finishedSessionHasZeroRemainingAndNeverNegative() {
        val remaining = SessionClock.remainingMillis(5 * min, 0, 0, 1, 0, 99 * min, 1)
        assertEquals(0L, remaining)
    }

    @Test fun remainingNeverExceedsPlanned() {
        assertEquals(5 * min, SessionClock.remainingMillis(5 * min, 0, 100, 1, 0, 50, 1)) // elapsed went "backwards"
    }
}

class BlockGateTest {
    @Test fun duplicateEventsForSameAppAreSuppressed() {
        val g = BlockGate(appDebounceMs = 1_200)
        val changed = g.onForeground("a.app")
        assertTrue(g.shouldShowAppBlock("a.app", 0, changed))
        assertFalse(g.shouldShowAppBlock("a.app", 100, g.onForeground("a.app")))
        assertFalse(g.shouldShowAppBlock("a.app", 1_000, g.onForeground("a.app")))
        assertTrue(g.shouldShowAppBlock("a.app", 1_300, g.onForeground("a.app")))
    }

    @Test fun rapidSwitchingAwayAndBackIsStillBlocked() {
        val g = BlockGate(appDebounceMs = 1_200)
        assertTrue(g.shouldShowAppBlock("a.app", 0, g.onForeground("a.app")))
        g.onForeground("com.focuslock.app")         // our block screen
        g.onForeground("com.android.launcher")
        assertTrue(g.shouldShowAppBlock("a.app", 200, g.onForeground("a.app"))) // re-opened within 200 ms
    }

    @Test fun rapidSwitchingBetweenTwoBlockedAppsBlocksBoth() {
        val g = BlockGate()
        var blocks = 0
        for ((i, p) in listOf("a.app", "b.app", "a.app", "b.app", "a.app").withIndex()) {
            if (g.shouldShowAppBlock(p, i * 50L, g.onForeground(p))) blocks++
        }
        assertEquals(5, blocks)
    }

    @Test fun feedBlockIsLatchedWhileOnTheSameScreen() {
        val g = BlockGate(feedLatchMs = 4_000)
        assertTrue(g.shouldShowFeedBlock("shorts", 0))
        assertFalse(g.shouldShowFeedBlock("shorts", 500))
        assertFalse(g.shouldShowFeedBlock("shorts", 3_900))
        assertTrue(g.shouldShowFeedBlock("shorts", 4_100))   // still visible after latch expiry: act again
    }

    @Test fun feedLatchClearsWhenFeedLeavesTheScreen() {
        val g = BlockGate(feedLatchMs = 4_000)
        assertTrue(g.shouldShowFeedBlock("shorts", 0))
        g.onFeedGone("shorts")
        assertTrue(g.shouldShowFeedBlock("shorts", 100))
    }

    @Test fun feedLatchClearsWhenForegroundAppChanges() {
        val g = BlockGate()
        g.onForeground("com.yt")
        assertTrue(g.shouldShowFeedBlock("shorts", 0))
        g.onForeground("other.app")
        g.onForeground("com.yt")
        assertTrue(g.shouldShowFeedBlock("shorts", 10))
    }

    @Test fun scansAreThrottled() {
        val g = BlockGate(scanThrottleMs = 350)
        assertTrue(g.shouldScan("a", 0))
        assertFalse(g.shouldScan("a", 100))
        assertTrue(g.shouldScan("a", 400))
        assertTrue(g.shouldScan("b", 410)) // other package unaffected
    }

    @Test fun debouncerToleratesClockGoingBackwards() {
        val d = Debouncer(1_000)
        assertTrue(d.shouldFire("k", 5_000))
        assertTrue(d.shouldFire("k", 100)) // negative delta must not block forever
    }

    @Test fun foregroundChangeReporting() {
        val g = BlockGate()
        assertTrue(g.onForeground("a"))
        assertFalse(g.onForeground("a"))
        assertTrue(g.onForeground("b"))
    }
}
