package com.focuslock.app.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Measures foreground time from UsageStatsManager events (requires the user to grant Usage Access).
 * This is for REPORTING only. Usage access cannot block an app; the accessibility service does that.
 *
 * Known limits: a session that began before [from] is not counted for its earlier part, and apps
 * that never emit a pause event are capped (see [MAX_OPEN_TAIL_MS]), so figures are close but not exact.
 */
class UsageStatsReader(private val context: Context) {

    @Suppress("DEPRECATION")
    fun foregroundMillis(from: Long, to: Long): Map<String, Long> {
        if (to <= from) return emptyMap()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(from, to)
            val e = UsageEvents.Event()
            val resumedAt = HashMap<String, Long>()
            val totals = HashMap<String, Long>()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> resumedAt[pkg] = e.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = resumedAt.remove(pkg)
                        if (start != null) {
                            totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - start).coerceAtLeast(0L)
                        }
                    }
                }
            }
            for ((pkg, start) in resumedAt) {
                val tail = to - start
                if (tail in 1..MAX_OPEN_TAIL_MS) totals[pkg] = (totals[pkg] ?: 0L) + tail
            }
            totals
        } catch (se: SecurityException) {
            emptyMap()
        } catch (ex: RuntimeException) {
            emptyMap()
        }
    }

    private companion object {
        const val MAX_OPEN_TAIL_MS = 6L * 60 * 60 * 1000
    }
}
