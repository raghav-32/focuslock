package com.focuslock.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.AppContainer
import com.focuslock.app.domain.BlockScope
import com.focuslock.app.domain.BlockedApp
import com.focuslock.app.domain.InstalledApp
import com.focuslock.app.domain.LockPolicy
import com.focuslock.app.domain.StatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

enum class AppFilter { ALL, BLOCKED, ALLOWED }

data class AppRow(
    val app: InstalledApp,
    val blocked: Boolean,
    val scope: BlockScope,
    val usageMs: Long?,
)

data class AppsUi(
    val loading: Boolean = true,
    val rows: List<AppRow> = emptyList(),
    val totalInstalled: Int = 0,
    val blockedCount: Int = 0,
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val showSystem: Boolean = false,
    val selected: Set<String> = emptySet(),
    val usageAvailable: Boolean = false,
    val message: String? = null,
)

private data class Inputs(
    val installed: List<InstalledApp>,
    val loading: Boolean,
    val blocked: Map<String, BlockedApp>,
    val usage: Map<String, Long>?,
)

private data class Controls(
    val query: String,
    val filter: AppFilter,
    val showSystem: Boolean,
    val selected: Set<String>,
    val message: String?,
)

class AppsViewModel(private val c: AppContainer) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val usage = MutableStateFlow<Map<String, Long>?>(null)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(AppFilter.ALL)
    private val showSystem = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)

    private val strictActive = MutableStateFlow(false)

    val ui: StateFlow<AppsUi> = combine(
        combine(installed, loading, c.rules.observeBlockedApps(), usage) { i, l, b, u ->
            Inputs(i, l, b.associateBy { it.packageName }, u)
        },
        combine(query, filter, showSystem, selected, message) { q, f, s, sel, m -> Controls(q, f, s, sel, m) },
    ) { inp, ctl ->
        val q = ctl.query.trim().lowercase()
        val rows = inp.installed.asSequence()
            .filter { ctl.showSystem || !it.isSystem || inp.blocked.containsKey(it.packageName) }
            .filter { q.isEmpty() || it.label.lowercase().contains(q) }
            .map { app ->
                val b = inp.blocked[app.packageName]
                AppRow(app, b?.enabled == true, b?.scope ?: BlockScope.ALWAYS, inp.usage?.get(app.packageName))
            }
            .filter {
                when (ctl.filter) {
                    AppFilter.ALL -> true
                    AppFilter.BLOCKED -> it.blocked
                    AppFilter.ALLOWED -> !it.blocked
                }
            }
            .toList()
        AppsUi(
            loading = inp.loading,
            rows = rows,
            totalInstalled = inp.installed.size,
            blockedCount = inp.blocked.values.count { it.enabled },
            query = ctl.query,
            filter = ctl.filter,
            showSystem = ctl.showSystem,
            selected = ctl.selected,
            usageAvailable = inp.usage != null,
            message = ctl.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUi())

    init {
        refresh()
        viewModelScope.launch {
            c.sessions.observeActive().collect { s ->
                strictActive.value = s != null && s.strict && s.remainingMillis(c.time) > 0L
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            val apps = try {
                c.discovery.loadLaunchableApps()
            } catch (e: Exception) {
                emptyList()
            }
            installed.value = apps
            loading.value = false
            loadUsage()
        }
    }

    private suspend fun loadUsage() {
        if (!c.permissions.usageAccessGranted()) {
            usage.value = null
            return
        }
        usage.value = withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val start = StatsCalculator.startOfDayMillis(LocalDate.now(zone), zone)
            c.usageReader.foregroundMillis(start, System.currentTimeMillis())
        }
    }

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: AppFilter) { filter.value = f }
    fun setShowSystem(v: Boolean) { showSystem.value = v }
    fun dismissMessage() { message.value = null }

    fun toggleSelected(pkg: String) = selected.update { if (pkg in it) it - pkg else it + pkg }
    fun clearSelection() { selected.value = emptySet() }
    fun selectAllVisible() { selected.value = ui.value.rows.map { it.app.packageName }.toSet() }

    private fun lockedOut(): Boolean {
        val locked = !LockPolicy.canEditRules(strictActive.value)
        if (locked) message.value = "A strict focus session is running. Rules can be changed when it ends."
        return locked
    }

    fun setBlocked(pkg: String, blocked: Boolean) {
        // Adding a block is always allowed; removing one is not during a strict session.
        if (!blocked && lockedOut()) return
        viewModelScope.launch { c.rules.setBlocked(listOf(pkg), blocked) }
    }

    fun blockSelected() {
        val sel = selected.value.toList()
        if (sel.isEmpty()) return
        viewModelScope.launch { c.rules.setBlocked(sel, true) }
        selected.value = emptySet()
    }

    fun unblockSelected() {
        if (lockedOut()) return
        val sel = selected.value.toList()
        if (sel.isEmpty()) return
        viewModelScope.launch { c.rules.setBlocked(sel, false) }
        selected.value = emptySet()
    }

    fun setScope(pkg: String, scope: BlockScope) {
        if (scope == BlockScope.SESSIONS_ONLY && lockedOut()) return
        viewModelScope.launch { c.rules.updateBlockedApp(BlockedApp(pkg, enabled = true, scope = scope)) }
    }
}
