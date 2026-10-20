package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookCheckpoint
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

/** S03.4 / F01,F64 / Q33,Q34: decoded workbook-version admission and byte preservation. */
@RunWith(AndroidJUnit4::class)
class WorkbookVersionRefusalTest {
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
        root = File(context.filesDir, "workbook-version-refusal-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        val book = store.create("Workbook version admission")
        val csv = File(root, "version.csv").apply { writeText("Category,Sales\nEast,10\nWest,20\nEast,5\n") }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(book, Uri.fromFile(csv))
        val imported = importer.apply(book, pending)
        val snapshot = checkNotNull(imported.snapshot)
        val sheet = imported.worksheets.single().copy(
            rowFieldId = snapshot.columns.single { it.name == "Category" }.id,
            measureFieldId = snapshot.columns.single { it.name == "Sales" }.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sortDescending = false,
        )
        document = store.save(imported.copy(worksheets = listOf(sheet)))
        pristineMetadata = metadata().readBytes()
        snapshotBytes = snapshotFile().readBytes()
        sourceBytes = sourceFile().readBytes()
        engine = DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1)
        assertEquals(listOf(listOf("East", "15"), listOf("West", "20")), nativeGroups(document))
        assertArrayEquals(pristineMetadata, metadata().readBytes())
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) engine.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun coercibleVersionsCannotSelectLegacySchema() = runBlocking {
        val unexpected = mutableListOf<String>()
        val values: List<Pair<String, Any>> = listOf(
            "fraction 2.9" to 2.9,
            "string 2" to "2",
            "wrapping Long 4294967298" to 4_294_967_298L,
        )
        for ((label, value) in values) {
            val json = JSONObject(String(pristineMetadata, Charsets.UTF_8)).apply {
                put("formatVersion", value)
                remove("commandJournal")
            }
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            val loaded = runCatching { WorkbookStore(root).load(document.id) }
            if (loaded.isSuccess) {
                val accepted = loaded.getOrThrow()
                val valuesAfterDecode = runCatching { nativeGroups(accepted) }.fold({ it.toString() }, { "native query failed: $it" })
                unexpected += "$label normalizedFormat=${accepted.formatVersion}, nativeValues=$valuesAfterDecode, " +
                    "recoveryCandidatesRetained=${candidates.keys.all { it.exists() }}"
            } else {
                assertPreserved(bytes, candidates)
                assertTrue(runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
                assertPreserved(bytes, candidates)
            }
        }
        assertTrue(
            "Only supported numeric whole versions may select a workbook schema. Unexpected acceptance: ${unexpected.joinToString("; ")}",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun directDocumentAndCheckpointDecodersRejectMalformedAndNonfiniteDecodedVersions() {
        val candidates = recoveryCandidates()
        for (fault in versionFaults()) {
            val json = bareDocument()
            applyFault(json, fault)
            assertTrue("${fault.label} cannot decode as a document", runCatching { WorkbookDocument.fromJson(json) }.isFailure)
            assertTrue("${fault.label} cannot select a checkpoint branch", runCatching { WorkbookCheckpoint.fromJson(json) }.isFailure)
        }
        // JSONObject(Map) genuinely preserves these Number objects on Android;
        // unlike put(), it does not reject them before the version decoder runs.
        // This is decoded-value coverage, not a claim about invalid JSON tokens.
        for ((label, value) in listOf("NaN" to Double.NaN, "positive infinity" to Double.POSITIVE_INFINITY, "negative infinity" to Double.NEGATIVE_INFINITY)) {
            val source = bareDocument()
            val entries = linkedMapOf<String, Any>()
            source.keys().forEach { key -> entries[key] = source.get(key) }
            entries["formatVersion"] = value
            val json = JSONObject(entries)
            val actual = json.get("formatVersion")
            assertTrue("the native object must hold the actual $label Number", actual is Number)
            actual as Number
            if (value.isNaN()) assertTrue(actual.toDouble().isNaN()) else assertEquals(value, actual.toDouble(), 0.0)
            assertTrue("$label cannot decode as a document", runCatching { WorkbookDocument.fromJson(json) }.isFailure)
            assertTrue("$label cannot select a checkpoint branch", runCatching { WorkbookCheckpoint.fromJson(json) }.isFailure)
        }
        assertPreserved(pristineMetadata, candidates)
    }

    @Test
    fun malformedStoredVersionCannotLoadOpenSaveOrConsumeRecoveryCandidates() = runBlocking {
        for (fault in versionFaults()) {
            val json = bareDocument()
            applyFault(json, fault)
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            assertTrue("${fault.label} must refuse stored load", runCatching { WorkbookStore(root).loadCheckpoint(document.id) }.isFailure)
            assertPreserved(bytes, candidates)
            assertTrue("${fault.label} must refuse coordinator opening", runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
            assertPreserved(bytes, candidates)
            assertTrue("${fault.label} must refuse save before migration or replacement", runCatching {
                WorkbookStore(root).save(document.copy(name = "Must not rewrite invalid format version"))
            }.isFailure)
            assertPreserved(bytes, candidates)
        }
    }

    @Test
    fun rehashedV3HeadAndHistoryCannotHideMalformedVersionValues() = runBlocking {
        document = store.coordinators.open(document.id).execute(
            WorkbookCommand.Rename("version-history", document.id, "version-test-window", document.revision, "Version history head"),
        ).document
        pristineMetadata = metadata().readBytes()
        val control = WorkbookStore(root).loadCheckpoint(document.id)
        assertEquals(1, control.undo.size)
        assertEquals(1, control.receipts.size)
        assertEquals(listOf(listOf("East", "15"), listOf("West", "20")), nativeGroups(control.document))
        val faults = listOf(
            VersionFault("missing", missing = true),
            VersionFault("explicit null", JSONObject.NULL),
            VersionFault("string current version", "3"),
            VersionFault("fractional current version", 3.9),
            VersionFault("future version", 4),
            VersionFault("positive Long wrapping to current", 4_294_967_299L),
            VersionFault("negative Long wrapping to current", -4_294_967_293L),
        )
        for (location in listOf("head", "history")) for (fault in faults) {
            val json = JSONObject(String(pristineMetadata, Charsets.UTF_8))
            val journal = json.getJSONObject("commandJournal")
            val target = if (location == "head") json else journal.getJSONArray("undo").getJSONObject(0)
            applyFault(target, fault)
            journal.put("headSha256", digest(JSONObject(json.toString()).apply { remove("commandJournal") }))
            journal.put("sha256", digest(JSONObject(journal.toString()).apply { remove("sha256") }))
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            val error = runCatching { WorkbookStore(root).loadCheckpoint(document.id) }.exceptionOrNull()
            assertTrue("$location ${fault.label} must refuse despite valid hashes", error != null)
            assertFalse("this case reaches version/content admission beyond checksums", error!!.message.orEmpty().contains("checksum", ignoreCase = true))
            assertPreserved(bytes, candidates)
            assertTrue(runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
            assertTrue(runCatching { WorkbookStore(root).save(document.copy(name = "Must not rewrite malformed $location version")) }.isFailure)
            assertPreserved(bytes, candidates)
        }
    }

    private data class VersionFault(val label: String, val value: Any = JSONObject.NULL, val missing: Boolean = false)

    private fun versionFaults(): List<VersionFault> = listOf(
        VersionFault("missing", missing = true),
        VersionFault("explicit null", JSONObject.NULL),
        VersionFault("empty string", ""),
        VersionFault("whitespace string", " \t "),
        VersionFault("string legacy one", "1"),
        VersionFault("string legacy two", "2"),
        VersionFault("string current", "3"),
        VersionFault("string fractional", "2.9"),
        VersionFault("string exponent", "2e0"),
        VersionFault("boolean true", true),
        VersionFault("boolean false", false),
        VersionFault("object", JSONObject().put("value", 2)),
        VersionFault("array", JSONArray().put(2)),
        VersionFault("fraction between legacy versions", 1.5),
        VersionFault("fraction selecting legacy two", 2.9),
        VersionFault("fraction immediately above legacy two", 2.0000000000000004),
        VersionFault("fraction selecting current", 3.9),
        VersionFault("negative integer", -1),
        VersionFault("zero integer", 0),
        VersionFault("future integer", 4),
        VersionFault("positive Long wrapping to legacy one", 4_294_967_297L),
        VersionFault("positive Long wrapping to legacy two", 4_294_967_298L),
        VersionFault("positive Long wrapping to current", 4_294_967_299L),
        VersionFault("negative Long wrapping to legacy two", -4_294_967_294L),
        VersionFault("maximum Long", Long.MAX_VALUE),
        VersionFault("minimum Long", Long.MIN_VALUE),
    )

    private fun bareDocument() = JSONObject(String(pristineMetadata, Charsets.UTF_8)).apply { remove("commandJournal") }

    private fun applyFault(json: JSONObject, fault: VersionFault) {
        if (fault.missing) json.remove("formatVersion") else json.put("formatVersion", fault.value)
    }

    private fun recoveryCandidates(): Map<File, ByteArray> = listOf(
        File(store.workbookDir(document.id), "workbook-921.tmp").apply { writeBytes(pristineMetadata) },
        File(store.workbookDir(document.id), "workbook-922.tmp").apply { writeText("partial unpublished version candidate") },
    ).associateWith { it.readBytes() }

    private fun assertPreserved(bytes: ByteArray, candidates: Map<File, ByteArray>) {
        assertArrayEquals("refusal keeps exact original metadata", bytes, metadata().readBytes())
        candidates.forEach { (file, original) -> assertArrayEquals("preserve ${file.name}", original, file.readBytes()) }
        assertFalse("version refusal cannot create migration backups", File(store.workbookDir(document.id), "migration").exists())
        assertArrayEquals(snapshotBytes, snapshotFile().readBytes())
        assertArrayEquals(sourceBytes, sourceFile().readBytes())
    }

    private fun nativeGroups(book: WorkbookDocument): List<List<String?>> {
        val snapshot = checkNotNull(book.snapshot)
        return WorksheetQuery(engine).query(
            snapshot, checkNotNull(book.activeWorksheet), store.resolveInsideWorkbook(book.id, snapshot.snapshotFile).absolutePath,
        ).result.rows.map { row -> row.map { it?.toString() } }
    }

    private fun digest(json: JSONObject): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson(json).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun metadata() = File(store.workbookDir(document.id), "workbook.json")
    private fun snapshotFile() = store.resolveInsideWorkbook(document.id, checkNotNull(document.snapshot).snapshotFile)
    private fun sourceFile() = store.resolveInsideWorkbook(document.id, checkNotNull(document.snapshot).originalFile!!)
}
