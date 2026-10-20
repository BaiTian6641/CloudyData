package io.github.baitian6641.cloudydata

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.Sql
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.ui.SaveState
import io.github.baitian6641.cloudydata.ui.ViewDataState
import io.github.baitian6641.cloudydata.ui.WorkbookUiState
import io.github.baitian6641.cloudydata.ui.WorkbookViewModel
import io.github.baitian6641.cloudydata.ui.WorkbookViewModelFactory
import io.github.baitian6641.cloudydata.ui.WorksheetQueryState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S03.2/S03.4 / F01,F64 / Q16,Q19,Q33,Q34: local tab, result and source-row identity. */
@RunWith(AndroidJUnit4::class)
class WorkbookSelectionConsistencyTest {
    private lateinit var context: Context
    private lateinit var root: File
    private val owners = mutableListOf<ViewModelStoreOwner>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "workbook-selection-consistency-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        onMain { owners.forEach { it.viewModelStore.clear() } }
        owners.clear()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun danglingStoredDefaultResolvesOneLocalSheetForQueryAndViewData() = runBlocking {
        val fixture = importFixture("dangling-default", MISSING_ID)
        val model = newModel(fixture)
        val ready = awaitQuery(model)
        val accepted = (ready.query as? WorksheetQueryState.Ready)?.result
        val grouped = accepted?.result?.rows?.map { row -> row.map { it?.toString() } }
        onMain { model.openViewData(EAST_ID, setOf(0)) }
        val view = withTimeout(20_000) { model.state.first { it.viewData !is ViewDataState.Loading } }.viewData
        val sourceRows = (view as? ViewDataState.Ready)?.result?.result?.rows
            ?.map { row -> row.map { it?.toString() } }?.sortedBy { it[1] }
        val expectedSource = listOf(listOf("East", "10"), listOf("East", "5"))
        assertUnchanged(fixture)
        assertEquals("the persisted reopen default remains available for explicit repair", MISSING_ID, fixture.store.load(fixture.document.id).activeWorksheetId)
        val coherent = ready.document?.activeWorksheetId == EAST_ID &&
            ready.document?.activeWorksheet?.id == EAST_ID && accepted?.queryIdentity?.worksheetId == EAST_ID &&
            grouped == listOf(listOf("East", "15")) && sourceRows == expectedSource
        assertTrue(
            "A dangling stored default must resolve one real local worksheet for tab, query and View Data. " +
                "visibleId=${ready.document?.activeWorksheetId}, resolvedGetterId=${ready.document?.activeWorksheet?.id}, " +
                "queryIdentity=${accepted?.queryIdentity?.worksheetId}, grouped=$grouped, viewData=$view, " +
                "sourceRows=$sourceRows, storedId=${fixture.store.load(fixture.document.id).activeWorksheetId}",
            coherent,
        )
    }

    @Test
    fun validStoredWestDefaultKeepsWestQueryAndExactSourceRowWithoutWrites() = runBlocking {
        val fixture = importFixture("valid-west-default", WEST_ID)
        val model = newModel(fixture)
        val ready = awaitQuery(model)
        val accepted = (ready.query as WorksheetQueryState.Ready).result
        assertEquals(WEST_ID, ready.document!!.activeWorksheetId)
        assertEquals(WEST_ID, ready.document.activeWorksheet!!.id)
        assertEquals(WEST_ID, accepted.queryIdentity!!.worksheetId)
        assertEquals(listOf(listOf("West", "20")), accepted.result.rows.map { row -> row.map { it?.toString() } })
        onMain { model.openViewData(WEST_ID, setOf(0)) }
        val view = withTimeout(20_000) { model.state.first { it.viewData !is ViewDataState.Loading } }.viewData
        assertTrue("valid West selection must expose its actual source row: $view", view is ViewDataState.Ready)
        val rows = (view as ViewDataState.Ready).result
        assertEquals(listOf(listOf("West", "20")), rows.result.rows.map { row -> row.map { it?.toString() } })
        assertEquals(1, rows.selectedMarkCount)
        assertTrue(rows.worksheetFilterApplied)
        assertUnchanged(fixture)
    }

