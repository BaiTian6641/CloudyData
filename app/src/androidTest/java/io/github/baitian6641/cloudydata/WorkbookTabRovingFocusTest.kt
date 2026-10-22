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
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef
import io.github.baitian6641.cloudydata.ui.SaveState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Real native key events and actual imported data for the qualified roving tab strip.
 *
 * Keyboard focus roves over the tabs while selection changes only on activation.
 * This supersedes the previous per-tab Tab/Shift+Tab keyboard route: only the
 * roving tab is a keyboard focus stop, unselected tabs stay click-actionable, and
 * the sequence leaves the strip for the New worksheet control once the selected
 * tab is restored. One intentional pointer activation verifies that mixed
 * pointer/keyboard use retains the roving focus owner. Endpoint wrap behavior
 * is deliberately not asserted; the sequence never presses an arrow at either
 * end of the strip.
 */
@RunWith(AndroidJUnit4::class)
class WorkbookTabRovingFocusTest {
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
        // Start from the launcher: leftover desktop windows and promo overlays
        // can retain window focus and swallow the real input events under test.
        device.pressHome()
        instrumentation.waitForIdleSync()
        dismissSystemOverlay()
        val runId = UUID.randomUUID().toString()
        evidence = File(checkNotNull(context.getExternalFilesDir(null)), "qualification/workbook-tab-roving-focus-$runId")
            .apply { check(mkdirs()) }
        val workbook = store.create("Roving worksheet focus $runId")
        createdWorkbookId = workbook.id
        input = File(context.cacheDir, "tab-roving-$runId.csv").apply {
            writeText("Category,Sales\nEast,10\nWest,20\nEast,5\n")
        }
        val importer = CsvImportService(context, store)
        val pending = importer.stageAndInspect(workbook, Uri.fromFile(input))
        val imported = importer.apply(workbook, pending)
        val snapshot = imported.snapshot!!
        val category = snapshot.columns.single { it.name == "Category" }
        val sales = snapshot.columns.single { it.name == "Sales" }
        east = imported.worksheets.single().copy(name = "East15",
            rowFieldId = category.id, measureFieldId = sales.id, aggregation = Aggregation.SUM,
            markType = MarkType.TABLE, sortDescending = true, chart = ChartOptions(title = "East15 exact values"),
            filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("East"))))
        west = east.copy(id = UUID.randomUUID().toString(), name = "West20",
            chart = ChartOptions(title = "West20 exact values"),
            filter = MarkFilter(FilterMode.KEEP, category.id, marks = listOf(FilterMark("West"))))
        document = store.save(imported.copy(worksheets = listOf(east, west), activeWorksheetId = east.id))
        val overflow = (1..20).map { index ->
            east.copy(
                id = UUID.randomUUID().toString(),
                name = "Overflow %02d".format(index),
                chart = ChartOptions(title = "Overflow %02d exact values".format(index)),
            )
        }
        document = store.save(document.copy(worksheets = listOf(east, west) + overflow))
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
    fun rovingFocusMovesWithoutSelectionAndTabLeavesOnlyAfterTheSelectedTabIsRestored() {
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
        dismissSystemOverlay()
        val initial = requireSheet(east.id, "East", "15")
        recordCapture("initial", initial, east.name)

        focusEastWithAtMost24Tabs()
        val eastFocusedBounds = tabOwner(east.name).visibleBounds
        stripTopPx = eastFocusedBounds.top
        stripBottomPx = eastFocusedBounds.bottom
        val eastFocused = requireSheet(east.id, "East", "15")
        recordCapture("east-focused", eastFocused, east.name)

        // Right must move only focus: East15 stays selected and query-owned.
        pressArrowAndRequireFocus(KeyEvent.KEYCODE_DPAD_RIGHT, west.name, "west-focused")
        val westFocused = requireSheet(east.id, "East", "15")
        recordCapture("west-focused", westFocused, east.name)

        assertTrue("Native Enter must be delivered to the focused West20 tab",
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        val westSelected = requireSheet(west.id, "West", "20")
        recordCapture("west-selected", westSelected, west.name)

        // Left must move only focus: West20 stays selected and query-owned.
        pressArrowAndRequireFocus(KeyEvent.KEYCODE_DPAD_LEFT, east.name, "east-focused-after-left")
        val eastFocusedAfterLeft = requireSheet(west.id, "West", "20")
        recordCapture("east-focused-after-left", eastFocusedAfterLeft, west.name)

        assertTrue("Native Enter must be delivered to the focused East15 tab",
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        val eastSelected = requireSheet(east.id, "East", "15")
        recordCapture("east-selected", eastSelected, east.name)

        // East15 is selected and roving; the unselected West20 tab is not a Tab stop.
        val eastTabBounds = tabOwner(east.name).visibleBounds
        assertTrue("Native Tab must be delivered from the selected East15 tab",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val overflowPoll = pollFreshFocus(::isOverflowMenuFocus, UI_TIMEOUT_MS, "tab-overflow-menu")
        recordFocusStep("tab-overflow-menu", overflowPoll)
        assertTrue("One Tab after the selected East15 tab must focus the pinned overflow menu control",
            overflowPoll.matched)
        assertTrue("Native Tab must be delivered from the pinned overflow menu control",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val newWorksheetPoll = pollFreshFocus(::isNewWorksheetFocus, UI_TIMEOUT_MS, "tab-new-worksheet")
        recordFocusStep("tab-new-worksheet", newWorksheetPoll)
        assertTrue("A second Tab after the overflow menu control must focus the New worksheet control",
            newWorksheetPoll.matched)
        val eastStillSelected = requireSheet(east.id, "East", "15")
        recordCapture("new-worksheet-focused", eastStillSelected, east.name)
        val newWorksheet = device.findObject(By.desc(NEW_WORKSHEET_LABEL).focused(true))
        assertNotNull("The focused New worksheet control must be an exposed native object", newWorksheet)
        val newWorksheetOwner = newWorksheet!!
        assertTrue("The New worksheet control must stay enabled, focusable and clickable",
            newWorksheetOwner.isEnabled && newWorksheetOwner.isFocusable && newWorksheetOwner.isClickable)
        val newWorksheetBounds = newWorksheetOwner.visibleBounds
        assertTrue("Tab must focus the tab-strip New worksheet control, not an unrelated control: " +
            "newWorksheet=$newWorksheetBounds eastTab=$eastTabBounds",
            newWorksheetBounds.top < eastTabBounds.bottom && newWorksheetBounds.bottom > eastTabBounds.top)

        // Shift+Tab reenters through the pinned overflow menu control; a second
        // Shift+Tab restores the selected East15 tab with no extra Enter.
        assertTrue("Native Shift+Tab must be delivered from the New worksheet control",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
        instrumentation.waitForIdleSync()
        val overflowReturnPoll = pollFreshFocus(::isOverflowMenuFocus, UI_TIMEOUT_MS, "shift-tab-overflow")
        recordFocusStep("shift-tab-overflow", overflowReturnPoll)
        assertTrue("One Shift+Tab must focus the pinned overflow menu control", overflowReturnPoll.matched)
        assertTrue("Native Shift+Tab must be delivered from the pinned overflow menu control",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
        instrumentation.waitForIdleSync()
        val reenteredPoll = pollFreshFocus(::isEastTabFocus, UI_TIMEOUT_MS, "shift-tab-east")
        recordFocusStep("shift-tab-east", reenteredPoll)
        assertTrue("A second Shift+Tab must reenter the selected East15 tab", reenteredPoll.matched)
        val eastReentered = requireSheet(east.id, "East", "15")
        recordCapture("east-reentered", eastReentered, east.name)
        assertTrue("Focus reentry alone must leave exactly East15 selected; no extra Enter may be required",
            device.findObject(tabOwnerSelector(east.name))?.isSelected == true)

        // A pointer activation from the focused strip must keep the roving focus owner;
        // this regression path previously cleared focus when canFocus moved to the clicked tab.
        val westOwner = tabOwner(west.name)
        assertTrue(device.click(westOwner.visibleBounds.centerX(), westOwner.visibleBounds.centerY()))
        val westPointerSelected = requireSheet(west.id, "West", "20")
        val westPointerPoll = pollFreshFocus(::isWestTabFocus, UI_TIMEOUT_MS, "west-pointer-focused")
        recordFocusStep("west-pointer-focused", westPointerPoll)
        assertTrue("Pointer activation from a focused strip must retain focus on the activated West20 tab",
            westPointerPoll.matched)
        recordCapture("west-pointer-selected", westPointerSelected, west.name)

        assertTrue("Native Tab must leave from the pointer-selected West20 tab",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val pointerOverflowPoll = pollFreshFocus(::isOverflowMenuFocus, UI_TIMEOUT_MS, "pointer-tab-overflow-menu")
        recordFocusStep("pointer-tab-overflow-menu", pointerOverflowPoll)
        assertTrue("One Tab after a pointer-selected tab must focus the overflow menu control",
            pointerOverflowPoll.matched)
        assertTrue("Native Tab must leave the overflow menu control",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val pointerNewPoll = pollFreshFocus(::isNewWorksheetFocus, UI_TIMEOUT_MS, "pointer-tab-new-worksheet")
        recordFocusStep("pointer-tab-new-worksheet", pointerNewPoll)
        assertTrue("A second Tab after a pointer-selected tab must focus New worksheet", pointerNewPoll.matched)
        assertTrue("Native Left must reenter the pointer-selected West20 tab from the pinned control",
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        instrumentation.waitForIdleSync()
        val arrowWestPoll = pollFreshFocus(::isWestTabFocus, UI_TIMEOUT_MS, "pointer-arrow-west")
        recordFocusStep("pointer-arrow-west", arrowWestPoll)
        assertTrue("One Left arrow from the pinned New worksheet control must focus West20", arrowWestPoll.matched)
        val westArrowReentered = requireSheet(west.id, "West", "20")
        recordCapture("west-arrow-reentered", westArrowReentered, west.name)
        assertTrue("Native Tab must leave the arrow-reentered West20 tab for the overflow menu control",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val arrowOverflowPoll = pollFreshFocus(::isOverflowMenuFocus, UI_TIMEOUT_MS, "pointer-arrow-tab-overflow-menu")
        recordFocusStep("pointer-arrow-tab-overflow-menu", arrowOverflowPoll)
        assertTrue("Tab must focus the overflow menu control after arrow reentry", arrowOverflowPoll.matched)
        assertTrue("Native Tab must leave the overflow menu control for New worksheet",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val arrowNewPoll = pollFreshFocus(::isNewWorksheetFocus, UI_TIMEOUT_MS, "pointer-arrow-tab-new-worksheet")
        recordFocusStep("pointer-arrow-tab-new-worksheet", arrowNewPoll)
        assertTrue("Tab must focus New worksheet after arrow reentry", arrowNewPoll.matched)
        assertTrue("Native Shift+Tab must return to the pinned overflow menu control",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
        instrumentation.waitForIdleSync()
        val arrowOverflowReturnPoll = pollFreshFocus(::isOverflowMenuFocus, UI_TIMEOUT_MS, "pointer-shift-tab-overflow")
        recordFocusStep("pointer-shift-tab-overflow", arrowOverflowReturnPoll)
        assertTrue("One Shift+Tab must focus the pinned overflow menu control", arrowOverflowReturnPoll.matched)
        assertTrue("Native Shift+Tab must return to the pointer-selected West20 tab",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
        instrumentation.waitForIdleSync()
        val westReturnPoll = pollFreshFocus(::isWestTabFocus, UI_TIMEOUT_MS, "pointer-shift-tab-west")
        recordFocusStep("pointer-shift-tab-west", westReturnPoll)
        assertTrue("A second Shift+Tab must reenter the pointer-selected West20 tab", westReturnPoll.matched)
        val westReentered = requireSheet(west.id, "West", "20")
        recordCapture("west-reentered-after-pointer", westReentered, west.name)

        // Overflow: walk to the far end of the strip so bringIntoView actually
        // scrolls, without changing the selected West20 worksheet and without
        // pressing an arrow at either endpoint.
        var farFocused = westReentered
        repeat(20) { index ->
            val target = "Overflow %02d".format(index + 1)
            val poll = pressArrowAndRequireFocus(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                target,
                "overflow-right-${index + 1}",
            )
            farFocused = requireSheet(west.id, "West", "20")
            assertFocusedTabVisible(target, poll, "overflow-right-${index + 1}")
        }
        recordCapture("overflow-20-focused", farFocused, west.name)
        repeat(21) { index ->
            val target = when {
                index <= 18 -> "Overflow %02d".format(19 - index)
                index == 19 -> west.name
                else -> east.name
            }
            val poll = pressArrowAndRequireFocus(
                KeyEvent.KEYCODE_DPAD_LEFT,
                target,
                "overflow-left-${index + 1}",
            )
            farFocused = requireSheet(west.id, "West", "20")
            assertFocusedTabVisible(target, poll, "overflow-left-${index + 1}")
        }
        recordCapture("overflow-east-focused", farFocused, west.name)

        val reopened = store.loadCheckpoint(document.id)
        assertEquals("The persisted checkpoint must not change", checkpoint, reopened)
        assertEquals("The saved default worksheet must not change", east.id, reopened.document.activeWorksheetId)
        assertEquals(document.activeWorksheetId, reopened.document.activeWorksheetId)
        assertEquals("The workbook revision must not change", checkpoint.document.revision, reopened.document.revision)
        assertEquals("Stored receipts must not change", checkpoint.receipts, reopened.receipts)
        assertEquals("Stored undo history must not change", checkpoint.undo, reopened.undo)
        assertEquals("Stored redo history must not change", checkpoint.redo, reopened.redo)
        assertArrayEquals("The canonical workbook bytes must not change", canonical,
            File(store.workbookDir(document.id), "workbook.json").readBytes())
        assertArrayEquals("The imported CSV bytes must not change", original,
            store.resolveInsideWorkbook(document.id, snapshot.originalFile!!).readBytes())
        assertArrayEquals("The published Parquet bytes must not change", parquet,
            store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).readBytes())
        Log.i(TAG, "ROVING_PATH_COMPLETE revision=${reopened.document.revision} " +
            "receipts=${reopened.receipts.size} undo=${reopened.undo.size} redo=${reopened.redo.size} " +
            "selectedFailures=$selectedFailures evidence=${evidence.absolutePath}")
        assertTrue("Only the selected tab may report selected=true at every phase: " +
            selectedFailures.joinToString("; "), selectedFailures.isEmpty())
    }

    @Test
    fun pinnedNewWorksheetActivatesWithEnterAfterRovingFocus() {
        val checkpoint = store.loadCheckpoint(document.id)
        val canonical = File(store.workbookDir(document.id), "workbook.json").readBytes()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WORKBOOK_ID, document.id))
        scenario!!.onActivity { activity ->
            model = ViewModelProvider(activity, WorkbookViewModelFactory(app, store, document.id))
                .get(document.id, WorkbookViewModel::class.java)
        }
        dismissSystemOverlay()
        val initial = requireSheet(east.id, "East", "15")
        recordCapture("pinned-enter-initial", initial, east.name)
        focusEastWithAtMost24Tabs()
        assertTrue("Native Tab must move from the selected East15 tab to the pinned overflow menu action",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val overflowPoll = pollFreshFocus(::isOverflowMenuFocus, UI_TIMEOUT_MS, "pinned-overflow-focused")
        recordFocusStep("pinned-overflow-focused", overflowPoll)
        assertTrue("The pinned overflow menu action must be focused", overflowPoll.matched)
        assertTrue("Native Tab must move from the overflow menu action to the pinned New worksheet action",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val pinnedPoll = pollFreshFocus(::isNewWorksheetFocus, UI_TIMEOUT_MS, "pinned-enter-focused")
        recordFocusStep("pinned-enter-focused", pinnedPoll)
        assertTrue("The pinned New worksheet action must be focused", pinnedPoll.matched)
        assertTrue("Native Enter must reach the pinned New worksheet action",
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        val added = runBlocking {
            withTimeout(20_000) {
                model.state.first {
                    it.save is SaveState.Saved &&
                        it.document?.worksheets?.size == checkpoint.document.worksheets.size + 1 &&
                        it.document.revision == checkpoint.document.revision + 1
                }
            }
        }
        val addedSheet = added.document!!.worksheets.single {
            it.id !in checkpoint.document.worksheets.map { sheet -> sheet.id }
        }
        assertEquals(addedSheet.id, added.document!!.activeWorksheetId)
        assertTrue("The added worksheet tab must scroll into view",
            device.wait(Until.hasObject(By.text(addedSheet.name)), UI_TIMEOUT_MS))
        assertTrue("The added worksheet tab must be selected by the actual command",
            device.wait(
                Until.hasObject(tabOwnerSelector(addedSheet.name)),
                UI_TIMEOUT_MS,
            ))
        val reopened = store.loadCheckpoint(document.id)
        assertEquals(checkpoint.document.revision + 1, reopened.document.revision)
        assertEquals(checkpoint.document.worksheets.size + 1, reopened.document.worksheets.size)
        assertEquals(checkpoint.document.activeWorksheetId, reopened.document.activeWorksheetId)
        assertEquals(checkpoint.receipts.size + 1, reopened.receipts.size)
        assertTrue("The actual Add Worksheet command must publish canonical bytes",
            !canonical.contentEquals(File(store.workbookDir(document.id), "workbook.json").readBytes()))
    }

    @Test
    fun overflowStripOpensAtTheSelectedWorksheet() {
        val far = document.worksheets.last()
        document = store.save(document.copy(activeWorksheetId = far.id))
        val checkpoint = store.loadCheckpoint(document.id)
        val canonical = File(store.workbookDir(document.id), "workbook.json").readBytes()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WORKBOOK_ID, document.id))
        scenario!!.onActivity { activity ->
            model = ViewModelProvider(activity, WorkbookViewModelFactory(app, store, document.id))
                .get(document.id, WorkbookViewModel::class.java)
        }
        dismissSystemOverlay()
        val opened = requireSheet(far.id, "East", "15")
        recordCapture("overflow-opens-selected-tab", opened, far.name)
        val bounds = tabOwner(far.name).visibleBounds
        val viewportRight = device.findObjects(By.desc(OVERFLOW_MENU_LABEL))
            .map { it.visibleBounds }
            .filter { it.top < bounds.bottom && it.bottom > bounds.top }
            .minOfOrNull { it.left } ?: device.displayWidth
        assertTrue("The selected worksheet tab must be scrolled into view when the workbook opens: $bounds",
            bounds.width() > 0 && bounds.height() > 0 && bounds.left >= 0 &&
                bounds.right <= viewportRight)
        assertTrue(device.findObject(tabOwnerSelector(far.name))?.isSelected == true)
        val reopened = store.loadCheckpoint(document.id)
        assertEquals(checkpoint, reopened)
        assertEquals(far.id, reopened.document.activeWorksheetId)
        assertArrayEquals(canonical, File(store.workbookDir(document.id), "workbook.json").readBytes())
    }

    @Test
    fun overflowMenuSelectsClippedTabAndCloseReturnsFocusToNeighbor() {
        val checkpoint = store.loadCheckpoint(document.id)
        val canonical = File(store.workbookDir(document.id), "workbook.json").readBytes()
        val overflow20 = document.worksheets.single { it.name == "Overflow 20" }
        val overflow19 = document.worksheets.single { it.name == "Overflow 19" }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WORKBOOK_ID, document.id))
        scenario!!.onActivity { activity ->
            model = ViewModelProvider(activity, WorkbookViewModelFactory(app, store, document.id))
                .get(document.id, WorkbookViewModel::class.java)
        }
        dismissSystemOverlay()
        val initial = requireSheet(east.id, "East", "15")
        recordCapture("menu-initial", initial, east.name)
        dismissSystemOverlay()

        // Keyboard route: Tab reaches the pinned overflow action, Enter opens the
        // menu, and Escape dismisses it without changing the persisted workbook.
        focusEastWithAtMost24Tabs()
        assertTrue("Native Tab must reach the pinned worksheet overflow menu action",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val overflowPoll = pollFreshFocus(::isOverflowMenuFocus, UI_TIMEOUT_MS, "menu-tab-overflow")
        recordFocusStep("menu-tab-overflow", overflowPoll)
        assertTrue("The pinned worksheet overflow menu action must be focused", overflowPoll.matched)
        assertTrue("Native Enter must open the worksheet overflow menu",
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        val opened = device.wait(
            Until.hasObject(menuOpenSelector()),
            UI_TIMEOUT_MS,
        )
        recordFocusStep("menu-opened-keyboard")
        assertTrue("Enter on the pinned action must open the worksheet overflow menu", opened)
        // Keyboard: arrows move through the open menu entry by entry; Enter
        // activates the focused West20 row and focus returns to its tab. Wait
        // for the popup to hold key focus before sending arrows.
        val menuRowFocus = By.focused(true).clickable(true).hasDescendant(By.text("Close"))
        if (!device.wait(Until.hasObject(menuRowFocus), 3_000)) {
            assertTrue("Native Down must seed focus inside the open overflow menu",
                device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
            SystemClock.sleep(200)
            instrumentation.waitForIdleSync()
        }
        val westRowSelector = By.clickable(true).focused(true).hasDescendant(By.text(west.name))
        instrumentation.uiAutomation.clearCache()
        var westRowFocused = device.hasObject(westRowSelector)
        var downSteps = 0
        while (!westRowFocused && downSteps++ < 8) {
            assertTrue("Native Down must be delivered inside the open overflow menu",
                device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
            SystemClock.sleep(250)
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.clearCache()
            westRowFocused = device.hasObject(westRowSelector)
        }
        assertTrue("Arrow keys must reach the West20 overflow menu entry", westRowFocused)
        assertTrue("Native Enter must activate the focused West20 overflow menu entry",
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        val keyboardSelected = requireSheet(west.id, "West", "20")
        recordCapture("menu-keyboard-selected-west", keyboardSelected, west.name)
        val keyboardSelectedFocus = pollFreshFocus(::isWestTabFocus, UI_TIMEOUT_MS, "menu-keyboard-selected-focus")
        recordFocusStep("menu-keyboard-selected-focus", keyboardSelectedFocus)
        assertTrue("Enter selection in the overflow menu must return focus to the West20 tab",
            keyboardSelectedFocus.matched)
        // Back on the strip: Tab reaches the pinned action again, Enter reopens,
        // and Escape dismisses the menu without changing the persisted workbook.
        assertTrue("Native Tab must reach the pinned worksheet overflow menu action again",
            device.pressKeyCode(KeyEvent.KEYCODE_TAB))
        instrumentation.waitForIdleSync()
        val reopenPoll = pollFreshFocus(::isOverflowMenuFocus, UI_TIMEOUT_MS, "menu-tab-overflow-again")
        recordFocusStep("menu-tab-overflow-again", reopenPoll)
        assertTrue("The pinned worksheet overflow menu action must be focused again", reopenPoll.matched)
        assertTrue("Native Enter must reopen the worksheet overflow menu",
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER))
        assertTrue("Enter on the pinned action must reopen the worksheet overflow menu",
            device.wait(Until.hasObject(menuOpenSelector()), UI_TIMEOUT_MS))
        assertTrue("Native Escape must dismiss the worksheet overflow menu",
            device.pressKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertTrue("Escape must close the worksheet overflow menu",
            device.wait(Until.gone(menuOpenSelector()), UI_TIMEOUT_MS))
        assertEquals("Menu keyboard interaction must not persist anything",
            checkpoint, store.loadCheckpoint(document.id))

        // Pointer route: activate the clipped Overflow 20 entry from the menu;
        // selection changes and focus returns to the newly visible tab.
        openOverflowMenu()
        clickMenuEntry(overflow20.name)
        val selected = requireSheet(overflow20.id, "East", "15")
        recordCapture("menu-selected-overflow-20", selected, overflow20.name)
        val selectedFocus = pollFreshFocus({ focusedTabLabel(it) == overflow20.name },
            UI_TIMEOUT_MS, "menu-selected-focus")
        recordFocusStep("menu-selected-focus", selectedFocus)
        assertTrue("Selecting a clipped tab from the overflow menu must return focus to that tab",
            selectedFocus.matched)

        // Closing the unselected East15 keeps the Overflow 20 selection and focus.
        openOverflowMenu()
        clickMenuEntryClose(east.name)
        val afterEastRemoval = awaitSavedDocument(checkpoint.document.revision + 1) { doc ->
            doc.worksheets.none { it.id == east.id }
        }
        assertEquals(document.worksheets.filterNot { it.id == east.id }, afterEastRemoval.worksheets)
        assertEquals(overflow20.id, afterEastRemoval.activeWorksheetId)
        val keptFocus = pollFreshFocus({ focusedTabLabel(it) == overflow20.name },
            UI_TIMEOUT_MS, "menu-close-unselected-focus")
        recordFocusStep("menu-close-unselected-focus", keptFocus)
        assertTrue("Closing an unselected worksheet must return focus to the selected tab",
            keptFocus.matched)

        // Closing the selected Overflow 20 moves selection and focus to the
        // Overflow 19 neighbor.
        openOverflowMenu()
        clickMenuEntryClose(overflow20.name)
        val afterSelectedRemoval = awaitSavedDocument(afterEastRemoval.revision + 1) { doc ->
            doc.worksheets.none { it.id == overflow20.id }
        }
        assertEquals(afterEastRemoval.worksheets.filterNot { it.id == overflow20.id },
            afterSelectedRemoval.worksheets)
        assertEquals(overflow19.id, afterSelectedRemoval.activeWorksheetId)
        val neighbor = requireSheet(overflow19.id, "East", "15")
        recordCapture("menu-close-selected-focus-neighbor", neighbor, overflow19.name)
        val neighborFocus = pollFreshFocus({ focusedTabLabel(it) == overflow19.name },
            UI_TIMEOUT_MS, "menu-close-selected-focus")
        recordFocusStep("menu-close-selected-focus", neighborFocus)
        assertTrue("Closing the selected worksheet must return focus to the newly selected neighbor",
            neighborFocus.matched)

        // Durable receipts, undo history, and the canonical byte oracle.
        val reopened = store.loadCheckpoint(document.id)
        // The persisted activeWorksheetId is the canonical reopen hint: East's
        // removal moved it to West; the view-local Overflow selections are not persisted.
        assertEquals(west.id, reopened.document.activeWorksheetId)
        assertEquals(afterSelectedRemoval.copy(activeWorksheetId = west.id), reopened.document)
        assertEquals(checkpoint.receipts.size + 2, reopened.receipts.size)
        val removes = reopened.receipts.takeLast(2).map { it.command }
        assertEquals(listOf(east.id, overflow20.id),
            removes.map { (it as WorkbookCommand.RemoveWorksheet).worksheetId })
        assertEquals(listOf(checkpoint.document.revision, checkpoint.document.revision + 1),
            removes.map { it.expectedRevision })
        assertEquals("Each removal must snapshot its predecessor for undo",
            listOf(checkpoint.document, afterEastRemoval.copy(activeWorksheetId = west.id)),
            reopened.undo.takeLast(2))
        val bytes = File(store.workbookDir(document.id), "workbook.json").readBytes()
        assertFalse("The removals must publish new canonical bytes", canonical.contentEquals(bytes))
        assertEquals(reopened.document, WorkbookDocument.fromJson(JSONObject(bytes.toString(Charsets.UTF_8))))
    }

    private fun dismissSystemOverlay() {
        // Transient system promo overlays steal window focus and would
        // otherwise swallow the real key/pointer events under test.
        var guard = 0
        while (guard++ < 5) {
            val dismiss = device.findObject(By.textContains("Dismiss overlay")) ?: break
            dismiss.click()
            instrumentation.waitForIdleSync()
        }
    }

    private fun closeTextSelector(): BySelector = By.pkg(context.packageName).text("Close")

    private fun menuOpenSelector(): BySelector = closeTextSelector()

    private fun openOverflowMenu() {
        val button = device.wait(Until.findObject(By.desc(OVERFLOW_MENU_LABEL)), UI_TIMEOUT_MS)
        assertNotNull("The pinned worksheet overflow menu action must exist", button)
        button!!.click()
        var opened = device.wait(Until.hasObject(menuOpenSelector()), 1_500)
        if (!opened) {
            // A pointer tap can be absorbed while a previous popup settles;
            // retry once against a fresh node before failing.
            device.findObject(By.desc(OVERFLOW_MENU_LABEL))?.click()
            opened = device.wait(Until.hasObject(menuOpenSelector()), UI_TIMEOUT_MS)
        }
        assertTrue("The overflow menu must list worksheets with close controls", opened)
    }

    /** Menu entry labels sit left of a Close control on the same popup row. */
    private fun findMenuLabel(label: String): UiObject2? {
        val closes = device.findObjects(closeTextSelector())
        if (closes.isEmpty()) return null
        return device.findObjects(By.pkg(context.packageName).text(label)).firstOrNull { node ->
            closes.any { close ->
                kotlin.math.abs(close.visibleBounds.centerY() - node.visibleBounds.centerY()) < 30 &&
                    close.visibleBounds.left - node.visibleBounds.right in -20..300
            }
        }
    }

    /** Find a menu entry label, swiping inside the popup bounds until it renders. */
    private fun scrollMenuIntoView(label: String): UiObject2? {
        findMenuLabel(label)?.let { return it }
        // The menu list is only scrollable when entries overflow the popup height.
        fun swipe(down: Boolean) {
            val list = device.findObject(By.scrollable(true).hasDescendant(closeTextSelector())) ?: return
            val bounds = list.visibleBounds
            if (down) {
                device.swipe(bounds.centerX(), bounds.centerY() - 300, bounds.centerX(), bounds.centerY() + 300, 15)
            } else {
                device.swipe(bounds.centerX(), bounds.centerY() + 300, bounds.centerX(), bounds.centerY() - 300, 15)
            }
            instrumentation.waitForIdleSync()
        }
        var guard = 0
        while (guard++ < 20) {
            swipe(down = false)
            findMenuLabel(label)?.let { return it }
        }
        guard = 0
        while (guard++ < 20) {
            swipe(down = true)
            findMenuLabel(label)?.let { return it }
        }
        return null
    }

    private fun clickMenuEntry(label: String) {
        val item = scrollMenuIntoView(label)
        assertNotNull("The overflow menu must expose the clipped $label worksheet", item)
        item!!.click()
    }

    private fun clickMenuEntryClose(label: String) {
        val labelNode = scrollMenuIntoView(label)
        assertNotNull("The overflow menu must expose the $label close control", labelNode)
        val rowCenterY = labelNode!!.visibleBounds.centerY()
        val close = device.findObjects(closeTextSelector())
            .firstOrNull { kotlin.math.abs(it.visibleBounds.centerY() - rowCenterY) < 30 }
        assertNotNull("The $label overflow entry must expose its close control", close)
        close!!.click()
    }

    private fun awaitSavedDocument(revision: Long, predicate: (WorkbookDocument) -> Boolean): WorkbookDocument =
        runBlocking {
            withTimeout(20_000) {
                model.state.first { state ->
                    val doc = state.document
                    state.save is SaveState.Saved && doc != null && doc.revision == revision && predicate(doc)
                }.document!!
            }
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

    private fun focusEastWithAtMost24Tabs() {
        recordFocusStep("before-first-tab")
        if (sampleFreshFocus(::isEastTabFocus).matchesTarget) return
        repeat(MAX_INITIAL_TABS) { index ->
            assertTrue("Native Tab must be delivered while entering the tab strip",
                device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            instrumentation.waitForIdleSync()
            val phase = "tab-${index + 1}"
            val poll = pollFreshFocus(::isEastTabFocus, FOCUS_TIMEOUT_MS, phase)
            recordFocusStep("$phase-eastFocused-${poll.matched}", poll)
            if (poll.matched) return
            if (sampleFreshFocus(::isNewWorksheetFocus).matchesTarget &&
                device.findObject(tabOwnerSelector(east.name))?.isSelected == true
            ) {
                // Chrome traversal can overshoot to the New worksheet exit while
                // accessibility focus publication is racing. Use the group's own
                // Shift+Tab route back through the overflow menu stop to the
                // selected East tab within the same bounded key budget; this does
                // not direct-focus any control.
                assertTrue("Native Shift+Tab must be available after an overshoot to New worksheet",
                    device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
                instrumentation.waitForIdleSync()
                pollFreshFocus(::isOverflowMenuFocus, FOCUS_TIMEOUT_MS, "$phase-shift-tab-overflow")
                assertTrue("Native Shift+Tab must be available from the overflow menu control",
                    device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
                instrumentation.waitForIdleSync()
                val recoveryPoll = pollFreshFocus(::isEastTabFocus, FOCUS_TIMEOUT_MS, "$phase-shift-tab-east")
                recordFocusStep("$phase-shift-tab-east-eastFocused-${recoveryPoll.matched}", recoveryPoll)
                if (recoveryPoll.matched) return
            } else if (sampleFreshFocus(::isOverflowMenuFocus).matchesTarget &&
                device.findObject(tabOwnerSelector(east.name))?.isSelected == true
            ) {
                assertTrue("Native Shift+Tab must be available after an overshoot to the overflow menu control",
                    device.pressKeyCode(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
                instrumentation.waitForIdleSync()
                val recoveryPoll = pollFreshFocus(::isEastTabFocus, FOCUS_TIMEOUT_MS, "$phase-overflow-shift-tab-east")
                recordFocusStep("$phase-overflow-shift-tab-east-eastFocused-${recoveryPoll.matched}", recoveryPoll)
                if (recoveryPoll.matched) return
            }
        }
        if (sampleFreshFocus(::isEastTabFocus).matchesTarget) return
        val finalSample = sampleFreshFocus(::isEastTabFocus)
        recordFocusStep("after-last-tab-eastFocused-${finalSample.matchesTarget}")
        recordCapture("east-focus-timeout", model.state.value, east.name)
        assertTrue("East15 must receive native keyboard focus within $MAX_INITIAL_TABS real Tab presses; " +
            "last native focus=${finalSample.description}", finalSample.matchesTarget)
    }

    private fun isEastTabFocus(node: AccessibilityNodeInfo): Boolean = focusedTabLabel(node) == east.name

    private fun isWestTabFocus(node: AccessibilityNodeInfo): Boolean = focusedTabLabel(node) == west.name

    private var stripTopPx: Int = 0
    private var stripBottomPx: Int = 0

    private fun assertFocusedTabVisible(label: String, poll: FocusPoll, phase: String) {
        val bounds = poll.lastFresh.bounds
        assertNotNull("Focused $label must expose native bounds at $phase", bounds)
        val actual = bounds!!
        val viewportRight = device.findObjects(By.desc(OVERFLOW_MENU_LABEL))
            .map { it.visibleBounds }
            .filter { it.top >= stripTopPx && it.bottom <= stripBottomPx }
            .minOfOrNull { it.left } ?: device.displayWidth
        assertTrue("Focused $label must be fully inside the horizontal tab viewport at $phase: $actual",
            actual.width() > 0 && actual.height() > 0 &&
                actual.top >= stripTopPx && actual.bottom <= stripBottomPx &&
                actual.left >= 0 && actual.right <= viewportRight)
        val owner = tabOwner(label)
        assertTrue("Focused $label must remain visible to the user at $phase",
            owner.visibleBounds.width() > 0 && owner.visibleBounds.height() > 0)
    }

    private fun pressArrowAndRequireFocus(keyCode: Int, label: String, phase: String): FocusPoll {
        assertTrue("Native ${if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) "Right" else "Left"} must be delivered " +
            "to the tab strip", device.pressKeyCode(keyCode))
        instrumentation.waitForIdleSync()
        val poll = pollFreshFocus({ focusedTabLabel(it) == label }, UI_TIMEOUT_MS, phase)
        recordFocusStep("$phase-targetFocused-${poll.matched}", poll)
        if (!poll.matched) recordCapture("$phase-focus-timeout", model.state.value,
            if (model.state.value.document?.activeWorksheetId == west.id) west.name else east.name)
        assertTrue("One arrow press must focus $label without activating it", poll.matched)
        return poll
    }

    private data class FocusSample(
        val clearCacheResult: Boolean,
        val description: String,
        val label: String?,
        val bounds: Rect?,
        val matchesTarget: Boolean,
    )

    private data class FocusPoll(
        val cached: String,
        val firstFresh: FocusSample,
        val lastFresh: FocusSample,
        val polls: Int,
        val elapsedMs: Long,
        val matched: Boolean,
    )

    /** Resolve the native tab label owning the focused node, tolerating a merged or child focus node. */
    private fun focusedTabLabel(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        if (isNativeTabOwner(node)) descendantTabLabel(node)?.let { return it }
        var ancestor = node.parent
        var depth = 0
        while (ancestor != null && depth++ < 12) {
            if (isNativeTabOwner(ancestor)) descendantTabLabel(ancestor)?.let { return it }
            ancestor = ancestor.parent
        }
        return null
    }

    private fun descendantTabLabel(node: AccessibilityNodeInfo): String? {
        node.text?.toString()?.takeIf { it in tabLabels() }?.let { return it }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            descendantTabLabel(child)?.let { return it }
        }
        return null
    }

    /**
     * Roving tabs keep their role/selection/click semantics; only the roving tab is a
     * keyboard focus stop. Focusability must not be required of every tab owner.
     */
    private fun isNativeTabOwner(node: AccessibilityNodeInfo): Boolean =
        node.isEnabled && (node.isClickable || node.isSelected)

    private fun isNewWorksheetFocus(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth++ < 12) {
            if (current.contentDescription?.toString() == NEW_WORKSHEET_LABEL && current.isClickable) return true
            current = current.parent
        }
        return false
    }

    private fun isOverflowMenuFocus(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth++ < 12) {
            if (current.contentDescription?.toString() == OVERFLOW_MENU_LABEL && current.isClickable) return true
            current = current.parent
        }
        return false
    }

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

    private fun sampleFreshFocus(isTarget: (AccessibilityNodeInfo) -> Boolean): FocusSample {
        val automation = instrumentation.uiAutomation
        // findFocus consults AccessibilityCache first. Clear immediately before
        // FOCUS_INPUT so this observation measures the live native focus owner.
        val cleared = automation.clearCache()
        val focused = automation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val matches = focused != null && focused.isFocused && isTarget(focused)
        val bounds = focused?.let { node -> Rect().also { node.getBoundsInScreen(it) } }
        return FocusSample(cleared, nativeFocusDescription(focused), focused?.let(::focusedTabLabel), bounds, matches)
    }

    private fun pollFreshFocus(
        isTarget: (AccessibilityNodeInfo) -> Boolean,
        timeoutMs: Long,
        phase: String,
    ): FocusPoll {
        val cached = nativeFocusDescription(instrumentation.uiAutomation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
        device.dumpWindowHierarchy(File(evidence, "$phase-cached.xml"))
        val started = SystemClock.uptimeMillis()
        val first = sampleFreshFocus(isTarget)
        var latest = first
        var polls = 1
        while (!latest.matchesTarget) {
            val remaining = timeoutMs - (SystemClock.uptimeMillis() - started)
            if (remaining <= 0) break
            SystemClock.sleep(minOf(FOCUS_POLL_MS, remaining))
            latest = sampleFreshFocus(isTarget)
            polls++
        }
        return FocusPoll(cached, first, latest, polls, SystemClock.uptimeMillis() - started, latest.matchesTarget)
    }

    private fun recordFocusStep(phase: String, poll: FocusPoll? = null) {
        val hierarchy = File(evidence, "$phase.xml")
        val fresh = sampleFreshFocus { false }
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

    /**
     * A selectable tab may merge its label onto the focus owner, and the selected
     * Role.Tab owner may omit its redundant click action. Roving semantics make only
     * the roving tab focusable, so the owner boundary is selected-or-clickable.
     */
    private fun tabOwnerSelector(label: String): BySelector {
        val selectedMergedOwner = By.enabled(true).selected(true).text(label)
        if (device.hasObject(selectedMergedOwner)) return selectedMergedOwner
        val selectedParent = By.enabled(true).selected(true).hasDescendant(By.text(label))
        if (device.hasObject(selectedParent)) return selectedParent
        val mergedOwner = By.enabled(true).clickable(true).text(label)
        return if (device.hasObject(mergedOwner)) mergedOwner
        else By.enabled(true).clickable(true).hasDescendant(By.text(label))
    }

    private fun tabOwner(label: String): UiObject2 {
        val owner = device.findObject(tabOwnerSelector(label))
        assertNotNull("An enabled native tab owner with a selected state or click action must exist for $label", owner)
        return owner!!
    }

    private fun selectedObservations(phase: String, expected: String): JSONArray {
        // Main-thread idle alone does not prove Compose accessibility publication.
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
            val owner = device.findObject(tabOwnerSelector(label))
            if (owner == null) {
                // A horizontally overflowed tab is legitimately absent from the
                // accessibility tree. The canonical one-selected and query
                // oracles still guard selection; only visible owners can assert
                // the native selected bit.
                observations.put(JSONObject().put("label", label).put("missing", true))
                continue
            }
            assertTrue("The native $label tab owner must stay enabled", owner.isEnabled)
            if (!owner.isSelected) {
                // Only the roving tab is required to be focusable; every unselected
                // tab must still expose its native activation action.
                assertTrue("Every unselected native tab must retain its click action: $label", owner.isClickable)
            }
            if (owner.isSelected) selected += label
            observations.put(JSONObject().put("label", label).put("className", owner.className)
                .put("ownerText", owner.text ?: JSONObject.NULL)
                .put("contentDescription", owner.contentDescription ?: JSONObject.NULL)
                .put("focused", owner.isFocused).put("selected", owner.isSelected)
                .put("clickable", owner.isClickable).put("focusable", owner.isFocusable).put("enabled", owner.isEnabled)
                .put("boundsPx", owner.visibleBounds.toShortString()))
        }
        val expectedVisible = device.findObject(tabOwnerSelector(expected)) != null
        if (selected != listOf(expected) && (selected.isNotEmpty() || expectedVisible)) {
            selectedFailures += "$phase expected=$expected actual=$selected expectedVisible=$expectedVisible"
        }
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

    private fun tabLabels(): List<String> = listOf(DATA_TAB_LABEL) + document.worksheets.map { it.name }

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
        private const val TAG = "WorkbookTabRovingFocusTest"
        private const val MAX_INITIAL_TABS = 24
        private const val FOCUS_TIMEOUT_MS = 500L
        private const val FOCUS_POLL_MS = 25L
        private const val UI_TIMEOUT_MS = 5_000L
        private const val DATA_TAB_LABEL = "Data source"
        private const val DATA_SOURCE_SUMMARY = "Local snapshot and field schema"
        private const val MISSING_SHEET_TEXT = "This worksheet no longer exists."
        private const val NEW_WORKSHEET_LABEL = "New worksheet"
        private const val OVERFLOW_MENU_LABEL = "Worksheet overflow menu"
    }
}
