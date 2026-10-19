package io.github.baitian6641.cloudydata.chart

import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.StackMode
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.ValueDisplayFormat
import io.github.baitian6641.cloudydata.model.valueAsDouble
import java.util.LinkedHashMap

/**
 * Converts a typed query result into a typed visual specification. All
 * aggregation, grouping and absence behavior is already fixed by the query; this
 * builder only shapes observed rows for rendering. It never invents absent
 * domain members or converts NULL to zero.
 */
object ChartSpecBuilder {
    const val MAX_BAR_CATEGORIES = 24
    const val MAX_LINE_POINTS = 2000

    fun build(
        chartId: String,
        revision: Long,
        result: WorksheetResult,
        options: ChartOptions,
        derivedTitle: String,
        sourceNote: String? = null,
    ): ChartSpec {
        require(chartId.length in 1..128) { "invalid chart id" }
        require(revision >= 0) { "invalid revision" }
        if (result.result.truncated) {
            throw ChartSpecException(
                "The result exceeded the chart row limit. The chart is not drawn from a partial result; use the table.",
            )
        }
        validateAxisOptions(options)
        return when (result.markType) {
            MarkType.LINE -> buildTimeSeries(
                chartId, revision, result, options, derivedTitle,
                kind = ChartKind.LINE,
                sourceNote = sourceNote,
            )
            MarkType.AREA -> buildTimeSeries(
                chartId, revision, result, options, derivedTitle,
                kind = ChartKind.AREA,
                sourceNote = sourceNote,
            )
            else -> buildBar(chartId, revision, result, options, derivedTitle, sourceNote)
        }
    }

    private fun buildBar(
        chartId: String,
        revision: Long,
        result: WorksheetResult,
        options: ChartOptions,
        derivedTitle: String,
        sourceNote: String? = null,
    ): ChartSpec {
        if (options.logScale) {
            throw ChartSpecException("A logarithmic value axis is only available for Line marks in this build.")
        }
        val rows = result.result.rows
        val hasSeries = result.hasSeriesColumn
        val wantsStacking = options.barStackMode == StackMode.STACKED ||
            options.barStackMode == StackMode.PERCENT_STACKED
        if (hasSeries && wantsStacking &&
            result.aggregation != Aggregation.SUM && result.aggregation != Aggregation.COUNT
        ) {
            throw ChartSpecException("Stacked bars require Sum or Count aggregation.")
        }
        val series = if (hasSeries) {
            buildBarSeries(result, options)
        } else {
            listOf(ChartSeries("Values", buildSingleBarPoints(result)))
        }
        val xLabels = if (hasSeries) {
            series.flatMap { it.points }.sortedBy { it.xOrder }.map { it.x }.distinct()
        } else {
            series.single().points.map { it.x }
        }
        if (xLabels.size > MAX_BAR_CATEGORIES) {
            throw ChartSpecException(
                "Bar chart supports at most $MAX_BAR_CATEGORIES categories; use the table or aggregate further.",
            )
        }
        val allPoints = series.flatMap { it.points }
        if (allPoints.none { it.defined }) throw ChartSpecException("The current view has no numeric marks.")
        validateYDomain(options, allPoints.mapNotNull { it.y })
        val units = options.units?.trim()?.take(16)?.takeIf { it.isNotEmpty() }
        val yTitle = titleWithUnits(
            options.yAxisTitle
                ?: result.measureField?.let { "${aggregationLabel(result.aggregation)} of ${it.name}" }
                ?: "Value",
            units,
        )
        val barHasSeries = hasSeries && series.any { it.points.isNotEmpty() }
        val stackMode = if (barHasSeries && wantsStacking) {
            options.barStackMode
        } else {
            StackMode.NONE
        }
        val percentStacked = stackMode == StackMode.PERCENT_STACKED
        val baseAxis = axisFor(options, yTitle)
        val yAxis = if (percentStacked) {
            baseAxis.copy(min = 0.0, max = 1.0, zero = true, log = false)
        } else {
            baseAxis
        }
        val format = if (percentStacked) ValueDisplayFormat.PERCENT else options.valueFormat
        return ChartSpec(
            chartId = chartId,
            revision = revision,
            kind = ChartKind.BAR,
            title = options.title ?: derivedTitle,
            subtitle = options.subtitle?.take(160),
            caption = options.caption?.take(240),
            xAxis = AxisSpec(
                title = options.xAxisTitle ?: result.rowField?.name ?: "View",
                reverse = false,
                labelValues = AxisLabelSelection.thin(xLabels, options.xLabelCount),
                titleAnchor = options.axisTitleAnchor,
            ),
            yAxis = yAxis,
            gridlines = GridlineSpec(
                options.showGridlines,
                options.gridlineWidth,
                options.gridlineContrast,
                options.minorGridlines,
            ),
            legend = LegendSpec(
                visible = barHasSeries && options.legendVisible,
                position = options.legendPosition,
                title = options.seriesTitle ?: result.seriesField?.name ?: "Series",
                orientation = options.legendOrientation,
                markSample = options.legendMarkSample,
            ),
            labels = LabelSpec(
                show = options.showValueLabels,
                fontSize = options.labelFontSize,
                position = options.labelPosition,
                collisionPolicy = options.labelCollisionPolicy,
            ),
            sourceNote = sourceNote,
            tooltip = TooltipSpec(options.showTooltip),
            series = series,
            hiddenSeries = emptySet(),
            barStackMode = stackMode,
            barHasSeries = barHasSeries,
            barOrientation = options.barOrientation,
            rowCount = rows.size,
            truncated = result.result.truncated,
            units = units,
            valueDecimals = options.valueDecimals,
            valueFormat = format,
            currencyCode = options.currencyCode,
            chartBorder = options.showChartBorder,
            transparentExportBackground = options.transparentExportBackground,
            padding = options.chartPadding,
        )
    }

