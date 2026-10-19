package io.github.baitian6641.cloudydata.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.baitian6641.cloudydata.chart.AxisSpec
import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartPoint
import io.github.baitian6641.cloudydata.chart.ChartSeries
import io.github.baitian6641.cloudydata.chart.ChartSpec
import io.github.baitian6641.cloudydata.chart.GridlineSpec
import io.github.baitian6641.cloudydata.chart.LabelSpec
import io.github.baitian6641.cloudydata.chart.LegendSpec
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.renderer.VegaRendererHost
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only qualification surface for the bounded renderer. It is not part of
 * the shipped application's navigation and does not own workbook state.
 */
@SuppressLint("SetTextI18n")
class RendererProbeActivity : Activity() {
    private lateinit var container: FrameLayout
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private var host: VegaRendererHost? = null
    private var hostGeneration = 0
    private var destroyed = false
    private var currentSpec: ChartSpec? = null
    private var rendererDeaths = 0
    private var recoveryAttempts = 0
    private var lastAppliedWidth = -1f
    private var lastAppliedHeight = -1f
    private val probeKind: String by lazy { intent.getStringExtra(EXTRA_PROBE_KIND) ?: "bar" }
    private val requestedWidthDp: Float by lazy {
        intent.getStringExtra(EXTRA_VIEW_WIDTH_DP)?.toFloatOrNull() ?: 0f
    }
    private val requestedHeightDp: Float by lazy {
        intent.getStringExtra(EXTRA_VIEW_HEIGHT_DP)?.toFloatOrNull() ?: 0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RendererProbeState.reset()
        currentSpec = buildSpec()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 247))
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply {
            text = "Starting offline renderer…"
            setTextColor(Color.rgb(24, 36, 39))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        container = FrameLayout(this)
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(
            container,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        summary = TextView(this).apply {
            text = accessibleSummary()
            setTextColor(Color.rgb(76, 96, 102))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        root.addView(summary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        createHost()
    }

    private fun createHost() {
        if (destroyed) return
        hostGeneration++
        val generation = hostGeneration
        lastAppliedWidth = -1f
        lastAppliedHeight = -1f
        host?.dispose()
        host = null
        container.removeAllViews()
        val newHost = VegaRendererHost(
            this,
            object : VegaRendererHost.Listener {
                override fun onReady(vegaVersion: String, interpreterAvailable: Boolean) {
                    runOnUiThread {
                        if (generation != hostGeneration) return@runOnUiThread
                        status.text = "Ready · Vega $vegaVersion · interpreter=$interpreterAvailable"
                        if (!interpreterAvailable) {
                            finishWithError("interpreter_unavailable", "The CSP interpreter was not exposed.")
                            return@runOnUiThread
                        }
                        container.post { applySpec(generation) }
                    }
                }

                override fun onRenderComplete(
                    chartId: String,
                    revision: Long,
                    markCount: Int,
                    sceneMarks: Map<String, Int>,
                    svgPaths: Int,
                    svgNodes: Int,
                    vegaVersion: String,
                    surfaceWidth: Int,
                    surfaceHeight: Int,
                    viewWidth: Int,
                    viewHeight: Int,
                ) {
                    runOnUiThread {
                        if (generation != hostGeneration || host == null) return@runOnUiThread
                        val web = host?.webView
                        val recovered = rendererDeaths > 0
                        status.text = buildString {
                            append("Rendered $chartId r$revision · ${currentSpec?.pointCount ?: 0} points · scene=$sceneMarks")
                            append(" · svgPaths=$svgPaths · svgNodes=$svgNodes · Vega $vegaVersion")
                            append(" · surface=${surfaceWidth}x$surfaceHeight · view=${viewWidth}x$viewHeight")
                            append(" · host=$generation")
                            if (recovered) append(" · recovered after $rendererDeaths death(s)")
                        }
                        RendererProbeState.complete(
                            RendererProbeState.Result(
                                vegaVersion = vegaVersion,
                                markCount = markCount,
                                sceneMarks = sceneMarks,
                                svgPaths = svgPaths,
                                svgNodes = svgNodes,
                                interpreterAvailable = true,
                                blockedRequests = RendererProbeState.blockedRequests.get(),
                                error = null,
                                widthPx = web?.width ?: 0,
                                heightPx = web?.height ?: 0,
                                rendererDeaths = rendererDeaths,
                                recoveryAttempts = recoveryAttempts,
                                surfaceWidth = surfaceWidth,
                                surfaceHeight = surfaceHeight,
                                viewWidth = viewWidth,
                                viewHeight = viewHeight,
                                hostGeneration = generation,
                                appliedWidthDp = lastAppliedWidth.coerceAtLeast(0f),
                                appliedHeightDp = lastAppliedHeight.coerceAtLeast(0f),
                            ),
                        )
                    }
                }

                override fun onSelectionIntent(
                    chartId: String,
                    revision: Long,
                    sourceRowIndex: Int,
                    series: String,
                    x: String,
                ) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        RendererProbeState.selectionRow.set(sourceRowIndex)
                        RendererProbeState.selectionCount.incrementAndGet()
                        status.text = "Selected row=$sourceRowIndex · series=$series · x=$x · $chartId r$revision"
                    }
                }

                override fun onFocusExit() {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        RendererProbeState.focusExitCount.incrementAndGet()
                        status.text = "Focus exit requested; WebView released"
                        host?.webView?.clearFocus()
                        status.requestFocus()
                    }
                }

                override fun onSvgExport(chartId: String, revision: Long, svg: String) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        val written = runCatching {
                            File(filesDir, "exported-chart.svg").writeText(svg, Charsets.UTF_8)
                        }.isSuccess
                        if (written) {
                            RendererProbeState.completeSvg(svg)
                            status.text = "Exported SVG $chartId r$revision · ${svg.length} chars"
                        } else {
                            RendererProbeState.failExport("Could not write the probe export file")
                            status.text = "Export write failed"
                        }
                    }
                }

                override fun onPngExport(
                    chartId: String,
                    revision: Long,
                    width: Int,
                    height: Int,
                    png: ByteArray,
                ) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        val written = runCatching {
                            File(filesDir, "exported-chart.png").writeBytes(png)
                        }.isSuccess
                        if (written) {
                            RendererProbeState.completePng(width, height, png)
                            status.text = "Exported PNG $chartId r$revision · ${width}x$height · ${png.size} bytes"
                        } else {
                            RendererProbeState.failExport("Could not write the probe PNG file")
                            status.text = "PNG write failed"
                        }
                    }
                }

                override fun onExportError(message: String) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        RendererProbeState.failExport(message)
                        status.text = "Export failed: $message"
                    }
                }

                override fun onRendererError(code: String, detail: String) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        if (code == "renderer_process_gone") {
                            handleRendererGone("reported")
                            return@runOnUiThread
                        }
                        status.text = "Renderer error: $code — $detail"
                        finishWithError(code, detail)
                    }
                }

                override fun onExternalRequestBlocked(url: String) {
                    RendererProbeState.blockedRequests.incrementAndGet()
                }
            },
        )
        host = newHost
        newHost.initialize()
        newHost.webView?.let { web ->
            web.setBackgroundColor(Color.TRANSPARENT)
            web.isFocusable = true
            web.isFocusableInTouchMode = true
            val params = if (requestedWidthDp > 0f && requestedHeightDp > 0f) {
                val density = resources.displayMetrics.density
                FrameLayout.LayoutParams(
                    (requestedWidthDp * density).toInt(),
                    (requestedHeightDp * density).toInt(),
                )
            } else {
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            container.addView(web, params)
            web.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val width = right - left
                if (width > 0 && generation == hostGeneration) {
                    container.post { applySpec(generation) }
                }
            }
        }
    }

    private fun applySpec(generation: Int) {
        if (generation != hostGeneration) return
        val spec = currentSpec ?: return
        val web = host?.webView ?: return
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val widthDp = when {
            requestedWidthDp > 0f -> requestedWidthDp
            web.width > 0 -> web.width / density
            else -> 640f
        }
        val heightDp = when {
            requestedHeightDp > 0f -> requestedHeightDp
            web.height > 0 -> web.height / density
            else -> 320f
        }
        if (widthDp == lastAppliedWidth && heightDp == lastAppliedHeight) return
        lastAppliedWidth = widthDp
        lastAppliedHeight = heightDp
        Log.i("CloudyRendererProbe", "apply ${spec.chartId} r${spec.revision} ${widthDp}x${heightDp}dp")
        runCatching {
            host?.applyChartSpec(
                spec = spec,
                dark = false,
                palette = listOf(0x006A6A, 0x3559C7),
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }.onFailure {
            finishWithError("spec_rejected", it.message ?: "spec rejected")
        }
    }

    /**
     * Shared by the real onRenderProcessGone callback and the deterministic
     * test trigger. Retiring the generation first is what prevents a queued
     * completion from the dead host being published as the recovered result.
     */
    private fun handleRendererGone(reason: String) {
        if (destroyed) return
        hostGeneration++ // Retire callbacks from the dead host before it is disposed.
        rendererDeaths++
        RendererProbeState.noteRendererDeath()
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            Log.i("CloudyRendererProbe", "$reason renderer death $rendererDeaths; recovery exhausted")
            status.text = "Renderer terminated; recovery exhausted after $recoveryAttempts attempt(s)"
            host?.dispose()
            host = null
            container.removeAllViews()
            finishWithError(
                "renderer_process_gone",
                "renderer terminated; recovery attempts exhausted ($recoveryAttempts)",
            )
            return
        }
        recoveryAttempts++
        Log.i("CloudyRendererProbe", "$reason renderer death $rendererDeaths; recreating")
        status.text = "Renderer terminated; recreating ($recoveryAttempts/$MAX_RECOVERY_ATTEMPTS)…"
        host?.dispose()
        host = null
        container.removeAllViews()
        container.postDelayed({ createHost() }, RECOVERY_DELAY_MS)
    }

    /** Test-only lifecycle trigger; the real path is WebViewClient.onRenderProcessGone. */
    fun simulateRendererGoneForTest() {
        runOnUiThread { handleRendererGone("simulated") }
    }

    fun focusRendererForTest(): Boolean = host?.webView?.requestFocus() == true

    fun exportForTest() {
        host?.requestSvgExport()
    }

    fun exportPngForTest() {
        host?.requestPngExport()
    }


    fun rendererBoundsForTest(): Rect? {
        val web = host?.webView ?: return null
        if (web.width <= 0 || web.height <= 0) return null
        val location = IntArray(2)
        web.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + web.width, location[1] + web.height)
    }

    private fun finishWithError(code: String, detail: String) {
        RendererProbeState.complete(
            RendererProbeState.Result(
                vegaVersion = null,
                markCount = 0,
                sceneMarks = emptyMap(),
                svgPaths = 0,
                svgNodes = 0,
                interpreterAvailable = false,
                blockedRequests = RendererProbeState.blockedRequests.get(),
                error = "$code: $detail",
                widthPx = host?.webView?.width ?: 0,
                heightPx = host?.webView?.height ?: 0,
                rendererDeaths = rendererDeaths,
                recoveryAttempts = recoveryAttempts,
                hostGeneration = hostGeneration,
                appliedWidthDp = lastAppliedWidth.coerceAtLeast(0f),
                appliedHeightDp = lastAppliedHeight.coerceAtLeast(0f),
            ),
        )
    }

    override fun onDestroy() {
        destroyed = true
        hostGeneration++
        host?.dispose()
        host = null
        super.onDestroy()
    }

    private fun buildSpec(): ChartSpec = when (probeKind) {
        "line" -> ChartSpec(
            chartId = "probe-line",
            revision = 1,
            kind = ChartKind.LINE,
            title = "Regional sales",
            xAxis = AxisSpec("Month"),
            yAxis = AxisSpec("Sales", min = 0.0, max = 40.0),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(true, LegendPosition.RIGHT, "Region"),
            labels = LabelSpec(true),
            series = listOf(
                ChartSeries(
                    "North",
                    listOf(
                        point("2026-01-01", 10.0, "North", 0),
                        point("2026-02-01", 20.0, "North", 1),
                        point("2026-03-01", 30.0, "North", 2),
                    ),
                ),
                ChartSeries(
                    "South",
                    listOf(
                        point("2026-01-01", 1.0, "South", 0, 3),
                        point("2026-02-01", 2.0, "South", 1, 4),
                        point("2026-04-01", 4.0, "South", 3, 5),
                    ),
                ),
            ),
            rowCount = 6,
            truncated = false,
        )
        "area" -> ChartSpec(
            chartId = "probe-area",
            revision = 1,
            kind = ChartKind.AREA,
            title = "Observed sales",
            xAxis = AxisSpec("Month"),
            yAxis = AxisSpec("Sales", min = 0.0, max = 40.0),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(false, LegendPosition.RIGHT, null),
            labels = LabelSpec(true),
            series = listOf(
                ChartSeries(
                    "Values",
                    listOf(
                        point("2026-01-01", 10.0, "Values", 0),
                        point("2026-02-01", 20.0, "Values", 1),
                        point("2026-03-01", 30.0, "Values", 2),
                        point("2026-04-01", 40.0, "Values", 3),
                    ),
                ),
            ),
            rowCount = 4,
            truncated = false,
        )
        else -> ChartSpec(
            chartId = "probe-chart",
            revision = 1,
            kind = ChartKind.BAR,
            title = "Regional sales",
            xAxis = AxisSpec("Region"),
            yAxis = AxisSpec("Sales"),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(true, LegendPosition.BOTTOM, "Region"),
            labels = LabelSpec(false),
            series = listOf(
                ChartSeries(
                    "Values",
                    listOf(
                        point("A", 40.0, "Values", 0),
                        point("B", 60.0, "Values", 1),
                    ),
                ),
            ),
            rowCount = 2,
            truncated = false,
        )
    }

    private fun accessibleSummary(): String = when (probeKind) {
        "line" -> "Accessible summary: North 2026-01-01 = 10, 2026-02-01 = 20, 2026-03-01 = 30; " +
            "South 2026-01-01 = 1, 2026-02-01 = 2, 2026-04-01 = 4."
        "area" -> "Accessible summary: 2026-01-01 = 10, 2026-02-01 = 20, " +
            "2026-03-01 = 30, 2026-04-01 = 40."
        else -> "Accessible summary: region A = 40, region B = 60."
    }

    private fun point(
        x: String,
        y: Double,
        series: String,
        order: Int,
        sourceRowIndex: Int = order,
    ) = ChartPoint(
        x = x,
        xOrder = order,
        series = series,
        y = y,
        exactY = y.toString(),
        defined = true,
        sourceRowIndex = sourceRowIndex,
    )

    companion object {
        const val EXTRA_PROBE_KIND = "probe_kind"
        const val EXTRA_VIEW_WIDTH_DP = "view_width_dp"
        const val EXTRA_VIEW_HEIGHT_DP = "view_height_dp"
        private const val MAX_RECOVERY_ATTEMPTS = 2
        private const val RECOVERY_DELAY_MS = 250L
    }
}

