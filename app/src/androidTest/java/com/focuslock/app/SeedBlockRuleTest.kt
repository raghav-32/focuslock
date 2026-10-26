package com.focuslock.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** CI helper, not a real test: blocks the package given as `-e pkg <name>` so the emulator script can exercise the service. */
class SeedBlockRuleTest {
    @Test
    fun seed() {
        val pkg = InstrumentationRegistry.getArguments().getString("pkg") ?: return
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FocusLockApp
        runBlocking { app.container.rules.setBlocked(listOf(pkg), true) }
    }
}
