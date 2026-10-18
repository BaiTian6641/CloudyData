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
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef
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
class LineChartQueryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var importer: CsvImportService

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "line-chart-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        importer = CsvImportService(context, store)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun multiSeriesLinePreservesObservedPointsAndAbsentMonth() {
        val workbook = store.create("Line chart")
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
            markType = MarkType.LINE,
            chart = ChartOptions(
                title = "Observed sales",
                xAxisTitle = "Event date",
                yAxisTitle = "Sales",
                seriesTitle = "Region",
                yMin = 0.0,
                yMax = 40.0,
                showGridlines = true,
                showValueLabels = true,
                legendVisible = true,
                legendPosition = LegendPosition.RIGHT,
            ),
        )
        val saved = store.save(applied.copy(worksheets = listOf(sheet)))
        val reopened = store.load(saved.id)
        val reopenedSheet = reopened.worksheets.single()
        assertEquals(series.id, reopenedSheet.seriesFieldId)
        assertEquals(MarkType.LINE, reopenedSheet.markType)
        assertEquals("Event date", reopenedSheet.chart.xAxisTitle)
        assertEquals(LegendPosition.RIGHT, reopenedSheet.chart.legendPosition)

        val path = store.resolveInsideWorkbook(reopened.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val result = WorksheetQuery(engine).query(reopened.snapshot!!, reopenedSheet, path)
            assertEquals(sheet.seriesFieldId, result.seriesField?.id)
            assertEquals(MarkType.LINE, result.markType)
            assertEquals(listOf("row", "series", "value"), result.result.columns.map { it.name })
            assertEquals(
                listOf(
                    listOf("2026-01-01", "North", "10"),
                    listOf("2026-01-01", "South", "1"),
                    listOf("2026-02-01", "North", "20"),
                    listOf("2026-02-01", "South", "2"),
                    listOf("2026-03-01", "North", "30"),
                    listOf("2026-04-01", "South", "4"),
                ),
                result.result.rows.map { row -> row.map { it?.toString() } },
            )
            assertTrue("March must not be synthesized", result.result.rows.none { it[0] == "2026-03-01" && it[1] == "South" })

            val spec = ChartSpecBuilder.build(
                chartId = "line-${reopened.id}",
                revision = 2,
                result = result,
                options = reopenedSheet.chart,
                derivedTitle = "derived",
            )
            assertEquals(ChartKind.LINE, spec.kind)
            assertEquals(2, spec.series.size)
            val north = spec.series.first { it.key == "North" }
            val south = spec.series.first { it.key == "South" }
            assertEquals(listOf("2026-01-01", "2026-02-01", "2026-03-01"), north.points.map { it.x })
            assertEquals(listOf(10.0, 20.0, 30.0), north.points.map { it.y })
            assertEquals(listOf("2026-01-01", "2026-02-01", "2026-04-01"), south.points.map { it.x })
            assertEquals(listOf(1.0, 2.0, 4.0), south.points.map { it.y })
            assertEquals(67.0, spec.series.flatMap { it.points }.sumOf { it.y!! }, 0.0)
            assertEquals(40.0, spec.yAxis.max!!, 0.0)
            assertEquals(LegendPosition.RIGHT, spec.legend.position)
        }
    }

    private fun fixtureUri(assetPath: String): Uri {
        val target = File(context.cacheDir, "line-fixture-${UUID.randomUUID()}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(target)
    }
}
