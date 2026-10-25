package com.focuslock.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StatsCalculator {
    fun startOfDayMillis(day: LocalDate, zone: ZoneId): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Oldest day first, ending at [endDay]; days without events appear with 0. */
    fun dailyCounts(
        timestamps: List<Long>,
        zone: ZoneId,
        endDay: LocalDate,
        days: Int,
    ): List<Pair<LocalDate, Int>> {
        val counts = HashMap<LocalDate, Int>()
        for (t in timestamps) {
            val d = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            counts[d] = (counts[d] ?: 0) + 1
        }
        return (days - 1 downTo 0).map { off ->
            val d = endDay.minusDays(off.toLong())
            d to (counts[d] ?: 0)
        }
    }

    fun hourlyCounts(timestamps: List<Long>, zone: ZoneId, day: LocalDate): List<Int> {
        val out = IntArray(24)
        for (t in timestamps) {
            val z = Instant.ofEpochMilli(t).atZone(zone)
            if (z.toLocalDate() == day) out[z.hour]++
        }
        return out.toList()
    }

    /** Null when there is no previous value to compare against. */
    fun percentChange(previous: Double, current: Double): Double? =
        if (previous <= 0.0) null else (current - previous) / previous * 100.0

    /** An ESTIMATE: blocked attempts x a user-configurable minutes-per-attempt baseline. */
    fun timeSavedMinutes(blockCount: Int, minutesPerBlock: Int): Int =
        blockCount * minutesPerBlock.coerceAtLeast(0)

    /** Time actually spent in a session (capped at the planned length). */
    fun sessionMillis(session: FocusSession, nowWall: Long): Long {
        val end = session.endedWall ?: nowWall
        return (end - session.startWall).coerceIn(0L, session.plannedMillis)
    }

    fun mostFrequent(items: List<String>): Pair<String, Int>? =
        items.groupingBy { it }.eachCount().maxByOrNull { it.value }?.let { it.key to it.value }
}

object BypassPolicy {
    fun canRequest(settings: AppSettings, strictSession: Boolean, nowWall: Long): Boolean {
        if (settings.bypassMode == BypassMode.NONE || strictSession) return false
        return cooldownRemainingMillis(settings, nowWall) == 0L
    }

    /** Cooldown starts when the previous bypass expired. */
    fun cooldownRemainingMillis(settings: AppSettings, nowWall: Long): Long {
        val readyAt = settings.lastBypassEndWall + settings.reopenCooldownMinutes * 60_000L
        return (readyAt - nowWall).coerceAtLeast(0L)
    }

    fun grantUntil(nowWall: Long, minutes: Int): Long = nowWall + minutes.coerceAtLeast(1) * 60_000L
}

/** Whether rules may be edited right now ("quick unblock subject to active lock rules"). */
object LockPolicy {
    fun canEditRules(strictSessionActive: Boolean): Boolean = !strictSessionActive
}

fun formatDuration(ms: Long): String {
    if (ms < 60_000L) return "${ms / 1000}s"
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
}

data class Preset(
    val key: String,
    val title: String,
    val description: String,
    val knownPackages: Set<String>,
    val includeGameCategory: Boolean = false,
)

object PresetCatalog {
    /** ApplicationInfo.CATEGORY_GAME == 0 (API 26+). */
    private const val CATEGORY_GAME = 0

    val all: List<Preset> = listOf(
        Preset(
            "social", "Social Media", "Social networks and messaging feeds.",
            setOf(
                "com.instagram.android", "com.facebook.katana", "com.facebook.lite",
                "com.twitter.android", "com.snapchat.android", "com.reddit.frontpage",
                "com.pinterest", "com.linkedin.android", "com.tumblr", "org.telegram.messenger",
            ),
        ),
        Preset(
            "short_video", "Short Videos", "Apps built around endless short clips. YouTube Shorts / Reels are handled by Feed Rules.",
            setOf(
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "in.mohalla.video",
                "com.eterno.shortvideos", "com.instagram.barcelona",
            ),
        ),
        Preset("games", "Games", "Every installed app the system categorizes as a game.", emptySet(), includeGameCategory = true),
        Preset(
            "entertainment", "Entertainment", "Streaming and video apps.",
            setOf(
                "com.netflix.mediaclient", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient",
                "in.startv.hotstar", "com.google.android.youtube", "tv.twitch.android.app",
                "com.hbo.hbonow", "com.spotify.music",
            ),
        ),
        Preset(
            "shopping", "Shopping", "Marketplaces and shopping apps.",
            setOf(
                "com.amazon.mShop.android.shopping", "com.flipkart.android", "com.daraz.android",
                "com.ebay.mobile", "com.alibaba.aliexpresshd", "com.einnovation.temu", "com.shopee.id",
            ),
        ),
    )

    fun matching(preset: Preset, installed: List<InstalledApp>): List<InstalledApp> =
        installed.filter {
            it.packageName in preset.knownPackages ||
                (preset.includeGameCategory && it.category == CATEGORY_GAME)
        }.sortedBy { it.label.lowercase() }
}
