package io.github.baitian6641.cloudydata

import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandPaletteTest {
    @Test
    fun capturePaletteForReview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            device.pressKeyCode(
                KeyEvent.KEYCODE_P,
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            )
            assertNotNull(device.wait(Until.findObject(By.text("Command palette")), 3_000))
            Thread.sleep(6_000)
            assertNotNull(device.findObject(By.text("Command palette")))
        }
    }

    @Test
    fun paletteOpensRunsSettingsAndClosesWithEscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            device.pressKeyCode(
                KeyEvent.KEYCODE_P,
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            )
            assertNotNull(
                "palette did not open",
                device.wait(Until.findObject(By.text("Command palette")), 3_000),
            )
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
            assertNotNull(
                "settings command did not execute",
                device.wait(Until.findObject(By.text("Appearance and density")), 3_000),
            )

            device.pressKeyCode(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            device.pressKeyCode(
                KeyEvent.KEYCODE_P,
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            )
            assertNotNull(
                "palette did not reopen",
                device.wait(Until.findObject(By.text("Command palette")), 3_000),
            )
            device.pressKeyCode(KeyEvent.KEYCODE_ESCAPE)
            assertNull(
                "Escape did not close the palette",
                device.wait(Until.findObject(By.text("Command palette")), 1_000),
            )
        }
    }
}
