package io.github.baitian6641.cloudydata

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudydata.debug.RendererProbeActivity
import io.github.baitian6641.cloudydata.debug.RendererProbeState
import org.junit.Assert.assertEquals
import kotlin.math.abs
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VegaRendererProbeTest {
    @Test
    fun packagedVegaRendersTrustedLineSpecOffline() {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RendererProbeActivity::class.java)
            .putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
        ActivityScenario.launch<RendererProbeActivity>(intent).use {
            val result = RendererProbeState.await(30)
            assertTrue("line renderer did not complete", result != null)
            assertEquals(null, result!!.error)
            assertEquals("6.4.0", result.vegaVersion)
            assertEquals(6, result.markCount)
            assertEquals("Vega did not draw both series lines", 2, result.sceneMarks["line"])
            assertTrue("SVG has too few paths for multi-series line", result.svgPaths >= 8)
            assertEquals("renderer made an external request", 0, result.blockedRequests)
        }
    }

    @Test
    fun rendererDeathRecreatesHostAndReplaysCurrentLineSpec() {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RendererProbeActivity::class.java)
            .putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
        ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
            val initial = RendererProbeState.await(30)
            assertTrue("initial render did not complete", initial != null)
            assertNull(initial!!.error)
            scenario.onActivity { it.simulateRendererGoneForTest() }
            val recovered = RendererProbeState.awaitRecovery(15)
            assertTrue("renderer did not recover", recovered != null)
            assertNull("recovered renderer returned an error", recovered!!.error)
            assertEquals(6, recovered.markCount)
            assertEquals("recovered renderer lost the two series", 2, recovered.sceneMarks["line"])
            assertEquals(1, recovered.rendererDeaths)
            assertEquals(1, recovered.recoveryAttempts)
            assertTrue(
                "recovered completion did not come from a newer host: ${initial.hostGeneration} -> ${recovered.hostGeneration}",
                recovered.hostGeneration > initial.hostGeneration,
            )
            assertTrue("recovered render has no surface width", recovered.widthPx > 0)
            assertEquals(0, recovered.blockedRequests)
        }
    }

    @Test
    fun matchedViewportSizesRenderAtTheRequestedSurface() {
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        val first = renderAt(360f, 240f)
        val second = renderAt(640f, 400f)
        val firstWidthPx = (360f * density).toInt()
        val firstHeightPx = (240f * density).toInt()
        val secondWidthPx = (640f * density).toInt()
        val secondHeightPx = (400f * density).toInt()
        assertEquals("view width at 360dp", firstWidthPx, first.widthPx)
        assertEquals("view height at 240dp", firstHeightPx, first.heightPx)
        assertEquals("view width at 640dp", secondWidthPx, second.widthPx)
        assertEquals("view height at 400dp", secondHeightPx, second.heightPx)
        assertTrue("renderer CSS surface width is not 360dp: $first", abs(first.surfaceWidth - 360) <= 1)
        assertTrue("renderer CSS surface height is not 240dp: $first", abs(first.surfaceHeight - 240) <= 1)
        assertTrue("renderer CSS surface width is not 640dp: $second", abs(second.surfaceWidth - 640) <= 1)
        assertTrue("renderer CSS surface height is not 400dp: $second", abs(second.surfaceHeight - 400) <= 1)
        assertTrue("Vega view width is not 360dp: $first", abs(first.viewWidth - 360) <= 1)
        assertTrue("Vega view height is not 240dp: $first", abs(first.viewHeight - 240) <= 1)
        assertTrue("Vega view width is not 640dp: $second", abs(second.viewWidth - 640) <= 1)
        assertTrue("Vega view height is not 400dp: $second", abs(second.viewHeight - 400) <= 1)
        assertEquals(360f, first.appliedWidthDp, 0.01f)
        assertEquals(240f, first.appliedHeightDp, 0.01f)
        assertEquals(640f, second.appliedWidthDp, 0.01f)
        assertEquals(400f, second.appliedHeightDp, 0.01f)
        assertNull(first.error)
        assertNull(second.error)
        assertEquals(2, first.markCount)
        assertEquals(2, second.markCount)
    }

    @Test
    fun recoveryStopsAfterTwoAttemptsAndReportsTheTerminalFailure() {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RendererProbeActivity::class.java)
            .putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
        ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            repeat(2) { attempt ->
                scenario.onActivity { it.simulateRendererGoneForTest() }
                val recovered = RendererProbeState.awaitRecovery(15)
                assertTrue("attempt ${attempt + 1} did not render", recovered != null)
                assertNull("attempt ${attempt + 1} returned an error", recovered!!.error)
            }
            scenario.onActivity { it.simulateRendererGoneForTest() }
            val terminal = RendererProbeState.awaitRecovery(10)
            assertTrue("terminal failure was not published", terminal != null)
            assertTrue("terminal failure is not an error: $terminal", terminal!!.error != null)
            assertTrue(
                "terminal failure does not name exhaustion: ${terminal.error}",
                terminal.error!!.contains("exhausted"),
            )
            assertEquals(3, terminal.rendererDeaths)
            assertEquals(2, terminal.recoveryAttempts)
        }
    }

    private fun renderAt(widthDp: Float, heightDp: Float): RendererProbeState.Result {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RendererProbeActivity::class.java)
            .putExtra(RendererProbeActivity.EXTRA_VIEW_WIDTH_DP, widthDp.toString())
            .putExtra(RendererProbeActivity.EXTRA_VIEW_HEIGHT_DP, heightDp.toString())
        ActivityScenario.launch<RendererProbeActivity>(intent).use {
            val result = RendererProbeState.await(30)
            assertTrue("viewport render did not complete", result != null)
            return result!!
        }
    }

    @Test
    fun packagedVegaRendersTrustedBarSpecOffline() {
        RendererProbeState.reset()
        ActivityScenario.launch(RendererProbeActivity::class.java).use {
            val result = RendererProbeState.await(30)
            assertTrue("renderer did not complete within 30s", result != null)
            assertEquals("unexpected renderer error", null, result!!.error)
            assertEquals("6.4.0", result.vegaVersion)
            assertEquals(2, result.markCount)
            assertTrue("Vega rendered no scene marks: ${result.sceneMarks}", result.sceneMarks.isNotEmpty())
            assertTrue("SVG DOM is empty", result.svgNodes >= 4)
            assertTrue("SVG has too few rendered paths", result.svgPaths >= 2)
            assertTrue("CSP interpreter was not available", result.interpreterAvailable)
            assertEquals("renderer made an external request", 0, result.blockedRequests)
            assertNull(result.error)
        }
    }
}
