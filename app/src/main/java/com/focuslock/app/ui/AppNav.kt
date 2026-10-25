package com.focuslock.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.PermissionState
import com.focuslock.app.ui.apps.AppsScreen
import com.focuslock.app.ui.feeds.FeedsScreen
import com.focuslock.app.ui.focus.FocusScreen
import com.focuslock.app.ui.groups.GroupsScreen
import com.focuslock.app.ui.home.HomeScreen
import com.focuslock.app.ui.onboarding.PermissionCenterScreen
import com.focuslock.app.ui.onboarding.RecoveryScreen
import com.focuslock.app.ui.schedules.ScheduleEditScreen
import com.focuslock.app.ui.schedules.SchedulesScreen
import com.focuslock.app.ui.settings.AboutScreen
import com.focuslock.app.ui.settings.BackupScreen
import com.focuslock.app.ui.settings.BypassSettingsScreen
import com.focuslock.app.ui.settings.NotificationSettingsScreen
import com.focuslock.app.ui.settings.PrivacyScreen
import com.focuslock.app.ui.settings.SettingsScreen
import com.focuslock.app.ui.stats.StatsScreen
import com.focuslock.app.ui.stats.WeeklyProgressScreen
import com.focuslock.app.system.SettingsIntents
import com.focuslock.app.system.openSettings
import androidx.compose.ui.platform.LocalContext

private object Route {
    const val HOME = "home"
    const val APPS = "apps"
    const val FOCUS = "focus"
    const val SCHEDULES = "schedules"
    const val STATS = "stats"
    const val SCHEDULE_EDIT = "schedule_edit/{id}"
    const val FEEDS = "feeds"
    const val GROUPS = "groups"
    const val WEEKLY = "weekly"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "settings_notifications"
    const val BYPASS = "settings_bypass"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"
    const val BACKUP = "backup"
    const val PERMISSIONS = "permissions"
    const val RECOVERY = "recovery"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Route.HOME, "Home", Icons.Filled.Home),
    Tab(Route.APPS, "Apps", Icons.Filled.Apps),
    Tab(Route.FOCUS, "Focus", Icons.Filled.Timer),
    Tab(Route.SCHEDULES, "Schedules", Icons.Filled.CalendarMonth),
    Tab(Route.STATS, "Stats", Icons.Filled.BarChart),
)

@Composable
fun AppNav(vm: MainViewModel, settings: AppSettings, permissions: PermissionState, startRoute: String?) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val session by vm.activeSession.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(startRoute) {
        if (startRoute != null && TABS.any { it.route == startRoute }) nav.navigate(startRoute) { launchSingleTop = true }
    }

    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }
    val back: () -> Unit = { nav.popBackStack() }

    Scaffold(
        bottomBar = {
            if (TABS.any { it.route == current }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Route.HOME, modifier = Modifier.padding(inner).consumeWindowInsets(inner)) {
            composable(Route.HOME) {
                HomeScreen(
                    settings = settings,
                    permissions = permissions,
                    session = session,
                    sessionRemaining = vm::sessionRemaining,
                    strictLocked = vm.strictLockedNow(),
                    onMasterChange = vm::setMasterEnabled,
                    onStartFocus = { go(Route.FOCUS) },
                    onManageApps = { go(Route.APPS) },
                    onPermissions = { go(Route.PERMISSIONS) },
                    onSettings = { go(Route.SETTINGS) },
                    onStats = { go(Route.STATS) },
                )
            }
            composable(Route.APPS) { AppsScreen(onFeeds = { go(Route.FEEDS) }, onGroups = { go(Route.GROUPS) }) }
            composable(Route.FOCUS) { FocusScreen(onEditApps = { go(Route.APPS) }) }
            composable(Route.SCHEDULES) { SchedulesScreen(onEdit = { go("schedule_edit/$it") }) }
            composable(Route.STATS) {
                StatsScreen(
                    onBack = null,
                    onWeekly = { go(Route.WEEKLY) },
                    onUsagePermission = { context.openSettings(SettingsIntents.usageAccess()) },
                )
            }
            composable(
                Route.SCHEDULE_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry -> ScheduleEditScreen(entry.arguments?.getLong("id") ?: 0L, onBack = back) }
            composable(Route.FEEDS) { FeedsScreen(onBack = back, onPermissions = { go(Route.PERMISSIONS) }) }
            composable(Route.GROUPS) { GroupsScreen(onBack = back) }
            composable(Route.WEEKLY) { WeeklyProgressScreen(onBack = back) }
            composable(Route.SETTINGS) {
                SettingsScreen(
                    permissions = permissions,
                    strictLocked = vm.strictLockedNow(),
                    onBack = back,
                    onNotifications = { go(Route.NOTIFICATIONS) },
                    onBypass = { go(Route.BYPASS) },
                    onPermissions = { go(Route.PERMISSIONS) },
                    onPrivacy = { go(Route.PRIVACY) },
                    onBackup = { go(Route.BACKUP) },
                    onAbout = { go(Route.ABOUT) },
                    onRecovery = { go(Route.RECOVERY) },
                    onGroups = { go(Route.GROUPS) },
                )
            }
            composable(Route.NOTIFICATIONS) { NotificationSettingsScreen(permissions, back) }
            composable(Route.BYPASS) { BypassSettingsScreen(back) }
            composable(Route.PRIVACY) { PrivacyScreen(back) }
            composable(Route.ABOUT) { AboutScreen(back) }
            composable(Route.BACKUP) { BackupScreen(back) }
            composable(Route.PERMISSIONS) { PermissionCenterScreen(permissions, back, onRecovery = { go(Route.RECOVERY) }) }
            composable(Route.RECOVERY) { RecoveryScreen(permissions, back) }
        }
    }
}
