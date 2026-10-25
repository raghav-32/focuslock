package com.focuslock.app.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslock.app.domain.BlockScope
import com.focuslock.app.domain.formatDuration
import com.focuslock.app.ui.common.AppIcon
import com.focuslock.app.ui.common.ConfirmDialog
import com.focuslock.app.ui.common.EmptyState
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.common.containerViewModel

private const val LARGE_SELECTION = 10

/**
 * One screen covers "all installed apps", "search and app selection" and "blocked-app management":
 * the filter chips switch between All / Blocked / Allowed, and long-press starts multi-select.
 */
@Composable
fun AppsScreen(onFeeds: () -> Unit, onGroups: () -> Unit) {
    val vm = containerViewModel { AppsViewModel(it) }
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var details by remember { mutableStateOf<AppRow?>(null) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmUnblock by remember { mutableStateOf(false) }
    val selecting = ui.selected.isNotEmpty()
    val haptic = com.focuslock.app.ui.common.rememberHaptic()

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    ScreenScaffold(
        title = if (selecting) "${ui.selected.size} selected" else "Apps",
        actions = {
            if (selecting) {
                TextButton(onClick = vm::selectAllVisible) { Text("Select all") }
                IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
            } else {
                IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh app list") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                        }
                    },
                )
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppFilter.entries.forEach { f ->
                        FilterChip(
                            selected = ui.filter == f,
                            onClick = { vm.setFilter(f) },
                            label = {
                                Text(
                                    when (f) {
                                        AppFilter.ALL -> "All"
                                        AppFilter.BLOCKED -> "Blocked (${ui.blockedCount})"
                                        AppFilter.ALLOWED -> "Allowed"
                                    },
                                )
                            },
                        )
                    }
                }
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onFeeds) { Text("Feed rules") }
                    TextButton(onClick = onGroups) { Text("Groups & presets") }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text("System apps", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = ui.showSystem, onCheckedChange = vm::setShowSystem, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (!ui.usageAvailable && !ui.loading) {
                    Text(
                        "Usage time is hidden because Usage access is off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                when {
                    ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ui.totalInstalled == 0 -> EmptyState(
                        "No apps found",
                        "FocusLock could not find any launchable apps. Pull to refresh with the button above.",
                    )
                    ui.rows.isEmpty() -> EmptyState("Nothing matches", "Try a different search or filter.")
                    else -> LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                        items(ui.rows, key = { it.app.packageName }) { row ->
                            AppListItem(
                                row = row,
                                selecting = selecting,
                                selected = row.app.packageName in ui.selected,
                                onToggleBlocked = { haptic(); vm.setBlocked(row.app.packageName, it) },
                                onClick = {
                                    if (selecting) vm.toggleSelected(row.app.packageName) else details = row
                                },
                                onLongClick = { vm.toggleSelected(row.app.packageName) },
                            )
                            HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                    }
                }
            }

            if (selecting) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { if (ui.selected.size > LARGE_SELECTION) confirmBlock = true else vm.blockSelected() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Block") }
                    Button(onClick = { confirmUnblock = true }, modifier = Modifier.weight(1f)) { Text("Unblock") }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (confirmBlock) {
        ConfirmDialog(
            title = "Block ${ui.selected.size} apps?",
            body = "These apps will be added to your blocked list. You can change this at any time unless a strict session is running.",
            confirmLabel = "Block them",
            onConfirm = { confirmBlock = false; vm.blockSelected() },
            onDismiss = { confirmBlock = false },
        )
    }
    if (confirmUnblock) {
        ConfirmDialog(
            title = "Unblock ${ui.selected.size} apps?",
            body = "They will be removed from your blocked list (schedules and groups that include them still apply).",
            confirmLabel = "Unblock",
            onConfirm = { confirmUnblock = false; vm.unblockSelected() },
            onDismiss = { confirmUnblock = false },
        )
    }
    details?.let { row ->
        AppDetailsDialog(
            row = row,
            onDismiss = { details = null },
            onScope = { vm.setScope(row.app.packageName, it); details = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
    row: AppRow,
    selecting: Boolean,
    selected: Boolean,
    onToggleBlocked: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selecting) Checkbox(checked = selected, onCheckedChange = { onClick() })
        AppIcon(row.app.packageName)
        Column(Modifier.weight(1f)) {
            Text(row.app.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = if (row.blocked) {
                if (row.scope == BlockScope.ALWAYS) "Blocked" else "Blocked in sessions & schedules"
            } else "Allowed"
            val usage = row.usageMs?.let { " · ${formatDuration(it)} today" }.orEmpty()
            Text(
                status + usage,
                style = MaterialTheme.typography.bodySmall,
                color = if (row.blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = row.blocked, onCheckedChange = onToggleBlocked)
    }
}

@Composable
private fun AppDetailsDialog(row: AppRow, onDismiss: () -> Unit, onScope: (BlockScope) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Package: ${row.app.packageName}", style = MaterialTheme.typography.bodySmall)
                Text(if (row.app.isSystem) "System app" else "Installed app", style = MaterialTheme.typography.bodySmall)
                row.usageMs?.let { Text("Used today: ${formatDuration(it)} (measured)") }
                if (row.blocked) {
                    Text("When should this app be blocked?", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onScope(BlockScope.ALWAYS) }) {
                        androidx.compose.material3.RadioButton(row.scope == BlockScope.ALWAYS, onClick = { onScope(BlockScope.ALWAYS) })
                        Text("Always")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onScope(BlockScope.SESSIONS_ONLY) },
                    ) {
                        androidx.compose.material3.RadioButton(row.scope == BlockScope.SESSIONS_ONLY, onClick = { onScope(BlockScope.SESSIONS_ONLY) })
                        Text("Only during focus sessions and schedules")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
