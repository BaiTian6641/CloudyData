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
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.canonicalJson
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.4 / F01,F17,F36,F64 / Q33,Q34: native secondary aggregation refusal and original preservation. */
@RunWith(AndroidJUnit4::class)
class LegacySecondaryAggregationRefusalTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore
    private lateinit var document: WorkbookDocument
    private lateinit var pristineMetadata: ByteArray
    private lateinit var snapshotBytes: ByteArray
    private lateinit var sourceBytes: ByteArray
    private lateinit var engine: DuckDbEngine

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "secondary-aggregation-refusal-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        val book = store.create("Legacy secondary aggregation")
        val csv = File(root, "dual-axis.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("csv/dual_axis.csv").use { input ->
            csv.outputStream().use { output -> input.copyTo(output) }
        }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(book, Uri.fromFile(csv))
        val imported = importer.apply(book, pending)
        val snapshot = checkNotNull(imported.snapshot)
        val sheet = imported.worksheets.single().copy(
            rowFieldId = snapshot.columns.single { it.name == "month" }.id,
            measureFieldId = snapshot.columns.single { it.name == "sales" }.id,
            secondaryMeasureFieldId = snapshot.columns.single { it.name == "cost" }.id,
            aggregation = Aggregation.SUM,
            secondaryAggregation = Aggregation.AVG,
            markType = MarkType.BAR,
        )
        document = store.save(imported.copy(worksheets = listOf(sheet)))
        pristineMetadata = metadata().readBytes()
        snapshotBytes = snapshotFile().readBytes()
        sourceBytes = sourceFile().readBytes()
        engine = DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1)
        assertEquals(listOf("2026-01-01", "150", "100.0"), nativeJanuary(document))
        val sumControl = document.copy(worksheets = listOf(sheet.copy(secondaryAggregation = Aggregation.SUM)))
        assertEquals(listOf("2026-01-01", "150", "200"), nativeJanuary(sumControl))
        assertArrayEquals(pristineMetadata, metadata().readBytes())
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) engine.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun malformedSecondaryAggregationCannotBecomeSum() = runBlocking {
        val unexpected = mutableListOf<String>()
        for (version in listOf(1, 2)) {
            val json = legacy(version).apply { getJSONArray("worksheets").getJSONObject(0).put("secondaryAggregation", "AVGG") }
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            val loaded = runCatching { WorkbookStore(root).load(document.id) }
            if (loaded.isSuccess) {
                val accepted = loaded.getOrThrow()
                val january = runCatching { nativeJanuary(accepted) }.fold({ it.toString() }, { "native query failed: $it" })
                unexpected += "v$version decoded=${accepted.activeWorksheet?.secondaryAggregation}, nativeJanuary=$january, " +
                    "recoveryCandidatesRetained=${candidates.keys.all { it.exists() }}"
            } else {
                assertPreserved(bytes, candidates)
                assertTrue(runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
                assertPreserved(bytes, candidates)
            }
        }
        assertTrue(
            "Malformed present secondary aggregation must refuse both legacy files; AVG January100 must never become SUM200. " +
                "Unexpected acceptance: ${unexpected.joinToString("; ")}",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun malformedPresentSecondaryAggregationRefusesActiveAndInactiveLegacyChannels() = runBlocking {
        val malformed: List<Pair<String, Any>> = listOf(
            "empty string" to "",
            "whitespace string" to " \t ",
            "lowercase enum" to "avg",
            "padded enum" to " AVG ",
            "unknown enum" to "AVGG",
            "unsupported enum" to "MEDIAN",
            "explicit null" to JSONObject.NULL,
            "integer" to 7,
            "fractional number" to 1.5,
            "true boolean" to true,
            "false boolean" to false,
            "object" to JSONObject().put("value", "AVG"),
            "array" to JSONArray().put("AVG"),
        )
        for (version in listOf(1, 2)) for (active in listOf(true, false)) for ((label, value) in malformed) {
            val json = legacy(version)
            val sheet = json.getJSONArray("worksheets").getJSONObject(0)
            if (!active) sheet.put("secondaryMeasureFieldId", JSONObject.NULL)
            sheet.put("secondaryAggregation", value)
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            val description = "legacy v$version $label (secondary channel active=$active)"
            assertTrue("$description must refuse load", runCatching { WorkbookStore(root).loadCheckpoint(document.id) }.isFailure)
            assertPreserved(bytes, candidates)
            assertTrue("$description must refuse open", runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
            assertPreserved(bytes, candidates)
            assertTrue("$description must refuse save", runCatching {
                WorkbookStore(root).save(document.copy(name = "Must not rewrite $label"))
            }.isFailure)
            assertPreserved(bytes, candidates)
        }
    }

    @Test
    fun malformedV3HeadAndHistoryRefuseEvenWithRecomputedValidEnvelopeHashes() = runBlocking {
        document = store.coordinators.open(document.id).execute(
            WorkbookCommand.Rename("history-control", document.id, "aggregation-test-window", document.revision, "Head with real history"),
        ).document
        pristineMetadata = metadata().readBytes()
        val control = WorkbookStore(root).loadCheckpoint(document.id)
        assertEquals(1, control.undo.size)
        assertEquals(Aggregation.AVG, control.document.worksheets.single().secondaryAggregation)
        assertEquals(Aggregation.AVG, control.undo.single().worksheets.single().secondaryAggregation)
        assertEquals(1, control.receipts.size)
        assertEquals(listOf("2026-01-01", "150", "100.0"), nativeJanuary(control.document))

        val malformed: List<Pair<String, Any>> = listOf(
            "unknown enum" to "AVGG",
            "padded enum" to " AVG ",
            "explicit null" to JSONObject.NULL,
            "number" to 7,
        )
        for (location in listOf("head", "history")) for ((label, value) in malformed) {
            val json = JSONObject(String(pristineMetadata, Charsets.UTF_8))
            val journal = json.getJSONObject("commandJournal")
            val target = if (location == "head") json else journal.getJSONArray("undo").getJSONObject(0)
            target.getJSONArray("worksheets").getJSONObject(0).put("secondaryAggregation", value)
            val rawDocument = JSONObject(json.toString()).apply { remove("commandJournal") }
            journal.put("headSha256", digest(rawDocument))
            journal.put("sha256", digest(JSONObject(journal.toString()).apply { remove("sha256") }))
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            val error = runCatching { WorkbookStore(root).loadCheckpoint(document.id) }.exceptionOrNull()
            assertTrue("v3 $location $label must refuse despite valid hashes", error != null)
            assertFalse("this case must reach content admission, not checksum failure", error!!.message.orEmpty().contains("checksum", ignoreCase = true))
            assertPreserved(bytes, candidates)
            assertTrue(runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
            assertTrue(runCatching { WorkbookStore(root).save(document.copy(name = "Must not rewrite malformed $location")) }.isFailure)
            assertPreserved(bytes, candidates)
        }
    }

    private fun legacy(version: Int) = JSONObject(String(pristineMetadata, Charsets.UTF_8)).apply {
        put("formatVersion", version)
        remove("commandJournal")
    }

    private fun recoveryCandidates(): Map<File, ByteArray> = listOf(
        File(store.workbookDir(document.id), "workbook-911.tmp").apply { writeBytes(pristineMetadata) },
        File(store.workbookDir(document.id), "workbook-912.tmp").apply { writeText("partial unpublished aggregation candidate") },
    ).associateWith { it.readBytes() }

    private fun assertPreserved(bytes: ByteArray, candidates: Map<File, ByteArray>) {
        assertArrayEquals("refusal keeps exact original metadata", bytes, metadata().readBytes())
        candidates.forEach { (file, original) -> assertArrayEquals("preserve ${file.name}", original, file.readBytes()) }
        assertFalse("refused decoding cannot create migration backups", File(store.workbookDir(document.id), "migration").exists())
        assertArrayEquals(snapshotBytes, snapshotFile().readBytes())
        assertArrayEquals(sourceBytes, sourceFile().readBytes())
    }

    private fun nativeJanuary(book: WorkbookDocument): List<String?> {
        val snapshot = checkNotNull(book.snapshot)
        val result = WorksheetQuery(engine).query(
            snapshot, checkNotNull(book.activeWorksheet), store.resolveInsideWorkbook(book.id, snapshot.snapshotFile).absolutePath,
        )
        return result.result.rows.single { it[0] == "2026-01-01" }.map { it?.toString() }
    }

    private fun digest(json: JSONObject): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson(json).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun metadata() = File(store.workbookDir(document.id), "workbook.json")
    private fun snapshotFile() = store.resolveInsideWorkbook(document.id, checkNotNull(document.snapshot).snapshotFile)
    private fun sourceFile() = store.resolveInsideWorkbook(document.id, checkNotNull(document.snapshot).originalFile!!)
}
