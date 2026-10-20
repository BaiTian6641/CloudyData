package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.Sql
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import java.io.File
import java.util.UUID
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

/** S03.4 / F01,F17,F36,F64 / Q33,Q34: exact native legacy secondary aggregation. */
@RunWith(AndroidJUnit4::class)
class LegacySecondaryAggregationMigrationTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "legacy-secondary-migration-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun validLegacyAverageRemains100ThroughAcceptedMigrationReopenAndLaterSave() = runBlocking {
        for (version in listOf(1, 2)) {
            exerciseMigration(legacyFixture(version, "AVG"), Aggregation.AVG,
                listOf(listOf("2026-01-01", "150", "100.0"),
                    listOf("2026-02-01", "120", "20.0"),
                    listOf("2026-03-01", "80", "60.0")))
        }
    }

    @Test
    fun explicitAndHistoricallyAbsentSecondaryAggregationBothRemainSum200() = runBlocking {
        for (version in listOf(1, 2)) {
            for (presentValue in listOf("SUM", null)) {
                exerciseMigration(legacyFixture(version, presentValue), Aggregation.SUM,
                    listOf(listOf("2026-01-01", "150", "200"),
                        listOf("2026-02-01", "120", "20"),
                        listOf("2026-03-01", "80", "60")))
            }
        }
    }

    @Test
    fun allExistingEnumTagsDecodeExactlyWithoutClaimingQueryApplicability() = runBlocking {
        for (version in listOf(1, 2)) {
            val fixture = legacyFixture(version, "AVG")
            val metadata = metadata(fixture.id)
            val backup = migrationBackup(fixture)
            val original = JSONObject(fixture.legacyBytes.toString(Charsets.UTF_8))
            for (aggregation in Aggregation.entries) {
                val candidate = JSONObject(original.toString())
                candidate.getJSONArray("worksheets").getJSONObject(0)
                    .put("secondaryAggregation", aggregation.name)
                val exactBytes = (candidate.toString(2) + "\n").toByteArray(Charsets.UTF_8)
                metadata.writeBytes(exactBytes)
                val loaded = WorkbookStore(root).loadCheckpoint(fixture.id)
                assertEquals("Decoding cannot replace a known enum tag", aggregation,
                    loaded.document.worksheets.single().secondaryAggregation)
                assertTrue(loaded.undo.isEmpty())
                assertTrue(loaded.redo.isEmpty())
                assertTrue(loaded.receipts.isEmpty())
                assertArrayEquals("Syntax-only admission is read-only", exactBytes, metadata.readBytes())
                assertFalse(backup.exists())
                assertSourceUnchanged(fixture, loaded.document)
            }
            // NONE and other tags above are accepted syntax, not a statement
            // that every aggregation/mark combination is executable. Read the
            // actual unchanged source independently of worksheet applicability.
            val snapshot = WorkbookStore(root).load(fixture.id).snapshot!!
            val path = store.resolveInsideWorkbook(fixture.id, snapshot.snapshotFile).absolutePath
            DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
                val source = engine.execute("SELECT COUNT(*), SUM(sales), SUM(cost) FROM read_parquet(${Sql.string(path)})", maxRows = 1)
                assertEquals(listOf("4", "350", "280"), source.rows.single().map { it?.toString() })
            }
        }
    }

    private suspend fun legacyFixture(version: Int, secondaryValue: String?): LegacyFixture {
        val workbook = store.create("Legacy secondary v$version ${UUID.randomUUID()}")
        val input = File(root, "dual-axis-${workbook.id}.csv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("csv/dual_axis.csv").use { source ->
            input.outputStream().use { source.copyTo(it) }
        }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        assertEquals(4L, snapshot.rowCount)
        val month = snapshot.columns.single { it.name == "month" }
        val sales = snapshot.columns.single { it.name == "sales" }
        val cost = snapshot.columns.single { it.name == "cost" }
        val json = imported.toJson().put("formatVersion", version)
            .put("unknownOriginalEvidence", "Preserve this older packet verbatim, including whitespace")
        val sheet = json.getJSONArray("worksheets").getJSONObject(0)
        sheet.put("rowFieldId", month.id).put("measureFieldId", sales.id)
            .put("secondaryMeasureFieldId", cost.id).put("aggregation", "SUM")
            .put("markType", "BAR").put("sortDescending", true)
        sheet.remove("seriesFieldId")
        sheet.remove("filter")
        if (secondaryValue == null) sheet.remove("secondaryAggregation")
        else sheet.put("secondaryAggregation", secondaryValue)
        if (version == 1) {
            sheet.remove("chart")
            sheet.put("chartTitle", "Legacy secondary values").put("showGridlines", true)
        } else {
            sheet.put("chart", JSONObject().put("version", 1).put("title", "Legacy secondary values")
                .put("secondaryAxisTitle", "Cost"))
        }
        val exactBytes = ("\n" + json.toString(2) + "\n").toByteArray(Charsets.UTF_8)
        metadata(imported.id).writeBytes(exactBytes)
        return LegacyFixture(imported.id, version, cost.id, exactBytes,
            store.resolveInsideWorkbook(imported.id, snapshot.originalFile!!).readBytes(),
            store.resolveInsideWorkbook(imported.id, snapshot.snapshotFile).readBytes())
    }

    private suspend fun exerciseMigration(
        fixture: LegacyFixture,
        expectedAggregation: Aggregation,
        expectedRows: List<List<String?>>,
    ) {
        val metadata = metadata(fixture.id)
        val backup = migrationBackup(fixture)
        val firstStore = WorkbookStore(root)
        val loaded = firstStore.loadCheckpoint(fixture.id)
        assertArrayEquals("Loading legacy metadata cannot rewrite its aggregation", fixture.legacyBytes, metadata.readBytes())
        assertFalse("Loading alone must not create a migration backup", backup.exists())
        assertTrue(loaded.undo.isEmpty())
        assertTrue(loaded.redo.isEmpty())
        assertTrue(loaded.receipts.isEmpty())
        assertEquals(WorkbookDocument.CURRENT_FORMAT_VERSION, loaded.document.formatVersion)
        assertEquals(expectedAggregation, loaded.document.worksheets.single().secondaryAggregation)
        assertEquals(fixture.costId, loaded.document.worksheets.single().secondaryMeasureFieldId)
        assertEquals(MarkType.BAR, loaded.document.worksheets.single().markType)
        assertNativeValues(loaded.document, expectedAggregation, expectedRows)
        assertArrayEquals(fixture.legacyBytes, metadata.readBytes())
        assertSourceUnchanged(fixture, loaded.document)

        val coordinator = firstStore.coordinators.open(fixture.id)
        assertEquals(loaded.document, coordinator.state.value.document)
        assertArrayEquals(fixture.legacyBytes, metadata.readBytes())
        assertFalse(backup.exists())
        val firstRequest = WorkbookCommand.Rename(UUID.randomUUID().toString(), fixture.id,
            "legacy-secondary-test", loaded.document.revision, "Accepted secondary aggregation migration")
        val firstOutcome = coordinator.execute(firstRequest)
        assertFalse(firstOutcome.duplicate)
        assertEquals(loaded.document.revision + 1, firstOutcome.acceptedRevision)
        val saved = firstStore.loadCheckpoint(fixture.id)
        assertEquals(listOf(loaded.document), saved.undo)
        assertTrue(saved.redo.isEmpty())
        assertEquals(listOf(firstRequest), saved.receipts.map { it.command })
        assertEquals(listOf(firstOutcome.acceptedRevision), saved.receipts.map { it.acceptedRevision })
        assertEquals(3, JSONObject(metadata.readText()).getInt("formatVersion"))
        assertArrayEquals("First accepted migration preserves the exact original packet", fixture.legacyBytes, backup.readBytes())
        assertEquals(expectedAggregation, saved.document.worksheets.single().secondaryAggregation)

        val reopenedStore = WorkbookStore(root)
        val reopened = reopenedStore.loadCheckpoint(fixture.id)
        assertEquals(saved, reopened)
        assertNativeValues(reopened.document, expectedAggregation, expectedRows)
        assertSourceUnchanged(fixture, reopened.document)

        val secondCoordinator = reopenedStore.coordinators.open(fixture.id)
        val secondRequest = WorkbookCommand.Rename(UUID.randomUUID().toString(), fixture.id,
            "legacy-secondary-test", saved.document.revision, "Later secondary aggregation save")
        val second = secondCoordinator.execute(secondRequest).document
        assertEquals(saved.document.revision + 1, second.revision)
        assertArrayEquals("A later accepted save cannot overwrite the legacy backup", fixture.legacyBytes, backup.readBytes())
        val secondBytes = metadata.readBytes()
        val thirdStore = WorkbookStore(root)
        val afterSecond = thirdStore.coordinators.open(fixture.id)
        assertEquals(second, afterSecond.state.value.document)
        val duplicate = afterSecond.execute(firstRequest)
        assertTrue(duplicate.duplicate)
        assertEquals(firstOutcome.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(second, duplicate.document)
        assertArrayEquals("Reopen and duplicate acknowledgement cannot replay the save", secondBytes, metadata.readBytes())
        val finalCheckpoint = thirdStore.loadCheckpoint(fixture.id)
        assertEquals(listOf(loaded.document, saved.document), finalCheckpoint.undo)
        assertTrue(finalCheckpoint.redo.isEmpty())
        assertEquals(listOf(firstRequest, secondRequest), finalCheckpoint.receipts.map { it.command })
        assertEquals(listOf(firstOutcome.acceptedRevision, second.revision), finalCheckpoint.receipts.map { it.acceptedRevision })
        assertNativeValues(second, expectedAggregation, expectedRows)
        assertSourceUnchanged(fixture, second)
        assertArrayEquals(fixture.legacyBytes, backup.readBytes())
    }

    private fun assertNativeValues(
        document: WorkbookDocument,
        expectedAggregation: Aggregation,
        expectedRows: List<List<String?>>,
    ) {
        val snapshot = document.snapshot!!
        val sheet = document.worksheets.single()
        val path = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val result = WorksheetQuery(engine).query(snapshot, sheet, path)
            assertEquals(expectedAggregation, result.secondaryAggregation)
            assertEquals(listOf("row", "value", "secondary_value"), result.result.columns.map { it.name })
            assertEquals(expectedRows, result.result.rows.map { row -> row.map { it?.toString() } })
            val january = result.result.rows.indexOfFirst { it[0] == "2026-01-01" }
            assertTrue(january >= 0)
            val underlying = UnderlyingRowsQuery(engine).query(snapshot, sheet, result, setOf(january), path)!!
            assertEquals(listOf("month", "sales", "cost"), underlying.result.columns.map { it.name })
            assertEquals(listOf(listOf("2026-01-01", "100", "120"), listOf("2026-01-01", "50", "80")),
                underlying.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it[1] })
            assertEquals(1, underlying.selectedMarkCount)
            assertFalse(underlying.truncated)
        }
    }

    private fun assertSourceUnchanged(fixture: LegacyFixture, document: WorkbookDocument) {
        val snapshot = document.snapshot!!
        assertArrayEquals(fixture.originalBytes,
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertArrayEquals(fixture.parquetBytes,
            store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
        assertTrue(File(store.workbookDir(document.id), "staging").listFiles().orEmpty().isEmpty())
    }

    private fun metadata(id: String) = File(store.workbookDir(id), "workbook.json")
    private fun migrationBackup(fixture: LegacyFixture) =
        File(store.workbookDir(fixture.id), "migration/workbook-v${fixture.version}-original.json")

    private data class LegacyFixture(
        val id: String,
        val version: Int,
        val costId: String,
        val legacyBytes: ByteArray,
        val originalBytes: ByteArray,
        val parquetBytes: ByteArray,
    )
}
