package io.github.baitian6641.cloudydata.ui

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartSpec
import io.github.baitian6641.cloudydata.chart.ChartSpecBuilder
import io.github.baitian6641.cloudydata.design.CloudyBadge
import io.github.baitian6641.cloudydata.design.CloudyBadgeTone
import io.github.baitian6641.cloudydata.design.CloudyTextButton
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.renderer.PdfExportWriter
import io.github.baitian6641.cloudydata.renderer.VegaRendererHost
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VegaChartCard(
    result: WorksheetResult,
    options: ChartOptions,
    selectedRowIndex: Int,
    onSelectRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartId: String = "worksheet-chart",
    title: String = chartTitle(result),
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val preview = remember(result, options, chartId, title) {
        runCatching { ChartSpecBuilder.build(chartId, 0, result, options, title) }
    }
    val spec = preview.getOrNull()
    val specError = preview.exceptionOrNull()?.message
    val dark = colors.canvas.luminance() < 0.5f
    val palette = if (dark) CloudyTokens.Chart.categoricalDark else CloudyTokens.Chart.categoricalLight
    val hostRef = remember { AtomicReference<VegaRendererHost?>(null) }
    var hostSerial by remember { mutableIntStateOf(0) }
    var rendererReady by remember { mutableStateOf(false) }
    var rendered by remember { mutableStateOf(false) }
    var rendererError by remember { mutableStateOf<String?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var retryToken by remember { mutableIntStateOf(0) }
    var requestRevision by remember { mutableIntStateOf(0) }
    val localDensity = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val currentRowCount = rememberUpdatedState(result.result.rows.size)
    val currentSelectRow = rememberUpdatedState(onSelectRow)
    val context = LocalContext.current
    val exportModel: ChartExportViewModel = viewModel(key = "svg-export-$chartId")
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    fun savePending(uri: android.net.Uri?, format: String) {
        val pending = when (format) {
            "png" -> exportModel.pendingPng
            "pdf" -> exportModel.pendingPdf
            else -> exportModel.pendingSvg
        }
        when (format) {
            "png" -> exportModel.pendingPng = null
            "pdf" -> exportModel.pendingPdf = null
            else -> exportModel.pendingSvg = null
        }
        if (uri == null) {
            exportStatus = null
        } else if (pending == null) {
            exportStatus = "Export was lost when the screen was recreated. Try again."
        } else {
            scope.launch {
                exportStatus = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(pending.bytes)
                        } ?: error("The destination could not be opened.")
                        "Exported ${pending.label} (${pending.bytes.size} bytes)."
                    }.getOrElse { error ->
                        "Export failed: ${error.message}"
                    }
                }
            }
        }
    }
    val saveSvg = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/svg+xml"),
    ) { savePending(it, format = "svg") }
    val savePng = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { savePending(it, format = "png") }
    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { savePending(it, format = "pdf") }

    Column(modifier.background(colors.surface).padding(horizontal = density.spaceMedium, vertical = density.spaceSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                specError != null -> CloudyBadge("Chart unavailable", tone = CloudyBadgeTone.Warning)
                rendererError != null -> {
                    CloudyBadge("Native fallback", tone = CloudyBadgeTone.Warning)
                    Spacer(Modifier.width(density.spaceSmall))
                    CloudyTextButton("Retry", {
                        rendererError = null
                        rendererReady = false
                        rendered = false
                        retryToken++
                    })
                }
                rendered -> CloudyBadge("Offline Vega", tone = CloudyBadgeTone.Neutral)
                else -> CloudyBadge("Loading renderer", tone = CloudyBadgeTone.Neutral)
            }
            if (rendered && specError == null && rendererError == null) {
                Spacer(Modifier.width(density.spaceSmall))
                CloudyTextButton("SVG", {
                    exportStatus = null
                    hostRef.get()?.requestSvgExport() ?: run {
                        exportStatus = "Renderer is unavailable."
                    }
                })
                Spacer(Modifier.width(density.spaceSmall))
                CloudyTextButton("PNG", {
                    exportStatus = null
                    hostRef.get()?.requestPngExport() ?: run {
                        exportStatus = "Renderer is unavailable."
                    }
                })
                Spacer(Modifier.width(density.spaceSmall))
                CloudyTextButton("PDF", {
                    exportStatus = null
                    hostRef.get()?.requestPdfExport() ?: run {
                        exportStatus = "Renderer is unavailable."
                    }
                })
            }
        }
        if (specError != null) {
            Text(
                "Chart unavailable: $specError",
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
            )
        } else if (rendererError != null) {
            Text(
                "Renderer unavailable: $rendererError. " +
                    if (result.markType == MarkType.BAR) {
                        "The native chart is shown with the same result."
                    } else {
                        "Use the exact-value table below until the renderer recovers."
                    },
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
            )
            if (result.markType == MarkType.BAR) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    NativeBarChartCard(result, selectedRowIndex, onSelectRow, Modifier.fillMaxSize(), title)
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(retryToken) {
                    AndroidView(
                        factory = { context ->
                            val container = FrameLayout(context)
                            container.setBackgroundColor(Color.TRANSPARENT)
                            val host = VegaRendererHost(
                                context,
                                object : VegaRendererHost.Listener {
                                    override fun onReady(vegaVersion: String, interpreterAvailable: Boolean) {
                                        if (!interpreterAvailable) {
                                            rendererError = "interpreter_unavailable"
                                            return
                                        }
                                        rendererReady = true
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
                                        rendered = true
                                    }

                                    override fun onSelectionIntent(
                                        chartId: String,
                                        revision: Long,
                                        sourceRowIndex: Int,
                                        series: String,
                                        x: String,
                                    ) {
                                        if (sourceRowIndex in 0 until currentRowCount.value) {
                                            hostRef.get()?.webView?.requestFocus()
                                            currentSelectRow.value(sourceRowIndex)
                                        }
                                    }

                                    override fun onFocusExit() {
                                        hostRef.get()?.webView?.clearFocus()
                                        focusManager.clearFocus(force = true)
                                    }

                                    override fun onSvgExport(chartId: String, revision: Long, svg: String) {
                                        if (hostRef.get() == null) return
                                        exportModel.pendingSvg = PendingExport(
                                            bytes = svg.toByteArray(Charsets.UTF_8),
                                            label = "SVG",
                                        )
                                        exportStatus = "Choose a destination for the SVG."
                                        saveSvg.launch("cloudy-chart.svg")
                                    }

                                    override fun onPngExport(
                                        chartId: String,
                                        revision: Long,
                                        purpose: String,
                                        width: Int,
                                        height: Int,
                                        png: ByteArray,
                                    ) {
                                        if (hostRef.get() == null) return
                                        if (purpose == "pdf") {
                                            scope.launch {
                                                exportStatus = "Preparing PDF."
                                                withContext(Dispatchers.IO) {
                                                    runCatching { PdfExportWriter.fromPng(png) }
                                                }.onSuccess { pdf ->
                                                    exportModel.pendingPdf = PendingExport(
                                                        bytes = pdf,
                                                        label = "PDF",
                                                    )
                                                    exportStatus = "Choose a destination for the PDF."
                                                    savePdf.launch("cloudy-chart.pdf")
                                                }.onFailure { error ->
                                                    exportStatus = "Export failed: ${error.message}"
                                                }
                                            }
                                        } else {
                                            exportModel.pendingPng = PendingExport(
                                                bytes = png,
                                                label = "PNG",
                                            )
                                            exportStatus = "Choose a destination for the PNG."
                                            savePng.launch("cloudy-chart.png")
                                        }
                                    }

                                    override fun onExportError(message: String) {
                                        exportStatus = "Export failed: $message"
                                    }

                                    override fun onRendererError(code: String, detail: String) {
                                        rendererError = "$code: $detail"
                                    }

                                    override fun onExternalRequestBlocked(url: String) {
                                        rendererError = "blocked_external_request"
                                    }
                                },
                            )
                            host.initialize()
                            host.webView?.let { web ->
                                web.setBackgroundColor(Color.TRANSPARENT)
                                web.isFocusable = true
                                web.isFocusableInTouchMode = true
                                container.addView(
                                    web,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                            }
                            hostRef.set(host)
                            hostSerial++
                            container
                        },
                        modifier = Modifier.fillMaxSize().onSizeChanged { viewSize = it },
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            spec?.let(::accessibleSummary) ?: "No drawable result is available.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 2,
        )
        exportStatus?.let { status ->
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = if (status.startsWith("Export failed")) colors.warning else colors.textSecondary,
                maxLines = 2,
            )
        }
    }

    DisposableEffect(retryToken) {
        onDispose {
            hostRef.getAndSet(null)?.dispose()
        }
    }

    LaunchedEffect(
        hostSerial,
        rendererReady,
        dark,
        viewSize,
        spec,
        options,
    ) {
        if (!rendererReady || spec == null || viewSize.width < 32 || viewSize.height < 32) return@LaunchedEffect
        rendered = false
        val widthDp = with(localDensity) { viewSize.width.toDp().value }
        val heightDp = with(localDensity) { viewSize.height.toDp().value }
        requestRevision++
        val built = runCatching {
            ChartSpecBuilder.build(chartId, requestRevision.toLong(), result, options, title)
        }.getOrElse {
            rendererError = "spec_rejected: ${it.message}"
            return@LaunchedEffect
        }
        runCatching {
            hostRef.get()?.applyChartSpec(
                spec = built,
                dark = dark,
                palette = palette.map { it.toInt() },
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }.onFailure { error ->
            rendererError = "spec_rejected: ${error.message}"
        }
    }
}

private fun accessibleSummary(spec: ChartSpec): String {
    val parts = spec.series.take(4).joinToString("; ") { series ->
        val points = series.points.take(4).joinToString(", ") { point -> "${point.x} = ${displayValue(point.y)}" }
        "${series.key}: $points"
    }
    val suffix = if (spec.series.size > 4) " plus ${spec.series.size - 4} more series" else ""
    val kind = when (spec.kind) {
        ChartKind.BAR -> "Bar"
        ChartKind.LINE -> "Line"
        ChartKind.AREA -> "Area"
    }
    return "$kind summary: $parts$suffix. Click a mark or use arrow keys to select a table row; Escape leaves the chart."
}

internal data class PendingExport(
    val bytes: ByteArray,
    val label: String,
)

internal class ChartExportViewModel : ViewModel() {
    var pendingSvg: PendingExport? = null
    var pendingPng: PendingExport? = null
    var pendingPdf: PendingExport? = null
}
