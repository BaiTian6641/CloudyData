package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.io.BufferedWriter
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkloadProbeTest {
    private lateinit var context: Context
    private lateinit var root: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "workload-test-${UUID.randomUUID()}")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun millionRowImportAndGroupedQueryPreserveTotals() {
        if (Build.DEVICE != "quartz" || !Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            // The 1M x 12 import is a physical-arm64 timeline probe. Other ABIs
            // still run the engine/import/query contracts in EngineSmokeTest.
            Log.i("CloudyWorkload", "skipped on non-physical target: ${Build.DEVICE}/${Build.SUPPORTED_ABIS.first()}")
            return
        }
        val rowCount = 1_000_000
        val expectedTotals = LongArray(5)
        val csv = File(context.cacheDir, "d1-probe-${UUID.randomUUID()}.csv")
        val writeStart = System.nanoTime()
        BufferedWriter(csv.bufferedWriter(Charsets.UTF_8), 1 shl 20).use { writer ->
            writer.write("id,region,category,value,quantity,flag,text,c8,c9,c10,c11,c12\n")
            for (index in 0 until rowCount) {
                val region = index % 5
                val value = index % 1000
                expectedTotals[region] += value.toLong()
                writer.write(
                    "$index,R$region,C${index % 50},$value,${index % 10},${index % 2},item-$index," +
                        "${index % 7},${index % 11},${index % 13},${index % 17},${index % 19}\n",
                )
            }
        }
        val writeMs = elapsedMs(writeStart)
        val memoryBefore = readMemory()
        val store = WorkbookStore(root)
        val importer = CsvImportService(context, store)
        val workbook = store.create("D1 probe")

        val inspectStart = System.nanoTime()
        val pending = runBlocking { importer.stageAndInspect(workbook, Uri.fromFile(csv)) }
        val inspectMs = elapsedMs(inspectStart)
        assertEquals(50, pending.previewRows.size)

        val applyStart = System.nanoTime()
        val applied = runBlocking { importer.apply(workbook, pending) }
        val applyMs = elapsedMs(applyStart)
        assertEquals(rowCount.toLong(), applied.snapshot?.rowCount)
        val snapshotPath = store.resolveInsideWorkbook(applied.id, applied.snapshot!!.snapshotFile).absolutePath
        val memoryAfter = readMemory()

        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val queryStart = System.nanoTime()
            val result = engine.execute(
                "SELECT region, SUM(value) AS total, COUNT(*) AS n " +
                    "FROM read_parquet('${snapshotPath.replace("'", "''")}') GROUP BY region ORDER BY region",
                maxRows = 100,
            )
            val queryMs = elapsedMs(queryStart)
            assertEquals(5, result.rows.size)
            result.rows.forEachIndexed { index, row ->
                assertEquals("R$index", row[0])
                assertEquals(expectedTotals[index].toString(), row[1].toString())
                assertEquals(200_000L, row[2])
            }

            val region = applied.snapshot!!.columns.first { it.name == "region" }
            val value = applied.snapshot!!.columns.first { it.name == "value" }
            val sheet = WorksheetDef(
                id = "sheet",
                name = "Sheet 1",
                rowFieldId = region.id,
                measureFieldId = value.id,
                aggregation = Aggregation.SUM,
                markType = MarkType.TABLE,
                sortDescending = false,
            )
            val worksheet = WorksheetQuery(engine).query(applied.snapshot!!, sheet, snapshotPath)
            assertEquals(5, worksheet.result.rows.size)
            worksheet.result.rows.forEach { row ->
                val regionName = row[0] as String
                val expected = expectedTotals[regionName.removePrefix("R").toInt()]
                assertEquals(expected.toString(), row[1].toString())
            }

            Log.i(
                "CloudyWorkload",
                "rows=$rowCount csvBytes=${csv.length()} parquetBytes=" +
                    "${File(snapshotPath).length()} writeMs=$writeMs inspectMs=$inspectMs applyMs=$applyMs " +
                    "queryMs=$queryMs memoryBefore=$memoryBefore memoryAfter=$memoryAfter config=" +
                    "${Build.HARDWARE}/${Build.SUPPORTED_ABIS.first()}/${Build.VERSION.SDK_INT}",
            )
            assertTrue("import exceeded probe budget: ${applyMs}ms", applyMs < 300_000)
            assertTrue("grouped query exceeded probe budget: ${queryMs}ms", queryMs < 30_000)
        }
        csv.delete()
    }

    private fun elapsedMs(startNanos: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

    private fun readMemory(): MemorySnapshot {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            totalPssKb = info.totalPss,
            nativePssKb = info.nativePss,
            javaHeapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024,
        )
    }

    private data class MemorySnapshot(
        val totalPssKb: Int,
        val nativePssKb: Int,
        val javaHeapUsedKb: Long,
    )
}
