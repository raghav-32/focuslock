package com.focuslock.app.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuslock.app.domain.BlockScope
import com.focuslock.app.domain.FeedRule
import com.focuslock.app.ui.FeedsViewModel
import com.focuslock.app.ui.common.ConfirmDialog
import com.focuslock.app.ui.common.InfoCard
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.common.WarningBanner
import com.focuslock.app.ui.common.containerViewModel

@Composable
fun FeedsScreen(onBack: () -> Unit, onPermissions: () -> Unit) {
    val vm = containerViewModel { FeedsViewModel(it) }
    val rules by vm.rules.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<FeedRule?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    ScreenScaffold("Feed rules", onBack) { pad ->
        androidx.compose.foundation.layout.Box(Modifier.padding(pad).fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Block a distracting area inside an app (like YouTube Shorts) while leaving the rest of the app usable.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                InfoCard {
                    Text("How reliable is this?", fontWeight = FontWeight.SemiBold)
                    Text(
                        "FocusLock recognises a feed by technical names that the app exposes to Android's accessibility system. " +
                            "Apps change these names in updates, some do not expose them at all, and FocusLock cannot guarantee detection. " +
                            "The starting rules below have NOT been verified against your installed app version: test them, edit the " +
                            "names if needed, and switch on \"Block the whole app instead\" if a feed cannot be detected reliably.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (!vm.serviceReady) {
                    WarningBanner(
                        "Accessibility is off",
                        "Feed rules need the accessibility service. Nothing is detected until it is running.",
                        "Open permissions",
                        onPermissions,
                    )
                }
                rules.forEach { rule ->
                    FeedRuleCard(
                        rule = rule,
                        installed = vm.isInstalled(rule.packageName),
                        version = vm.installedVersion(rule.packageName),
                        onChange = vm::save,
                        onEdit = { editing = rule },
                    )
                }
                OutlinedButton(onClick = { confirmReset = true }) { Text("Restore default rules") }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    editing?.let { rule ->
        EditRuleDialog(rule, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null })
    }
    if (confirmReset) {
        ConfirmDialog(
            "Restore default feed rules?",
            "Your edits and on/off choices for feed rules will be replaced by the defaults (all off).",
            "Restore", destructive = true,
            onConfirm = { confirmReset = false; vm.resetDefaults() },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun FeedRuleCard(rule: FeedRule, installed: Boolean, version: Long, onChange: (FeedRule) -> Unit, onEdit: () -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (installed) "App installed" + if (version > 0) " (version code $version)" else "" else "App not installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = { onChange(rule.copy(enabled = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = rule.scope == BlockScope.ALWAYS,
                onCheckedChange = { onChange(rule.copy(scope = if (it) BlockScope.ALWAYS else BlockScope.SESSIONS_ONLY)) },
            )
            Text("Block always (otherwise only during sessions and schedules)", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rule.fallbackWholeApp, onCheckedChange = { onChange(rule.copy(fallbackWholeApp = it)) })
            Text("Block the whole app instead (fallback if detection is unreliable)", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onEdit) { Text("Edit detection names") }
    }
}

@Composable
private fun EditRuleDialog(rule: FeedRule, onDismiss: () -> Unit, onSave: (FeedRule) -> Unit) {
    var ids by remember { mutableStateOf(rule.idTokens.joinToString("\n")) }
    var texts by remember { mutableStateOf(rule.textTokens.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("One per line. A feed counts as detected when any name matches.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(ids, { ids = it }, label = { Text("View id contains") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    texts, { texts = it },
                    label = { Text("Text / description contains (optional)") },
                    supportingText = { Text("Text matches can fire on menu labels too, so use sparingly.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    rule.copy(
                        idTokens = ids.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(30),
                        textTokens = texts.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(30),
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
