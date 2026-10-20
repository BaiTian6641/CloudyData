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
import io.github.baitian6641.cloudydata.ui.HomeLibraryController
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
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

/** S03.4 / F01,F64 / Q33,Q34: persisted favorite values cannot silently become Boolean state. */
@RunWith(AndroidJUnit4::class)
class WorkbookFavoriteRefusalTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var engine: DuckDbEngine

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "workbook-favorite-refusal-${UUID.randomUUID()}").apply { mkdirs() }
        engine = DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1)
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) engine.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun malformedLegacyFavoritesCannotBecomeLibraryOrSavedBooleanState() = runBlocking {
        val unexpected = mutableListOf<String>()
        val malformed = listOf(
            "string true" to "true",
            "string false" to "false",
            "unknown string" to "not-a-boolean",
            "number" to 1,
            "explicit null" to JSONObject.NULL,
        )
        for (version in listOf(1, 2)) for ((label, value) in malformed) {
            // Isolated imports keep an unexpected migration in one case from
            // altering the backup, history or library projection of another.
            val fixture = importFixture("legacy-$version-$label")
            val json = JSONObject(String(fixture.pristineMetadata, Charsets.UTF_8)).apply {
                put("formatVersion", version)
                remove("commandJournal")
                put("favorite", value)
            }
            fixture.metadata.writeText(json.toString(2) + "\n")
            val bytes = fixture.metadata.readBytes()
            val candidates = recoveryCandidates(fixture)
            val loaded = runCatching { WorkbookStore(fixture.storeRoot).load(fixture.document.id) }
            if (loaded.isFailure) {
                assertPreserved(fixture, bytes, candidates)
                val reopened = WorkbookStore(fixture.storeRoot)
                assertTrue(runCatching { reopened.coordinators.open(fixture.document.id) }.isFailure)
                assertTrue("malformed favorite must not become a library row", reopened.list().none { it.id == fixture.document.id })
                assertPreserved(fixture, bytes, candidates)
            } else {
                val admitted = loaded.getOrThrow()
                val projection = runCatching {
                    val home = HomeLibraryController(WorkbookStore(fixture.storeRoot), "favorite-admission-home")
                    home.reload()
                    val summary = home.state.value.workbooks.singleOrNull { it.id == fixture.document.id }
                    "libraryFavorite=${summary?.favorite}, libraryRevision=${summary?.revision}"
                }.fold({ it }, { "library reload failed: $it" })
                val effect = runCatching {
                    val reopened = WorkbookStore(fixture.storeRoot)
                    val coordinator = reopened.coordinators.open(fixture.document.id)
                    val outcome = coordinator.execute(WorkbookCommand.Rename(
                        "one-favorite-probe", fixture.document.id, "favorite-admission-window", admitted.revision,
                        "One rename after a malformed favorite",
                    ))
                    val saved = reopened.loadCheckpoint(fixture.document.id)
                    val persisted = JSONObject(fixture.metadata.readText()).get("favorite")
                    "savedFavorite=${saved.document.favorite}, persistedValue=$persisted, " +
                        "persistedType=${persisted.javaClass.simpleName}, acceptedRevision=${outcome.acceptedRevision}, " +
                        "receipts=${saved.receipts.size}, undo=${saved.undo.size}"
                }.fold({ it }, { "Rename failed: $it" })
                val values = runCatching { nativeGroups(fixture, admitted) }
                    .fold({ it.toString() }, { "native query failed: $it" })
                unexpected += "legacy$version $label raw=${json.get("favorite")}, decodedFavorite=${admitted.favorite}, " +
                    "$projection, $effect, nativeValues=$values, recoveryCandidatesRetained=${candidates.keys.all { it.exists() }}"
            }
        }
        assertTrue(
            "A present malformed favorite must refuse before library projection or a rename can persist a substituted Boolean. " +
                "Unexpected acceptance: ${unexpected.joinToString("; ")}",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun directDecodersRequireActualBooleansWhileAbsentFavoriteDefaultsToFalse() = runBlocking {
        val fixture = importFixture("direct-decoders")
        val candidates = recoveryCandidates(fixture)
        for (version in listOf(1, 2, 3)) {
            for (favorite in listOf(false, true)) {
                val json = documentJson(fixture, version).apply { put("favorite", favorite) }
                if (version == 3) rehash(json)
                assertEquals("v$version actual Boolean is preserved", favorite, WorkbookDocument.fromJson(json).favorite)
                assertEquals("v$version valid checkpoint preserves actual Boolean", favorite, WorkbookCheckpoint.fromJson(json).document.favorite)
            }
            val missing = documentJson(fixture, version).apply { remove("favorite") }
            assertFalse("absent favorite retains the document default", WorkbookDocument.fromJson(missing).favorite)
            if (version < 3) {
                assertFalse("legacy absent favorite remains readable", WorkbookCheckpoint.fromJson(missing).document.favorite)
            } else {
                rehash(missing)
                assertTrue("v3 retains its existing raw-content guard for missing favorite", runCatching {
                    WorkbookCheckpoint.fromJson(missing)
                }.isFailure)
            }
            for (fault in favoriteFaults()) {
                val json = documentJson(fixture, version).apply { put("favorite", fault.value) }
                assertTrue("v$version ${fault.label} cannot decode as a document", runCatching {
                    WorkbookDocument.fromJson(json)
                }.isFailure)
                assertTrue("v$version ${fault.label} cannot decode as a checkpoint", runCatching {
                    WorkbookCheckpoint.fromJson(json)
                }.isFailure)
            }
            // JSONObject(Map) holds these actual Number values on Android.
            // This does not claim that nonfinite tokens are valid JSON text.
            for ((label, value) in listOf(
                "NaN" to Double.NaN,
                "positive infinity" to Double.POSITIVE_INFINITY,
                "negative infinity" to Double.NEGATIVE_INFINITY,
            )) {
                val source = documentJson(fixture, version)
                val entries = linkedMapOf<String, Any>()
                source.keys().forEach { key -> entries[key] = source.get(key) }
                entries["favorite"] = value
                val json = JSONObject(entries)
                val actual = json.get("favorite")
                assertTrue("$label remains a native Double in the fixture", actual is Double)
                actual as Double
                if (value.isNaN()) assertTrue(actual.isNaN()) else assertEquals(value, actual, 0.0)
                assertTrue("v$version $label cannot become favorite=false", runCatching {
                    WorkbookDocument.fromJson(json)
                }.isFailure)
                assertTrue("v$version $label cannot decode as a checkpoint", runCatching {
                    WorkbookCheckpoint.fromJson(json)
                }.isFailure)
            }
        }
        assertPreserved(fixture, fixture.pristineMetadata, candidates)
    }

    @Test
    fun malformedStoredFavoritesRefuseWithoutAtomicWriteOrEvidenceChanges() = runBlocking {
        for (version in listOf(1, 2)) {
            val fixture = importFixture("stored-legacy-$version")
            for (fault in favoriteFaults()) {
                val json = documentJson(fixture, version).apply { put("favorite", fault.value) }
                fixture.metadata.writeText(json.toString(2) + "\n")
                val bytes = fixture.metadata.readBytes()
                val candidates = recoveryCandidates(fixture)
                assertMalformedStored(fixture, bytes, candidates, "legacy$version ${fault.label}")
            }

            val migration = File(fixture.metadata.parentFile, "migration").apply { mkdirs() }
            val original = File(migration, "workbook-v1-original.json").apply {
                writeText("Previously retained original favorite evidence\n")
            }
            val backups = mapOf(original to original.readBytes())
            val json = documentJson(fixture, version).apply { put("favorite", "true") }
            fixture.metadata.writeText(json.toString(2) + "\n")
            val bytes = fixture.metadata.readBytes()
            val candidates = recoveryCandidates(fixture)
            assertMalformedStored(fixture, bytes, candidates, "legacy$version with existing original backup", backups)
        }
    }

    @Test
    fun rehashedV3HeadUndoAndRedoCannotHideMalformedOrMissingFavorites() = runBlocking {
        val imported = importFixture("rehashed-history")
        val coordinator = imported.store.coordinators.open(imported.document.id)
        val favorite = coordinator.execute(WorkbookCommand.SetFavorite(
            "favorite-history-desired-true", imported.document.id, "favorite-history-window", imported.document.revision, true,
        )).document
        val renamed = coordinator.execute(WorkbookCommand.Rename(
            "favorite-history-rename", imported.document.id, "favorite-history-window", favorite.revision,
            "Favorite history renamed",
        )).document
        val head = coordinator.undo(renamed.revision)
        val fixture = imported.copy(document = head, pristineMetadata = imported.metadata.readBytes())
        val control = WorkbookStore(fixture.storeRoot).loadCheckpoint(head.id)
        assertEquals(1, control.undo.size)
        assertEquals(1, control.redo.size)
        assertEquals(2, control.receipts.size)
        assertTrue(control.document.favorite)
        assertFalse(control.undo.single().favorite)
        assertTrue(control.redo.single().favorite)
        assertEquals(listOf(listOf("East", "15"), listOf("West", "20")), nativeGroups(fixture, head))
        // v3 already refuses normalization through raw-content guards. Rehash
        // each mutation so these cases exercise that admission beyond checksums.
        for (location in listOf("head", "undo", "redo")) {
            for (fault in favoriteFaults() + FavoriteFault("missing", missing = true)) {
                val json = JSONObject(String(fixture.pristineMetadata, Charsets.UTF_8))
                val target = if (location == "head") json else json.getJSONObject("commandJournal").getJSONArray(location).getJSONObject(0)
                if (fault.missing) target.remove("favorite") else target.put("favorite", fault.value)
                rehash(json)
                fixture.metadata.writeText(json.toString(2) + "\n")
                val bytes = fixture.metadata.readBytes()
                val candidates = recoveryCandidates(fixture)
                assertMalformedStored(fixture, bytes, candidates, "v3 $location ${fault.label}", beyondChecksum = true)
            }
        }
    }

    private data class FavoriteFault(val label: String, val value: Any = JSONObject.NULL, val missing: Boolean = false)

    private fun favoriteFaults(): List<FavoriteFault> = listOf(
        FavoriteFault("explicit null", JSONObject.NULL),
        FavoriteFault("empty string", ""),
        FavoriteFault("whitespace string", " \t "),
        FavoriteFault("string true", "true"),
        FavoriteFault("string false", "false"),
        FavoriteFault("uppercase string true", "TRUE"),
        FavoriteFault("uppercase string false", "FALSE"),
        FavoriteFault("mixed-case string true", "True"),
        FavoriteFault("mixed-case string false", "False"),
        FavoriteFault("padded string true", " true "),
        FavoriteFault("padded string false", " false "),
        FavoriteFault("unknown string", "not-a-boolean"),
        FavoriteFault("string zero", "0"),
        FavoriteFault("string one", "1"),
        FavoriteFault("integer zero", 0),
        FavoriteFault("integer one", 1),
        FavoriteFault("negative integer", -1),
        FavoriteFault("fraction", 1.5),
        FavoriteFault("Double zero", 0.0),
        FavoriteFault("maximum Long", Long.MAX_VALUE),
        FavoriteFault("object", JSONObject().put("value", true)),
        FavoriteFault("array", JSONArray().put(true)),
        FavoriteFault("empty array", JSONArray()),
    )

    private fun documentJson(fixture: Fixture, version: Int): JSONObject =
        JSONObject(String(fixture.pristineMetadata, Charsets.UTF_8)).apply {
            put("formatVersion", version)
            if (version < 3) remove("commandJournal")
        }

    private fun rehash(json: JSONObject) {
        val journal = json.getJSONObject("commandJournal")
        journal.put("headSha256", digest(JSONObject(json.toString()).apply { remove("commandJournal") }))
        journal.put("sha256", digest(JSONObject(journal.toString()).apply { remove("sha256") }))
    }

    private suspend fun assertMalformedStored(
        fixture: Fixture,
        bytes: ByteArray,
        candidates: Map<File, ByteArray>,
        label: String,
        backups: Map<File, ByteArray> = emptyMap(),
        beyondChecksum: Boolean = false,
    ) {
        val phases = AtomicInteger()
        val observed = WorkbookStore(fixture.storeRoot) { phases.incrementAndGet() }
        val error = runCatching { observed.loadCheckpoint(fixture.document.id) }.exceptionOrNull()
        assertTrue("$label must refuse stored load", error != null)
        if (beyondChecksum) assertFalse("$label must fail beyond checksum validation", error!!.message.orEmpty().contains("checksum", ignoreCase = true))
        assertPreserved(fixture, bytes, candidates, backups)
        assertTrue("$label must refuse coordinator opening", runCatching { observed.coordinators.open(fixture.document.id) }.isFailure)
        assertPreserved(fixture, bytes, candidates, backups)
        assertTrue("$label must be omitted from the existing library projection", observed.list().none { it.id == fixture.document.id })
        assertPreserved(fixture, bytes, candidates, backups)
        assertTrue("$label must refuse save before backup or atomic publication", runCatching {
            observed.save(fixture.document.copy(name = "Must not overwrite malformed favorite evidence"))
        }.isFailure)
        assertEquals("$label must not enter any atomic metadata write phase", 0, phases.get())
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
        val book = store.create("Favorite admission $label")
        val csv = File(storeRoot, "favorite.csv").apply {
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
        File(fixture.metadata.parentFile, "workbook-951.tmp").apply { writeBytes(fixture.pristineMetadata) },
        File(fixture.metadata.parentFile, "workbook-952.tmp").apply { writeText("partial unpublished favorite candidate") },
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
            assertFalse("favorite refusal cannot create migration backups", migration.exists())
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
