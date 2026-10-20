package io.github.baitian6641.cloudydata

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
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
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.4: legitimate v1/v2 defaults preserve native filter meaning through migration. */
@RunWith(AndroidJUnit4::class)
class LegacyFilterMigrationTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "legacy-filter-migration-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun unpairedKeepUsesOmittedVersionAndSeriesDefaultsWithoutWideningTo35() = runBlocking {
        for (version in listOf(1, 2)) {
            val fixture = legacyFixture(version, BASE_CSV, paired = false) { category, _ ->
                // Missing optional filter version means v1. An unpaired mark
                // needs no series anchor or value; row identity remains explicit.
                JSONObject().put("mode", "KEEP").put("rowFieldId", category)
                    .put("marks", JSONArray().put(JSONObject().put("row", "East")))
            }
            exerciseMigration(fixture,
                expectedFilter = MarkFilter(FilterMode.KEEP, fixture.categoryId, marks = listOf(FilterMark("East"))),
                expectedAggregates = listOf(listOf("East", "15")),
                expectedSourceRows = listOf(listOf("East", "N", "10"), listOf("East", "S", "5")))
        }
    }

    @Test
    fun pairedKeepRetainsBothFieldAnchorsAndExactEastNorth10() = runBlocking {
        for (version in listOf(1, 2)) {
            val fixture = legacyFixture(version, BASE_CSV, paired = true) { category, series ->
                JSONObject().put("version", 1).put("mode", "KEEP")
                    .put("rowFieldId", category).put("seriesFieldId", series)
                    .put("marks", JSONArray().put(JSONObject().put("row", "East").put("series", "N")))
            }
            exerciseMigration(fixture,
                expectedFilter = MarkFilter(FilterMode.KEEP, fixture.categoryId, fixture.seriesId,
                    listOf(FilterMark("East", "N"))),
                expectedAggregates = listOf(listOf("East", "N", "10")),
                expectedSourceRows = listOf(listOf("East", "N", "10")))
        }
    }

    @Test
    fun duplicateAndEmptyWhitespaceMarksRemainExactWithoutMultiplyingRows() = runBlocking {
        val exactMarks = listOf(FilterMark("East"), FilterMark("East"), FilterMark(""), FilterMark("  "))
        for (version in listOf(1, 2)) {
            val fixture = legacyFixture(version, BASE_CSV, paired = false) { category, _ ->
                JSONObject().put("mode", "KEEP").put("rowFieldId", category)
                    .put("marks", JSONArray().apply {
                        exactMarks.forEach { put(JSONObject().put("row", it.row)) }
                    })
            }
            // Repeated OR terms are valid and remain in the saved definition.
            // Empty and whitespace values are literal identities, not missing
            // records or NULLs. Neither occurs in this independently known CSV.
            exerciseMigration(fixture,
                expectedFilter = MarkFilter(FilterMode.KEEP, fixture.categoryId, marks = exactMarks),
                expectedAggregates = listOf(listOf("East", "15")),
                expectedSourceRows = listOf(listOf("East", "N", "10"), listOf("East", "S", "5")))
        }
    }

    @Test
    fun absentAndExplicitNullFiltersRemainIntentionallyUnfiltered() = runBlocking {
        for (version in listOf(1, 2)) {
            for (explicitNull in listOf(false, true)) {
                val fixture = legacyFixture(version, BASE_CSV, paired = false,
                    explicitNullFilter = explicitNull) { _, _ -> null }
                exerciseMigration(fixture,
                    expectedFilter = null,
                    expectedAggregates = listOf(listOf("West", "20"), listOf("East", "15")),
                    expectedSourceRows = listOf(listOf("East", "N", "10"),
                        listOf("East", "S", "5"), listOf("West", "N", "20")))
            }
        }
    }

    @Test
    fun explicitNullRowMarkKeepsNullRatherThanEmptyStringOrAllRows() = runBlocking {
        for (version in listOf(1, 2)) {
            val fixture = legacyFixture(version, BASE_CSV + ",N,3\n", paired = false) { category, _ ->
                JSONObject().put("mode", "KEEP").put("rowFieldId", category)
                    .put("seriesFieldId", JSONObject.NULL)
                    .put("marks", JSONArray().put(JSONObject().put("row", JSONObject.NULL)))
            }
            exerciseMigration(fixture,
                expectedFilter = MarkFilter(FilterMode.KEEP, fixture.categoryId, marks = listOf(FilterMark(null))),
                expectedAggregates = listOf(listOf(null, "3")),
                expectedSourceRows = listOf(listOf(null, "N", "3")))
        }
    }

    private suspend fun legacyFixture(
        version: Int,
        csv: String,
        paired: Boolean,
        explicitNullFilter: Boolean = false,
        filter: (String, String) -> JSONObject?,
    ): LegacyFixture {
        val workbook = store.create("Legacy filter v$version ${UUID.randomUUID()}")
        val input = File(root, "input-${workbook.id}.csv").apply { writeText(csv) }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        val category = snapshot.columns.single { it.name == "Category" }
        val series = snapshot.columns.single { it.name == "Series" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        val json = imported.toJson().put("formatVersion", version)
            .put("unknownOriginalEvidence", "Verbatim older packet text stays in the original backup")
        json.remove("favorite")
        json.remove("tags")
        val sheet = json.getJSONArray("worksheets").getJSONObject(0)
        sheet.put("rowFieldId", category.id).put("measureFieldId", sales.id)
            .put("aggregation", "SUM").put("markType", if (paired) "LINE" else "TABLE")
            .put("sortDescending", true)
        if (paired) sheet.put("seriesFieldId", series.id) else sheet.remove("seriesFieldId")
        // These fields did not exist in older packets. Missing optional values
        // carry their historical defaults, independently of strict present data.
        sheet.remove("secondaryMeasureFieldId")
        sheet.remove("secondaryAggregation")
        val title = "Legacy v$version exact filter"
        if (version == 1) {
            sheet.remove("chart")
            sheet.put("chartTitle", title).put("showGridlines", false).put("showValueLabels", true)
        } else {
            sheet.put("chart", JSONObject().put("version", 1).put("title", title)
                .put("showGridlines", false).put("showValueLabels", true))
            sheet.put("chartTitle", "Superseded flat chart title")
        }
        val filterJson = filter(category.id, series.id)
        when {
            filterJson != null -> sheet.put("filter", filterJson)
            explicitNullFilter -> sheet.put("filter", JSONObject.NULL)
            else -> sheet.remove("filter")
        }
        val metadata = File(store.workbookDir(imported.id), "workbook.json")
        // Formatting and an accepted unknown legacy field make byte-preserving
        // backup observably different from reserializing the normalized model.
        metadata.writeText("\n" + json.toString(2) + "\n")
        return LegacyFixture(imported.id, version, category.id, series.id, metadata.readBytes(),
            store.resolveInsideWorkbook(imported.id, snapshot.originalFile!!).readBytes(),
            store.resolveInsideWorkbook(imported.id, snapshot.snapshotFile).readBytes(), title)
    }

    private suspend fun exerciseMigration(
        fixture: LegacyFixture,
        expectedFilter: MarkFilter?,
        expectedAggregates: List<List<String?>>,
        expectedSourceRows: List<List<String?>>,
    ) {
        val metadata = File(store.workbookDir(fixture.id), "workbook.json")
        val backup = File(store.workbookDir(fixture.id), "migration/workbook-v${fixture.version}-original.json")
        val firstStore = WorkbookStore(root)
        val loaded = firstStore.loadCheckpoint(fixture.id)
        assertArrayEquals("Opening legacy metadata is read-only", fixture.legacyBytes, metadata.readBytes())
        assertFalse("Opening cannot create a migration backup", backup.exists())
        assertTrue(loaded.undo.isEmpty())
        assertTrue(loaded.redo.isEmpty())
        assertTrue(loaded.receipts.isEmpty())
        assertEquals(WorkbookDocument.CURRENT_FORMAT_VERSION, loaded.document.formatVersion)
        assertEquals(expectedFilter, loaded.document.worksheets.single().filter)
        assertFalse(loaded.document.favorite)
        assertTrue(loaded.document.tags.isEmpty())
        assertDecodedDefaults(fixture, loaded.document)
        assertNativeValues(loaded.document, expectedAggregates, expectedSourceRows, expectedFilter != null)
        assertArrayEquals(fixture.legacyBytes, metadata.readBytes())
        assertSourceUnchanged(fixture, loaded.document)

        val coordinator = firstStore.coordinators.open(fixture.id)
        assertEquals(loaded.document, coordinator.state.value.document)
        assertArrayEquals(fixture.legacyBytes, metadata.readBytes())
        assertFalse(backup.exists())
        val request = WorkbookCommand.Rename(UUID.randomUUID().toString(), fixture.id,
            "legacy-migration-test", loaded.document.revision, "Accepted legacy filter migration")
        val outcome = coordinator.execute(request)
        assertFalse(outcome.duplicate)
        assertEquals(loaded.document.revision + 1, outcome.acceptedRevision)
        val saved = firstStore.loadCheckpoint(fixture.id)
        assertEquals(listOf(loaded.document), saved.undo)
        assertTrue(saved.redo.isEmpty())
        assertEquals(listOf(request), saved.receipts.map { it.command })
        assertEquals(listOf(outcome.acceptedRevision), saved.receipts.map { it.acceptedRevision })
        assertEquals(3, JSONObject(metadata.readText()).getInt("formatVersion"))
        assertArrayEquals("First accepted migration must preserve exact original bytes", fixture.legacyBytes, backup.readBytes())
        assertEquals(expectedFilter, saved.document.worksheets.single().filter)

        val reopenedStore = WorkbookStore(root)
        val reopened = reopenedStore.loadCheckpoint(fixture.id)
        assertEquals(saved, reopened)
        assertDecodedDefaults(fixture, reopened.document)
        assertNativeValues(reopened.document, expectedAggregates, expectedSourceRows, expectedFilter != null)
        assertSourceUnchanged(fixture, reopened.document)

        val secondCoordinator = reopenedStore.coordinators.open(fixture.id)
        val secondRequest = WorkbookCommand.Rename(UUID.randomUUID().toString(), fixture.id,
            "legacy-migration-test", saved.document.revision, "Second accepted legacy save")
        val second = secondCoordinator.execute(secondRequest).document
        assertEquals(saved.document.revision + 1, second.revision)
        assertArrayEquals("Later saves cannot replace the legacy original", fixture.legacyBytes, backup.readBytes())
        val secondBytes = metadata.readBytes()
        val thirdStore = WorkbookStore(root)
        val secondReopened = thirdStore.coordinators.open(fixture.id)
        assertEquals(second, secondReopened.state.value.document)
        val duplicate = secondReopened.execute(request)
        assertTrue(duplicate.duplicate)
        assertEquals(outcome.acceptedRevision, duplicate.acceptedRevision)
        assertEquals(second, duplicate.document)
        assertArrayEquals("Opening and duplicate acceptance cannot replay a save", secondBytes, metadata.readBytes())
        assertEquals(2, thirdStore.loadCheckpoint(fixture.id).receipts.size)
        assertEquals(expectedFilter, second.worksheets.single().filter)
        assertNativeValues(second, expectedAggregates, expectedSourceRows, expectedFilter != null)
        assertSourceUnchanged(fixture, second)
        assertArrayEquals(fixture.legacyBytes, backup.readBytes())
    }

    private fun assertDecodedDefaults(fixture: LegacyFixture, document: WorkbookDocument) {
        val sheet = document.worksheets.single()
        assertNull(sheet.secondaryMeasureFieldId)
        assertEquals(Aggregation.SUM, sheet.secondaryAggregation)
        assertEquals(fixture.title, sheet.chart.title)
        assertFalse(sheet.chart.showGridlines)
        assertTrue(sheet.chart.showValueLabels)
        assertEquals(document.worksheets.single().id, document.activeWorksheetId)
        assertEquals(if (sheet.seriesFieldId == null) MarkType.TABLE else MarkType.LINE, sheet.markType)
    }

    private fun assertNativeValues(
        document: WorkbookDocument,
        expectedAggregates: List<List<String?>>,
        expectedSourceRows: List<List<String?>>,
        filtered: Boolean,
    ) {
        val snapshot = document.snapshot!!
        val sheet = document.worksheets.single()
        val path = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
        DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2).use { engine ->
            val result = WorksheetQuery(engine).query(snapshot, sheet, path)
            assertEquals(expectedAggregates, result.result.rows.map { row -> row.map { it?.toString() } })
            val underlying = UnderlyingRowsQuery(engine).query(snapshot, sheet, result,
                result.result.rows.indices.toSet(), path)!!
            assertEquals(listOf("Category", "Series", "Sales"), underlying.result.columns.map { it.name })
            assertEquals(expectedSourceRows.sortedBy { it.joinToString("|") },
                underlying.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it.joinToString("|") })
            assertEquals(filtered, underlying.worksheetFilterApplied)
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

    private data class LegacyFixture(
        val id: String,
        val version: Int,
        val categoryId: String,
        val seriesId: String,
        val legacyBytes: ByteArray,
        val originalBytes: ByteArray,
        val parquetBytes: ByteArray,
        val title: String,
    )

    companion object {
        private const val BASE_CSV = "Category,Series,Sales\nEast,N,10\nEast,S,5\nWest,N,20\n"
    }
}
