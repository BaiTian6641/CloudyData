package io.github.baitian6641.cloudydata

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.MarkType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TableSelectionTest {
    private var workbookId: String? = null

    @After
    fun tearDown() {
        workbookId?.let { id ->
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CloudyDataApp
            app.workbookStore.workbookDir(id).deleteRecursively()
        }
    }

    @Test
    fun tablePointerModifiersToggleAndExtendFromTheAnchor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = createTableWorkbook(instrumentation)
        launchWorkbook(instrumentation, id).use {
            instrumentation.waitForIdleSync()
            val device = UiDevice.getInstance(instrumentation)
            assertNotNull(
                "result table did not render",
                device.wait(Until.findObject(By.text("2026-01-01")), 8_000),
            )

            clickWithMeta(instrumentation, "2026-01-01", 0)
            assertNotNull(
                "plain table click did not select one row",
                device.wait(Until.findObject(By.text("1 selected mark")), 4_000),
            )

            clickWithMeta(
                instrumentation,
                "2026-03-01",
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
            )
            assertNotNull(
                "Shift-click did not extend a three-row range",
                device.wait(Until.findObject(By.text("3 selected marks")), 4_000),
            )

            clickWithMeta(instrumentation, "2026-04-01", KeyEvent.META_CTRL_ON)
            assertNotNull(
                "Ctrl-click did not add the fourth row",
                device.wait(Until.findObject(By.text("4 selected marks")), 4_000),
            )

            clickWithMeta(instrumentation, "2026-01-01", KeyEvent.META_CTRL_ON)
            assertNotNull(
                "second Ctrl-click did not toggle the anchor row off",
                device.wait(Until.findObject(By.text("3 selected marks")), 4_000),
            )

            clickWithMeta(instrumentation, "2026-02-01", 0)
            assertNotNull(
                "plain click did not replace the modified selection",
                device.wait(Until.findObject(By.text("1 selected mark")), 4_000),
            )
        }
    }

    @Test
    fun tableAccessibilityActionsAddAndExtendSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = createTableWorkbook(instrumentation)
        launchWorkbook(instrumentation, id).use {
            instrumentation.waitForIdleSync()
            val device = UiDevice.getInstance(instrumentation)
            assertNotNull(
                "result table did not render",
                device.wait(Until.findObject(By.text("2026-01-01")), 8_000),
            )
            clickWithMeta(instrumentation, "2026-04-01", 0)
            assertNotNull(device.wait(Until.findObject(By.text("1 selected mark")), 4_000))

            assertTrue(
                "missing Add row to selection action",
                performCustomAction(instrumentation, "2026-04-01", "Add row to selection"),
            )
            assertTrue(
                "missing Add row action on a second row",
                performCustomAction(instrumentation, "2026-03-01", "Add row to selection"),
            )
            assertNotNull(
                "Add row to selection should not remove an already-selected row",
                device.wait(Until.findObject(By.text("2 selected marks")), 4_000),
            )

            assertTrue(
                "missing Select range action",
                performCustomAction(instrumentation, "2026-02-01", "Select range to this row"),
            )
            assertNotNull(
                "accessibility range did not extend from the anchor",
                device.wait(Until.findObject(By.text("3 selected marks")), 4_000),
            )
        }
    }

    @Test
    fun canceledModifiedPressDoesNotLeakIntoAccessibilityActivation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = createTableWorkbook(instrumentation)
        launchWorkbook(instrumentation, id).use {
            instrumentation.waitForIdleSync()
            val device = UiDevice.getInstance(instrumentation)
            assertNotNull(
                "result table did not render",
                device.wait(Until.findObject(By.text("2026-01-01")), 8_000),
            )
            clickWithMeta(instrumentation, "2026-01-01", 0)
            assertNotNull(device.wait(Until.findObject(By.text("1 selected mark")), 4_000))

            cancelDrag(instrumentation, "2026-02-01", KeyEvent.META_CTRL_ON)
            instrumentation.waitForIdleSync()
            assertNotNull(
                "canceled drag should not change the selection",
                device.findObject(By.text("1 selected mark")),
            )

            assertTrue(
                "row did not expose an accessibility click",
                performAccessibilityClick(instrumentation, "2026-02-01"),
            )
            assertTrue(
                "row did not expose a range action after accessibility replace",
                performCustomAction(instrumentation, "2026-03-01", "Select range to this row"),
            )
            assertNotNull(
                "accessibility replace should move the anchor to February before the March range",
                device.wait(Until.findObject(By.text("2 selected marks")), 4_000),
            )
            assertNull(
                "stale Ctrl mode from the canceled drag or an ignored accessibility click",
                device.findObject(By.text("3 selected marks")),
            )
        }
    }

    @Test
    fun viewDataShowsUnderlyingSourceRowsForTheSelectedMark() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = createTableWorkbook(instrumentation)
        launchWorkbook(instrumentation, id).use {
            instrumentation.waitForIdleSync()
            val device = UiDevice.getInstance(instrumentation)
            assertNotNull(
                "result table did not render",
                device.wait(Until.findObject(By.text("2026-01-01")), 8_000),
            )
            clickWithMeta(instrumentation, "2026-01-01", 0)
            assertNotNull(device.wait(Until.findObject(By.text("1 selected mark")), 4_000))

            val viewDataButton = device.wait(Until.findObject(By.text("View data")), 2_000)
            assertNotNull("View data action is missing", viewDataButton)
            viewDataButton!!.click()
            assertNotNull(
                "underlying rows did not load",
                device.wait(Until.findObject(By.text("2 source rows.")), 8_000),
            )
            assertNotNull("underlying North row is missing", device.findObject(By.text("North")))
            assertNotNull("underlying South row is missing", device.findObject(By.text("South")))

            device.findObject(By.text("Close")).click()
            assertNull(
                "View data dialog did not close",
                device.wait(Until.findObject(By.text("2 source rows.")), 2_000),
            )
        }
    }

    @Test
    fun switchingSheetTabQueriesTheVisibleWorksheet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val id = createTableWorkbook(instrumentation)
        val app = instrumentation.targetContext.applicationContext as CloudyDataApp
        val loaded = app.workbookStore.load(id)
        val snapshot = loaded.snapshot!!
        val series = snapshot.columns.first { it.name == "series" }
        val first = loaded.activeWorksheet!!
        val second = first.copy(
            id = UUID.randomUUID().toString(),
            name = "Sheet 2",
            rowFieldId = series.id,
        )
        app.workbookStore.save(
            loaded.copy(
                worksheets = listOf(first, second),
                activeWorksheetId = first.id,
            ),
        )
        launchWorkbook(instrumentation, id).use {
            instrumentation.waitForIdleSync()
            val device = UiDevice.getInstance(instrumentation)
            assertNotNull(
                "first worksheet did not render",
                device.wait(Until.findObject(By.text("2026-01-01")), 8_000),
            )
            val tab = device.wait(Until.findObject(By.text("Sheet 2")), 2_000)
            assertNotNull("Sheet 2 tab is missing", tab)
            tab!!.click()
            assertNotNull(
                "visible worksheet did not requery to series rows",
                device.wait(Until.findObject(By.text("North")), 8_000),
            )
            assertNotNull("South row is missing after tab switch", device.findObject(By.text("South")))
            assertNull(
                "old worksheet result remained under the new tab",
                device.findObject(By.text("2026-01-01")),
            )
        }
    }

    private fun createTableWorkbook(instrumentation: android.app.Instrumentation): String {
        val app = instrumentation.targetContext.applicationContext as CloudyDataApp
        val fixture = File(instrumentation.targetContext.cacheDir, "table-selection-${UUID.randomUUID()}.csv")
        instrumentation.context.assets.open("csv/chart_line_series.csv").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        val document = app.workbookStore.create("Table selection ${UUID.randomUUID()}")
        workbookId = document.id
        val pending = runBlocking {
            CsvImportService(app, app.workbookStore).stageAndInspect(document, Uri.fromFile(fixture))
        }
        val imported = runBlocking {
            CsvImportService(app, app.workbookStore).apply(document, pending)
        }
        val snapshot = imported.snapshot!!
        val date = snapshot.columns.first { it.name == "event_date" }
        val value = snapshot.columns.first { it.name == "value" }
        val sheet = imported.activeWorksheet!!.copy(
            rowFieldId = date.id,
            measureFieldId = value.id,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sortDescending = false,
            seriesFieldId = null,
            filter = null,
        )
        app.workbookStore.save(imported.copy(worksheets = listOf(sheet)))
        return imported.id
    }

    private fun launchWorkbook(
        instrumentation: android.app.Instrumentation,
        id: String,
    ): ActivityScenario<MainActivity> {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_WORKBOOK_ID, id)
        }
        return ActivityScenario.launch(intent)
    }

    private fun performCustomAction(
        instrumentation: android.app.Instrumentation,
        text: String,
        label: String,
    ): Boolean {
        val node = findRowNode(instrumentation, text) ?: return false
        val action = node.actionList.firstOrNull { it.label?.toString() == label } ?: return false
        return node.performAction(action.id)
    }

    private fun performAccessibilityClick(
        instrumentation: android.app.Instrumentation,
        text: String,
    ): Boolean {
        val node = findRowNode(instrumentation, text) ?: return false
        return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findRowNode(
        instrumentation: android.app.Instrumentation,
        text: String,
    ): android.view.accessibility.AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        fun findText(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                findText(child)?.let { return it }
            }
            return null
        }
        var candidate = findText(root)
        while (candidate != null) {
            if (candidate.isClickable) return candidate
            candidate = candidate.parent
        }
        return null
    }

    private fun cancelDrag(
        instrumentation: android.app.Instrumentation,
        text: String,
        metaState: Int,
    ) {
        val node = UiDevice.getInstance(instrumentation).wait(Until.findObject(By.text(text)), 4_000)
            ?: error("Could not find table row '$text'")
        val bounds = node.visibleBounds
        val now = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(
            MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_DOWN,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
                metaState,
            ),
        )
        instrumentation.sendPointerSync(
            MotionEvent.obtain(
                now,
                now + 30,
                MotionEvent.ACTION_MOVE,
                bounds.exactCenterX(),
                bounds.exactCenterY() + 48f,
                metaState,
            ),
        )
        instrumentation.sendPointerSync(
            MotionEvent.obtain(
                now,
                now + 60,
                MotionEvent.ACTION_UP,
                bounds.exactCenterX(),
                bounds.exactCenterY() + 48f,
                metaState,
            ),
        )
        instrumentation.waitForIdleSync()
    }

    private fun clickWithMeta(
        instrumentation: android.app.Instrumentation,
        text: String,
        metaState: Int,
    ) {
        val device = UiDevice.getInstance(instrumentation)
        val node = device.wait(Until.findObject(By.text(text)), 4_000)
            ?: error("Could not find table row '$text'")
        val bounds = node.visibleBounds
        val now = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(
            MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_DOWN,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
                metaState,
            ),
        )
        instrumentation.sendPointerSync(
            MotionEvent.obtain(
                now,
                now + 20,
                MotionEvent.ACTION_UP,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
                metaState,
            ),
        )
        instrumentation.waitForIdleSync()
    }
}
