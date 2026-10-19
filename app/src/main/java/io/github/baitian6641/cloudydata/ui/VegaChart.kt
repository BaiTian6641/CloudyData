package io.github.baitian6641.cloudydata.ui

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Color
import android.print.PrintAttributes
import android.print.PrintManager
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import io.github.baitian6641.cloudydata.model.SelectionMode
import io.github.baitian6641.cloudydata.renderer.ExportBundleWriter
import io.github.baitian6641.cloudydata.renderer.ExportWriteResult
import io.github.baitian6641.cloudydata.renderer.FrozenExport
import io.github.baitian6641.cloudydata.renderer.PdfExportWriter
import io.github.baitian6641.cloudydata.renderer.PdfPrintAdapter
import io.github.baitian6641.cloudydata.renderer.PdfTable
import io.github.baitian6641.cloudydata.renderer.VegaRendererHost
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("ClickableViewAccessibility") // performClick is called on UP; the listener never consumes the event.
@Composable
fun VegaChartCard(
    result: WorksheetResult,
    options: ChartOptions,
    selectedRowIndex: Int,
    selectedRowIndices: Set<Int>,
    onSelectRow: (Int) -> Unit,
    onSelection: (Int, SelectionMode) -> Unit,
    modifier: Modifier = Modifier,
    chartId: String = "worksheet-chart",
    title: String = chartTitle(result),
    sourceNote: String? = null,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val preview = remember(result, options, chartId, title, sourceNote) {
        runCatching { ChartSpecBuilder.build(chartId, 0, result, options, title, sourceNote) }
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
    val currentSelection = rememberUpdatedState(onSelection)
    var appliedExport by remember { mutableStateOf<AppliedExport?>(null) }
    val context = LocalContext.current
    val exportModel: ChartExportViewModel = viewModel(key = "svg-export-$chartId")
    val currentExportModel = rememberUpdatedState(exportModel)
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }
    var exportInFlight by remember { mutableStateOf(false) }
    fun writeFrozenBundle(uri: android.net.Uri?) {
        val frozen = exportModel.frozenBundle
        exportModel.frozenBundle = null
        if (uri == null) {
            exportStatus = "Export cancelled."
            return
        }
        if (frozen == null) {
            exportStatus = "Export was lost when the screen was recreated. Try again."
            return
        }
        scope.launch {
            exportStatus = withContext(Dispatchers.IO) {
                when (val result = ExportBundleWriter.write(context, uri, frozen)) {
                    is ExportWriteResult.Success ->
                        "Exported SVG, PNG and PDF (${frozen.width}×${frozen.height}) to " +
                            "${result.directory ?: "the selected folder"}."
                    is ExportWriteResult.Failure -> "Export failed: ${result.message}"
                }
            }
        }
    }
    val saveBundle = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { writeFrozenBundle(it) }
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
    fun pdfTable(applied: AppliedExport): PdfTable {
        val resultNow = applied.result
        return PdfTable(
            title = applied.title,
            sourceNote = applied.sourceNote,
            summary = accessibleSummary(applied.spec),
            columns = resultNow.result.columns.map { it.name },
            rows = resultNow.result.rows.map { row ->
                row.map { it?.toString() ?: "NULL" }
            },
            rasterNotice = if (resultNow.result.truncated) {
                "Page 1 is a rasterized chart image for a truncated result; " +
                    "the exact rows below are the accepted captured result."
            } else {
                "Page 1 is a rasterized chart image. This page provides " +
                    "accessible title, provenance and exact rows."
            },
        )
    }

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
                        exportInFlight = false
                        retryToken++
                    })
                }
                rendered -> CloudyBadge("Offline Vega", tone = CloudyBadgeTone.Neutral)
                else -> CloudyBadge("Loading renderer", tone = CloudyBadgeTone.Neutral)
            }
            if (rendered && specError == null && rendererError == null) {
                Spacer(Modifier.width(density.spaceSmall))
                Box {
                    CloudyTextButton("Export", { showExportMenu = true })
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("All formats — SVG, PNG, PDF") },
                            enabled = !exportInFlight,
                            onClick = {
                                showExportMenu = false
                                exportStatus = null
                                exportModel.bundleAction = BundleAction.SAVE_ALL
                                exportInFlight = true
                                hostRef.get()?.requestExportBundle() ?: run {
                                    exportInFlight = false
                                    exportStatus = "Renderer is unavailable."
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Print worksheet…") },
                            enabled = !exportInFlight,
                            onClick = {
                                showExportMenu = false
                                exportStatus = null
                                exportModel.bundleAction = BundleAction.PRINT
                                exportInFlight = true
                                hostRef.get()?.requestExportBundle() ?: run {
                                    exportInFlight = false
                                    exportStatus = "Renderer is unavailable."
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("SVG only") },
                            onClick = {
                                showExportMenu = false
                                exportStatus = null
                                hostRef.get()?.requestSvgExport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("PNG only") },
                            onClick = {
                                showExportMenu = false
                                exportStatus = null
                                hostRef.get()?.requestPngExport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("PDF only") },
                            onClick = {
                                showExportMenu = false
                                exportStatus = null
                                hostRef.get()?.requestPdfExport()
                            },
                        )
                    }
                }
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
                    NativeBarChartCard(
                        result,
                        selectedRowIndex,
                        selectedRowIndices,
                        onSelectRow,
                        Modifier.fillMaxSize(),
                        title,
                    )
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
                                        mode: SelectionMode,
                                    ) {
                                        if (sourceRowIndex in 0 until currentRowCount.value) {
                                            hostRef.get()?.webView?.requestFocus()
                                            currentSelection.value(sourceRowIndex, mode)
                                        }
                                    }

                                    override fun onFocusExit() {
                                        hostRef.get()?.webView?.clearFocus()
                                        focusManager.clearFocus(force = true)
                                    }

                                    override fun onSvgExport(chartId: String, revision: Long, svg: String) {
                                        if (hostRef.get() == null) return
                                        currentExportModel.value.pendingSvg = PendingExport(
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
                                                val applied = appliedExport
                                                withContext(Dispatchers.IO) {
                                                    runCatching {
                                                        if (applied != null) {
                                                            PdfExportWriter.fromPngWithTable(png, pdfTable(applied)).bytes
                                                        } else {
                                                            PdfExportWriter.fromPng(png)
                                                        }
                                                    }
                                                }.onSuccess { pdf ->
                                                    currentExportModel.value.pendingPdf = PendingExport(
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
                                            currentExportModel.value.pendingPng = PendingExport(
                                                bytes = png,
                                                label = "PNG",
                                            )
                                            exportStatus = "Choose a destination for the PNG."
                                            savePng.launch("cloudy-chart.png")
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
                                        if (hostRef.get() == null) return
                                        val applied = appliedExport
                                        if (applied == null) {
                                            exportInFlight = false
                                            exportStatus = "Export scene is unavailable."
                                            return
                                        }
                                        val titleNow = applied.title
                                        val table = pdfTable(applied)
                                        scope.launch {
                                            exportStatus = "Preparing the frozen export bundle."
                                            withContext(Dispatchers.IO) {
                                                runCatching { PdfExportWriter.fromPngWithTable(png, table) }
                                            }.onSuccess { pdf ->
                                                exportInFlight = false
                                                if (currentExportModel.value.bundleAction == BundleAction.PRINT) {
                                                    val printManager = context.getSystemService(PrintManager::class.java)
                                                    if (printManager == null) {
                                                        exportStatus = "Printing is unavailable on this device."
                                                        return@onSuccess
                                                    }
                                                    runCatching {
                                                        printManager.print(
                                                            "Cloudy Data — ${titleNow.take(80)}",
                                                            PdfPrintAdapter(
                                                                pdf = pdf.bytes,
                                                                jobName = "cloudy-chart",
                                                                pageCount = pdf.pageCount,
                                                            ),
                                                            PrintAttributes.Builder()
                                                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                                                .build(),
                                                        )
                                                    }.onSuccess { job ->
                                                        exportStatus = if (job != null) {
                                                            "Print dialog opened; printing is not yet confirmed."
                                                        } else {
                                                            "Printing is unavailable on this device."
                                                        }
                                                    }.onFailure { error ->
                                                        exportStatus = "Print failed: ${error.message}"
                                                    }
                                                } else {
                                                    currentExportModel.value.frozenBundle = FrozenExport(
                                                        svg = svg.toByteArray(Charsets.UTF_8),
                                                        png = png,
                                                        pdf = pdf.bytes,
                                                        pdfPages = pdf.pageCount,
                                                        width = width,
                                                        height = height,
                                                    )
                                                    exportStatus = "Choose a folder for the three frozen files."
                                                    saveBundle.launch(null)
                                                }
                                            }.onFailure { error ->
                                                exportInFlight = false
                                                exportStatus = "Export failed: ${error.message}"
                                            }
                                        }
                                    }

                                    override fun onExportError(message: String) {
                                        exportInFlight = false
                                        exportStatus = "Export failed: $message"
                                    }

                                    override fun onRendererError(code: String, detail: String) {
                                        exportInFlight = false
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
                                web.setOnTouchListener { view, event ->
                                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                                        host.notePointerMetaState(event.metaState)
                                    }
                                    if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                                        view.performClick()
                                    }
                                    false
                                }
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
        // Reserve one line so showing export feedback never resizes the chart
        // between SVG/PNG/PDF exports.
        Text(
            exportStatus ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = if (exportStatus?.startsWith("Export failed") == true) colors.warning else colors.textSecondary,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        sourceNote,
    ) {
        if (!rendererReady || spec == null || viewSize.width < 32 || viewSize.height < 32) return@LaunchedEffect
        rendered = false
        val widthDp = with(localDensity) { viewSize.width.toDp().value }
        val heightDp = with(localDensity) { viewSize.height.toDp().value }
        requestRevision++
        val built = runCatching {
            ChartSpecBuilder.build(chartId, requestRevision.toLong(), result, options, title, sourceNote)
        }.getOrElse {
            rendererError = "spec_rejected: ${it.message}"
            return@LaunchedEffect
        }
        appliedExport = AppliedExport(result, built, title, sourceNote)
        exportInFlight = false
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

    LaunchedEffect(rendered, appliedExport, selectedRowIndices) {
        if (!rendered) return@LaunchedEffect
        val applied = appliedExport ?: return@LaunchedEffect
        hostRef.get()?.applySelection(
            chartId = applied.spec.chartId,
            revision = applied.spec.revision,
            rows = selectedRowIndices,
        )
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
    return "$kind summary: $parts$suffix. Click a mark or use arrow keys to select a table row; " +
        "Ctrl-click or Ctrl+arrow adds, Shift-click or Shift+arrow selects a range; Escape leaves the chart."
}

internal data class PendingExport(
    val bytes: ByteArray,
    val label: String,
)

internal data class AppliedExport(
    val result: WorksheetResult,
    val spec: ChartSpec,
    val title: String,
    val sourceNote: String?,
)

internal enum class BundleAction { SAVE_ALL, PRINT }

internal class ChartExportViewModel : ViewModel() {
    var bundleAction: BundleAction = BundleAction.SAVE_ALL
    var frozenBundle: FrozenExport? = null
    var pendingSvg: PendingExport? = null
    var pendingPng: PendingExport? = null
    var pendingPdf: PendingExport? = null
}