    private fun buildSingleBarPoints(result: WorksheetResult): List<ChartPoint> {
        val rows = result.result.rows
        val valueColumn = if (result.rowField != null) 1 else 0
        val labelColumn = if (result.rowField != null) 0 else null
        return rows.mapIndexed { index, row ->
            val raw = row.getOrNull(valueColumn)
            val y = raw?.let { valueAsDouble(it) }
            if (raw != null && y == null) throw ChartSpecException("Bar value for row ${index + 1} is not numeric.")
            if (y != null && !y.isFinite()) throw ChartSpecException("Bar value for row ${index + 1} is not finite.")
            ChartPoint(
                x = labelColumn?.let { display(row.getOrNull(it)) } ?: "Total",
                xOrder = index,
                series = "Values",
                y = y,
                exactY = raw?.toString(),
                defined = y != null,
                sourceRowIndex = index,
                stackBase = 0.0,
            )
        }
    }

    private fun buildBarSeries(result: WorksheetResult, options: ChartOptions): List<ChartSeries> {
        val xOrder = LinkedHashMap<String, Int>()
        val seriesOrder = LinkedHashMap<String, Int>()
        val byCategory = LinkedHashMap<String, LinkedHashMap<String, BarSegment>>()
        result.result.rows.forEachIndexed { rowIndex, row ->
            val x = display(row.getOrNull(0))
            val categoryOrder = xOrder.getOrPut(x) { xOrder.size }
            val series = display(row.getOrNull(1))
            val seriesIndex = seriesOrder.getOrPut(series) { seriesOrder.size }
            val raw = row.getOrNull(2)
            val y = raw?.let { valueAsDouble(it) }
            if (raw != null && y == null) {
                throw ChartSpecException("Bar value for $x / $series is not numeric.")
            }
            if (y != null && !y.isFinite()) {
                throw ChartSpecException("Bar value for $x / $series is not finite.")
            }
            byCategory.getOrPut(x) { LinkedHashMap() }[series] = BarSegment(
                x = x,
                xOrder = categoryOrder,
                series = series,
                seriesIndex = seriesIndex,
                y = y,
                exactY = raw?.toString(),
                sourceRowIndex = rowIndex,
            )
        }
        val percentStacked = options.barStackMode == StackMode.PERCENT_STACKED
        val stacked = percentStacked || options.barStackMode == StackMode.STACKED
        if (percentStacked) {
            byCategory.forEach { (category, segments) ->
                if (segments.values.any { it.y != null && it.y < 0.0 }) {
                    throw ChartSpecException(
                        "100% stacked bars require non-negative values; category $category has a negative value.",
                    )
                }
            }
        }
        val totals = if (percentStacked) {
            byCategory.mapValues { (category, segments) ->
                segments.values.sumOf { it.y ?: 0.0 }.also { total ->
                    if (total <= 0.0) {
                        throw ChartSpecException(
                            "100% stacked bars need a positive category total; $category has $total.",
                        )
                    }
                }
            }
        } else {
            emptyMap()
        }
        val points = seriesOrder.keys.associateWith { mutableListOf<ChartPoint>() }
        for ((x, categoryOrder) in xOrder) {
            var positiveBase = 0.0
            var negativeBase = 0.0
            val category = byCategory[x].orEmpty()
            for ((series, seriesIndex) in seriesOrder) {
                val segment = category[series] ?: continue
                val rawY = segment.y
                val plotY = if (percentStacked && rawY != null) {
                    rawY / totals.getValue(x)
                } else {
                    rawY
                }
                val base = when {
                    !stacked || plotY == null -> 0.0
                    plotY >= 0.0 -> positiveBase
                    else -> negativeBase
                }
                points.getValue(series).add(
                    ChartPoint(
                        x = x,
                        xOrder = categoryOrder,
                        series = series,
                        y = plotY,
                        exactY = segment.exactY,
                        defined = plotY != null,
                        sourceRowIndex = segment.sourceRowIndex,
                        stackBase = base,
                        seriesIndex = seriesIndex,
                    ),
                )
                if (stacked && plotY != null) {
                    if (plotY >= 0.0) {
                        positiveBase += plotY
                    } else {
                        negativeBase += plotY
                    }
                }
            }
        }
        return seriesOrder.keys.map { key -> ChartSeries(key, points.getValue(key)) }
    }

