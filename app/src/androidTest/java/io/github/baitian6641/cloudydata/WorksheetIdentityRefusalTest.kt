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
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.canonicalJson
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.4 / F01,F64 / Q33,Q34: native ambiguous worksheet identity refusal. */
@RunWith(AndroidJUnit4::class)
class WorksheetIdentityRefusalTest {
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
        root = File(context.filesDir, "worksheet-identity-refusal-${UUID.randomUUID()}")
        store = WorkbookStore(root)
        val book = store.create("Worksheet identity admission")
        val csv = File(root, "identities.csv").apply { writeText("Category,Sales\nEast,10\nWest,20\nEast,5\n") }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(book, Uri.fromFile(csv))
        val imported = importer.apply(book, pending)
        val snapshot = checkNotNull(imported.snapshot)
        val sheet = imported.worksheets.single().copy(
            name = "First worksheet",
            rowFieldId = snapshot.columns.single { it.name == "Category" }.id,
            measureFieldId = snapshot.columns.single { it.name == "Sales" }.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sortDescending = false,
            chart = ChartOptions(title = "First original chart"),
        )
        document = store.save(imported.copy(worksheets = listOf(sheet)))
        pristineMetadata = metadata().readBytes()
        snapshotBytes = snapshotFile().readBytes()
        sourceBytes = sourceFile().readBytes()
        engine = DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1)
        assertEquals(listOf(listOf("East", "15"), listOf("West", "20")), nativeGroups(document))
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) engine.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun duplicateWorksheetIdsCannotMakeOneChartCommandEditTwoSheets() = runBlocking {
        val json = JSONObject(String(pristineMetadata, Charsets.UTF_8)).apply {
            put("formatVersion", 2)
            remove("commandJournal")
        }
        appendDuplicate(json)
        metadata().writeText(json.toString(2) + "\n")
        val bytes = metadata().readBytes()
        val candidates = recoveryCandidates()
        val newStore = WorkbookStore(root)
        val loaded = runCatching { newStore.load(document.id) }
        if (loaded.isFailure) {
            assertPreserved(bytes, candidates)
            assertTrue(runCatching { newStore.coordinators.open(document.id) }.isFailure)
            assertPreserved(bytes, candidates)
        } else {
            val accepted = loaded.getOrThrow()
            val commandEffect = runCatching {
                val coordinator = newStore.coordinators.open(document.id)
                val outcome = coordinator.execute(WorkbookCommand.SetChart(
                    "one-chart-request", document.id, "duplicate-id-probe", accepted.revision,
                    accepted.worksheets.first().id, ChartOptions(title = "One requested chart edit"),
                ))
                val titles = outcome.document.worksheets.map { it.chart.title }
                val changed = titles.count { it == "One requested chart edit" }
                val receipts = newStore.loadCheckpoint(document.id).receipts.size
                "changedTitles=$changed, titles=$titles, receipts=$receipts, acceptedRevision=${outcome.acceptedRevision}"
            }.fold({ it }, { "SetChart failed: $it" })
            val values = runCatching { nativeGroups(accepted) }.fold({ it.toString() }, { "native query failed: $it" })
            assertTrue(
                "Duplicate worksheet IDs must refuse load/open before a single targeted command can edit two definitions. " +
                    "Admitted names=${accepted.worksheets.map { it.name }}, IDs=${accepted.worksheets.map { it.id }}, " +
                    "$commandEffect, nativeValues=$values, recoveryCandidatesRetained=${candidates.keys.all { it.exists() }}",
                false,
            )
        }
    }

    @Test
    fun duplicateLegacyIdentitiesRefuseWithoutChangingCandidatesOrExistingOriginalBackups() = runBlocking {
        for (version in listOf(1, 2)) for (nonAdjacent in listOf(false, true)) {
            val json = JSONObject(String(pristineMetadata, Charsets.UTF_8)).apply {
                put("formatVersion", version)
                remove("commandJournal")
            }
            if (nonAdjacent) {
                val sheets = json.getJSONArray("worksheets")
                sheets.put(JSONObject(sheets.getJSONObject(0).toString()).apply {
                    put("id", "distinct-middle-sheet")
                    put("name", "Distinct middle worksheet")
                })
            }
            appendDuplicate(json)
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            assertMalformedStored(bytes, candidates, "legacy$version nonadjacent=$nonAdjacent")
        }

        val migration = File(store.workbookDir(document.id), "migration").apply { mkdirs() }
        val existingBackup = File(migration, "workbook-v1-original.json").apply {
            writeText("Previously retained original package evidence\n")
        }
        val originalBackupBytes = existingBackup.readBytes()
        val json = JSONObject(String(pristineMetadata, Charsets.UTF_8)).apply {
            put("formatVersion", 2)
            remove("commandJournal")
        }
        appendDuplicate(json)
        metadata().writeText(json.toString(2) + "\n")
        val bytes = metadata().readBytes()
        val candidates = recoveryCandidates()
        fun assertExistingBackupAndSourcePreserved() {
            assertArrayEquals(bytes, metadata().readBytes())
            candidates.forEach { (file, original) -> assertArrayEquals(original, file.readBytes()) }
            assertArrayEquals("an existing original backup must remain exact", originalBackupBytes, existingBackup.readBytes())
            assertEquals("refusal must not create another migration backup", listOf(existingBackup.name), migration.listFiles().orEmpty().map { it.name }.sorted())
            assertArrayEquals(snapshotBytes, snapshotFile().readBytes())
            assertArrayEquals(sourceBytes, sourceFile().readBytes())
        }
        assertTrue(runCatching { WorkbookStore(root).loadCheckpoint(document.id) }.isFailure)
        assertExistingBackupAndSourcePreserved()
        assertTrue(runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
        assertExistingBackupAndSourcePreserved()
        assertTrue(runCatching { WorkbookStore(root).save(document.copy(name = "Must not rewrite ambiguous legacy metadata")) }.isFailure)
        assertExistingBackupAndSourcePreserved()
    }

    @Test
    fun duplicateV3HeadUndoAndRedoIdentitiesRefuseBeyondRecomputedValidHashes() = runBlocking {
        prepareUndoAndRedoControl()
        for (location in listOf("head", "undo", "redo")) {
            val json = JSONObject(String(pristineMetadata, Charsets.UTF_8))
            val journal = json.getJSONObject("commandJournal")
            val target = if (location == "head") json else journal.getJSONArray(location).getJSONObject(0)
            appendDuplicate(target)
            journal.put("headSha256", digest(JSONObject(json.toString()).apply { remove("commandJournal") }))
            journal.put("sha256", digest(JSONObject(journal.toString()).apply { remove("sha256") }))
            metadata().writeText(json.toString(2) + "\n")
            val bytes = metadata().readBytes()
            val candidates = recoveryCandidates()
            assertMalformedStored(bytes, candidates, "v3 $location", beyondChecksum = true)
        }
    }

    @Test
    fun directDecoderAndConstructedCheckpointCannotSerializeOrPublishDuplicateIdentities() = runBlocking {
        val control = prepareUndoAndRedoControl()
        val canonical = metadata().readBytes()
        for (version in listOf(1, 2, 3)) {
            val json = JSONObject(String(pristineMetadata, Charsets.UTF_8)).apply { put("formatVersion", version) }
            if (version < 3) json.remove("commandJournal")
            appendDuplicate(json)
            assertTrue("direct document v$version must reject ambiguous IDs", runCatching { WorkbookDocument.fromJson(json) }.isFailure)
            assertArrayEquals(canonical, metadata().readBytes())
        }
        val phases = AtomicInteger()
        val writer = WorkbookStore(root) { phases.incrementAndGet() }
        fun duplicate(book: WorkbookDocument): WorkbookDocument {
            val first = book.worksheets.first()
            return book.copy(worksheets = book.worksheets + first.copy(name = "Constructed duplicate worksheet"))
        }
        fun malformed(location: String): WorkbookCheckpoint = when (location) {
            "head" -> control.copy(document = duplicate(control.document))
            "undo" -> control.copy(undo = listOf(duplicate(control.undo.single())))
            else -> control.copy(redo = listOf(duplicate(control.redo.single())))
        }
        for (location in listOf("head", "undo", "redo")) {
            assertTrue("constructed $location must refuse checkpoint serialization", runCatching { malformed(location).toJson() }.isFailure)
            assertTrue("constructed $location must refuse Store publication", runCatching { writer.saveCheckpoint(malformed(location)) }.isFailure)
            assertEquals("a rejected candidate never reaches atomic write", 0, phases.get())
            assertArrayEquals(canonical, metadata().readBytes())
            assertEquals(control, WorkbookStore(root).loadCheckpoint(document.id))
            assertFalse(File(store.workbookDir(document.id), "migration").exists())
            assertArrayEquals(snapshotBytes, snapshotFile().readBytes())
            assertArrayEquals(sourceBytes, sourceFile().readBytes())
        }
        assertTrue("raw Store save must not bypass duplicate admission", runCatching { writer.save(duplicate(control.document)) }.isFailure)
        assertEquals(0, phases.get())
        assertArrayEquals(canonical, metadata().readBytes())
        assertEquals(control, WorkbookStore(root).loadCheckpoint(document.id))
        assertFalse(File(store.workbookDir(document.id), "migration").exists())
        assertArrayEquals(snapshotBytes, snapshotFile().readBytes())
        assertArrayEquals(sourceBytes, sourceFile().readBytes())
        // No temp-file retention assertion belongs here: loading the existing
        // valid canonical checkpoint may legitimately clean abandoned temps.
    }

    private suspend fun prepareUndoAndRedoControl(): WorkbookCheckpoint {
        val coordinator = store.coordinators.open(document.id)
        val first = coordinator.execute(WorkbookCommand.Rename(
            "identity-history-first", document.id, "identity-test-window", document.revision, "First history name",
        )).document
        val second = coordinator.execute(WorkbookCommand.Rename(
            "identity-history-second", document.id, "identity-test-window", first.revision, "Second history name",
        )).document
        document = coordinator.undo(second.revision)
        pristineMetadata = metadata().readBytes()
        val control = WorkbookStore(root).loadCheckpoint(document.id)
        assertEquals(1, control.undo.size)
        assertEquals(1, control.redo.size)
        assertEquals(2, control.receipts.size)
        assertEquals(document.worksheets.single().id, control.undo.single().worksheets.single().id)
        assertEquals(document.worksheets.single().id, control.redo.single().worksheets.single().id)
        assertEquals(listOf(listOf("East", "15"), listOf("West", "20")), nativeGroups(document))
        return control
    }

    private suspend fun assertMalformedStored(bytes: ByteArray, candidates: Map<File, ByteArray>, label: String, beyondChecksum: Boolean = false) {
        val error = runCatching { WorkbookStore(root).loadCheckpoint(document.id) }.exceptionOrNull()
        assertTrue("$label must refuse stored load", error != null)
        if (beyondChecksum) assertFalse("$label must fail content admission with valid hashes", error!!.message.orEmpty().contains("checksum", ignoreCase = true))
        assertPreserved(bytes, candidates)
        assertTrue("$label must refuse coordinator opening", runCatching { WorkbookStore(root).coordinators.open(document.id) }.isFailure)
        assertPreserved(bytes, candidates)
        assertTrue("$label must refuse save", runCatching { WorkbookStore(root).save(document.copy(name = "Must not rewrite ambiguous worksheet identities")) }.isFailure)
        assertPreserved(bytes, candidates)
    }

    private fun appendDuplicate(json: JSONObject) {
        val sheets = json.getJSONArray("worksheets")
        val second = JSONObject(sheets.getJSONObject(0).toString()).apply {
            put("name", "Second worksheet")
            put("chart", ChartOptions(title = "Second original chart").toJson())
        }
        sheets.put(second)
    }

    private fun recoveryCandidates(): Map<File, ByteArray> = listOf(
        File(store.workbookDir(document.id), "workbook-931.tmp").apply { writeBytes(pristineMetadata) },
        File(store.workbookDir(document.id), "workbook-932.tmp").apply { writeText("partial unpublished worksheet candidate") },
    ).associateWith { it.readBytes() }

    private fun assertPreserved(bytes: ByteArray, candidates: Map<File, ByteArray>) {
        assertArrayEquals("identity refusal keeps exact canonical metadata", bytes, metadata().readBytes())
        candidates.forEach { (file, original) -> assertArrayEquals("preserve ${file.name}", original, file.readBytes()) }
        assertFalse("identity refusal cannot create migration backups", File(store.workbookDir(document.id), "migration").exists())
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
