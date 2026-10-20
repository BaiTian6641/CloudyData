package io.github.baitian6641.cloudydata

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.Xml
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Real native key events and actual imported data; no pointer or direct-focus shortcut. */
@RunWith(AndroidJUnit4::class)
class WorkbookTabKeyboardTest {
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
    private lateinit var model: WorkbookViewModel
    private val selectedFailures = mutableListOf<String>()
    private var createdWorkbookId: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = runBlocking {
        context = instrumentation.targetContext
        app = context.applicationContext as CloudyDataApp
        store = app.workbookStore
        device = UiDevice.getInstance(instrumentation)
        val runId = UUID.randomUUID().toString()
        evidence = File(checkNotNull(context.getExternalFilesDir(null)), "qualification/workbook-tab-keyboard-$runId")
            .apply { check(mkdirs()) }
        val workbook = store.create("Keyboard worksheet selection $runId")
        createdWorkbookId = workbook.id
        input = File(context.cacheDir, "tab-keyboard-$runId.csv").apply {
            writeText("Category,Sales\nEast,10\nWest,20\nEast,5\n")
        }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        east = imported.worksheets.single().copy(name = "East keyboard",
            rowFieldId = category.id, measureFieldId = sales.id, aggregation = Aggregation.SUM,
            markType = MarkType.TABLE, sortDescending = true, chart = ChartOptions(title = "East exact values"),
            filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East"))))
        west = east.copy(id = UUID.randomUUID().toString(), name = "West keyboard",
            chart = ChartOptions(title = "West exact values"),
            filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("West"))))
        document = store.save(imported.copy(worksheets = listOf(east, west), activeWorksheetId = east.id))
    }

    @After
    fun tearDown() {
        scenario?.close()
        instrumentation.waitForIdleSync()
        createdWorkbookId?.let { store.workbookDir(it).deleteRecursively() }
        if (::input.isInitialized) input.delete()
        // Retain external native captures and traces after deleting only this fixture.
    }

    @Test
    fun tabAndShiftTabEnterSelectExactWorksheetWhileFocusAndSelectedStateRemainDistinct() {
        val checkpoint = store.loadCheckpoint(document.id)
        val canonical = File(store.workbookDir(document.id), "workbook.json").readBytes()
        val snapshot = document.snapshot!!
        val original = store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes()
        val parquet = store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WORKBOOK_ID, document.id))
        scenario!!.onActivity { activity ->
            model = ViewModelProvider(activity, WorkbookViewModelFactory(app, store, document.id))
                .get(document.id, WorkbookViewModel::class.java)
        }
        val initial = requireSheet(east.id, "East", "15")
        recordCapture("initial", initial, east.name)

        focusWestWithAtMost24Tabs()
        val westFocused = requireSheet(east.id, "East", "15")
        recordCapture("west-focused", westFocused, east.name)
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        val westSelected = requireSheet(west.id, "West", "20")
        recordCapture("west-selected", westSelected, west.name)

        pressTabAndRequireFocus(east.name, shift = true, phase = "shift-tab-east")
        val eastFocused = requireSheet(west.id, "West", "20")
        recordCapture("east-focused", eastFocused, west.name)
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        val eastSelected = requireSheet(east.id, "East", "15")
        recordCapture("east-selected", eastSelected, east.name)

        pressTabAndRequireFocus(DATA_TAB_LABEL, shift = true, phase = "shift-tab-data")
        val dataFocused = requireSheet(east.id, "East", "15")
        recordCapture("data-focused", dataFocused, east.name)
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        assertTrue("Enter on Data source must show the actual schema surface",
            device.wait(Until.hasObject(By.text(DATA_SOURCE_SUMMARY)), UI_TIMEOUT_MS))
        assertTrue(device.hasObject(By.text("Local Parquet")))
        assertTrue(device.hasObject(By.text("Original preserved")))
        assertTrue(device.hasObject(By.text(snapshot.displayName)))
        assertTrue(device.hasObject(By.text("Schema")))
        assertTrue(device.hasObject(By.text("Category")))
        assertTrue(device.hasObject(By.text("Sales")))
        assertTrue(device.wait(Until.gone(By.text("15")), UI_TIMEOUT_MS))
        val dataSelected = awaitReady(east.id)
        assertEquals(listOf(listOf("East", "15")), textRows(dataSelected))
        recordCapture("data-selected", dataSelected, DATA_TAB_LABEL)

        pressTabAndRequireFocus(east.name, shift = false, phase = "tab-east-from-data")
        assertTrue("Focus alone must leave the Data source surface selected",
            device.hasObject(By.text(DATA_SOURCE_SUMMARY)))
        assertTrue(device.wait(Until.gone(By.text("15")), UI_TIMEOUT_MS))
        val eastFromDataFocused = awaitReady(east.id)
        recordCapture("east-from-data-focused", eastFromDataFocused, DATA_TAB_LABEL)
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        val eastFromDataSelected = requireSheet(east.id, "East", "15")
        recordCapture("east-from-data-selected", eastFromDataSelected, east.name)

        val reopened = store.loadCheckpoint(document.id)
        assertEquals(checkpoint, reopened)
        assertEquals(document.revision, reopened.document.revision)
        assertEquals(east.id, reopened.document.activeWorksheetId)
        assertEquals(checkpoint.receipts, reopened.receipts)
        assertEquals(checkpoint.undo, reopened.undo)
        assertEquals(checkpoint.redo, reopened.redo)
        assertArrayEquals(canonical, File(store.workbookDir(document.id), "workbook.json").readBytes())
        assertArrayEquals(original, store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertArrayEquals(parquet, store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
        Log.i(TAG, "KEYBOARD_PATH_COMPLETE revision=${reopened.document.revision} " +
            "receipts=${reopened.receipts.size} selectedFailures=$selectedFailures evidence=${evidence.absolutePath}")
        // Preserve the complete native keyboard path on the unchanged-production
        // baseline, then independently fail its absent/incorrect selected semantics.
        assertTrue("Only the currently active native tab must report selected=true at every phase: " +
            selectedFailures.joinToString("; "), selectedFailures.isEmpty())
    }

    private fun requireSheet(sheetId: String, category: String, value: String): WorkbookUiState {
        val state = awaitReady(sheetId)
        assertEquals(listOf(listOf(category, value)), textRows(state))
        assertTrue("The actual native worksheet must show $category $value",
            device.wait(Until.hasObject(By.text(value)), UI_TIMEOUT_MS))
        assertTrue(device.wait(Until.gone(By.text(DATA_SOURCE_SUMMARY)), UI_TIMEOUT_MS))
        assertTrue(device.wait(Until.gone(By.text(MISSING_SHEET_TEXT)), UI_TIMEOUT_MS))
        return state
    }

    private fun awaitReady(sheetId: String): WorkbookUiState = runBlocking {
        withTimeout(20_000) {
            model.state.first { state ->
                val ready = state.query as? WorksheetQueryState.Ready
                return@first !state.loading && state.loadError == null &&
                    state.document?.activeWorksheetId == sheetId && ready?.result?.queryIdentity?.worksheetId == sheetId
            }
        }
    }

    private fun textRows(state: WorkbookUiState): List<List<String?>> =
        (state.query as WorksheetQueryState.Ready).result.result.rows.map { row -> row.map { it?.toString() } }

    /** A selectable tab may merge its label onto the focus owner; both shapes name the same native control. */
    private fun tabOwnerSelector(label: String): BySelector {
        // Android may omit the redundant click action on the already-selected
        // Role.Tab owner. Keep its exact label and selected state as the owner
        // boundary; unselected owners must still expose their click action.
        val selectedMergedOwner = By.enabled(true).focusable(true).selected(true).text(label)
        if (device.hasObject(selectedMergedOwner)) return selectedMergedOwner
        val selectedParent = By.enabled(true).focusable(true).selected(true).hasDescendant(By.text(label))
        if (device.hasObject(selectedParent)) return selectedParent
        val mergedOwner = By.enabled(true).clickable(true).focusable(true).text(label)
        return if (device.hasObject(mergedOwner)) mergedOwner
        else By.enabled(true).clickable(true).focusable(true).hasDescendant(By.text(label))
    }

    private fun tabOwner(label: String): UiObject2 {
        val owner = device.findObject(tabOwnerSelector(label))
        assertNotNull("An enabled, focusable native tab owner with a selected state or click action must exist for $label", owner)
        return owner!!
    }

    private fun focusWestWithAtMost24Tabs() {
        recordFocusStep("before-first-tab")
        if (sampleFreshFocus(west.name).matchesTarget) return
        repeat(MAX_INITIAL_TABS) { index ->
            assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            instrumentation.waitForIdleSync()
            val phase = "tab-${index + 1}"
            val poll = pollFreshFocus(west.name, FOCUS_TIMEOUT_MS, phase)
            recordFocusStep("$phase-westFocused-${poll.matched}", poll)
            if (poll.matched) return
        }
        if (sampleFreshFocus(west.name).matchesTarget) return
        recordCapture("west-focus-timeout", model.state.value, east.name)
        instrumentation.uiAutomation.clearCache()
        assertNotNull("West must receive native keyboard focus within $MAX_INITIAL_TABS real Tab presses",
            device.findObject(tabOwnerSelector(west.name).focused(true)))
    }

    private fun pressTabAndRequireFocus(label: String, shift: Boolean, phase: String) {
        val modifiers = if (shift) KeyEvent.META_SHIFT_ON else 0
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_TAB, modifiers))
        instrumentation.waitForIdleSync()
        val poll = pollFreshFocus(label, UI_TIMEOUT_MS, phase)
        recordFocusStep("$phase-targetFocused-${poll.matched}", poll)
        if (!poll.matched) recordCapture("$phase-focus-timeout", model.state.value,
            if (device.hasObject(By.text(DATA_SOURCE_SUMMARY))) DATA_TAB_LABEL
            else if (model.state.value.document?.activeWorksheetId == west.id) west.name else east.name)
        assertTrue("A single ${if (shift) "Shift+Tab" else "Tab"} must focus the adjacent native $label tab", poll.matched)
    }

    private data class FocusSample(val clearCacheResult: Boolean, val description: String, val matchesTarget: Boolean)
    private data class FocusPoll(
        val cached: String,
        val firstFresh: FocusSample,
        val lastFresh: FocusSample,
        val polls: Int,
        val elapsedMs: Long,
        val matched: Boolean,
    )

    private fun descendantTabLabel(node: AccessibilityNodeInfo): String? {
        node.text?.toString()?.takeIf { it in tabLabels() }?.let { return it }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            descendantTabLabel(child)?.let { return it }
        }
        return null
    }

    private fun isNativeTabOwner(node: AccessibilityNodeInfo): Boolean =
        node.isEnabled && node.isFocusable && (node.isClickable || node.isSelected)

    private fun nativeFocusDescription(node: AccessibilityNodeInfo?): String {
        if (node == null) return "<none>"
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val label = if (isNativeTabOwner(node)) descendantTabLabel(node) else null
        var ancestor = node.parent
        var ancestorLabel: String? = null
        var ancestorCount = 0
        while (ancestor != null && ancestorCount++ < 12 && ancestorLabel == null) {
            if (isNativeTabOwner(ancestor)) ancestorLabel = descendantTabLabel(ancestor)
            ancestor = ancestor.parent
        }
        return "class=${node.className} text=${node.text} description=${node.contentDescription} " +
            "bounds=${bounds.toShortString()} clickable=${node.isClickable} focusable=${node.isFocusable} " +
            "enabled=${node.isEnabled} focused=${node.isFocused} selected=${node.isSelected} " +
            "ownerTabLabel=$label ancestorTabLabel=$ancestorLabel"
    }

    private fun sampleFreshFocus(target: String): FocusSample {
        val automation = instrumentation.uiAutomation
        // findFocus consults AccessibilityCache first. Clear immediately before
        // FOCUS_INPUT so this observation measures the live native focus owner.
        val cleared = automation.clearCache()
        val focused = automation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val matches = focused != null && focused.isFocused && isNativeTabOwner(focused) &&
            descendantTabLabel(focused) == target
        return FocusSample(cleared, nativeFocusDescription(focused), matches)
    }

    private fun pollFreshFocus(target: String, timeoutMs: Long, phase: String): FocusPoll {
        val cached = nativeFocusDescription(instrumentation.uiAutomation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
        device.dumpWindowHierarchy(File(evidence, "$phase-cached.xml"))
        val started = SystemClock.uptimeMillis()
        val first = sampleFreshFocus(target)
        var latest = first
        var polls = 1
        while (!latest.matchesTarget) {
            val remaining = timeoutMs - (SystemClock.uptimeMillis() - started)
            if (remaining <= 0) break
            SystemClock.sleep(minOf(FOCUS_POLL_MS, remaining))
            latest = sampleFreshFocus(target)
            polls++
        }
        return FocusPoll(cached, first, latest, polls, SystemClock.uptimeMillis() - started, latest.matchesTarget)
    }

    private fun recordFocusStep(phase: String, poll: FocusPoll? = null) {
        val hierarchy = File(evidence, "$phase.xml")
        val fresh = sampleFreshFocus(west.name)
        device.dumpWindowHierarchy(hierarchy)
        val focused = mutableListOf<String>()
        hierarchy.inputStream().use { inputStream ->
            val parser = Xml.newPullParser().apply { setInput(inputStream, Charsets.UTF_8.name()) }
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "node" &&
                    parser.getAttributeValue(null, "focused") == "true") {
                    fun attribute(name: String) = parser.getAttributeValue(null, name).orEmpty()
                    focused += "class=${attribute("class")} text=${attribute("text")} " +
                        "description=${attribute("content-desc")} bounds=${attribute("bounds")} " +
                        "clickable=${attribute("clickable")} focusable=${attribute("focusable")} " +
                        "selected=${attribute("selected")} enabled=${attribute("enabled")}"
                }
                parser.next()
            }
        }
        val record = "$phase cachedNative=${poll?.cached ?: "<not-polled>"} " +
            "firstFresh=${poll?.firstFresh} lastFresh=${poll?.lastFresh} polls=${poll?.polls} elapsedMs=${poll?.elapsedMs} " +
            "freshBeforeXml=$fresh xmlFocused=${focused.ifEmpty { listOf("<none>") }.joinToString(" || ")} " +
            "hierarchy=${hierarchy.absolutePath}\n"
        File(evidence, "focus-trace.txt").appendText(record)
        Log.i(TAG, "FOCUS_OBSERVATION $record")
    }

    private fun selectedObservations(phase: String, expected: String): JSONArray {
        // Main-thread idle alone does not prove Compose accessibility publication.
        // This exact native-state wait retains the same expectation on the baseline.
        val started = SystemClock.uptimeMillis()
        do {
            instrumentation.uiAutomation.clearCache()
            if (device.findObject(tabOwnerSelector(expected))?.isSelected == true) break
            val remaining = FOCUS_TIMEOUT_MS - (SystemClock.uptimeMillis() - started)
            if (remaining <= 0) break
            SystemClock.sleep(minOf(FOCUS_POLL_MS, remaining))
        } while (true)
        val observations = JSONArray()
        val selected = mutableListOf<String>()
        for (label in tabLabels()) {
            instrumentation.uiAutomation.clearCache()
            val owner = tabOwner(label)
            assertTrue("The native $label tab owner must stay enabled and focusable", owner.isEnabled && owner.isFocusable)
            if (!owner.isSelected) assertTrue("Every unselected native tab must retain its click action: $label", owner.isClickable)
            if (owner.isSelected) selected += label
            observations.put(JSONObject().put("label", label).put("className", owner.className)
                .put("ownerText", owner.text ?: JSONObject.NULL)
                .put("contentDescription", owner.contentDescription ?: JSONObject.NULL)
                .put("focused", owner.isFocused).put("selected", owner.isSelected)
                .put("clickable", owner.isClickable).put("focusable", owner.isFocusable).put("enabled", owner.isEnabled)
                .put("boundsPx", owner.visibleBounds.toShortString()))
        }
        if (selected != listOf(expected)) selectedFailures += "$phase expected=$expected actual=$selected"
        File(evidence, "selected-trace.txt").appendText("$phase expected=$expected actual=$selected\n")
        return observations
    }

    /** Record the platform's role/class/extras without inventing a TalkBack acceptance result. */
    private fun nativeRoleObservations(): JSONArray {
        instrumentation.uiAutomation.clearCache()
        val observations = JSONArray()
        val labels = tabLabels().toSet()
        fun descendantLabel(node: AccessibilityNodeInfo): String? {
            node.text?.toString()?.takeIf { it in labels }?.let { return it }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                descendantLabel(child)?.let { return it }
            }
            return null
        }
        fun visit(node: AccessibilityNodeInfo) {
            if (isNativeTabOwner(node)) {
                val label = descendantLabel(node)
                if (label != null) {
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    observations.put(JSONObject().put("label", label)
                        .put("className", node.className?.toString() ?: JSONObject.NULL)
                        .put("selected", node.isSelected).put("focused", node.isFocused)
                        .put("clickable", node.isClickable).put("focusable", node.isFocusable).put("enabled", node.isEnabled)
                        .put("boundsPx", bounds.toShortString()).put("extras", node.extras.toString())
                        .put("collectionInfo", node.collectionInfo?.toString() ?: JSONObject.NULL)
                        .put("collectionItemInfo", node.collectionItemInfo?.toString() ?: JSONObject.NULL))
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it) }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let { visit(it) }
        return observations
    }

    private fun tabLabels(): List<String> = listOf(DATA_TAB_LABEL, east.name, west.name)

    private fun recordCapture(phase: String, state: WorkbookUiState, expectedSelected: String) {
        instrumentation.waitForIdleSync()
        val observations = selectedObservations(phase, expectedSelected)
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
            .put("queryRows", result.result.rows.toString()).put("expectedSelectedTab", expectedSelected)
            .put("tabs", observations).put("nativeRoleEvidence", nativeRoleObservations())
            .put("dataSourceSurfaceVisible", device.hasObject(By.text(DATA_SOURCE_SUMMARY)))
        instrumentation.uiAutomation.clearCache()
        device.dumpWindowHierarchy(File(evidence, "$phase.xml"))
        val capture = File(evidence, "$phase.png")
        assertTrue("An actual native screenshot must be available", device.takeScreenshot(capture))
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
        private const val TAG = "WorkbookTabKeyboardTest"
        private const val MAX_INITIAL_TABS = 24
        private const val FOCUS_TIMEOUT_MS = 500L
        private const val FOCUS_POLL_MS = 25L
        private const val UI_TIMEOUT_MS = 5_000L
        private const val DATA_TAB_LABEL = "Data source"
        private const val DATA_SOURCE_SUMMARY = "Local snapshot and field schema"
        private const val MISSING_SHEET_TEXT = "This worksheet no longer exists."
    }
}
