package com.focuslock.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuslock.app.domain.PermissionState
import com.focuslock.app.domain.ServiceHealth
import com.focuslock.app.system.SettingsIntents
import com.focuslock.app.system.openSettings
import com.focuslock.app.ui.common.InfoCard
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.theme.Purple

@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = Purple, modifier = Modifier.height(72.dp))
        Spacer(Modifier.height(12.dp))
        Text("FocusLock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

private enum class Step { WELCOME, ACCESSIBILITY, USAGE, NOTIFICATIONS, BACKGROUND, DONE }

@Composable
fun OnboardingScreen(permissions: PermissionState, onFinish: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val steps = Step.entries
    val step = steps[index]
    val context = LocalContext.current

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* status is polled */ }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        LinearProgressIndicator(
            progress = { (index + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                Step.WELCOME -> {
                    Text("Welcome to FocusLock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "FocusLock helps you block distracting apps and short-video feeds such as YouTube Shorts and " +
                            "Instagram Reels, using focus sessions and schedules you control.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    InfoCard {
                        Text("What to expect", fontWeight = FontWeight.SemiBold)
                        Text("• Blocking needs one special permission (Accessibility), which you turn on yourself.")
                        Text("• Everything stays on this phone. FocusLock does not ask for internet access.")
                        Text("• No app can make blocking unbreakable. You can always switch FocusLock off in Android settings, and we will tell you if blocking stops working.")
                    }
                }
                Step.ACCESSIBILITY -> PermissionStep(
                    title = "Accessibility access (needed for blocking)",
                    why = "Android only tells an app which screen is in front through its Accessibility API. " +
                        "FocusLock uses it to notice when a blocked app opens and, for feed rules, whether a short-video " +
                        "feed is on screen. It does not read messages or passwords, take screenshots or send anything off your phone.",
                    granted = permissions.accessibility == ServiceHealth.CONNECTED,
                    statusText = when (permissions.accessibility) {
                        ServiceHealth.CONNECTED -> "On and running"
                        ServiceHealth.ENABLED_NOT_CONNECTED -> "Enabled but not running yet. Turn it off and on again."
                        ServiceHealth.DISABLED -> "Off"
                    },
                    actionLabel = "Open Accessibility settings",
                    onAction = { context.openSettings(SettingsIntents.accessibility()) },
                    extra = {
                        Text(
                            "Steps: Installed apps (or Downloaded apps) → FocusLock → turn it on → confirm.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (Build.VERSION.SDK_INT >= 33) {
                            Text(
                                "If the switch is greyed out and says \"Restricted setting\" (common for apps installed from a file), " +
                                    "open FocusLock's App info, tap ⋮ and choose \"Allow restricted settings\", then try again.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = { context.openSettings(SettingsIntents.appDetails(context)) }) {
                                Text("Open FocusLock App info")
                            }
                        }
                    },
                )
                Step.USAGE -> PermissionStep(
                    title = "Usage access (optional, for statistics)",
                    why = "Lets FocusLock measure how long you spend in apps, so your dashboard shows real numbers. " +
                        "It cannot block anything by itself and FocusLock works without it; you just will not see usage time.",
                    granted = permissions.usageAccess,
                    statusText = if (permissions.usageAccess) "Granted" else "Not granted",
                    actionLabel = "Open Usage access settings",
                    onAction = { context.openSettings(SettingsIntents.usageAccess()) },
                )
                Step.NOTIFICATIONS -> PermissionStep(
                    title = "Notifications (optional)",
                    why = "Used for session start and finish, schedule changes, and a warning if blocking stops working. " +
                        "You can choose which ones you get in Settings.",
                    granted = permissions.notifications,
                    statusText = if (permissions.notifications) "Allowed" else "Not allowed",
                    actionLabel = if (Build.VERSION.SDK_INT >= 33) "Allow notifications" else "Open notification settings",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.openSettings(SettingsIntents.notifications(context))
                    },
                )
                Step.BACKGROUND -> PermissionStep(
                    title = "Background running (recommended)",
                    why = "Some phone makers stop background apps aggressively, which can switch blocking off. " +
                        "Setting FocusLock to \"Unrestricted\" battery use makes that less likely. After a restart FocusLock " +
                        "recovers its schedules and sessions automatically.",
                    granted = permissions.batteryUnrestricted,
                    statusText = if (permissions.batteryUnrestricted) "Unrestricted" else "Default battery limits",
                    actionLabel = "Open battery settings",
                    onAction = { context.openSettings(SettingsIntents.batteryOptimization()) },
                )
                Step.DONE -> {
                    Text("You are set up", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    SummaryRow("Blocking (Accessibility)", permissions.accessibility == ServiceHealth.CONNECTED)
                    SummaryRow("Usage statistics", permissions.usageAccess)
                    SummaryRow("Notifications", permissions.notifications)
                    SummaryRow("Background running", permissions.batteryUnrestricted)
                    if (permissions.accessibility != ServiceHealth.CONNECTED) {
                        Text(
                            "Blocking is NOT active yet. You can finish now and enable Accessibility later from the Home screen " +
                                "or Settings → Permissions.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (index > 0) TextButton(onClick = { index-- }) { Text("Back") } else Spacer(Modifier.height(1.dp))
            if (step == Step.DONE) {
                Button(onClick = onFinish) { Text("Start using FocusLock") }
            } else {
                Button(onClick = { index++ }) {
                    val skip = when (step) {
                        Step.ACCESSIBILITY -> permissions.accessibility != ServiceHealth.CONNECTED
                        Step.USAGE -> !permissions.usageAccess
                        Step.NOTIFICATIONS -> !permissions.notifications
                        Step.BACKGROUND -> !permissions.batteryUnrestricted
                        else -> false
                    }
                    Text(if (skip) "Skip for now" else "Next")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (ok) "Done" else "Not done",
            tint = if (ok) Purple else MaterialTheme.colorScheme.outline,
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PermissionStep(
    title: String,
    why: String,
    granted: Boolean,
    statusText: String,
    actionLabel: String,
    onAction: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(why, style = MaterialTheme.typography.bodyLarge)
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (granted) Purple else MaterialTheme.colorScheme.outline,
            )
            Text("Status: $statusText", fontWeight = FontWeight.SemiBold)
        }
        if (!granted) Button(onClick = onAction) { Text(actionLabel) }
    }
    extra()
}

// ---------------------------------------------------------------------------------------------
// Permission center / recovery
// ---------------------------------------------------------------------------------------------

@Composable
fun PermissionCenterScreen(permissions: PermissionState, onBack: () -> Unit, onRecovery: () -> Unit) {
    val context = LocalContext.current
    ScreenScaffold("Permissions", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionLine(
                "Accessibility (blocking)",
                when (permissions.accessibility) {
                    ServiceHealth.CONNECTED -> "On and running"
                    ServiceHealth.ENABLED_NOT_CONNECTED -> "Enabled but not running"
                    ServiceHealth.DISABLED -> "Off. App and feed blocking will NOT work."
                },
                permissions.accessibility == ServiceHealth.CONNECTED,
                "Fix",
            ) { context.openSettings(SettingsIntents.accessibility()) }
            PermissionLine(
                "Usage access (statistics)",
                if (permissions.usageAccess) "Granted" else "Off. Usage time and the usage warning are unavailable.",
                permissions.usageAccess, "Grant",
            ) { context.openSettings(SettingsIntents.usageAccess()) }
            PermissionLine(
                "Notifications",
                if (permissions.notifications) "Allowed" else "Off. You will not get session or failure alerts.",
                permissions.notifications, "Open",
            ) { context.openSettings(SettingsIntents.notifications(context)) }
            PermissionLine(
                "Unrestricted battery",
                if (permissions.batteryUnrestricted) "Unrestricted" else "Default limits (blocking can be interrupted on some phones).",
                permissions.batteryUnrestricted, "Open",
            ) { context.openSettings(SettingsIntents.batteryOptimization()) }
            OutlinedButton(onClick = onRecovery, Modifier.fillMaxWidth()) { Text("Blocking not working? Recovery guide") }
        }
    }
}

@Composable
private fun PermissionLine(title: String, status: String, ok: Boolean, action: String, onAction: () -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (ok) "OK" else "Needs attention",
                tint = if (ok) Purple else MaterialTheme.colorScheme.error,
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!ok) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun RecoveryScreen(permissions: PermissionState, onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenScaffold("Recovery guide", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "If an app is not being blocked, or you are stuck and need to get out, work through these steps.",
                style = MaterialTheme.typography.bodyLarge,
            )
            InfoCard {
                Text("Blocking is not working", fontWeight = FontWeight.SemiBold)
                Text("1. Check Accessibility below. It must say \"On and running\".")
                Text("2. If it says \"Enabled but not running\": switch FocusLock off and on in Accessibility settings.")
                Text("3. If the switch is greyed out: open FocusLock App info → ⋮ → Allow restricted settings.")
                Text("4. Set battery use to Unrestricted, then restart the phone if it still fails.")
                Text("5. Some phones (Xiaomi, Huawei, Oppo, Vivo, Samsung power saving) also need \"Autostart\" or \"Protected apps\" turned on for FocusLock.")
                Text(
                    "Current status: " + when (permissions.accessibility) {
                        ServiceHealth.CONNECTED -> "running"
                        ServiceHealth.ENABLED_NOT_CONNECTED -> "enabled, not running"
                        ServiceHealth.DISABLED -> "off"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = { context.openSettings(SettingsIntents.accessibility()) }) { Text("Open Accessibility settings") }
            }
            if (SettingsIntents.isXiaomiFamily()) {
                InfoCard {
                    Text("Xiaomi / Redmi / POCO (HyperOS, MIUI)", fontWeight = FontWeight.SemiBold)
                    Text("These phones stop background apps and hide block screens unless you allow all of the following:")
                    Text("1. Autostart: ON for FocusLock.")
                    Button(onClick = { context.openSettings(SettingsIntents.xiaomiAutostart()) }) { Text("Open Autostart") }
                    Text("2. Other permissions → \"Display pop-up windows while running in the background\": Allow. Without it the block screen cannot appear over other apps.")
                    Button(onClick = { context.openSettings(SettingsIntents.xiaomiOtherPermissions(context)) }) { Text("Open FocusLock permissions") }
                    Text("3. Battery saver: No restrictions (App info → Battery saver).")
                    Text("4. Recent apps: press and hold the FocusLock card and tap the lock icon so it is not cleaned.")
                    Text("5. After the phone kills FocusLock, switch it off and on again in Accessibility.")
                    Button(onClick = { context.openSettings(SettingsIntents.appDetails(context)) }) { Text("Open App info") }
                }
            }
            InfoCard {
                Text("I am locked out of an app I need", fontWeight = FontWeight.SemiBold)
                Text("• Use \"Emergency access\" on the block screen (unless the session is strict).")
                Text("• Phone, Settings, the keyboard, your home launcher and FocusLock are never blocked.")
                Text("• Turn off blocking for everything with the master switch on the Home screen (not available during a strict session).")
                Text("• A strict session always ends by itself when its time is up.")
                Text("• As a last resort, you can switch FocusLock off in Android Settings → Accessibility, or uninstall it. FocusLock never tries to prevent this.")
            }
            InfoCard {
                Text("Something is wrong with my rules", fontWeight = FontWeight.SemiBold)
                Text("Settings → Backup & restore lets you restore a saved backup. Settings → Reset application settings returns settings to defaults without deleting your rules.")
            }
        }
    }
}
