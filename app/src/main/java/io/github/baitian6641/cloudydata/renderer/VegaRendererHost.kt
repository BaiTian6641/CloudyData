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
    private var lastChartId: String? = null
    private var lastRevision: Long = -1

    private data class PendingSpec(
        val chartId: String,
        val revision: Long,
        val values: List<Pair<String, Double>>,
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

    fun applyBarSpec(
        chartId: String,
        revision: Long,
        values: List<Pair<String, Double>>,
        dark: Boolean,
        palette: List<Int>,
        widthDp: Float = 640f,
        heightDp: Float = 320f,
    ) {
        require(chartId.length in 1..128) { "invalid chart id" }
        require(revision >= 0) { "invalid revision" }
        require(values.size in 1..MAX_PROBE_VALUES) { "value count out of bounds" }
        require(palette.isNotEmpty()) { "palette is empty" }
        require(widthDp >= 160f && heightDp >= 120f) { "renderer surface is too small" }
        val request = PendingSpec(chartId, revision, values, dark, palette, widthDp, heightDp)
        if (!ready) {
            pendingSpec = request
            return
        }
        dispatch(request)
    }

    private fun dispatch(request: PendingSpec) {
        val view = webView ?: error("renderer is not initialized")
        val message = JSONObject()
            .put("type", "ApplyChartSpec")
            .put("chartId", request.chartId)
            .put("revision", request.revision)
            .put("spec", barSpec(request.values, request.dark, request.palette, request.widthDp, request.heightDp))
        lastChartId = request.chartId
        lastRevision = request.revision
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
                if (chartId != lastChartId || revision != lastRevision) {
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
                )
            }
            "RendererError" -> listener.onRendererError(
                json.optString("code", "renderer_error"),
                json.optString("detail", "Renderer failed."),
            )
            else -> listener.onRendererError("unsupported_message", "Unsupported renderer message type.")
        }
    }

    private fun barSpec(
        values: List<Pair<String, Double>>,
        dark: Boolean,
        palette: List<Int>,
        widthDp: Float,
        heightDp: Float,
    ): JSONObject {
        val rows = JSONArray()
        values.forEach { (label, value) ->
            rows.put(JSONObject().put("label", label).put("value", value))
        }
        val textColor = if (dark) "#E1EBED" else "#182427"
        val gridColor = if (dark) "#35515A" else "#D3DFE2"
        val colorScale = JSONObject()
            .put("name", "color")
            .put("type", "ordinal")
            .put("domain", JSONObject().put("data", "table").put("field", "label"))
            .put(
                "range",
                JSONArray(palette.map { "#%06X".format(0xFFFFFF and it) }),
            )
        return JSONObject()
            .put("\$schema", "https://vega.github.io/schema/vega/v6.json")
            .put("width", widthDp)
            .put("height", heightDp)
            .put("padding", 8)
            .put("data", JSONArray().put(JSONObject().put("name", "table").put("values", rows)))
            .put(
                "scales",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("name", "x")
                            .put("type", "band")
                            .put("domain", JSONObject().put("data", "table").put("field", "label"))
                            .put("range", "width")
                            .put("paddingInner", 0.2),
                    )
                    .put(
                        JSONObject()
                            .put("name", "y")
                            .put("type", "linear")
                            .put("domain", JSONObject().put("data", "table").put("field", "value"))
                            .put("range", "height")
                            .put("nice", true)
                            .put("zero", true),
                    )
                    .put(colorScale),
            )
            .put(
                "axes",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("orient", "bottom")
                            .put("scale", "x")
                            .put("labelColor", textColor)
                            .put("labelFontSize", 12)
                            .put("domainColor", gridColor)
                            .put("tickColor", gridColor),
                    )
                    .put(
                        JSONObject()
                            .put("orient", "left")
                            .put("scale", "y")
                            .put("labelColor", textColor)
                            .put("labelFontSize", 12)
                            .put("domainColor", gridColor)
                            .put("tickColor", gridColor)
                            .put("grid", true)
                            .put("gridColor", gridColor),
                    ),
            )
            .put(
                "marks",
                JSONArray().put(
                    JSONObject()
                        .put("type", "rect")
                        .put("from", JSONObject().put("data", "table"))
                        .put(
                            "encode",
                            JSONObject().put(
                                "enter",
                                JSONObject()
                                    .put("x", JSONObject().put("scale", "x").put("field", "label"))
                                    .put("width", JSONObject().put("scale", "x").put("band", 1))
                                    .put("y", JSONObject().put("scale", "y").put("field", "value"))
                                    .put("y2", JSONObject().put("scale", "y").put("value", 0))
                                    .put("fill", JSONObject().put("scale", "color").put("field", "label")),
                            ),
                        ),
                ),
            )
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
        const val MAX_PROBE_VALUES = 24
        const val MAX_MESSAGE_CHARS = 64 * 1024
    }
}
