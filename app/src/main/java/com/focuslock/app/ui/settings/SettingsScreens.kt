package com.focuslock.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import com.focuslock.app.BuildConfig
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.BypassMode
import com.focuslock.app.domain.PermissionState
import com.focuslock.app.domain.ServiceHealth
import com.focuslock.app.domain.ThemeMode
import com.focuslock.app.system.SettingsIntents
import com.focuslock.app.system.openSettings
import com.focuslock.app.ui.common.ConfirmDialog
import com.focuslock.app.ui.common.InfoCard
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.common.SectionTitle
import com.focuslock.app.ui.common.containerViewModel

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- hub ----------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    permissions: PermissionState,
    strictLocked: Boolean,
    onBack: () -> Unit,
    onNotifications: () -> Unit,
    onBypass: () -> Unit,
    onPermissions: () -> Unit,
    onPrivacy: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onRecovery: () -> Unit,
    onGroups: () -> Unit,
) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    val groups by vm.groups.collectAsState()
    var confirmResetSettings by remember { mutableStateOf(false) }
    var confirmResetStats by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    ScreenScaffold("Settings", onBack) { pad ->
        androidx.compose.foundation.layout.Box(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle("Status")
                InfoCard {
                    val a11y = when (permissions.accessibility) {
                        ServiceHealth.CONNECTED -> "Accessibility service: on and running"
                        ServiceHealth.ENABLED_NOT_CONNECTED -> "Accessibility service: enabled but not running"
                        ServiceHealth.DISABLED -> "Accessibility service: OFF (blocking does not work)"
                    }
                    Text(a11y, fontWeight = FontWeight.SemiBold)
                    Text(if (permissions.usageAccess) "Usage access: granted" else "Usage access: off (usage time unavailable)")
                    NavRow("Permissions", "Review and fix permissions", onPermissions)
                    NavRow("Recovery guide", "Blocking not working, or locked out?", onRecovery)
                }

                SectionTitle("Appearance")
                InfoCard {
                    Text("Theme")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(
                                selected = s.themeMode == m,
                                onClick = { vm.update { it.copy(themeMode = m) } },
                                label = { Text(m.name.lowercase().replaceFirstChar(Char::titlecase)) },
                            )
                        }
                    }
                    Text("Language")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = s.language.isEmpty(), onClick = { vm.setLanguage("") }, label = { Text("System") })
                        FilterChip(selected = s.language == "en", onClick = { vm.setLanguage("en") }, label = { Text("English") })
                    }
                    Text(
                        "Only English is translated so far. Strings live in resource files, so new languages can be added without code changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchRow("Haptic feedback", checked = s.haptics) { v -> vm.update { it.copy(haptics = v) } }
                }

                SectionTitle("Block screen message")
                InfoCard {
                    var text by remember(s.blockMessage) { mutableStateOf(s.blockMessage) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(240) },
                        label = { Text("Your message") },
                        supportingText = { Text("Shown on the block screen. Keep it kind.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.update { it.copy(blockMessage = text.ifBlank { AppSettings.DEFAULT_BLOCK_MESSAGE }) } }) { Text("Save") }
                        OutlinedButton(onClick = { vm.update { it.copy(blockMessage = AppSettings.DEFAULT_BLOCK_MESSAGE) } }) { Text("Use default") }
                    }
                }

                SectionTitle("Focus defaults")
                InfoCard {
                    Text("Default duration: ${s.defaultFocusMinutes} min")
                    Slider(
                        value = s.defaultFocusMinutes.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(defaultFocusMinutes = (v / 5).toInt().coerceAtLeast(1) * 5) } },
                        valueRange = 5f..180f,
                    )
                    Text("Default group for focus sessions")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = s.defaultGroupId <= 0, onClick = { vm.update { it.copy(defaultGroupId = -1L) } }, label = { Text("My blocked list") })
                        groups.forEach { g ->
                            FilterChip(selected = s.defaultGroupId == g.id, onClick = { vm.update { it.copy(defaultGroupId = g.id) } }, label = { Text(g.name) })
                        }
                    }
                    NavRow("Manage groups & presets", null, onGroups)
                    Text("Minutes saved per blocked attempt: ${s.minutesSavedPerBlock}")
                    Text(
                        "Used only for the \"estimated time saved\" figure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = s.minutesSavedPerBlock.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(minutesSavedPerBlock = v.toInt().coerceIn(0, 30)) } },
                        valueRange = 0f..30f,
                    )
                }

                SectionTitle("More")
                InfoCard {
                    NavRow("Notifications", "Choose which alerts you get", onNotifications)
                    NavRow("Emergency access", "Bypass rules for the block screen", onBypass)
                    NavRow("Privacy", "What FocusLock collects and how to delete it", onPrivacy)
                    NavRow("Backup & restore", "Export or import rules, export statistics", onBackup)
                    NavRow("About", "Version and how blocking works", onAbout)
                }

                SectionTitle("Reset")
                InfoCard {
                    OutlinedButton(onClick = { confirmResetStats = true }, enabled = !strictLocked) { Text("Reset statistics") }
                    OutlinedButton(onClick = { confirmResetSettings = true }, enabled = !strictLocked) { Text("Reset application settings") }
                    if (strictLocked) Text("Unavailable during a strict session.", style = MaterialTheme.typography.bodySmall)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (confirmResetStats) {
        ConfirmDialog(
            "Delete all statistics?",
            "Block events, usage summaries and focus-session history will be permanently deleted from this phone. Your rules are kept.",
            "Delete statistics", true,
            onConfirm = { confirmResetStats = false; vm.resetStatistics() },
            onDismiss = { confirmResetStats = false },
        )
    }
    if (confirmResetSettings) {
        ConfirmDialog(
            "Reset settings?",
            "Theme, messages, notification and bypass preferences return to defaults. Your blocked apps, schedules and groups are kept.",
            "Reset settings", true,
            onConfirm = { confirmResetSettings = false; vm.resetSettings() },
            onDismiss = { confirmResetSettings = false },
        )
    }
}

