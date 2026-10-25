package com.focuslock.app.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.focuslock.app.domain.RulesRepository
import com.focuslock.app.domain.ScheduleEvaluator
import com.focuslock.app.domain.SessionRepository
import com.focuslock.app.domain.TimeSource
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

/**
 * Plans ONE inexact alarm for the next interesting moment (session end or schedule boundary).
 *
 * Important design point: ENFORCEMENT never depends on this alarm. Every accessibility event is
 * evaluated against the current time. The alarm only lets us send notifications and close a finished
 * session while no app event is happening. We use setAndAllowWhileIdle (no exact-alarm permission),
 * so it may fire a few minutes late in Doze; WorkManager provides a periodic safety net.
 */
class Scheduler(
    private val context: Context,
    private val rules: RulesRepository,
    private val sessions: SessionRepository,
    private val time: TimeSource,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_TICK,
        Intent(context, SystemEventsReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    suspend fun replan() {
        val candidates = ArrayList<Long>(2)

        sessions.observeActive().first()?.let { s ->
            val remaining = s.remainingMillis(time)
            if (remaining > 0L) candidates += time.wallMillis() + remaining
        }
        ScheduleEvaluator.nextBoundary(rules.getSchedules(), ZonedDateTime.now())?.let {
            candidates += it.at.toInstant().toEpochMilli()
        }

        val next = candidates.minOrNull()
        val pi = pendingIntent()
        if (next == null) {
            alarmManager.cancel(pi)
        } else {
            // +1s so we wake just AFTER the boundary and observe the new state.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next + 1_000L, pi)
        }
    }

    companion object {
        const val ACTION_TICK = "com.focuslock.app.action.TICK"
        private const val REQUEST_TICK = 41
    }
}
