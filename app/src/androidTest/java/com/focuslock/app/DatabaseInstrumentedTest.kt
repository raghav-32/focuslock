package com.focuslock.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.app.data.db.FocusLockDatabase
import com.focuslock.app.data.repo.RoomRulesRepository
import com.focuslock.app.data.repo.RoomSessionRepository
import com.focuslock.app.domain.BlockedApp
import com.focuslock.app.domain.Days
import com.focuslock.app.domain.Schedule
import com.focuslock.app.domain.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** NOT executed in the authoring environment (no emulator). Run with: ./gradlew connectedDebugAndroidTest */
@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private lateinit var db: FocusLockDatabase
    private lateinit var rules: RoomRulesRepository
    private val clock = object : TimeSource {
        var wall = 1_000_000L
        var elapsed = 10_000L
        var boot = 1
        override fun wallMillis() = wall
        override fun elapsedMillis() = elapsed
        override fun bootCount() = boot
    }

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FocusLockDatabase::class.java).build()
        rules = RoomRulesRepository(db)
    }

    @After fun close() = db.close()

    @Test fun blockedAppsPersistAndUnblock() = runBlocking {
        rules.setBlocked(listOf("a.app", "b.app"), true)
        assertEquals(setOf("a.app", "b.app"), rules.getBlockedApps().map { it.packageName }.toSet())
        rules.setBlocked(listOf("a.app"), false)
        assertEquals(listOf("b.app"), rules.getBlockedApps().map { it.packageName })
    }

    @Test fun reBlockingKeepsScopeChoice() = runBlocking {
        rules.setBlocked(listOf("a.app"), true)
        rules.updateBlockedApp(BlockedApp("a.app", true, com.focuslock.app.domain.BlockScope.SESSIONS_ONLY))
        rules.setBlocked(listOf("a.app"), true)
        assertEquals(com.focuslock.app.domain.BlockScope.SESSIONS_ONLY, rules.getBlockedApps().single().scope)
    }

    @Test fun schedulesRoundTrip() = runBlocking {
        val id = rules.saveSchedule(Schedule(0, "Night", 22 * 60, 6 * 60, Days.ALL, packages = setOf("a.app"), exceptionPackages = setOf("b.app")))
        val back = rules.getSchedule(id)!!
        assertEquals("Night", back.name)
        assertEquals(setOf("a.app"), back.packages)
        assertEquals(setOf("b.app"), back.exceptionPackages)
        rules.saveSchedule(back.copy(name = "Renamed"))
        assertEquals(1, rules.getSchedules().size)
        rules.deleteSchedule(id)
        assertNull(rules.getSchedule(id))
    }

    @Test fun appCanBelongToSeveralGroups() = runBlocking {
        val g1 = rules.createGroup("Study", setOf("a.app", "b.app"))
        val g2 = rules.createGroup("Work", setOf("a.app"))
        val groups = rules.getGroups().associateBy { it.id }
        assertTrue("a.app" in groups[g1]!!.members && "a.app" in groups[g2]!!.members)
        rules.deleteGroup(g1)
        assertEquals(1, rules.getGroups().size)
    }

    @Test fun feedRulesSeedOnce() = runBlocking {
        rules.seedDefaultFeedRulesIfEmpty()
        val n = rules.getFeedRules().size
        assertTrue(n >= 3)
        rules.seedDefaultFeedRulesIfEmpty()
        assertEquals(n, rules.getFeedRules().size)
        assertTrue(rules.getFeedRules().none { it.enabled })
    }

    @Test fun sessionLifecycleAndStrictCancel() = runBlocking {
        val sessions = RoomSessionRepository(db.focusSessionDao(), clock)
        val strict = sessions.start(10 * 60_000L, strict = true, groupId = null)
        assertFalse(sessions.cancel(strict.id))                       // refused while running
        assertNotNull(sessions.observeActive().first())
        clock.elapsed += 11 * 60_000L
        val r = sessions.reconcile()
        assertNull(r.active)
        assertNotNull(r.justCompleted)                                // exactly one caller sees completion
        assertNull(sessions.reconcile().justCompleted)
    }

    @Test fun startingTwiceReturnsRunningSession() = runBlocking {
        val sessions = RoomSessionRepository(db.focusSessionDao(), clock)
        val a = sessions.start(10 * 60_000L, false, null)
        val b = sessions.start(25 * 60_000L, false, null)
        assertEquals(a.id, b.id)
    }

    @Test fun sessionSurvivesRebootViaWallClock() = runBlocking {
        val sessions = RoomSessionRepository(db.focusSessionDao(), clock)
        sessions.start(60 * 60_000L, false, null)
        clock.boot = 2; clock.elapsed = 5_000L; clock.wall += 20 * 60_000L // rebooted 20 minutes later
        val active = sessions.reconcile().active!!
        assertEquals(40 * 60_000L, active.remainingMillis(clock))
    }
}
