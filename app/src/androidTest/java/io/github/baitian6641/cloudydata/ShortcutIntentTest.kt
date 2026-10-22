package io.github.baitian6641.cloudydata

import android.content.Intent
import android.os.SystemClock
import android.system.Os
import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import io.github.baitian6641.cloudydata.ui.SaveState
import io.github.baitian6641.cloudydata.ui.WorkbookViewModel
import io.github.baitian6641.cloudydata.ui.WorkbookViewModelFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutIntentTest {
    private var workbookId: String? = null

    @Test
    fun ctrlSRetriesAFailedEditFromTheActualWorkbookKeyboard() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as CloudyDataApp
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        val document = app.workbookStore.create("Shortcut retry ${UUID.randomUUID()}")
        workbookId = document.id
        val directory = app.workbookStore.workbookDir(document.id)
        val before = app.workbookStore.loadCheckpoint(document.id)
        val beforeBytes = File(directory, "workbook.json").readBytes()
        var originalMode: Int? = null

        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_WORKBOOK_ID, document.id)
        }
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                lateinit var model: WorkbookViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity, WorkbookViewModelFactory(app, app.workbookStore, document.id))
                        .get(document.id, WorkbookViewModel::class.java)
                }
                assertTrue("The launched workbook surface must be visible",
                    device.wait(Until.hasObject(By.text(document.name)), 5_000))
                withTimeout(20_000) {
                    model.state.first { !it.loading && it.document?.id == document.id }
                }
                originalMode = Os.stat(directory.path).st_mode and 0x1ff
                Os.chmod(directory.path, 0x16d)
                scenario.onActivity { model.addWorksheet() }
                val failed = withTimeout(20_000) {
                    model.state.first { it.save is SaveState.Failed }
                }
                assertTrue("The real denied commit must reach the view as a failed save: ${failed.save}",
                    (failed.save as SaveState.Failed).message.isNotBlank())
                assertEquals(before.document.revision, app.workbookStore.load(document.id).revision)
                assertArrayEquals(beforeBytes, File(directory, "workbook.json").readBytes())

                Os.chmod(directory.path, checkNotNull(originalMode))
                originalMode = null
                val now = SystemClock.uptimeMillis()
                instrumentation.sendKeySync(
                    KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 0, KeyEvent.META_CTRL_ON),
                )
                instrumentation.sendKeySync(
                    KeyEvent(now, now + 20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S, 0, KeyEvent.META_CTRL_ON),
                )
                val saved = withTimeout(20_000) {
                    model.state.first {
                        it.save is SaveState.Saved && it.document?.revision == before.document.revision + 1
                    }
                }
                val after = app.workbookStore.loadCheckpoint(document.id)
                assertEquals(before.document.revision + 1, after.document.revision)
                assertEquals(2, after.document.worksheets.size)
                assertEquals(before.document.activeWorksheetId, after.document.activeWorksheetId)
                assertEquals(1, after.receipts.size)
                assertEquals(
                    before.document.revision,
                    after.receipts.single().command.expectedRevision,
                )
                assertEquals(saved.document!!.worksheets.map { it.id }, after.document.worksheets.map { it.id })
            }
        } finally {
            originalMode?.let { Os.chmod(directory.path, it) }
        }
    }

    @After
    fun tearDown() {
        workbookId?.let { id ->
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CloudyDataApp
            app.workbookStore.workbookDir(id).deleteRecursively()
        }
    }

    @Test
    fun workbookIntentOpensDocumentAndIdleCtrlSDoesNotInventARevision() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as CloudyDataApp
        val document = app.workbookStore.create("Shortcut ${UUID.randomUUID()}")
        workbookId = document.id
        val before = app.workbookStore.loadCheckpoint(document.id)
        val beforeBytes = File(app.workbookStore.workbookDir(document.id), "workbook.json").readBytes()

        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_WORKBOOK_ID, document.id)
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
            val now = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(
                KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 0, KeyEvent.META_CTRL_ON),
            )
            instrumentation.sendKeySync(
                KeyEvent(now, now + 20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S, 0, KeyEvent.META_CTRL_ON),
            )
            Thread.sleep(500)
            val after = app.workbookStore.loadCheckpoint(document.id)
            assertTrue(
                "Idle Ctrl+S must not invent a revision for an already persisted command (${before.document.revision} -> ${after.document.revision})",
                after.document.revision == before.document.revision,
            )
            assertEquals(before, after)
            assertArrayEquals(beforeBytes, File(app.workbookStore.workbookDir(document.id), "workbook.json").readBytes())
        }
    }
}
