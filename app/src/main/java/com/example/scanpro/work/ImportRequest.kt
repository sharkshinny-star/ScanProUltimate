package com.example.scanpro.work

import androidx.work.Data
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a camera scan asks [ImportScanWorker] to import: strings and string arrays (photo URIs, mode, and per page a
 * crop, filter and slider settings). Unlike WorkManager's [Data], which refuses more than 10 KB (about 60 pages),
 * it holds any number of pages; [toWorkData] gives the Data form when it fits, and otherwise the request travels as a
 * file in the scan's folder ([ImportScanWorker.enqueue]).
 */
class ImportRequest private constructor(private val values: Map<String, Any?>) {
    fun getString(key: String): String? = values[key] as? String

    fun getNullableStringArray(key: String): Array<String?>? =
        (values[key] as? Array<*>)?.map { it as? String }?.toTypedArray()

    fun getStringArray(key: String): Array<String?>? = getNullableStringArray(key)

    /** WorkManager's form, or null when the request is over its 10 KB limit. */
    fun toWorkData(): Data? = runCatching {
        Data.Builder().putAll(values).build().also { it.toByteArray() }
    }.getOrNull()

    fun toJson(): String = JSONObject().apply {
        values.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Array<*> -> put(key, JSONArray().apply { value.forEach { put(it?.toString() ?: "") } })
            }
        }
    }.toString()

    class Builder {
        private val values = linkedMapOf<String, Any?>()
        fun putString(key: String, value: String?) = apply { values[key] = value }
        fun putStringArray(key: String, value: Array<String?>) = apply { values[key] = value }
        fun putAll(other: ImportRequest) = apply { values.putAll(other.values) }
        fun build() = ImportRequest(values.toMap())
    }

    companion object {
        fun fromData(data: Data): ImportRequest = ImportRequest(data.keyValueMap)

        fun fromJson(json: String): ImportRequest {
            val obj = JSONObject(json)
            val builder = Builder()
            obj.keys().forEach { key ->
                when (val value = obj.get(key)) {
                    is JSONArray -> builder.putStringArray(key, Array(value.length()) { value.optString(it) })
                    else -> builder.putString(key, value.toString())
                }
            }
            return builder.build()
        }

        /** A saved request: JSON, or WorkManager's own bytes as written before large requests were possible. */
        fun readFile(file: File): ImportRequest? {
            val bytes = file.readBytes()
            if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) return runCatching { fromJson(String(bytes, Charsets.UTF_8)) }.getOrNull()
            return runCatching { fromData(Data.fromByteArray(bytes)) }.getOrNull()
        }
    }
}