object RendererProbeState {
    data class Result(
        val vegaVersion: String?,
        val markCount: Int,
        val sceneMarks: Map<String, Int>,
        val svgPaths: Int,
        val svgNodes: Int,
        val interpreterAvailable: Boolean,
        val blockedRequests: Int,
        val error: String?,
        val widthPx: Int = 0,
        val heightPx: Int = 0,
        val rendererDeaths: Int = 0,
        val recoveryAttempts: Int = 0,
        val surfaceWidth: Int = 0,
        val surfaceHeight: Int = 0,
        val viewWidth: Int = 0,
        val viewHeight: Int = 0,
        val hostGeneration: Int = 0,
        val appliedWidthDp: Float = 0f,
        val appliedHeightDp: Float = 0f,
    )

    @Volatile
    var latch: CountDownLatch = CountDownLatch(1)
        private set

    @Volatile
    private var recoveryLatch: CountDownLatch = CountDownLatch(1)

    @Volatile
    var result: Result? = null
        private set

    val blockedRequests = AtomicInteger(0)
    val selectionRow = AtomicInteger(-1)
    val selectionCount = AtomicInteger(0)
    val focusExitCount = AtomicInteger(0)

    @Volatile
    var exportedSvg: String? = null
        private set

    @Volatile
    private var svgLatch: CountDownLatch = CountDownLatch(1)

