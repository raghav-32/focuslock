package com.focuslock.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.BlockSource
import com.focuslock.app.domain.BypassMode
import com.focuslock.app.domain.RuleKind
import com.focuslock.app.domain.ThemeMode
import com.focuslock.app.ui.block.BlockArgs
import com.focuslock.app.ui.block.BlockScreen
import com.focuslock.app.ui.theme.FocusLockTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** NOT executed in the authoring environment (no emulator). Run with: ./gradlew connectedDebugAndroidTest */
@RunWith(AndroidJUnit4::class)
class BlockScreenUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(mode: ThemeMode, args: BlockArgs, settings: AppSettings = AppSettings(), onHome: () -> Unit = {}) {
        compose.setContent {
            FocusLockTheme(mode) {
                BlockScreen(args, settings, onHome = onHome, onProgress = {}, onBackToApp = {}, onBypass = {})
            }
        }
    }

    private val appArgs = BlockArgs("com.example", "Example", RuleKind.APP, BlockSource.FOCUS_SESSION, "Focus session", 0L, false)

    @Test fun showsRequiredContentInDarkTheme() {
        show(ThemeMode.DARK, appArgs)
        compose.onNodeWithText("This App Is Blocked").assertIsDisplayed()
        compose.onNodeWithText("Example is blocked.").assertIsDisplayed()
        compose.onNodeWithText("Return to Home").assertIsDisplayed()
        compose.onNodeWithText("View My Progress").assertIsDisplayed()
    }

    @Test fun showsRequiredContentInLightTheme() {
        show(ThemeMode.LIGHT, appArgs)
        compose.onNodeWithText("This App Is Blocked").assertIsDisplayed()
    }

    @Test fun returnToHomeInvokesCallback() {
        var home = false
        show(ThemeMode.DARK, appArgs, onHome = { home = true })
        compose.onNodeWithText("Return to Home").performClick()
        assertTrue(home)
    }

    @Test fun strictSessionHidesEmergencyAccess() {
        show(ThemeMode.DARK, appArgs.copy(strict = true), AppSettings(bypassMode = BypassMode.CONFIRM))
        compose.onNodeWithText("Strict session: bypass is turned off until it ends.").assertIsDisplayed()
    }

    @Test fun feedBlockNamesTheFeed() {
        show(ThemeMode.DARK, BlockArgs("com.yt", "YouTube Shorts", RuleKind.FEED, BlockSource.SCHEDULE, "Study", 0L, false))
        compose.onNodeWithText("YouTube Shorts is blocked.").assertIsDisplayed()
    }
}
