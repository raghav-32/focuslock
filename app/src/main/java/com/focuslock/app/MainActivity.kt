package com.focuslock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.focuslock.app.domain.ThemeMode
import com.focuslock.app.ui.AppNav
import com.focuslock.app.ui.MainViewModel
import com.focuslock.app.ui.common.LocalHapticsEnabled
import com.focuslock.app.ui.common.containerViewModel
import com.focuslock.app.ui.onboarding.OnboardingScreen
import com.focuslock.app.ui.onboarding.SplashScreen
import com.focuslock.app.ui.theme.FocusLockTheme

class MainActivity : ComponentActivity() {

    private var startRoute: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startRoute = intent?.getStringExtra(EXTRA_ROUTE)
        setContent { Root(startRoute) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startRoute = intent.getStringExtra(EXTRA_ROUTE)
        setContent { Root(startRoute) }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

@Composable
private fun Root(startRoute: String?) {
    val vm = containerViewModel { MainViewModel(it) }
    val settings by vm.settings.collectAsState()
    val permissions by vm.permissions.collectAsState()

    // Re-check permissions whenever the user comes back from a system settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissions() }

    val s = settings
    FocusLockTheme(s?.themeMode ?: ThemeMode.DARK) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                s == null -> SplashScreen()
                !s.onboardingDone -> OnboardingScreen(permissions, onFinish = vm::finishOnboarding)
                else -> CompositionLocalProvider(LocalHapticsEnabled provides s.haptics) {
                    AppNav(vm, s, permissions, startRoute)
                }
            }
        }
    }
}
