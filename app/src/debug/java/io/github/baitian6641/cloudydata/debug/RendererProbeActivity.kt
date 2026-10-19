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
import io.github.baitian6641.cloudydata.model.RowSelectionReducer
import io.github.baitian6641.cloudydata.model.RowSelectionState
import io.github.baitian6641.cloudydata.model.SelectionMode
import io.github.baitian6641.cloudydata.chart.ChartPoint
import io.github.baitian6641.cloudydata.chart.ChartSeries
import io.github.baitian6641.cloudydata.chart.ChartSpec
import io.github.baitian6641.cloudydata.chart.GridlineSpec
import io.github.baitian6641.cloudydata.chart.LabelSpec
import io.github.baitian6641.cloudydata.chart.LegendSpec
import io.github.baitian6641.cloudydata.model.BarOrientation
import io.github.baitian6641.cloudydata.model.BarStackMode
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.renderer.VegaRendererHost
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only qualification surface for the bounded renderer. It is not part of
 * the shipped application's navigation and does not own workbook state.
 */
@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
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
    private val exportTitle: String? by lazy { intent.getStringExtra(EXTRA_EXPORT_TITLE) }
    private val exportSubtitle: String? by lazy { intent.getStringExtra(EXTRA_EXPORT_SUBTITLE) }
    private val exportCaption: String? by lazy { intent.getStringExtra(EXTRA_EXPORT_CAPTION) }
    private val exportSource: String? by lazy { intent.getStringExtra(EXTRA_EXPORT_SOURCE) }
    private val requestChartBorder: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_CHART_BORDER, false)
    }
    private val requestDark: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_DARK, false)
    }
    private val requestTransparentExport: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_TRANSPARENT_EXPORT, false)
    }
    private val requestMinorGrid: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_MINOR_GRID, false)
    }
    private val requestedHiddenSeries: String? by lazy {
        intent.getStringExtra(EXTRA_HIDDEN_SERIES)
    }
    private val requestHideMarkers: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_HIDE_MARKERS, false)
    }
    private val requestValueFormat by lazy {
        intent.getStringExtra(EXTRA_VALUE_FORMAT)?.let { extra ->
            runCatching {
                io.github.baitian6641.cloudydata.model.ValueDisplayFormat.valueOf(extra)
            }.getOrNull()
        }
    }
    private val requestCurrencyCode by lazy {
        runCatching {
            io.github.baitian6641.cloudydata.model.CurrencyCode.valueOf(
                intent.getStringExtra(EXTRA_CURRENCY_CODE)
                    ?: io.github.baitian6641.cloudydata.model.CurrencyCode.USD.name,
            )
        }.getOrDefault(io.github.baitian6641.cloudydata.model.CurrencyCode.USD)
    }
    private val requestCategoryPalette by lazy {
        runCatching {
            io.github.baitian6641.cloudydata.model.CategoryPalette.valueOf(
                intent.getStringExtra(EXTRA_CATEGORY_PALETTE)
                    ?: io.github.baitian6641.cloudydata.model.CategoryPalette.CLOUDY.name,
            )
        }.getOrDefault(io.github.baitian6641.cloudydata.model.CategoryPalette.CLOUDY)
    }
    private val requestLegendSample by lazy {
        runCatching {
            io.github.baitian6641.cloudydata.model.LegendMarkSample.valueOf(
                intent.getStringExtra(EXTRA_LEGEND_SAMPLE)
                    ?: io.github.baitian6641.cloudydata.model.LegendMarkSample.MARKER.name,
            )
        }.getOrDefault(io.github.baitian6641.cloudydata.model.LegendMarkSample.MARKER)
    }
    private val requestLineOpacity by lazy {
        intent.getStringExtra(EXTRA_LINE_OPACITY)?.toDoubleOrNull()
    }
    private val requestLineDash by lazy {
        runCatching {
            io.github.baitian6641.cloudydata.model.LineDashStyle.valueOf(
                intent.getStringExtra(EXTRA_LINE_DASH)
                    ?: io.github.baitian6641.cloudydata.model.LineDashStyle.SOLID.name,
            )
        }.getOrDefault(io.github.baitian6641.cloudydata.model.LineDashStyle.SOLID)
    }
    private val requestLineInterpolation by lazy {
        runCatching {
            io.github.baitian6641.cloudydata.model.LineInterpolation.valueOf(
                intent.getStringExtra(EXTRA_LINE_INTERPOLATION)
                    ?: io.github.baitian6641.cloudydata.model.LineInterpolation.LINEAR.name,
            )
        }.getOrDefault(io.github.baitian6641.cloudydata.model.LineInterpolation.LINEAR)
    }
    private val requestMarkerOpacity by lazy {
        intent.getStringExtra(EXTRA_MARKER_OPACITY)?.toDoubleOrNull()
    }
    private val requestMarkerBorder by lazy {
        intent.getBooleanExtra(EXTRA_MARKER_BORDER, false)
    }
    private val requestMarkerBorderWidth by lazy {
        intent.getStringExtra(EXTRA_MARKER_BORDER_WIDTH)?.toDoubleOrNull()
    }
    private val requestMarkerShape by lazy {
        runCatching {
            io.github.baitian6641.cloudydata.model.MarkerShape.valueOf(
                intent.getStringExtra(EXTRA_MARKER_SHAPE)
                    ?: io.github.baitian6641.cloudydata.model.MarkerShape.CIRCLE.name,
            )
        }.getOrDefault(io.github.baitian6641.cloudydata.model.MarkerShape.CIRCLE)
    }
    private val requestedWidthDp: Float by lazy {
        intent.getStringExtra(EXTRA_VIEW_WIDTH_DP)?.toFloatOrNull() ?: 0f
    }
    private val requestedHeightDp: Float by lazy {
        intent.getStringExtra(EXTRA_VIEW_HEIGHT_DP)?.toFloatOrNull() ?: 0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RendererProbeState.reset()
        val baseSpec = buildSpec()
        currentSpec = baseSpec.copy(
            title = exportTitle ?: baseSpec.title,
            subtitle = exportSubtitle ?: baseSpec.subtitle,
            caption = exportCaption ?: baseSpec.caption,
            sourceNote = exportSource ?: baseSpec.sourceNote,
            chartBorder = requestChartBorder,
            transparentExportBackground = requestTransparentExport,
            gridlines = baseSpec.gridlines.copy(minor = requestMinorGrid),
            hiddenSeries = requestedHiddenSeries?.let(::setOf) ?: emptySet(),
            markers = baseSpec.markers.copy(
                show = baseSpec.markers.show && !requestHideMarkers,
                shape = requestMarkerShape,
                opacity = requestMarkerOpacity,
                border = requestMarkerBorder,
                borderWidth = requestMarkerBorderWidth,
            ),
            lineInterpolation = requestLineInterpolation,
            lineDashStyle = requestLineDash,
            lineOpacity = requestLineOpacity,
            legend = baseSpec.legend.copy(markSample = requestLegendSample),
            valueFormat = requestValueFormat ?: baseSpec.valueFormat,
            currencyCode = requestCurrencyCode,
        )
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
                    svgTexts: List<String>,
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
                                svgTexts = svgTexts,
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

                override fun onLegendToggle(
                    chartId: String,
                    revision: Long,
                    series: String,
                ) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        RendererProbeState.legendToggles.add(series)
                        status.text = "Legend toggle: $series · $chartId r$revision"
                    }
                }

                override fun onSelectionIntent(
                    chartId: String,
                    revision: Long,
                    sourceRowIndex: Int,
                    series: String,
                    x: String,
                    mode: SelectionMode,
                ) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        RendererProbeState.applySelection(sourceRowIndex, mode)
                        host?.applySelection(chartId, revision, RendererProbeState.selectionRows)
                        status.text = "Selected row=$sourceRowIndex · mode=$mode · " +
                            "rows=${RendererProbeState.selectionRows.size} · series=$series · x=$x · " +
                            "$chartId r$revision"
                    }
                }

                override fun onLassoSelection(
                    chartId: String,
                    revision: Long,
                    sourceRowIndices: Set<Int>,
                    additive: Boolean,
                ) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        RendererProbeState.applyLasso(sourceRowIndices, additive)
                        host?.applySelection(chartId, revision, RendererProbeState.selectionRows)
                        status.text = "Lasso selected rows=${RendererProbeState.selectionRows.size} · " +
                            "additive=$additive · $chartId r$revision"
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
                    purpose: String,
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

                override fun onExportBundle(
                    chartId: String,
                    revision: Long,
                    svg: String,
                    png: ByteArray,
                    width: Int,
                    height: Int,
                ) {
                    runOnUiThread {
                        if (generation != hostGeneration || destroyed) return@runOnUiThread
                        val svgWritten = runCatching {
                            File(filesDir, "bundle-chart.svg").writeText(svg, Charsets.UTF_8)
                        }.isSuccess
                        val pngWritten = runCatching {
                            File(filesDir, "bundle-chart.png").writeBytes(png)
                        }.isSuccess
                        if (svgWritten && pngWritten) {
                            RendererProbeState.completeBundle(svg, png, width, height)
                            status.text = "Exported bundle $chartId r$revision · ${width}x$height"
                        } else {
                            RendererProbeState.failExport("Could not write the probe bundle files")
                            status.text = "Bundle write failed"
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
            web.setOnTouchListener { view, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    newHost.notePointerMetaState(event.metaState)
                }
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    view.performClick()
                }
                false
            }
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
                dark = requestDark,
                palette = io.github.baitian6641.cloudydata.chart.categoryPaletteColors(
                    requestCategoryPalette,
                    requestDark,
                ).map { it.toInt() },
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

    fun exportBundleForTest() {
        host?.requestExportBundle()
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
            sourceNote = "Source: probe fixture",
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
        "groupedbar", "stackedbar" -> barSeriesSpec(probeKind == "stackedbar")
        "percentbar" -> percentBarSpec()
        "horizontalbar" -> horizontalBarSpec()
        "palette" -> ChartSpec(
            chartId = "probe-palette",
            revision = 1,
            kind = ChartKind.LINE,
            title = "Eight category palette",
            xAxis = AxisSpec("Month"),
            yAxis = AxisSpec("Sales"),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(true, LegendPosition.RIGHT, "Series"),
            labels = LabelSpec(false),
            sourceNote = "Source: probe fixture",
            series = (0 until 8).map { seriesIndex ->
                ChartSeries(
                    "Series ${seriesIndex + 1}",
                    listOf(0, 1, 2).map { pointIndex ->
                        point(
                            "M${pointIndex + 1}",
                            10.0 + pointIndex * 5.0 + seriesIndex,
                            "Series ${seriesIndex + 1}",
                            pointIndex,
                            seriesIndex * 3 + pointIndex,
                        )
                    },
                )
            },
            rowCount = 24,
            truncated = false,
        )
        "format" -> ChartSpec(
            chartId = "probe-format",
            revision = 1,
            kind = ChartKind.BAR,
            title = "Formatted values",
            xAxis = AxisSpec("Region"),
            yAxis = AxisSpec("Sales"),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(false, LegendPosition.RIGHT, null),
            labels = LabelSpec(
                true,
                collisionPolicy = io.github.baitian6641.cloudydata.model.LabelCollisionPolicy.HIDE_CROWDED,
            ),
            sourceNote = "Source: probe fixture",
            series = listOf(
                ChartSeries(
                    "Values",
                    listOf(
                        point("A", 1234.567, "Values", 0),
                        point("B", 2000.0, "Values", 1),
                    ),
                ),
            ),
            rowCount = 2,
            truncated = false,
            valueDecimals = 2,
        )
        "crowdedlabel" -> ChartSpec(
            chartId = "probe-crowded-label",
            revision = 1,
            kind = ChartKind.BAR,
            title = "Crowded labels",
            xAxis = AxisSpec("Category"),
            yAxis = AxisSpec("Sales"),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(false, LegendPosition.RIGHT, null),
            labels = LabelSpec(
                true,
                collisionPolicy = io.github.baitian6641.cloudydata.model.LabelCollisionPolicy.HIDE_CROWDED,
            ),
            sourceNote = "Source: probe fixture",
            series = listOf(
                ChartSeries(
                    "Values",
                    (0 until 60).map { index ->
                        point("C$index", 1234.567 + index * 10.0, "Values", index)
                    },
                ),
            ),
            rowCount = 60,
            truncated = false,
            valueDecimals = 2,
        )
        "percent" -> ChartSpec(
            chartId = "probe-percent",
            revision = 1,
            kind = ChartKind.BAR,
            title = "Percent values",
            xAxis = AxisSpec("Region"),
            yAxis = AxisSpec("Rate"),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(false, LegendPosition.RIGHT, null),
            labels = LabelSpec(true),
            sourceNote = "Source: probe fixture",
            series = listOf(
                ChartSeries(
                    "Values",
                    listOf(
                        point("A", 0.125, "Values", 0),
                        point("B", 0.25, "Values", 1),
                    ),
                ),
            ),
            rowCount = 2,
            truncated = false,
            valueDecimals = 1,
            valueFormat = io.github.baitian6641.cloudydata.model.ValueDisplayFormat.PERCENT,
        )
        "currency" -> ChartSpec(
            chartId = "probe-currency",
            revision = 1,
            kind = ChartKind.BAR,
            title = "Currency values",
            xAxis = AxisSpec("Region"),
            yAxis = AxisSpec("Sales"),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(false, LegendPosition.RIGHT, null),
            labels = LabelSpec(true),
            sourceNote = "Source: probe fixture",
            series = listOf(
                ChartSeries(
                    "Values",
                    listOf(
                        point("A", -1234.5, "Values", 0),
                        point("B", 2000.0, "Values", 1),
                    ),
                ),
            ),
            rowCount = 2,
            truncated = false,
            valueFormat = io.github.baitian6641.cloudydata.model.ValueDisplayFormat.CURRENCY,
            currencyCode = io.github.baitian6641.cloudydata.model.CurrencyCode.EUR,
        )
        "crowdedline" -> ChartSpec(
            chartId = "probe-crowded-line",
            revision = 1,
            kind = ChartKind.LINE,
            title = "Crowded series labels",
            xAxis = AxisSpec("Category"),
            yAxis = AxisSpec("Sales"),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(true, LegendPosition.RIGHT, "Region"),
            labels = LabelSpec(
                true,
                collisionPolicy = io.github.baitian6641.cloudydata.model.LabelCollisionPolicy.HIDE_CROWDED,
            ),
            sourceNote = "Source: probe fixture",
            series = listOf("North", "South", "East").mapIndexed { seriesIndex, name ->
                ChartSeries(
                    name,
                    (0 until 40).map { index ->
                        point(
                            "C$index",
                            1234.567 + index * 10.0 + seriesIndex * 2.0,
                            name,
                            index,
                        )
                    },
                )
            },
            rowCount = 120,
            truncated = false,
            valueDecimals = 2,
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
            sourceNote = "Source: probe fixture",
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
            sourceNote = "Source: probe fixture",
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

    private fun horizontalBarSpec(): ChartSpec = ChartSpec(
        chartId = "probe-horizontal-bar",
        revision = 1,
        kind = ChartKind.BAR,
        title = "Horizontal sales",
        xAxis = AxisSpec("Category"),
        yAxis = AxisSpec("Value", min = 0.0, max = 100.0),
        gridlines = GridlineSpec(true),
        legend = LegendSpec(true, LegendPosition.BOTTOM, "Series"),
        labels = LabelSpec(false),
        sourceNote = "Source: probe fixture",
        series = listOf(
            ChartSeries(
                "North",
                listOf(
                    ChartPoint("Jan", 0, "North", 60.0, "60", true, 0, stackBase = 0.0, seriesIndex = 0),
                ),
            ),
            ChartSeries(
                "South",
                listOf(
                    ChartPoint("Jan", 0, "South", 40.0, "40", true, 1, stackBase = 60.0, seriesIndex = 1),
                ),
            ),
        ),
        rowCount = 2,
        truncated = false,
        barStackMode = BarStackMode.STACKED,
        barHasSeries = true,
        barOrientation = BarOrientation.HORIZONTAL,
    )

    private fun percentBarSpec(): ChartSpec = ChartSpec(
        chartId = "probe-percent-bar",
        revision = 1,
        kind = ChartKind.BAR,
        title = "100% stacked sales",
        xAxis = AxisSpec("Category"),
        yAxis = AxisSpec("Share", min = 0.0, max = 1.0),
        gridlines = GridlineSpec(true),
        legend = LegendSpec(true, LegendPosition.BOTTOM, "Series"),
        labels = LabelSpec(true),
        sourceNote = "Source: probe fixture",
        series = listOf(
            ChartSeries(
                "North",
                listOf(
                    ChartPoint("Jan", 0, "North", 0.6, "60", true, 0, stackBase = 0.0, seriesIndex = 0),
                ),
            ),
            ChartSeries(
                "South",
                listOf(
                    ChartPoint("Jan", 0, "South", 0.4, "40", true, 1, stackBase = 0.6, seriesIndex = 1),
                ),
            ),
        ),
        rowCount = 2,
        truncated = false,
        valueDecimals = 1,
        valueFormat = io.github.baitian6641.cloudydata.model.ValueDisplayFormat.PERCENT,
        barStackMode = BarStackMode.PERCENT_STACKED,
        barHasSeries = true,
    )

    private fun barSeriesSpec(stacked: Boolean): ChartSpec {
        fun point(x: String, order: Int, series: String, y: Double, base: Double, row: Int) =
            ChartPoint(
                x = x,
                xOrder = order,
                series = series,
                y = y,
                exactY = y.toString(),
                defined = true,
                sourceRowIndex = row,
                stackBase = base,
                seriesIndex = if (series == "North") 0 else 1,
            )
        val north = ChartSeries(
            "North",
            listOf(
                point("Jan", 0, "North", 10.0, 0.0, 0),
                point("Feb", 1, "North", 20.0, 0.0, 1),
                point("Mar", 2, "North", 30.0, 0.0, 2),
            ),
        )
        val south = ChartSeries(
            "South",
            listOf(
                point("Jan", 0, "South", 5.0, if (stacked) 10.0 else 0.0, 3),
                point("Feb", 1, "South", 7.0, if (stacked) 20.0 else 0.0, 4),
                point("Mar", 2, "South", 2.0, if (stacked) 30.0 else 0.0, 5),
            ),
        )
        return ChartSpec(
            chartId = if (stacked) "probe-stacked-bar" else "probe-grouped-bar",
            revision = 1,
            kind = ChartKind.BAR,
            title = if (stacked) "Stacked sales" else "Grouped sales",
            xAxis = AxisSpec("Category"),
            yAxis = AxisSpec("Value", min = 0.0),
            gridlines = GridlineSpec(true),
            legend = LegendSpec(true, LegendPosition.BOTTOM, "Series"),
            labels = LabelSpec(false),
            sourceNote = "Source: probe fixture",
            series = listOf(north, south),
            barStackMode = if (stacked) BarStackMode.STACKED else BarStackMode.NONE,
            barHasSeries = true,
            rowCount = 6,
            truncated = false,
        )
    }

    private fun accessibleSummary(): String = when (probeKind) {
        "groupedbar" -> "Accessible summary: North and South across Jan, Feb, Mar."
        "stackedbar" -> "Accessible summary: stacked North and South across Jan, Feb, Mar."
        "percentbar" -> "Accessible summary: Jan is 60% North and 40% South."
        "horizontalbar" -> "Accessible summary: Jan has North 60 and South 40."
        "palette" -> "Accessible summary: eight series across three months."
        "line" -> "Accessible summary: North 2026-01-01 = 10, 2026-02-01 = 20, 2026-03-01 = 30; " +
            "South 2026-01-01 = 1, 2026-02-01 = 2, 2026-04-01 = 4."
        "format" -> "Accessible summary: A = 1234.567, B = 2000.0."
        "crowdedlabel" -> "Accessible summary: 60 crowded categories."
        "crowdedline" -> "Accessible summary: 40 crowded categories across three series."
        "percent" -> "Accessible summary: A = 0.125, B = 0.25."
        "currency" -> "Accessible summary: A = -1234.5, B = 2000.0."
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
        const val EXTRA_EXPORT_TITLE = "export_title"
        const val EXTRA_EXPORT_SUBTITLE = "export_subtitle"
        const val EXTRA_EXPORT_CAPTION = "export_caption"
        const val EXTRA_EXPORT_SOURCE = "export_source"
        const val EXTRA_CHART_BORDER = "chart_border"
        const val EXTRA_DARK = "dark"
        const val EXTRA_TRANSPARENT_EXPORT = "transparent_export"
        const val EXTRA_MINOR_GRID = "minor_grid"
        const val EXTRA_HIDDEN_SERIES = "hidden_series"
        const val EXTRA_HIDE_MARKERS = "hide_markers"
        const val EXTRA_MARKER_SHAPE = "marker_shape"
        const val EXTRA_MARKER_OPACITY = "marker_opacity"
        const val EXTRA_MARKER_BORDER = "marker_border"
        const val EXTRA_MARKER_BORDER_WIDTH = "marker_border_width"
        const val EXTRA_LINE_INTERPOLATION = "line_interpolation"
        const val EXTRA_LINE_DASH = "line_dash"
        const val EXTRA_LINE_OPACITY = "line_opacity"
        const val EXTRA_LEGEND_SAMPLE = "legend_sample"
        const val EXTRA_VALUE_FORMAT = "value_format"
        const val EXTRA_CURRENCY_CODE = "currency_code"
        const val EXTRA_CATEGORY_PALETTE = "category_palette"
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
        val svgTexts: List<String> = emptyList(),
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

    @Volatile
    var selectionRows: Set<Int> = emptySet()
        private set

    @Volatile
    var selectionMode: SelectionMode = SelectionMode.REPLACE
        private set
    val focusExitCount = AtomicInteger(0)
    val legendToggles = CopyOnWriteArrayList<String>()

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

    data class BundleExport(
        val svg: String,
        val png: ByteArray,
        val width: Int,
        val height: Int,
    )

    @Volatile
    var bundleExport: BundleExport? = null
        private set

    @Volatile
    private var bundleLatch: CountDownLatch = CountDownLatch(1)

    @Volatile
    var exportError: String? = null
        private set

    fun reset() {
        result = null
        blockedRequests.set(0)
        selectionRow.set(-1)
        selectionCount.set(0)
        selectionRows = emptySet()
        selectionMode = SelectionMode.REPLACE
        selectionState = RowSelectionState()
        focusExitCount.set(0)
        legendToggles.clear()
        exportedSvg = null
        exportedPng = null
        pngWidth = 0
        pngHeight = 0
        bundleExport = null
        exportError = null
        svgLatch = CountDownLatch(1)
        pngLatch = CountDownLatch(1)
        bundleLatch = CountDownLatch(1)
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

    fun completeBundle(svg: String, png: ByteArray, width: Int, height: Int) {
        bundleExport = BundleExport(svg, png, width, height)
        exportError = null
        bundleLatch.countDown()
    }

    fun awaitBundle(timeoutSeconds: Long): BundleExport? {
        bundleLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        return bundleExport
    }

    fun failExport(message: String) {
        exportError = message
        svgLatch.countDown()
        pngLatch.countDown()
        bundleLatch.countDown()
    }

    fun awaitSvg(timeoutSeconds: Long): String? {
        svgLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        return exportedSvg
    }

    private var selectionState = RowSelectionState()

    fun applySelection(row: Int, mode: SelectionMode) {
        selectionState = RowSelectionReducer.select(selectionState, row, mode)
        selectionRow.set(row)
        selectionRows = selectionState.rows
        selectionMode = mode
        selectionCount.incrementAndGet()
    }

    fun applyLasso(rows: Set<Int>, additive: Boolean) {
        selectionState = RowSelectionReducer.selectMany(selectionState, rows, additive)
        selectionRow.set(selectionState.primary)
        selectionRows = selectionState.rows
        selectionMode = if (additive) SelectionMode.TOGGLE else SelectionMode.REPLACE
        selectionCount.incrementAndGet()
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
