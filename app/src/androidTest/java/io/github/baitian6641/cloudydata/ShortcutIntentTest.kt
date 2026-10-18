package io.github.baitian6641.cloudydata

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutIntentTest {
    private var workbookId: String? = null

    @After
    fun tearDown() {
        workbookId?.let { id ->
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CloudyDataApp
            app.workbookStore.workbookDir(id).deleteRecursively()
        }
    }

    @Test
    fun workbookIntentOpensDocumentAndCtrlSCommitsSave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as CloudyDataApp
        val document = app.workbookStore.create("Shortcut ${UUID.randomUUID()}")
        workbookId = document.id
        val before = app.workbookStore.load(document.id).revision

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
            val deadline = System.currentTimeMillis() + 5_000
            var after = before
            while (System.currentTimeMillis() < deadline && after <= before) {
                Thread.sleep(100)
                after = app.workbookStore.load(document.id).revision
            }
            assertTrue("Ctrl+S did not commit a new revision ($before -> $after)", after > before)
        }
    }
}
