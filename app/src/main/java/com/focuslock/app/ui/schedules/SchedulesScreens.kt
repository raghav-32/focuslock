package com.focuslock.app.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import com.focuslock.app.domain.Days
import com.focuslock.app.domain.Schedule
import com.focuslock.app.domain.ScheduleEvaluator
import com.focuslock.app.ui.SchedulesViewModel
import com.focuslock.app.ui.common.ConfirmDialog
import com.focuslock.app.ui.common.EmptyState
import com.focuslock.app.ui.common.InfoCard
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.common.SectionTitle
import com.focuslock.app.ui.common.containerViewModel
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

private fun daysSummary(mask: Int): String = when (mask) {
    Days.ALL -> "Every day"
    Days.WEEKDAYS -> "Weekdays"
    Days.WEEKEND -> "Weekends"
    0 -> "No days"
    else -> DayOfWeek.entries.filter { Days.contains(mask, it) }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
fun SchedulesScreen(onEdit: (Long) -> Unit) {
    val vm = containerViewModel { SchedulesViewModel(it) }
    val schedules by vm.schedules.collectAsState()
    var deleting by remember { mutableStateOf<Schedule?>(null) }
    val now = ZonedDateTime.now()

    ScreenScaffold("Schedules") { pad ->
        androidx.compose.foundation.layout.Box(Modifier.padding(pad).fillMaxSize()) {
            if (schedules.isEmpty()) {
                EmptyState(
                    "No schedules yet",
                    "Create a schedule such as \"Study time 7:00 AM to 12:00 PM\" and FocusLock will block your chosen apps automatically.",
                    action = { Button(onClick = { onEdit(0L) }) { Text("Create a schedule") } },
                )
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    schedules.forEach { s ->
                        InfoCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${ScheduleEvaluator.formatMinute(s.startMinute)} – ${ScheduleEvaluator.formatMinute(s.endMinute)}" +
                                            if (s.startMinute > s.endMinute) " (overnight)" else "",
                                    )
                                    Text(daysSummary(s.daysMask), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val n = s.packages.size
                                    Text(
                                        "$n app(s), ${s.groupIds.size} group(s), ${s.feedRuleIds.size} feed(s)" +
                                            if (s.requireFocusSession) " · only with a focus session" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (ScheduleEvaluator.isActive(s, now)) {
                                        Text("Active now", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Switch(checked = s.enabled, onCheckedChange = { vm.setEnabled(s, it) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onEdit(s.id) }) { Text("Edit") }
                                TextButton(onClick = { deleting = s }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { onEdit(0L) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) { Icon(Icons.Filled.Add, contentDescription = "New schedule") }
            }
        }
    }
    deleting?.let { s ->
        ConfirmDialog(
            "Delete \"${s.name}\"?", "This schedule will stop blocking and cannot be recovered.", "Delete", true,
            onConfirm = { vm.delete(s.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(id: Long, onBack: () -> Unit) {
    val vm = containerViewModel { SchedulesViewModel(it) }
    val groups by vm.groups.collectAsState()
    val feeds by vm.feedRules.collectAsState()
    val installed by vm.installed.collectAsState()

    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(9 * 60) }
    var end by remember { mutableStateOf(17 * 60) }
    var days by remember { mutableStateOf(Days.WEEKDAYS) }
    var requireSession by remember { mutableStateOf(false) }
    var packages by remember { mutableStateOf(setOf<String>()) }
    var groupIds by remember { mutableStateOf(setOf<Long>()) }
    var feedIds by remember { mutableStateOf(setOf<String>()) }
    var exceptions by remember { mutableStateOf(setOf<String>()) }
    var enabled by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var pickApps by remember { mutableStateOf<String?>(null) } // "apps" | "exceptions"
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id != 0L) {
            vm.load(id)?.let { s ->
                name = s.name; start = s.startMinute; end = s.endMinute; days = s.daysMask
                requireSession = s.requireFocusSession; packages = s.packages; groupIds = s.groupIds
                feedIds = s.feedRuleIds; exceptions = s.exceptionPackages; enabled = s.enabled
            }
            loaded = true
        }
    }

    ScreenScaffold(if (id == 0L) "New schedule" else "Edit schedule", onBack) { pad ->
        if (!loaded) return@ScreenScaffold
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            SectionTitle("Time")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = "start" }, Modifier.weight(1f)) { Text("Start ${ScheduleEvaluator.formatMinute(start)}") }
                OutlinedButton(onClick = { picking = "end" }, Modifier.weight(1f)) { Text("End ${ScheduleEvaluator.formatMinute(end)}") }
            }
            if (start > end) Text("This schedule runs overnight and ends the next morning.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (start == end) Text("Start and end are the same, so this schedule would never block.", color = MaterialTheme.colorScheme.error)

            SectionTitle("Days")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(
                        selected = Days.contains(days, d),
                        onClick = { days = days xor Days.bit(d) },
                        label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }

            SectionTitle("What to block")
            OutlinedButton(onClick = { pickApps = "apps" }, Modifier.fillMaxWidth()) { Text("Apps (${packages.size} selected)") }
            if (groups.isNotEmpty()) {
                Text("Groups", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { g ->
                        FilterChip(
                            selected = g.id in groupIds,
                            onClick = { groupIds = if (g.id in groupIds) groupIds - g.id else groupIds + g.id },
                            label = { Text(g.name) },
                        )
                    }
                }
            }
            if (feeds.isNotEmpty()) {
                Text("Feeds", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    feeds.forEach { f ->
                        FilterChip(
                            selected = f.id in feedIds,
                            onClick = { feedIds = if (f.id in feedIds) feedIds - f.id else feedIds + f.id },
                            label = { Text(f.name) },
                        )
                    }
                }
                Text(
                    "A feed only blocks when its rule is also switched on in Feed rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { pickApps = "exceptions" }, Modifier.fillMaxWidth()) { Text("Exceptions (${exceptions.size} apps stay allowed)") }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Only while a focus session runs")
                    Text(
                        "Combine the schedule with a session instead of blocking on its own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(requireSession, { requireSession = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", Modifier.weight(1f))
                Switch(enabled, { enabled = it })
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    error = when {
                        name.isBlank() -> "Give the schedule a name."
                        days == 0 -> "Choose at least one day."
                        start == end -> "Start and end time must differ."
                        packages.isEmpty() && groupIds.isEmpty() && feedIds.isEmpty() -> "Choose at least one app, group or feed."
                        else -> null
                    }
                    if (error == null) {
                        vm.save(
                            Schedule(
                                id = id, name = name.trim(), startMinute = start, endMinute = end, daysMask = days, enabled = enabled,
                                packages = packages, groupIds = groupIds, feedRuleIds = feedIds, exceptionPackages = exceptions,
                                requireFocusSession = requireSession,
                            ),
                            onDone = onBack,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save schedule") }
        }
    }

    picking?.let { which ->
        val initial = if (which == "start") start else end
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = false)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (which == "start") "Start time" else "End time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    if (which == "start") start = m else end = m
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        )
    }

    pickApps?.let { which ->
        val current = if (which == "apps") packages else exceptions
        var selection by remember(which) { mutableStateOf(current) }
        var q by remember(which) { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickApps = null },
            title = { Text(if (which == "apps") "Apps to block" else "Apps that stay allowed") },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 300.dp)) {
                        val list = installed.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
                        items(list, key = { it.packageName }) { app ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = app.packageName in selection,
                                    onCheckedChange = {
                                        selection = if (app.packageName in selection) selection - app.packageName else selection + app.packageName
                                    },
                                )
                                Text(app.label, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (which == "apps") packages = selection else exceptions = selection
                    pickApps = null
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { pickApps = null }) { Text("Cancel") } },
        )
    }
}
