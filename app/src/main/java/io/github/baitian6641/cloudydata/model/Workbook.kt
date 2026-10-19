package io.github.baitian6641.cloudydata.model

import org.json.JSONArray
import org.json.JSONObject

enum class LogicalType {
    BOOLEAN,
    INTEGER,
    DECIMAL,
    DOUBLE,
    STRING,
    DATE,
    TIMESTAMP,
    OTHER,
}

enum class FieldRole { DIMENSION, MEASURE }

enum class Aggregation { SUM, AVG, MIN, MAX, COUNT, COUNT_DISTINCT, NONE }

enum class MarkType { TABLE, BAR, LINE, AREA }

enum class LegendPosition { BOTTOM, RIGHT }

enum class LegendOrientation { VERTICAL, HORIZONTAL }

enum class LabelPosition { TOP, INSIDE_END }

enum class AxisTitleAnchor { AUTO, START, END }

data class ChartOptions(
    val title: String? = null,
    val subtitle: String? = null,
    val caption: String? = null,
    val showGridlines: Boolean = true,
    val gridlineWidth: Double? = null,
    val showValueLabels: Boolean = false,
    val xAxisTitle: String? = null,
    val yAxisTitle: String? = null,
    val seriesTitle: String? = null,
    val yMin: Double? = null,
    val yMax: Double? = null,
    val zeroBaseline: Boolean = true,
    val logScale: Boolean = false,
    val reverseY: Boolean = false,
    val legendVisible: Boolean = true,
    val legendPosition: LegendPosition = LegendPosition.BOTTOM,
    val legendOrientation: LegendOrientation? = null,
    val showTooltip: Boolean = true,
    val yTickCount: Int? = null,
    val labelFontSize: Int? = null,
    val lineWidth: Double? = null,
    val labelPosition: LabelPosition = LabelPosition.TOP,
    val units: String? = null,
    val axisTitleAnchor: AxisTitleAnchor = AxisTitleAnchor.AUTO,
    val chartPadding: Int = DEFAULT_CHART_PADDING,
    val xLabelCount: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("title", title ?: JSONObject.NULL)
        .put("subtitle", subtitle ?: JSONObject.NULL)
        .put("caption", caption ?: JSONObject.NULL)
        .put("showGridlines", showGridlines)
        .put("gridlineWidth", gridlineWidth ?: JSONObject.NULL)
        .put("showValueLabels", showValueLabels)
        .put("xAxisTitle", xAxisTitle ?: JSONObject.NULL)
        .put("yAxisTitle", yAxisTitle ?: JSONObject.NULL)
        .put("seriesTitle", seriesTitle ?: JSONObject.NULL)
        .put("yMin", yMin ?: JSONObject.NULL)
        .put("yMax", yMax ?: JSONObject.NULL)
        .put("zeroBaseline", zeroBaseline)
        .put("logScale", logScale)
        .put("reverseY", reverseY)
        .put("legendVisible", legendVisible)
        .put("legendPosition", legendPosition.name)
        .put("legendOrientation", legendOrientation?.name ?: JSONObject.NULL)
        .put("showTooltip", showTooltip)
        .put("yTickCount", yTickCount ?: JSONObject.NULL)
        .put("labelFontSize", labelFontSize ?: JSONObject.NULL)
        .put("lineWidth", lineWidth ?: JSONObject.NULL)
        .put("labelPosition", labelPosition.name)
        .put("units", units ?: JSONObject.NULL)
        .put("axisTitleAnchor", axisTitleAnchor.name)
        .put("chartPadding", chartPadding)
        .put("xLabelCount", xLabelCount ?: JSONObject.NULL)

    companion object {
        const val VERSION = 1
        const val MIN_TICK_COUNT = 2
        const val MAX_TICK_COUNT = 50
        const val MIN_LABEL_FONT_SIZE = 8
        const val MAX_LABEL_FONT_SIZE = 16
        const val MIN_LINE_WIDTH = 1.0
        const val MAX_LINE_WIDTH = 8.0
        const val MIN_GRIDLINE_WIDTH = 0.5
        const val MAX_GRIDLINE_WIDTH = 3.0
        const val MIN_CHART_PADDING = 0
        const val MAX_CHART_PADDING = 32
        const val DEFAULT_CHART_PADDING = 8

        fun fromJson(json: JSONObject): ChartOptions = ChartOptions(
            title = if (json.isNull("title")) null else json.optString("title").takeIf { it.isNotEmpty() },
            subtitle = if (json.isNull("subtitle")) null else json.optString("subtitle").takeIf { it.isNotEmpty() },
            caption = if (json.isNull("caption")) null else json.optString("caption").takeIf { it.isNotEmpty() },
            showGridlines = json.optBoolean("showGridlines", true),
            gridlineWidth = if (json.isNull("gridlineWidth")) {
                null
            } else {
                json.optDouble("gridlineWidth").takeIf {
                    it.isFinite() && it in MIN_GRIDLINE_WIDTH..MAX_GRIDLINE_WIDTH
                }
            },
            showValueLabels = json.optBoolean("showValueLabels", false),
            xAxisTitle = if (json.isNull("xAxisTitle")) null else json.optString("xAxisTitle").takeIf { it.isNotEmpty() },
            yAxisTitle = if (json.isNull("yAxisTitle")) null else json.optString("yAxisTitle").takeIf { it.isNotEmpty() },
            seriesTitle = if (json.isNull("seriesTitle")) null else json.optString("seriesTitle").takeIf { it.isNotEmpty() },
            yMin = if (json.isNull("yMin")) null else json.optDouble("yMin"),
            yMax = if (json.isNull("yMax")) null else json.optDouble("yMax"),
            zeroBaseline = json.optBoolean("zeroBaseline", true),
            logScale = json.optBoolean("logScale", false),
            reverseY = json.optBoolean("reverseY", false),
            legendVisible = json.optBoolean("legendVisible", true),
            legendPosition = runCatching {
                LegendPosition.valueOf(json.optString("legendPosition", LegendPosition.BOTTOM.name))
            }.getOrDefault(LegendPosition.BOTTOM),
            legendOrientation = if (json.isNull("legendOrientation")) {
                null
            } else {
                runCatching { LegendOrientation.valueOf(json.optString("legendOrientation")) }.getOrNull()
            },
            showTooltip = json.optBoolean("showTooltip", true),
            yTickCount = if (json.isNull("yTickCount")) {
                null
            } else {
                json.optInt("yTickCount").takeIf { it in MIN_TICK_COUNT..MAX_TICK_COUNT }
            },
            labelFontSize = if (json.isNull("labelFontSize")) {
                null
            } else {
                json.optInt("labelFontSize").takeIf { it in MIN_LABEL_FONT_SIZE..MAX_LABEL_FONT_SIZE }
            },
            lineWidth = if (json.isNull("lineWidth")) {
                null
            } else {
                json.optDouble("lineWidth").takeIf { it.isFinite() && it in MIN_LINE_WIDTH..MAX_LINE_WIDTH }
            },
            labelPosition = runCatching {
                LabelPosition.valueOf(json.optString("labelPosition", LabelPosition.TOP.name))
            }.getOrDefault(LabelPosition.TOP),
            units = if (json.isNull("units")) {
                null
            } else {
                json.optString("units").trim().take(16).takeIf { it.isNotEmpty() }
            },
            axisTitleAnchor = runCatching {
                AxisTitleAnchor.valueOf(json.optString("axisTitleAnchor", AxisTitleAnchor.AUTO.name))
            }.getOrDefault(AxisTitleAnchor.AUTO),
            chartPadding = json.optInt("chartPadding", DEFAULT_CHART_PADDING)
                .takeIf { it in MIN_CHART_PADDING..MAX_CHART_PADDING }
                ?: DEFAULT_CHART_PADDING,
            xLabelCount = if (json.isNull("xLabelCount")) {
                null
            } else {
                json.optInt("xLabelCount").takeIf { it in MIN_TICK_COUNT..MAX_TICK_COUNT }
            },
        )
    }
}

