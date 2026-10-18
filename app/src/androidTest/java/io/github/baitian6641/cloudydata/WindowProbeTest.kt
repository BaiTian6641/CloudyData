package io.github.baitian6641.cloudydata

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WindowProbeTest {
    @Test
    fun requestedBoundsAndSecondInstanceAreObservable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val requested = Rect(120, 80, 1320, 980)
        val options = ActivityOptions.makeBasic().setLaunchBounds(requested).toBundle()
        val first = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            },
            options,
        )
        instrumentation.waitForIdleSync()
        val firstMetrics = measure(first)
        Log.i("CloudyWindowProbe", "first requested=$requested observed=$firstMetrics")
        assertTrue("window has no observed bounds: $firstMetrics", firstMetrics.widthPx > 0 && firstMetrics.heightPx > 0)
        assertTrue("unexpectedly narrow window: $firstMetrics", firstMetrics.widthDp >= 320f)

        val second = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            },
            ActivityOptions.makeBasic().setLaunchBounds(Rect(500, 220, 1700, 1120)).toBundle(),
        )
        instrumentation.waitForIdleSync()
        val secondMetrics = measure(second)
        Log.i("CloudyWindowProbe", "second observed=$secondMetrics")
        assertNotEquals("second instance was not a distinct activity", first, second)
        assertTrue("second instance is not alive", !second.isFinishing)
        assertTrue(secondMetrics.widthPx > 0 && secondMetrics.heightPx > 0)

        first.finish()
        second.finish()
    }

    private fun measure(activity: android.app.Activity): WindowObservation {
        val windowManager = activity.getSystemService(WindowManager::class.java)
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets
        val configuration = activity.resources.configuration
        return WindowObservation(
            widthPx = metrics.bounds.width(),
            heightPx = metrics.bounds.height(),
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            densityDpi = configuration.densityDpi,
            fontScale = configuration.fontScale,
            systemBarsTop = insets.getInsets(WindowInsets.Type.statusBars()).top,
            captionTop = insets.getInsets(WindowInsets.Type.captionBar()).top,
            captionInsetsAvailable = insets.isVisible(WindowInsets.Type.captionBar()) ||
                insets.getInsets(WindowInsets.Type.captionBar()).top > 0 ||
                insets.getInsets(WindowInsets.Type.captionBar()).bottom > 0,
            imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom,
            decorWidth = activity.window.decorView.width,
            decorHeight = activity.window.decorView.height,
        )
    }

    private data class WindowObservation(
        val widthPx: Int,
        val heightPx: Int,
        val widthDp: Int,
        val heightDp: Int,
        val densityDpi: Int,
        val fontScale: Float,
        val systemBarsTop: Int,
        val captionTop: Int,
        val captionInsetsAvailable: Boolean,
        val imeBottom: Int,
        val decorWidth: Int,
        val decorHeight: Int,
    )
}
