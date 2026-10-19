package io.github.baitian6641.cloudydata.chart

import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.MarkType
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
                allowSeries = true,
                sourceNote = sourceNote,
            )
            MarkType.AREA -> buildTimeSeries(
                chartId, revision, result, options, derivedTitle,
                kind = ChartKind.AREA,
                allowSeries = false,
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
        if (result.seriesField != null) {
            throw ChartSpecException("Color series is only available for Line marks in this build.")
        }
        if (options.logScale) {
            throw ChartSpecException("A logarithmic value axis is only available for Line marks in this build.")
        }
        val rows = result.result.rows
        if (rows.size > MAX_BAR_CATEGORIES) {
            throw ChartSpecException(
                "Bar chart supports at most $MAX_BAR_CATEGORIES categories; use the table or aggregate further.",
            )
        }
        val valueColumn = if (result.rowField != null) 1 else 0
        val labelColumn = if (result.rowField != null) 0 else null
        val points = rows.mapIndexed { index, row ->
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
            )
        }
        if (points.none { it.defined }) throw ChartSpecException("The current view has no numeric marks.")
        validateYDomain(options, points.mapNotNull { it.y })
        val units = options.units?.trim()?.take(16)?.takeIf { it.isNotEmpty() }
        val yTitle = titleWithUnits(
            options.yAxisTitle
                ?: result.measureField?.let { "${aggregationLabel(result.aggregation)} of ${it.name}" }
                ?: "Value",
            units,
        )
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
                labelValues = AxisLabelSelection.thin(points.map { it.x }, options.xLabelCount),
                titleAnchor = options.axisTitleAnchor,
            ),
            yAxis = axisFor(options, yTitle),
            gridlines = GridlineSpec(
                options.showGridlines,
                options.gridlineWidth,
                options.gridlineContrast,
                options.minorGridlines,
            ),
            // Category color legends are intentionally deferred until grouped
            // bar semantics exist; the legend control applies to line series.
            legend = LegendSpec(false, options.legendPosition, null),
            labels = LabelSpec(
                show = options.showValueLabels,
                fontSize = options.labelFontSize,
                position = options.labelPosition,
                collisionPolicy = options.labelCollisionPolicy,
            ),
            sourceNote = sourceNote,
            tooltip = TooltipSpec(options.showTooltip),
            series = listOf(ChartSeries("Values", points)),
            rowCount = rows.size,
            truncated = result.result.truncated,
            units = units,
            valueDecimals = options.valueDecimals,
            valueFormat = options.valueFormat,
            chartBorder = options.showChartBorder,
            transparentExportBackground = options.transparentExportBackground,
            padding = options.chartPadding,
        )
    }

    private fun buildTimeSeries(
        chartId: String,
        revision: Long,
        result: WorksheetResult,
        options: ChartOptions,
        derivedTitle: String,
        kind: ChartKind,
        allowSeries: Boolean,
        sourceNote: String?,
    ): ChartSpec {
        val label = if (kind == ChartKind.AREA) "Area" else "Line"
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
        if (result.seriesField != null && !allowSeries) {
            throw ChartSpecException("Area charts do not support a color series in this build.")
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
        val series = grouped.map { (key, points) -> ChartSeries(key, points.sortedBy { it.xOrder }) }
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
                baseAxis.copy(
                    min = baseAxis.min?.coerceAtMost(0.0),
                    max = baseAxis.max?.coerceAtLeast(0.0),
                    zero = true,
                )
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
                visible = kind == ChartKind.LINE && options.legendVisible && series.size > 1,
                position = options.legendPosition,
                title = options.seriesTitle ?: result.seriesField?.name ?: "Series",
                orientation = options.legendOrientation,
            ),
            labels = LabelSpec(
                show = options.showValueLabels,
                fontSize = options.labelFontSize,
                position = LabelPosition.TOP,
                collisionPolicy = options.labelCollisionPolicy,
            ),
            sourceNote = sourceNote,
            tooltip = TooltipSpec(options.showTooltip),
            series = series,
            rowCount = result.result.rows.size,
            truncated = result.result.truncated,
            missingPolicy = MissingPolicy.CONNECT_OBSERVED,
            lineWidth = options.lineWidth,
            units = units,
            valueDecimals = options.valueDecimals,
            valueFormat = options.valueFormat,
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
