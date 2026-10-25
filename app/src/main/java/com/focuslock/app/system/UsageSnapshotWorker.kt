package com.focuslock.app.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focuslock.app.container
import com.focuslock.app.domain.DailyUsage
import com.focuslock.app.domain.current
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs about every 30 minutes (not exact; WorkManager batches work). It
 *  1. runs [Maintenance] as a safety net for missed alarms,
 *  2. stores today's measured foreground time for apps the user chose to track,
 *  3. sends at most one "time check" notification per day.
 */
class UsageSnapshotWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        return try {
            c.maintenance.run()
            if (c.permissions.usageAccessGranted()) snapshotUsage()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun snapshotUsage() {
        val c = applicationContext.container
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val tracked = trackedPackages()
        if (tracked.isEmpty()) return

        val rows = ArrayList<DailyUsage>()
        var todayTotal = 0L
        for (offset in 0L..1L) { // yesterday too, so late-night usage finishes up correctly
            val day = today.minusDays(offset)
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
            val perApp = c.usageReader.foregroundMillis(from, to)
            for (pkg in tracked) {
                val ms = perApp[pkg] ?: 0L
                if (ms > 0L) rows += DailyUsage(day.toEpochDay(), pkg, ms)
                if (offset == 0L) todayTotal += ms
            }
        }
        c.stats.upsertUsage(rows)

        val s = c.settings.current()
        val warnMs = s.usageWarnMinutes * 60_000L
        if (s.notifyUsageWarning && todayTotal >= warnMs && s.lastUsageWarnDay != today.toEpochDay()) {
            c.settings.update { it.copy(lastUsageWarnDay = today.toEpochDay()) }
            c.notifier.usageWarning((todayTotal / 60_000L).toInt())
        }
    }

    private suspend fun trackedPackages(): Set<String> {
        val c = applicationContext.container
        val out = HashSet<String>()
        c.rules.getBlockedApps().forEach { out += it.packageName }
        c.rules.getFeedRules().filter { it.enabled }.forEach { out += it.packageName }
        c.rules.getGroups().forEach { out += it.members }
        return out
    }

    companion object {
        const val NAME = "usage_snapshot"
    }
}
