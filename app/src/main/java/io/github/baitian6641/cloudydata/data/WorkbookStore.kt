package io.github.baitian6641.cloudydata.data

import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorkbookSummary
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class WorkbookStore(rootDir: File) {
    val root: File = File(rootDir, "workbooks").apply { mkdirs() }
    val coordinators: WorkbookRegistry by lazy { WorkbookRegistry(this) }
    private val writeLock = synchronized(writeLocks) { writeLocks.getOrPut(root.canonicalPath) { Any() } }

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

    fun save(document: WorkbookDocument): WorkbookDocument = synchronized(writeLock) {
        require(isSafeId(document.id)) { "invalid workbook id" }
        val dir = workbookDir(document.id)
        val metadata = File(dir, "workbook.json")
        if (metadata.exists()) {
            val actual = load(document.id).revision
            if (actual != document.revision) throw WorkbookConflictException(document.revision, actual)
        }
        if (!dir.exists() && !dir.mkdirs()) error("Could not create workbook directory")
        val now = System.currentTimeMillis()
        val next = document.copy(
            revision = document.revision + 1,
            updatedAtEpochMs = now,
        )
        atomicWrite(File(dir, "workbook.json"), next.toJson().toString())
        next
    }

    fun load(id: String): WorkbookDocument {
        require(isSafeId(id)) { "invalid workbook id" }
        val file = File(workbookDir(id), "workbook.json")
        return WorkbookDocument.fromJson(org.json.JSONObject(file.readText(StandardCharsets.UTF_8)))
    }

    fun list(): List<WorkbookSummary> {
        val dirs = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            runCatching {
                val doc = load(dir.name)
                WorkbookSummary(
                    id = doc.id,
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

    private fun atomicWrite(target: File, text: String) {
        val temp = File.createTempFile("workbook-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(text.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            runCatching {
                java.nio.channels.FileChannel.open(target.parentFile.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun isSafeId(id: String): Boolean = id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' }

    private companion object {
        // Same-process handles share the CAS lock. Cross-process writers remain unsupported.
        val writeLocks = mutableMapOf<String, Any>()
    }
}