data class FieldDef(
    val id: String,
    val name: String,
    val physicalType: String,
    val logicalType: LogicalType,
    val role: FieldRole,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("physicalType", physicalType)
        .put("logicalType", logicalType.name)
        .put("role", role.name)

    companion object {
        fun fromJson(json: JSONObject): FieldDef = FieldDef(
            id = json.getString("id"),
            name = json.getString("name"),
            physicalType = json.getString("physicalType"),
            logicalType = LogicalType.valueOf(json.getString("logicalType")),
            role = FieldRole.valueOf(json.getString("role")),
        )
    }
}

data class DataSnapshot(
    val id: String,
    val displayName: String,
    val sourceKind: String,
    /** Relative path inside the workbook package. */
    val snapshotFile: String,
    /** Relative path to the staged original, when preserved. */
    val originalFile: String?,
    val importedAtEpochMs: Long,
    val rowCount: Long,
    val columns: List<FieldDef>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("displayName", displayName)
        .put("sourceKind", sourceKind)
        .put("snapshotFile", snapshotFile)
        .put("originalFile", originalFile ?: JSONObject.NULL)
        .put("importedAtEpochMs", importedAtEpochMs)
        .put("rowCount", rowCount)
        .put("columns", JSONArray(columns.map { it.toJson() }))

    companion object {
        fun fromJson(json: JSONObject): DataSnapshot {
            val columnsJson = json.getJSONArray("columns")
            return DataSnapshot(
                id = json.getString("id"),
                displayName = json.getString("displayName"),
                sourceKind = json.getString("sourceKind"),
                snapshotFile = json.getString("snapshotFile"),
                originalFile = if (json.isNull("originalFile")) null else json.getString("originalFile"),
                importedAtEpochMs = json.getLong("importedAtEpochMs"),
                rowCount = json.getLong("rowCount"),
                columns = buildList {
                    for (index in 0 until columnsJson.length()) {
                        add(FieldDef.fromJson(columnsJson.getJSONObject(index)))
                    }
                },
            )
        }
    }
}

