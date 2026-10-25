package org.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// TEST-HARNESS ONLY stand-in for Android's org.json (not shipped in the app).
class JSONException(message: String) : Exception(message)

private fun toEl(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JSONObject -> v.toJson()
    is JSONArray -> v.toJson()
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    else -> JsonPrimitive(v.toString())
}

private fun fromEl(e: JsonElement): Any? = when (e) {
    is JsonNull -> null
    is JsonObject -> JSONObject(e)
    is JsonArray -> JSONArray().also { a -> e.forEach { a.put(fromEl(it)) } }
    is JsonPrimitive -> when {
        e.isString -> e.content
        e.content == "true" || e.content == "false" -> e.content.toBoolean()
        e.content.toLongOrNull() != null -> e.content.toLong()
        else -> e.content.toDouble()
    }
}

class JSONArray() {
    internal val list = ArrayList<Any?>()
    constructor(c: Collection<*>) : this() { list.addAll(c) }
    fun put(v: Any?): JSONArray { list.add(v); return this }
    fun length() = list.size
    fun optJSONObject(i: Int): JSONObject? = list.getOrNull(i) as? JSONObject
    fun optString(i: Int, d: String): String = when (val v = list.getOrNull(i)) { null -> d; is String -> v; else -> v.toString() }
    internal fun toJson(): JsonElement = JsonArray(list.map { toEl(it) })
}

class JSONObject() {
    internal val map = LinkedHashMap<String, Any?>()
    internal constructor(o: JsonObject) : this() { o.forEach { (k, v) -> map[k] = fromEl(v) } }
    constructor(text: String) : this() {
        val el = try { Json.parseToJsonElement(text) } catch (e: Exception) { throw JSONException("bad json") }
        if (el !is JsonObject) throw JSONException("not an object")
        el.forEach { (k, v) -> map[k] = fromEl(v) }
    }
    fun put(k: String, v: Any?): JSONObject { map[k] = v; return this }
    fun has(k: String) = map.containsKey(k)
    fun optString(k: String): String = optString(k, "")
    fun optString(k: String, d: String): String = when (val v = map[k]) { null -> d; is String -> v; else -> v.toString() }
    fun optInt(k: String, d: Int): Int = (map[k] as? Number)?.toInt() ?: d
    fun optInt(k: String): Int = optInt(k, 0)
    fun optLong(k: String, d: Long): Long = (map[k] as? Number)?.toLong() ?: d
    fun optLong(k: String): Long = optLong(k, 0L)
    fun optBoolean(k: String, d: Boolean): Boolean = (map[k] as? Boolean) ?: d
    fun optBoolean(k: String): Boolean = optBoolean(k, false)
    fun optJSONArray(k: String): JSONArray? = map[k] as? JSONArray
    fun optJSONObject(k: String): JSONObject? = map[k] as? JSONObject
    internal fun toJson(): JsonElement = JsonObject(map.mapValues { toEl(it.value) })
    fun toString(indent: Int): String =
        Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), toJson())
    override fun toString(): String = Json.encodeToString(JsonElement.serializer(), toJson())
}
