package io.github.domicc.lara.core.journal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Journal lines are JSON Lines: one compact JSON object per line, keys `t el up type f ctx`. */
object JournalCodec {

    fun encode(record: JournalRecord): String {
        val map = LinkedHashMap<String, JsonElement>()
        map["t"] = JsonPrimitive(record.wallMs)
        map["el"] = JsonPrimitive(record.elapsedMs)
        map["up"] = JsonPrimitive(record.uptimeMs)
        map["type"] = JsonPrimitive(record.type)
        if (record.fields.isNotEmpty()) map["f"] = toJson(record.fields)
        if (record.ctx.isNotEmpty()) map["ctx"] = toJson(record.ctx)
        return JsonObject(map).toString()
    }

    /** Returns null for a line that is not a journal record (truncated write, foreign text). */
    fun decode(line: String): JournalRecord? = try {
        val obj = Json.parseToJsonElement(line).jsonObject
        JournalRecord(
            wallMs = obj.getValue("t").jsonPrimitive.longOrNull ?: return null,
            elapsedMs = obj.getValue("el").jsonPrimitive.longOrNull ?: return null,
            uptimeMs = obj.getValue("up").jsonPrimitive.longOrNull ?: return null,
            type = obj.getValue("type").jsonPrimitive.content,
            fields = (obj["f"] as? JsonObject)?.let(::fromJsonObject) ?: emptyMap(),
            ctx = (obj["ctx"] as? JsonObject)?.let(::fromJsonObject) ?: emptyMap(),
        )
    } catch (e: Exception) {
        null
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map(::toJson))
        is Array<*> -> JsonArray(value.map(::toJson))
        else -> JsonPrimitive(value.toString())
    }

    private fun fromJsonObject(obj: JsonObject): Map<String, Any?> =
        obj.entries.associate { (k, v) -> k to fromJson(v) }

    private fun fromJson(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> fromJsonObject(element)
        is JsonArray -> element.map(::fromJson)
        is JsonPrimitive ->
            if (element.isString) element.content
            else element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.content
    }
}
