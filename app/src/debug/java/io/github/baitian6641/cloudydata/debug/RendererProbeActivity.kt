package io.github.baitian6641.cloudydata.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only qualification surface for the bounded renderer. It is not part of
 * the shipped application's navigation and does not own workbook state.
 */
@SuppressLint("SetTextI18n")
class RendererProbeActivity : Activity(), VegaRendererHost.Listener {
    private lateinit var host: VegaRendererHost
    private lateinit var status: TextView
    private val probeKind: String by lazy { intent.getStringExtra(EXTRA_PROBE_KIND) ?: "bar" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RendererProbeState.reset()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 247))
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply {
            text = "Starting offline renderer…"
            setTextColor(Color.rgb(24, 36, 39))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val container = FrameLayout(this)
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(
            container,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        val summary = TextView(this).apply {
            text = "Accessible summary: region A = 40, region B = 60."
            setTextColor(Color.rgb(76, 96, 102))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        root.addView(summary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        host = VegaRendererHost(this, this)
        host.initialize()
        host.webView?.let { web ->
            container.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onReady(vegaVersion: String, interpreterAvailable: Boolean) {
        runOnUiThread {
            status.text = "Ready · Vega $vegaVersion · interpreter=$interpreterAvailable"
            if (!interpreterAvailable) {
                finishWithError("interpreter_unavailable", "The CSP interpreter was not exposed.")
                return@runOnUiThread
            }
            val spec = if (probeKind == "line") {
                ChartSpec(
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
                                point("2026-01-01", 1.0, "South", 0),
                                point("2026-02-01", 2.0, "South", 1),
                                point("2026-04-01", 4.0, "South", 3),
                            ),
                        ),
                    ),
                    rowCount = 6,
                    truncated = false,
                )
            } else {
                ChartSpec(
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
            host.applyChartSpec(
                spec = spec,
                dark = false,
                palette = listOf(0x006A6A, 0x3559C7),
                widthDp = 640f,
                heightDp = 320f,
            )
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
    ) {
        runOnUiThread {
            status.text = "Rendered $chartId r$revision · 2 values · scene=$sceneMarks · svgPaths=$svgPaths · svgNodes=$svgNodes · Vega $vegaVersion"
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
                ),
            )
        }
    }

    override fun onRendererError(code: String, detail: String) {
        runOnUiThread {
            status.text = "Renderer error: $code — $detail"
            finishWithError(code, detail)
        }
    }

    override fun onExternalRequestBlocked(url: String) {
        RendererProbeState.blockedRequests.incrementAndGet()
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
            ),
        )
    }

    override fun onDestroy() {
        if (::host.isInitialized) host.dispose()
        super.onDestroy()
    }

    private fun point(x: String, y: Double, series: String, order: Int) = ChartPoint(
        x = x,
        xOrder = order,
        series = series,
        y = y,
        exactY = y.toString(),
        defined = true,
        sourceRowIndex = order,
    )

    companion object {
        const val EXTRA_PROBE_KIND = "probe_kind"
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
    )

    @Volatile
    var latch: CountDownLatch = CountDownLatch(1)
        private set

    @Volatile
    var result: Result? = null
        private set

    val blockedRequests = AtomicInteger(0)

    fun reset() {
        result = null
        blockedRequests.set(0)
        latch = CountDownLatch(1)
    }

    fun complete(value: Result) {
        result = value
        latch.countDown()
    }

    fun await(timeoutSeconds: Long): Result? {
        latch.await(timeoutSeconds, TimeUnit.SECONDS)
        return result
    }
}
