package com.focuslock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleEvaluatorTest {
    private val utc = ZoneId.of("UTC")
    private fun t(h: Int, m: Int = 0, day: Int = 21, zone: ZoneId = utc, month: Int = 10, year: Int = 2026) =
        ZonedDateTime.of(year, month, day, h, m, 0, 0, zone)

    private val study = Schedule(1, "Study", 7 * 60, 12 * 60, Days.ALL)
    private val night = Schedule(2, "Night", 22 * 60, 6 * 60, Days.ALL)

    @Test fun normalWindowEdges() {
        assertTrue(ScheduleEvaluator.isActive(study, t(7)))
        assertTrue(ScheduleEvaluator.isActive(study, t(11, 59)))
        assertFalse(ScheduleEvaluator.isActive(study, t(12)))
        assertFalse(ScheduleEvaluator.isActive(study, t(6, 59)))
    }

    @Test fun overnightWindow() {
        assertTrue(ScheduleEvaluator.isActive(night, t(22)))
        assertTrue(ScheduleEvaluator.isActive(night, t(23, 59)))
        assertTrue(ScheduleEvaluator.isActive(night, t(0)))
        assertTrue(ScheduleEvaluator.isActive(night, t(5, 59)))
        assertFalse(ScheduleEvaluator.isActive(night, t(6)))
        assertFalse(ScheduleEvaluator.isActive(night, t(12)))
    }

    @Test fun degenerateSchedulesNeverActive() {
        assertFalse(ScheduleEvaluator.isActive(study.copy(startMinute = 60, endMinute = 60), t(1)))
        assertFalse(ScheduleEvaluator.isActive(study.copy(daysMask = 0), t(8)))
        assertFalse(ScheduleEvaluator.isActive(study.copy(enabled = false), t(8)))
    }

    @Test fun currentEndForNormalAndOvernight() {
        assertEquals(t(12), ScheduleEvaluator.currentEnd(study, t(9)))
        assertEquals(t(6, day = 22), ScheduleEvaluator.currentEnd(night, t(23)))
        assertEquals(t(6, day = 21), ScheduleEvaluator.currentEnd(night, t(3)))
        assertNull(ScheduleEvaluator.currentEnd(study, t(15)))
    }

    @Test fun nextBoundaryPicksEarliestStartOrEnd() {
        val b = ScheduleEvaluator.nextBoundary(listOf(study, night), t(8))!!
        assertFalse(b.isStart)               // Study ends at 12:00 before Night starts at 22:00
        assertEquals(t(12), b.at)
        val c = ScheduleEvaluator.nextBoundary(listOf(study, night), t(13))!!
        assertTrue(c.isStart)
        assertEquals(t(22), c.at)
        assertEquals("Night", c.scheduleName)
    }

    @Test fun nextBoundaryWhileOvernightIsActiveIsTheMorningEnd() {
        val b = ScheduleEvaluator.nextBoundary(listOf(night), t(3))!!
        assertFalse(b.isStart)
        assertEquals(t(6), b.at)
    }

    @Test fun nextBoundarySkipsUnselectedDays() {
        val weekend = Schedule(3, "Weekend", 10 * 60, 11 * 60, Days.WEEKEND)
        val b = ScheduleEvaluator.nextBoundary(listOf(weekend), t(12, day = 21))!! // Wednesday
        assertEquals(DayOfWeek.SATURDAY, b.at.dayOfWeek)
        assertTrue(b.isStart)
    }

    @Test fun noSchedulesMeansNoBoundary() {
        assertNull(ScheduleEvaluator.nextBoundary(emptyList(), t(12)))
        assertNull(ScheduleEvaluator.nextBoundary(listOf(study.copy(enabled = false)), t(12)))
    }

    @Test fun startsOnlyFilter() {
        val b = ScheduleEvaluator.nextBoundary(listOf(study), t(8), startsOnly = true)!!
        assertTrue(b.isStart)
        assertEquals(22, b.at.dayOfMonth) // tomorrow 07:00
    }

    @Test fun timeZoneChangeEvaluation() {
        val ny = ZoneId.of("America/New_York")
        val kathmandu = ZoneId.of("Asia/Kathmandu")
        val instant = t(12, zone = utc).toInstant()
        assertTrue(ScheduleEvaluator.isActive(study, instant.atZone(ny)))         // 08:00 in New York
        assertFalse(ScheduleEvaluator.isActive(study, instant.atZone(kathmandu))) // 17:45 in Kathmandu
    }

    @Test fun springForwardGapStartsAtTheNextValidTime() {
        val ny = ZoneId.of("America/New_York")
        val gap = Schedule(4, "Gap", 2 * 60 + 30, 4 * 60, Days.ALL) // 02:30 does not exist on 2026-03-08
        val before = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ny)
        val b = ScheduleEvaluator.nextBoundary(listOf(gap), before)!!
        assertTrue(b.isStart)
        assertEquals(3, b.at.hour)
        assertEquals(30, b.at.minute)
        assertTrue(ScheduleEvaluator.isActive(gap, ZonedDateTime.of(2026, 3, 8, 3, 45, 0, 0, ny)))
    }

    @Test fun fallBackRepeatedHourDoesNotBreakEvaluation() {
        val ny = ZoneId.of("America/New_York")
        val s = Schedule(5, "Late", 1 * 60, 2 * 60, Days.ALL)
        val first = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, ny)       // first 01:30 (EDT)
        val second = first.plusHours(1)                                   // second 01:30 (EST)
        assertTrue(ScheduleEvaluator.isActive(s, first))
        assertTrue(ScheduleEvaluator.isActive(s, second))
        assertNotNull(ScheduleEvaluator.nextBoundary(listOf(s), second))
    }

    @Test fun formatMinute() {
        assertEquals("12:00 AM", ScheduleEvaluator.formatMinute(0))
        assertEquals("7:05 AM", ScheduleEvaluator.formatMinute(7 * 60 + 5))
        assertEquals("12:00 PM", ScheduleEvaluator.formatMinute(12 * 60))
        assertEquals("10:30 PM", ScheduleEvaluator.formatMinute(22 * 60 + 30))
    }
}