// ---- notifications ------------------------------------------------------------------------------

@Composable
fun NotificationSettingsScreen(permissions: PermissionState, onBack: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    ScreenScaffold("Notifications", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!permissions.notifications) {
                InfoCard {
                    Text("Notifications are turned off for FocusLock in Android, so none of these will appear.")
                    Button(onClick = { context.openSettings(SettingsIntents.notifications(context)) }) { Text("Open notification settings") }
                }
            }
            InfoCard {
                SwitchRow("Focus session started", checked = s.notifySessionStart) { v -> vm.update { it.copy(notifySessionStart = v) } }
                SwitchRow("Focus session completed", checked = s.notifySessionEnd) { v -> vm.update { it.copy(notifySessionEnd = v) } }
                SwitchRow("Scheduled blocking activated", checked = s.notifyScheduleStart) { v -> vm.update { it.copy(notifyScheduleStart = v) } }
                SwitchRow("Scheduled blocking ended", checked = s.notifyScheduleEnd) { v -> vm.update { it.copy(notifyScheduleEnd = v) } }
                SwitchRow("Permission or service problem", "Recommended: tells you when blocking stops working", s.notifyServiceFailure) { v ->
                    vm.update { it.copy(notifyServiceFailure = v) }
                }
                SwitchRow("Excessive usage warning", "At most once per day. Needs Usage access.", s.notifyUsageWarning) { v ->
                    vm.update { it.copy(notifyUsageWarning = v) }
                }
                if (s.notifyUsageWarning) {
                    Text("Warn after ${s.usageWarnMinutes} min in tracked apps")
                    Slider(
                        value = s.usageWarnMinutes.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(usageWarnMinutes = (v / 15).toInt().coerceAtLeast(1) * 15) } },
                        valueRange = 15f..360f,
                    )
                }
            }
        }
    }
}

// ---- emergency bypass ---------------------------------------------------------------------------

