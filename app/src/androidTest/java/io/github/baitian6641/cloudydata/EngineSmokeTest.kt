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
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class EngineSmokeTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var importer: CsvImportService
    private val engines = mutableListOf<DuckDbEngine>()
    private val workbooks = mutableListOf<WorkbookDocument>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "engine-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        importer = CsvImportService(context, store)
    }

    @After
    fun tearDown() {
        engines.forEach { it.close() }
        root.deleteRecursively()
    }

    @Test
    fun typedInt64DecimalTimestampAndUnicodeRoundTrip() {
        val engine = newEngine()
        val result = engine.execute(
            """
            SELECT
              9007199254740993::BIGINT AS big,
              (0.10::DECIMAL(18,2) + 0.20::DECIMAL(18,2)) AS dec,
              TIMESTAMP '2026-10-18 12:34:56.123456' AS ts,
              'héllo 世界 🚀' AS txt
            """.trimIndent(),
            maxRows = 1,
        )
        assertEquals(listOf("BIGINT", "DECIMAL(18,2)", "TIMESTAMP", "VARCHAR"), result.columns.map { it.type })
        val row = result.rows.single()
        assertEquals(9007199254740993L, row[0])
        assertEquals("0.30", row[1])
        assertEquals("2026-10-18 12:34:56.123456", row[2])
        assertEquals("héllo 世界 🚀", row[3])
        assertTrue(DuckDbEngine.version().isNotBlank())
    }

    @Test
    fun csvImportPublishesVerifiedSnapshotAndReopens() {
        val document = newWorkbook()
        val pending = runBlocking { importer.stageAndInspect(document, fixtureUri("csv/q01_adversarial.csv")) }
        assertEquals(3, pending.previewRows.size)
        val id = pending.columns.first { it.name == "id" }
        assertEquals("VARCHAR", id.physicalType)
        assertEquals(
            listOf("00123", "00124", "00125"),
            pending.previewRows.map { it[pending.previewColumns.indexOf("id")] as String },
        )
        val nameIndex = pending.previewColumns.indexOf("name")
        assertEquals("Alpha, Inc.", pending.previewRows[0][nameIndex])
        assertEquals("Quote \"inside\"", pending.previewRows[1][nameIndex])
        val noteIndex = pending.previewColumns.indexOf("note")
        assertEquals("line one\nline two", pending.previewRows[0][noteIndex])

        val applied = runBlocking { importer.apply(document, pending) }
        assertNotNull(applied.snapshot)
        assertEquals(3L, applied.snapshot?.rowCount ?: -1L)
        assertTrue(store.resolveInsideWorkbook(applied.id, applied.snapshot!!.snapshotFile).exists())
        assertTrue(store.resolveInsideWorkbook(applied.id, applied.snapshot!!.originalFile!!).exists())

        val reopened = store.load(applied.id)
        assertEquals(applied.snapshot?.id, reopened.snapshot?.id)

        val engine = newEngine()
        val path = store.resolveInsideWorkbook(reopened.id, reopened.snapshot!!.snapshotFile).absolutePath
        val ids = engine.execute(
            "SELECT id FROM read_parquet('${path.replace("'", "''")}') ORDER BY id",
            maxRows = 10,
        )
        assertEquals(listOf("00123", "00124", "00125"), ids.rows.map { it[0] })
    }

    @Test
    fun decimalAndBigIntSurviveCsvQuery() {
        val document = newWorkbook()
        val pending = runBlocking { importer.stageAndInspect(document, fixtureUri("csv/q02_exact.csv")) }
        val applied = runBlocking { importer.apply(document, pending) }
        val engine = newEngine()
        val path = store.resolveInsideWorkbook(applied.id, applied.snapshot!!.snapshotFile).absolutePath
        val result = engine.execute(
            "SELECT big, dec, txt, event_time FROM read_parquet('${path.replace("'", "''")}') ORDER BY id LIMIT 1",
            maxRows = 1,
        )
        assertEquals(9007199254740993L, result.rows[0][0])
        assertEquals(0, java.math.BigDecimal("0.10").compareTo(java.math.BigDecimal(result.rows[0][1] as String)))
        assertEquals("héllo 世界 🚀", result.rows[0][2])
        assertEquals("2026-10-18 12:34:56.123456", result.rows[0][3])
    }

    @Test
    fun worksheetAggregationUsesExactDecimalSum() {
        val document = newWorkbook()
        val pending = runBlocking { importer.stageAndInspect(document, fixtureUri("csv/q02_exact.csv")) }
        val applied = runBlocking { importer.apply(document, pending) }
        val snapshot = applied.snapshot!!
        val id = snapshot.columns.first { it.name == "id" }
        val dec = snapshot.columns.first { it.name == "dec" }
        val sheet = WorksheetDef(
            id = "sheet",
            name = "Sheet 1",
            rowFieldId = null,
            measureFieldId = dec.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sortDescending = false,
        )
        val engine = newEngine()
        val path = store.resolveInsideWorkbook(applied.id, snapshot.snapshotFile).absolutePath
        val result = WorksheetQuery(engine).query(snapshot, sheet, path)
        assertEquals(0, java.math.BigDecimal("0.30").compareTo(java.math.BigDecimal(result.result.rows.first()[0] as String)))
    }

    private fun newEngine(): DuckDbEngine = DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).also { engines += it }

    private fun newWorkbook(): WorkbookDocument {
        val document = store.create("Test ${workbooks.size + 1}")
        workbooks += document
        return document
    }

    private fun fixtureUri(assetPath: String): Uri {
        val target = File(context.cacheDir, "fixture-${UUID.randomUUID()}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input -> target.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(target)
    }
}
