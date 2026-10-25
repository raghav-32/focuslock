package com.focuslock.app.domain

/** Abstraction over the three clocks we care about so logic can be unit-tested. */
interface TimeSource {
    /** Wall clock (can be changed by the user or the network). */
    fun wallMillis(): Long

    /** Monotonic, includes deep sleep, resets at reboot. */
    fun elapsedMillis(): Long

    /** Number of boots since factory reset, or -1 when the device does not expose it. */
    fun bootCount(): Int
}

/**
 * Session timing.
 *
 * Within one boot we measure with the monotonic clock, so changing the wall clock cannot
 * lengthen or shorten a session. After a reboot the monotonic clock restarts, so we fall back
 * to the wall clock (time the phone spent powered off counts toward the session). If the wall
 * clock moved backwards across the reboot we count zero elapsed time instead of a negative value.
 */
object SessionClock {
    fun elapsedMillis(
        startWall: Long,
        startElapsed: Long,
        startBoot: Int,
        nowWall: Long,
        nowElapsed: Long,
        nowBoot: Int,
    ): Long {
        val sameBoot = startBoot == nowBoot && nowElapsed >= startElapsed
        return if (sameBoot) nowElapsed - startElapsed else (nowWall - startWall).coerceAtLeast(0L)
    }

    fun remainingMillis(
        plannedMillis: Long,
        startWall: Long,
        startElapsed: Long,
        startBoot: Int,
        nowWall: Long,
        nowElapsed: Long,
        nowBoot: Int,
    ): Long {
        val elapsed = elapsedMillis(startWall, startElapsed, startBoot, nowWall, nowElapsed, nowBoot)
        return (plannedMillis - elapsed).coerceIn(0L, plannedMillis)
    }
}

/** Per-key rate limiter used for duplicate / rapid accessibility events. */
class Debouncer(private val windowMs: Long) {
    private val last = HashMap<String, Long>()

    /** True if the caller may act now. A backwards clock jump never blocks the caller. */
    fun shouldFire(key: String, nowMs: Long): Boolean {
        val previous = last[key]
        if (previous != null && (nowMs - previous) in 0L until windowMs) return false
        last[key] = nowMs
        if (last.size > 64) {
            val it = last.entries.iterator()
            while (it.hasNext()) {
                if (nowMs - it.next().value > windowMs * 4) it.remove()
            }
        }
        return true
    }

    fun markFired(key: String, nowMs: Long) {
        last[key] = nowMs
    }

    fun reset() = last.clear()
}
