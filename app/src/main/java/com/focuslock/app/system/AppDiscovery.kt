package com.focuslock.app.system

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.pm.PackageInfoCompat
import com.focuslock.app.domain.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Essentials(
    val neverBlock: Set<String>,
    val ignoredForForeground: Set<String>,
)

/**
 * App discovery uses the launcher-intent <queries> declaration (see manifest) instead of the broad
 * QUERY_ALL_PACKAGES permission: we only ever need apps the user can actually open.
 */
class AppDiscovery(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    @Suppress("DEPRECATION")
    private fun queryActivities(intent: Intent) =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }

    /** Launchable apps minus the essential set (launcher, dialer, settings, keyboard, ourselves). */
    suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val essential = essentials().neverBlock
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        queryActivities(launcher)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName !in essential }
            .map { ai ->
                InstalledApp(
                    packageName = ai.packageName,
                    label = ai.loadLabel(pm).toString(),
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    category = ai.category,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Packages that must never be blocked, so the user can always reach calls, settings and home. */
    fun essentials(): Essentials {
        val never = HashSet<String>(STATIC_NEVER_BLOCK)
        never += context.packageName

        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            queryActivities(home).forEach { ri -> ri.activityInfo?.packageName?.let { never += it } }
        }
        runCatching {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
                ?.defaultDialerPackage?.let { never += it }
        }
        val ime = HashSet<String>()
        runCatching {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.enabledInputMethodList?.forEach { ime += it.packageName }
        }
        never += ime
        val ignored = HashSet<String>(ime).apply { add("com.android.systemui"); add("android") }
        return Essentials(never, ignored)
    }

    fun labelOf(packageName: String): String = try {
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    fun launchIntentFor(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    /** Installed version code, or 0 if unknown (rules then apply regardless of version). */
    fun versionCode(packageName: String): Long = try {
        @Suppress("DEPRECATION")
        PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(packageName, 0))
    } catch (e: Exception) {
        0L
    }

    companion object {
        val STATIC_NEVER_BLOCK: Set<String> = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.emergency",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )
    }
}
