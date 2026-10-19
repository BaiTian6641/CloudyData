package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.EngineColumn
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.DataSnapshot
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.LogicalType
import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewDataQueryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var importer: CsvImportService
    private val engines = mutableListOf<DuckDbEngine>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "view-data-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        importer = CsvImportService(context, store)
    }

    @After
    fun tearDown() {
        engines.forEach { it.close() }
        root.deleteRecursively()
    }

    @Test
    fun selectedAggregateResolvesToExactSourceRowsAndHonorsWorksheetFilter() {
        val fixture = File(context.cacheDir, "view-data-${UUID.randomUUID()}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("csv/chart_line_series.csv")
            .use { input -> fixture.outputStream().use { input.copyTo(it) } }
        val workbook = store.create("View data ${UUID.randomUUID()}")
        val pending = runBlocking { importer.stageAndInspect(workbook, Uri.fromFile(fixture)) }
        val applied = runBlocking { importer.apply(workbook, pending) }
        val snapshot = applied.snapshot!!
        val date = snapshot.columns.first { it.name == "event_date" }
        val value = snapshot.columns.first { it.name == "value" }
        val sheet = WorksheetDef(
            id = "sheet",
            name = "Sheet 1",
            rowFieldId = date.id,
            measureFieldId = value.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sortDescending = false,
        )
        val engine = newEngine()
        val path = store.resolveInsideWorkbook(applied.id, snapshot.snapshotFile).absolutePath
        val aggregated = WorksheetQuery(engine).query(snapshot, sheet, path)
        val januaryIndex = aggregated.result.rows.indexOfFirst { it[0] == "2026-01-01" }
        assertTrue("January aggregate is missing", januaryIndex >= 0)

        val rows = UnderlyingRowsQuery(engine).query(
            snapshot = snapshot,
            sheet = sheet,
            worksheetResult = aggregated,
            selectedRows = setOf(januaryIndex),
            snapshotPath = path,
        )!!

        assertEquals(1, rows.selectedMarkCount)
        assertTrue(rows.sql.contains("LIMIT 201"))
        assertEquals(false, rows.truncated)
        assertEquals(false, rows.worksheetFilterApplied)
        assertEquals(
            listOf(listOf("North", "2026-01-01", "10"), listOf("South", "2026-01-01", "1")),
            rows.result.rows.map { row -> row.map { it.toString() } }.sortedBy { it.first() },
        )

        val series = snapshot.columns.first { it.name == "series" }
        val filteredSheet = WorksheetDef(
            id = "filtered",
            name = "Sheet 2",
            rowFieldId = series.id,
            measureFieldId = value.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sortDescending = false,
            filter = MarkFilter(
                mode = FilterMode.EXCLUDE,
                rowFieldId = series.id,
                marks = listOf(FilterMark("North")),
            ),
        )
        val filteredAggregate = WorksheetQuery(engine).query(snapshot, filteredSheet, path)
        val southIndex = filteredAggregate.result.rows.indexOfFirst { it[0] == "South" }
        assertTrue("South aggregate is missing", southIndex >= 0)
        val filteredRows = UnderlyingRowsQuery(engine).query(
            snapshot = snapshot,
            sheet = filteredSheet,
            worksheetResult = filteredAggregate,
            selectedRows = setOf(southIndex),
            snapshotPath = path,
        )!!
        assertTrue(filteredRows.worksheetFilterApplied)
        assertEquals(
            listOf(
                listOf("South", "2026-01-01", "1"),
                listOf("South", "2026-02-01", "2"),
                listOf("South", "2026-04-01", "4"),
            ),
            filteredRows.result.rows.map { row -> row.map { it.toString() } }.sortedBy { it[1] },
        )

        val sourceCount = engine.execute(
            "SELECT COUNT(*) FROM read_parquet('${path.replace("'", "''")}')",
            maxRows = 1,
        )
        assertEquals(6L, sourceCount.rows.single()[0])
        assertEquals(6L, applied.snapshot?.rowCount)
    }

    @Test
    fun seriesMarkResolvesOnlyTheExactRowAndSeriesPair() {
        val fixture = File(context.cacheDir, "view-data-series-${UUID.randomUUID()}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("csv/chart_line_series.csv")
            .use { input -> fixture.outputStream().use { input.copyTo(it) } }
        val workbook = store.create("View data series ${UUID.randomUUID()}")
        val pending = runBlocking { importer.stageAndInspect(workbook, Uri.fromFile(fixture)) }
        val applied = runBlocking { importer.apply(workbook, pending) }
        val snapshot = applied.snapshot!!
        val date = snapshot.columns.first { it.name == "event_date" }
        val series = snapshot.columns.first { it.name == "series" }
        val value = snapshot.columns.first { it.name == "value" }
        val lineSheet = WorksheetDef(
            id = "line",
            name = "Line",
            rowFieldId = date.id,
            measureFieldId = value.id,
            seriesFieldId = series.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sortDescending = false,
        )
        val engine = newEngine()
        val path = store.resolveInsideWorkbook(applied.id, snapshot.snapshotFile).absolutePath
        val aggregated = WorksheetQuery(engine).query(snapshot, lineSheet, path)
        val northJanuary = aggregated.result.rows.indexOfFirst {
            it[0] == "2026-01-01" && it[1] == "North"
        }
        assertTrue("North January line mark is missing", northJanuary >= 0)

        val rows = UnderlyingRowsQuery(engine).query(
            snapshot = snapshot,
            sheet = lineSheet,
            worksheetResult = aggregated,
            selectedRows = setOf(northJanuary),
            snapshotPath = path,
        )!!
        assertEquals(1, rows.selectedMarkCount)
        assertEquals(
            listOf(listOf("North", "2026-01-01", "10")),
            rows.result.rows.map { row -> row.map { it.toString() } },
        )
    }

    @Test
    fun rowsWithoutDimensionIdentityAreNotInvented() {
        val measure = FieldDef("value", "value", "BIGINT", LogicalType.INTEGER, FieldRole.MEASURE)
        val snapshot = DataSnapshot(
            id = "snapshot",
            displayName = "none",
            sourceKind = "test",
            snapshotFile = "unused",
            originalFile = null,
            importedAtEpochMs = 0,
            rowCount = 1,
            columns = listOf(measure),
        )
        val result = WorksheetResult(
            result = EngineResult(listOf(EngineColumn("value", "BIGINT")), listOf(listOf(10L)), truncated = false),
            rowField = null,
            measureField = measure,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sql = "test",
        )
        val sheet = WorksheetDef(
            id = "sheet",
            name = "Sheet 1",
            rowFieldId = null,
            measureFieldId = measure.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sortDescending = false,
        )
        assertNull(
            UnderlyingRowsQuery(newEngine()).query(
                snapshot = snapshot,
                sheet = sheet,
                worksheetResult = result,
                selectedRows = setOf(0),
                snapshotPath = "unused",
            ),
        )
    }

    private fun newEngine(): DuckDbEngine =
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).also { engines += it }
}
