package com.focuslock.app.domain

/**
 * Pure event-gating logic for the accessibility service, kept free of Android types so it can be
 * unit tested. It answers: "is this a NEW situation worth reacting to, or noise?"
 *
 *  - Whole-app blocks are debounced per package (duplicate window events, rapid switching).
 *  - Feed blocks are latched per rule while the user stays in the same app; the latch is cleared
 *    when the feed is no longer detected or the foreground app changes.
 *  - Content scans are throttled per package so a busy UI cannot cause a scan storm.
 */
class BlockGate(
    private val appDebounceMs: Long = 1_200L,
    private val feedLatchMs: Long = 4_000L,
    private val scanThrottleMs: Long = 350L,
) {
    private val appDebounce = Debouncer(appDebounceMs)
    private val scanDebounce = Debouncer(scanThrottleMs)
    private val feedLatchedAt = HashMap<String, Long>()

    var foregroundPackage: String? = null
        private set

    /** Returns true when the foreground app actually changed. */
    fun onForeground(pkg: String): Boolean {
        if (pkg == foregroundPackage) return false
        foregroundPackage = pkg
        feedLatchedAt.clear()
        return true
    }

    /**
     * A genuine switch INTO the app ([foregroundChanged]) is always a new event, so quickly
     * re-opening a blocked app can never slip past the debounce. Only repeated events while the
     * same app stays in front (duplicate window events) are suppressed.
     */
    fun shouldShowAppBlock(pkg: String, nowMs: Long, foregroundChanged: Boolean): Boolean {
        if (foregroundChanged) {
            appDebounce.markFired(pkg, nowMs)
            return true
        }
        return appDebounce.shouldFire(pkg, nowMs)
    }

    fun shouldScan(pkg: String, nowMs: Long): Boolean = scanDebounce.shouldFire(pkg, nowMs)

    /** True the first time a rule matches (or again after the latch expired while still visible). */
    fun shouldShowFeedBlock(ruleId: String, nowMs: Long): Boolean {
        val at = feedLatchedAt[ruleId]
        if (at != null && nowMs - at in 0L until feedLatchMs) return false
        feedLatchedAt[ruleId] = nowMs
        return true
    }

    /** The feed rule is not on screen any more, so the next appearance is a new event. */
    fun onFeedGone(ruleId: String) {
        feedLatchedAt.remove(ruleId)
    }

    fun reset() {
        foregroundPackage = null
        feedLatchedAt.clear()
        appDebounce.reset()
        scanDebounce.reset()
    }
}
