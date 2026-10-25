package com.focuslock.app.domain

/**
 * The only screen information FocusLock ever looks at: view-id names, short text and content
 * descriptions of nodes in an app the user attached a feed rule to. Samples live in memory for
 * the duration of one scan and are never logged or stored.
 */
data class NodeSample(
    val viewId: String?,
    val text: String?,
    val description: String?,
)

object FeedDetector {
    /**
     * Best-effort match. View ids are the primary signal (stable within an app version, but they
     * change between versions, and some apps expose none). Text tokens are optional and prone to
     * false positives (e.g. a "Shorts" tab label is visible on the home screen), which is why the
     * shipped defaults leave them empty.
     */
    fun matches(rule: FeedRule, nodes: List<NodeSample>): Boolean {
        val ids = rule.idTokens.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val texts = rule.textTokens.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (ids.isEmpty() && texts.isEmpty()) return false
        for (n in nodes) {
            val id = n.viewId?.lowercase()
            if (id != null && ids.any { id.contains(it) }) return true
            if (texts.isNotEmpty()) {
                val t = n.text?.lowercase()
                val d = n.description?.lowercase()
                if (texts.any { (t != null && t.contains(it)) || (d != null && d.contains(it)) }) return true
            }
        }
        return false
    }
}

/**
 * Starting points only. These identifiers were NOT verified against current app releases in this
 * build environment (no device available). They ship disabled and are editable in the app; always
 * test on a real device and fall back to "block the whole app" if detection is unreliable.
 */
object DefaultFeedRules {
    val all: List<FeedRule> = listOf(
        FeedRule(
            id = "youtube_shorts",
            packageName = "com.google.android.youtube",
            name = "YouTube Shorts",
            idTokens = listOf("reel_recycler", "reel_player_page_container", "reel_watch_fragment_root"),
        ),
        FeedRule(
            id = "instagram_reels",
            packageName = "com.instagram.android",
            name = "Instagram Reels",
            idTokens = listOf("clips_viewer_view_pager", "clips_viewer_fragment", "root_clips_layout"),
        ),
        FeedRule(
            id = "facebook_reels",
            packageName = "com.facebook.katana",
            name = "Facebook Reels",
            // Facebook obfuscates most ids; low confidence. Prefer the whole-app fallback.
            idTokens = listOf("reels_viewer", "reels_surface"),
        ),
    )
}
