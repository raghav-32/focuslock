package com.focuslock.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "focuslock_settings")

private object K {
    val onboardingDone = booleanPreferencesKey("onboarding_done")
    val themeMode = stringPreferencesKey("theme_mode")
    val language = stringPreferencesKey("language")
    val haptics = booleanPreferencesKey("haptics")
    val blockMessage = stringPreferencesKey("block_message")
    val notifySessionStart = booleanPreferencesKey("notify_session_start")
    val notifySessionEnd = booleanPreferencesKey("notify_session_end")
    val notifyScheduleStart = booleanPreferencesKey("notify_schedule_start")
    val notifyScheduleEnd = booleanPreferencesKey("notify_schedule_end")
    val notifyUsageWarning = booleanPreferencesKey("notify_usage_warning")
    val notifyServiceFailure = booleanPreferencesKey("notify_service_failure")
    val usageWarnMinutes = intPreferencesKey("usage_warn_minutes")
    val defaultFocusMinutes = intPreferencesKey("default_focus_minutes")
    val defaultGroupId = longPreferencesKey("default_group_id")
    val bypassMode = stringPreferencesKey("bypass_mode")
    val bypassMinutes = intPreferencesKey("bypass_minutes")
    val countdownSeconds = intPreferencesKey("countdown_seconds")
    val reopenCooldownMinutes = intPreferencesKey("reopen_cooldown_minutes")
    val emergencyApps = stringSetPreferencesKey("emergency_apps")
    val bypassGrants = stringSetPreferencesKey("bypass_grants")
    val lastBypassEndWall = longPreferencesKey("last_bypass_end_wall")
    val masterEnabled = booleanPreferencesKey("master_enabled")
    val minutesSavedPerBlock = intPreferencesKey("minutes_saved_per_block")
    val lastUsageWarnDay = longPreferencesKey("last_usage_warn_day")
    val lastFailureNotifiedWall = longPreferencesKey("last_failure_notified_wall")
    val activeScheduleIds = stringSetPreferencesKey("active_schedule_ids")
}

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        onboardingDone = this[K.onboardingDone] ?: d.onboardingDone,
        themeMode = this[K.themeMode]?.let { parseEnum(it, d.themeMode) } ?: d.themeMode,
        language = this[K.language] ?: d.language,
        haptics = this[K.haptics] ?: d.haptics,
        blockMessage = this[K.blockMessage] ?: d.blockMessage,
        notifySessionStart = this[K.notifySessionStart] ?: d.notifySessionStart,
        notifySessionEnd = this[K.notifySessionEnd] ?: d.notifySessionEnd,
        notifyScheduleStart = this[K.notifyScheduleStart] ?: d.notifyScheduleStart,
        notifyScheduleEnd = this[K.notifyScheduleEnd] ?: d.notifyScheduleEnd,
        notifyUsageWarning = this[K.notifyUsageWarning] ?: d.notifyUsageWarning,
        notifyServiceFailure = this[K.notifyServiceFailure] ?: d.notifyServiceFailure,
        usageWarnMinutes = this[K.usageWarnMinutes] ?: d.usageWarnMinutes,
        defaultFocusMinutes = this[K.defaultFocusMinutes] ?: d.defaultFocusMinutes,
        defaultGroupId = this[K.defaultGroupId] ?: d.defaultGroupId,
        bypassMode = this[K.bypassMode]?.let { parseEnum(it, d.bypassMode) } ?: d.bypassMode,
        bypassMinutes = this[K.bypassMinutes] ?: d.bypassMinutes,
        countdownSeconds = this[K.countdownSeconds] ?: d.countdownSeconds,
        reopenCooldownMinutes = this[K.reopenCooldownMinutes] ?: d.reopenCooldownMinutes,
        emergencyApps = this[K.emergencyApps] ?: d.emergencyApps,
        bypassGrants = (this[K.bypassGrants] ?: emptySet()).mapNotNull { entry ->
            val i = entry.lastIndexOf('|')
            val until = if (i > 0) entry.substring(i + 1).toLongOrNull() else null
            if (until == null) null else entry.substring(0, i) to until
        }.toMap(),
        lastBypassEndWall = this[K.lastBypassEndWall] ?: d.lastBypassEndWall,
        masterEnabled = this[K.masterEnabled] ?: d.masterEnabled,
        minutesSavedPerBlock = this[K.minutesSavedPerBlock] ?: d.minutesSavedPerBlock,
        lastUsageWarnDay = this[K.lastUsageWarnDay] ?: d.lastUsageWarnDay,
        lastFailureNotifiedWall = this[K.lastFailureNotifiedWall] ?: d.lastFailureNotifiedWall,
        activeScheduleIds = (this[K.activeScheduleIds] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet(),
    )
}

private fun MutablePreferences.write(s: AppSettings) {
    this[K.onboardingDone] = s.onboardingDone
    this[K.themeMode] = s.themeMode.name
    this[K.language] = s.language
    this[K.haptics] = s.haptics
    this[K.blockMessage] = s.blockMessage
    this[K.notifySessionStart] = s.notifySessionStart
    this[K.notifySessionEnd] = s.notifySessionEnd
    this[K.notifyScheduleStart] = s.notifyScheduleStart
    this[K.notifyScheduleEnd] = s.notifyScheduleEnd
    this[K.notifyUsageWarning] = s.notifyUsageWarning
    this[K.notifyServiceFailure] = s.notifyServiceFailure
    this[K.usageWarnMinutes] = s.usageWarnMinutes
    this[K.defaultFocusMinutes] = s.defaultFocusMinutes
    this[K.defaultGroupId] = s.defaultGroupId
    this[K.bypassMode] = s.bypassMode.name
    this[K.bypassMinutes] = s.bypassMinutes
    this[K.countdownSeconds] = s.countdownSeconds
    this[K.reopenCooldownMinutes] = s.reopenCooldownMinutes
    this[K.emergencyApps] = s.emergencyApps
    this[K.bypassGrants] = s.bypassGrants.map { (pkg, until) -> "$pkg|$until" }.toSet()
    this[K.lastBypassEndWall] = s.lastBypassEndWall
    this[K.masterEnabled] = s.masterEnabled
    this[K.minutesSavedPerBlock] = s.minutesSavedPerBlock
    this[K.lastUsageWarnDay] = s.lastUsageWarnDay
    this[K.lastFailureNotifiedWall] = s.lastFailureNotifiedWall
    this[K.activeScheduleIds] = s.activeScheduleIds.map { it.toString() }.toSet()
}

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = store.data
        .catch { e ->
            // A corrupted/unreadable file must not crash blocking: fall back to defaults.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    override suspend fun resetAll() {
        store.edit { it.clear() }
    }
}
