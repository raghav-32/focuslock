package com.focuslock.app.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.app.container
import com.focuslock.app.domain.BlockAction
import com.focuslock.app.domain.BlockDecision
import com.focuslock.app.domain.BlockEvent
import com.focuslock.app.domain.BlockGate
import com.focuslock.app.domain.FeedDetector
import com.focuslock.app.domain.FeedRule
import com.focuslock.app.domain.NodeSample
import com.focuslock.app.domain.RuleEngine
import com.focuslock.app.domain.RuleKind
import com.focuslock.app.ui.block.BlockActivity
import kotlinx.coroutines.launch

/**
 * The blocking engine. Privacy rules enforced in this file:
 *  - Window-state events: only the package name is read.
 *  - Content scans run ONLY for the foreground app, ONLY if the user enabled a feed rule for it,
 *    and only read view-id names (plus short text/description when a rule uses text tokens).
 *  - Nothing read from the screen is stored or logged; only "rule X fired for package Y" is recorded.
 */
class FocusLockAccessibilityService : AccessibilityService() {

    private val gate = BlockGate()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceStatus.connected = true
        gate.reset()
        container.ruleStore.refreshEssentials()
        Log.d(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val pkg = event.packageName?.toString() ?: return
            val store = container.ruleStore
            val snapshot = store.current()
            if (pkg in snapshot.ignoredForForeground) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val changed = gate.onForeground(pkg)
                    evaluateApp(pkg, changed)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Right after the service (re)connects we have not seen a window change yet.
                    if (gate.foregroundPackage == null) gate.onForeground(pkg)
                    if (pkg != gate.foregroundPackage) return
                    if (!gate.shouldScan(pkg, now())) return
                    // Also catches a schedule/session that STARTS while the app is already open.
                    evaluateApp(pkg, foregroundChanged = false)
                }
            }
        } catch (e: Exception) {
            // A bug or a recycled node must never take the service down.
            Log.w(TAG, "event handling failed: ${e.javaClass.simpleName}")
        }
    }

    private fun evaluateApp(pkg: String, foregroundChanged: Boolean) {
        val snapshot = container.ruleStore.current()
        val wall = container.time.wallMillis()
        val decision = RuleEngine.decideApp(snapshot, pkg, wall)
        if (decision != null) {
            if (gate.shouldShowAppBlock(pkg, now(), foregroundChanged)) block(decision, label = container.discovery.labelOf(pkg))
            return
        }
        // Not blocked as a whole app: the feed rules may still apply after the screen settles.
        snapshot.feedRulesByPackage[pkg]?.let { evaluateFeeds(pkg, it) }
    }

    private fun evaluateFeeds(pkg: String, rules: List<FeedRule>) {
        val snapshot = container.ruleStore.current()
        val wall = container.time.wallMillis()
        val version = container.discovery.versionCode(pkg)
        val active = rules.filter { it.appliesToVersion(version) }
            .mapNotNull { r -> RuleEngine.decideFeed(snapshot, r, wall)?.let { r to it } }
        if (active.isEmpty()) return

        val root = rootInActiveWindow ?: return // unavailable accessibility info: fallback is whole-app rule
        try {
            if (root.packageName?.toString() != pkg) return
            val needText = active.any { it.first.textTokens.isNotEmpty() }
            val samples = collect(root, needText)
            for ((rule, decision) in active) {
                if (FeedDetector.matches(rule, samples)) {
                    if (gate.shouldShowFeedBlock(rule.id, now())) {
                        block(decision, label = rule.name)
                        performGlobalAction(GLOBAL_ACTION_BACK) // leave the feed; the block screen explains why
                    }
                    return
                }
                gate.onFeedGone(rule.id)
            }
        } finally {
            recycleCompat(root)
        }
    }

    private fun block(decision: BlockDecision, label: String) {
        val c = container
        val wall = c.time.wallMillis()
        c.scope.launch {
            runCatching {
                c.stats.recordBlock(
                    BlockEvent(
                        timestamp = wall,
                        packageName = decision.packageName,
                        kind = decision.kind,
                        ruleId = decision.ruleId,
                        ruleLabel = if (decision.kind == RuleKind.FEED) decision.label else label,
                        source = decision.source,
                        action = BlockAction.SHOWN,
                    ),
                )
            }
        }
        val intent = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(BlockActivity.EXTRA_PACKAGE, decision.packageName)
            .putExtra(BlockActivity.EXTRA_TARGET_LABEL, label)
            .putExtra(BlockActivity.EXTRA_KIND, decision.kind.name)
            .putExtra(BlockActivity.EXTRA_SOURCE, decision.source.name)
            .putExtra(BlockActivity.EXTRA_SOURCE_NAME, decision.sourceName)
            .putExtra(BlockActivity.EXTRA_ENDS_AT, decision.endsAtWall ?: 0L)
            .putExtra(BlockActivity.EXTRA_STRICT, decision.strict)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fall back to sending the user home so a blocked app never stays usable by accident.
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /** Bounded walk of the node tree; never unbounded recursion on pathological UIs. */
    private fun collect(root: AccessibilityNodeInfo, needText: Boolean): List<NodeSample> {
        val out = ArrayList<NodeSample>(64)
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            visited++
            val id = node.viewIdResourceName
            val text = if (needText) node.text?.toString()?.take(MAX_TEXT) else null
            val desc = if (needText) node.contentDescription?.toString()?.take(MAX_TEXT) else null
            if (id != null || text != null || desc != null) out += NodeSample(id, text, desc)
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    stack.addLast(child to depth + 1)
                }
            }
            if (node !== root) recycleCompat(node)
        }
        // Release anything left unvisited when the cap was hit.
        while (stack.isNotEmpty()) recycleCompat(stack.removeLast().first)
        return out
    }

    @Suppress("DEPRECATION")
    private fun recycleCompat(node: AccessibilityNodeInfo) {
        // No-op on API 33+, required for correctness on API 26-32.
        try {
            node.recycle()
        } catch (e: IllegalStateException) {
            // already recycled
        }
    }

    private fun now(): Long = android.os.SystemClock.elapsedRealtime()

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceStatus.connected = false
        gate.reset()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceStatus.connected = false
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FocusLockA11y"
        const val MAX_NODES = 700
        const val MAX_DEPTH = 40
        const val MAX_TEXT = 80
    }
}
