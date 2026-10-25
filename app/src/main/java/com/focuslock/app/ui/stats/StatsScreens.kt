package com.focuslock.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuslock.app.domain.formatDuration
import com.focuslock.app.ui.StatsRange
import com.focuslock.app.ui.StatsUi
import com.focuslock.app.ui.StatsViewModel
import com.focuslock.app.ui.common.BarChart
import com.focuslock.app.ui.common.EmptyState
import com.focuslock.app.ui.common.HorizontalBars
import com.focuslock.app.ui.common.InfoCard
import com.focuslock.app.ui.common.ScreenScaffold
import com.focuslock.app.ui.common.SectionTitle
import com.focuslock.app.ui.common.StatTile
import com.focuslock.app.ui.common.containerViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun changeText(pct: Double?, previousLabel: String): String = when {
    pct == null -> "No $previousLabel data to compare yet"
    abs(pct) < 1.0 -> "About the same as $previousLabel"
    pct > 0 -> "Up ${pct.roundToInt()}% vs $previousLabel"
    else -> "Down ${abs(pct).roundToInt()}% vs $previousLabel"
}

private fun dayLabel(d: LocalDate, range: StatsRange): String =
    if (range == StatsRange.MONTH) d.dayOfMonth.toString() else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Composable
fun StatsScreen(onBack: (() -> Unit)?, onWeekly: () -> Unit, onUsagePermission: () -> Unit) {
    val vm = containerViewModel { StatsViewModel(it) }
    val ui by vm.ui.collectAsState()

    ScreenScaffold("Usage & progress", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { r ->
                    FilterChip(selected = ui.range == r, onClick = { vm.setRange(r) }, label = { Text(r.label) })
                }
            }
            val prevLabel = if (ui.range == StatsRange.DAY) "yesterday" else "the previous ${ui.range.days} days"

            if (!ui.hasAnyData) {
                EmptyState(
                    "No data for this period yet",
                    "Block events and focus sessions show up here as they happen. Usage time needs Usage access.",
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Blocks", ui.blocks.toString(), Modifier.weight(1f), changeText(ui.changePercent, prevLabel))
                StatTile("Focus time", formatDuration(ui.focusMillis), Modifier.weight(1f), "${ui.sessionsCompleted} session(s) completed")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Most blocked",
                    ui.topBlocked?.first ?: "—",
                    Modifier.weight(1f),
                    ui.topBlocked?.let { "${it.second} times" } ?: "Nothing blocked yet",
                )
                StatTile("Emergency opens", ui.bypasses.toString(), Modifier.weight(1f), "times you used a bypass")
            }

            SectionTitle("Estimated time saved")
            InfoCard {
                Text("≈ ${ui.savedMinutesEstimate} min", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "This is an ESTIMATE, not a measurement: ${ui.blocks} blocks × ${ui.minutesPerBlock} min assumed per distraction. " +
                        "You can change the assumed minutes in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(if (ui.range == StatsRange.DAY) "Blocks by hour today" else "Blocks per day")
            InfoCard {
                if (ui.range == StatsRange.DAY) {
                    BarChart(
                        values = ui.hourlyBlocks.map { it.toFloat() },
                        labels = ui.hourlyBlocks.indices.map { if (it % 6 == 0) it.toString() else "" },
                        describe = "Blocks by hour of today",
                    )
                } else {
                    BarChart(
                        values = ui.dailyBlocks.map { it.second.toFloat() },
                        labels = ui.dailyBlocks.map { dayLabel(it.first, ui.range) },
                        describe = "Blocks per day: " + ui.dailyBlocks.joinToString { "${it.first} ${it.second}" },
                    )
                }
            }

            SectionTitle("Measured app usage")
            InfoCard {
                if (!ui.usageAvailable) {
                    Text("Usage access is off, so FocusLock cannot measure time spent in apps.")
                    OutlinedButton(onClick = onUsagePermission) { Text("Turn on usage access") }
                } else if (ui.usageByApp.isEmpty()) {
                    Text("No usage recorded for your tracked apps in this period. Only apps you blocked or put in groups are tracked.")
                } else {
                    Text(
                        "Total: ${formatDuration(ui.usageTotalMs)}  ·  " +
                            changeText(
                                com.focuslock.app.domain.StatsCalculator.percentChange(
                                    ui.previousUsageTotalMs.toDouble(), ui.usageTotalMs.toDouble(),
                                ),
                                prevLabel,
                            ),
                        fontWeight = FontWeight.SemiBold,
                    )
                    HorizontalBars(ui.usageByApp.map { it.label to it.millis }, ::formatDuration)
                    Text(
                        "Measured from Android's usage statistics. Figures are close to, but not exactly, what Digital Wellbeing shows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(onClick = onWeekly, Modifier.fillMaxWidth()) { Text("Weekly progress") }
        }
    }
}

@Composable
fun WeeklyProgressScreen(onBack: () -> Unit) {
    val vm = containerViewModel { StatsViewModel(it) }
    LaunchedEffectRange(vm)
    val ui by vm.ui.collectAsState()

    ScreenScaffold("Weekly progress", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeeklySummary(ui)
        }
    }
}

@Composable
private fun LaunchedEffectRange(vm: StatsViewModel) {
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.setRange(StatsRange.WEEK) }
}

@Composable
private fun WeeklySummary(ui: StatsUi) {
    InfoCard {
        Text("Last 7 days", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text("${ui.blocks} blocks · ${formatDuration(ui.focusMillis)} focused", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(changeText(ui.changePercent, "the week before"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val prevFocus = ui.previousFocusMillis
        Text(
            if (prevFocus > 0L) "Focus time last week: ${formatDuration(prevFocus)}" else "No focus sessions the week before.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionTitle("Blocks per day")
    InfoCard {
        BarChart(
            values = ui.dailyBlocks.map { it.second.toFloat() },
            labels = ui.dailyBlocks.map { it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
            describe = "Blocks per day this week: " + ui.dailyBlocks.joinToString { "${it.first.dayOfWeek} ${it.second}" },
        )
    }
    SectionTitle("Focus minutes per day")
    InfoCard {
        BarChart(
            values = ui.dailyFocusMinutes.map { it.second.toFloat() },
            labels = ui.dailyFocusMinutes.map { it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
            barColor = MaterialTheme.colorScheme.secondary,
            describe = "Focus minutes per day this week: " + ui.dailyFocusMinutes.joinToString { "${it.first.dayOfWeek} ${it.second}" },
        )
    }
}
