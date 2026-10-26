package com.focuslock.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.focuslock.app.MainActivity
import com.focuslock.app.R

/**
 * Foreground service whose only job is to tell the phone that FocusLock is actively in use, so
 * aggressive task killers (Xiaomi/HyperOS, Oppo, Vivo, Huawei) are much less likely to stop the
 * process that hosts the blocking service. It does no work and reads nothing. It is started when the
 * accessibility service connects and stopped when that service stops.
 * This reduces, but cannot guarantee against, being killed: the user can still force-stop the app.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Blocking is active", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "Keeps FocusLock running so blocking keeps working. You can hide this notification in Android settings."
                        setShowBadge(false)
                    },
            )
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n: Notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("FocusLock is on")
                .setContentText("Blocking is running.")
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(open)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, n)
            }
        } catch (t: Throwable) {
            Diagnostics.logCrash(this, "KeepAliveService", t)
            stopSelf()
        }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "keepalive"
        private const val ID = 4001

        fun start(context: Context) {
            try {
                val i = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            } catch (t: Throwable) {
                Diagnostics.logCrash(context, "start KeepAliveService", t)
            }
        }

        fun stop(context: Context) {
            try { context.stopService(Intent(context, KeepAliveService::class.java)) } catch (_: Throwable) {}
        }
    }
}
