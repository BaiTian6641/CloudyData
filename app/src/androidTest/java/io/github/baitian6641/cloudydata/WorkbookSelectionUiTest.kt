package io.github.baitian6641.cloudydata

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.FilterMark
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkFilter
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef
import io.github.baitian6641.cloudydata.ui.WorkbookUiState
import io.github.baitian6641.cloudydata.ui.WorkbookViewModel
import io.github.baitian6641.cloudydata.ui.WorkbookViewModelFactory
import io.github.baitian6641.cloudydata.ui.WorksheetQueryState
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual native Activity/Compose selection; no window, preference or device reconfiguration. */
@RunWith(AndroidJUnit4::class)
class WorkbookSelectionUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private lateinit var app: CloudyDataApp
    private lateinit var store: WorkbookStore
    private lateinit var device: UiDevice
    private lateinit var document: WorkbookDocument
    private lateinit var east: WorksheetDef
    private lateinit var west: WorksheetDef
    private lateinit var input: File
    private lateinit var evidence: File
    private var createdWorkbookId: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = runBlocking {
        context = instrumentation.targetContext
        app = context.applicationContext as CloudyDataApp
        store = app.workbookStore
        device = UiDevice.getInstance(instrumentation)
        val runId = UUID.randomUUID().toString()
        evidence = File(checkNotNull(context.getExternalFilesDir(null)), "qualification/workbook-selection-$runId")
            .apply { check(mkdirs()) }
        val workbook = store.create("Selection coherence $runId")
        createdWorkbookId = workbook.id
        input = File(context.cacheDir, "selection-ui-$runId.csv").apply {
            writeText("Category,Sales\nEast,10\nWest,20\nEast,5\n")
        }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        east = imported.worksheets.single().copy(name = "East selection",
            rowFieldId = category.id, measureFieldId = sales.id, aggregation = Aggregation.SUM,
            markType = MarkType.TABLE, sortDescending = true, chart = ChartOptions(title = "East exact values"),
            filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East"))))
        west = east.copy(id = UUID.randomUUID().toString(), name = "West selection",
            chart = ChartOptions(title = "West exact values"),
            filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("West"))))
        document = store.save(imported.copy(worksheets = listOf(east, west), activeWorksheetId = "missing-sheet"))
    }

    @After
    fun tearDown() {
        scenario?.close()
        instrumentation.waitForIdleSync()
        createdWorkbookId?.let { store.workbookDir(it).deleteRecursively() }
        if (::input.isInitialized) input.delete()
        // External qualification captures deliberately survive fixture cleanup.
    }

    @Test
    fun nativeLaunchResolvesFirstSheetAndExplicitTabsKeepVisibleValuesAndOwnershipCoherent() {
        val canonical = File(store.workbookDir(document.id), "workbook.json").readBytes()
        val snapshot = document.snapshot!!
        val original = store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes()
        val parquet = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WORKBOOK_ID, document.id))
        lateinit var model: WorkbookViewModel
        scenario!!.onActivity { activity ->
            model = ViewModelProvider(activity, WorkbookViewModelFactory(app, store, document.id))
                .get(document.id, WorkbookViewModel::class.java)
        }
        val initial = awaitReady(model)
        instrumentation.waitForIdleSync()
        val initialEastVisible = device.wait(Until.hasObject(By.text("15")), UI_TIMEOUT_MS)
        val initialMissing = device.hasObject(By.text(MISSING_SHEET_TEXT))
        val initialResult = (initial.query as WorksheetQueryState.Ready).result
        val initiallyCoherent = initial.document?.activeWorksheetId == east.id &&
            initialResult.queryIdentity?.worksheetId == east.id && initialEastVisible && !initialMissing
        recordCapture("initial", initial, initialMissing)
        Log.i(TAG, "INITIAL_SELECTION visibleId=${initial.document?.activeWorksheetId} " +
            "querySheet=${initialResult.queryIdentity?.worksheetId} rows=${initialResult.result.rows} " +
            "east15Visible=$initialEastVisible missingSheetText=$initialMissing coherent=$initiallyCoherent")

        // Retain this real tab control even when the initial-state regression is
        // present. The final assertion still fails on the original incoherence.
        val westTab = device.wait(Until.findObject(By.text(west.name)), UI_TIMEOUT_MS)
        assertNotNull("The actual West worksheet tab must be visible", westTab)
        westTab!!.click()
        val westState = awaitReady(model, west.id)
        assertEquals(listOf(listOf("West", "20")), textRows(westState))
        assertTrue("The native result must show West's exact 20", device.wait(Until.hasObject(By.text("20")), UI_TIMEOUT_MS))
        assertTrue(device.wait(Until.gone(By.text(MISSING_SHEET_TEXT)), UI_TIMEOUT_MS))
        recordCapture("west-selected", westState, missing = false)
        assertArrayEquals(canonical, File(store.workbookDir(document.id), "workbook.json").readBytes())

        val eastTab = device.wait(Until.findObject(By.text(east.name)), UI_TIMEOUT_MS)
        assertNotNull(eastTab)
        eastTab!!.click()
        val eastState = awaitReady(model, east.id)
        assertEquals(listOf(listOf("East", "15")), textRows(eastState))
        assertTrue("The native result must show East's exact 15", device.wait(Until.hasObject(By.text("15")), UI_TIMEOUT_MS))
        assertTrue(device.wait(Until.gone(By.text(MISSING_SHEET_TEXT)), UI_TIMEOUT_MS))
        recordCapture("east-selected", eastState, missing = false)

        val dataTab = device.wait(Until.findObject(By.text("Data source")), UI_TIMEOUT_MS)
        assertNotNull("The actual Data source tab must be visible", dataTab)
        dataTab!!.click()
        assertTrue("The Data source tab must show the native snapshot/schema surface",
            device.wait(Until.hasObject(By.text(DATA_SOURCE_SUMMARY)), UI_TIMEOUT_MS))
        assertTrue(device.hasObject(By.text("Local Parquet")))
        assertTrue(device.hasObject(By.text("Original preserved")))
        assertTrue(device.hasObject(By.text(snapshot.displayName)))
        assertTrue(device.hasObject(By.text("Schema")))
        assertTrue(device.hasObject(By.text("Category")))
        assertTrue(device.hasObject(By.text("Sales")))
        assertTrue("The worksheet's East 15 must leave the visible surface on Data source",
            device.wait(Until.gone(By.text("15")), UI_TIMEOUT_MS))
        assertTrue(device.wait(Until.gone(By.text(MISSING_SHEET_TEXT)), UI_TIMEOUT_MS))
        val dataState = awaitReady(model, east.id)
        assertEquals(listOf(listOf("East", "15")), textRows(dataState))
        recordCapture("data-source", dataState, missing = false)

        val returnWestTab = device.wait(Until.findObject(By.text(west.name)), UI_TIMEOUT_MS)
        assertNotNull("The West worksheet must remain selectable from Data source", returnWestTab)
        returnWestTab!!.click()
        val returnWestState = awaitReady(model, west.id)
        assertEquals(listOf(listOf("West", "20")), textRows(returnWestState))
        assertTrue("Returning from Data source must show West's exact 20",
            device.wait(Until.hasObject(By.text("20")), UI_TIMEOUT_MS))
        assertTrue(device.wait(Until.gone(By.text(DATA_SOURCE_SUMMARY)), UI_TIMEOUT_MS))
        assertTrue(device.wait(Until.gone(By.text(MISSING_SHEET_TEXT)), UI_TIMEOUT_MS))
        recordCapture("west-after-data", returnWestState, missing = false)
        assertArrayEquals(canonical, File(store.workbookDir(document.id), "workbook.json").readBytes())
        assertEquals("missing-sheet", store.load(document.id).activeWorksheetId)
        assertArrayEquals(original, store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertArrayEquals(parquet, store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
        assertTrue("On launch the visible local tab, actual East 15 and query owner must agree; " +
            "initial missing-sheet text=$initialMissing, visibleId=${initial.document?.activeWorksheetId}, " +
            "querySheet=${initialResult.queryIdentity?.worksheetId}", initiallyCoherent)
        assertFalse(initialMissing)
        assertEquals(listOf(listOf("East", "15")), textRows(initial))
    }

    private fun awaitReady(model: WorkbookViewModel, sheetId: String? = null): WorkbookUiState = runBlocking {
        withTimeout(20_000) {
            model.state.first { state ->
                val ready = state.query as? WorksheetQueryState.Ready
                return@first !state.loading && state.loadError == null && state.query is WorksheetQueryState.Ready &&
                    (sheetId == null || (state.document?.activeWorksheetId == sheetId &&
                        ready?.result?.queryIdentity?.worksheetId == sheetId))
            }
        }
    }

    private fun textRows(state: WorkbookUiState): List<List<String?>> =
        (state.query as WorksheetQueryState.Ready).result.result.rows.map { row -> row.map { it?.toString() } }

    private fun recordCapture(phase: String, state: WorkbookUiState, missing: Boolean) {
        instrumentation.waitForIdleSync()
        val observation = JSONObject()
        scenario!!.onActivity { activity ->
            val metrics = activity.getSystemService(WindowManager::class.java).currentWindowMetrics
            val configuration = activity.resources.configuration
            val preferences = app.displayPreferences.state.value
            val effectiveAppearance = if (preferences.appearance.name == "System") {
                if ((configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "Dark" else "Light"
            } else preferences.appearance.name
            observation.put("windowBoundsPx", metrics.bounds.toShortString())
                .put("windowWidthPx", metrics.bounds.width()).put("windowHeightPx", metrics.bounds.height())
                .put("decorWidthPx", activity.window.decorView.width).put("decorHeightPx", activity.window.decorView.height)
                .put("screenWidthDp", configuration.screenWidthDp).put("screenHeightDp", configuration.screenHeightDp)
                .put("densityDpi", configuration.densityDpi).put("fontScale", configuration.fontScale.toDouble())
                .put("configuredFontFamilies", "IBM Plex Sans / IBM Plex Mono")
                .put("appearancePreference", preferences.appearance.name).put("densityPreference", preferences.density.name)
                .put("effectiveAppearance", effectiveAppearance)
                .put("configurationNightMode", configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                .put("captionInsetsPx", metrics.windowInsets.getInsets(WindowInsets.Type.captionBar()).toString())
                .put("taskId", activity.taskId)
        }
        val result = (state.query as WorksheetQueryState.Ready).result
        observation.put("phase", phase).put("workbookId", document.id)
            .put("canonicalActiveWorksheetId", document.activeWorksheetId)
            .put("visibleActiveWorksheetId", state.document?.activeWorksheetId ?: JSONObject.NULL)
            .put("queryWorksheetId", result.queryIdentity?.worksheetId ?: JSONObject.NULL)
            .put("queryRows", result.result.rows.toString()).put("missingSheetTextVisible", missing)
            .put("dataSourceSurfaceVisible", device.hasObject(By.text(DATA_SOURCE_SUMMARY)))
        val hierarchy = File(evidence, "$phase.xml")
        device.dumpWindowHierarchy(hierarchy)
        val capture = File(evidence, "$phase.png")
        assertTrue("Actual native screenshot must be available", device.takeScreenshot(capture))
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(capture.absolutePath, size)
        assertTrue(size.outWidth > 0 && size.outHeight > 0)
        val hash = MessageDigest.getInstance("SHA-256").digest(capture.readBytes()).joinToString("") { "%02x".format(it) }
        observation.put("capturePath", capture.absolutePath).put("captureSha256", hash)
            .put("captureWidthPx", size.outWidth).put("captureHeightPx", size.outHeight)
        File(evidence, "$phase.json").writeText(observation.toString(2) + "\n")
        Log.i(TAG, "CAPTURE ${observation.toString()}")
    }

    companion object {
        private const val TAG = "WorkbookSelectionUiTest"
        private const val UI_TIMEOUT_MS = 5_000L
        private const val MISSING_SHEET_TEXT = "This worksheet no longer exists."
        private const val DATA_SOURCE_SUMMARY = "Local snapshot and field schema"
    }
}
