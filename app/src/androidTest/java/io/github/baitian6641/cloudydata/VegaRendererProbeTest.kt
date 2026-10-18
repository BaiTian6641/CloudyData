package io.github.baitian6641.cloudydata

import androidx.test.core.app.ActivityScenario
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
