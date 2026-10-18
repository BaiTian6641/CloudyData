package io.github.baitian6641.cloudydata.ui

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.baitian6641.cloudydata.design.CloudyBadge
import io.github.baitian6641.cloudydata.design.CloudyBadgeTone
import io.github.baitian6641.cloudydata.design.CloudyTextButton
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.renderer.VegaRendererHost
import java.util.concurrent.atomic.AtomicReference

private const val MAX_RENDERED_CATEGORIES = 24

@Composable
fun VegaBarChartCard(
    result: WorksheetResult,
    selectedRowIndex: Int,
    onSelectRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartId: String = "worksheet-bar",
    title: String = chartTitle(result),
    showGridlines: Boolean = true,
    showValueLabels: Boolean = false,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val chartData = remember(result) { chartValues(result) }
    if (chartData == null) {
        NativeBarChartCard(result, selectedRowIndex, onSelectRow, modifier, title)
        return
    }
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

    Column(modifier.background(colors.surface).padding(horizontal = density.spaceMedium, vertical = density.spaceSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (rendererError != null) {
                CloudyBadge("Native fallback", tone = CloudyBadgeTone.Warning)
                Spacer(Modifier.width(density.spaceSmall))
                CloudyTextButton("Retry", {
                    rendererError = null
                    rendererReady = false
                    rendered = false
                    retryToken++
                })
            } else if (rendered) {
                CloudyBadge("Offline Vega", tone = CloudyBadgeTone.Neutral)
            } else {
                CloudyBadge("Loading renderer", tone = CloudyBadgeTone.Neutral)
            }
        }
        if (rendererError != null) {
            Text(
                "Renderer unavailable: $rendererError. The native chart is shown with the same result.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                NativeBarChartCard(result, selectedRowIndex, onSelectRow, Modifier.fillMaxSize(), title)
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
                                    ) {
                                        rendered = true
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
            accessibleSummary(chartData),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 2,
        )
    }

    DisposableEffect(retryToken) {
        onDispose {
            hostRef.getAndSet(null)?.dispose()
        }
    }

    LaunchedEffect(hostSerial, rendererReady, dark, viewSize, chartData, showGridlines, showValueLabels) {
        if (!rendererReady || viewSize.width < 32 || viewSize.height < 32) return@LaunchedEffect
        val widthDp = with(localDensity) { viewSize.width.toDp().value }
        val heightDp = with(localDensity) { viewSize.height.toDp().value }
        requestRevision++
        hostRef.get()?.applyBarSpec(
            chartId = chartId,
            revision = requestRevision.toLong(),
            values = chartData.map { it.label to it.value },
            dark = dark,
            palette = palette.map { it.toInt() },
            widthDp = widthDp,
            heightDp = heightDp,
            showGridlines = showGridlines,
            showValueLabels = showValueLabels,
        )
    }
}

private data class ChartValue(val label: String, val value: Double)

private fun chartValues(result: WorksheetResult): List<ChartValue>? {
    val rows = result.result.rows
    if (rows.size > MAX_RENDERED_CATEGORIES) return null
    val valueColumn = if (result.rowField != null) 1 else 0
    val labelColumn = if (result.rowField != null) 0 else null
    val values = rows.map { row ->
        val numeric = valueAsDouble(row.getOrNull(valueColumn)) ?: return null
        if (!numeric.isFinite() || numeric < 0.0) return null
        ChartValue(
            label = labelColumn?.let { displayValue(row.getOrNull(it)) } ?: "Total",
            value = numeric,
        )
    }
    return values.takeIf { it.isNotEmpty() }
}

private fun accessibleSummary(values: List<ChartValue>): String {
    return "Chart summary: " + values.joinToString(", ") { "${it.label} = ${displayValue(it.value)}" } +
        ". Select a table row for exact values."
}
