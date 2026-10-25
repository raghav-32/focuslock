package com.focuslock.app.system

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focuslock.app.domain.PermissionState
import com.focuslock.app.domain.ServiceHealth

/** In-process truth about whether the accessibility service is bound right now. */
object ServiceStatus {
    @Volatile
    var connected: Boolean = false
}

class PermissionChecker(private val context: Context) {

    fun accessibilityHealth(): ServiceHealth {
        if (ServiceStatus.connected) return ServiceHealth.CONNECTED
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        val expected = ComponentName(context, FocusLockAccessibilityService::class.java)
        val listed = !enabled.isNullOrEmpty() &&
            enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        return if (listed) ServiceHealth.ENABLED_NOT_CONNECTED else ServiceHealth.DISABLED
    }

    @Suppress("DEPRECATION")
    fun usageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun batteryUnrestricted(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun state(): PermissionState = PermissionState(
        accessibility = accessibilityHealth(),
        usageAccess = usageAccessGranted(),
        notifications = notificationsAllowed(),
        batteryUnrestricted = batteryUnrestricted(),
    )
}

/** Supported system-settings intents used by onboarding, recovery and settings screens. */
object SettingsIntents {
    fun accessibility() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun usageAccess() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    fun batteryOptimization() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun notifications(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Needed on Android 13+ for sideloaded builds: "Allow restricted settings" lives here. */
    fun appDetails(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
}

fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: ActivityNotFoundException) {
            // Nothing else we can do; the UI keeps showing the permission as missing.
        }
    }
}
