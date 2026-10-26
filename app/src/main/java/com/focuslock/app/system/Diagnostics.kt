package com.focuslock.app.system

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny local-only event log (never leaves the phone) so a crash or a killed service can be
 * explained after the fact. Holds only technical events: service connected/unbound, crash stack
 * traces. Never any screen content or app usage.
 */
object Diagnostics {
    private const val FILE = "diagnostics.log"
    private const val MAX_BYTES = 24_000

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    @Synchronized
    fun log(context: Context, line: String) {
        try {
            val f = file(context)
            if (f.exists() && f.length() > MAX_BYTES) {
                f.writeText(f.readText().takeLast(MAX_BYTES / 2))
            }
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText("$stamp  $line\n")
        } catch (_: Throwable) {
            // diagnostics must never cause a failure of their own
        }
    }

    fun logCrash(context: Context, where: String, t: Throwable) {
        val trace = t.stackTraceToString().lineSequence().take(40).joinToString("\n")
        log(context, "CRASH in $where:\n$trace")
    }

    fun read(context: Context): String = try {
        val f = file(context)
        if (f.exists()) f.readText().takeLast(8_000).ifBlank { "(empty)" } else "(no events recorded yet)"
    } catch (_: Throwable) {
        "(could not read log)"
    }

    fun clear(context: Context) {
        try { file(context).delete() } catch (_: Throwable) {}
    }

    /** Last-resort handler: record the crash, then let Android handle it as usual. */
    fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrash(context, "thread ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
