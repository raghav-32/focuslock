package com.focuslock.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.FocusSession
import com.focuslock.app.domain.PermissionState
import com.focuslock.app.domain.ServiceHealth
import com.focuslock.app.domain.formatDuration
import com.focuslock.app.ui.HomeViewModel
import com.focuslock.app.ui.common.BarChart
import com.focuslock.app.ui.common.InfoCard
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.common.SectionTitle
import com.focuslock.app.ui.common.StatTile
import com.focuslock.app.ui.common.WarningBanner
import com.focuslock.app.ui.common.containerViewModel
import com.focuslock.app.ui.common.rememberNow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    settings: AppSettings,
    permissions: PermissionState,
    session: FocusSession?,
    sessionRemaining: (FocusSession) -> Long,
    strictLocked: Boolean,
    onMasterChange: (Boolean) -> Unit,
    onStartFocus: () -> Unit,
    onManageApps: () -> Unit,
    onPermissions: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
) {
    val vm = containerViewModel { HomeViewModel(it) }
    val ui by vm.ui.collectAsState()
    val now by rememberNow(1_000L)
    // Depends on `now` so the countdown re-evaluates every second.
    val remainingMs = remember(session, now) { session?.let(sessionRemaining) ?: 0L }

    LifecycleResumeEffect(Unit) {
        vm.refreshUsage()
        onPauseOrDispose { }
    }

    ScreenScaffold(
        title = "FocusLock",
        actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val hour = LocalTime.now().hour
            val greeting = when {
                hour < 5 -> "Good night"
                hour < 12 -> "Good morning"
                hour < 18 -> "Good afternoon"
                else -> "Good evening"
            }
            Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (permissions.accessibility != ServiceHealth.CONNECTED) {
                WarningBanner(
                    title = if (permissions.accessibility == ServiceHealth.DISABLED) "Blocking is OFF" else "Blocking service is not running",
                    body = "Accessibility access is needed to block apps and feeds. Until it is on, nothing is being blocked, " +
                        "including during focus sessions and schedules.",
                    actionLabel = "Fix now",
                    onAction = onPermissions,
                )
            }

            // Active block status
            InfoCard {
                val activeNames = ui.activeScheduleNames
                val sessionActive = session != null && remainingMs > 0
                Text("Block status", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                when {
                    !settings.masterEnabled -> Text("Paused: all blocking is switched off.", fontWeight = FontWeight.SemiBold)
                    sessionActive -> Text(
                        "Focus session: ${formatDuration(remainingMs)} left" + if (session?.strict == true) " (strict)" else "",
                        fontWeight = FontWeight.SemiBold,
                    )
                    activeNames.isNotEmpty() -> Text("Schedule active: ${activeNames.joinToString()}", fontWeight = FontWeight.SemiBold)
                    ui.blockedAppCount > 0 -> Text("${ui.blockedAppCount} app(s) on your always-blocked list.", fontWeight = FontWeight.SemiBold)
                    else -> Text("Nothing is blocked right now.", fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Blocking enabled")
                        if (strictLocked) {
                            Text(
                                "Locked by strict session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = settings.masterEnabled,
                        onCheckedChange = onMasterChange,
                        enabled = !strictLocked,
                    )
                }
            }

            Button(onClick = onStartFocus, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(if (session != null) "View focus session" else "Start focus session")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Blocked today", ui.blocksToday.toString(), Modifier.weight(1f), "times FocusLock stepped in")
                StatTile("Focus today", formatDuration(ui.focusMillisToday), Modifier.weight(1f), "measured")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Tracked app usage",
                    ui.usageTodayMs?.let { formatDuration(it) } ?: "—",
                    Modifier.weight(1f),
                    if (ui.usageTodayMs == null) "Usage access off" else "measured today",
                )
                StatTile(
                    "Active rules",
                    (ui.blockedAppCount + ui.activeFeedRules).toString(),
                    Modifier.weight(1f).clickable(onClick = onManageApps),
                    "${ui.blockedAppCount} apps, ${ui.activeFeedRules} feeds",
                )
            }

            InfoCard {
                Text("Next scheduled block", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                val b = ui.nextBoundary
                if (b == null) {
                    Text("No schedules yet. Create one in the Schedules tab.")
                } else {
                    val t = b.at.format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault()))
                    Text("${b.scheduleName}: ${if (b.isStart) "starts" else "ends"} $t", fontWeight = FontWeight.SemiBold)
                }
            }

            SectionTitle("Blocks this week")
            InfoCard {
                if (ui.weekly.all { it.second == 0 }) {
                    Text("No blocks recorded yet. Counts appear here as FocusLock blocks apps and feeds.")
                } else {
                    BarChart(
                        values = ui.weekly.map { it.second.toFloat() },
                        labels = ui.weekly.map { it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                        describe = "Blocks per day for the last 7 days: " +
                            ui.weekly.joinToString { "${it.first.dayOfWeek} ${it.second}" },
                    )
                }
                OutlinedButton(onClick = onStats) { Text("See all statistics") }
            }
        }
    }
}
