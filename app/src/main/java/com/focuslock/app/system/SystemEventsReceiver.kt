package com.focuslock.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.app.container
import kotlinx.coroutines.launch

/** Alarm ticks plus the system events that invalidate planned alarms. All work is idempotent. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val pending = goAsync()
        val c = context.container
        c.scope.launch {
            try {
                c.maintenance.run()
            } catch (e: Exception) {
                // Never crash a receiver; the periodic worker will retry.
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Scheduler.ACTION_TICK,
        )
    }
}