    @Volatile
    var exportedPng: ByteArray? = null
        private set

    @Volatile
    var pngWidth: Int = 0
        private set

    @Volatile
    var pngHeight: Int = 0
        private set

    @Volatile
    private var pngLatch: CountDownLatch = CountDownLatch(1)

    @Volatile
    var exportError: String? = null
        private set

    fun reset() {
        result = null
        blockedRequests.set(0)
        selectionRow.set(-1)
        selectionCount.set(0)
        focusExitCount.set(0)
        exportedSvg = null
        exportedPng = null
        pngWidth = 0
        pngHeight = 0
        exportError = null
        svgLatch = CountDownLatch(1)
        pngLatch = CountDownLatch(1)
        latch = CountDownLatch(1)
        recoveryLatch = CountDownLatch(1)
    }

    fun completeSvg(svg: String) {
        exportedSvg = svg
        exportError = null
        svgLatch.countDown()
    }

    fun completePng(width: Int, height: Int, png: ByteArray) {
        pngWidth = width
        pngHeight = height
        exportedPng = png
        exportError = null
        pngLatch.countDown()
    }

    fun awaitPng(timeoutSeconds: Long): ByteArray? {
        pngLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        return exportedPng
    }

    fun failExport(message: String) {
        exportError = message
        svgLatch.countDown()
        pngLatch.countDown()
    }

    fun awaitSvg(timeoutSeconds: Long): String? {
        svgLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        return exportedSvg
    }

    fun noteRendererDeath() {
        // A subsequent render completion is the recovery barrier.
        recoveryLatch = CountDownLatch(1)
    }

    fun complete(value: Result) {
        result = value
        latch.countDown()
        recoveryLatch.countDown()
    }

    fun await(timeoutSeconds: Long): Result? {
        latch.await(timeoutSeconds, TimeUnit.SECONDS)
        return result
    }

    fun awaitRecovery(timeoutSeconds: Long): Result? {
        recoveryLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        return result
    }
}