    @Test
    fun queuedGenericEditCannotRetargetFirstSheetAfterUndoRemovesSelectedAddition() = runBlocking {
        val fixture = importFixture("queued-removed-target", EAST_ID)
        val model = newModel(fixture)
        awaitQuery(model)
        onMain { model.addWorksheet() }
        val addition = withTimeout(20_000) {
            model.state.first { it.document?.worksheets?.size == 3 && it.save is SaveState.Saved }
        }.document!!
        val addedId = addition.worksheets.single { it.id != EAST_ID && it.id != WEST_ID }.id
        withTimeout(20_000) {
            model.state.first {
                it.document?.activeWorksheetId == addedId &&
                    (it.query as? WorksheetQueryState.Ready)?.result?.queryIdentity?.worksheetId == addedId
            }
        }
        val afterAddition = fixture.store.loadCheckpoint(fixture.document.id)
        assertEquals(1, afterAddition.receipts.size)
        val coordinator = fixture.store.coordinators.open(fixture.document.id)
        val releaseReader = CompletableDeferred<Unit>()
        try {
            withTimeout(20_000) {
                val readerEntered = CompletableDeferred<Unit>()
                val reader = async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.readSnapshot(checkNotNull(fixture.document.snapshot).id) {
                        readerEntered.complete(Unit)
                        releaseReader.await()
                        snapshotCountAndSum(fixture)
                    }
                }
                readerEntered.await()
                onMain {
                    model.undo()
                    model.setAggregation(Aggregation.AVG)
                }
                assertEquals("the real snapshot reader still holds off both queued writes", addition.revision, fixture.store.load(fixture.document.id).revision)
                releaseReader.complete(Unit)
                assertEquals(listOf(3L, 35L), reader.await())
                val settled = model.state.first {
                    it.save is SaveState.Conflict ||
                        (it.save is SaveState.Saved && it.document?.revision == addition.revision + 2)
                }
                val checkpoint = fixture.store.loadCheckpoint(fixture.document.id)
                val groups = nativeGroups(fixture, checkpoint.document)
                val conflict = settled.save as? SaveState.Conflict
                val message = conflict?.message.orEmpty().lowercase()
                val coherent = checkpoint.document.revision == addition.revision + 1 &&
                    checkpoint.document.worksheets == fixture.document.worksheets &&
                    checkpoint.receipts == afterAddition.receipts &&
                    message.contains("worksheet") && message.contains("not applied") &&
                    (message.contains("missing") || message.contains("no longer")) &&
                    groups == mapOf(EAST_ID to listOf(listOf("East", "15")), WEST_ID to listOf(listOf("West", "20")))
                assertArrayEquals(fixture.snapshotBytes, fixture.snapshotFile.readBytes())
                assertArrayEquals(fixture.sourceBytes, fixture.sourceFile.readBytes())
                assertTrue(
                    "A generic edit queued for the added worksheet must be refused after Undo removes that target. " +
                        "Expected only Undo at revision ${addition.revision + 1}; actualRevision=${checkpoint.document.revision}, " +
                        "aggregations=${checkpoint.document.worksheets.map { it.id to it.aggregation }}, " +
                        "receipts=${checkpoint.receipts.size}, nativeGroups=$groups, save=${settled.save}, " +
                        "visibleId=${settled.document?.activeWorksheetId}",
                    coherent,
                )
            }
        } finally {
            releaseReader.complete(Unit)
        }
    }

    @Test
    fun nullableLegacyDefaultsAndEmptyWorkbooksResolveLocallyWithoutRepairWrites() = runBlocking {
        for (version in listOf(1, 2)) for (mode in listOf("missing", "null")) {
            val imported = importFixture("legacy-$version-$mode-default", MISSING_ID)
            val json = JSONObject(String(imported.metadataBytes, Charsets.UTF_8)).apply {
                put("formatVersion", version)
                remove("commandJournal")
                if (mode == "missing") remove("activeWorksheetId") else put("activeWorksheetId", JSONObject.NULL)
            }
            imported.metadata.writeText(json.toString(2) + "\n")
            val fixture = imported.copy(metadataBytes = imported.metadata.readBytes())
            val model = newModel(fixture)
            try {
                val ready = awaitReadyFor(model, EAST_ID, listOf(listOf("East", "15")))
                val beforeQuery = ready.query
                val beforeRevision = ready.document!!.revision
                onMain { model.selectWorksheet(MISSING_ID) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(EAST_ID, model.state.value.document!!.activeWorksheetId)
                assertEquals(beforeRevision, model.state.value.document!!.revision)
                assertSame("invalid selection cannot discard an accepted result", beforeQuery, model.state.value.query)
                assertViewData(model, EAST_ID, listOf(listOf("East", "10"), listOf("East", "5")))
                assertEquals(null, fixture.store.load(fixture.document.id).activeWorksheetId)
                assertFalse(File(fixture.metadata.parentFile, "migration").exists())
                assertUnchanged(fixture)
            } finally {
                clearLastModel()
            }
        }

        val nullFixture = importFixture("current-null-default", null)
        val nullModel = newModel(nullFixture)
        try {
            awaitReadyFor(nullModel, EAST_ID, listOf(listOf("East", "15")))
            assertViewData(nullModel, EAST_ID, listOf(listOf("East", "10"), listOf("East", "5")))
            assertEquals(null, nullFixture.store.load(nullFixture.document.id).activeWorksheetId)
            assertUnchanged(nullFixture)
        } finally {
            clearLastModel()
        }

        for (version in listOf(1, 2, 3)) {
            val storedDefault = if (version == 2) null else MISSING_ID
            val imported = importFixture("empty-$version", storedDefault, emptySheets = true)
            val fixture = if (version < 3) {
                val json = JSONObject(String(imported.metadataBytes, Charsets.UTF_8)).apply {
                    put("formatVersion", version)
                    remove("commandJournal")
                }
                imported.metadata.writeText(json.toString(2) + "\n")
                imported.copy(metadataBytes = imported.metadata.readBytes())
            } else imported
            val model = newModel(fixture)
            try {
                val loaded = withTimeout(20_000) { model.state.first { !it.loading } }
                assertEquals(null, loaded.loadError)
                assertTrue(loaded.document!!.worksheets.isEmpty())
                assertEquals(null, loaded.document.activeWorksheetId)
                assertEquals(null, loaded.document.activeWorksheet)
                assertTrue(loaded.query is WorksheetQueryState.Empty)
                onMain {
                    model.selectWorksheet(EAST_ID)
                    model.openViewData(EAST_ID, setOf(0))
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertEquals(null, model.state.value.document!!.activeWorksheetId)
                assertTrue(model.state.value.query is WorksheetQueryState.Empty)
                assertTrue(model.state.value.viewData is ViewDataState.Idle)
                assertEquals(storedDefault, fixture.store.load(fixture.document.id).activeWorksheetId)
                assertEquals(listOf(3L, 35L), snapshotCountAndSum(fixture))
                assertUnchanged(fixture)
            } finally {
                clearLastModel()
            }
        }
    }

    @Test
    fun rapidTabChangeInvalidatesOldViewDataWhileSharedEditsKeepEachLocalTarget() = runBlocking {
        val fixture = importFixture("two-local-views", MISSING_ID)
        val westView = newModel(fixture)
        val eastView = newModel(fixture)
        awaitReadyFor(westView, EAST_ID, listOf(listOf("East", "15")))
        awaitReadyFor(eastView, EAST_ID, listOf(listOf("East", "15")))
        val coordinator = fixture.store.coordinators.open(fixture.document.id)
        val releaseReader = CompletableDeferred<Unit>()
        try {
            withTimeout(20_000) {
                val readerEntered = CompletableDeferred<Unit>()
                val reader = async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.readSnapshot(checkNotNull(fixture.document.snapshot).id) {
                        readerEntered.complete(Unit)
                        releaseReader.await()
                        snapshotCountAndSum(fixture)
                    }
                }
                readerEntered.await()
                onMain { westView.openViewData(EAST_ID, setOf(0)) }
                assertTrue("the East source request is pending behind a real reader", westView.state.value.viewData is ViewDataState.Loading)
                onMain { westView.selectWorksheet(WEST_ID) }
                assertTrue("changing tabs invalidates the pending East source request immediately", westView.state.value.viewData is ViewDataState.Idle)
                val switching = westView.state.value.query
                assertTrue(switching is WorksheetQueryState.Loading)
                assertEquals("an East result cannot appear beneath the West tab", null, (switching as WorksheetQueryState.Loading).previous)
                releaseReader.complete(Unit)
                assertEquals(listOf(3L, 35L), reader.await())
                awaitReadyFor(westView, WEST_ID, listOf(listOf("West", "20")))
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertTrue("the old source result cannot reopen View Data after West is ready", westView.state.value.viewData is ViewDataState.Idle)
            }
        } finally {
            releaseReader.complete(Unit)
        }
        awaitReadyFor(eastView, EAST_ID, listOf(listOf("East", "15")))
        onMain { westView.openViewData(EAST_ID, setOf(0)) }
        assertTrue("a request for another local tab must be ignored", westView.state.value.viewData is ViewDataState.Idle)
        assertUnchanged(fixture)

        onMain { eastView.renameWorkbook("Shared name with independent tabs") }
        withTimeout(20_000) {
            for (model in listOf(eastView, westView)) {
                model.state.first {
                    it.document?.name == "Shared name with independent tabs" &&
                        it.document.revision == fixture.document.revision + 1 && it.save is SaveState.Saved
                }
            }
        }
        awaitReadyFor(eastView, EAST_ID, listOf(listOf("East", "15")))
        awaitReadyFor(westView, WEST_ID, listOf(listOf("West", "20")))
        assertEquals(MISSING_ID, fixture.store.load(fixture.document.id).activeWorksheetId)

        onMain { westView.setAggregation(Aggregation.COUNT) }
        withTimeout(20_000) {
            westView.state.first {
                it.document?.revision == fixture.document.revision + 2 && it.save is SaveState.Saved &&
                    (it.query as? WorksheetQueryState.Ready)?.result?.result?.rows?.singleOrNull()?.get(1)?.toString() == "1"
            }
            eastView.state.first { it.document?.revision == fixture.document.revision + 2 }
        }
        awaitReadyFor(westView, WEST_ID, listOf(listOf("West", "1")))
        awaitReadyFor(eastView, EAST_ID, listOf(listOf("East", "15")))
        val checkpoint = fixture.store.loadCheckpoint(fixture.document.id)
        assertEquals(Aggregation.SUM, checkpoint.document.worksheets.single { it.id == EAST_ID }.aggregation)
        assertEquals(Aggregation.COUNT, checkpoint.document.worksheets.single { it.id == WEST_ID }.aggregation)
        assertEquals(MISSING_ID, checkpoint.document.activeWorksheetId)
        assertEquals(1, checkpoint.receipts.size)
        assertEquals(2, checkpoint.undo.size)
        assertEquals(
            mapOf(EAST_ID to listOf(listOf("East", "15")), WEST_ID to listOf(listOf("West", "1"))),
            nativeGroups(fixture, checkpoint.document),
        )
        assertViewData(eastView, EAST_ID, listOf(listOf("East", "10"), listOf("East", "5")))
        assertViewData(westView, WEST_ID, listOf(listOf("West", "20")))
        assertArrayEquals(fixture.snapshotBytes, fixture.snapshotFile.readBytes())
        assertArrayEquals(fixture.sourceBytes, fixture.sourceFile.readBytes())
    }

    private data class Fixture(
        val store: WorkbookStore,
        val document: WorkbookDocument,
        val metadata: File,
        val metadataBytes: ByteArray,
        val snapshotFile: File,
        val snapshotBytes: ByteArray,
        val sourceFile: File,
        val sourceBytes: ByteArray,
    )

    private suspend fun importFixture(label: String, storedActiveId: String?, emptySheets: Boolean = false): Fixture {
        val storeRoot = File(root, label)
        val store = WorkbookStore(storeRoot)
        val original = store.create("Selection consistency $label")
        val csv = File(storeRoot, "selection.csv").apply {
            writeText("Category,Sales\nEast,10\nWest,20\nEast,5\n")
        }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(original, Uri.fromFile(csv))
        val imported = importer.apply(original, pending)
        val snapshot = checkNotNull(imported.snapshot)
        val rowId = snapshot.columns.single { it.name == "Category" }.id
        val measureId = snapshot.columns.single { it.name == "Sales" }.id
        val east = imported.worksheets.single().copy(
            id = EAST_ID,
            name = "East worksheet",
            rowFieldId = rowId,
            measureFieldId = measureId,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sortDescending = false,
            filter = MarkFilter(FilterMode.KEEP, rowId, marks = listOf(FilterMark("East"))),
        )
        val west = east.copy(
            id = WEST_ID,
            name = "West worksheet",
            filter = MarkFilter(FilterMode.KEEP, rowId, marks = listOf(FilterMark("West"))),
        )
        val document = store.save(imported.copy(
            worksheets = if (emptySheets) emptyList() else listOf(east, west), activeWorksheetId = storedActiveId,
        ))
        val metadata = File(store.workbookDir(document.id), "workbook.json")
        val snapshotFile = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile)
        val sourceFile = store.resolveInsideWorkbook(document.id, checkNotNull(snapshot.originalFile))
        return Fixture(
            store, document, metadata, metadata.readBytes(), snapshotFile, snapshotFile.readBytes(),
            sourceFile, sourceFile.readBytes(),
        )
    }

    private fun newModel(fixture: Fixture): WorkbookViewModel {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        owners += owner
        lateinit var model: WorkbookViewModel
        onMain {
            model = ViewModelProvider(
                owner, WorkbookViewModelFactory(context.applicationContext as Application, fixture.store, fixture.document.id),
            )[WorkbookViewModel::class.java]
        }
        return model
    }

    private suspend fun awaitQuery(model: WorkbookViewModel): WorkbookUiState = withTimeout(20_000) {
        model.state.first {
            !it.loading && (it.query is WorksheetQueryState.Ready || it.query is WorksheetQueryState.Failed || it.loadError != null)
        }.also {
            assertEquals(null, it.loadError)
            assertTrue("real worksheet query must be ready: ${it.query}", it.query is WorksheetQueryState.Ready)
        }
    }

    private suspend fun awaitReadyFor(
        model: WorkbookViewModel,
        sheetId: String,
        expected: List<List<String>>,
    ): WorkbookUiState = withTimeout(20_000) {
        model.state.first {
            !it.loading && it.document?.activeWorksheetId == sheetId &&
                (it.query as? WorksheetQueryState.Ready)?.result?.queryIdentity?.worksheetId == sheetId
        }.also {
            assertEquals(null, it.loadError)
            assertEquals(sheetId, it.document!!.activeWorksheet!!.id)
            assertEquals(expected, (it.query as WorksheetQueryState.Ready).result.result.rows.map { row -> row.map { value -> value?.toString() } })
        }
    }

    private suspend fun assertViewData(model: WorkbookViewModel, sheetId: String, expected: List<List<String>>) {
        onMain { model.openViewData(sheetId, setOf(0)) }
        val view = withTimeout(20_000) { model.state.first { it.viewData !is ViewDataState.Loading } }.viewData
        assertTrue("$sheetId must expose the selected source rows: $view", view is ViewDataState.Ready)
        val rows = (view as ViewDataState.Ready).result
        assertEquals(expected.sortedBy { it[1] }, rows.result.rows.map { row -> row.map { it?.toString() } }.sortedBy { it[1] })
        assertEquals(1, rows.selectedMarkCount)
        assertTrue(rows.worksheetFilterApplied)
        assertFalse(rows.truncated)
    }

    private fun clearLastModel() {
        val owner = owners.removeAt(owners.lastIndex)
        onMain { owner.viewModelStore.clear() }
    }

    private fun assertUnchanged(fixture: Fixture) {
        assertArrayEquals("opening and local selection must not rewrite metadata", fixture.metadataBytes, fixture.metadata.readBytes())
        assertArrayEquals(fixture.snapshotBytes, fixture.snapshotFile.readBytes())
        assertArrayEquals(fixture.sourceBytes, fixture.sourceFile.readBytes())
    }

    private fun snapshotCountAndSum(fixture: Fixture): List<Long> =
        DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1).use { engine ->
            engine.execute(
                "SELECT COUNT(*), CAST(SUM(Sales) AS BIGINT) FROM read_parquet(${Sql.string(fixture.snapshotFile.absolutePath)})",
                maxRows = 1,
            ).rows.single().map { it as Long }
        }

    private fun nativeGroups(fixture: Fixture, document: WorkbookDocument): Map<String, List<List<String?>>> =
        DuckDbEngine.open(":memory:", memoryLimitMb = 128, threads = 1).use { engine ->
            document.worksheets.associate { sheet ->
                sheet.id to WorksheetQuery(engine).query(
                    checkNotNull(document.snapshot), sheet, fixture.snapshotFile.absolutePath,
                ).result.rows.map { row -> row.map { it?.toString() } }
            }
        }

    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    companion object {
        private const val EAST_ID = "selection-east-sheet"
        private const val WEST_ID = "selection-west-sheet"
        private const val MISSING_ID = "selection-missing-sheet"
    }
}
