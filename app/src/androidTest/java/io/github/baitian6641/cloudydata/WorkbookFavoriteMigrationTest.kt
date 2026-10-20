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

/** S03.4 / F01,F64 / Q33,Q34: real Boolean defaults, projections and durable intent. */
@RunWith(AndroidJUnit4::class)
class WorkbookFavoriteMigrationTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "workbook-favorite-migration-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun absentAndExplicitLegacyBooleansKeepTheirProjectionAndVerbatimOriginal() = runBlocking {
        for (version in listOf(1, 2)) {
            for (value in listOf<Boolean?>(null, false, true)) {
                // null here requests an absent key, never an explicit JSON null.
                val fixture = fixture(version, value)
                val migration = migrateByRename(fixture)
                val beforeDuplicate = metadata(fixture.id).readBytes()
                val fresh = WorkbookStore(root)
                val duplicate = fresh.coordinators.open(fixture.id).execute(migration.request)
                assertTrue(duplicate.duplicate)
                assertEquals(migration.document.revision, duplicate.acceptedRevision)
                assertEquals(migration.document, duplicate.document)
                assertArrayEquals(beforeDuplicate, metadata(fixture.id).readBytes())
                assertProjection(fixture.id, value ?: false)
                assertBackup(fixture)
                assertNativeValues(fixture, duplicate.document)
            }
        }
    }

    @Test
    fun explicitOppositeDesiredValueNoOpAndHistoryNeverReplayAToggle() = runBlocking {
        for ((version, originalValue) in listOf(1 to false, 2 to true)) {
            val fixture = fixture(version, originalValue)
            val migration = migrateByRename(fixture)
            val desired = !originalValue
            val fresh = WorkbookStore(root)
            val coordinator = fresh.coordinators.open(fixture.id)
            val request = favorite(migration.document, desired)
            val accepted = coordinator.execute(request)
            assertFalse(accepted.duplicate)
            assertEquals(migration.document.revision + 1, accepted.acceptedRevision)
            assertEquals(desired, accepted.document.favorite)
            assertEquals(migration.document.worksheets, accepted.document.worksheets)
            assertProjection(fixture.id, desired)
            val acceptedCheckpoint = fresh.loadCheckpoint(fixture.id)
            assertEquals(listOf(fixture.document, migration.document), acceptedCheckpoint.undo)
            val noOp = favorite(accepted.document, desired)
            val noOpOutcome = coordinator.execute(noOp)
            assertFalse(noOpOutcome.duplicate)
            assertEquals(accepted.document.revision + 1, noOpOutcome.acceptedRevision)
            val afterNoOp = fresh.loadCheckpoint(fixture.id)
            assertEquals(acceptedCheckpoint.undo, afterNoOp.undo)
            assertEquals(acceptedCheckpoint.redo, afterNoOp.redo)
            assertEquals(listOf(migration.request, request, noOp), afterNoOp.receipts.map { it.command })
            assertEquals(listOf(migration.document.revision, accepted.acceptedRevision, noOpOutcome.acceptedRevision),
                afterNoOp.receipts.map { it.acceptedRevision })
            val noOpBytes = metadata(fixture.id).readBytes()
            val duplicate = coordinator.execute(request)
            assertTrue(duplicate.duplicate)
            assertEquals(accepted.acceptedRevision, duplicate.acceptedRevision)
            assertEquals(noOpOutcome.document, duplicate.document)
            assertArrayEquals(noOpBytes, metadata(fixture.id).readBytes())

            val undone = coordinator.undo(noOpOutcome.document.revision)
            assertEquals(originalValue, undone.favorite)
            assertEquals(noOpOutcome.document.revision + 1, undone.revision)
            val reopenedStore = WorkbookStore(root)
            val reopened = reopenedStore.coordinators.open(fixture.id)
            assertEquals(undone, reopened.state.value.document)
            assertTrue(reopened.state.value.canRedo)
            assertProjection(fixture.id, originalValue)
            val undoBytes = metadata(fixture.id).readBytes()
            val duplicateAfterUndo = reopened.execute(request)
            assertTrue(duplicateAfterUndo.duplicate)
            assertEquals(accepted.acceptedRevision, duplicateAfterUndo.acceptedRevision)
            assertEquals(undone, duplicateAfterUndo.document)
            assertArrayEquals("Accepted desired value cannot be replayed after undo", undoBytes, metadata(fixture.id).readBytes())
            val redone = reopened.redo(undone.revision)
            assertEquals(desired, redone.favorite)
            assertEquals(undone.revision + 1, redone.revision)
            val finalCheckpoint = WorkbookStore(root).loadCheckpoint(fixture.id)
            assertEquals(redone, finalCheckpoint.document)
            assertTrue(finalCheckpoint.redo.isEmpty())
            assertEquals(afterNoOp.receipts, finalCheckpoint.receipts)
            assertProjection(fixture.id, desired)
            assertBackup(fixture)
            assertNativeValues(fixture, redone)
        }
    }

    @Test
    fun currentExactBooleansAndExistingHistoryReopenWithoutChangingTheirAuthority() = runBlocking {
        for (value in listOf(false, true)) {
            val fixture = fixture(3, value)
            val original = fixture.currentCheckpoint!!
            val fresh = WorkbookStore(root)
            val loaded = fresh.loadCheckpoint(fixture.id)
            assertEquals(original, loaded)
            assertEquals(value, loaded.document.favorite)
            assertEquals(1, loaded.undo.size)
            assertEquals(1, loaded.receipts.size)
            assertProjection(fixture.id, value)
            assertArrayEquals(fixture.rawBytes, metadata(fixture.id).readBytes())
            assertFalse(File(store.workbookDir(fixture.id), "migration").exists())
            val coordinator = fresh.coordinators.open(fixture.id)
            val originalReceipt = loaded.receipts.single()
            val oldDuplicate = coordinator.execute(originalReceipt.command)
            assertTrue(oldDuplicate.duplicate)
            assertEquals(originalReceipt.acceptedRevision, oldDuplicate.acceptedRevision)
            assertEquals(loaded.document, oldDuplicate.document)
            assertArrayEquals(fixture.rawBytes, metadata(fixture.id).readBytes())
            val request = favorite(loaded.document, !value)
            val saved = coordinator.execute(request)
            assertEquals(!value, saved.document.favorite)
            assertProjection(fixture.id, !value)
            val undone = coordinator.undo(saved.document.revision)
            val afterUndo = WorkbookStore(root).coordinators.open(fixture.id)
            assertEquals(undone, afterUndo.state.value.document)
            assertEquals(value, undone.favorite)
            assertProjection(fixture.id, value)
            val bytes = metadata(fixture.id).readBytes()
            assertTrue(afterUndo.execute(request).duplicate)
            assertArrayEquals(bytes, metadata(fixture.id).readBytes())
            val redone = afterUndo.redo(undone.revision)
            assertEquals(!value, redone.favorite)
            val final = WorkbookStore(root).loadCheckpoint(fixture.id)
            assertEquals(redone, final.document)
            assertEquals(listOf(originalReceipt.command, request), final.receipts.map { it.command })
            assertProjection(fixture.id, !value)
            assertBackup(fixture)
            assertNativeValues(fixture, redone)
        }
    }

    private suspend fun migrateByRename(fixture: Fixture): Migration {
        val fresh = WorkbookStore(root)
        val loaded = fresh.loadCheckpoint(fixture.id)
        assertEquals(fixture.document, loaded.document)
        assertTrue(loaded.undo.isEmpty())
        assertTrue(loaded.redo.isEmpty())
        assertTrue(loaded.receipts.isEmpty())
        assertProjection(fixture.id, fixture.document.favorite)
        assertArrayEquals("Loading and projecting the legacy default is read-only", fixture.rawBytes, metadata(fixture.id).readBytes())
        assertFalse(File(store.workbookDir(fixture.id), "migration").exists())
        assertNativeValues(fixture, loaded.document)
        val request = WorkbookCommand.Rename(UUID.randomUUID().toString(), fixture.id,
            "favorite-migration-test", loaded.document.revision, "Accepted Boolean migration")
        val outcome = fresh.coordinators.open(fixture.id).execute(request)
        assertFalse(outcome.duplicate)
        assertEquals(loaded.document.revision + 1, outcome.acceptedRevision)
        assertEquals(loaded.document.favorite, outcome.document.favorite)
        val reopened = WorkbookStore(root).loadCheckpoint(fixture.id)
        assertEquals(outcome.document, reopened.document)
        assertEquals(listOf(loaded.document), reopened.undo)
        assertTrue(reopened.redo.isEmpty())
        assertEquals(listOf(request), reopened.receipts.map { it.command })
        assertEquals(listOf(outcome.acceptedRevision), reopened.receipts.map { it.acceptedRevision })
        assertProjection(fixture.id, loaded.document.favorite)
        assertBackup(fixture)
        assertNativeValues(fixture, reopened.document)
        return Migration(outcome.document, request)
    }

    private suspend fun fixture(version: Int, value: Boolean?): Fixture {
        val workbook = store.create("Boolean migration ${UUID.randomUUID()}")
        val input = File(root, "favorite-input-${workbook.id}.csv").apply { writeText(SOURCE_CSV) }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        val mapped = store.save(imported.copy(favorite = value ?: false, worksheets = imported.worksheets.map {
            it.copy(rowFieldId = category.id, measureFieldId = sales.id, aggregation = Aggregation.SUM,
                markType = MarkType.TABLE, sortDescending = true,
                filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East"))))
        }))
        val document = if (version == 3) store.coordinators.open(mapped.id).execute(
            WorkbookCommand.Rename(UUID.randomUUID().toString(), mapped.id, "current-Boolean-control",
                mapped.revision, "Current exact Boolean workbook")).document else mapped
        val currentCheckpoint = if (version == 3) store.loadCheckpoint(document.id) else null
        val raw = currentCheckpoint?.toJson() ?: document.toJson().put("formatVersion", version)
            .put("unknownOriginalEvidence", "Retain exact Boolean/default packet bytes")
        if (value == null) raw.remove("favorite")
        val bytes = ("\n" + raw.toString(2) + "\n").toByteArray(Charsets.UTF_8)
        metadata(document.id).writeBytes(bytes)
        return Fixture(document.id, version, document, currentCheckpoint, bytes,
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes(),
            store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
    }

    private fun assertNativeValues(fixture: Fixture, document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        val sheet = document.worksheets.single()
        val path = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val query = WorksheetQuery(engine)
            assertEquals(listOf(listOf("West", "20"), listOf("East", "15")),
                query.query(snapshot, sheet.copy(filter = null), path).result.rows.map { row -> row.map { it?.toString() } })
            val filtered = query.query(snapshot, sheet, path)
            assertEquals(listOf(listOf("East", "15")), filtered.result.rows.map { row -> row.map { it?.toString() } })
            val rows = UnderlyingRowsQuery(engine).query(snapshot, sheet, filtered, setOf(0), path)!!
            assertEquals(listOf(listOf("East", "10"), listOf("East", "5")),
                rows.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it[1] })
            assertTrue(rows.worksheetFilterApplied)
            assertFalse(rows.truncated)
        }
        assertArrayEquals(fixture.original, store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertArrayEquals(fixture.parquet, store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
        assertTrue(File(store.workbookDir(document.id), "staging").listFiles().orEmpty().isEmpty())
    }

    private fun assertProjection(id: String, favorite: Boolean) {
        assertEquals("The actual library summary must reflect the exact Boolean/default", favorite,
            WorkbookStore(root).list().single { it.id == id }.favorite)
    }

    private fun assertBackup(fixture: Fixture) {
        val directory = File(store.workbookDir(fixture.id), "migration")
        if (fixture.version == 3) assertFalse(directory.exists()) else {
            assertArrayEquals(fixture.rawBytes, File(directory, "workbook-v${fixture.version}-original.json").readBytes())
        }
    }

    private fun favorite(document: WorkbookDocument, value: Boolean) = WorkbookCommand.SetFavorite(
        UUID.randomUUID().toString(), document.id, "favorite-migration-test", document.revision, value)
    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")
    private data class Migration(val document: WorkbookDocument, val request: WorkbookCommand.Rename)
    private data class Fixture(
        val id: String,
        val version: Int,
        val document: WorkbookDocument,
        val currentCheckpoint: WorkbookCheckpoint?,
        val rawBytes: ByteArray,
        val original: ByteArray,
        val parquet: ByteArray,
    )
    companion object {
        private const val SOURCE_CSV = "Category,Sales\nEast,10\nWest,20\nEast,5\n"
    }
}
