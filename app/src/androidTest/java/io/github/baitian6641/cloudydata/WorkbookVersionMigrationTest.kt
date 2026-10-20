package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookCheckpoint
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.4 / F01,F64 / Q33,Q34: numeric version compatibility with real snapshots. */
@RunWith(AndroidJUnit4::class)
class WorkbookVersionMigrationTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "workbook-version-migration-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun integralLegacyLiteralTokensMigrateWithExactOriginalBytesAndVersionedBackupNames() = runBlocking {
        val versions = listOf(1 to "1", 1 to "1.0", 1 to "1e0",
            2 to "2", 2 to "2.0", 2 to "2E+0")
        for ((version, token) in versions) {
            val fixture = fixture(version, token)
            val metadata = metadata(fixture.id)
            val backup = File(store.workbookDir(fixture.id), "migration/workbook-v$version-original.json")
            val firstStore = WorkbookStore(root)
            val loaded = firstStore.loadCheckpoint(fixture.id)
            assertEquals(fixture.document, loaded.document)
            assertEquals(3, loaded.document.formatVersion)
            assertTrue(loaded.undo.isEmpty())
            assertTrue(loaded.redo.isEmpty())
            assertTrue(loaded.receipts.isEmpty())
            assertArrayEquals("Loading supported numeric $token must be read-only", fixture.rawBytes, metadata.readBytes())
            assertFalse(backup.exists())
            assertNativeValues(loaded.document)
            assertSourceUnchanged(fixture, loaded.document)

            val coordinator = firstStore.coordinators.open(fixture.id)
            assertArrayEquals(fixture.rawBytes, metadata.readBytes())
            assertFalse(backup.exists())
            val firstRequest = WorkbookCommand.Rename(UUID.randomUUID().toString(), fixture.id,
                "version-migration-test", loaded.document.revision, "Accepted version $version migration")
            val accepted = coordinator.execute(firstRequest)
            assertFalse(accepted.duplicate)
            assertEquals(loaded.document.revision + 1, accepted.acceptedRevision)
            val saved = firstStore.loadCheckpoint(fixture.id)
            assertEquals(listOf(loaded.document), saved.undo)
            assertTrue(saved.redo.isEmpty())
            assertEquals(listOf(firstRequest), saved.receipts.map { it.command })
            assertEquals(listOf(accepted.acceptedRevision), saved.receipts.map { it.acceptedRevision })
            assertEquals(3, JSONObject(metadata.readText()).getInt("formatVersion"))
            assertArrayEquals("The literal token and original whitespace must survive in backup", fixture.rawBytes, backup.readBytes())
            assertEquals(listOf("workbook-v$version-original.json"), backup.parentFile!!.listFiles()!!.map { it.name }.sorted())
            assertNativeValues(saved.document)
            assertSourceUnchanged(fixture, saved.document)

            val reopenedStore = WorkbookStore(root)
            assertEquals(saved, reopenedStore.loadCheckpoint(fixture.id))
            val reopened = reopenedStore.coordinators.open(fixture.id)
            val secondRequest = WorkbookCommand.Rename(UUID.randomUUID().toString(), fixture.id,
                "version-migration-test", saved.document.revision, "Later version $version save")
            val second = reopened.execute(secondRequest).document
            assertEquals(saved.document.revision + 1, second.revision)
            assertArrayEquals(fixture.rawBytes, backup.readBytes())
            val secondBytes = metadata.readBytes()
            val thirdStore = WorkbookStore(root)
            val afterSecond = thirdStore.coordinators.open(fixture.id)
            assertEquals(second, afterSecond.state.value.document)
            val duplicate = afterSecond.execute(firstRequest)
            assertTrue(duplicate.duplicate)
            assertEquals(accepted.acceptedRevision, duplicate.acceptedRevision)
            assertEquals(second, duplicate.document)
            assertArrayEquals("Duplicate acknowledgement cannot replay migration or save", secondBytes, metadata.readBytes())
            val finalCheckpoint = thirdStore.loadCheckpoint(fixture.id)
            assertEquals(listOf(loaded.document, saved.document), finalCheckpoint.undo)
            assertTrue(finalCheckpoint.redo.isEmpty())
            assertEquals(listOf(firstRequest, secondRequest), finalCheckpoint.receipts.map { it.command })
            assertEquals(listOf(accepted.acceptedRevision, second.revision), finalCheckpoint.receipts.map { it.acceptedRevision })
            assertNativeValues(second)
            assertSourceUnchanged(fixture, second)
            assertArrayEquals(fixture.rawBytes, backup.readBytes())
        }
    }

    @Test
    fun currentIntegralLiteralTokensReopenWithoutRewritingJournalOrCreatingMigration() = runBlocking {
        for (token in listOf("3", "3.0", "3e0")) {
            val fixture = fixture(3, token)
            val metadata = metadata(fixture.id)
            val original = fixture.currentCheckpoint!!
            val reopenedStore = WorkbookStore(root)
            val loaded = reopenedStore.loadCheckpoint(fixture.id)
            assertEquals(original, loaded)
            assertEquals(3, loaded.document.formatVersion)
            assertEquals(1, loaded.undo.size)
            assertTrue(loaded.redo.isEmpty())
            assertEquals(1, loaded.receipts.size)
            assertArrayEquals("Equivalent numeric $token must retain its actual original bytes", fixture.rawBytes, metadata.readBytes())
            assertFalse(File(store.workbookDir(fixture.id), "migration").exists())
            val coordinator = reopenedStore.coordinators.open(fixture.id)
            assertEquals(loaded.document, coordinator.state.value.document)
            val receipt = loaded.receipts.single()
            val duplicate = coordinator.execute(receipt.command)
            assertTrue(duplicate.duplicate)
            assertEquals(receipt.acceptedRevision, duplicate.acceptedRevision)
            assertEquals(loaded.document, duplicate.document)
            assertArrayEquals(fixture.rawBytes, metadata.readBytes())
            assertEquals(loaded, WorkbookStore(root).loadCheckpoint(fixture.id))
            assertNativeValues(loaded.document)
            assertSourceUnchanged(fixture, loaded.document)
        }
    }

    @Test
    fun integralIntLongDoubleControlsKeepLegacyAndCurrentJournalBoundariesDistinct() = runBlocking {
        val fixture = fixture(3, "3.0")
        val checkpoint = fixture.currentCheckpoint!!
        val metadataBefore = metadata(fixture.id).readBytes()
        for (version in 1..3) {
            for (numeric in listOf<Number>(version, version.toLong(), version.toDouble())) {
                val raw = if (version == 3) checkpoint.toJson() else checkpoint.document.toJson()
                raw.put("formatVersion", numeric)
                assertEquals("The in-memory control must retain the requested numeric kind",
                    numeric.javaClass, raw.get("formatVersion").javaClass)
                assertEquals(checkpoint.document, WorkbookDocument.fromJson(raw))
                val decoded = WorkbookCheckpoint.fromJson(raw)
                assertEquals(checkpoint.document, decoded.document)
                if (version == 3) assertEquals(checkpoint, decoded) else {
                    assertTrue(decoded.undo.isEmpty())
                    assertTrue(decoded.redo.isEmpty())
                    assertTrue(decoded.receipts.isEmpty())
                }
            }
        }
        for (numeric in listOf<Number>(3, 3L, 3.0)) {
            val bare = checkpoint.document.toJson().put("formatVersion", numeric)
            assertCheckpointRefused("Version 3 requires its journal", bare)
        }
        for (version in listOf(1, 2)) {
            for (numeric in listOf<Number>(version, version.toLong(), version.toDouble())) {
                val mixed = checkpoint.toJson().put("formatVersion", numeric)
                assertCheckpointRefused("A legacy version cannot carry the current journal", mixed)
            }
        }
        assertArrayEquals("In-memory decoder controls cannot write the stored workbook", metadataBefore, metadata(fixture.id).readBytes())
        assertEquals(checkpoint, WorkbookStore(root).loadCheckpoint(fixture.id))
        assertFalse(File(store.workbookDir(fixture.id), "migration").exists())
        assertNativeValues(checkpoint.document)
        assertSourceUnchanged(fixture, checkpoint.document)
    }

    private suspend fun fixture(version: Int, literalToken: String): Fixture {
        val workbook = store.create("Numeric version $version ${UUID.randomUUID()}")
        val input = File(root, "version-input-${workbook.id}.csv").apply { writeText(SOURCE_CSV) }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        assertEquals(3L, snapshot.rowCount)
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        val mapped = store.save(imported.copy(worksheets = imported.worksheets.map {
            it.copy(rowFieldId = category.id, measureFieldId = sales.id,
                aggregation = Aggregation.SUM, markType = MarkType.TABLE, sortDescending = true,
                filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East"))))
        }))
        val document = if (version == 3) {
            store.coordinators.open(mapped.id).execute(WorkbookCommand.Rename(UUID.randomUUID().toString(), mapped.id,
                "current-version-control", mapped.revision, "Current numeric version workbook")).document
        } else mapped
        val currentCheckpoint = if (version == 3) store.loadCheckpoint(document.id) else null
        val raw = currentCheckpoint?.toJson() ?: document.toJson()
        if (version < 3) raw.put("unknownOriginalEvidence", "Preserve the exact numeric token and this older packet")
        val marker = "__literal_workbook_version_token__"
        raw.put("formatVersion", marker)
        val formatted = raw.toString(2)
        val quotedMarker = JSONObject.quote(marker)
        assertEquals(1, Regex(Regex.escape(quotedMarker)).findAll(formatted).count())
        // JSONObject serialization deliberately canonicalizes integral doubles.
        // Emit only this controlled, known numeric token into the actual file.
        val rawText = "\n" + formatted.replace(quotedMarker, literalToken) + "\n"
        assertTrue(rawText.contains("\"formatVersion\": $literalToken"))
        val bytes = rawText.toByteArray(Charsets.UTF_8)
        metadata(document.id).writeBytes(bytes)
        return Fixture(document.id, document, currentCheckpoint, bytes,
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes(),
            store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
    }

    private fun assertNativeValues(document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        val sheet = document.worksheets.single()
        assertEquals(FilterMode.KEEP, sheet.filter!!.mode)
        assertEquals(listOf(FilterMark("East")), sheet.filter!!.marks)
        val path = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val query = WorksheetQuery(engine)
            val all = query.query(snapshot, sheet.copy(filter = null), path)
            assertEquals(listOf(listOf("West", "20"), listOf("East", "15")),
                all.result.rows.map { row -> row.map { it?.toString() } })
            val filtered = query.query(snapshot, sheet, path)
            assertEquals(listOf(listOf("East", "15")), filtered.result.rows.map { row -> row.map { it?.toString() } })
            val underlying = UnderlyingRowsQuery(engine).query(snapshot, sheet, filtered, setOf(0), path)!!
            assertEquals(listOf("Category", "Sales"), underlying.result.columns.map { it.name })
            assertEquals(listOf(listOf("East", "10"), listOf("East", "5")),
                underlying.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it[1] })
            assertTrue(underlying.worksheetFilterApplied)
            assertFalse(underlying.truncated)
        }
    }

    private fun assertSourceUnchanged(fixture: Fixture, document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        assertArrayEquals(fixture.originalBytes,
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertArrayEquals(fixture.parquetBytes,
            store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
        assertTrue(File(store.workbookDir(document.id), "staging").listFiles().orEmpty().isEmpty())
    }

    private fun assertCheckpointRefused(message: String, raw: JSONObject) {
        val failure = runCatching { WorkbookCheckpoint.fromJson(raw) }.exceptionOrNull()
        assertTrue("$message: $failure", failure is IllegalArgumentException)
    }

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")

    private data class Fixture(
        val id: String,
        val document: WorkbookDocument,
        val currentCheckpoint: WorkbookCheckpoint?,
        val rawBytes: ByteArray,
        val originalBytes: ByteArray,
        val parquetBytes: ByteArray,
    )

    companion object {
        private const val SOURCE_CSV = "Category,Sales\nEast,10\nWest,20\nEast,5\n"
    }
}
