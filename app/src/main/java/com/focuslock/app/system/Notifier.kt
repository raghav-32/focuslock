package com.focuslock.app.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focuslock.app.MainActivity
import com.focuslock.app.R
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.SettingsRepository
import com.focuslock.app.domain.current

/**
 * Every notification type has its own user preference and ONE fixed id, so a repeated event
 * replaces the previous notification instead of stacking up.
 */
class Notifier(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_SESSION, "Focus sessions", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "When a focus session starts or finishes." },
                NotificationChannel(CH_SCHEDULE, "Scheduled blocking", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "When a blocking schedule becomes active or ends." },
                NotificationChannel(CH_WARNING, "Usage warnings", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "When time in your tracked apps passes your limit." },
                NotificationChannel(CH_SERVICE, "Permission and service problems", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "When blocking cannot work because a permission or service is off." },
            ),
        )
    }

    suspend fun sessionStarted(minutes: Int) =
        post(AppSettings::notifySessionStart, CH_SESSION, ID_SESSION_START, "Focus session started", "Stay with it for $minutes minutes.")

    suspend fun sessionCompleted() =
        post(AppSettings::notifySessionEnd, CH_SESSION, ID_SESSION_END, "Focus session complete", "Nice work. Your blocks from this session have ended.")

    suspend fun scheduleStarted(name: String) =
        post(AppSettings::notifyScheduleStart, CH_SCHEDULE, ID_SCHEDULE_START, "Blocking active: $name", "Your schedule has started.")

    suspend fun scheduleEnded(name: String) =
        post(AppSettings::notifyScheduleEnd, CH_SCHEDULE, ID_SCHEDULE_END, "Schedule ended: $name", "Blocking for this schedule has ended.")

    suspend fun usageWarning(minutes: Int) =
        post(AppSettings::notifyUsageWarning, CH_WARNING, ID_USAGE, "Time check", "You have spent about $minutes minutes in your tracked apps today.")

    suspend fun serviceFailure() =
        post(
            AppSettings::notifyServiceFailure, CH_SERVICE, ID_SERVICE,
            "FocusLock is not blocking",
            "Accessibility access is off, so your blocking rules are not being enforced. Tap to fix it.",
        )

    @SuppressLint("MissingPermission") // checked explicitly in canNotify()
    private suspend fun post(
        enabled: (AppSettings) -> Boolean,
        channel: String,
        id: Int,
        title: String,
        text: String,
    ) {
        if (!enabled(settings.current()) || !canNotify()) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private companion object {
        const val CH_SESSION = "session"
        const val CH_SCHEDULE = "schedule"
        const val CH_WARNING = "warning"
        const val CH_SERVICE = "service"
        const val ID_SESSION_START = 1001
        const val ID_SESSION_END = 1002
        const val ID_SCHEDULE_START = 1003
        const val ID_SCHEDULE_END = 1004
        const val ID_USAGE = 1005
        const val ID_SERVICE = 1006
    }
}