data class WorksheetDef(
    val id: String,
    val name: String,
    val rowFieldId: String?,
    val measureFieldId: String?,
    val aggregation: Aggregation,
    val markType: MarkType,
    val sortDescending: Boolean,
    val seriesFieldId: String? = null,
    val chart: ChartOptions = ChartOptions(),
    val filter: MarkFilter? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("rowFieldId", rowFieldId ?: JSONObject.NULL)
        .put("measureFieldId", measureFieldId ?: JSONObject.NULL)
        .put("aggregation", aggregation.name)
        .put("markType", markType.name)
        .put("sortDescending", sortDescending)
        .put("seriesFieldId", seriesFieldId ?: JSONObject.NULL)
        .put("chart", chart.toJson())
        .put("filter", filter?.toJson() ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): WorksheetDef {
            val markType = MarkType.valueOf(json.getString("markType"))
            val parsedChart = json.optJSONObject("chart")?.let { ChartOptions.fromJson(it) }
                ?: ChartOptions(
                    title = if (json.isNull("chartTitle")) null else json.optString("chartTitle").takeIf { it.isNotEmpty() },
                    showGridlines = json.optBoolean("showGridlines", true),
                    showValueLabels = json.optBoolean("showValueLabels", false),
                )
            return WorksheetDef(
                id = json.getString("id"),
            name = json.getString("name"),
            rowFieldId = if (json.isNull("rowFieldId")) null else json.getString("rowFieldId"),
            measureFieldId = if (json.isNull("measureFieldId")) null else json.getString("measureFieldId"),
            aggregation = Aggregation.valueOf(json.getString("aggregation")),
                markType = markType,
                sortDescending = json.getBoolean("sortDescending"),
                seriesFieldId = if (json.isNull("seriesFieldId")) null else json.optString("seriesFieldId").takeIf { it.isNotEmpty() },
                // Log/legend line settings must not make a bar document unrenderable.
                chart = if (markType == MarkType.LINE) parsedChart else parsedChart.copy(logScale = false),
                filter = runCatching {
                    json.optJSONObject("filter")?.let { MarkFilter.fromJson(it) }
                }.getOrNull()?.takeIf { it.marks.isNotEmpty() },
            )
        }
    }
}

data class WorkbookDocument(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val id: String,
    val name: String,
    val revision: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val snapshot: DataSnapshot?,
    val worksheets: List<WorksheetDef>,
    val activeWorksheetId: String?,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    val activeWorksheet: WorksheetDef?
        get() = worksheets.firstOrNull { it.id == activeWorksheetId } ?: worksheets.firstOrNull()

    fun toJson(): JSONObject = JSONObject()
        .put("formatVersion", formatVersion)
        .put("id", id)
        .put("name", name)
        .put("revision", revision)
        .put("createdAtEpochMs", createdAtEpochMs)
        .put("updatedAtEpochMs", updatedAtEpochMs)
        .put("snapshot", snapshot?.toJson() ?: JSONObject.NULL)
        .put("worksheets", JSONArray(worksheets.map { it.toJson() }))
        .put("activeWorksheetId", activeWorksheetId ?: JSONObject.NULL)
        .put("favorite", favorite)
        .put("tags", JSONArray(tags))

    companion object {
        const val CURRENT_FORMAT_VERSION = 2

        fun fromJson(json: JSONObject): WorkbookDocument {
            val version = json.getInt("formatVersion")
            require(version <= CURRENT_FORMAT_VERSION) {
                "Workbook format $version is newer than this build supports ($CURRENT_FORMAT_VERSION)."
            }
            val sheetsJson = json.getJSONArray("worksheets")
            return WorkbookDocument(
                // Normalize accepted v1 documents to the current shape so a
                // subsequent save cannot write new options under format v1.
                formatVersion = CURRENT_FORMAT_VERSION,
                id = json.getString("id"),
                name = json.getString("name"),
                revision = json.getLong("revision"),
                createdAtEpochMs = json.getLong("createdAtEpochMs"),
                updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
                snapshot = if (json.isNull("snapshot")) null else DataSnapshot.fromJson(json.getJSONObject("snapshot")),
                worksheets = buildList {
                    for (index in 0 until sheetsJson.length()) {
                        add(WorksheetDef.fromJson(sheetsJson.getJSONObject(index)))
                    }
                },
                activeWorksheetId = if (json.isNull("activeWorksheetId")) null else json.getString("activeWorksheetId"),
                favorite = json.optBoolean("favorite", false),
                tags = buildList {
                    val tagsJson = json.optJSONArray("tags") ?: JSONArray()
                    for (index in 0 until tagsJson.length()) add(tagsJson.getString(index))
                },
            )
        }
    }
}

data class WorkbookSummary(
    val id: String,
    val name: String,
    val updatedAtEpochMs: Long,
    val sourceDisplayName: String?,
    val rowCount: Long?,
    val hasSnapshot: Boolean,
    val favorite: Boolean,
)
