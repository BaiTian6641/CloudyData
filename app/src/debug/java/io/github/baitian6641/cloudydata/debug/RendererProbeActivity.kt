package io.github.baitian6641.cloudydata.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.baitian6641.cloudydata.renderer.VegaRendererHost
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only qualification surface for the bounded renderer. It is not part of
 * the shipped application's navigation and does not own workbook state.
 */
class RendererProbeActivity : Activity(), VegaRendererHost.Listener {
    private lateinit var host: VegaRendererHost
    private lateinit var status: TextView

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
            host.applyBarSpec(
                chartId = "probe-chart",
                revision = 1,
                values = listOf("A" to 40.0, "B" to 60.0),
                dark = false,
                palette = listOf(0x006A6A, 0x3559C7),
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
