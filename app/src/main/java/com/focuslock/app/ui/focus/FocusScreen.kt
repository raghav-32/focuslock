package com.focuslock.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focuslock.app.domain.FocusSession
import com.focuslock.app.domain.formatDuration
import com.focuslock.app.ui.FocusViewModel
import com.focuslock.app.ui.common.ConfirmDialog
import com.focuslock.app.ui.common.InfoCard
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.common.SectionTitle
import com.focuslock.app.ui.common.containerViewModel
import com.focuslock.app.ui.common.rememberNow

private val PRESETS = listOf(5, 15, 25, 45, 60, 90, 120)

private fun presetLabel(min: Int) = if (min >= 120) "${min / 60} hours" else "$min min"

@Composable
fun FocusScreen(onEditApps: () -> Unit) {
    val vm = containerViewModel { FocusViewModel(it) }
    val active by vm.active.collectAsState()
    val settings by vm.settings.collectAsState()
    val groups by vm.groups.collectAsState()
    val ruleCount by vm.blockedCount.collectAsState()
    val message by vm.message.collectAsState()
    val now by rememberNow(1_000L)
    val snackbar = remember { SnackbarHostState() }

    var minutes by remember(settings.defaultFocusMinutes) { mutableStateOf(settings.defaultFocusMinutes) }
    var custom by remember { mutableStateOf("") }
    var strict by remember { mutableStateOf(false) }
    var groupId by remember(settings.defaultGroupId) { mutableStateOf(settings.defaultGroupId.takeIf { it > 0L }) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmStrict by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val session = active
    val remaining = remember(session, now) { session?.remainingMillis(vm.timeSource()) ?: 0L }
    // When the countdown hits zero while the screen is open, close the session and notify once.
    LaunchedEffect(session?.id, remaining <= 0L) {
        if (session != null && remaining <= 0L) vm.reconcile()
    }

    ScreenScaffold(if (session != null && remaining > 0) "Focus session" else "Start a focus session") { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (session != null && remaining > 0) {
                ActiveSession(session, remaining, onCancel = { confirmCancel = true })
            } else {
                SectionTitle("Duration")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { m ->
                        FilterChip(selected = custom.isBlank() && minutes == m, onClick = { minutes = m; custom = "" }, label = { Text(presetLabel(m)) })
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { v -> custom = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Custom (minutes)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val chosen = custom.toIntOrNull()?.coerceIn(1, 1440) ?: minutes

                SectionTitle("What to block")
                InfoCard {
                    Text(
                        if (groupId == null) "Your blocked-apps list and enabled feed rules ($ruleCount)." else "Only the selected group.",
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = groupId == null, onClick = { groupId = null }, label = { Text("My blocked list") })
                        groups.forEach { g ->
                            FilterChip(selected = groupId == g.id, onClick = { groupId = g.id }, label = { Text("${g.name} (${g.members.size})") })
                        }
                    }
                    if (groupId == null && ruleCount == 0) {
                        Text(
                            "Nothing is selected to block yet, so this session would not block anything.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onEditApps) { Text("Choose apps to block") }
                    }
                }

                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Strict session", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Hides the cancel button, switches off emergency access and stops rules being loosened until the timer ends.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = strict, onCheckedChange = { strict = it })
                    }
                    if (strict) {
                        Text(
                            "Honest limit: Android lets you turn off FocusLock's accessibility access, force-stop it or uninstall it at any time. Strict mode only removes the easy ways out inside FocusLock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = { if (strict) confirmStrict = true else vm.start(chosen, false, groupId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start ${formatDuration(chosen * 60_000L)} session") }
            }
        }
        androidx.compose.foundation.layout.Box(Modifier.padding(pad).fillMaxSize()) {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "End this session early?",
            body = if (session?.strict == true) "This is a strict session, so it cannot be ended early from the app." else "Your blocks from this session will stop now.",
            confirmLabel = "End session",
            destructive = true,
            onConfirm = { confirmCancel = false; vm.cancel() },
            onDismiss = { confirmCancel = false },
        )
    }
    if (confirmStrict) {
        val chosen = custom.toIntOrNull()?.coerceIn(1, 1440) ?: minutes
        ConfirmDialog(
            title = "Start a strict session?",
            body = "You will not be able to cancel it from the app for ${formatDuration(chosen * 60_000L)}.",
            confirmLabel = "Start strict session",
            onConfirm = { confirmStrict = false; vm.start(chosen, true, groupId) },
            onDismiss = { confirmStrict = false },
        )
    }
}

@Composable
private fun ActiveSession(session: FocusSession, remaining: Long, onCancel: () -> Unit) {
    InfoCard {
        Text(formatClock(remaining), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text(
            "of ${formatDuration(session.plannedMillis)}" + if (session.strict) " · strict" else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { 1f - (remaining.toFloat() / session.plannedMillis).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Stay with your original intention. Blocking stays active until the timer ends.")
    }
    if (session.strict) {
        Text(
            "Strict session: cancelling and loosening rules is turned off until the timer ends.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        OutlinedButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text("End session early") }
    }
}

internal fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
