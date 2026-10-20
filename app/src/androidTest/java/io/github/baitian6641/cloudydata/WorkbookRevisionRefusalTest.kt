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

/** S03.4 / F01,F64 / Q33,Q34: decoded revision admission before command authority. */
@RunWith(AndroidJUnit4::class)
class WorkbookRevisionRefusalTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var engine: DuckDbEngine

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "workbook-revision-refusal-${UUID.randomUUID()}").apply { mkdirs() }
        engine = DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1)
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) engine.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun coercibleLegacyRevisionsCannotAuthorizeCommands() = runBlocking {
        val unexpected = mutableListOf<String>()
        for (version in listOf(1, 2)) for (kind in listOf("fraction", "numeric string")) {
            // Each case has its own real import and workbook directory. A prior
            // unexpected save cannot create backups or receipts for another case.
            val fixture = importFixture("legacy-$version-$kind")
            val revision = fixture.document.revision
            val json = JSONObject(String(fixture.pristineMetadata, Charsets.UTF_8)).apply {
                put("formatVersion", version)
                remove("commandJournal")
                put("revision", if (kind == "fraction") revision + 0.75 else revision.toString())
            }
            fixture.metadata.writeText(json.toString(2) + "\n")
            val bytes = fixture.metadata.readBytes()
            val candidates = recoveryCandidates(fixture)
            val loaded = runCatching { WorkbookStore(fixture.storeRoot).load(fixture.document.id) }
            if (loaded.isFailure) {
                assertPreserved(fixture, bytes, candidates)
                assertTrue(runCatching { WorkbookStore(fixture.storeRoot).coordinators.open(fixture.document.id) }.isFailure)
                assertPreserved(fixture, bytes, candidates)
            } else {
                val admitted = loaded.getOrThrow()
                val effect = runCatching {
                    val reopened = WorkbookStore(fixture.storeRoot)
                    val coordinator = reopened.coordinators.open(fixture.document.id)
                    val outcome = coordinator.execute(WorkbookCommand.Rename(
                        "one-revision-probe", fixture.document.id, "revision-admission-probe", revision,
                        "One command after a malformed revision",
                    ))
                    val checkpoint = reopened.loadCheckpoint(fixture.document.id)
                    "commandRevision=${admitted.revision}->${outcome.acceptedRevision}, " +
                        "receipts=${checkpoint.receipts.size}, undo=${checkpoint.undo.size}, redo=${checkpoint.redo.size}"
                }.fold({ it }, { "Rename failed: $it" })
                val values = runCatching { nativeGroups(fixture, admitted) }
                    .fold({ it.toString() }, { "native query failed: $it" })
                unexpected += "legacy$version $kind raw=${json.get("revision")}, normalizedRevision=${admitted.revision}, " +
                    "$effect, nativeValues=$values, recoveryCandidatesRetained=${candidates.keys.all { it.exists() }}"
            }
        }
        assertTrue(
            "Malformed revisions must refuse load/open before coercion can authorize a command. " +
                "Unexpected acceptance: ${unexpected.joinToString("; ")}",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun directDocumentAndCheckpointDecodersRefuseInvalidAndNonfiniteRevisions() = runBlocking {
        val fixture = importFixture("direct-decoders")
        val candidates = recoveryCandidates(fixture)
        for (version in listOf(1, 2, 3)) {
            val valid = documentJson(fixture, version)
            assertEquals(fixture.document.revision, WorkbookDocument.fromJson(valid).revision)
            assertEquals(fixture.document.revision, WorkbookCheckpoint.fromJson(valid).document.revision)
            for (fault in revisionFaults(fixture.document.revision)) {
                val json = documentJson(fixture, version)
                applyFault(json, fault)
                assertTrue("v$version ${fault.label} must refuse direct document decoding", runCatching {
                    WorkbookDocument.fromJson(json)
                }.isFailure)
                assertTrue("v$version ${fault.label} must refuse checkpoint decoding", runCatching {
                    WorkbookCheckpoint.fromJson(json)
                }.isFailure)
            }
            // Native JSONObject(Map) retains nonfinite Number objects. This
            // exercises decoded values without claiming invalid JSON tokens.
            for ((label, value) in listOf(
                "NaN" to Double.NaN,
                "positive infinity" to Double.POSITIVE_INFINITY,
                "negative infinity" to Double.NEGATIVE_INFINITY,
            )) {
                val source = documentJson(fixture, version)
                val entries = linkedMapOf<String, Any>()
                source.keys().forEach { key -> entries[key] = source.get(key) }
                entries["revision"] = value
                val json = JSONObject(entries)
                val actual = json.get("revision")
                assertTrue("the native object must hold the actual $label Double", actual is Double)
                actual as Double
                if (value.isNaN()) assertTrue(actual.isNaN()) else assertEquals(value, actual, 0.0)
                assertTrue("v$version $label must refuse direct document decoding", runCatching {
                    WorkbookDocument.fromJson(json)
                }.isFailure)
                assertTrue("v$version $label must refuse checkpoint decoding", runCatching {
                    WorkbookCheckpoint.fromJson(json)
                }.isFailure)
            }
        }
        assertPreserved(fixture, fixture.pristineMetadata, candidates)
    }

    @Test
    fun malformedStoredRevisionsRefuseLoadOpenAndSaveWithoutChangingEvidence() = runBlocking {
        for (version in listOf(1, 2)) {
            val fixture = importFixture("stored-legacy-$version")
            for (fault in revisionFaults(fixture.document.revision)) {
                val json = documentJson(fixture, version)
                applyFault(json, fault)
                fixture.metadata.writeText(storedText(json, fault))
                val bytes = fixture.metadata.readBytes()
                val candidates = recoveryCandidates(fixture)
                assertMalformedStored(fixture, bytes, candidates, "legacy$version ${fault.label}")
            }

            val migration = File(fixture.metadata.parentFile, "migration").apply { mkdirs() }
            val original = File(migration, "workbook-v1-original.json").apply {
                writeText("Previously preserved original revision evidence\n")
            }
            val backups = mapOf(original to original.readBytes())
            val json = documentJson(fixture, version).apply { put("revision", fixture.document.revision.toString()) }
            fixture.metadata.writeText(json.toString(2) + "\n")
            val bytes = fixture.metadata.readBytes()
            val candidates = recoveryCandidates(fixture)
            assertMalformedStored(fixture, bytes, candidates, "legacy$version with existing original backup", backups)
        }
    }

    @Test
    fun rehashedV3HeadUndoAndRedoCannotHideInvalidRevisions() = runBlocking {
        val imported = importFixture("rehashed-history")
        val coordinator = imported.store.coordinators.open(imported.document.id)
        val first = coordinator.execute(WorkbookCommand.Rename(
            "revision-history-first", imported.document.id, "revision-history-window", imported.document.revision,
            "First revision history name",
        )).document
        val second = coordinator.execute(WorkbookCommand.Rename(
            "revision-history-second", imported.document.id, "revision-history-window", first.revision,
            "Second revision history name",
        )).document
        val head = coordinator.undo(second.revision)
        val fixture = imported.copy(document = head, pristineMetadata = imported.metadata.readBytes())
        val control = WorkbookStore(fixture.storeRoot).loadCheckpoint(head.id)
        assertEquals(1, control.undo.size)
        assertEquals(1, control.redo.size)
        assertEquals(2, control.receipts.size)
        assertEquals(listOf(listOf("East", "15"), listOf("West", "20")), nativeGroups(fixture, head))
        // v3 already has content/history guards. These cases retain that refusal
        // contract while proving a valid envelope hash cannot hide the fault.
        for (location in listOf("head", "undo", "redo")) {
            val expectedRevision = when (location) {
                "head" -> head.revision
                "undo" -> control.undo.single().revision
                else -> control.redo.single().revision
            }
            val faults = listOf(
                RevisionFault("missing", missing = true),
                RevisionFault("explicit null", JSONObject.NULL),
                RevisionFault("numeric string", expectedRevision.toString()),
                RevisionFault("fraction", expectedRevision + 0.75),
                RevisionFault("boolean", true),
                RevisionFault("zero", 0),
                RevisionFault("negative", -1L),
                RevisionFault("maximum Long", Long.MAX_VALUE),
            )
            for (fault in faults) {
                val json = JSONObject(String(fixture.pristineMetadata, Charsets.UTF_8))
                val journal = json.getJSONObject("commandJournal")
                val target = if (location == "head") json else journal.getJSONArray(location).getJSONObject(0)
                applyFault(target, fault)
                journal.put("headSha256", digest(JSONObject(json.toString()).apply { remove("commandJournal") }))
                journal.put("sha256", digest(JSONObject(journal.toString()).apply { remove("sha256") }))
                fixture.metadata.writeText(json.toString(2) + "\n")
                val bytes = fixture.metadata.readBytes()
                val candidates = recoveryCandidates(fixture)
                assertMalformedStored(fixture, bytes, candidates, "v3 $location ${fault.label}", beyondChecksum = true)
            }
        }
    }

    private data class RevisionFault(
        val label: String,
        val value: Any = JSONObject.NULL,
        val missing: Boolean = false,
        val storedLiteral: String? = null,
    )

    private fun revisionFaults(revision: Long): List<RevisionFault> = listOf(
        RevisionFault("missing", missing = true),
        RevisionFault("explicit null", JSONObject.NULL),
        RevisionFault("empty string", ""),
        RevisionFault("whitespace string", " \t "),
        RevisionFault("numeric string", revision.toString()),
        RevisionFault("fractional string", "$revision.75"),
        RevisionFault("exponent string", "${revision}e0"),
        RevisionFault("boolean true", true),
        RevisionFault("boolean false", false),
        RevisionFault("object", JSONObject().put("value", revision)),
        RevisionFault("array", JSONArray().put(revision)),
        RevisionFault("fraction", revision + 0.75),
        RevisionFault("fraction below one", 0.9999999999999999),
        RevisionFault("smallest positive Double", Double.MIN_VALUE, storedLiteral = "4.9E-324"),
        RevisionFault("negative fraction", -0.5),
        RevisionFault("zero", 0),
        RevisionFault("negative zero", -0.0),
        RevisionFault("negative integer", -1L),
        RevisionFault("minimum Long", Long.MIN_VALUE),
        RevisionFault("maximum Long", Long.MAX_VALUE),
        RevisionFault("Double at 2^63", 9.223372036854776E18, storedLiteral = "9.223372036854776E18"),
        RevisionFault("Double above 2^63", 9.223372036854778E18, storedLiteral = "9.223372036854778E18"),
        RevisionFault("largest finite Double", Double.MAX_VALUE, storedLiteral = "1.7976931348623157E308"),
    )

    private fun documentJson(fixture: Fixture, version: Int): JSONObject =
        JSONObject(String(fixture.pristineMetadata, Charsets.UTF_8)).apply {
            put("formatVersion", version)
            if (version < 3) remove("commandJournal")
        }

    private fun applyFault(json: JSONObject, fault: RevisionFault) {
        if (fault.missing) json.remove("revision") else json.put("revision", fault.value)
    }

    private fun storedText(json: JSONObject, fault: RevisionFault): String {
        val literal = fault.storedLiteral ?: return json.toString(2) + "\n"
        // Android numberToString can turn an integral Double at 2^63 into a
        // Long token. Emit the intended literal and inspect its decoded type.
        val marker = "__WORKBOOK_REVISION_LITERAL__"
        json.put("revision", marker)
        val text = json.toString(2).replace(JSONObject.quote(marker), literal) + "\n"
        val actual = JSONObject(text).get("revision")
        assertTrue("${fault.label} must be tested as a stored Double", actual is Double)
        assertEquals((fault.value as Number).toDouble(), (actual as Number).toDouble(), 0.0)
        return text
    }

    private suspend fun assertMalformedStored(
        fixture: Fixture,
        bytes: ByteArray,
        candidates: Map<File, ByteArray>,
        label: String,
        backups: Map<File, ByteArray> = emptyMap(),
        beyondChecksum: Boolean = false,
    ) {
        val error = runCatching { WorkbookStore(fixture.storeRoot).loadCheckpoint(fixture.document.id) }.exceptionOrNull()
        assertTrue("$label must refuse stored load", error != null)
        if (beyondChecksum) assertFalse("$label must fail beyond checksum validation", error!!.message.orEmpty().contains("checksum", ignoreCase = true))
        assertPreserved(fixture, bytes, candidates, backups)
        assertTrue("$label must refuse coordinator opening", runCatching {
            WorkbookStore(fixture.storeRoot).coordinators.open(fixture.document.id)
        }.isFailure)
        assertPreserved(fixture, bytes, candidates, backups)
        assertTrue("$label must refuse save before original backup or atomic publication", runCatching {
            WorkbookStore(fixture.storeRoot).save(fixture.document.copy(name = "Must not overwrite invalid revision evidence"))
        }.isFailure)
        assertPreserved(fixture, bytes, candidates, backups)
    }

    private data class Fixture(
        val storeRoot: File,
        val store: WorkbookStore,
        val document: WorkbookDocument,
        val metadata: File,
        val pristineMetadata: ByteArray,
        val snapshotFile: File,
        val snapshotBytes: ByteArray,
        val sourceFile: File,
        val sourceBytes: ByteArray,
    )

    private suspend fun importFixture(label: String): Fixture {
        val storeRoot = File(root, label)
        val store = WorkbookStore(storeRoot)
        val book = store.create("Revision admission $label")
        val csv = File(storeRoot, "revision.csv").apply {
            writeText("Category,Sales\nEast,10\nWest,20\nEast,5\n")
        }
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
        val document = store.save(imported.copy(worksheets = listOf(sheet)))
        val metadata = File(store.workbookDir(document.id), "workbook.json")
        val snapshotFile = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile)
        val sourceFile = store.resolveInsideWorkbook(document.id, checkNotNull(snapshot.originalFile))
        return Fixture(
            storeRoot, store, document, metadata, metadata.readBytes(), snapshotFile, snapshotFile.readBytes(),
            sourceFile, sourceFile.readBytes(),
        ).also {
            assertEquals(listOf(listOf("East", "15"), listOf("West", "20")), nativeGroups(it, document))
        }
    }

    private fun recoveryCandidates(fixture: Fixture): Map<File, ByteArray> = listOf(
        File(fixture.metadata.parentFile, "workbook-941.tmp").apply { writeBytes(fixture.pristineMetadata) },
        File(fixture.metadata.parentFile, "workbook-942.tmp").apply { writeText("partial unpublished revision candidate") },
    ).associateWith { it.readBytes() }

    private fun assertPreserved(
        fixture: Fixture,
        metadata: ByteArray,
        candidates: Map<File, ByteArray>,
        backups: Map<File, ByteArray> = emptyMap(),
    ) {
        assertArrayEquals("refusal keeps exact canonical metadata", metadata, fixture.metadata.readBytes())
        candidates.forEach { (file, original) -> assertArrayEquals("preserve ${file.name}", original, file.readBytes()) }
        val migration = File(fixture.metadata.parentFile, "migration")
        if (backups.isEmpty()) {
            assertFalse("revision refusal cannot create migration backups", migration.exists())
        } else {
            backups.forEach { (file, original) -> assertArrayEquals("preserve existing ${file.name}", original, file.readBytes()) }
            assertEquals(backups.keys.map { it.name }.sorted(), migration.listFiles().orEmpty().map { it.name }.sorted())
        }
        assertArrayEquals(fixture.snapshotBytes, fixture.snapshotFile.readBytes())
        assertArrayEquals(fixture.sourceBytes, fixture.sourceFile.readBytes())
    }

    private fun digest(json: JSONObject): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson(json).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun nativeGroups(fixture: Fixture, document: WorkbookDocument): List<List<String?>> {
        val snapshot = checkNotNull(document.snapshot)
        return WorksheetQuery(engine).query(
            snapshot, checkNotNull(document.activeWorksheet),
            fixture.store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath,
        ).result.rows.map { row -> row.map { it?.toString() } }
    }
}
