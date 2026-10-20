package io.github.baitian6641.cloudydata.data

import android.util.Log
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorkbookSummary
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.json.JSONObject
import java.util.UUID

enum class WorkbookCommitPhase { TEMP_SYNCED, REPLACED }

class WorkbookStore(
    rootDir: File,
    private val commitObserver: (WorkbookCommitPhase) -> Unit = {},
) {
    val root: File = File(rootDir, "workbooks").apply { mkdirs() }
    val coordinators: WorkbookRegistry by lazy { WorkbookRegistry(this) }
    private val writeLock = synchronized(writeLocks) { writeLocks.getOrPut(root.canonicalPath) { WriteState() } }

    fun create(name: String): WorkbookDocument {
        val trimmed = name.trim().ifEmpty { "Untitled workbook" }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val sheetId = UUID.randomUUID().toString()
        val document = WorkbookDocument(
            id = id,
            name = trimmed,
            revision = 1,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            snapshot = null,
            worksheets = listOf(
                WorksheetDef(
                    id = sheetId,
                    name = "Sheet 1",
                    rowFieldId = null,
                    measureFieldId = null,
                    aggregation = Aggregation.SUM,
                    markType = MarkType.TABLE,
                    sortDescending = true,
                ),
            ),
            activeWorksheetId = sheetId,
        )
        workbookDir(id).mkdirs()
        File(workbookDir(id), "data").mkdirs()
        File(workbookDir(id), "source").mkdirs()
        return save(document)
    }

    /** Raw saves/import publication are durable history barriers, preserving accepted IDs. */
    fun save(document: WorkbookDocument): WorkbookDocument = synchronized(writeLock) {
        val existing = metadataFile(document.id).takeIf { it.exists() }?.let { loadCheckpoint(document.id) }
        writeCheckpoint(WorkbookCheckpoint(document, receipts = existing?.receipts.orEmpty())).document
    }

    fun saveCheckpoint(checkpoint: WorkbookCheckpoint): WorkbookCheckpoint = synchronized(writeLock) {
        writeCheckpoint(checkpoint)
    }

    private fun writeCheckpoint(checkpoint: WorkbookCheckpoint): WorkbookCheckpoint {
        val document = checkpoint.document
        require(isSafeId(document.id)) { "invalid workbook id" }
        val metadata = metadataFile(document.id)
        if (metadata.exists()) {
            val actual = loadCheckpoint(document.id).document.revision
            if (actual != document.revision) throw WorkbookConflictException(document.revision, actual)
        }
        val dir = workbookDir(document.id)
        if (!dir.exists() && !dir.mkdirs()) error("Could not create workbook directory")
        // Existing field/worksheet normalization is performed before acceptance,
        // so the saved object and a fresh reopen describe exactly the same content.
        val normalized = WorkbookDocument.fromJson(document.toJson())
        val next = checkpoint.copy(document = normalized.copy(
            revision = Math.addExact(document.revision, 1),
            updatedAtEpochMs = System.currentTimeMillis(),
        ))
        val bytes = next.toJson().toString().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_METADATA_BYTES) { "Workbook metadata exceeds the 32 MiB limit." }
        if (metadata.exists()) {
            val oldVersion = WorkbookDocument.readFormatVersion(JSONObject(metadata.readText(StandardCharsets.UTF_8)))
            if (oldVersion < WorkbookDocument.CURRENT_FORMAT_VERSION) preserveLegacy(metadata, oldVersion)
        }
        atomicWrite(metadata, bytes)
        return next
    }

    fun load(id: String): WorkbookDocument = loadCheckpoint(id).document

    fun loadCheckpoint(id: String): WorkbookCheckpoint = synchronized(writeLock) {
        val file = metadataFile(id)
        require(file.length() <= MAX_METADATA_BYTES) { "Workbook metadata exceeds the 32 MiB limit." }
        val checkpoint = WorkbookCheckpoint.fromJson(JSONObject(file.readText(StandardCharsets.UTF_8))).also {
            require(it.document.id == id) { "Workbook metadata identity does not match its directory." }
        }
        // Only an accepted canonical checkpoint makes unpublished metadata expendable.
        // Never promote a temporary file or discard candidates when validation fails.
        runCatching { removeAbandonedMetadataTemps(file.parentFile) }.onFailure {
            Log.w("WorkbookStore", "Could not inspect abandoned metadata temporary files.", it)
        }
        checkpoint
    }

    private fun metadataFile(id: String) = File(workbookDir(id), "workbook.json")

    private fun removeAbandonedMetadataTemps(directory: File) {
        val parent = directory.canonicalFile.toPath()
        val entries = directory.listFiles() ?: run {
            Log.w("WorkbookStore", "Could not inspect abandoned metadata temporary files.")
            return
        }
        entries.forEach { candidate ->
            val path = candidate.toPath()
            if (!METADATA_TEMP_NAME.matches(candidate.name) ||
                !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
                parent.resolve(candidate.name) in writeLock.activeMetadataTemps
            ) return@forEach
            // Recovery remains available if a stale file cannot be removed. Delete
            // only this direct path, never a link target or anything recursively.
            runCatching { Files.deleteIfExists(path) }.onFailure {
                Log.w("WorkbookStore", "Could not remove an abandoned metadata temporary file.", it)
            }
        }
    }

    private fun preserveLegacy(metadata: File, version: Int) {
        val dir = File(metadata.parentFile, "migration").apply { mkdirs() }
        val original = File(dir, "workbook-v$version-original.json")
        if (original.exists()) return
        val temporary = File.createTempFile("legacy-", ".tmp", dir)
        try {
            FileOutputStream(temporary).use { output ->
                metadata.inputStream().use { it.copyTo(output) }
                output.flush()
                output.fd.sync()
            }
            Files.move(temporary.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
    }

    fun list(): List<WorkbookSummary> {
        val dirs = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            runCatching {
                val doc = load(dir.name)
                WorkbookSummary(
                    id = doc.id,
                    revision = doc.revision,
                    name = doc.name,
                    updatedAtEpochMs = doc.updatedAtEpochMs,
                    sourceDisplayName = doc.snapshot?.displayName,
                    rowCount = doc.snapshot?.rowCount,
                    hasSnapshot = doc.snapshot != null,
                    favorite = doc.favorite,
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAtEpochMs }
    }

    fun workbookDir(id: String): File {
        require(isSafeId(id)) { "invalid workbook id" }
        return File(root, id)
    }

    fun resolveInsideWorkbook(id: String, relativePath: String): File {
        val rootDir = workbookDir(id).canonicalFile
        val target = File(rootDir, relativePath).canonicalFile
        require(target.path.startsWith(rootDir.path + File.separator)) {
            "path escapes workbook package"
        }
        return target
    }

    fun writeSnapshotReceipt(document: WorkbookDocument, snapshot: io.github.baitian6641.cloudydata.model.DataSnapshot): WorkbookDocument {
        return save(document.copy(snapshot = snapshot))
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temp = File.createTempFile("workbook-", ".tmp", target.parentFile)
        // Shared among handles for this root. Monitors are reentrant: an observer
        // can load through another Store on this same thread during TEMP_SYNCED.
        val tempKey = temp.parentFile.canonicalFile.toPath().resolve(temp.name)
        writeLock.activeMetadataTemps.add(tempKey)
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(bytes)
                stream.flush()
                stream.fd.sync()
            }
            commitObserver(WorkbookCommitPhase.TEMP_SYNCED)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            commitObserver(WorkbookCommitPhase.REPLACED)
            runCatching {
                java.nio.channels.FileChannel.open(target.parentFile.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
            }
        } finally {
            try {
                if (temp.exists()) temp.delete()
            } finally {
                writeLock.activeMetadataTemps.remove(tempKey)
            }
        }
    }

    private fun isSafeId(id: String): Boolean = id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' }

    private class WriteState {
        val activeMetadataTemps = mutableSetOf<Path>()
    }

    private companion object {
        // Same-process handles share the CAS lock. Cross-process writers remain unsupported.
        const val MAX_METADATA_BYTES = 32 * 1024 * 1024
        val METADATA_TEMP_NAME = Regex("workbook-[0-9]+\\.tmp")
        val writeLocks = mutableMapOf<String, WriteState>()
    }
}
