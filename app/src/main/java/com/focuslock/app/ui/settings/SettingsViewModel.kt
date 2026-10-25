package com.focuslock.app.ui.settings

import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.AppContainer
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.BackupCodec
import com.focuslock.app.domain.BackupData
import com.focuslock.app.domain.BackupGroup
import com.focuslock.app.domain.BackupResult
import com.focuslock.app.domain.BackupSchedule
import com.focuslock.app.domain.AppGroup
import com.focuslock.app.domain.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val groups: StateFlow<List<AppGroup>> = c.rules.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val installed = MutableStateFlow<List<InstalledApp>>(emptyList())

    /** One-shot result text for snackbars / dialogs. */
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { installed.value = runCatching { c.discovery.loadLaunchableApps() }.getOrDefault(emptyList()) }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { c.settings.update(transform) }
    }

    fun setLanguage(tag: String) {
        update { it.copy(language = tag) }
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                c.app.getSystemService(android.app.LocaleManager::class.java)
                    .applicationLocales = LocaleList.forLanguageTags(tag)
            }
        }
    }

    fun clearMessage() { message.value = null }

    // ---- backup / restore / export -----------------------------------------------------------

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            message.value = try {
                val data = BackupData(
                    exportedAt = System.currentTimeMillis(),
                    blockedApps = c.rules.getBlockedApps(),
                    feedRules = c.rules.getFeedRules(),
                    groups = c.rules.getGroups().map { BackupGroup(it.name, it.members, it.presetKey) },
                    schedules = run {
                        val groupsById = c.rules.getGroups().associateBy { it.id }
                        c.rules.getSchedules().map { s ->
                            BackupSchedule(s, s.groupIds.mapNotNull { groupsById[it]?.name }.toSet())
                        }
                    },
                    blockEvents = c.stats.getAllEvents(),
                    dailyUsage = c.stats.getAllUsage(),
                )
                writeText(uri, BackupCodec.encode(data))
                "Backup saved."
            } catch (e: Exception) {
                "Could not save the backup."
            }
        }
    }

    fun exportStatsCsv(uri: Uri) {
        viewModelScope.launch {
            message.value = try {
                val csv = BackupCodec.statsCsv(c.stats.getAllEvents(), c.stats.getAllUsage(), ZoneId.systemDefault())
                writeText(uri, csv)
                "Statistics exported."
            } catch (e: Exception) {
                "Could not export statistics."
            }
        }
    }

    /** Validates first; nothing is changed unless the whole file passes. */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                readText(uri)
            } catch (e: Exception) {
                message.value = "Could not read that file."
                return@launch
            }
            when (val r = BackupCodec.parse(text)) {
                is BackupResult.Invalid -> message.value = "Not restored: ${r.reason}"
                is BackupResult.Ok -> {
                    try {
                        val d = r.data
                        c.rules.replaceAll(d.blockedApps, d.feedRules, d.groups, d.schedules)
                        c.stats.replaceHistory(d.blockEvents, d.dailyUsage)
                        c.maintenance.run()
                        message.value = "Backup restored."
                    } catch (e: Exception) {
                        message.value = "Restore failed. Your existing data was kept."
                    }
                }
            }
        }
    }

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = c.app.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = c.app.contentResolver.openInputStream(uri) ?: error("no stream")
        input.use {
            val bytes = it.readNBytesCapped(BackupCodec.MAX_CHARS * 4)
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCapped(max: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
            if (buf.size() > max) error("too large")
        }
        return buf.toByteArray()
    }

    // ---- destructive actions (the UI confirms first) -------------------------------------------

    fun resetStatistics() {
        viewModelScope.launch {
            c.stats.clear()
            message.value = "Statistics deleted."
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            c.settings.update { AppSettings(onboardingDone = true) }
            message.value = "Settings reset."
        }
    }
}
