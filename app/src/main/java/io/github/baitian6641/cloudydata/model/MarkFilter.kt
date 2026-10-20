package io.github.baitian6641.cloudydata.model

import org.json.JSONArray
import org.json.JSONObject

enum class FilterMode { KEEP, EXCLUDE }

/**
 * One selected mark identity in the current worksheet result.
 * [row] and [series] are the exact VARCHAR values displayed by the query.
 */
data class FilterMark(
    val row: String?,
    val series: String? = null,
)

/**
 * A source-side keep/exclude constraint derived from selected marks.
 * The filter stores dimension identities, never source row offsets or row data.
 */
data class MarkFilter(
    val mode: FilterMode,
    val rowFieldId: String,
    val seriesFieldId: String? = null,
    val marks: List<FilterMark>,
) {
    fun toJson(): JSONObject {
        val marksJson = JSONArray()
        marks.forEach { mark ->
            marksJson.put(
                JSONObject()
                    .put("row", mark.row ?: JSONObject.NULL)
                    .put("series", mark.series ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("mode", mode.name)
            .put("rowFieldId", rowFieldId)
            .put("seriesFieldId", seriesFieldId ?: JSONObject.NULL)
            .put("marks", marksJson)
    }

    companion object {
        const val VERSION = 1
        const val MAX_MARKS = 2001

        fun fromJson(json: JSONObject): MarkFilter {
            requireKnownKeys(json, setOf("version", "mode", "rowFieldId", "seriesFieldId", "marks"))
            val version = if (json.has("version")) json.get("version") else VERSION
            require(version is Number && version.toDouble() == VERSION.toDouble()) {
                "Unsupported mark filter version."
            }
            val mode = json.get("mode")
            require(mode is String) { "Invalid saved filter mode." }
            val rowFieldId = fieldId(json, "rowFieldId", required = true)!!
            val seriesFieldId = fieldId(json, "seriesFieldId", required = false)
            val marksJson = json.get("marks")
            require(marksJson is JSONArray && marksJson.length() in 1..MAX_MARKS) {
                "Saved filters must contain 1–$MAX_MARKS marks."
            }
            val marks = buildList {
                for (index in 0 until marksJson.length()) {
                    val mark = marksJson.get(index)
                    require(mark is JSONObject) { "Invalid saved filter mark." }
                    requireKnownKeys(mark, setOf("row", "series"))
                    val row = markValue(mark, "row", required = true)
                    val series = markValue(mark, "series", required = seriesFieldId != null)
                    require(seriesFieldId != null || series == null) {
                        "A saved series value requires a series field reference."
                    }
                    add(FilterMark(row, series))
                }
            }
            return MarkFilter(
                mode = FilterMode.valueOf(mode),
                rowFieldId = rowFieldId,
                seriesFieldId = seriesFieldId,
                // Repeated OR terms are valid and do not multiply source rows.
                // Preserve the stored entries rather than silently normalizing them.
                marks = marks,
            )
        }

        private fun requireKnownKeys(json: JSONObject, allowed: Set<String>) {
            require(json.keys().asSequence().all { it in allowed }) { "Unsupported saved filter fields." }
        }

        private fun fieldId(json: JSONObject, key: String, required: Boolean): String? {
            if (!json.has(key) || json.isNull(key)) {
                require(!required) { "A saved filter requires its row field reference." }
                return null
            }
            val id = json.get(key)
            require(id is String && id.isNotBlank()) { "Invalid saved filter field reference." }
            return id
        }

        private fun markValue(json: JSONObject, key: String, required: Boolean): String? {
            require(!required || json.has(key)) { "A saved filter mark is missing its $key value." }
            if (!json.has(key) || json.isNull(key)) return null
            val value = json.get(key)
            require(value is String) { "Saved filter values must be text or explicit NULL." }
            return value
        }
    }
}
