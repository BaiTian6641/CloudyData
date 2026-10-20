package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.engine.WorksheetReferenceChannel
import io.github.baitian6641.cloudydata.engine.WorksheetReferenceException
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.DataSnapshot
import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.4 / F01,F05,F14,F64 / Q01,Q16,Q17,Q19,Q34: real CSV/Parquet/native query oracles. */
@RunWith(AndroidJUnit4::class)
class WorksheetReferenceTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var document: WorkbookDocument
    private lateinit var snapshot: DataSnapshot
    private lateinit var sheet: WorksheetDef
    private lateinit var engine: DuckDbEngine
    private lateinit var metadataBytes: ByteArray
    private lateinit var snapshotPath: String
    private lateinit var unavailablePath: String

    private val category get() = snapshot.columns.single { it.name == "Category" }
    private val series get() = snapshot.columns.single { it.name == "Series" }
    private val sales get() = snapshot.columns.single { it.name == "Sales" }
    private val cost get() = snapshot.columns.single { it.name == "Cost" }

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "worksheet-reference-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        val book = store.create("Reference admission")
        val fixture = File(root, "reference.csv").apply {
            writeText("Category,Series,Sales,Cost\nEast,N,10,4\nEast,S,5,2\nWest,N,20,8\n")
        }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(book, Uri.fromFile(fixture))
        val imported = importer.apply(book, pending)
        snapshot = checkNotNull(imported.snapshot)
        sheet = imported.worksheets.single().copy(
            rowFieldId = category.id,
            measureFieldId = sales.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sortDescending = false,
        )
        document = store.save(imported.copy(worksheets = listOf(sheet)))
        metadataBytes = metadata().readBytes()
        snapshotPath = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
        unavailablePath = File(root, "must-not-be-opened.parquet").absolutePath
        assertFalse(File(unavailablePath).exists())
        engine = DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1)
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) engine.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun validReferencesKeepExactGroupsSeriesSecondaryFilterAndUnderlyingRows() {
        assertEquals(3L, snapshot.rowCount)
        val grouped = query(sheet)
        assertNotNull(grouped.queryIdentity)
        assertEquals(listOf(listOf("East", "15"), listOf("West", "20")), textRows(grouped))
        val east = grouped.result.rows.indexOfFirst { it[0] == "East" }
        assertTrue(east >= 0)
        val eastRows = checkNotNull(UnderlyingRowsQuery(engine).query(snapshot, sheet, grouped, setOf(east), snapshotPath))
        assertEquals(1, eastRows.selectedMarkCount)
        assertFalse(eastRows.worksheetFilterApplied)
        assertEquals(
            listOf(listOf("East", "N", "10", "4"), listOf("East", "S", "5", "2")),
            eastRows.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it[1] },
        )

        val seriesSheet = sheet.copy(seriesFieldId = series.id)
        val bySeries = query(seriesSheet)
        assertEquals(
            listOf(listOf("East", "N", "10"), listOf("East", "S", "5"), listOf("West", "N", "20")),
            textRows(bySeries),
        )
        val secondary = query(sheet.copy(secondaryMeasureFieldId = cost.id, secondaryAggregation = Aggregation.AVG))
        assertEquals(listOf("row", "value", "secondary_value"), secondary.result.columns.map { it.name })
        assertEquals(listOf(listOf("East", "15", "3.0"), listOf("West", "20", "8.0")), textRows(secondary))

        val filteredSheet = seriesSheet.copy(
            filter = MarkFilter(FilterMode.KEEP, category.id, series.id, listOf(FilterMark("East", "N"))),
        )
        val filtered = query(filteredSheet)
        assertEquals(listOf(listOf("East", "N", "10")), textRows(filtered))
        val filteredRows = checkNotNull(UnderlyingRowsQuery(engine).query(snapshot, filteredSheet, filtered, setOf(0), snapshotPath))
        assertTrue(filteredRows.worksheetFilterApplied)
        assertEquals(listOf(listOf("East", "N", "10", "4")), filteredRows.result.rows.map { row -> row.map { it?.toString() } })
        assertMetadataUnchanged()
    }

    @Test
    fun missingMappingsAreTypedErrorsBeforeAnyUnavailableSnapshotPathCanExecute() {
        val cases = listOf(
            Triple(WorksheetReferenceChannel.ROWS, "missing-row", sheet.copy(rowFieldId = "missing-row")),
            Triple(WorksheetReferenceChannel.MEASURE, "missing-measure", sheet.copy(measureFieldId = "missing-measure")),
            Triple(WorksheetReferenceChannel.SERIES, "missing-series", sheet.copy(seriesFieldId = "missing-series")),
            Triple(WorksheetReferenceChannel.SECONDARY_MEASURE, "missing-secondary", sheet.copy(secondaryMeasureFieldId = "missing-secondary")),
        )
        cases.forEach { (channel, fieldId, broken) ->
            assertReference(channel, fieldId) { WorksheetQuery(engine).query(snapshot, broken, unavailablePath) }
        }
        // Missing Rows cannot become global SUM=35, missing Measure cannot
        // become East COUNT=2 / West COUNT=1, and missing series/secondary cannot
        // collapse the result shape and report a different successful query.
        assertMetadataUnchanged()
    }

    @Test
    fun missingAndMismatchedPersistedFilterAnchorsCannotSilentlyExposeUnfilteredRows() {
        val seriesSheet = sheet.copy(seriesFieldId = series.id)
        val accepted = query(seriesSheet)
        val unknownRows = seriesSheet.copy(
            filter = MarkFilter(FilterMode.KEEP, "missing-filter-row", series.id, listOf(FilterMark("East", "N"))),
        )
        val unknownSeries = seriesSheet.copy(
            filter = MarkFilter(FilterMode.KEEP, category.id, "missing-filter-series", listOf(FilterMark("East", "N"))),
        )
        val mismatchedRows = seriesSheet.copy(
            filter = MarkFilter(FilterMode.KEEP, series.id, series.id, listOf(FilterMark("East", "N"))),
        )
        val mismatchedSeries = seriesSheet.copy(
            filter = MarkFilter(FilterMode.KEEP, category.id, cost.id, listOf(FilterMark("East", "N"))),
        )
        listOf(
            Triple(WorksheetReferenceChannel.FILTER_ROWS, "missing-filter-row", unknownRows),
            Triple(WorksheetReferenceChannel.FILTER_SERIES, "missing-filter-series", unknownSeries),
            Triple(WorksheetReferenceChannel.FILTER_SHAPE, null, mismatchedRows),
            Triple(WorksheetReferenceChannel.FILTER_SHAPE, null, mismatchedSeries),
        ).forEach { (channel, fieldId, broken) ->
            assertReference(channel, fieldId) { WorksheetQuery(engine).query(snapshot, broken, unavailablePath) }
            assertReference(channel, fieldId) {
                UnderlyingRowsQuery(engine).query(snapshot, broken, accepted, setOf(0), unavailablePath)
            }
        }
        assertMetadataUnchanged()
    }

    @Test
    fun duplicateOrEmptySnapshotFieldIdentityIsRejectedEvenWhenTheFieldIsNotMapped() {
        val duplicate = snapshot.copy(columns = snapshot.columns + cost.copy(name = "Different physical column"))
        val empty = snapshot.copy(columns = snapshot.columns.map { if (it.id == cost.id) it.copy(id = "") else it })
        val accepted = query(sheet)
        listOf(cost.id to duplicate, "" to empty).forEach { (badId, invalidSnapshot) ->
            assertReference(WorksheetReferenceChannel.SNAPSHOT, badId) {
                WorksheetQuery(engine).query(invalidSnapshot, sheet, unavailablePath)
            }
            assertReference(WorksheetReferenceChannel.SNAPSHOT, badId) {
                UnderlyingRowsQuery(engine).query(invalidSnapshot, sheet, accepted, setOf(0), unavailablePath)
            }
        }
        assertMetadataUnchanged()
    }

    @Test
    fun viewDataRejectsOldSnapshotSheetQueryShapeFieldDefinitionsAndMissingResultIdentity() {
        val accepted = query(sheet)
        val east = accepted.result.rows.indexOfFirst { it[0] == "East" }
        assertTrue(east >= 0)
        assertReference(WorksheetReferenceChannel.RESULT) {
            UnderlyingRowsQuery(engine).query(snapshot.copy(id = "different-snapshot"), sheet, accepted, setOf(east), unavailablePath)
        }
        val otherShapes = listOf(
            sheet.copy(id = "different-sheet"),
            sheet.copy(aggregation = Aggregation.AVG),
            sheet.copy(sortDescending = true),
            sheet.copy(markType = MarkType.TABLE),
            sheet.copy(rowFieldId = series.id),
            sheet.copy(measureFieldId = cost.id),
            sheet.copy(seriesFieldId = series.id),
            sheet.copy(secondaryMeasureFieldId = cost.id, secondaryAggregation = Aggregation.AVG),
            sheet.copy(filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East")))),
        )
        otherShapes.forEach { changed ->
            assertReference(WorksheetReferenceChannel.RESULT) {
                UnderlyingRowsQuery(engine).query(snapshot, changed, accepted, setOf(east), unavailablePath)
            }
        }
        val renamedField = snapshot.copy(columns = snapshot.columns.map {
            if (it.id == category.id) it.copy(name = "Renamed physical field") else it
        })
        assertReference(WorksheetReferenceChannel.RESULT) {
            UnderlyingRowsQuery(engine).query(renamedField, sheet, accepted, setOf(east), unavailablePath)
        }
        assertReference(WorksheetReferenceChannel.RESULT) {
            UnderlyingRowsQuery(engine).query(snapshot, sheet, accepted.copy(queryIdentity = null), setOf(east), unavailablePath)
        }
        assertMetadataUnchanged()
    }

    @Test
    fun presentationOnlyNameAndChartChangesKeepTheSameQueryIdentityAndExactViewData() {
        val accepted = query(sheet)
        val presentation = sheet.copy(
            name = "Presentation-only worksheet title",
            chart = sheet.chart.copy(title = "Presentation-only chart title", showGridlines = false, valueDecimals = 2),
        )
        val afterPresentation = query(presentation)
        assertNotNull(accepted.queryIdentity)
        assertEquals(accepted.queryIdentity, afterPresentation.queryIdentity)
        assertEquals(accepted.sql, afterPresentation.sql)
        assertEquals(textRows(accepted), textRows(afterPresentation))
        val east = accepted.result.rows.indexOfFirst { it[0] == "East" }
        assertTrue(east >= 0)
        val rows = checkNotNull(UnderlyingRowsQuery(engine).query(snapshot, presentation, accepted, setOf(east), snapshotPath))
        assertEquals(
            listOf(listOf("East", "N", "10", "4"), listOf("East", "S", "5", "2")),
            rows.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it[1] },
        )
        assertMetadataUnchanged()
    }

    @Test
    fun legitimatelyUnmappedDimensionReturnsNullOnlyAfterAllPersistedReferencesValidate() {
        val measureOnly = sheet.copy(rowFieldId = null)
        val actual = query(measureOnly)
        assertEquals(listOf(listOf("35")), textRows(actual))
        assertNull(actual.rowField)
        assertNull(UnderlyingRowsQuery(engine).query(snapshot, measureOnly, actual, setOf(0), unavailablePath))
        assertNull("legacy dimensionless results cannot invent source identity", UnderlyingRowsQuery(engine).query(
            snapshot, measureOnly, actual.copy(queryIdentity = null), setOf(0), unavailablePath,
        ))
        assertReference(WorksheetReferenceChannel.MEASURE, "missing-dimensionless-measure") {
            UnderlyingRowsQuery(engine).query(
                snapshot, measureOnly.copy(measureFieldId = "missing-dimensionless-measure"), actual, setOf(0), unavailablePath,
            )
        }
        assertReference(WorksheetReferenceChannel.ROWS, "missing-dimension") {
            UnderlyingRowsQuery(engine).query(
                snapshot, measureOnly.copy(rowFieldId = "missing-dimension"), actual, setOf(0), unavailablePath,
            )
        }
        assertMetadataUnchanged()
    }

    private fun query(definition: WorksheetDef): WorksheetResult = WorksheetQuery(engine).query(snapshot, definition, snapshotPath)

    private fun textRows(result: WorksheetResult): List<List<String?>> = result.result.rows.map { row -> row.map { it?.toString() } }

    private fun assertReference(channel: WorksheetReferenceChannel, fieldId: String? = null, operation: () -> Any?) {
        val error = runCatching(operation).exceptionOrNull()
        assertTrue("expected typed $channel admission error, got $error", error is WorksheetReferenceException)
        error as WorksheetReferenceException
        assertEquals(channel, error.channel)
        if (fieldId != null) assertEquals(fieldId, error.fieldId)
        assertTrue(error.message.orEmpty().isNotBlank())
    }

    private fun metadata() = File(store.workbookDir(document.id), "workbook.json")

    private fun assertMetadataUnchanged() {
        assertArrayEquals("query admission never rewrites persisted repairs or history", metadataBytes, metadata().readBytes())
        assertEquals(document, WorkbookStore(root).load(document.id))
    }
}
