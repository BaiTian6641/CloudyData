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
            require(json.optInt("version", VERSION) in 1..VERSION) {
                "Unsupported mark filter version."
            }
            val marksJson = json.optJSONArray("marks") ?: JSONArray()
            val marks = buildList {
                for (index in 0 until marksJson.length().coerceAtMost(MAX_MARKS)) {
                    val mark = marksJson.optJSONObject(index) ?: continue
                    add(
                        FilterMark(
                            row = if (mark.isNull("row")) null else mark.optString("row"),
                            series = if (mark.isNull("series")) null else mark.optString("series"),
                        ),
                    )
                }
            }
            return MarkFilter(
                mode = FilterMode.valueOf(json.getString("mode")),
                rowFieldId = json.getString("rowFieldId"),
                seriesFieldId = if (json.isNull("seriesFieldId")) null else json.optString("seriesFieldId").takeIf { it.isNotEmpty() },
                marks = marks.distinct(),
            )
        }
    }
}