    private data class BarSegment(
        val x: String,
        val xOrder: Int,
        val series: String,
        val seriesIndex: Int,
        val y: Double?,
        val exactY: String?,
        val sourceRowIndex: Int,
    )

    private fun buildTimeSeries(
        chartId: String,
        revision: Long,
        result: WorksheetResult,
        options: ChartOptions,
        derivedTitle: String,
        kind: ChartKind,
        sourceNote: String?,
    ): ChartSpec {
        val label = if (kind == ChartKind.AREA) "Area" else "Line"
        if (options.lineOpacity != null &&
            (!options.lineOpacity.isFinite() ||
                options.lineOpacity !in ChartOptions.MIN_LINE_OPACITY..ChartOptions.MAX_LINE_OPACITY)
        ) {
            throw ChartSpecException(
                "Line opacity must be between ${ChartOptions.MIN_LINE_OPACITY} and " +
                    "${ChartOptions.MAX_LINE_OPACITY}.",
            )
        }
        if (options.lineWidth != null &&
            (!options.lineWidth.isFinite() || options.lineWidth !in ChartOptions.MIN_LINE_WIDTH..ChartOptions.MAX_LINE_WIDTH)
        ) {
            throw ChartSpecException(
                "Line width must be between ${ChartOptions.MIN_LINE_WIDTH} and ${ChartOptions.MAX_LINE_WIDTH}.",
            )
        }
        if (kind == ChartKind.AREA && options.logScale) {
            throw ChartSpecException("A logarithmic value axis is not available for Area marks in this build.")
        }
        val rowField = result.rowField
            ?: throw ChartSpecException("$label marks require a dimension on Rows.")
        val measure = result.measureField
            ?: throw ChartSpecException("$label marks require a measure on Values.")
        if (result.aggregation == Aggregation.NONE) {
            throw ChartSpecException("$label marks require an aggregate; raw-value lines are not implemented.")
        }
        if (result.result.rows.size > MAX_LINE_POINTS) {
            throw ChartSpecException(
                "$label chart supports at most $MAX_LINE_POINTS observed points; narrow the view or aggregate further.",
            )
        }
        val hasSeries = result.seriesField != null
        if (result.seriesField != null) {
            if (result.seriesField.id == rowField.id) {
                throw ChartSpecException("Color series must be a different field from the Rows dimension.")
            }
            if (result.seriesField.role != io.github.baitian6641.cloudydata.model.FieldRole.DIMENSION) {
                throw ChartSpecException("Color series must be a dimension.")
            }
        }
        val xColumn = 0
        val seriesColumn = if (hasSeries) 1 else null
        val valueColumn = if (hasSeries) 2 else 1
        val xOrder = LinkedHashMap<String, Int>()
        val grouped = LinkedHashMap<String, MutableList<ChartPoint>>()
        result.result.rows.forEachIndexed { rowIndex, row ->
            val x = display(row.getOrNull(xColumn))
            val order = xOrder.getOrPut(x) { xOrder.size }
            val series = seriesColumn?.let { display(row.getOrNull(it)) } ?: measure.name
            val raw = row.getOrNull(valueColumn)
            val y = raw?.let { valueAsDouble(it) }
            if (raw != null && y == null) throw ChartSpecException("$label value for $x is not numeric.")
            if (y != null && !y.isFinite()) throw ChartSpecException("$label value for $x is not finite.")
            grouped.getOrPut(series) { mutableListOf() }.add(
                ChartPoint(
                    x = x,
                    xOrder = order,
                    series = series,
                    y = y,
                    exactY = raw?.toString(),
                    defined = y != null,
                    sourceRowIndex = rowIndex,
                ),
            )
        }
        if (grouped.isEmpty()) throw ChartSpecException("The current view has no $label points.")
        validateYDomain(options, grouped.values.flatten().mapNotNull { it.y })
        if (grouped.values.flatten().none { it.defined }) {
            throw ChartSpecException("The current view has no numeric $label points.")
        }
        val areaStacked = kind == ChartKind.AREA &&
            hasSeries &&
            options.areaStackMode != StackMode.NONE
        if (areaStacked &&
            result.aggregation != Aggregation.SUM && result.aggregation != Aggregation.COUNT
        ) {
            throw ChartSpecException("Stacked area charts require Sum or Count aggregation.")
        }
        val percentArea = areaStacked && options.areaStackMode == StackMode.PERCENT_STACKED
        val effectiveAreaStackMode = if (areaStacked) options.areaStackMode else StackMode.NONE
        val series = if (areaStacked) {
            val allPoints = grouped.values.flatten()
            val negative = allPoints.firstOrNull { it.y != null && it.y < 0.0 }
            if (negative != null) {
                throw ChartSpecException(
                    "Stacked area charts require non-negative values; ${negative.x} has a negative value.",
                )
            }
            val totals = xOrder.keys.associateWith { x ->
                allPoints.filter { it.x == x }.sumOf { it.y ?: 0.0 }.also { total ->
                    if (percentArea && total <= 0.0) {
                        throw ChartSpecException(
                            "100% area charts need a positive point total; $x has $total.",
                        )
                    }
                }
            }
            val rawBySeries = grouped.mapValues { (_, points) -> points.associateBy { it.x } }
            val stackedPoints = grouped.keys.associateWith { mutableListOf<ChartPoint>() }
            for (x in xOrder.keys) {
                var base = 0.0
                for (key in grouped.keys) {
                    val point = rawBySeries.getValue(key)[x] ?: continue
                    val rawY = point.y
                    val plotY = if (percentArea && rawY != null) {
                        rawY / totals.getValue(x)
                    } else {
                        rawY
                    }
                    stackedPoints.getValue(key).add(
                        point.copy(y = plotY, stackBase = base),
                    )
                    if (plotY != null) base += plotY
                }
            }
            grouped.keys.map { key -> ChartSeries(key, stackedPoints.getValue(key)) }
        } else {
            grouped.map { (key, points) -> ChartSeries(key, points.sortedBy { it.xOrder }) }
        }
        val units = options.units?.trim()?.take(16)?.takeIf { it.isNotEmpty() }
        val yTitle = titleWithUnits(
            options.yAxisTitle ?: "${aggregationLabel(result.aggregation)} of ${measure.name}",
            units,
        )
        val baseAxis = axisFor(options, yTitle)
        return ChartSpec(
            chartId = chartId,
            revision = revision,
            kind = kind,
            title = options.title ?: derivedTitle,
            subtitle = options.subtitle?.take(160),
            caption = options.caption?.take(240),
            xAxis = AxisSpec(
                title = options.xAxisTitle ?: rowField.name,
                reverse = false,
                labelValues = AxisLabelSelection.thin(xOrder.keys.toList(), options.xLabelCount),
                titleAnchor = options.axisTitleAnchor,
            ),
            yAxis = if (kind == ChartKind.AREA) {
                if (percentArea) {
                    baseAxis.copy(min = 0.0, max = 1.0, zero = true)
                } else {
                    baseAxis.copy(
                        min = baseAxis.min?.coerceAtMost(0.0),
                        max = baseAxis.max?.coerceAtLeast(0.0),
                        zero = true,
                    )
                }
            } else {
                baseAxis
            },
            gridlines = GridlineSpec(
                options.showGridlines,
                options.gridlineWidth,
                options.gridlineContrast,
                options.minorGridlines,
            ),
            legend = LegendSpec(
                visible = options.legendVisible && series.size > 1 &&
                    (kind == ChartKind.LINE || (kind == ChartKind.AREA && hasSeries)),
                position = options.legendPosition,
                title = options.seriesTitle ?: result.seriesField?.name ?: "Series",
                orientation = options.legendOrientation,
                markSample = options.legendMarkSample,
            ),
            labels = LabelSpec(
                show = options.showValueLabels,
                fontSize = options.labelFontSize,
                position = LabelPosition.TOP,
                collisionPolicy = options.labelCollisionPolicy,
            ),
            markers = MarkerSpec(
                options.showMarkers,
                options.markerSize,
                options.markerShape,
                options.markerOpacity,
                options.markerBorder,
                options.markerBorderWidth,
            ),
            sourceNote = sourceNote,
            tooltip = TooltipSpec(options.showTooltip),
            series = series,
            hiddenSeries = if ((kind == ChartKind.LINE || kind == ChartKind.AREA) && series.size > 1) {
                options.hiddenSeries.intersect(series.map { it.key }.toSet())
            } else {
                emptySet()
            },
            rowCount = result.result.rows.size,
            truncated = result.result.truncated,
            missingPolicy = MissingPolicy.CONNECT_OBSERVED,
            lineWidth = options.lineWidth,
            lineInterpolation = options.lineInterpolation,
            lineDashStyle = options.lineDashStyle,
            lineOpacity = options.lineOpacity,
            units = units,
            valueDecimals = options.valueDecimals,
            valueFormat = if (percentArea) ValueDisplayFormat.PERCENT else options.valueFormat,
            currencyCode = options.currencyCode,
            areaStackMode = effectiveAreaStackMode,
            chartBorder = options.showChartBorder,
            transparentExportBackground = options.transparentExportBackground,
            padding = options.chartPadding,
        )
    }

