package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookCheckpoint
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.File
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

/** S03.4 / F01,F64 / Q33,Q34: exact revision compatibility with native snapshots. */
@RunWith(AndroidJUnit4::class)
class WorkbookRevisionMigrationTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "workbook-revision-migration-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun integralLegacyRevisionTokensAdvanceExactlyAndPreserveOriginalMigrationBytes() = runBlocking {
        for (version in listOf(1, 2)) {
            for (token in listOf("7", "7.0", "7e0")) exerciseTwoAcceptedCommands(fixture(version, 7L, token))
        }
    }

    @Test
    fun exactLongAboveDoublePrecisionSurvivesLegacyCurrentCommandsAndHistory() = runBlocking {
        val exactRevision = 9_007_199_254_740_993L
        for (version in listOf(1, 2, 3)) {
            val fixture = fixture(version, exactRevision, exactRevision.toString())
            assertTrue(JSONObject(metadata(fixture.id).readText()).get("revision") is Long)
            exerciseTwoAcceptedCommands(fixture)
        }
        val upperDouble = Math.nextDown(Long.MAX_VALUE.toDouble())
        val exactUpper = 9_223_372_036_854_774_784L // Independently: 2^63 - 1024.
        for (version in listOf(1, 2, 3)) {
            val upper = fixture(version, exactUpper, upperDouble.toString())
            assertTrue(JSONObject(metadata(upper.id).readText()).get("revision") is Double)
            val direct = upper.document.toJson().put("formatVersion", version).put("revision", upperDouble)
            assertEquals(exactUpper, WorkbookDocument.fromJson(direct).revision)
            exerciseTwoAcceptedCommands(upper)
            val raw = if (version == 3) WorkbookCheckpoint(upper.document).toJson()
                else upper.document.toJson().put("formatVersion", version)
            raw.put("revision", Long.MAX_VALUE.toDouble()) // Exactly 2^63, not a valid Long revision.
            assertTrue(raw.get("revision") is Double)
            val failure = runCatching { WorkbookCheckpoint.fromJson(raw) }.exceptionOrNull()
            assertTrue("The direct 2^63 boundary cannot be an accepted checkpoint: $failure", failure is IllegalArgumentException)
        }
    }

    @Test
    fun currentNumericEquivalenceAndInMemoryIntegralKindsRetainTheirExistingMeaning() = runBlocking {
        for (token in listOf("7", "7.0", "7E+0")) {
            val fixture = fixture(3, 7L, token)
            val loaded = WorkbookStore(root).loadCheckpoint(fixture.id)
            assertEquals(fixture.document, loaded.document)
            assertArrayEquals(fixture.rawBytes, metadata(fixture.id).readBytes())
            for (numeric in listOf<Number>(7.toByte(), 7.toShort(), 7, 7L, 7.0f, 7.0)) {
                val raw = loaded.toJson().put("revision", numeric)
                assertEquals(numeric.javaClass, raw.get("revision").javaClass)
                assertEquals(7L, WorkbookDocument.fromJson(raw).revision)
                assertEquals(loaded, WorkbookCheckpoint.fromJson(raw))
            }
            assertArrayEquals(fixture.rawBytes, metadata(fixture.id).readBytes())
            exerciseTwoAcceptedCommands(fixture)
        }
    }

    @Test
    fun supportedBoundsLoadAndOnlyAnAvailableRevisionCanReachTheTerminalCheckpoint() = runBlocking {
        for (version in listOf(1, 2, 3)) {
            for (revision in listOf(1L, Long.MAX_VALUE - 1)) {
                val fixture = fixture(version, revision, revision.toString())
                val phases = AtomicInteger()
                val observed = WorkbookStore(root) { phases.incrementAndGet() }
                val coordinator = observed.coordinators.open(fixture.id)
                assertEquals(revision, coordinator.state.value.document.revision)
                assertEquals(fixture.document, observed.load(fixture.id))
                assertArrayEquals(fixture.rawBytes, metadata(fixture.id).readBytes())
                assertNativeValues(fixture.document)
                if (revision == Long.MAX_VALUE - 1) {
                    val before = observed.loadCheckpoint(fixture.id)
                    val request = rename(fixture.document, "Must not exhaust accepted revision")
                    val failure = runCatching { coordinator.execute(request) }.exceptionOrNull()
                    assertTrue("Exhausted revision must refuse before publication: $failure", failure is IllegalArgumentException)
                    assertEquals(0, phases.get())
                    assertEquals(before, WorkbookStore(root).loadCheckpoint(fixture.id))
                    assertEquals(fixture.document, coordinator.state.value.document)
                    assertArrayEquals(fixture.rawBytes, metadata(fixture.id).readBytes())
                    assertFalse(File(store.workbookDir(fixture.id), "migration").exists())
                }
                assertSourceUnchanged(fixture, observed.load(fixture.id))
            }
            val near = fixture(version, Long.MAX_VALUE - 2, (Long.MAX_VALUE - 2).toString())
            val phases = AtomicInteger()
            val observed = WorkbookStore(root) { phases.incrementAndGet() }
            val coordinator = observed.coordinators.open(near.id)
            val request = rename(near.document, "Accepted final available revision")
            val outcome = coordinator.execute(request)
            assertFalse(outcome.duplicate)
            assertEquals(Long.MAX_VALUE - 1, outcome.acceptedRevision)
            assertEquals(Long.MAX_VALUE - 1, outcome.document.revision)
            assertEquals(2, phases.get())
            val reopened = WorkbookStore(root).loadCheckpoint(near.id)
            assertEquals(listOf(near.document), reopened.undo)
            assertEquals(listOf(request), reopened.receipts.map { it.command })
            assertEquals(listOf(Long.MAX_VALUE - 1), reopened.receipts.map { it.acceptedRevision })
            assertEquals(Long.MAX_VALUE - 2, reopened.receipts.single().command.expectedRevision)
            val terminalBytes = metadata(near.id).readBytes()
            val exhausted = runCatching { coordinator.execute(rename(outcome.document, "Refused after final available save")) }.exceptionOrNull()
            assertTrue(exhausted is IllegalArgumentException)
            assertEquals("Refusal cannot begin another atomic write", 2, phases.get())
            assertArrayEquals(terminalBytes, metadata(near.id).readBytes())
            assertEquals(reopened, WorkbookStore(root).loadCheckpoint(near.id))
            val duplicate = coordinator.execute(request)
            assertTrue(duplicate.duplicate)
            assertEquals(Long.MAX_VALUE - 1, duplicate.acceptedRevision)
            assertEquals(outcome.document, duplicate.document)
            assertEquals(2, phases.get())
            assertArrayEquals(terminalBytes, metadata(near.id).readBytes())
            assertBackup(near)
            assertNativeValues(reopened.document)
            assertSourceUnchanged(near, reopened.document)
        }
    }

    private suspend fun exerciseTwoAcceptedCommands(fixture: Fixture) {
        val fresh = WorkbookStore(root)
        val initial = fresh.loadCheckpoint(fixture.id)
        assertEquals(fixture.document, initial.document)
        assertEquals(fixture.revision, initial.document.revision)
        assertTrue(initial.undo.isEmpty())
        assertTrue(initial.redo.isEmpty())
        assertTrue(initial.receipts.isEmpty())
        assertArrayEquals(fixture.rawBytes, metadata(fixture.id).readBytes())
        assertFalse(File(store.workbookDir(fixture.id), "migration").exists())
        assertNativeValues(initial.document)
        val coordinator = fresh.coordinators.open(fixture.id)
        val firstRequest = rename(initial.document, "First exact revision save")
        val first = coordinator.execute(firstRequest)
        assertFalse(first.duplicate)
        assertEquals(Math.addExact(fixture.revision, 1L), first.acceptedRevision)
        assertEquals(first.acceptedRevision, first.document.revision)
        val afterFirstStore = WorkbookStore(root)
        val afterFirst = afterFirstStore.loadCheckpoint(fixture.id)
        assertEquals(listOf(initial.document), afterFirst.undo)
        assertTrue(afterFirst.redo.isEmpty())
        assertEquals(listOf(firstRequest), afterFirst.receipts.map { it.command })
        assertEquals(listOf(first.acceptedRevision), afterFirst.receipts.map { it.acceptedRevision })
        assertEquals(fixture.revision, afterFirst.receipts.single().command.expectedRevision)
        assertBackup(fixture)
        val secondCoordinator = afterFirstStore.coordinators.open(fixture.id)
        val secondRequest = rename(first.document, "Second exact revision save")
        val second = secondCoordinator.execute(secondRequest)
        assertEquals(Math.addExact(fixture.revision, 2L), second.acceptedRevision)
        val afterSecondStore = WorkbookStore(root)
        val afterSecond = afterSecondStore.loadCheckpoint(fixture.id)
        assertEquals(second.document, afterSecond.document)
        assertEquals(listOf(initial.document, first.document), afterSecond.undo)
        assertTrue(afterSecond.redo.isEmpty())
        assertEquals(listOf(firstRequest, secondRequest), afterSecond.receipts.map { it.command })
        assertEquals(listOf(first.acceptedRevision, second.acceptedRevision), afterSecond.receipts.map { it.acceptedRevision })
        assertEquals(listOf(fixture.revision, Math.addExact(fixture.revision, 1L)),
            afterSecond.receipts.map { it.command.expectedRevision })
        val beforeDuplicate = metadata(fixture.id).readBytes()
        val duplicate = afterSecondStore.coordinators.open(fixture.id).execute(firstRequest)
        assertTrue(duplicate.duplicate)
        assertEquals(first.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(second.document, duplicate.document)
        assertArrayEquals(beforeDuplicate, metadata(fixture.id).readBytes())
        assertEquals(afterSecond, WorkbookStore(root).loadCheckpoint(fixture.id))
        assertBackup(fixture)
        assertNativeValues(second.document)
        assertSourceUnchanged(fixture, second.document)
    }

    private suspend fun fixture(version: Int, revision: Long, literal: String): Fixture {
        val workbook = store.create("Revision compatibility ${UUID.randomUUID()}")
        val input = File(root, "revision-input-${workbook.id}.csv").apply { writeText(SOURCE_CSV) }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        val document = imported.copy(revision = revision, worksheets = imported.worksheets.map {
            it.copy(rowFieldId = category.id, measureFieldId = sales.id, aggregation = Aggregation.SUM,
                markType = MarkType.TABLE, sortDescending = true,
                filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East"))))
        })
        val raw = if (version == 3) WorkbookCheckpoint(document).toJson() else document.toJson()
            .put("formatVersion", version).put("unknownOriginalEvidence", "Keep the exact revision token")
        val marker = "__literal_revision_number__"
        raw.put("revision", marker)
        val formatted = raw.toString(2)
        val quoted = JSONObject.quote(marker)
        assertEquals(1, Regex(Regex.escape(quoted)).findAll(formatted).count())
        val text = "\n" + formatted.replace(quoted, literal) + "\n"
        assertTrue(text.contains("\"revision\": $literal"))
        val bytes = text.toByteArray(Charsets.UTF_8)
        metadata(document.id).writeBytes(bytes)
        return Fixture(document.id, version, revision, document, bytes,
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes(),
            store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
    }

    private fun assertNativeValues(document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        val sheet = document.worksheets.single()
        val path = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val query = WorksheetQuery(engine)
            val all = query.query(snapshot, sheet.copy(filter = null), path)
            assertEquals(listOf(listOf("West", "20"), listOf("East", "15")),
                all.result.rows.map { row -> row.map { it?.toString() } })
            val filtered = query.query(snapshot, sheet, path)
            assertEquals(listOf(listOf("East", "15")), filtered.result.rows.map { row -> row.map { it?.toString() } })
            val rows = UnderlyingRowsQuery(engine).query(snapshot, sheet, filtered, setOf(0), path)!!
            assertEquals(listOf(listOf("East", "10"), listOf("East", "5")),
                rows.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it[1] })
            assertTrue(rows.worksheetFilterApplied)
            assertFalse(rows.truncated)
        }
    }

    private fun assertSourceUnchanged(fixture: Fixture, document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        assertArrayEquals(fixture.original, store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertArrayEquals(fixture.parquet, store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
        assertTrue(File(store.workbookDir(document.id), "staging").listFiles().orEmpty().isEmpty())
    }

    private fun assertBackup(fixture: Fixture) {
        val directory = File(store.workbookDir(fixture.id), "migration")
        if (fixture.version == 3) assertFalse(directory.exists()) else {
            assertArrayEquals(fixture.rawBytes, File(directory, "workbook-v${fixture.version}-original.json").readBytes())
        }
    }

    private fun rename(document: WorkbookDocument, name: String) = WorkbookCommand.Rename(
        UUID.randomUUID().toString(), document.id, "exact-revision-control", document.revision, name)

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")

    private data class Fixture(
        val id: String,
        val version: Int,
        val revision: Long,
        val document: WorkbookDocument,
        val rawBytes: ByteArray,
        val original: ByteArray,
        val parquet: ByteArray,
    )

    companion object {
        private const val SOURCE_CSV = "Category,Sales\nEast,10\nWest,20\nEast,5\n"
    }
}
