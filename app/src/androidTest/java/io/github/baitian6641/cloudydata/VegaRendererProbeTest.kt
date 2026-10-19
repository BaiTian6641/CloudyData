package io.github.baitian6641.cloudydata

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.UiDevice
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudydata.debug.RendererProbeActivity
import io.github.baitian6641.cloudydata.debug.RendererProbeState
import io.github.baitian6641.cloudydata.renderer.PdfExportWriter
import io.github.baitian6641.cloudydata.renderer.SvgExportValidator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import kotlin.math.abs
import org.junit.Assert.assertNotNull
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

    @Test
    fun clickSelectsTheExactSourceRowFromTheVegaMark() {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RendererProbeActivity::class.java)
        ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            var bounds: Rect? = null
            scenario.onActivity { bounds = it.rendererBoundsForTest() }
            assertTrue("renderer bounds were not available", bounds != null)
            val chart = bounds!!
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.click(
                (chart.left + chart.width() * 0.28f).toInt(),
                (chart.top + chart.height() * 0.78f).toInt(),
            )
            assertTrue("click on bar A did not select row 0", waitFor(3000) {
                RendererProbeState.selectionRow.get() == 0
            })
            device.click(
                (chart.left + chart.width() * 0.72f).toInt(),
                (chart.top + chart.height() * 0.78f).toInt(),
            )
            assertTrue("click on bar B did not select row 1", waitFor(3000) {
                RendererProbeState.selectionRow.get() == 1
            })
            assertEquals(2, RendererProbeState.selectionCount.get())
        }
    }

    @Test
    fun keyboardArrowsMoveSelectionAndEscapeReleasesTheWebView() {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RendererProbeActivity::class.java)
            .putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
        ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            var focused = false
            scenario.onActivity { focused = it.focusRendererForTest() }
            assertTrue("WebView did not accept focus", focused)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val device = UiDevice.getInstance(instrumentation)
            sendKey(device, KeyEvent.KEYCODE_DPAD_RIGHT)
            assertTrue("ArrowRight did not select the first row", waitFor(5000) {
                RendererProbeState.selectionRow.get() == 0
            })
            sendKey(device, KeyEvent.KEYCODE_DPAD_RIGHT)
            assertTrue("second ArrowRight did not advance", waitFor(5000) {
                RendererProbeState.selectionRow.get() == 1
            })
            assertTrue("selection intents were not observed", RendererProbeState.selectionCount.get() >= 2)
            sendKey(device, KeyEvent.KEYCODE_ESCAPE)
            assertTrue("Escape did not emit a focus exit", waitFor(5000) {
                RendererProbeState.focusExitCount.get() >= 1
            })
            val countAfterEscape = RendererProbeState.selectionCount.get()
            sendKey(device, KeyEvent.KEYCODE_DPAD_RIGHT)
            SystemClock.sleep(700)
            assertEquals(
                "ArrowRight still reached the WebView after Escape",
                countAfterEscape,
                RendererProbeState.selectionCount.get(),
            )

            scenario.onActivity { it.focusRendererForTest() }
            instrumentation.waitForIdleSync()
            sendKey(device, KeyEvent.KEYCODE_TAB)
            assertTrue("Tab did not emit a second focus exit", waitFor(5000) {
                RendererProbeState.focusExitCount.get() >= 2
            })
            val countAfterTab = RendererProbeState.selectionCount.get()
            sendKey(device, KeyEvent.KEYCODE_DPAD_RIGHT)
            SystemClock.sleep(700)
            assertEquals(
                "ArrowRight still reached the WebView after Tab",
                countAfterTab,
                RendererProbeState.selectionCount.get(),
            )
        }
    }

    private fun sendKey(device: UiDevice, keyCode: Int) {
        device.pressKeyCode(keyCode)
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    @Test
    fun exportsCurrentChartPngWithBoundedDimensionsAndEvidenceFile() {
        RendererProbeState.reset()
        ActivityScenario.launch(RendererProbeActivity::class.java).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            scenario.onActivity { it.exportForTest() }
            val svg = RendererProbeState.awaitSvg(15)
            assertNull("SVG export failed before PNG: ${RendererProbeState.exportError}", RendererProbeState.exportError)
            assertNotNull("SVG export did not complete before PNG", svg)
            val svgWidth = Regex("<svg[^>]*width=\"([0-9.]+)\"").find(svg!!)?.groupValues?.get(1)?.toDouble()
            val svgHeight = Regex("<svg[^>]*height=\"([0-9.]+)\"").find(svg)?.groupValues?.get(1)?.toDouble()
            assertNotNull("SVG width is missing", svgWidth)
            assertNotNull("SVG height is missing", svgHeight)
            scenario.onActivity { it.exportPngForTest() }
            val png = RendererProbeState.awaitPng(30)
            assertNull("PNG export failed: ${RendererProbeState.exportError}", RendererProbeState.exportError)
            assertTrue("PNG export did not complete", png != null)
            val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            assertTrue("PNG signature is wrong", png!!.size > 24 && png.copyOfRange(0, 8).contentEquals(signature))
            val width = ((png[16].toInt() and 0xFF) shl 24) or
                ((png[17].toInt() and 0xFF) shl 16) or
                ((png[18].toInt() and 0xFF) shl 8) or
                (png[19].toInt() and 0xFF)
            val height = ((png[20].toInt() and 0xFF) shl 24) or
                ((png[21].toInt() and 0xFF) shl 16) or
                ((png[22].toInt() and 0xFF) shl 8) or
                (png[23].toInt() and 0xFF)
            assertTrue("PNG dimensions are not bounded: ${width}x$height", width in 1..4096 && height in 1..4096)
            assertEquals("PNG width does not match the exported SVG", svgWidth!!.toInt(), width)
            assertEquals("PNG height does not match the exported SVG", svgHeight!!.toInt(), height)
            assertEquals(width, RendererProbeState.pngWidth)
            assertEquals(height, RendererProbeState.pngHeight)
            val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
            assertNotNull("PNG did not decode", bitmap)
            var opaque = 0
            val stepX = (bitmap!!.width / 50).coerceAtLeast(1)
            val stepY = (bitmap.height / 50).coerceAtLeast(1)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    if ((bitmap.getPixel(x, y) ushr 24) != 0) opaque++
                    x += stepX
                }
                y += stepY
            }
            assertTrue("PNG raster is blank or nearly blank (opaque samples=$opaque)", opaque > 100)
            assertTrue("PNG raster does not match the rendered surface", bitmap.width == width && bitmap.height == height)
            val fileBytes = File(
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "exported-chart.png",
            ).readBytes()
            assertTrue("probe PNG evidence file does not match the payload", png.contentEquals(fileBytes))

            val pdf = PdfExportWriter.fromPng(png)
            assertTrue("PDF signature is wrong", pdf.size > 5 && pdf.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray()))
            assertTrue("PDF exceeded the bounded limit", pdf.size <= PdfExportWriter.MAX_PDF_BYTES)
            val pdfFile = File(
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "exported-chart.pdf",
            )
            pdfFile.writeBytes(pdf)
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals("PDF must contain one chart page", 1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        assertEquals("PDF page width does not match the PNG", width, page.width)
                        assertEquals("PDF page height does not match the PNG", height, page.height)
                    }
                }
            }
        }
    }

    @Test
    fun svgValidatorIgnoresTextUrlsButRejectsActiveContent() {
        val safe = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
            "<text>href=\"http://example.invalid\" url(http://example.invalid)</text>" +
            "<rect fill=\"url(#paint)\"/></svg>"
        assertNull(SvgExportValidator.validate(safe))
        assertNotNull(SvgExportValidator.validate("<svg><script/></svg>"))
        assertNotNull(SvgExportValidator.validate("<svg><foreignObject/></svg>"))
        assertNotNull(SvgExportValidator.validate("<svg><image href=\"https://example.invalid/x\"/></svg>"))
        assertNotNull(SvgExportValidator.validate("<svg><a href=\"https://example.invalid\"/></svg>"))
        assertNotNull(SvgExportValidator.validate("<svg><rect style=\"fill:url(http://example.invalid)\"/></svg>"))
        assertNotNull(SvgExportValidator.validate("<svg onload=\"alert(1)\"/></svg>"))
        assertNotNull(SvgExportValidator.validate("<svg><rect onclick=\"alert(1)\"/></svg>"))
        assertNotNull(SvgExportValidator.validate("not an svg"))
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
    fun packagedVegaRendersSingleSeriesAreaOffline() {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RendererProbeActivity::class.java)
            .putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "area")
        ActivityScenario.launch<RendererProbeActivity>(intent).use {
            val result = RendererProbeState.await(30)
            assertTrue("area renderer did not complete", result != null)
            assertNull("area renderer returned an error", result!!.error)
            assertEquals("area lost observed points", 4, result.markCount)
            assertEquals("Vega did not draw an area", 1, result.sceneMarks["area"])
            assertEquals("Vega did not draw the area outline", 1, result.sceneMarks["line"])
            assertEquals("renderer made an external request", 0, result.blockedRequests)
        }
    }

    @Test
    fun exportsCurrentChartSvgWithoutScriptsOrRemoteReferences() {
        RendererProbeState.reset()
        ActivityScenario.launch(RendererProbeActivity::class.java).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            scenario.onActivity { it.exportForTest() }
            val svg = RendererProbeState.awaitSvg(15)
            assertNull("export failed: ${RendererProbeState.exportError}", RendererProbeState.exportError)
            assertTrue("SVG export did not complete", svg != null)
            assertTrue("export is not an SVG document", svg!!.trimStart().startsWith("<svg"))
            assertNull("export failed validation: ${SvgExportValidator.validate(svg)}", SvgExportValidator.validate(svg))
            assertTrue("current bar category A is missing", svg.contains(">A<"))
            assertTrue("current bar category B is missing", svg.contains(">B<"))
            assertTrue("current axis title is missing", svg.contains(">Region<"))
            assertTrue("export is unexpectedly small: ${svg.length}", svg.length > 500)
            assertTrue("export exceeded the bounded limit", svg.length <= 2 * 1024 * 1024)
            val fileSvg = File(
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "exported-chart.svg",
            ).readText()
            assertEquals("probe evidence file does not match the exported payload", svg, fileSvg)
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
