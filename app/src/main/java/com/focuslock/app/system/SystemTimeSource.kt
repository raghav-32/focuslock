package com.focuslock.app.system

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.focuslock.app.domain.TimeSource

class SystemTimeSource(private val context: Context) : TimeSource {
    override fun wallMillis(): Long = System.currentTimeMillis()

    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    /** Settings.Global.BOOT_COUNT (API 24+) increments on every boot; -1 if unreadable. */
    override fun bootCount(): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (e: Exception) {
        -1
    }
}
