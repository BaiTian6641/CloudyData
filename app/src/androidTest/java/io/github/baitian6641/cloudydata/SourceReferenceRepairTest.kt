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
import io.github.baitian6641.cloudydata.engine.WorksheetReferenceChannel
import io.github.baitian6641.cloudydata.engine.WorksheetReferenceException
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.4 / F01,F05,F14,F64 / real replacement, reference admission and explicit repair. */
@RunWith(AndroidJUnit4::class)
class SourceReferenceRepairTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var importer: CsvImportService

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "source-reference-repair-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        importer = CsvImportService(context, store)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun sameHeaderReplacementKeepsRepairableIdsAndRefusesRowsThenMeasureBeforeSql() = runBlocking {
        val fixture = replaceMappedSource()
        val replacement = fixture.replacement
        val snapshot = replacement.snapshot!!
        val sheet = replacement.worksheets.single()
        assertEquals(fixture.mapped.worksheets, replacement.worksheets)
        assertEquals(listOf("Category", "Sales"), snapshot.columns.map { it.name })
        assertTrue(fixture.mapped.snapshot!!.columns.map { it.id }.toSet()
            .intersect(snapshot.columns.map { it.id }.toSet()).isEmpty())
        assertNotEquals(fixture.mapped.snapshot!!.id, snapshot.id)

        val reopenedStore = WorkbookStore(root)
        val reopened = reopenedStore.load(replacement.id)
        assertEquals(replacement, reopened)
        assertEquals(fixture.mapped.worksheets.single().rowFieldId, reopened.worksheets.single().rowFieldId)
        assertEquals(fixture.mapped.worksheets.single().measureFieldId, reopened.worksheets.single().measureFieldId)
        val beforeRowsRefusal = preserve(replacement.id)
        assertTrue(beforeRowsRefusal.checkpoint.undo.isNotEmpty())
        assertTrue(beforeRowsRefusal.checkpoint.receipts.isNotEmpty())
        val unavailablePath = File(root, "this-parquet-file-does-not-exist.parquet").absolutePath
        assertFalse(File(unavailablePath).exists())

        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            assertReferenceFailure(WorksheetReferenceChannel.ROWS, sheet.rowFieldId) {
                WorksheetQuery(engine).query(snapshot, sheet, unavailablePath)
            }
            assertReferenceFailure(WorksheetReferenceChannel.ROWS, sheet.rowFieldId) {
                UnderlyingRowsQuery(engine).query(snapshot, sheet, fixture.oldResult,
                    setOf(fixture.oldEastIndex), unavailablePath)
            }
            assertPreserved(replacement.id, beforeRowsRefusal)

            // Repair only Rows through the existing coordinator path. The old
            // measure remains a distinct unresolved reference, never COUNT(*).
            val coordinator = reopenedStore.coordinators.open(replacement.id)
            val category = snapshot.columns.single { it.name == "Category" }
            val partial = coordinator.edit(replacement.revision) { document ->
                document.copy(worksheets = document.worksheets.map { it.copy(rowFieldId = category.id) })
            }
            assertEquals(category.id, partial.worksheets.single().rowFieldId)
            assertEquals(sheet.measureFieldId, partial.worksheets.single().measureFieldId)
            val beforeMeasureRefusal = preserve(replacement.id)
            assertReferenceFailure(WorksheetReferenceChannel.MEASURE, sheet.measureFieldId) {
                WorksheetQuery(engine).query(snapshot, partial.worksheets.single(), unavailablePath)
            }
            assertPreserved(replacement.id, beforeMeasureRefusal)
            assertEquals(partial, WorkbookStore(root).load(replacement.id))
        }
        assertCurrentReplacementSource(replacement)
    }

    @Test
    fun explicitRebindRestoresExactNewValuesButOldResultCannotInspectReplacementRows() = runBlocking {
        val fixture = replaceMappedSource()
        val replacement = fixture.replacement
        val snapshot = replacement.snapshot!!
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        val coordinator = WorkbookStore(root).coordinators.open(replacement.id)
        val repaired = coordinator.edit(replacement.revision) { document ->
            document.copy(worksheets = document.worksheets.map {
                it.copy(rowFieldId = category.id, measureFieldId = sales.id)
            })
        }
        val saved = WorkbookStore(root).load(repaired.id)
        assertEquals(repaired, saved)
        assertEquals(snapshot, saved.snapshot)
        assertEquals(replacement.activeWorksheetId, saved.activeWorksheetId)
        assertEquals(listOf(category.id, sales.id), listOf(
            saved.worksheets.single().rowFieldId, saved.worksheets.single().measureFieldId,
        ))
        val path = store.resolveInsideWorkbook(saved.id, snapshot.snapshotFile).absolutePath
        val beforeStaleResult = preserve(saved.id)

        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val query = WorksheetQuery(engine)
            val sheet = saved.worksheets.single()
            // Current mappings are now valid; ownership of an accepted result
            // from the old snapshot must still fail before any file is opened.
            assertReferenceFailure(WorksheetReferenceChannel.RESULT, null) {
                UnderlyingRowsQuery(engine).query(
                    snapshot = snapshot,
                    sheet = sheet,
                    worksheetResult = fixture.oldResult,
                    selectedRows = setOf(fixture.oldEastIndex),
                    snapshotPath = File(root, "unavailable-stale-view-data.parquet").absolutePath,
                )
            }
            assertPreserved(saved.id, beforeStaleResult)

            val current = query.query(snapshot, sheet, path)
            assertEquals(listOf(listOf("West", "11"), listOf("East", "7")), stringRows(current))
            val eastIndex = current.result.rows.indexOfFirst { it[0] == "East" }
            assertTrue(eastIndex >= 0)
            val eastRows = UnderlyingRowsQuery(engine).query(
                snapshot = snapshot,
                sheet = sheet,
                worksheetResult = current,
                selectedRows = setOf(eastIndex),
                snapshotPath = path,
            )!!
            assertEquals(listOf("Category", "Sales"), eastRows.result.columns.map { it.name })
            assertEquals(listOf(listOf("East", "7")), eastRows.result.rows.map { row -> row.map { it?.toString() } })
            assertEquals(1, eastRows.selectedMarkCount)
            assertFalse(eastRows.truncated)
            assertPreserved(saved.id, beforeStaleResult)

            // Undo is metadata repair history against the current source. It
            // cannot resurrect or replay the overwritten previous dataset.
            val undone = coordinator.undo(repaired.revision)
            assertEquals(replacement.worksheets, undone.worksheets)
            assertEquals(snapshot, undone.snapshot)
            val undoCheckpoint = preserve(undone.id)
            assertReferenceFailure(WorksheetReferenceChannel.ROWS, fixture.mapped.worksheets.single().rowFieldId) {
                query.query(snapshot, undone.worksheets.single(), path)
            }
            assertPreserved(undone.id, undoCheckpoint)
            val afterUndo = WorkbookStore(root).coordinators.open(undone.id)
            assertEquals(undone, afterUndo.state.value.document)
            val redone = afterUndo.redo(undone.revision)
            assertEquals(repaired.worksheets, redone.worksheets)
            assertEquals(snapshot, redone.snapshot)
            val reopened = WorkbookStore(root).load(redone.id)
            assertEquals(redone, reopened)
            assertEquals(listOf(listOf("West", "11"), listOf("East", "7")),
                stringRows(query.query(reopened.snapshot!!, reopened.worksheets.single(), path)))
        }
        assertCurrentReplacementSource(WorkbookStore(root).load(saved.id))
    }

    private suspend fun replaceMappedSource(): ReplacedFixture {
        val workbook = store.create("Reference repair workbook")
        val coordinator = store.coordinators.open(workbook.id)
        val originalInput = File(root, "original-input.csv").apply { writeText(ORIGINAL_CSV) }
        val firstPending = importer.stageAndInspect(workbook, Uri.fromFile(originalInput))
        val imported = coordinator.importSnapshot(workbook.revision) { importer.apply(it, firstPending) }
        val originalSnapshot = imported.snapshot!!
        assertEquals(3L, originalSnapshot.rowCount)
        assertArrayEquals(ORIGINAL_CSV.toByteArray(Charsets.UTF_8),
            store.resolveInsideWorkbook(imported.id, originalSnapshot.originalFile!!).readBytes())
        val category = originalSnapshot.columns.single { it.name == "Category" }
        val sales = originalSnapshot.columns.single { it.name == "Sales" }
        val mapped = coordinator.edit(imported.revision) { document ->
            document.copy(worksheets = document.worksheets.map {
                it.copy(rowFieldId = category.id, measureFieldId = sales.id,
                    aggregation = Aggregation.SUM, markType = MarkType.TABLE, sortDescending = true)
            })
        }
        val oldResult = DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            WorksheetQuery(engine).query(originalSnapshot, mapped.worksheets.single(),
                store.resolveInsideWorkbook(mapped.id, originalSnapshot.snapshotFile).absolutePath)
        }
        assertEquals(listOf(listOf("West", "20"), listOf("East", "15")), stringRows(oldResult))
        val oldEastIndex = oldResult.result.rows.indexOfFirst { it[0] == "East" }
        assertTrue(oldEastIndex >= 0)

        val replacementInput = File(root, "replacement-input.csv").apply { writeText(REPLACEMENT_CSV) }
        val secondPending = importer.stageAndInspect(mapped, Uri.fromFile(replacementInput))
        val replaced = coordinator.importSnapshot(mapped.revision) { importer.apply(it, secondPending) }
        assertEquals(mapped.worksheets, replaced.worksheets)
        assertEquals(2L, replaced.snapshot!!.rowCount)
        assertEquals("data/snapshot.parquet", replaced.snapshot!!.snapshotFile)
        assertEquals("source/original.csv", replaced.snapshot!!.originalFile)
        assertTrue(File(store.workbookDir(replaced.id), "staging").listFiles().orEmpty().isEmpty())
        // Give admission a nonempty actual history and typed receipt to preserve.
        val withHistory = coordinator.execute(WorkbookCommand.SetFavorite(
            UUID.randomUUID().toString(), replaced.id, "reference-repair-test", replaced.revision, true,
        )).document
        assertCurrentReplacementSource(withHistory)
        return ReplacedFixture(mapped, withHistory, oldResult, oldEastIndex)
    }

    private fun assertCurrentReplacementSource(document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        assertEquals(2L, snapshot.rowCount)
        assertArrayEquals(REPLACEMENT_CSV.toByteArray(Charsets.UTF_8),
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertTrue(store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).length() > 0)
    }

    private fun preserve(id: String): Preserved {
        val checkpoint = store.loadCheckpoint(id)
        val snapshot = checkpoint.document.snapshot!!
        return Preserved(checkpoint, File(store.workbookDir(id), "workbook.json").readBytes(),
            store.resolveInsideWorkbook(id, snapshot.originalFile!!).readBytes(),
            store.resolveInsideWorkbook(id, snapshot.snapshotFile).readBytes())
    }

    private fun assertPreserved(id: String, expected: Preserved) {
        val snapshot = expected.checkpoint.document.snapshot!!
        assertArrayEquals("Admission cannot rewrite metadata/history/receipts", expected.metadata,
            File(store.workbookDir(id), "workbook.json").readBytes())
        assertEquals(expected.checkpoint, WorkbookStore(root).loadCheckpoint(id))
        assertArrayEquals(expected.original, store.resolveInsideWorkbook(id, snapshot.originalFile!!).readBytes())
        assertArrayEquals(expected.parquet, store.resolveInsideWorkbook(id, snapshot.snapshotFile).readBytes())
    }

    private fun assertReferenceFailure(channel: WorksheetReferenceChannel, fieldId: String?, operation: () -> Any?) {
        val failure = runCatching(operation).exceptionOrNull()
        assertTrue("Expected typed $channel admission before SQL, received $failure", failure is WorksheetReferenceException)
        failure as WorksheetReferenceException
        assertEquals(channel, failure.channel)
        assertEquals(fieldId, failure.fieldId)
    }

    private fun stringRows(result: WorksheetResult): List<List<String?>> =
        result.result.rows.map { row -> row.map { it?.toString() } }

    private data class ReplacedFixture(
        val mapped: WorkbookDocument,
        val replacement: WorkbookDocument,
        val oldResult: WorksheetResult,
        val oldEastIndex: Int,
    )

    private data class Preserved(
        val checkpoint: WorkbookCheckpoint,
        val metadata: ByteArray,
        val original: ByteArray,
        val parquet: ByteArray,
    )

    companion object {
        private const val ORIGINAL_CSV = "Category,Sales\nEast,10\nWest,20\nEast,5\n"
        private const val REPLACEMENT_CSV = "Category,Sales\nEast,7\nWest,11\n"
    }
}
