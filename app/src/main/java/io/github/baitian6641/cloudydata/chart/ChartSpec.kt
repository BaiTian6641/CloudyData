package io.github.baitian6641.cloudydata.chart

import io.github.baitian6641.cloudydata.model.LegendPosition

enum class ChartKind { BAR, LINE }

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
)

data class GridlineSpec(val show: Boolean)

data class LegendSpec(
    val visible: Boolean,
    val position: LegendPosition,
    val title: String?,
)

data class LabelSpec(val show: Boolean)

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
    val xAxis: AxisSpec,
    val yAxis: AxisSpec,
    val gridlines: GridlineSpec,
    val legend: LegendSpec,
    val labels: LabelSpec,
    val series: List<ChartSeries>,
    val rowCount: Int,
    val truncated: Boolean,
    val missingPolicy: MissingPolicy = MissingPolicy.CONNECT_OBSERVED,
    val version: Int = SPEC_VERSION,
) {
    val pointCount: Int get() = series.sumOf { it.points.size }
    val definedPointCount: Int get() = series.sumOf { series -> series.points.count { it.defined } }

    companion object {
        const val SPEC_VERSION = 1
    }
}

class ChartSpecException(message: String) : IllegalArgumentException(message)
