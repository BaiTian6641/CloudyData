package io.github.baitian6641.cloudydata.data

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

enum class ImportPhase {
    IntentRecorded,
    DataPrepared,
    DataCommitting,
    DataCommitted,
    MetadataPublished,
}

data class ImportReceipt(
    val operationId: String,
    val workbookId: String,
    val phase: ImportPhase,
    val proposedSnapshotId: String,
    val displayName: String,
    val rowCount: Long,
    val stagedBytes: Long,
    val stagedSha256: String?,
    val hadPreviousSnapshot: Boolean,
    val updatedAtEpochMs: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("operationId", operationId)
        .put("workbookId", workbookId)
        .put("phase", phase.name)
        .put("proposedSnapshotId", proposedSnapshotId)
        .put("displayName", displayName)
        .put("rowCount", rowCount)
        .put("stagedBytes", stagedBytes)
        .put("stagedSha256", stagedSha256 ?: JSONObject.NULL)
        .put("hadPreviousSnapshot", hadPreviousSnapshot)
        .put("updatedAtEpochMs", updatedAtEpochMs)

    companion object {
        fun fromJson(json: JSONObject): ImportReceipt = ImportReceipt(
            operationId = json.getString("operationId"),
            workbookId = json.getString("workbookId"),
            phase = ImportPhase.valueOf(json.getString("phase")),
            proposedSnapshotId = json.getString("proposedSnapshotId"),
            displayName = json.getString("displayName"),
            rowCount = json.getLong("rowCount"),
            stagedBytes = json.getLong("stagedBytes"),
            stagedSha256 = if (json.isNull("stagedSha256")) null else json.getString("stagedSha256"),
            hadPreviousSnapshot = json.optBoolean("hadPreviousSnapshot", false),
            updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
        )
    }
}

data class ReconciliationResult(
    val messages: List<String>,
    val retainedOperationDirectories: List<String>,
)

/**
 * Reconciles the metadata authority with data files after an interrupted
 * import. This handles the two-store boundary explicitly: a Parquet write is not
 * made transactional by a DuckDB transaction.
 */
class ImportRecovery(private val store: WorkbookStore) {

    fun reconcile(workbookId: String): ReconciliationResult {
        val workbookDir = store.workbookDir(workbookId)
        val stagingRoot = File(workbookDir, "staging")
        val operationDirs = stagingRoot.listFiles { file -> file.isDirectory }?.sortedBy { it.name }.orEmpty()
        if (operationDirs.isEmpty()) return ReconciliationResult(emptyList(), emptyList())

        val currentSnapshotId = runCatching { store.load(workbookId).snapshot?.id }.getOrNull()
        val metadataReadable = runCatching { store.load(workbookId) }.isSuccess
        if (!metadataReadable) {
            return ReconciliationResult(
                messages = listOf("Workbook metadata is unreadable; retained ${operationDirs.size} import operation(s) for diagnosis."),
                retainedOperationDirectories = operationDirs.map { it.name },
            )
        }

        val messages = mutableListOf<String>()
        val retained = mutableListOf<String>()
        operationDirs.forEach { operationDir ->
            val receipt = readReceipt(operationDir)
            if (receipt == null) {
                // Inspection staged data but never began an accepted commit.
                operationDir.deleteRecursively()
                return@forEach
            }
            when (receipt.phase) {
                ImportPhase.IntentRecorded, ImportPhase.DataPrepared -> {
                    operationDir.deleteRecursively()
                }
                ImportPhase.MetadataPublished -> {
                    operationDir.deleteRecursively()
                }
                ImportPhase.DataCommitting, ImportPhase.DataCommitted -> {
                    if (currentSnapshotId == receipt.proposedSnapshotId) {
                        messages += "Import ${receipt.displayName} was already published; cleaned its operation files."
                        operationDir.deleteRecursively()
                    } else {
                        val restored = restorePrevious(operationDir, workbookDir, receipt, currentSnapshotId)
                        if (restored.isSuccess) {
                            messages += "Recovered the previous snapshot for ${receipt.displayName} after an interrupted import."
                            operationDir.deleteRecursively()
                        } else {
                            retained += operationDir.name
                            messages += "Could not recover the previous snapshot for ${receipt.displayName}: ${restored.exceptionOrNull()?.message}"
                        }
                    }
                }
            }
        }
        return ReconciliationResult(messages, retained)
    }

    private fun restorePrevious(
        operationDir: File,
        workbookDir: File,
        receipt: ImportReceipt,
        currentSnapshotId: String?,
    ): Result<Unit> = runCatching {
        val previousDir = File(operationDir, "previous")
        val previousSnapshot = File(previousDir, "snapshot.parquet")
        val previousOriginal = File(previousDir, "original.csv")
        val finalSnapshot = File(workbookDir, "data/snapshot.parquet")
        val finalOriginal = File(workbookDir, "source/original.csv")
        if (previousSnapshot.exists()) {
            Files.move(
                previousSnapshot.toPath(),
                finalSnapshot.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            if (previousOriginal.exists()) {
                Files.move(
                    previousOriginal.toPath(),
                    finalOriginal.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }
        } else if (currentSnapshotId == null) {
            // No previous accepted snapshot existed; remove partially published files.
            finalSnapshot.delete()
            finalOriginal.delete()
        } else {
            error("the accepted snapshot ${receipt.displayName} has no retained previous file")
        }
    }

    private fun readReceipt(operationDir: File): ImportReceipt? =
        runCatching {
            ImportReceipt.fromJson(JSONObject(File(operationDir, "receipt.json").readText(StandardCharsets.UTF_8)))
        }.getOrNull()
}

object ReceiptFiles {
    fun write(operationDir: File, receipt: ImportReceipt) {
        val target = File(operationDir, "receipt.json")
        val temp = File.createTempFile("receipt-", ".tmp", operationDir)
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(receipt.toJson().toString().toByteArray(StandardCharsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun backup(previousFile: File, operationDir: File, name: String): Boolean {
        if (!previousFile.exists()) return false
        val targetDir = File(operationDir, "previous").apply { mkdirs() }
        val target = File(targetDir, name)
        Files.move(
            previousFile.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
        return true
    }
}
