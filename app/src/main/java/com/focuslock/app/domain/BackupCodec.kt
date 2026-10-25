package com.focuslock.app.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

sealed interface BackupResult {
    data class Ok(val data: BackupData) : BackupResult
    data class Invalid(val reason: String) : BackupResult
}

/**
 * JSON backup format (schemaVersion 1) with strict validation on import: size cap, version check,
 * enum / range / package-name validation and per-collection item limits. Nothing in a backup is
 * ever executed or trusted as code.
 */
object BackupCodec {
    const val SCHEMA = 1
    const val MAX_CHARS = 2_000_000

    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    private class FormatError(message: String) : Exception(message)

    private fun fail(message: String): Nothing = throw FormatError(message)

    // ---- encode ----------------------------------------------------------------------------

    fun encode(d: BackupData): String {
        val root = JSONObject()
        root.put("app", "FocusLock")
        root.put("schemaVersion", SCHEMA)
        root.put("exportedAt", d.exportedAt)

        root.put("blockedApps", JSONArray().also { arr ->
            d.blockedApps.forEach {
                arr.put(JSONObject().put("package", it.packageName).put("enabled", it.enabled).put("scope", it.scope.name))
            }
        })
        root.put("feedRules", JSONArray().also { arr ->
            d.feedRules.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id).put("package", it.packageName).put("name", it.name)
                        .put("enabled", it.enabled).put("scope", it.scope.name)
                        .put("idTokens", JSONArray(it.idTokens)).put("textTokens", JSONArray(it.textTokens))
                        .put("fallbackWholeApp", it.fallbackWholeApp)
                        .put("minVersion", it.minVersion).put("maxVersion", it.maxVersion),
                )
            }
        })
        root.put("groups", JSONArray().also { arr ->
            d.groups.forEach {
                arr.put(
                    JSONObject().put("name", it.name).put("members", JSONArray(it.members.toList()))
                        .put("presetKey", it.presetKey ?: ""),
                )
            }
        })
        root.put("schedules", JSONArray().also { arr ->
            d.schedules.forEach { bs ->
                val s = bs.schedule
                arr.put(
                    JSONObject()
                        .put("name", s.name).put("startMinute", s.startMinute).put("endMinute", s.endMinute)
                        .put("daysMask", s.daysMask).put("enabled", s.enabled)
                        .put("packages", JSONArray(s.packages.toList()))
                        .put("groupNames", JSONArray(bs.groupNames.toList()))
                        .put("feedRuleIds", JSONArray(s.feedRuleIds.toList()))
                        .put("exceptions", JSONArray(s.exceptionPackages.toList()))
                        .put("requireFocusSession", s.requireFocusSession),
                )
            }
        })
        root.put("blockEvents", JSONArray().also { arr ->
            d.blockEvents.forEach {
                arr.put(
                    JSONObject().put("t", it.timestamp).put("package", it.packageName)
                        .put("kind", it.kind.name).put("ruleId", it.ruleId).put("label", it.ruleLabel)
                        .put("source", it.source.name).put("action", it.action.name),
                )
            }
        })
        root.put("dailyUsage", JSONArray().also { arr ->
            d.dailyUsage.forEach {
                arr.put(JSONObject().put("day", it.epochDay).put("package", it.packageName).put("ms", it.foregroundMillis))
            }
        })
        return root.toString(2)
    }

    // ---- decode + validate -----------------------------------------------------------------

    fun parse(text: String): BackupResult {
        if (text.length > MAX_CHARS) return BackupResult.Invalid("The file is too large to be a FocusLock backup.")
        return try {
            BackupResult.Ok(parseInternal(JSONObject(text)))
        } catch (e: FormatError) {
            BackupResult.Invalid(e.message ?: "Invalid backup.")
        } catch (e: JSONException) {
            BackupResult.Invalid("This is not a valid FocusLock backup file.")
        }
    }

    private fun parseInternal(root: JSONObject): BackupData {
        if (root.optString("app") != "FocusLock") fail("This file was not created by FocusLock.")
        if (root.optInt("schemaVersion", -1) != SCHEMA) fail("Unsupported backup version.")

        val blocked = objects(root, "blockedApps", 5000).map {
            BlockedApp(pkg(it.optString("package")), it.optBoolean("enabled", true), enumOf<BlockScope>(it, "scope"))
        }
        val feeds = objects(root, "feedRules", 200).map {
            val id = it.str("id", 64).also { v -> if (v.isBlank()) fail("Feed rule without id.") }
            FeedRule(
                id = id,
                packageName = pkg(it.optString("package")),
                name = it.str("name", 80).ifBlank { fail("Feed rule without name.") },
                enabled = it.optBoolean("enabled", false),
                scope = enumOf(it, "scope"),
                idTokens = strings(it, "idTokens", 50) { s -> token(s) },
                textTokens = strings(it, "textTokens", 50) { s -> token(s) },
                fallbackWholeApp = it.optBoolean("fallbackWholeApp", false),
                minVersion = it.longIn("minVersion", 0L, Long.MAX_VALUE / 2),
                maxVersion = it.longIn("maxVersion", 0L, Long.MAX_VALUE / 2),
            )
        }
        val groups = objects(root, "groups", 200).map {
            BackupGroup(
                name = it.str("name", 60).ifBlank { fail("Group without name.") },
                members = strings(it, "members", 5000) { s -> pkg(s) }.toSet(),
                presetKey = it.optString("presetKey", "").ifBlank { null },
            )
        }
        val groupNames = groups.map { it.name }.toSet()
        val schedules = objects(root, "schedules", 200).map {
            val names = strings(it, "groupNames", 200) { s -> s }.toSet()
            if (!groupNames.containsAll(names)) fail("A schedule refers to an unknown group.")
            BackupSchedule(
                Schedule(
                    name = it.str("name", 60).ifBlank { fail("Schedule without name.") },
                    startMinute = it.intIn("startMinute", 0..1439),
                    endMinute = it.intIn("endMinute", 0..1439),
                    daysMask = it.intIn("daysMask", 0..127),
                    enabled = it.optBoolean("enabled", true),
                    packages = strings(it, "packages", 5000) { s -> pkg(s) }.toSet(),
                    feedRuleIds = strings(it, "feedRuleIds", 200) { s -> s.take(64) }.toSet(),
                    exceptionPackages = strings(it, "exceptions", 5000) { s -> pkg(s) }.toSet(),
                    requireFocusSession = it.optBoolean("requireFocusSession", false),
                ),
                names,
            )
        }
        val events = objects(root, "blockEvents", 20_000).map {
            BlockEvent(
                timestamp = it.longIn("t", 0L, Long.MAX_VALUE / 2),
                packageName = pkg(it.optString("package")),
                kind = enumOf(it, "kind"),
                ruleId = it.str("ruleId", 64),
                ruleLabel = it.str("label", 80),
                source = enumOf(it, "source"),
                action = enumOf(it, "action"),
            )
        }
        val usage = objects(root, "dailyUsage", 20_000).map {
            DailyUsage(
                epochDay = it.longIn("day", 0L, 1_000_000L),
                packageName = pkg(it.optString("package")),
                foregroundMillis = it.longIn("ms", 0L, 86_400_000L),
            )
        }
        return BackupData(root.optLong("exportedAt", 0L), blocked, feeds, groups, schedules, events, usage)
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun pkg(s: String): String {
        if (s.length > 255 || !PACKAGE.matches(s)) fail("Invalid app identifier in backup.")
        return s
    }

    private fun token(s: String): String {
        if (s.length > 120) fail("A detection token is too long.")
        return s
    }

    private fun objects(root: JSONObject, key: String, max: Int): List<JSONObject> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        if (arr.length() > max) fail("Too many entries in $key.")
        return (0 until arr.length()).map { arr.optJSONObject(it) ?: fail("Malformed entry in $key.") }
    }

    private fun strings(o: JSONObject, key: String, max: Int, each: (String) -> String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        if (arr.length() > max) fail("Too many entries in $key.")
        return (0 until arr.length()).map { each(arr.optString(it, "")) }
    }

    private fun JSONObject.str(key: String, max: Int): String {
        val v = optString(key, "")
        if (v.length > max) fail("$key is too long.")
        return v
    }

    private fun JSONObject.intIn(key: String, range: IntRange): Int {
        if (!has(key)) fail("Missing $key.")
        val v = optInt(key, Int.MIN_VALUE)
        if (v !in range) fail("$key is out of range.")
        return v
    }

    private fun JSONObject.longIn(key: String, min: Long, max: Long): Long {
        val v = optLong(key, min)
        if (v < min || v > max) fail("$key is out of range.")
        return v
    }

    private inline fun <reified E : Enum<E>> enumOf(o: JSONObject, key: String): E =
        try {
            enumValueOf<E>(o.optString(key))
        } catch (e: IllegalArgumentException) {
            fail("Unknown value for $key.")
        }

    // ---- CSV export of statistics ----------------------------------------------------------

    fun statsCsv(events: List<BlockEvent>, usage: List<DailyUsage>, zone: ZoneId): String {
        val sb = StringBuilder()
        sb.append("# Block events (measured: each row is one time FocusLock showed or bypassed a block)\n")
        sb.append("timestamp,package,kind,rule,source,action\n")
        for (e in events) {
            sb.append(Instant.ofEpochMilli(e.timestamp).atZone(zone).toOffsetDateTime()).append(',')
                .append(csv(e.packageName)).append(',').append(e.kind).append(',')
                .append(csv(e.ruleLabel.ifBlank { e.ruleId })).append(',').append(e.source).append(',')
                .append(e.action).append('\n')
        }
        sb.append("\n# Daily foreground usage (measured via Usage Access)\n")
        sb.append("epoch_day,package,foreground_ms\n")
        for (u in usage) sb.append(u.epochDay).append(',').append(csv(u.packageName)).append(',').append(u.foregroundMillis).append('\n')
        return sb.toString()
    }

    private fun csv(s: String): String {
        // Neutralize spreadsheet formula injection as well as CSV metacharacters.
        val safe = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
