package io.github.baitian6641.cloudydata.data

import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.canonicalJson
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class CommandReceipt(val command: WorkbookCommand, val acceptedRevision: Long)

/** One atomic envelope, not an executable replay log or a second metadata store. */
data class WorkbookCheckpoint(
    val document: WorkbookDocument,
    val undo: List<WorkbookDocument> = emptyList(),
    val redo: List<WorkbookDocument> = emptyList(),
    val receipts: List<CommandReceipt> = emptyList(),
) {
    fun toJson(): JSONObject {
        validate()
        val journal = JSONObject()
            .put("version", VERSION)
            .put("headSha256", digest(document.toJson()))
            .put("undo", JSONArray(undo.map { it.toJson() }))
            .put("redo", JSONArray(redo.map { it.toJson() }))
            .put("receipts", JSONArray(receipts.map {
                JSONObject().put("command", it.command.toJson()).put("acceptedRevision", it.acceptedRevision)
            }))
        journal.put("sha256", digest(journal))
        return document.toJson().put("commandJournal", journal)
    }

    fun validate() {
        document.validateWorksheetIdentities()
        require(undo.size + redo.size <= HISTORY_LIMIT) { "Workbook history exceeds its limit." }
        require(receipts.size <= RECEIPT_LIMIT) { "Workbook command receipt limit reached." }
        require(document.revision in 1 until Long.MAX_VALUE) { "Invalid workbook revision." }
        for (entry in undo + redo) {
            entry.validateWorksheetIdentities()
            require(entry.id == document.id && entry.snapshot == document.snapshot && entry.revision in 1..document.revision) {
                "Workbook history has a foreign workbook, snapshot or revision."
            }
            require(entry.createdAtEpochMs == document.createdAtEpochMs) { "Workbook history changed creation identity." }
        }
        require(undo.zipWithNext().all { (a, b) -> a.revision < b.revision }) { "Workbook undo revisions are out of order." }
        require(redo.zipWithNext().all { (a, b) -> a.revision < b.revision }) { "Workbook redo revisions are out of order." }
        require(receipts.map { it.command.commandId }.distinct().size == receipts.size) { "Duplicate stored command identity." }
        require(receipts.zipWithNext().all { (a, b) -> a.acceptedRevision < b.acceptedRevision }) { "Command receipts are out of order." }
        receipts.forEach {
            it.command.validate()
            require(it.command.workbookId == document.id && it.acceptedRevision == it.command.expectedRevision + 1 &&
                it.acceptedRevision in 1..document.revision) { "Invalid stored command revision/ownership." }
        }
    }

    companion object {
        const val VERSION = 1
        const val HISTORY_LIMIT = 50
        const val RECEIPT_LIMIT = 4096

        fun fromJson(json: JSONObject): WorkbookCheckpoint {
            val version = WorkbookDocument.readFormatVersion(json)
            val document = WorkbookDocument.fromJson(json)
            if (version < 3 && !json.has("commandJournal")) return WorkbookCheckpoint(document).also { it.validate() }
            require(version == 3 && json.has("commandJournal")) { "Workbook command checkpoint is missing or unsupported." }
            val rawDocument = JSONObject(json.toString()).apply { remove("commandJournal") }
            require(canonicalJson(rawDocument) == canonicalJson(document.toJson())) { "Malformed workbook checkpoint content." }
            val journal = json.getJSONObject("commandJournal")
            require(journal.getInt("version") == VERSION) { "Unsupported workbook journal version." }
            val unsigned = JSONObject(journal.toString()).apply { remove("sha256") }
            require(journal.getString("sha256") == digest(unsigned)) { "Workbook journal checksum failed." }
            require(journal.getString("headSha256") == digest(document.toJson())) { "Workbook checkpoint does not match its journal." }
            fun history(key: String): List<WorkbookDocument> {
                val array = journal.getJSONArray(key)
                require(array.length() <= HISTORY_LIMIT) { "Workbook history exceeds its limit." }
                return (0 until array.length()).map { index ->
                    val raw = array.getJSONObject(index)
                    WorkbookDocument.fromJson(raw).also {
                        require(canonicalJson(raw) == canonicalJson(it.toJson())) { "Malformed history checkpoint." }
                    }
                }
            }
            val receiptJson = journal.getJSONArray("receipts")
            require(receiptJson.length() <= RECEIPT_LIMIT) { "Workbook command receipt limit reached." }
            return WorkbookCheckpoint(document, history("undo"), history("redo"), (0 until receiptJson.length()).map {
                val raw = receiptJson.getJSONObject(it)
                CommandReceipt(WorkbookCommand.fromJson(raw.getJSONObject("command")), raw.getLong("acceptedRevision"))
            }).also { checkpoint ->
                checkpoint.validate()
                require(canonicalJson(json) == canonicalJson(checkpoint.toJson())) { "Malformed workbook journal envelope." }
            }
        }

        private fun digest(json: JSONObject): String = MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson(json).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
