package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkFilterQueryTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var importer: CsvImportService
    private val engines = mutableListOf<DuckDbEngine>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "filter-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        importer = CsvImportService(context, store)
    }

    @After
    fun tearDown() {
        engines.forEach { it.close() }
        root.deleteRecursively()
    }

    @Test
    fun markFilterAndWorksheetJsonRoundTrip() {
        val filter = MarkFilter(
            mode = FilterMode.EXCLUDE,
            rowFieldId = "row",
            seriesFieldId = "series",
            marks = listOf(FilterMark("2026-01-01", "North"), FilterMark(null, "South")),
        )
        assertEquals(filter, MarkFilter.fromJson(filter.toJson()))

        val sheet = WorksheetDef(
            id = "sheet",
            name = "Sheet 1",
            rowFieldId = "row",
            measureFieldId = "value",
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sortDescending = false,
            seriesFieldId = "series",
            filter = filter,
        )
        val restored = WorksheetDef.fromJson(sheet.toJson())
        assertEquals(filter, restored.filter)
        assertEquals(null, WorksheetDef.fromJson(sheet.copy(filter = null).toJson()).filter)
    }

    @Test
    fun keepAndExcludeConstrainTheSourceBeforeAggregation() {
        val (_, applied) = importFixture()
        val snapshot = applied.snapshot!!
        val series = snapshot.columns.first { it.name == "series" }
        val value = snapshot.columns.first { it.name == "value" }
        val base = Sheet(
            rowFieldId = series.id,
            measureFieldId = value.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
        )
        val engine = newEngine()
        val path = store.resolveInsideWorkbook(applied.id, snapshot.snapshotFile).absolutePath

        val unfiltered = query(engine, snapshot, base, path)
        assertEquals(listOf("North", "South"), unfiltered.result.rows.map { it[0] })
        assertEquals(listOf("60", "7"), unfiltered.result.rows.map { it[1].toString() })

        val keep = base.copy(
            filter = MarkFilter(FilterMode.KEEP, series.id, marks = listOf(FilterMark("North"))),
        )
        val kept = query(engine, snapshot, keep, path)
        assertEquals(listOf("North"), kept.result.rows.map { it[0] })
        assertEquals("60", kept.result.rows.single()[1].toString())
        assertTrue(kept.sql.contains(" WHERE "))

        val exclude = base.copy(
            filter = MarkFilter(FilterMode.EXCLUDE, series.id, marks = listOf(FilterMark("North"))),
        )
        val excluded = query(engine, snapshot, exclude, path)
        assertEquals(listOf("South"), excluded.result.rows.map { it[0] })
        assertEquals("7", excluded.result.rows.single()[1].toString())

        val sourceCount = engine.execute(
            "SELECT COUNT(*) FROM read_parquet('${path.replace("'", "''")}')",
            maxRows = 1,
        )
        assertEquals(6L, sourceCount.rows.single()[0])
        assertEquals(6L, applied.snapshot?.rowCount)
    }

    @Test
    fun seriesPairsKeepAndExcludeExactMarks() {
        val (_, applied) = importFixture()
        val snapshot = applied.snapshot!!
        val date = snapshot.columns.first { it.name == "event_date" }
        val series = snapshot.columns.first { it.name == "series" }
        val value = snapshot.columns.first { it.name == "value" }
        val base = Sheet(
            rowFieldId = date.id,
            measureFieldId = value.id,
            seriesFieldId = series.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
        )
        val engine = newEngine()
        val path = store.resolveInsideWorkbook(applied.id, snapshot.snapshotFile).absolutePath

        val keep = base.copy(
            filter = MarkFilter(
                FilterMode.KEEP,
                date.id,
                series.id,
                marks = listOf(
                    FilterMark("2026-01-01", "North"),
                    FilterMark("2026-02-01", "South"),
                ),
            ),
        )
        val kept = query(engine, snapshot, keep, path)
        assertEquals(2, kept.result.rows.size)
        assertEquals(
            listOf(listOf("2026-01-01", "North", "10"), listOf("2026-02-01", "South", "2")),
            kept.result.rows.map { row -> row.map { it.toString() } },
        )

        val exclude = keep.copy(filter = keep.filter?.copy(mode = FilterMode.EXCLUDE))
        val excluded = query(engine, snapshot, exclude, path)
        assertEquals(4, excluded.result.rows.size)
        assertFalse(excluded.result.rows.any { it[0] == "2026-01-01" && it[1] == "North" })
        assertFalse(excluded.result.rows.any { it[0] == "2026-02-01" && it[1] == "South" })
    }

    private fun importFixture(): Pair<WorkbookDocument, WorkbookDocument> {
        val document = store.create("Filter ${UUID.randomUUID()}")
        val fixture = File(context.cacheDir, "filter-${UUID.randomUUID()}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("csv/chart_line_series.csv").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        val pending = runBlocking { importer.stageAndInspect(document, Uri.fromFile(fixture)) }
        val applied = runBlocking { importer.apply(document, pending) }
        return document to applied
    }

    private fun newEngine(): DuckDbEngine =
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).also { engines += it }

    private fun query(
        engine: DuckDbEngine,
        snapshot: io.github.baitian6641.cloudydata.model.DataSnapshot,
        sheet: Sheet,
        path: String,
    ) = WorksheetQuery(engine).query(snapshot, sheet.def(), path)

    private data class Sheet(
        val rowFieldId: String?,
        val measureFieldId: String?,
        val aggregation: Aggregation,
        val markType: MarkType,
        val seriesFieldId: String? = null,
        val filter: MarkFilter? = null,
    ) {
        fun def() = WorksheetDef(
            id = "sheet",
            name = "Sheet 1",
            rowFieldId = rowFieldId,
            measureFieldId = measureFieldId,
            aggregation = aggregation,
            markType = markType,
            sortDescending = true,
            seriesFieldId = seriesFieldId,
            filter = filter,
        )
    }
}
