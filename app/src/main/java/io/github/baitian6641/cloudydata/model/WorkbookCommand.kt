package io.github.baitian6641.cloudydata.model

import org.json.JSONArray
import org.json.JSONObject

/** Typed, inert metadata requests. Opening a workbook never executes these records. */
sealed interface WorkbookCommand {
    val commandId: String
    val workbookId: String
    val originWindowId: String
    val expectedRevision: Long

    data class Rename(
        override val commandId: String,
        override val workbookId: String,
        override val originWindowId: String,
        override val expectedRevision: Long,
        val name: String,
    ) : WorkbookCommand

    data class SetChart(
        override val commandId: String,
        override val workbookId: String,
        override val originWindowId: String,
        override val expectedRevision: Long,
        val worksheetId: String,
        val options: ChartOptions,
    ) : WorkbookCommand

    data class AddWorksheet(
        override val commandId: String,
        override val workbookId: String,
        override val originWindowId: String,
        override val expectedRevision: Long,
        val worksheetId: String,
        val name: String,
    ) : WorkbookCommand

    data class RemoveWorksheet(
        override val commandId: String,
        override val workbookId: String,
        override val originWindowId: String,
        override val expectedRevision: Long,
        val worksheetId: String,
    ) : WorkbookCommand

    data class SetFavorite(
        override val commandId: String,
        override val workbookId: String,
        override val originWindowId: String,
        override val expectedRevision: Long,
        val value: Boolean,
    ) : WorkbookCommand

    fun validate() {
        for (id in listOf(commandId, workbookId, originWindowId)) {
            require(id.length in 1..128 && id.all { it.isLetterOrDigit() || it in "-_." }) { "Invalid command identity." }
        }
        require(expectedRevision in 1 until Long.MAX_VALUE) { "Invalid expected revision." }
        when (this) {
            is SetFavorite -> Unit
            is Rename -> require(name.isNotBlank() && name == name.trim() && name.length <= 512) { "Workbook name must contain 1–512 characters." }
            is SetChart -> {
                require(worksheetId.length in 1..128) { "Invalid worksheet identity." }
                require(ChartOptions.fromJson(options.toJson()) == options) { "Chart options are invalid or unsupported." }
            }
            is AddWorksheet -> {
                require(worksheetId.length in 1..128 && worksheetId.all { it.isLetterOrDigit() || it in "-_." }) {
                    "Invalid new worksheet identity."
                }
                require(name.isNotBlank() && name == name.trim() && name.length <= 512) {
                    "Worksheet name must contain 1–512 characters."
                }
            }
            is RemoveWorksheet -> {
                require(worksheetId.length in 1..128) { "Invalid worksheet identity." }
            }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("kind", when (this) {
            is Rename -> "rename"
            is SetChart -> "setChart"
            is AddWorksheet -> "addWorksheet"
            is RemoveWorksheet -> "removeWorksheet"
            is SetFavorite -> "setFavorite"
        })
        .put("commandId", commandId)
        .put("workbookId", workbookId)
        .put("originWindowId", originWindowId)
        .put("expectedRevision", expectedRevision)
        .apply {
            when (val command = this@WorkbookCommand) {
                is Rename -> put("name", command.name)
                is SetChart -> put("worksheetId", command.worksheetId).put("options", command.options.toJson())
                is AddWorksheet -> put("worksheetId", command.worksheetId).put("name", command.name)
                is RemoveWorksheet -> put("worksheetId", command.worksheetId)
                is SetFavorite -> put("value", command.value)
            }
        }

    companion object {
        fun fromJson(json: JSONObject): WorkbookCommand {
            val id = json.getString("commandId")
            val workbook = json.getString("workbookId")
            val window = json.getString("originWindowId")
            val revision = json.getLong("expectedRevision")
            val command = when (json.getString("kind")) {
                "rename" -> Rename(id, workbook, window, revision, json.getString("name"))
                "setChart" -> SetChart(id, workbook, window, revision, json.getString("worksheetId"), ChartOptions.fromJson(json.getJSONObject("options")))
                "addWorksheet" -> AddWorksheet(id, workbook, window, revision, json.getString("worksheetId"), json.getString("name"))
                "removeWorksheet" -> RemoveWorksheet(id, workbook, window, revision, json.getString("worksheetId"))
                "setFavorite" -> SetFavorite(id, workbook, window, revision, json.getBoolean("value"))
                else -> error("Unsupported workbook command kind.")
            }
            command.validate()
            require(canonicalJson(json) == canonicalJson(command.toJson())) { "Malformed workbook command record." }
            return command
        }
    }
}

/** Stable structural representation; numeric 1 and 1.0 use JSON's canonical number spelling. */
fun canonicalJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + canonicalJson(value.get(it))
    }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalJson(value.get(it)) }
    is Number -> JSONObject.numberToString(value)
    is Boolean -> value.toString()
    is String -> JSONObject.quote(value)
    else -> error("Unsupported JSON value.")
}

data class CommandOutcome(val document: WorkbookDocument, val acceptedRevision: Long, val duplicate: Boolean)
