package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.chart.ChartSpecBuilder
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
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
class DualAxisQueryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var importer: CsvImportService

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "dual-axis-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        importer = CsvImportService(context, store)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun secondaryMeasureSharesRowsAndBuildsLineLayer() {
        val workbook = store.create("Dual axis")
        val pending = runBlocking { importer.stageAndInspect(workbook, fixtureUri("csv/dual_axis.csv")) }
        val applied = runBlocking { importer.apply(workbook, pending) }
        val snapshot = applied.snapshot!!
        val month = snapshot.columns.first { it.name == "month" }
        val sales = snapshot.columns.first { it.name == "sales" }
        val cost = snapshot.columns.first { it.name == "cost" }
        val sheet = applied.worksheets.single().copy(
            rowFieldId = month.id,
            measureFieldId = sales.id,
            secondaryMeasureFieldId = cost.id,
            aggregation = Aggregation.SUM,
            secondaryAggregation = Aggregation.AVG,
            markType = MarkType.BAR,
            chart = ChartOptions(
                xAxisTitle = "Month",
                yAxisTitle = "Sales",
                secondaryAxisTitle = "Cost",
            ),
        )
        val saved = store.save(applied.copy(worksheets = listOf(sheet)))
        val reopened = store.load(saved.id)
        val reopenedSheet = reopened.worksheets.single()
        assertEquals(cost.id, reopenedSheet.secondaryMeasureFieldId)
        assertEquals(Aggregation.AVG, reopenedSheet.secondaryAggregation)
        assertEquals("Cost", reopenedSheet.chart.secondaryAxisTitle)

        val path = store.resolveInsideWorkbook(reopened.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val result = WorksheetQuery(engine).query(reopened.snapshot!!, reopenedSheet, path)
            assertEquals(listOf("row", "value", "secondary_value"), result.result.columns.map { it.name })
            assertEquals(cost.id, result.secondaryMeasureField?.id)
            assertEquals(
                listOf(
                    listOf("2026-01-01", "150", "100.0"),
                    listOf("2026-02-01", "120", "20.0"),
                    listOf("2026-03-01", "80", "60.0"),
                ),
                result.result.rows.map { row -> row.map { it?.toString() } },
            )
            val spec = ChartSpecBuilder.build(
                "dual-${reopened.id}", 1, result, reopenedSheet.chart, "derived",
            )
            val layer = spec.secondaryLayer!!
            assertEquals("Cost", layer.axis.title)
            assertEquals(listOf(100.0, 20.0, 60.0), layer.series.single().points.map { it.y })
            assertEquals(
                listOf("2026-01-01", "2026-02-01", "2026-03-01"),
                layer.series.single().points.map { it.x },
            )

            val underlying = UnderlyingRowsQuery(engine).query(
                snapshot = reopened.snapshot!!,
                sheet = reopenedSheet,
                worksheetResult = result,
                selectedRows = setOf(0),
                snapshotPath = path,
            )!!
            assertEquals("Jan must resolve to its two source rows", 2, underlying.result.rows.size)
            assertTrue(underlying.result.rows.all { row -> row.any { it?.toString() == "2026-01-01" } })
            assertTrue(underlying.result.rows.any { row -> row.any { it?.toString() == "100" } })
            assertTrue(underlying.result.rows.any { row -> row.any { it?.toString() == "50" } })
        }
    }

    private fun fixtureUri(assetPath: String): Uri {
        val target = File(context.cacheDir, "dual-fixture-${UUID.randomUUID()}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(target)
    }
}
