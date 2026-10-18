package io.github.baitian6641.cloudydata

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudydata.debug.RendererProbeActivity
import io.github.baitian6641.cloudydata.debug.RendererProbeState
import org.junit.Assert.assertEquals
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