@Composable
fun BypassSettingsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    val installed by vm.installed.collectAsState()
    ScreenScaffold("Emergency access", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Decide how hard it is to open a blocked app from the block screen. Strict focus sessions always switch emergency access off.",
                style = MaterialTheme.typography.bodyLarge,
            )
            InfoCard {
                Text("Mode", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        BypassMode.NONE to "No bypass",
                        BypassMode.CONFIRM to "Confirm",
                        BypassMode.COUNTDOWN to "Wait, then open",
                    ).forEach { (m, label) ->
                        FilterChip(selected = s.bypassMode == m, onClick = { vm.update { it.copy(bypassMode = m) } }, label = { Text(label) })
                    }
                }
                if (s.bypassMode != BypassMode.NONE) {
                    Text("Access lasts ${s.bypassMinutes} min")
                    Slider(
                        value = s.bypassMinutes.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(bypassMinutes = v.toInt().coerceIn(1, 30)) } },
                        valueRange = 1f..30f,
                    )
                    Text("Cooldown before the next bypass: ${s.reopenCooldownMinutes} min")
                    Slider(
                        value = s.reopenCooldownMinutes.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(reopenCooldownMinutes = v.toInt().coerceIn(0, 120)) } },
                        valueRange = 0f..120f,
                    )
                }
                if (s.bypassMode == BypassMode.COUNTDOWN) {
                    Text("Wait ${s.countdownSeconds} s before opening")
                    Slider(
                        value = s.countdownSeconds.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(countdownSeconds = v.toInt().coerceIn(5, 120)) } },
                        valueRange = 5f..120f,
                    )
                }
            }
            SectionTitle("Always-allowed apps")
            InfoCard {
                Text(
                    "These apps are never blocked, even during sessions (for example a banking, maps or messaging app you may need in an emergency).",
                    style = MaterialTheme.typography.bodySmall,
                )
                var q by remember { mutableStateOf("") }
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
                val list = installed.filter { q.isNotBlank() && it.label.contains(q, true) }.take(8)
                list.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.update { it.copy(emergencyApps = it.emergencyApps + app.packageName) } }.padding(8.dp),
                    ) { Text("+ ${app.label}") }
                }
                val labels = installed.associate { it.packageName to it.label }
                s.emergencyApps.sorted().forEach { pkg ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(labels[pkg] ?: pkg, Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { vm.update { it.copy(emergencyApps = it.emergencyApps - pkg) } }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

// ---- privacy / about ----------------------------------------------------------------------------

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold("Privacy", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard {
                Text("What FocusLock stores on this phone", fontWeight = FontWeight.SemiBold)
                Text("• Your rules: blocked apps, feed rules, groups and schedules.")
                Text("• Block events: time, app, which rule fired. Never screen content.")
                Text("• Focus sessions: start time, length, strict or not.")
                Text("• Daily usage totals per tracked app, if you grant Usage access.")
                Text("• Your settings and block-screen message.")
            }
            InfoCard {
                Text("What the accessibility service looks at", fontWeight = FontWeight.SemiBold)
                Text("• Which app is in the foreground (its package name).")
                Text("• Only for apps where you switched on a feed rule: the technical view names (and, if you add text names, short labels) on screen, checked in memory and discarded straight away.")
                Text("• It does not read messages, passwords or typed text, does not take screenshots, and does not record what you do.")
            }
            InfoCard {
                Text("Where data goes", fontWeight = FontWeight.SemiBold)
                Text(
                    "FocusLock does not request internet permission, so it cannot send data anywhere. It has no analytics and no accounts. " +
                        "Data leaves the phone only if YOU export a backup or statistics file and share it. Automatic Android cloud backup is switched off for this app.",
                )
                Text(
                    "This guarantee covers FocusLock's own code. Other apps and Android itself may handle your data under their own policies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InfoCard {
                Text("How to delete it", fontWeight = FontWeight.SemiBold)
                Text("• Settings → Reset statistics deletes events, usage and session history.")
                Text("• Android Settings → Apps → FocusLock → Storage → Clear data deletes everything.")
                Text("• Uninstalling FocusLock removes all of it.")
                Text("• Turn off the accessibility service any time in Android Settings → Accessibility.")
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold("About", onBack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard {
                Text("FocusLock — App & Feed Blocker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("Build date ${BuildConfig.BUILD_DATE}")
                Text(BuildConfig.APPLICATION_ID, style = MaterialTheme.typography.bodySmall)
            }
            InfoCard {
                Text("How blocking works", fontWeight = FontWeight.SemiBold)
                Text(
                    "FocusLock's accessibility service is told when a window changes. If the app that just opened matches an active rule, " +
                        "FocusLock shows its block screen on top. Usage access is only used to measure time, never to block.",
                )
                Text("Rules are checked against the current time on every event, so blocking does not depend on alarms firing exactly on time.")
            }
            InfoCard {
                Text("Known limits", fontWeight = FontWeight.SemiBold)
                Text("• Blocking stops if you disable the service, revoke the permission, or force-stop FocusLock. FocusLock warns you but cannot prevent this.")
                Text("• Feed (Shorts / Reels) detection depends on what each app exposes to accessibility and can break after app updates.")
                Text("• Some phone makers restrict background apps; set battery use to Unrestricted.")
                Text("• Phone, Settings, keyboards and your home launcher are never blocked.")
            }
            InfoCard {
                Text("Rule priority", fontWeight = FontWeight.SemiBold)
                Text(
                    "Never-blocked and emergency-allowed apps win. Then a running bypass (not in strict sessions). Otherwise an app is blocked if any rule applies; " +
                        "the reason shown is focus session, then schedule, then always-blocked list. Whole-app blocks beat feed rules.",
                )
            }
        }
    }
}

// ---- backup -------------------------------------------------------------------------------------

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportStatsCsv(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    ScreenScaffold("Backup & restore", onBack) { pad ->
        androidx.compose.foundation.layout.Box(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard {
                    Text("Backup", fontWeight = FontWeight.SemiBold)
                    Text("Saves your rules, groups, schedules, block events and usage totals to a file you choose.")
                    Button(onClick = { exportBackup.launch("focuslock-backup.json") }) { Text("Export backup") }
                }
                InfoCard {
                    Text("Restore", fontWeight = FontWeight.SemiBold)
                    Text("The file is checked first. If anything looks wrong, nothing is changed. A restore REPLACES your current rules.")
                    OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import backup") }
                }
                InfoCard {
                    Text("Statistics export", fontWeight = FontWeight.SemiBold)
                    Text("A spreadsheet-friendly CSV of block events and measured usage.")
                    OutlinedButton(onClick = { exportCsv.launch("focuslock-stats.csv") }) { Text("Export statistics (CSV)") }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    confirmImport?.let { uri ->
        ConfirmDialog(
            "Replace your rules with this backup?",
            "Your current blocked apps, feed rules, groups, schedules and history will be replaced if the file is valid.",
            "Restore", true,
            onConfirm = { confirmImport = null; vm.importBackup(uri) },
            onDismiss = { confirmImport = null },
        )
    }
}