    private fun axisFor(options: ChartOptions, title: String): AxisSpec = AxisSpec(
        title = title,
        min = options.yMin,
        max = options.yMax,
        zero = options.zeroBaseline && !options.logScale,
        log = options.logScale,
        reverse = options.reverseY,
        tickCount = options.yTickCount,
        titleAnchor = options.axisTitleAnchor,
    )

    private fun titleWithUnits(title: String, units: String?): String =
        if (units == null || title.endsWith("($units)")) title else "$title ($units)"

    private fun validateAxisOptions(options: ChartOptions) {
        val min = options.yMin
        val max = options.yMax
        if (min != null && !min.isFinite()) throw ChartSpecException("Y minimum must be finite.")
        if (max != null && !max.isFinite()) throw ChartSpecException("Y maximum must be finite.")
        if (min != null && max != null && min >= max) {
            throw ChartSpecException("Y minimum must be less than Y maximum.")
        }
        if (options.logScale) {
            if (min != null && min <= 0.0) throw ChartSpecException("A log Y minimum must be greater than zero.")
            if (max != null && max <= 0.0) throw ChartSpecException("A log Y maximum must be greater than zero.")
        }
        if (options.yTickCount != null &&
            options.yTickCount !in ChartOptions.MIN_TICK_COUNT..ChartOptions.MAX_TICK_COUNT
        ) {
            throw ChartSpecException(
                "Y tick count must be between ${ChartOptions.MIN_TICK_COUNT} and ${ChartOptions.MAX_TICK_COUNT}.",
            )
        }
        if (options.valueDecimals != null &&
            options.valueDecimals !in ChartOptions.MIN_VALUE_DECIMALS..ChartOptions.MAX_VALUE_DECIMALS
        ) {
            throw ChartSpecException(
                "Value decimals must be between ${ChartOptions.MIN_VALUE_DECIMALS} and " +
                    "${ChartOptions.MAX_VALUE_DECIMALS}.",
            )
        }
        if (options.markerBorderWidth != null &&
            (!options.markerBorderWidth.isFinite() ||
                options.markerBorderWidth !in
                ChartOptions.MIN_MARKER_BORDER_WIDTH..ChartOptions.MAX_MARKER_BORDER_WIDTH)
        ) {
            throw ChartSpecException(
                "Marker border width must be between ${ChartOptions.MIN_MARKER_BORDER_WIDTH} and " +
                    "${ChartOptions.MAX_MARKER_BORDER_WIDTH}.",
            )
        }
        if (options.markerOpacity != null &&
            (!options.markerOpacity.isFinite() ||
                options.markerOpacity !in ChartOptions.MIN_MARKER_OPACITY..ChartOptions.MAX_MARKER_OPACITY)
        ) {
            throw ChartSpecException(
                "Marker opacity must be between ${ChartOptions.MIN_MARKER_OPACITY} and " +
                    "${ChartOptions.MAX_MARKER_OPACITY}.",
            )
        }
        if (options.markerSize != null &&
            (!options.markerSize.isFinite() ||
                options.markerSize !in ChartOptions.MIN_MARKER_SIZE..ChartOptions.MAX_MARKER_SIZE)
        ) {
            throw ChartSpecException(
                "Marker size must be between ${ChartOptions.MIN_MARKER_SIZE} and " +
                    "${ChartOptions.MAX_MARKER_SIZE}.",
            )
        }
        if (options.gridlineWidth != null &&
            (!options.gridlineWidth.isFinite() ||
                options.gridlineWidth !in ChartOptions.MIN_GRIDLINE_WIDTH..ChartOptions.MAX_GRIDLINE_WIDTH)
        ) {
            throw ChartSpecException(
                "Gridline width must be between ${ChartOptions.MIN_GRIDLINE_WIDTH} and " +
                    "${ChartOptions.MAX_GRIDLINE_WIDTH}.",
            )
        }
        if (options.xLabelCount != null &&
            options.xLabelCount !in ChartOptions.MIN_TICK_COUNT..ChartOptions.MAX_TICK_COUNT
        ) {
            throw ChartSpecException(
                "X label count must be between ${ChartOptions.MIN_TICK_COUNT} and ${ChartOptions.MAX_TICK_COUNT}.",
            )
        }
        if (options.chartPadding !in ChartOptions.MIN_CHART_PADDING..ChartOptions.MAX_CHART_PADDING) {
            throw ChartSpecException(
                "Chart padding must be between ${ChartOptions.MIN_CHART_PADDING} and " +
                    "${ChartOptions.MAX_CHART_PADDING}.",
            )
        }
        if (options.labelFontSize != null &&
            options.labelFontSize !in ChartOptions.MIN_LABEL_FONT_SIZE..ChartOptions.MAX_LABEL_FONT_SIZE
        ) {
            throw ChartSpecException(
                "Label font size must be between ${ChartOptions.MIN_LABEL_FONT_SIZE} and " +
                    "${ChartOptions.MAX_LABEL_FONT_SIZE}.",
            )
        }
    }

    private fun validateYDomain(options: ChartOptions, values: List<Double>) {
        if (options.logScale && values.any { it <= 0.0 }) {
            throw ChartSpecException("A logarithmic Y axis requires every value to be greater than zero.")
        }
    }

    private fun display(value: Any?): String = when (value) {
        null -> "NULL"
        else -> value.toString()
    }

    private fun aggregationLabel(aggregation: Aggregation): String = when (aggregation) {
        Aggregation.SUM -> "Sum"
        Aggregation.AVG -> "Average"
        Aggregation.MIN -> "Minimum"
        Aggregation.MAX -> "Maximum"
        Aggregation.COUNT -> "Count"
        Aggregation.COUNT_DISTINCT -> "Distinct count"
        Aggregation.NONE -> "Value"
    }
}
