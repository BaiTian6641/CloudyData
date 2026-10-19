package io.github.baitian6641.cloudydata

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudydata.debug.RendererProbeActivity
import io.github.baitian6641.cloudydata.debug.RendererProbeState
import io.github.baitian6641.cloudydata.model.SelectionMode
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
    fun fixedDecimalValueLabelsRenderFormattedSvgText() {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, RendererProbeActivity::class.java)
            .putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "format")
        ActivityScenario.launch<RendererProbeActivity>(intent).use {
            val result = RendererProbeState.await(30)
            assertTrue("format renderer did not complete", result != null)
            assertNull(result!!.error)
            assertTrue(
                "formatted label text missing from SVG: ${result.svgTexts}",
                result.svgTexts.any { it == "1,234.57" },
            )
            assertTrue(
                "formatted whole value missing from SVG: ${result.svgTexts}",
                result.svgTexts.any { it == "2,000.00" },
            )
        }
    }

    @Test
    fun crowdedLabelPolicyHidesNarrowBarValuesInRenderedSvg() {
        RendererProbeState.reset()
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RendererProbeActivity::class.java,
        ).putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "crowdedlabel")
        ActivityScenario.launch<RendererProbeActivity>(intent).use {
            val result = RendererProbeState.await(30)
            assertTrue("crowded-label renderer did not complete", result != null)
            assertNull(result!!.error)
            assertTrue(
                "a crowded value label was drawn: ${result.svgTexts}",
                result.svgTexts.none { it == "1,234.57" || it == "1,334.57" },
            )
        }
    }

    @Test
    fun crowdedLabelPolicyHidesMultiSeriesLineValuesInRenderedSvg() {
        RendererProbeState.reset()
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RendererProbeActivity::class.java,
        ).putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "crowdedline")
        ActivityScenario.launch<RendererProbeActivity>(intent).use {
            val result = RendererProbeState.await(30)
            assertTrue("crowded-line renderer did not complete", result != null)
            assertNull(result!!.error)
            assertTrue(
                "a crowded series label was drawn: ${result.svgTexts}",
                result.svgTexts.none { it == "1,234.57" || it == "1,244.57" },
            )
        }
    }

    @Test
    fun transparentExportOmitsOpaqueBackgroundFromSvgAndPng() {
        RendererProbeState.reset()
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RendererProbeActivity::class.java,
        ).putExtra(RendererProbeActivity.EXTRA_TRANSPARENT_EXPORT, true)
        ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            scenario.onActivity { it.exportBundleForTest() }
            val bundle = RendererProbeState.awaitBundle(30)
            assertNull("transparent export failed: ${RendererProbeState.exportError}", RendererProbeState.exportError)
            assertTrue("transparent export did not complete", bundle != null)
            val svg = bundle!!.svg
            assertNull("transparent SVG failed validation: ${SvgExportValidator.validate(svg)}", SvgExportValidator.validate(svg))
            assertTrue(
                "transparent export still has an opaque background rect",
                Regex("""<rect x="0" y="0"[^>]*fill="none"""").containsMatchIn(svg),
            )
            val bitmap = BitmapFactory.decodeByteArray(bundle.png, 0, bundle.png.size)
            assertNotNull("transparent PNG did not decode", bitmap)
            assertEquals("transparent PNG corner alpha", 0, bitmap!!.getPixel(0, 0) ushr 24)
            var opaque = 0
            val stepX = (bitmap.width / 40).coerceAtLeast(1)
            val stepY = (bitmap.height / 40).coerceAtLeast(1)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    if ((bitmap.getPixel(x, y) ushr 24) != 0) opaque++
                    x += stepX
                }
                y += stepY
            }
            assertTrue("transparent PNG has no chart content", opaque > 50)
        }
    }

    @Test
    fun transparentDarkPdfUsesThemeBackgroundForContrast() {
        RendererProbeState.reset()
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RendererProbeActivity::class.java,
        ).putExtra(RendererProbeActivity.EXTRA_TRANSPARENT_EXPORT, true)
            .putExtra(RendererProbeActivity.EXTRA_DARK, true)
        ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            scenario.onActivity { it.exportBundleForTest() }
            val bundle = RendererProbeState.awaitBundle(30)
            assertNull("dark transparent export failed: ${RendererProbeState.exportError}", RendererProbeState.exportError)
            assertTrue("dark transparent export did not complete", bundle != null)
            assertEquals("transparent PNG corner alpha", 0, BitmapFactory.decodeByteArray(
                bundle!!.png,
                0,
                bundle.png.size,
            ).getPixel(0, 0) ushr 24)
            val background = Color.rgb(24, 36, 39)
            val pdf = PdfExportWriter.fromPng(bundle.png, background)
            val file = File(
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "transparent-dark.pdf",
            )
            file.writeBytes(pdf)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val rendered = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val corner = rendered.getPixel(0, 0)
                        assertTrue("PDF corner red: ${Color.red(corner)}", abs(Color.red(corner) - 24) <= 3)
                        assertTrue("PDF corner green: ${Color.green(corner)}", abs(Color.green(corner) - 36) <= 3)
                        assertTrue("PDF corner blue: ${Color.blue(corner)}", abs(Color.blue(corner) - 39) <= 3)
                        rendered.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun minorGridlinesAddMutedLinesToExportedSvg() {
        fun export(minor: Boolean): String {
            RendererProbeState.reset()
            val intent = Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                RendererProbeActivity::class.java,
            ).putExtra(RendererProbeActivity.EXTRA_MINOR_GRID, minor)
            ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
                assertNull(RendererProbeState.await(30)!!.error)
                scenario.onActivity { it.exportForTest() }
                val svg = RendererProbeState.awaitSvg(15)
                assertNull("export failed: ${RendererProbeState.exportError}", RendererProbeState.exportError)
                assertTrue("SVG export did not complete", svg != null)
                return svg!!
            }
        }
        val baseline = export(false)
        val minor = export(true)
        val lineY = Regex("<line transform=\"translate\\(0,([0-9.]+)\\)\"")
        val minorY = Regex(
            "<line transform=\"translate\\(0,([0-9.]+)\\)\"[^>]*stroke-opacity=\"0.55\"",
        )
        val baselinePositions = lineY.findAll(baseline).map { it.groupValues[1].toDouble() }.toSet()
        val minorPositions = minorY.findAll(minor).map { it.groupValues[1].toDouble() }.toList()
        assertTrue("too few distinct minor gridlines: $minorPositions", minorPositions.toSet().size >= 10)
        assertTrue(
            "minor gridlines do not add a position between majors: $minorPositions vs $baselinePositions",
            minorPositions.any { position ->
                baselinePositions.none { kotlin.math.abs(it - position) < 0.5 }
            },
        )
    }

    @Test
    fun percentFormatRendersPercentSignedLabels() {
        RendererProbeState.reset()
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RendererProbeActivity::class.java,
        ).putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "percent")
        ActivityScenario.launch<RendererProbeActivity>(intent).use {
            val result = RendererProbeState.await(30)
            assertTrue("percent renderer did not complete", result != null)
            assertNull(result!!.error)
            assertTrue(
                "12.5% label missing from SVG: ${result.svgTexts}",
                result.svgTexts.any { it == "12.5%" },
            )
            assertTrue(
                "25.0% label missing from SVG: ${result.svgTexts}",
                result.svgTexts.any { it == "25.0%" },
            )
            assertTrue(
                "percent axis tick missing from SVG: ${result.svgTexts}",
                result.svgTexts.any { it.endsWith("%") && it != "12.5%" && it != "25.0%" },
            )
        }
    }

    @Test
    fun legendPointerClickPostsSeriesToggleAndExportStaysClean() {
        RendererProbeState.reset()
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RendererProbeActivity::class.java,
        ).putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
        ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            assertEquals(0, RendererProbeState.selectionCount.get())
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val found = device.wait(
                Until.hasObject(By.descStartsWith("Symbol legend")),
                5000,
            )
            assertTrue("legend accessibility node missing", found)
            val legend = device.findObject(By.descStartsWith("Symbol legend"))
            val bounds = legend!!.visibleBounds
            for (fraction in listOf(0.55f, 0.65f, 0.75f, 0.85f, 0.95f)) {
                device.click(bounds.centerX(), (bounds.top + bounds.height() * fraction).toInt())
                if (waitFor(750) {
                        RendererProbeState.legendToggles.toSet().containsAll(setOf("North", "South"))
                    }
                ) {
                    break
                }
            }
            assertEquals(
                "both legend entries must be clickable exactly once: ${RendererProbeState.legendToggles}",
                setOf("North", "South"),
                RendererProbeState.legendToggles.toSet(),
            )
            assertEquals(
                "legend click posted duplicate toggles: ${RendererProbeState.legendToggles}",
                2,
                RendererProbeState.legendToggles.size,
            )
            assertEquals(
                "legend toggle must not select a row",
                0,
                RendererProbeState.selectionCount.get(),
            )
            scenario.onActivity { it.exportForTest() }
            val svg = RendererProbeState.awaitSvg(15)
            assertTrue("export did not complete", svg != null)
            assertFalse("interactive legend hit leaked into export", svg!!.contains("cloudy-legend-hit"))
            assertFalse(
                "interactive legend style leaked into export",
                svg.contains("cursor: pointer") || svg.contains("cursor:pointer"),
            )
        }
    }

    @Test
    fun hiddenSeriesFiltersMarksButKeepsLegendLabels() {
        RendererProbeState.reset()
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RendererProbeActivity::class.java,
        ).putExtra(RendererProbeActivity.EXTRA_HIDDEN_SERIES, "South")
            .putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
        ActivityScenario.launch<RendererProbeActivity>(intent).use {
            val result = RendererProbeState.await(30)
            assertTrue("hidden-series renderer did not complete", result != null)
            assertNull(result!!.error)
            assertEquals("table rows must remain intact", 6, result.markCount)
            assertEquals("hidden series line was still drawn", 1, result.sceneMarks["line"])
            assertTrue("North legend label missing", result.svgTexts.any { it == "North" })
            assertTrue("South legend label missing", result.svgTexts.any { it == "South" })
        }
    }

    @Test
    fun hiddenMarkersRemoveSymbolsButKeepLines() {
        fun symbols(hide: Boolean): RendererProbeState.Result {
            RendererProbeState.reset()
            val intent = Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                RendererProbeActivity::class.java,
            ).putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
                .putExtra(RendererProbeActivity.EXTRA_HIDE_MARKERS, hide)
            ActivityScenario.launch<RendererProbeActivity>(intent).use {
                val result = RendererProbeState.await(30)
                assertTrue("marker probe did not complete", result != null)
                assertNull(result!!.error)
                return result
            }
        }
        val visible = symbols(false)
        val hidden = symbols(true)
        assertTrue(
            "markers were not rendered: ${visible.sceneMarks}",
            (visible.sceneMarks["symbol"] ?: 0) > (hidden.sceneMarks["symbol"] ?: 0),
        )
        assertEquals("hiding markers changed the series lines", visible.sceneMarks["line"], hidden.sceneMarks["line"])
        assertEquals(6, visible.markCount)
        assertEquals(6, hidden.markCount)
    }

    @Test
    fun markerShapeRendersWithoutChangingMarkCounts() {
        data class Rendered(val result: RendererProbeState.Result, val svg: String)
        fun render(shape: String): Rendered {
            RendererProbeState.reset()
            val intent = Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                RendererProbeActivity::class.java,
            ).putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
                .putExtra(RendererProbeActivity.EXTRA_MARKER_SHAPE, shape)
            ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
                val result = RendererProbeState.await(30)
                assertTrue("marker-shape render did not complete", result != null)
                assertNull(result!!.error)
                scenario.onActivity { it.exportForTest() }
                val svg = RendererProbeState.awaitSvg(15)
                assertTrue("marker-shape export did not complete", svg != null)
                return Rendered(result, svg!!)
            }
        }
        val circle = render("CIRCLE")
        val diamond = render("DIAMOND")
        assertEquals(circle.result.markCount, diamond.result.markCount)
        assertEquals(circle.result.sceneMarks["line"], diamond.result.sceneMarks["line"])
        assertEquals(circle.result.sceneMarks["symbol"], diamond.result.sceneMarks["symbol"])
        assertFalse("circle and diamond exported identical SVG", circle.svg == diamond.svg)
    }

    @Test
    fun stepInterpolationRendersWithoutChangingMarkCounts() {
        data class Rendered(val result: RendererProbeState.Result, val svg: String)
        fun render(interpolation: String): Rendered {
            RendererProbeState.reset()
            val intent = Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                RendererProbeActivity::class.java,
            ).putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
                .putExtra(RendererProbeActivity.EXTRA_LINE_INTERPOLATION, interpolation)
            ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
                val result = RendererProbeState.await(30)
                assertTrue("interpolation render did not complete", result != null)
                assertNull(result!!.error)
                scenario.onActivity { it.exportForTest() }
                val svg = RendererProbeState.awaitSvg(15)
                assertTrue("interpolation export did not complete", svg != null)
                return Rendered(result, svg!!)
            }
        }
        val linear = render("LINEAR")
        val step = render("STEP")
        assertEquals(linear.result.markCount, step.result.markCount)
        assertEquals(linear.result.sceneMarks["line"], step.result.sceneMarks["line"])
        assertEquals(linear.result.sceneMarks["symbol"], step.result.sceneMarks["symbol"])
        assertFalse("linear and step exported identical SVG", linear.svg == step.svg)
    }

    @Test
    fun dashedStyleRendersWithoutChangingMarkCounts() {
        data class Rendered(val result: RendererProbeState.Result, val svg: String)
        fun render(dash: String): Rendered {
            RendererProbeState.reset()
            val intent = Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                RendererProbeActivity::class.java,
            ).putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
                .putExtra(RendererProbeActivity.EXTRA_LINE_DASH, dash)
            ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
                val result = RendererProbeState.await(30)
                assertTrue("dash render did not complete", result != null)
                assertNull(result!!.error)
                scenario.onActivity { it.exportForTest() }
                val svg = RendererProbeState.awaitSvg(15)
                assertTrue("dash export did not complete", svg != null)
                return Rendered(result, svg!!)
            }
        }
        val solid = render("SOLID")
        val dashed = render("DASHED")
        val dotted = render("DOTTED")
        for (variant in listOf(dashed, dotted)) {
            assertEquals(solid.result.markCount, variant.result.markCount)
            assertEquals(solid.result.sceneMarks["line"], variant.result.sceneMarks["line"])
            assertEquals(solid.result.sceneMarks["symbol"], variant.result.sceneMarks["symbol"])
            assertFalse("solid and dashed/dotted exported identical SVG", solid.svg == variant.svg)
        }
        assertFalse("dashed and dotted exported identical SVG", dashed.svg == dotted.svg)
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
    fun ctrlClickAddsABarToTheCurrentSelection() {
        RendererProbeState.reset()
        ActivityScenario.launch(RendererProbeActivity::class.java).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            var bounds: Rect? = null
            scenario.onActivity { bounds = it.rendererBoundsForTest() }
            val chart = bounds!!
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            device.click(
                (chart.left + chart.width() * 0.28f).toInt(),
                (chart.top + chart.height() * 0.78f).toInt(),
            )
            assertTrue("plain click did not select row 0", waitFor(5000) {
                RendererProbeState.selectionRow.get() == 0
            })
            val x = (chart.left + chart.width() * 0.72f).toInt()
            val y = (chart.top + chart.height() * 0.78f).toInt()
            clickWithMeta(instrumentation, x, y, KeyEvent.META_CTRL_ON)
            assertTrue("Ctrl-click did not add the second row", waitFor(5000) {
                RendererProbeState.selectionRows == setOf(0, 1) &&
                    RendererProbeState.selectionMode == io.github.baitian6641.cloudydata.model.SelectionMode.TOGGLE
            })
        }
    }

    @Test
    fun keyboardReplaceIsNotHijackedByAPendingPointerModifier() {
        RendererProbeState.reset()
        ActivityScenario.launch(RendererProbeActivity::class.java).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            var bounds: Rect? = null
            scenario.onActivity { bounds = it.rendererBoundsForTest() }
            val chart = bounds!!
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            var focused = false
            scenario.onActivity { focused = it.focusRendererForTest() }
            assertTrue("WebView did not accept focus", focused)
            instrumentation.waitForIdleSync()
            clickWithMeta(
                instrumentation,
                chart.left + 8,
                chart.top + 8,
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
            )
            sendModifiedKey(instrumentation, KeyEvent.KEYCODE_DPAD_RIGHT, 0)
            assertTrue("keyboard replace was hijacked by the pointer hint", waitFor(5000) {
                RendererProbeState.selectionMode == io.github.baitian6641.cloudydata.model.SelectionMode.REPLACE &&
                    RendererProbeState.selectionRows == setOf(0)
            })
        }
    }

    @Test
    fun shiftClickSelectsAPointerRangeFromTheAnchor() {
        RendererProbeState.reset()
        ActivityScenario.launch(RendererProbeActivity::class.java).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            var bounds: Rect? = null
            scenario.onActivity { bounds = it.rendererBoundsForTest() }
            val chart = bounds!!
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            UiDevice.getInstance(instrumentation).click(
                (chart.left + chart.width() * 0.28f).toInt(),
                (chart.top + chart.height() * 0.78f).toInt(),
            )
            assertTrue("plain click did not select row 0", waitFor(5000) {
                RendererProbeState.selectionRow.get() == 0
            })
            val now = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(
                KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, KeyEvent.META_SHIFT_LEFT_ON),
            )
            clickWithMeta(
                instrumentation,
                (chart.left + chart.width() * 0.72f).toInt(),
                (chart.top + chart.height() * 0.78f).toInt(),
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
            )
            instrumentation.sendKeySync(
                KeyEvent(now, now + 30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0),
            )
            val rangeObserved = waitFor(5000) {
                RendererProbeState.selectionRows == setOf(0, 1) &&
                    RendererProbeState.selectionMode == io.github.baitian6641.cloudydata.model.SelectionMode.RANGE
            }
            assertTrue(
                "Shift-click did not select the range: row=${RendererProbeState.selectionRow.get()} " +
                    "rows=${RendererProbeState.selectionRows} mode=${RendererProbeState.selectionMode}",
                rangeObserved,
            )
        }
    }

    @Test
    fun rectangleDragSelectsIntersectingMarksAndCtrlDragAdds() {
        RendererProbeState.reset()
        ActivityScenario.launch(RendererProbeActivity::class.java).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            var bounds: Rect? = null
            scenario.onActivity { bounds = it.rendererBoundsForTest() }
            val chart = bounds!!
            val instrumentation = InstrumentationRegistry.getInstrumentation()

            dragWithMeta(
                instrumentation,
                (chart.left + chart.width() * 0.05f).toInt(),
                (chart.top + chart.height() * 0.12f).toInt(),
                (chart.left + chart.width() * 0.95f).toInt(),
                (chart.top + chart.height() * 0.88f).toInt(),
                0,
            )
            assertTrue("rectangle did not select both bars", waitFor(5000) {
                RendererProbeState.selectionRows == setOf(0, 1)
            })

            // Raw pointer injection exercises the pointerup hit-test immediately
            // after the lasso; a new press must not inherit click suppression.
            clickWithMeta(
                instrumentation,
                (chart.left + chart.width() * 0.28f).toInt(),
                (chart.top + chart.height() * 0.78f).toInt(),
                0,
            )
            assertTrue(
                "plain click did not replace with row 0: rows=${RendererProbeState.selectionRows} " +
                    "row=${RendererProbeState.selectionRow.get()} mode=${RendererProbeState.selectionMode}",
                waitFor(5000) { RendererProbeState.selectionRows == setOf(0) },
            )

            dragWithMeta(
                instrumentation,
                (chart.left + chart.width() * 0.60f).toInt(),
                (chart.top + chart.height() * 0.20f).toInt(),
                (chart.left + chart.width() * 0.95f).toInt(),
                (chart.top + chart.height() * 0.88f).toInt(),
                KeyEvent.META_CTRL_ON,
                stepDelayMs = 150L,
            )
            assertTrue(
                "Slow Ctrl-drag did not add the second bar: rows=${RendererProbeState.selectionRows}",
                waitFor(5000) {
                    RendererProbeState.selectionRows == setOf(0, 1) &&
                        RendererProbeState.selectionMode == SelectionMode.TOGGLE
                },
            )

            dragWithMeta(
                instrumentation,
                chart.left + 2,
                chart.top + 2,
                chart.left + 12,
                chart.top + 12,
                0,
            )
            assertTrue("empty rectangle did not clear the selection", waitFor(5000) {
                RendererProbeState.selectionRows.isEmpty()
            })

        }
    }

    private fun dragWithMeta(
        instrumentation: android.app.Instrumentation,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        metaState: Int,
        stepDelayMs: Long = 30L,
    ) {
        val downTime = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX.toFloat(), startY.toFloat(), metaState),
        )
        for (step in 1..4) {
            SystemClock.sleep(stepDelayMs)
            val fraction = step / 4f
            instrumentation.sendPointerSync(
                MotionEvent.obtain(
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE,
                    startX + (endX - startX) * fraction,
                    startY + (endY - startY) * fraction,
                    metaState,
                ),
            )
        }
        SystemClock.sleep(stepDelayMs)
        instrumentation.sendPointerSync(
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                endX.toFloat(),
                endY.toFloat(),
                metaState,
            ),
        )
    }

    private fun clickWithMeta(
        instrumentation: android.app.Instrumentation,
        x: Int,
        y: Int,
        metaState: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(
            android.view.MotionEvent.obtain(
                now, now, android.view.MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), metaState,
            ),
        )
        instrumentation.sendPointerSync(
            android.view.MotionEvent.obtain(
                now, now + 20, android.view.MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), metaState,
            ),
        )
    }

    @Test
    fun keyboardModifiersExtendRangeAndToggleRows() {
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
            sendModifiedKey(instrumentation, KeyEvent.KEYCODE_DPAD_RIGHT, 0)
            assertTrue("plain arrow did not select row 0", waitFor(5000) {
                RendererProbeState.selectionRow.get() == 0
            })
            assertEquals(setOf(0), RendererProbeState.selectionRows)

            sendModifiedKey(instrumentation, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON)
            assertTrue("Shift+Right did not extend a range", waitFor(5000) {
                RendererProbeState.selectionMode == io.github.baitian6641.cloudydata.model.SelectionMode.RANGE &&
                    RendererProbeState.selectionRows == setOf(0, 1)
            })
            sendModifiedKey(instrumentation, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON)
            assertTrue("second Shift+Right did not extend the same range", waitFor(5000) {
                RendererProbeState.selectionRows == setOf(0, 1, 2)
            })

            sendModifiedKey(instrumentation, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON)
            assertTrue("Ctrl+Right did not add the next row", waitFor(5000) {
                RendererProbeState.selectionMode == io.github.baitian6641.cloudydata.model.SelectionMode.TOGGLE &&
                    RendererProbeState.selectionRows == setOf(0, 1, 2, 3)
            })
            sendModifiedKey(instrumentation, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON)
            assertTrue("second Ctrl+Right did not add the next row", waitFor(5000) {
                RendererProbeState.selectionRows == setOf(0, 1, 2, 3, 4)
            })
            sendModifiedKey(instrumentation, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_CTRL_ON)
            assertTrue("Ctrl+Left did not toggle the current row off", waitFor(5000) {
                RendererProbeState.selectionRows == setOf(0, 1, 2, 4)
            })
        }
    }

    private fun sendModifiedKey(
        instrumentation: android.app.Instrumentation,
        keyCode: Int,
        metaState: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        instrumentation.sendKeySync(KeyEvent(now, now + 20, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    @Test
    fun frozenBundleHasMatchingSvgPngPdfAndEvidenceFiles() {
        RendererProbeState.reset()
        ActivityScenario.launch(RendererProbeActivity::class.java).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            scenario.onActivity { it.exportBundleForTest() }
            val bundle = RendererProbeState.awaitBundle(30)
            assertNull("bundle export failed: ${RendererProbeState.exportError}", RendererProbeState.exportError)
            assertTrue("bundle export did not complete", bundle != null)
            val svg = bundle!!.svg
            assertNull("bundle SVG failed validation: ${SvgExportValidator.validate(svg)}", SvgExportValidator.validate(svg))
            assertTrue("bundle title missing", svg.contains(">Regional sales<"))
            assertTrue("bundle source missing", svg.contains(">Source: probe fixture<"))
            assertTrue(
                "default export lost its opaque background",
                Regex("""<rect x="0" y="0"[^>]*fill="#FFFFFF"""").containsMatchIn(svg),
            )
            val dimensions = SvgExportValidator.dimensions(svg)
            assertNotNull("bundle SVG dimensions missing", dimensions)
            assertTrue(
                "bundle PNG dimensions do not match the SVG: ${dimensions!!.width}x${dimensions.height} vs ${bundle.width}x${bundle.height}",
                kotlin.math.abs(dimensions.width - bundle.width) <= 1.0 &&
                    kotlin.math.abs(dimensions.height - bundle.height) <= 1.0,
            )
            val bitmap = BitmapFactory.decodeByteArray(bundle.png, 0, bundle.png.size)
            assertNotNull("bundle PNG did not decode", bitmap)
            assertEquals("default PNG corner must be opaque", 255, bitmap!!.getPixel(0, 0) ushr 24)
            assertEquals("bundle PNG width", bundle.width, bitmap!!.width)
            assertEquals("bundle PNG height", bundle.height, bitmap.height)
            var opaque = 0
            val stepX = (bitmap.width / 40).coerceAtLeast(1)
            val stepY = (bitmap.height / 40).coerceAtLeast(1)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    if ((bitmap.getPixel(x, y) ushr 24) != 0) opaque++
                    x += stepX
                }
                y += stepY
            }
            assertTrue("bundle PNG is blank", opaque > 50)
            val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            assertEquals("bundle SVG evidence file mismatch", svg, File(filesDir, "bundle-chart.svg").readText())
            assertTrue("bundle PNG evidence file mismatch", bundle.png.contentEquals(File(filesDir, "bundle-chart.png").readBytes()))

            val pdf = PdfExportWriter.fromPng(bundle.png)
            val pdfFile = File(filesDir, "bundle-chart.pdf")
            pdfFile.writeBytes(pdf)
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        assertEquals(bundle.width, page.width)
                        assertEquals(bundle.height, page.height)
                    }
                }
            }
        }
    }

    @Test
    fun narrowExportEllipsizesLongTitleAndSourceNote() {
        RendererProbeState.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val longTitle = "A very long chart title ".repeat(12).trim()
        val longSource = "Source: a-very-long-source-name-with-many-characters.csv · 123 rows · snapshot abcdef12 " +
            "additional provenance text ".repeat(4)
        val subtitle = "A bounded subtitle for export provenance"
        val caption = "A long caption that should wrap into two bounded export lines without displacing the chart ".repeat(2)
        val intent = Intent(context, RendererProbeActivity::class.java)
            .putExtra(RendererProbeActivity.EXTRA_PROBE_KIND, "line")
            .putExtra(RendererProbeActivity.EXTRA_VIEW_WIDTH_DP, "360")
            .putExtra(RendererProbeActivity.EXTRA_VIEW_HEIGHT_DP, "240")
            .putExtra(RendererProbeActivity.EXTRA_EXPORT_TITLE, longTitle)
            .putExtra(RendererProbeActivity.EXTRA_EXPORT_SUBTITLE, subtitle)
            .putExtra(RendererProbeActivity.EXTRA_EXPORT_CAPTION, caption)
            .putExtra(RendererProbeActivity.EXTRA_EXPORT_SOURCE, longSource)
        ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
            assertNull(RendererProbeState.await(30)!!.error)
            scenario.onActivity { it.exportForTest() }
            val svg = RendererProbeState.awaitSvg(15)
            assertNull("export failed: ${RendererProbeState.exportError}", RendererProbeState.exportError)
            assertNotNull("narrow export did not complete", svg)
            val texts = Regex("<text[^>]*>([^<]+)</text>").findAll(svg!!).map { it.groupValues[1] }.toList()
            val title = texts.firstOrNull { it.startsWith("A very long chart title") }
            val source = texts.firstOrNull { it.contains("a-very-long-source-name") }
            val subtitleNode = texts.firstOrNull { it.startsWith("A bounded subtitle") }
            val captionLines = texts.filter { it.contains("A long caption") }
            assertTrue("title was not truncated: $title", title != null && title.endsWith("\u2026") && title.length < longTitle.length)
            assertTrue("source was not truncated: $source", source != null && source.endsWith("\u2026") && source.length < longSource.length)
            assertTrue("subtitle missing from export header", subtitleNode != null)
            assertTrue("caption did not occupy two bounded export lines: $captionLines", captionLines.size == 2)
        }
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
            assertTrue("chart title is missing from the export", svg.contains(">Regional sales<"))
            assertTrue("source provenance is missing from the export", svg.contains(">Source: probe fixture<"))
            assertFalse(
                "default export unexpectedly has a chart border",
                Regex("<rect x=\"0\\.5\" y=\"0\\.5\"[^>]*fill=\"none\"").containsMatchIn(svg),
            )
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
    fun chartBorderIsPresentInExportedSvg() {
        for ((dark, stroke) in listOf(false to "#D3DFE2", true to "#35515A")) {
            RendererProbeState.reset()
            val intent = Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                RendererProbeActivity::class.java,
            ).putExtra(RendererProbeActivity.EXTRA_CHART_BORDER, true)
                .putExtra(RendererProbeActivity.EXTRA_DARK, dark)
            ActivityScenario.launch<RendererProbeActivity>(intent).use { scenario ->
                assertNull(RendererProbeState.await(30)!!.error)
                scenario.onActivity { it.exportForTest() }
                val svg = RendererProbeState.awaitSvg(15)
                assertNull("export failed: ${RendererProbeState.exportError}", RendererProbeState.exportError)
                assertTrue("SVG export did not complete", svg != null)
                val document = svg!!
                assertNull(
                    "border export failed validation: ${SvgExportValidator.validate(document)}",
                    SvgExportValidator.validate(document),
                )
                val outerTag = Regex("<svg[^>]*>").find(document)!!.value
                val outerWidth = attribute(outerTag, "width")
                val outerHeight = attribute(outerTag, "height")
                val borderTag = Regex("<rect x=\"0\\.5\" y=\"0\\.5\"[^>]*/>")
                    .find(document)?.value
                assertTrue("outer chart border missing from export", borderTag != null)
                assertEquals(0.5, attribute(borderTag!!, "x"), 0.0)
                assertEquals(0.5, attribute(borderTag, "y"), 0.0)
                assertEquals("none", attributeText(borderTag, "fill"))
                assertEquals(stroke, attributeText(borderTag, "stroke"))
                assertEquals("1", attributeText(borderTag, "stroke-width"))
                assertEquals(outerWidth - 1.0, attribute(borderTag, "width"), 0.01)
                assertEquals(outerHeight - 1.0, attribute(borderTag, "height"), 0.01)
                val nestedTag = Regex("<svg[^>]*y=\"[^\"]+\"[^>]*>").findAll(document)
                    .map { it.value }
                    .first { it != outerTag }
                val nestedHeight = attribute(nestedTag, "height")
                assertTrue(
                    "export border does not include the header: outer=$outerHeight nested=$nestedHeight",
                    outerHeight > nestedHeight,
                )
            }
        }
    }

    private fun attribute(tag: String, name: String): Double =
        attributeText(tag, name).toDouble()

    private fun attributeText(tag: String, name: String): String =
        Regex("""$name="([^"]*)"""").find(tag)!!.groupValues[1]

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
