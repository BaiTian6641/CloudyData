package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.Sql
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.4 / F01,F64 / Q33,Q34: identity uniqueness is per document, across valid migrations. */
@RunWith(AndroidJUnit4::class)
class WorksheetIdentityMigrationTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "worksheet-identity-migration-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun uniqueCurrentSheetsKeepTargetedChartsAndRepeatedIdentityAcrossHistory() = runBlocking {
        val fixture = fixture()
        val before = metadata(fixture.document.id).readBytes()
        val reopened = WorkbookStore(root).loadCheckpoint(fixture.document.id)
        assertEquals(fixture.document, reopened.document)
        assertTrue(reopened.undo.isEmpty())
        assertTrue(reopened.redo.isEmpty())
        assertTrue(reopened.receipts.isEmpty())
        assertArrayEquals(before, metadata(fixture.document.id).readBytes())
        assertFalse(File(store.workbookDir(fixture.document.id), "migration").exists())
        exerciseTargetedChartHistory(fixture, fixture.document)
    }

    @Test
    fun uniqueLegacySheetsMigrateVerbatimAndKeepIndependentDefinitionsThroughHistory() = runBlocking {
        for (version in listOf(1, 2)) {
            val fixture = fixture()
            val raw = legacyJson(fixture.document, version)
            val exact = ("\n" + raw.toString(2) + "\n").toByteArray(Charsets.UTF_8)
            metadata(fixture.document.id).writeBytes(exact)
            val backup = backup(fixture.document.id, version)
            val loaded = WorkbookStore(root).loadCheckpoint(fixture.document.id)
            assertEquals(fixture.document, loaded.document)
            assertTrue(loaded.undo.isEmpty())
            assertTrue(loaded.redo.isEmpty())
            assertTrue(loaded.receipts.isEmpty())
            assertArrayEquals(exact, metadata(fixture.document.id).readBytes())
            assertFalse(backup.exists())
            assertNativeValues(fixture, loaded.document)
            exerciseTargetedChartHistory(fixture, loaded.document, backup, exact)
            assertArrayEquals(exact, backup.readBytes())
        }
    }

    @Test
    fun existingMissingOrNullActiveIdKeepsFirstSheetFallbackWithoutIdentityRepair() = runBlocking {
        for (version in listOf(1, 2)) {
            for (missing in listOf(false, true)) {
                val fixture = fixture()
                val expected = fixture.document.copy(activeWorksheetId = null)
                val raw = legacyJson(expected, version)
                if (missing) raw.remove("activeWorksheetId") else raw.put("activeWorksheetId", JSONObject.NULL)
                val exact = (raw.toString(2) + "\n").toByteArray(Charsets.UTF_8)
                metadata(expected.id).writeBytes(exact)
                val fresh = WorkbookStore(root)
                val loaded = fresh.load(expected.id)
                assertEquals(expected, loaded)
                assertNull(loaded.activeWorksheetId)
                assertEquals(expected.worksheets.first(), loaded.activeWorksheet)
                assertArrayEquals(exact, metadata(expected.id).readBytes())
                assertFalse(backup(expected.id, version).exists())
                val coordinator = fresh.coordinators.open(expected.id)
                val saved = coordinator.execute(WorkbookCommand.Rename(UUID.randomUUID().toString(), expected.id,
                    "nullable-active-control", expected.revision, "Saved nullable active worksheet")).document
                val reopened = WorkbookStore(root).load(saved.id)
                assertEquals(saved, reopened)
                assertEquals(expected.worksheets, reopened.worksheets)
                assertNull(reopened.activeWorksheetId)
                assertEquals(expected.worksheets.first(), reopened.activeWorksheet)
                assertArrayEquals(exact, backup(expected.id, version).readBytes())
                assertNativeValues(fixture, reopened)
            }
        }
        val current = fixture()
        val saved = store.save(current.document.copy(activeWorksheetId = null))
        val exact = metadata(saved.id).readBytes()
        val reopened = WorkbookStore(root).load(saved.id)
        assertEquals(saved, reopened)
        assertEquals(current.document.worksheets, reopened.worksheets)
        assertNull(reopened.activeWorksheetId)
        assertEquals(saved.worksheets.first(), reopened.activeWorksheet)
        assertArrayEquals(exact, metadata(saved.id).readBytes())
        assertNativeValues(current, reopened)
    }

    @Test
    fun existingEmptyWorksheetListsRemainValidWithoutInventingAnActiveSheet() = runBlocking {
        for (version in listOf(1, 2, 3)) {
            val fixture = fixture()
            val empty = store.save(fixture.document.copy(worksheets = emptyList(), activeWorksheetId = null))
            val exact = if (version < 3) {
                (legacyJson(empty, version).toString(2) + "\n").toByteArray(Charsets.UTF_8)
            } else metadata(empty.id).readBytes()
            metadata(empty.id).writeBytes(exact)
            val fresh = WorkbookStore(root)
            val loaded = fresh.loadCheckpoint(empty.id)
            assertEquals(empty, loaded.document)
            assertTrue(loaded.document.worksheets.isEmpty())
            assertNull(loaded.document.activeWorksheetId)
            assertNull(loaded.document.activeWorksheet)
            assertArrayEquals(exact, metadata(empty.id).readBytes())
            val coordinator = fresh.coordinators.open(empty.id)
            val saved = coordinator.execute(WorkbookCommand.Rename(UUID.randomUUID().toString(), empty.id,
                "empty-sheet-control", empty.revision, "Accepted empty-sheet workbook")).document
            val reopened = WorkbookStore(root).load(saved.id)
            assertEquals(saved, reopened)
            assertTrue(reopened.worksheets.isEmpty())
            assertNull(reopened.activeWorksheet)
            if (version < 3) assertArrayEquals(exact, backup(empty.id, version).readBytes())
            else assertFalse(File(store.workbookDir(empty.id), "migration").exists())
            assertSourceUnchanged(fixture, reopened)
            // An empty sheet list has no worksheet query to fabricate. Its
            // accepted source still has the independently known grouped values.
            val path = store.resolveInsideWorkbook(empty.id, reopened.snapshot!!.snapshotFile).absolutePath
            DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
                val result = engine.execute("SELECT Category, SUM(Sales) FROM read_parquet(${Sql.string(path)}) " +
                    "GROUP BY Category ORDER BY 2 DESC", maxRows = 10)
                assertEquals(listOf(listOf("West", "20"), listOf("East", "15")),
                    result.rows.map { row -> row.map { it?.toString() } })
            }
        }
    }

    @Test
    fun caseAndPaddingDistinctNonblankIdsKeepExactIdentityAndTargeting() = runBlocking {
        for (version in listOf(1, 2, 3)) {
            val fixture = fixture()
            val first = fixture.document.worksheets[0].copy(id = "Sheet-A")
            val second = fixture.document.worksheets[1].copy(id = "sheet-a")
            val padded = second.copy(id = " Sheet-A ", name = "Padded identity",
                chart = second.chart.copy(title = "Padded values"))
            val seeded = store.save(fixture.document.copy(worksheets = listOf(first, second, padded),
                activeWorksheetId = first.id))
            if (version < 3) metadata(seeded.id).writeText("\n" + legacyJson(seeded, version).toString(2) + "\n")
            val originalBytes = metadata(seeded.id).readBytes()
            val fresh = WorkbookStore(root)
            val loaded = fresh.loadCheckpoint(seeded.id)
            assertEquals(seeded, loaded.document)
            assertEquals(listOf("Sheet-A", "sheet-a", " Sheet-A "), loaded.document.worksheets.map { it.id })
            assertArrayEquals(originalBytes, metadata(seeded.id).readBytes())
            val options = padded.chart.copy(title = "Only padded ID changed")
            val request = WorkbookCommand.SetChart(UUID.randomUUID().toString(), seeded.id,
                "exact-worksheet-id-control", seeded.revision, " Sheet-A ", options)
            val accepted = fresh.coordinators.open(seeded.id).execute(request)
            assertFalse(accepted.duplicate)
            assertEquals(seeded.revision + 1, accepted.document.revision)
            assertEquals(listOf(first, second, padded.copy(chart = options)), accepted.document.worksheets)
            assertEquals("Sheet-A", accepted.document.activeWorksheetId)
            val afterSave = WorkbookStore(root)
            val checkpoint = afterSave.loadCheckpoint(seeded.id)
            assertEquals(listOf(request), checkpoint.receipts.map { it.command })
            assertEquals(" Sheet-A ", (checkpoint.receipts.single().command as WorkbookCommand.SetChart).worksheetId)
            assertEquals(listOf(seeded), checkpoint.undo)
            val acceptedBytes = metadata(seeded.id).readBytes()
            val duplicate = afterSave.coordinators.open(seeded.id).execute(request)
            assertTrue(duplicate.duplicate)
            assertEquals(accepted.acceptedRevision, duplicate.acceptedRevision)
            assertEquals(accepted.document, duplicate.document)
            assertArrayEquals(acceptedBytes, metadata(seeded.id).readBytes())
            if (version < 3) assertArrayEquals(originalBytes, backup(seeded.id, version).readBytes())
            assertNativeValues(fixture, duplicate.document)
        }
    }

    private suspend fun exerciseTargetedChartHistory(
        fixture: Fixture,
        initial: WorkbookDocument,
        backup: File? = null,
        legacyBytes: ByteArray? = null,
    ) {
        val originalSheets = initial.worksheets
        assertEquals(2, originalSheets.map { it.id }.distinct().size)
        assertEquals(listOf("East subset", "All categories"), originalSheets.map { it.name })
        val options = originalSheets[1].chart.copy(title = "Changed second chart only", caption = "Accepted chart edit")
        val expectedSheets = listOf(originalSheets[0], originalSheets[1].copy(chart = options))
        val firstStore = WorkbookStore(root)
        val coordinator = firstStore.coordinators.open(initial.id)
        val request = WorkbookCommand.SetChart(UUID.randomUUID().toString(), initial.id, "unique-sheet-chart",
            initial.revision, originalSheets[1].id, options)
        val accepted = coordinator.execute(request)
        assertFalse(accepted.duplicate)
        assertEquals(initial.revision + 1, accepted.acceptedRevision)
        assertEquals(expectedSheets, accepted.document.worksheets)
        assertEquals(originalSheets[0], accepted.document.worksheets[0])
        assertEquals(initial.activeWorksheetId, accepted.document.activeWorksheetId)
        val saved = firstStore.loadCheckpoint(initial.id)
        assertEquals(listOf(initial), saved.undo)
        assertTrue(saved.redo.isEmpty())
        assertEquals(listOf(request), saved.receipts.map { it.command })
        assertEquals(listOf(accepted.acceptedRevision), saved.receipts.map { it.acceptedRevision })
        if (backup != null) assertArrayEquals(legacyBytes!!, backup.readBytes())
        assertNativeValues(fixture, saved.document)

        val afterSave = WorkbookStore(root).coordinators.open(initial.id)
        assertEquals(saved.document, afterSave.state.value.document)
        val undone = afterSave.undo(saved.document.revision)
        assertEquals(originalSheets, undone.worksheets)
        assertEquals(saved.document.revision + 1, undone.revision)
        assertEquals(initial.activeWorksheetId, undone.activeWorksheetId)
        val afterUndoStore = WorkbookStore(root)
        val afterUndo = afterUndoStore.coordinators.open(initial.id)
        assertEquals(undone, afterUndo.state.value.document)
        assertTrue(afterUndo.state.value.canRedo)
        assertNativeValues(fixture, undone)
        val redone = afterUndo.redo(undone.revision)
        assertEquals(expectedSheets, redone.worksheets)
        assertEquals(undone.revision + 1, redone.revision)
        val finalStore = WorkbookStore(root)
        val final = finalStore.coordinators.open(initial.id)
        assertEquals(redone, final.state.value.document)
        val bytes = metadata(initial.id).readBytes()
        val duplicate = final.execute(request)
        assertTrue(duplicate.duplicate)
        assertEquals(accepted.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(redone, duplicate.document)
        assertArrayEquals("Retry cannot target a second sheet or create another revision", bytes, metadata(initial.id).readBytes())
        val checkpoint = finalStore.loadCheckpoint(initial.id)
        assertEquals(listOf(originalSheets), checkpoint.undo.map { it.worksheets })
        assertTrue(checkpoint.redo.isEmpty())
        assertEquals(listOf(request), checkpoint.receipts.map { it.command })
        val revisions = listOf(checkpoint.document) + checkpoint.undo + checkpoint.redo
        assertTrue(revisions.size > 1)
        revisions.forEach { revision ->
            assertEquals(originalSheets.map { it.id }, revision.worksheets.map { it.id })
            assertEquals(2, revision.worksheets.map { it.id }.distinct().size)
        }
        assertNativeValues(fixture, redone)
        if (backup != null) assertArrayEquals(legacyBytes!!, backup.readBytes())
    }

    private suspend fun fixture(): Fixture {
        val workbook = store.create("Unique worksheet fixture ${UUID.randomUUID()}")
        val input = File(root, "worksheet-input-${workbook.id}.csv").apply { writeText(SOURCE_CSV) }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        val first = imported.worksheets.single().copy(name = "East subset",
            rowFieldId = category.id, measureFieldId = sales.id, aggregation = Aggregation.SUM,
            markType = MarkType.TABLE, sortDescending = true,
            chart = ChartOptions(title = "East values", showGridlines = false),
            filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East"))))
        val second = WorksheetDef(id = UUID.randomUUID().toString(), name = "All categories",
            rowFieldId = category.id, measureFieldId = sales.id, aggregation = Aggregation.SUM,
            markType = MarkType.TABLE, sortDescending = true,
            chart = ChartOptions(title = "All values", showValueLabels = true))
        val document = store.save(imported.copy(worksheets = listOf(first, second), activeWorksheetId = first.id))
        return Fixture(document,
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes(),
            store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
    }

    private fun legacyJson(document: WorkbookDocument, version: Int): JSONObject {
        val raw = document.toJson().put("formatVersion", version)
            .put("unknownOriginalEvidence", "Keep these original unique worksheet definitions verbatim")
        if (version == 1) {
            val sheets = raw.getJSONArray("worksheets")
            for (index in 0 until sheets.length()) {
                val sheet = sheets.getJSONObject(index)
                val chart = document.worksheets[index].chart
                sheet.remove("chart")
                sheet.put("chartTitle", chart.title).put("showGridlines", chart.showGridlines)
                    .put("showValueLabels", chart.showValueLabels)
            }
        }
        return raw
    }

    private fun assertNativeValues(fixture: Fixture, document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        val path = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            document.worksheets.forEachIndexed { index, sheet ->
                val result = WorksheetQuery(engine).query(snapshot, sheet, path)
                assertEquals(if (index == 0) listOf(listOf("East", "15"))
                    else listOf(listOf("West", "20"), listOf("East", "15")),
                    result.result.rows.map { row -> row.map { it?.toString() } })
                val east = result.result.rows.indexOfFirst { it[0] == "East" }
                assertTrue(east >= 0)
                val rows = UnderlyingRowsQuery(engine).query(snapshot, sheet, result, setOf(east), path)!!
                assertEquals(listOf(listOf("East", "10"), listOf("East", "5")),
                    rows.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it[1] })
                assertEquals(index == 0, rows.worksheetFilterApplied)
                assertFalse(rows.truncated)
            }
        }
        assertSourceUnchanged(fixture, document)
    }

    private fun assertSourceUnchanged(fixture: Fixture, document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        assertArrayEquals(fixture.originalBytes,
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertArrayEquals(fixture.parquetBytes,
            store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
        assertTrue(File(store.workbookDir(document.id), "staging").listFiles().orEmpty().isEmpty())
    }

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")
    private fun backup(id: String, version: Int) = File(store.workbookDir(id), "migration/workbook-v$version-original.json")

    private data class Fixture(val document: WorkbookDocument, val originalBytes: ByteArray, val parquetBytes: ByteArray)

    companion object {
        private const val SOURCE_CSV = "Category,Sales\nEast,10\nWest,20\nEast,5\n"
    }
}
