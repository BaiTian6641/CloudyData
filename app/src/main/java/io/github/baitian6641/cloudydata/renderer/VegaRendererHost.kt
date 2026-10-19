package io.github.baitian6641.cloudydata.renderer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.baitian6641.cloudydata.BuildConfig
import io.github.baitian6641.cloudydata.chart.ChartSpec
import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * One bounded, offline Vega surface. The host exposes only Ready/RenderComplete/
 * RendererError/SelectionIntent-style typed messages; it has no SQL, file,
 * network or credential path. Message validation and origin checks happen in
 * both directions.
 */
class VegaRendererHost(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady(vegaVersion: String, interpreterAvailable: Boolean)
        fun onRenderComplete(
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
        )
        fun onRendererError(code: String, detail: String)
        fun onExternalRequestBlocked(url: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val assetLoader = WebViewAssetLoader.Builder()
        .setDomain(ASSET_DOMAIN)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    var webView: WebView? = null
        private set

    private var disposed = false
    private var initialized = false
    private var ready = false
    private var pendingSpec: PendingSpec? = null
    private val revisionGate = RendererRevisionGate()

    private data class PendingSpec(
        val spec: ChartSpec,
        val dark: Boolean,
        val palette: List<Int>,
        val widthDp: Float,
        val heightDp: Float,
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        if (initialized || disposed) return
        initialized = true
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            listener.onRendererError("web_message_listener_unavailable", "This WebView lacks WebMessageListener support.")
            return
        }
        val view = WebView(context)
        webView = view
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkLoads = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        view.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == ASSET_DOMAIN) {
                    return assetLoader.shouldInterceptRequest(uri)
                        ?: blockedResponse("asset_not_found")
                }
                listener.onExternalRequestBlocked(uri.toString())
                return blockedResponse("external_request_blocked")
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // The only navigable document is loaded explicitly with loadUrl.
                listener.onExternalRequestBlocked(request.url.toString())
                return true
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?,
            ): Boolean {
                if (disposed) return true
                listener.onRendererError(
                    "renderer_process_gone",
                    "WebView renderer terminated (crashed=${detail?.didCrash()}).",
                )
                return true
            }
        }
        WebViewCompat.addWebMessageListener(
            view,
            BRIDGE_NAME,
            setOf("https://$ASSET_DOMAIN"),
        ) { _, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, _ ->
            validateAndDispatch(message, sourceOrigin, isMainFrame)
        }
        view.loadUrl("https://$ASSET_DOMAIN/assets/renderer/index.html")
    }

    fun applyChartSpec(
        spec: ChartSpec,
        dark: Boolean,
        palette: List<Int>,
        widthDp: Float = 640f,
        heightDp: Float = 320f,
    ) {
        require(spec.chartId.length in 1..128) { "invalid chart id" }
        require(spec.revision >= 0) { "invalid revision" }
        require(spec.series.isNotEmpty()) { "chart has no series" }
        require(spec.version == ChartSpec.SPEC_VERSION) { "unsupported chart spec version" }
        require(spec.pointCount in 1..MAX_CHART_POINTS) { "chart point count out of bounds" }
        require(palette.isNotEmpty()) { "palette is empty" }
        require(widthDp >= 160f && heightDp >= 120f) { "renderer surface is too small" }
        val request = PendingSpec(spec, dark, palette, widthDp, heightDp)
        if (!ready) {
            pendingSpec = request
            return
        }
        dispatch(request)
    }

    private fun dispatch(request: PendingSpec) {
        val view = webView ?: error("renderer is not initialized")
        val specJson = try {
            VegaSpecBuilder.build(
                request.spec,
                request.dark,
                request.palette,
                request.widthDp,
                request.heightDp,
            )
        } catch (error: Throwable) {
            listener.onRendererError("spec_rejected", error.message ?: "Chart specification was rejected.")
            return
        }
        val message = JSONObject()
            .put("type", "ApplyChartSpec")
            .put("chartId", request.spec.chartId)
            .put("revision", request.spec.revision)
            .put("specVersion", request.spec.version)
            .put("spec", specJson)
        if (message.toString().length > MAX_SPEC_CHARS) {
            listener.onRendererError(
                "spec_too_large",
                "The chart specification exceeds the bounded renderer payload.",
            )
            return
        }
        revisionGate.dispatch(request.spec.chartId, request.spec.revision)
        mainHandler.post {
            if (!disposed && webView === view) {
                view.evaluateJavascript(
                    "window.cloudyReceive(${JSONObject.quote(message.toString())})",
                    null,
                )
            }
        }
    }

    fun dispose() {
        disposed = true
        ready = false
        pendingSpec = null
        val view = webView ?: return
        webView = null
        mainHandler.post {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                runCatching { WebViewCompat.removeWebMessageListener(view, BRIDGE_NAME) }
            }
            runCatching { view.stopLoading() }
            runCatching { view.loadUrl("about:blank") }
            runCatching { view.destroy() }
        }
    }

    private fun validateAndDispatch(message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean) {
        if (!isMainFrame || sourceOrigin.toString() != "https://$ASSET_DOMAIN" || message.type != WebMessageCompat.TYPE_STRING) {
            listener.onRendererError("invalid_message_origin", "Rejected a renderer message with an unexpected origin or frame.")
            return
        }
        val text = message.data ?: return
        if (text.length > MAX_MESSAGE_CHARS) {
            listener.onRendererError("message_too_large", "Renderer message exceeded the control-message limit.")
            return
        }
        val json = runCatching { JSONObject(text) }.getOrElse {
            listener.onRendererError("invalid_json", "Renderer message was not valid JSON.")
            return
        }
        when (json.optString("type")) {
            "Ready" -> {
                if (json.optInt("protocol", 0) != 1) {
                    listener.onRendererError("protocol_mismatch", "Renderer protocol version is unsupported.")
                    return
                }
                ready = true
                listener.onReady(json.optString("vegaVersion"), json.optBoolean("interpreter", false))
                pendingSpec?.let {
                    pendingSpec = null
                    dispatch(it)
                }
            }
            "RenderComplete" -> {
                val chartId = json.optString("chartId")
                val revision = json.optLong("revision", -1)
                if (!revisionGate.isCurrent(chartId, revision)) {
                    // A superseded render is discarded without error; the current
                    // request remains pending and will report its own completion.
                    return
                }
                val marksJson = json.optJSONObject("sceneMarks")
                val marks = buildMap {
                    if (marksJson != null) {
                        marksJson.keys().forEach { key -> put(key, marksJson.optInt(key, 0)) }
                    }
                }
                listener.onRenderComplete(
                    chartId,
                    revision,
                    json.optInt("markCount", -1),
                    marks,
                    json.optInt("svgPaths", -1),
                    json.optInt("svgNodes", -1),
                    json.optString("vegaVersion"),
                    json.optInt("surfaceWidth", 0),
                    json.optInt("surfaceHeight", 0),
                    json.optInt("viewWidth", 0),
                    json.optInt("viewHeight", 0),
                )
            }
            "RendererError" -> {
                val errorChartId = json.optString("chartId")
                val errorRevision = json.optLong("revision", -1)
                if (errorChartId.isNotEmpty() && errorRevision >= 0 &&
                    !revisionGate.isCurrent(errorChartId, errorRevision)
                ) {
                    return
                }
                listener.onRendererError(
                    json.optString("code", "renderer_error"),
                    json.optString("detail", "Renderer failed."),
                )
            }
            else -> listener.onRendererError("unsupported_message", "Unsupported renderer message type.")
        }
    }

    private fun blockedResponse(reason: String): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            403,
            "Blocked",
            mapOf("X-Cloudy-Blocked" to reason),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    companion object {
        const val ASSET_DOMAIN = "appassets.androidplatform.net"
        const val BRIDGE_NAME = "CloudyBridge"
        const val MAX_CHART_POINTS = 2000
        const val MAX_MESSAGE_CHARS = 64 * 1024
        const val MAX_SPEC_CHARS = 512 * 1024
    }
}
