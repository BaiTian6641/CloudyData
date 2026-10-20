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

/** S03.4 / F01,F05,F14,F64 / Q16,Q17,Q34: native legacy decoder refusal and byte preservation. */
@RunWith(AndroidJUnit4::class)
class LegacyFilterRefusalTest {
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
        root = File(context.filesDir, "legacy-filter-refusal-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        val book = store.create("Legacy filtered workbook")
        val csv = File(root, "filtered.csv").apply { writeText("Category,Sales\nEast,10\nWest,20\nEast,5\n") }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(book, Uri.fromFile(csv))
        val imported = importer.apply(book, pending)
        val snapshot = checkNotNull(imported.snapshot)
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        val sheet = imported.worksheets.single().copy(
            rowFieldId = category.id,
            measureFieldId = sales.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sortDescending = false,
            filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East"))),
        )
        document = store.save(imported.copy(worksheets = listOf(sheet)))
        pristineMetadata = metadata().readBytes()
        snapshotBytes = snapshotFile().readBytes()
        sourceBytes = sourceFile().readBytes()
        engine = DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1)
        assertEquals("the real fixture starts constrained to East", listOf(listOf("East", "15")), nativeGroups(document))
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) engine.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun futureFilterVersionIsRefusedWithoutRewritingLegacyFiles() = runBlocking {
        val unexpected = mutableListOf<String>()
        for (legacyVersion in listOf(1, 2)) {
            val bytes = writeLegacy(legacyVersion) { sheet -> sheet.getJSONObject("filter").put("version", 2) }
            val candidates = recoveryCandidates()
            val loaded = runCatching { WorkbookStore(root).load(document.id) }
            if (loaded.isSuccess) {
                val accepted = loaded.getOrThrow()
                val groups = runCatching { nativeGroups(accepted) }.fold({ it.toString() }, { "native query failed: $it" })
                unexpected += "v$legacyVersion effectiveFilter=${accepted.activeWorksheet?.filter}, nativeGroups=$groups, " +
                    "recoveryCandidatesRetained=${candidates.keys.all { it.exists() }}"
            } else {
                assertPreserved(bytes, candidates)
                assertTrue(runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
                assertPreserved(bytes, candidates)
            }
        }
        assertTrue(
            "Future filter version must refuse both legacy files; KEEP East must never become unfiltered East15/West20. " +
                "Unexpected acceptance: ${unexpected.joinToString("; ")}",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun presentFilterMustBeAnObjectAndUnknownFilterKeysCannotBeIgnored() = runBlocking {
        val cases: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "string filter" to { it.put("filter", "KEEP East"); Unit },
            "empty string filter" to { it.put("filter", ""); Unit },
            "numeric filter" to { it.put("filter", 7); Unit },
            "boolean filter" to { it.put("filter", false); Unit },
            "array filter" to { it.put("filter", JSONArray().put("East")); Unit },
            "unknown filter option" to { it.getJSONObject("filter").put("futureOption", true); Unit },
            "unknown null filter option" to { it.getJSONObject("filter").put("futureOption", JSONObject.NULL); Unit },
        )
        for (version in listOf(1, 2)) for ((label, mutate) in cases) assertRefusedLegacy(version, label, mutate)
    }

    @Test
    fun presentFilterVersionAndModeHaveStrictSupportedTypesAndValues() = runBlocking {
        val cases: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "string version" to { it.getJSONObject("filter").put("version", "1"); Unit },
            "fractional version" to { it.getJSONObject("filter").put("version", 1.5); Unit },
            "zero version" to { it.getJSONObject("filter").put("version", 0); Unit },
            "negative version" to { it.getJSONObject("filter").put("version", -1); Unit },
            "null version" to { it.getJSONObject("filter").put("version", JSONObject.NULL); Unit },
            "boolean version" to { it.getJSONObject("filter").put("version", true); Unit },
            "object version" to { it.getJSONObject("filter").put("version", JSONObject()); Unit },
            "array version" to { it.getJSONObject("filter").put("version", JSONArray()); Unit },
            "missing mode" to { it.getJSONObject("filter").remove("mode"); Unit },
            "unknown mode" to { it.getJSONObject("filter").put("mode", "REPLACE"); Unit },
            "lowercase mode" to { it.getJSONObject("filter").put("mode", "keep"); Unit },
            "empty mode" to { it.getJSONObject("filter").put("mode", ""); Unit },
            "numeric mode" to { it.getJSONObject("filter").put("mode", 1); Unit },
            "boolean mode" to { it.getJSONObject("filter").put("mode", true); Unit },
            "null mode" to { it.getJSONObject("filter").put("mode", JSONObject.NULL); Unit },
            "object mode" to { it.getJSONObject("filter").put("mode", JSONObject()); Unit },
            "array mode" to { it.getJSONObject("filter").put("mode", JSONArray()); Unit },
        )
        for (version in listOf(1, 2)) for ((label, mutate) in cases) assertRefusedLegacy(version, label, mutate)
    }

    @Test
    fun marksAreBoundedCompleteTypedRecordsAndCannotBeSkippedTruncatedOrReinterpreted() = runBlocking {
        fun oneMark(mark: Any): JSONArray = JSONArray().put(mark)
        val cases: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "missing marks" to { it.getJSONObject("filter").remove("marks"); Unit },
            "string marks" to { it.getJSONObject("filter").put("marks", "East"); Unit },
            "null marks" to { it.getJSONObject("filter").put("marks", JSONObject.NULL); Unit },
            "object marks" to { it.getJSONObject("filter").put("marks", JSONObject()); Unit },
            "empty marks" to { it.getJSONObject("filter").put("marks", JSONArray()); Unit },
            "oversized marks" to { sheet ->
                val marks = JSONArray()
                repeat(2002) { marks.put(JSONObject().put("row", "East").put("series", JSONObject.NULL)) }
                sheet.getJSONObject("filter").put("marks", marks)
            },
            "string mark" to { it.getJSONObject("filter").put("marks", oneMark("East")); Unit },
            "null mark" to { it.getJSONObject("filter").put("marks", oneMark(JSONObject.NULL)); Unit },
            "numeric mark" to { it.getJSONObject("filter").put("marks", oneMark(7)); Unit },
            "valid then invalid mark" to { sheet ->
                sheet.getJSONObject("filter").getJSONArray("marks").put(false)
                Unit
            },
            "missing row key" to { it.getJSONObject("filter").put("marks", oneMark(JSONObject().put("series", JSONObject.NULL))); Unit },
            "numeric row value" to { it.getJSONObject("filter").getJSONArray("marks").getJSONObject(0).put("row", 7); Unit },
            "boolean row value" to { it.getJSONObject("filter").getJSONArray("marks").getJSONObject(0).put("row", true); Unit },
            "array row value" to { it.getJSONObject("filter").getJSONArray("marks").getJSONObject(0).put("row", JSONArray()); Unit },
            "object row value" to { it.getJSONObject("filter").getJSONArray("marks").getJSONObject(0).put("row", JSONObject()); Unit },
            "series value without anchor" to { it.getJSONObject("filter").getJSONArray("marks").getJSONObject(0).put("series", "N"); Unit },
            "unknown mark key" to { it.getJSONObject("filter").getJSONArray("marks").getJSONObject(0).put("futureMarkOption", true); Unit },
            "missing paired series key" to { sheet ->
                val seriesId = checkNotNull(document.snapshot).columns.single { it.name == "Sales" }.id
                sheet.put("seriesFieldId", seriesId)
                val filter = sheet.getJSONObject("filter").put("seriesFieldId", seriesId)
                filter.put("marks", oneMark(JSONObject().put("row", "East")))
                Unit
            },
            "numeric paired series value" to { sheet ->
                val seriesId = checkNotNull(document.snapshot).columns.single { it.name == "Sales" }.id
                sheet.put("seriesFieldId", seriesId)
                val filter = sheet.getJSONObject("filter").put("seriesFieldId", seriesId)
                filter.put("marks", oneMark(JSONObject().put("row", "East").put("series", 7)))
                Unit
            },
        )
        for (version in listOf(1, 2)) for ((label, mutate) in cases) assertRefusedLegacy(version, label, mutate)
    }

    @Test
    fun filterAnchorsMustRemainNonblankStringsRatherThanCoercedOrDroppedReferences() = runBlocking {
        val invalid: List<Pair<String, Any>> = listOf(
            "empty" to "",
            "blank" to " \t ",
            "numeric" to 7,
            "boolean" to true,
            "array" to JSONArray().put("field"),
            "object" to JSONObject().put("id", "field"),
        )
        for (version in listOf(1, 2)) {
            assertRefusedLegacy(version, "missing row anchor") { it.getJSONObject("filter").remove("rowFieldId") }
            assertRefusedLegacy(version, "null row anchor") { it.getJSONObject("filter").put("rowFieldId", JSONObject.NULL) }
            for ((label, value) in invalid) {
                assertRefusedLegacy(version, "$label row anchor") { it.getJSONObject("filter").put("rowFieldId", value) }
                assertRefusedLegacy(version, "$label series anchor") { it.getJSONObject("filter").put("seriesFieldId", value) }
            }
        }
    }

    @Test
    fun everyNonnullWorksheetReferenceMustBeANonblankString() = runBlocking {
        val invalid: List<Pair<String, Any>> = listOf(
            "empty" to "",
            "blank" to " \t ",
            "numeric" to 7,
            "boolean" to false,
            "array" to JSONArray().put("field"),
            "object" to JSONObject().put("id", "field"),
        )
        for (version in listOf(1, 2)) {
            for (reference in listOf("rowFieldId", "measureFieldId", "seriesFieldId", "secondaryMeasureFieldId")) {
                for ((label, value) in invalid) assertRefusedLegacy(version, "$label $reference") { it.put(reference, value) }
            }
        }
    }

    @Test
    fun recomputedValidV3EnvelopeCannotAuthorizeMalformedFilterOrReferenceContent() = runBlocking {
        val cases: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "future filter" to { it.getJSONObject("filter").put("version", 2); Unit },
            "wrong filter container" to { it.put("filter", "KEEP East"); Unit },
            "invalid mark after valid mark" to { it.getJSONObject("filter").getJSONArray("marks").put(7); Unit },
            "numeric row reference" to { it.put("rowFieldId", 7); Unit },
            "empty series reference" to { it.put("seriesFieldId", ""); Unit },
        )
        for ((label, mutate) in cases) {
            val json = JSONObject(String(pristineMetadata, Charsets.UTF_8))
            mutate(json.getJSONArray("worksheets").getJSONObject(0))
            val rawDocument = JSONObject(json.toString()).apply { remove("commandJournal") }
            val journal = json.getJSONObject("commandJournal")
            journal.put("headSha256", digest(rawDocument))
            journal.put("sha256", digest(JSONObject(journal.toString()).apply { remove("sha256") }))
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            val error = runCatching { WorkbookStore(root).loadCheckpoint(document.id) }.exceptionOrNull()
            assertTrue("$label must refuse despite valid envelope hashes", error != null)
            assertFalse("$label exercises content decoding, not a stale checksum", error!!.message.orEmpty().contains("checksum", ignoreCase = true))
            assertPreserved(bytes, candidates)
            assertTrue(runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
            assertTrue(runCatching { WorkbookStore(root).save(document.copy(name = "Must not rewrite $label")) }.isFailure)
            assertPreserved(bytes, candidates)
        }
    }

    private fun writeLegacy(version: Int, mutate: (JSONObject) -> Unit): ByteArray {
        val json = JSONObject(String(pristineMetadata, Charsets.UTF_8)).apply {
            put("formatVersion", version)
            remove("commandJournal")
        }
        mutate(json.getJSONArray("worksheets").getJSONObject(0))
        metadata().writeText(json.toString(2) + "\n")
        return metadata().readBytes()
    }

    private suspend fun assertRefusedLegacy(version: Int, label: String, mutate: (JSONObject) -> Unit) {
        val bytes = writeLegacy(version, mutate)
        val candidates = recoveryCandidates()
        assertTrue("legacy v$version $label must refuse load", runCatching { WorkbookStore(root).loadCheckpoint(document.id) }.isFailure)
        assertPreserved(bytes, candidates)
        assertTrue("legacy v$version $label must refuse open", runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
        assertPreserved(bytes, candidates)
        assertTrue("legacy v$version $label must refuse save", runCatching {
            WorkbookStore(root).save(document.copy(name = "Must not rewrite $label"))
        }.isFailure)
        assertPreserved(bytes, candidates)
    }

    private fun recoveryCandidates(): Map<File, ByteArray> = listOf(
        File(store.workbookDir(document.id), "workbook-901.tmp").apply { writeBytes(pristineMetadata) },
        File(store.workbookDir(document.id), "workbook-902.tmp").apply { writeText("partial unpublished candidate") },
    ).associateWith { it.readBytes() }

    private fun assertPreserved(bytes: ByteArray, candidates: Map<File, ByteArray>) {
        assertArrayEquals("refusal must preserve exact original metadata", bytes, metadata().readBytes())
        candidates.forEach { (candidate, original) -> assertArrayEquals("preserve ${candidate.name}", original, candidate.readBytes()) }
        assertFalse("refused decoding must not start migration", File(store.workbookDir(document.id), "migration").exists())
        assertArrayEquals(snapshotBytes, snapshotFile().readBytes())
        assertArrayEquals(sourceBytes, sourceFile().readBytes())
    }

    private fun nativeGroups(book: WorkbookDocument): List<List<String?>> {
        val snapshot = checkNotNull(book.snapshot)
        return WorksheetQuery(engine).query(
            snapshot, checkNotNull(book.activeWorksheet),
            store.resolveInsideWorkbook(book.id, snapshot.snapshotFile).absolutePath,
        ).result.rows.map { row -> row.map { it?.toString() } }
    }

    private fun digest(json: JSONObject): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson(json).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun metadata() = File(store.workbookDir(document.id), "workbook.json")
    private fun snapshotFile() = store.resolveInsideWorkbook(document.id, checkNotNull(document.snapshot).snapshotFile)
    private fun sourceFile() = store.resolveInsideWorkbook(document.id, checkNotNull(document.snapshot).originalFile!!)
}
