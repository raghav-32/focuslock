package com.focuslock.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.AppContainer
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.FocusSession
import com.focuslock.app.domain.PermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state: settings (null while loading = splash), permission health, strict-lock status. */
class MainViewModel(private val c: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings?> = c.settings.settings
        .map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeSession: StateFlow<FocusSession?> = c.sessions.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    init {
        // Cheap Settings.Secure / AppOps reads. Polling here (only while the app UI is alive)
        // is how a revoked permission becomes visible without the user restarting the app.
        viewModelScope.launch {
            while (true) {
                _permissions.value = c.permissions.state()
                delay(2_500)
            }
        }
    }

    fun refreshPermissions() {
        _permissions.value = c.permissions.state()
        viewModelScope.launch { runCatching { c.maintenance.run() } }
    }

    fun finishOnboarding() {
        viewModelScope.launch { c.settings.update { it.copy(onboardingDone = true) } }
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch { c.settings.update { it.copy(masterEnabled = enabled) } }
    }

    fun sessionRemaining(s: FocusSession): Long = s.remainingMillis(c.time)

    /** True while a strict session is running: rules cannot be loosened. */
    fun strictLockedNow(): Boolean {
        val s = activeSession.value ?: return false
        return s.strict && s.remainingMillis(c.time) > 0L
    }
}
