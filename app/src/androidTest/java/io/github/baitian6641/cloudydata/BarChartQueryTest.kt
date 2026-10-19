package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartSpecBuilder
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.MarkFilters
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.StackMode
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarChartQueryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var importer: CsvImportService

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "bar-chart-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        importer = CsvImportService(context, store)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun colorSeriesBarGroupsByCategoryAndSeriesAndStacksSums() {
        val workbook = store.create("Bar chart")
        val pending = runBlocking { importer.stageAndInspect(workbook, fixtureUri("csv/chart_line_series.csv")) }
        val applied = runBlocking { importer.apply(workbook, pending) }
        val snapshot = applied.snapshot!!
        val series = snapshot.columns.first { it.name == "series" }
        val eventDate = snapshot.columns.first { it.name == "event_date" }
        val value = snapshot.columns.first { it.name == "value" }
        val sheet = applied.worksheets.single().copy(
            rowFieldId = eventDate.id,
            measureFieldId = value.id,
            seriesFieldId = series.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sortDescending = true,
            chart = ChartOptions(
                title = "Stacked sales",
                xAxisTitle = "Event date",
                yAxisTitle = "Sales",
                seriesTitle = "Region",
                barStackMode = StackMode.STACKED,
            ),
        )
        val saved = store.save(applied.copy(worksheets = listOf(sheet)))
        val reopened = store.load(saved.id)
        val reopenedSheet = reopened.worksheets.single()
        assertEquals(series.id, reopenedSheet.seriesFieldId)
        assertEquals(MarkType.BAR, reopenedSheet.markType)
        assertEquals(StackMode.STACKED, reopenedSheet.chart.barStackMode)

        val path = store.resolveInsideWorkbook(reopened.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val query = WorksheetQuery(engine)
            val result = query.query(reopened.snapshot!!, reopenedSheet, path)
            assertEquals(MarkType.BAR, result.markType)
            assertEquals(series.id, result.seriesField?.id)
            assertEquals(listOf("row", "series", "value"), result.result.columns.map { it.name })
            assertEquals(
                listOf(
                    listOf("2026-03-01", "North", "30"),
                    listOf("2026-02-01", "North", "20"),
                    listOf("2026-02-01", "South", "2"),
                    listOf("2026-01-01", "North", "10"),
                    listOf("2026-01-01", "South", "1"),
                    listOf("2026-04-01", "South", "4"),
                ),
                result.result.rows.map { row -> row.map { it?.toString() } },
            )

            val spec = ChartSpecBuilder.build(
                chartId = "bar-${reopened.id}",
                revision = 3,
                result = result,
                options = reopenedSheet.chart,
                derivedTitle = "derived",
            )
            assertEquals(ChartKind.BAR, spec.kind)
            assertEquals(StackMode.STACKED, spec.barStackMode)
            assertTrue(spec.barHasSeries)
            assertEquals(listOf("North", "South"), spec.series.map { it.key })
            assertEquals(
                listOf("2026-03-01", "2026-02-01", "2026-01-01", "2026-04-01"),
                spec.series.flatMap { it.points }.sortedBy { it.xOrder }.map { it.x }.distinct(),
            )
            val south = spec.series.first { it.key == "South" }
            assertEquals(
                listOf("2026-02-01", "2026-01-01", "2026-04-01"),
                south.points.map { it.x },
            )
            assertEquals(listOf(20.0, 10.0, 0.0), south.points.map { it.stackBase })
            assertEquals(listOf(2.0, 1.0, 4.0), south.points.map { it.y })
            val febTotal = spec.series.flatMap { it.points }
                .filter { it.x == "2026-02-01" }
                .sumOf { it.y!! }
            assertEquals(22.0, febTotal, 0.0)
            assertEquals("Region", spec.legend.title)
            assertTrue(spec.legend.visible)

            val underlying = UnderlyingRowsQuery(engine).query(
                snapshot = reopened.snapshot!!,
                sheet = reopenedSheet,
                worksheetResult = result,
                selectedRows = setOf(2),
                snapshotPath = path,
            )!!
            assertEquals("Feb South must resolve to one source row", 1, underlying.result.rows.size)
            val sourceRow = underlying.result.rows.single()
            assertTrue(sourceRow.any { it?.toString() == "South" })
            assertTrue(sourceRow.any { it?.toString() == "2" })

            val filter = MarkFilters.fromResult(
                reopenedSheet,
                result,
                setOf(2),
                FilterMode.KEEP,
            )!!
            assertEquals("Feb South filter must carry the series identity", series.id, filter.seriesFieldId)
            val filtered = query.query(
                reopened.snapshot!!,
                reopenedSheet.copy(filter = filter),
                path,
            )
            assertEquals(
                listOf(listOf("2026-02-01", "South", "2")),
                filtered.result.rows.map { row -> row.map { it?.toString() } },
            )

            val ascending = query.query(
                reopened.snapshot!!,
                reopenedSheet.copy(sortDescending = false),
                path,
            )
            assertEquals(
                listOf("2026-04-01", "2026-01-01", "2026-02-01", "2026-03-01"),
                ascending.result.rows.map { it[0].toString() }.distinct(),
            )
        }
    }

    private fun fixtureUri(assetPath: String): Uri {
        val target = File(context.cacheDir, "bar-fixture-${UUID.randomUUID()}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(target)
    }
}
