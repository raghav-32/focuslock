package com.focuslock.app.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuslock.app.domain.AppGroup
import com.focuslock.app.domain.Preset
import com.focuslock.app.ui.GroupsViewModel
import com.focuslock.app.ui.common.ConfirmDialog
import com.focuslock.app.ui.common.InfoCard
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.common.SectionTitle
import com.focuslock.app.ui.common.containerViewModel

@Composable
fun GroupsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { GroupsViewModel(it) }
    val groups by vm.groups.collectAsState()
    val installed by vm.installed.collectAsState()
    var preview by remember { mutableStateOf<Preset?>(null) }
    var editing by remember { mutableStateOf<AppGroup?>(null) } // id 0 = new
    var deleting by remember { mutableStateOf<AppGroup?>(null) }
    val labels = remember(installed) { installed.associate { it.packageName to it.label } }

    ScreenScaffold("Groups & presets", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Groups let you block, schedule or focus on a set of apps together. An app can be in several groups.",
                style = MaterialTheme.typography.bodyLarge,
            )

            SectionTitle("Your groups")
            if (groups.isEmpty()) {
                Text("No groups yet. Create one, or start from a preset below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            groups.forEach { g ->
                InfoCard {
                    Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (g.members.isEmpty()) "No apps" else g.members.map { labels[it] ?: it }.sorted().take(6).joinToString() +
                            if (g.members.size > 6) " +${g.members.size - 6} more" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = g }) { Text("Edit") }
                        OutlinedButton(onClick = { vm.blockGroup(g) }, enabled = g.members.isNotEmpty()) { Text("Block all") }
                        TextButton(onClick = { deleting = g }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Button(onClick = { editing = AppGroup(0L, "", emptySet()) }) { Text("New group") }

            SectionTitle("Presets")
            Text(
                "A preset never blocks anything silently. You will see exactly which of your apps it affects before anything happens.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.presets.forEach { p ->
                InfoCard {
                    Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(p.description, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { preview = p }) { Text("Review apps") }
                }
            }
            InfoCard {
                Text("Priority when rules overlap", fontWeight = FontWeight.SemiBold)
                Text(
                    "1) Never-blocked apps (phone, settings, keyboard, launcher, FocusLock) and your emergency-access apps always win.\n" +
                        "2) A running emergency bypass allows that app, except in a strict session.\n" +
                        "3) Otherwise the app is blocked if ANY rule applies. The reason shown is: focus session, then schedule, then always-blocked list.\n" +
                        "4) Whole-app blocks take priority over feed rules for the same app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    preview?.let { p ->
        val apps = vm.previewPreset(p)
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(p.title) },
            text = {
                if (apps.isEmpty()) {
                    Text("None of the apps in this preset are installed on this phone.")
                } else {
                    Column {
                        Text("${apps.size} installed app(s) would be in this group:")
                        LazyColumn(Modifier.heightIn(max = 240.dp).padding(top = 8.dp)) {
                            items(apps, key = { it.packageName }) { Text("• ${it.label}", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            },
            confirmButton = {
                if (apps.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { vm.applyPreset(p, alsoBlock = true); preview = null }) { Text("Create group and block them") }
                        TextButton(onClick = { vm.applyPreset(p, alsoBlock = false); preview = null }) { Text("Create group only") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } },
        )
    }

    editing?.let { g ->
        var name by remember(g.id) { mutableStateOf(g.name) }
        var members by remember(g.id) { mutableStateOf(g.members) }
        var q by remember(g.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (g.id == 0L) "New group" else "Edit group") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(q, { q = it }, placeholder = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    LazyColumn(Modifier.heightIn(max = 260.dp).padding(top = 8.dp)) {
                        items(installed.filter { q.isBlank() || it.label.contains(q, true) }, key = { it.packageName }) { app ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = app.packageName in members,
                                    onCheckedChange = {
                                        members = if (app.packageName in members) members - app.packageName else members + app.packageName
                                    },
                                )
                                Text(app.label, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (g.id == 0L) vm.createGroup(name, members) else { vm.rename(g.id, name); vm.setMembers(g.id, members) }
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { g ->
        ConfirmDialog(
            "Delete \"${g.name}\"?", "The group is removed. Apps stay as they are, but schedules that used this group no longer include it.",
            "Delete", true,
            onConfirm = { vm.delete(g.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}
