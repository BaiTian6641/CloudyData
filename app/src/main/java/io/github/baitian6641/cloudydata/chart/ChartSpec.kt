package io.github.baitian6641.cloudydata.chart

import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
import io.github.baitian6641.cloudydata.model.GridlineContrast
import io.github.baitian6641.cloudydata.model.CurrencyCode
import io.github.baitian6641.cloudydata.model.LabelCollisionPolicy
import io.github.baitian6641.cloudydata.model.LineDashStyle
import io.github.baitian6641.cloudydata.model.LineInterpolation
import io.github.baitian6641.cloudydata.model.MarkerShape
import io.github.baitian6641.cloudydata.model.ValueDisplayFormat
import io.github.baitian6641.cloudydata.model.LegendMarkSample
import io.github.baitian6641.cloudydata.model.LegendOrientation
import io.github.baitian6641.cloudydata.model.LegendPosition

enum class ChartKind { BAR, LINE, AREA }

enum class MissingPolicy { CONNECT_OBSERVED }

data class ChartPoint(
    /** Display label for the x domain. */
    val x: String,
    /** Stable x-domain order from the semantic query, not array position. */
    val xOrder: Int,
    val series: String,
    /** Geometry-only value; null means an observed NULL or an explicit gap. */
    val y: Double?,
    /** Exact value text from the engine; never display-rounded. */
    val exactY: String?,
    val defined: Boolean,
    /** Index into the underlying EngineResult rows for selection reconciliation. */
    val sourceRowIndex: Int,
)

data class ChartSeries(
    val key: String,
    val points: List<ChartPoint>,
)

data class AxisSpec(
    val title: String?,
    val min: Double? = null,
    val max: Double? = null,
    val zero: Boolean = true,
    val log: Boolean = false,
    val reverse: Boolean = false,
    val tickCount: Int? = null,
    val labelValues: List<String>? = null,
    val titleAnchor: AxisTitleAnchor = AxisTitleAnchor.AUTO,
)

data class GridlineSpec(
    val show: Boolean,
    val width: Double? = null,
    val contrast: GridlineContrast = GridlineContrast.DEFAULT,
    val minor: Boolean = false,
)

data class LegendSpec(
    val visible: Boolean,
    val position: LegendPosition,
    val title: String?,
    val orientation: LegendOrientation? = null,
    val markSample: LegendMarkSample = LegendMarkSample.MARKER,
)

data class MarkerSpec(
    val show: Boolean = true,
    val size: Double? = null,
    val shape: MarkerShape = MarkerShape.CIRCLE,
    val opacity: Double? = null,
)

data class LabelSpec(
    val show: Boolean,
    val fontSize: Int? = null,
    val position: LabelPosition = LabelPosition.TOP,
    val collisionPolicy: LabelCollisionPolicy = LabelCollisionPolicy.SHOW_ALL,
)

data class TooltipSpec(val show: Boolean)

/**
 * Renderer-independent visual specification built by the semantic layer. The
 * renderer may position marks and draw axes; it cannot change the supplied
 * data, grouping, filters or scale semantics.
 */
data class ChartSpec(
    val chartId: String,
    val revision: Long,
    val kind: ChartKind,
    val title: String,
    val subtitle: String? = null,
    val caption: String? = null,
    val xAxis: AxisSpec,
    val yAxis: AxisSpec,
    val gridlines: GridlineSpec,
    val legend: LegendSpec,
    val labels: LabelSpec,
    val markers: MarkerSpec = MarkerSpec(),
    val sourceNote: String? = null,
    val tooltip: TooltipSpec = TooltipSpec(true),
    val series: List<ChartSeries>,
    val rowCount: Int,
    val truncated: Boolean,
    val missingPolicy: MissingPolicy = MissingPolicy.CONNECT_OBSERVED,
    val lineWidth: Double? = null,
    val lineInterpolation: LineInterpolation = LineInterpolation.LINEAR,
    val lineDashStyle: LineDashStyle = LineDashStyle.SOLID,
    val lineOpacity: Double? = null,
    val units: String? = null,
    val valueDecimals: Int? = null,
    val valueFormat: ValueDisplayFormat = ValueDisplayFormat.NUMBER,
    val currencyCode: CurrencyCode = CurrencyCode.USD,
    val hiddenSeries: Set<String> = emptySet(),
    val chartBorder: Boolean = false,
    val transparentExportBackground: Boolean = false,
    val padding: Int = 8,
    val version: Int = SPEC_VERSION,
) {
    val pointCount: Int get() = series.sumOf { it.points.size }
    val definedPointCount: Int get() = series.sumOf { series -> series.points.count { it.defined } }

    companion object {
        const val SPEC_VERSION = 1
    }
}

class ChartSpecException(message: String) : IllegalArgumentException(message)
