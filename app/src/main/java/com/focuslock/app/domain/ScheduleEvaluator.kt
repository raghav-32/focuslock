package com.focuslock.app.domain

import java.time.ZonedDateTime

/**
 * Schedules are defined in LOCAL WALL-CLOCK time ("7:00-12:00"). Evaluation therefore uses the
 * device's current zone, so a time-zone change or daylight-saving shift moves the real instant at
 * which a schedule starts but never changes what the user sees on the clock.
 *
 * A schedule with start > end crosses midnight; its day mask refers to the day it STARTS on.
 */
object ScheduleEvaluator {
    data class Boundary(
        val at: ZonedDateTime,
        val isStart: Boolean,
        val scheduleId: Long,
        val scheduleName: String,
    )

    private fun minuteOfDay(t: ZonedDateTime): Int = t.hour * 60 + t.minute

    fun isActive(s: Schedule, now: ZonedDateTime): Boolean {
        if (!s.enabled || s.startMinute == s.endMinute) return false
        val m = minuteOfDay(now)
        val today = Days.contains(s.daysMask, now.dayOfWeek)
        return if (s.startMinute < s.endMinute) {
            today && m >= s.startMinute && m < s.endMinute
        } else {
            val yesterday = Days.contains(s.daysMask, now.dayOfWeek.minus(1))
            (today && m >= s.startMinute) || (yesterday && m < s.endMinute)
        }
    }

    /** The instant the currently running period of [s] ends, or null when it is not active. */
    fun currentEnd(s: Schedule, now: ZonedDateTime): ZonedDateTime? {
        if (!isActive(s, now)) return null
        val m = minuteOfDay(now)
        val today = now.toLocalDate()
        val endDate = when {
            s.startMinute < s.endMinute -> today
            m >= s.startMinute -> today.plusDays(1)
            else -> today
        }
        return endDate.atTime(s.endMinute / 60, s.endMinute % 60).atZone(now.zone)
    }

    /** Earliest start/end strictly after [now] among enabled schedules. */
    fun nextBoundary(
        schedules: List<Schedule>,
        now: ZonedDateTime,
        startsOnly: Boolean = false,
    ): Boundary? {
        var best: Boundary? = null
        fun consider(candidate: Boundary) {
            val current = best
            if (current == null || candidate.at.isBefore(current.at)) best = candidate
        }
        for (s in schedules) {
            if (!s.enabled || s.startMinute == s.endMinute || s.daysMask == 0) continue
            val overnight = s.startMinute > s.endMinute
            // Offset -1 matters: an overnight period that began yesterday ends today.
            for (offset in -1L..8L) {
                val date = now.toLocalDate().plusDays(offset)
                if (!Days.contains(s.daysMask, date.dayOfWeek)) continue
                val start = date.atTime(s.startMinute / 60, s.startMinute % 60).atZone(now.zone)
                if (start.isAfter(now)) consider(Boundary(start, true, s.id, s.name))
                if (!startsOnly) {
                    val endDate = if (overnight) date.plusDays(1) else date
                    val end = endDate.atTime(s.endMinute / 60, s.endMinute % 60).atZone(now.zone)
                    if (end.isAfter(now)) consider(Boundary(end, false, s.id, s.name))
                }
            }
        }
        return best
    }

    fun formatMinute(minuteOfDay: Int): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        val suffix = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${m.toString().padStart(2, '0')} $suffix"
    }
}
