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

enum class MarkType { TABLE, BAR, LINE }

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
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("rowFieldId", rowFieldId ?: JSONObject.NULL)
        .put("measureFieldId", measureFieldId ?: JSONObject.NULL)
        .put("aggregation", aggregation.name)
        .put("markType", markType.name)
        .put("sortDescending", sortDescending)

    companion object {
        fun fromJson(json: JSONObject): WorksheetDef = WorksheetDef(
            id = json.getString("id"),
            name = json.getString("name"),
            rowFieldId = if (json.isNull("rowFieldId")) null else json.getString("rowFieldId"),
            measureFieldId = if (json.isNull("measureFieldId")) null else json.getString("measureFieldId"),
            aggregation = Aggregation.valueOf(json.getString("aggregation")),
            markType = MarkType.valueOf(json.getString("markType")),
            sortDescending = json.getBoolean("sortDescending"),
        )
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
        const val CURRENT_FORMAT_VERSION = 1

        fun fromJson(json: JSONObject): WorkbookDocument {
            val version = json.getInt("formatVersion")
            require(version <= CURRENT_FORMAT_VERSION) {
                "Workbook format $version is newer than this build supports ($CURRENT_FORMAT_VERSION)."
            }
            val sheetsJson = json.getJSONArray("worksheets")
            return WorkbookDocument(
                formatVersion = version,
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
