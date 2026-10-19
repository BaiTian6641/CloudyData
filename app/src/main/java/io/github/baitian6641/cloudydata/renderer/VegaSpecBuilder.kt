package io.github.baitian6641.cloudydata.renderer

import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartPoint
import io.github.baitian6641.cloudydata.chart.ChartSpec
import io.github.baitian6641.cloudydata.chart.LegendLayout
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
import io.github.baitian6641.cloudydata.model.BarOrientation
import io.github.baitian6641.cloudydata.model.StackMode
import io.github.baitian6641.cloudydata.model.GridlineContrast
import io.github.baitian6641.cloudydata.model.LabelCollisionPolicy
import io.github.baitian6641.cloudydata.model.LineDashStyle
import io.github.baitian6641.cloudydata.model.CurrencyCode
import io.github.baitian6641.cloudydata.model.LegendMarkSample
import io.github.baitian6641.cloudydata.model.LineInterpolation
import io.github.baitian6641.cloudydata.model.MarkerShape
import io.github.baitian6641.cloudydata.model.ValueDisplayFormat
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.LegendPosition
import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap

/**
 * Lowers a validated typed ChartSpec to Vega JSON. This is the only renderer
 * lowering path; it cannot aggregate, filter, interpolate or synthesize data.
 */
object VegaSpecBuilder {
    fun build(
        spec: ChartSpec,
        dark: Boolean,
        palette: List<Int>,
        widthDp: Float,
        heightDp: Float,
    ): JSONObject {
        require(palette.isNotEmpty()) { "palette is empty" }
        val textColor = if (dark) "#E1EBED" else "#182427"
        val gridColor = colorHex(if (dark) CloudyTokens.Dark.divider else CloudyTokens.Light.divider)
        val rows = JSONArray()
        val xOrder = TreeMap<Int, String>()
        val hiddenSeries = spec.hiddenSeries
        val visibleExpr = if (hiddenSeries.isEmpty()) {
            "true"
        } else {
            hiddenSeries.joinToString(" && ") {
                "datum.series !== '${escapeVegaString(it)}'"
            }
        }
        val seriesDomain = JSONArray()
        spec.series.forEach { series ->
            seriesDomain.put(series.key)
            series.points.forEach { point ->
                xOrder[point.xOrder] = point.x
                rows.put(pointJson(point))
            }
        }
        val xDomain = JSONArray(xOrder.values)
        val colorDomain = when (spec.kind) {
            ChartKind.LINE, ChartKind.AREA -> seriesDomain
            ChartKind.BAR -> if (spec.barHasSeries) seriesDomain else xDomain
        }
        val colorScale = JSONObject()
            .put("name", "color")
            .put("type", "ordinal")
            .put("domain", colorDomain)
            .put("range", JSONArray(palette.map { "#%06X".format(0xFFFFFF and it) }))

        val horizontalBar = spec.kind == ChartKind.BAR &&
            spec.barOrientation == BarOrientation.HORIZONTAL
        val hasExplicitRange = spec.yAxis.min != null || spec.yAxis.max != null
        fun valueScale(name: String, range: String): JSONObject {
            val scale = JSONObject()
                .put("name", name)
                .put("type", if (spec.yAxis.log) "log" else "linear")
                .put("range", range)
                // Explicit bounds are authoritative; Vega's default zero/nice would
                // silently expand them and misreport the configured range.
                .put("nice", !hasExplicitRange)
                .put("reverse", spec.yAxis.reverse)
            val bothBounds = spec.yAxis.min != null && spec.yAxis.max != null
            if (bothBounds) {
                scale.put("domain", JSONArray().put(spec.yAxis.min).put(spec.yAxis.max))
                scale.put("zero", false)
            } else {
                scale.put(
                    "domain",
                    JSONObject().put("data", "table")
                        .put("field", if (usesStackedValueDomain(spec)) "displayY" else "y"),
                )
                spec.yAxis.min?.let { scale.put("domainMin", it) }
                spec.yAxis.max?.let { scale.put("domainMax", it) }
                if (!spec.yAxis.log) scale.put("zero", spec.yAxis.zero)
            }
            if (spec.yAxis.log) scale.put("zero", false)
            return scale
        }
        val categoryScale = JSONObject()
            .put("name", if (horizontalBar) "y" else "x")
            .put("type", "band")
            .put("domain", xDomain)
            .put("range", if (horizontalBar) "height" else "width")
            .put("paddingInner", if (spec.kind == ChartKind.BAR) 0.2 else 0.05)
        val xScale = if (horizontalBar) valueScale("x", "width") else categoryScale
        val yScale = if (horizontalBar) categoryScale else valueScale("y", "height")

        val visibleXLabels = spec.xAxis.labelValues?.size ?: xDomain.length()
        val valueFormat = when (spec.valueFormat) {
            ValueDisplayFormat.NUMBER -> spec.valueDecimals?.let { ",.${it}f" }
            ValueDisplayFormat.PERCENT -> ",.${spec.valueDecimals ?: 1}%"
            ValueDisplayFormat.CURRENCY ->
                if (spec.currencyCode == CurrencyCode.USD) "$,.2f" else ",.2f"
        }
        val gridlineStroke = when (spec.gridlines.contrast) {
            GridlineContrast.DEFAULT -> gridColor
            GridlineContrast.STRONG ->
                colorHex(if (dark) CloudyTokens.Dark.outline else CloudyTokens.Light.outline)
        }
        val valueAxisLabelSignal = if (spec.valueFormat == ValueDisplayFormat.CURRENCY &&
            spec.currencyCode != CurrencyCode.USD
        ) {
            currencyAxisLabelSignal(spec.currencyCode)
        } else {
            null
        }
        val axes = JSONArray()
        if (horizontalBar) {
            axes.put(
                axisJson(
                    orient = "left",
                    scale = "y",
                    title = spec.xAxis.title,
                    textColor = textColor,
                    gridColor = gridColor,
                    grid = false,
                    labelAngle = 0,
                    titleAnchor = spec.xAxis.titleAnchor,
                    labelValues = spec.xAxis.labelValues,
                ),
            )
            if (spec.gridlines.minor && spec.gridlines.show && !spec.yAxis.log) {
                val minorValues =
                    "domain('x')[0] === domain('x')[1] ? [] : " +
                        "sequence(domain('x')[0], domain('x')[1], " +
                        "(domain('x')[1] - domain('x')[0]) / 20)"
                axes.put(
                    axisJson(
                        orient = "bottom",
                        scale = "x",
                        title = null,
                        textColor = textColor,
                        gridColor = gridColor,
                        grid = true,
                        gridLineColor = gridlineStroke,
                        gridWidth = 0.5,
                        gridOpacity = 0.55,
                        labelAngle = 0,
                        valueSignal = minorValues,
                        domain = false,
                        ticks = false,
                        labels = false,
                        aria = false,
                    ),
                )
            }
            axes.put(
                axisJson(
                    orient = "bottom",
                    scale = "x",
                    title = spec.yAxis.title,
                    textColor = textColor,
                    gridColor = gridColor,
                    grid = spec.gridlines.show,
                    gridLineColor = gridlineStroke,
                    gridWidth = spec.gridlines.width,
                    labelAngle = 0,
                    tickCount = spec.yAxis.tickCount,
                    format = valueFormat,
                    labelSignal = valueAxisLabelSignal,
                    titleAnchor = spec.yAxis.titleAnchor,
                ),
            )
        } else {
            axes.put(
                axisJson(
                    orient = "bottom",
                    scale = "x",
                    title = spec.xAxis.title,
                    textColor = textColor,
                    gridColor = gridColor,
                    grid = false,
                    labelAngle = if (spec.kind != ChartKind.BAR && visibleXLabels > 8) -35 else 0,
                    titleAnchor = spec.xAxis.titleAnchor,
                    labelValues = spec.xAxis.labelValues,
                ),
            )
            if (spec.gridlines.minor && spec.gridlines.show && !spec.yAxis.log) {
                // Explicit values from the resolved scale domain, because a second
                // tickCount often quantizes to the same nice ticks as the major axis.
                val minorValues =
                    "domain('y')[0] === domain('y')[1] ? [] : " +
                        "sequence(domain('y')[0], domain('y')[1], " +
                        "(domain('y')[1] - domain('y')[0]) / 20)"
                axes.put(
                    axisJson(
                        orient = "left",
                        scale = "y",
                        title = null,
                        textColor = textColor,
                        gridColor = gridColor,
                        grid = true,
                        gridLineColor = gridlineStroke,
                        gridWidth = 0.5,
                        gridOpacity = 0.55,
                        labelAngle = 0,
                        valueSignal = minorValues,
                        domain = false,
                        ticks = false,
                        labels = false,
                        aria = false,
                    ),
                )
            }
            axes.put(
                axisJson(
                    orient = "left",
                    scale = "y",
                    title = spec.yAxis.title,
                    textColor = textColor,
                    gridColor = gridColor,
                    grid = spec.gridlines.show,
                    gridLineColor = gridlineStroke,
                    gridWidth = spec.gridlines.width,
                    labelAngle = 0,
                    tickCount = spec.yAxis.tickCount,
                    format = valueFormat,
                    labelSignal = valueAxisLabelSignal,
                    titleAnchor = spec.yAxis.titleAnchor,
                ),
            )
        }

        val marks = JSONArray()
        when (spec.kind) {
            ChartKind.BAR -> {
                marks.put(barMark(spec))
                if (spec.labels.show) marks.put(barLabelMark(spec, textColor, dark))
                marks.put(barSelectionMark(spec, textColor))
            }
            ChartKind.LINE -> {
                marks.put(lineGroup(spec, textColor, visibleExpr))
                // Selected points are drawn beneath the transparent hit target so
                // the hit target keeps receiving clicks.
                marks.put(selectedPointMark(spec, textColor, visibleExpr))
                // Facet groups do not reliably surface the child point datum on a
                // click. A top-level, nearly transparent symbol gives every observed
                // point a stable hit target that carries sourceRowIndex.
                marks.put(lineHitMark(spec, visibleExpr))
            }
            ChartKind.AREA -> {
                marks.put(areaMark(spec))
                marks.put(areaLineMark(spec))
                if (spec.pointCount <= 200 && spec.markers.show) {
                    marks.put(areaSymbolMark(spec, textColor))
                }
                if (spec.labels.show) marks.put(areaLabelMark(spec, textColor))
                marks.put(selectedPointMark(spec, textColor, visibleExpr))
                marks.put(lineHitMark(spec, visibleExpr))
            }
        }

        val root = JSONObject()
            .put("\$schema", "https://vega.github.io/schema/vega/v6.json")
            .put("version", spec.version)
            .put("width", widthDp)
            .put("height", heightDp)
            .put("padding", spec.padding)
            .put(
                "data",
                JSONArray()
                    .put(JSONObject().put("name", "table").put("values", rows))
                    .put(
                        JSONObject().put("name", "visible").put("source", "table")
                            .put(
                                "transform",
                                JSONArray().put(
                                    JSONObject().put("type", "filter").put("expr", visibleExpr),
                                ),
                            ),
                    )
                    .put(JSONObject().put("name", "selected").put("values", JSONArray())),
            )
            .put("scales", JSONArray().put(xScale).put(yScale).put(colorScale))
            .put("axes", axes)
            .put(
                "signals",
                JSONArray()
                    .put(JSONObject().put("name", "cloudyPlotHeight").put("value", heightDp))
                    .put(JSONObject().put("name", "cloudyPlotWidth").put("value", widthDp))
                    .put(selectionSignal())
                    .put(hoverSignal()),
            )
            .put(
                "usermeta",
                JSONObject()
                    .put("tooltip", spec.tooltip.show)
            .put("chartBorder", spec.chartBorder)
            .put("transparentExportBackground", spec.transparentExportBackground)
            .put("hiddenSeries", JSONArray(spec.hiddenSeries.toList()))
            .put("barStackMode", spec.barStackMode.name)
            .put("barOrientation", spec.barOrientation.name)
            .put("areaStackMode", spec.areaStackMode.name)
            .put("legendInteractive", spec.kind != ChartKind.BAR)
            .put("series", JSONArray(spec.series.map { it.key }))
                    .put("units", spec.units ?: ""),
            )
            .put("marks", marks)
        val legend = legendJson(spec, textColor, widthDp)
        if (legend != null) root.put("legends", JSONArray().put(legend))
        return root
    }

    private fun pointJson(point: ChartPoint): JSONObject = JSONObject()
        .put("x", point.x)
        .put("y", point.y ?: JSONObject.NULL)
        .put("series", point.series)
        .put("sourceRowIndex", point.sourceRowIndex)
        .put("exactY", point.exactY ?: JSONObject.NULL)
        .put("defined", point.defined)
        .put("stackBase", point.stackBase ?: JSONObject.NULL)
        .put(
            "displayY",
            if (point.stackBase != null && point.y != null) {
                point.stackBase + point.y
            } else {
                point.y ?: JSONObject.NULL
            },
        )
        .put("seriesIndex", point.seriesIndex)

    private fun markerArea(spec: ChartSpec, default: Double): Double =
        spec.markers.size?.let { it * it } ?: default

    private fun applyLineStroke(spec: ChartSpec, mark: JSONObject, defaultWidth: Double) {
        val enter = mark.getJSONObject("encode").getJSONObject("enter")
        spec.lineOpacity?.let { enter.put("opacity", JSONObject().put("value", it)) }
        if (spec.lineInterpolation != LineInterpolation.LINEAR) {
            enter.put(
                "interpolate",
                JSONObject().put("value", lineInterpolationName(spec.lineInterpolation)),
            )
        }
        lineDashArray(spec, defaultWidth)?.let {
            enter.put("strokeDash", JSONObject().put("value", it))
        }
        if (spec.lineDashStyle == LineDashStyle.DOTTED) {
            enter.put("strokeCap", JSONObject().put("value", "round"))
        }
    }

    private fun currencyValueExpression(code: CurrencyCode, value: String): String {
        if (code == CurrencyCode.USD) return "format($value, '$,.2f')"
        val symbol = currencySymbol(code)
        return "($value < 0 ? '\u2212$symbol' : '$symbol') + format(abs($value), ',.2f')"
    }

    private fun currencyAxisLabelSignal(code: CurrencyCode): String =
        currencyValueExpression(code, "datum.value")

    private fun currencySymbol(code: CurrencyCode): String = when (code) {
        CurrencyCode.USD -> "$"
        CurrencyCode.EUR -> "€"
        CurrencyCode.GBP -> "£"
        CurrencyCode.JPY -> "¥"
    }

    private fun lineDashArray(spec: ChartSpec, defaultWidth: Double): JSONArray? =
        when (spec.lineDashStyle) {
            LineDashStyle.SOLID -> null
            LineDashStyle.DASHED -> JSONArray().put(6).put(4)
            LineDashStyle.DOTTED -> {
                val width = spec.lineWidth ?: defaultWidth
                JSONArray().put(1).put(width + 3.0)
            }
        }

    private fun lineInterpolationName(interpolation: LineInterpolation): String =
        when (interpolation) {
            LineInterpolation.LINEAR -> "linear"
            LineInterpolation.STEP -> "step"
            LineInterpolation.MONOTONE -> "monotone"
        }

    private fun markerShapeName(shape: MarkerShape): String = when (shape) {
        MarkerShape.CIRCLE -> "circle"
        MarkerShape.SQUARE -> "square"
        MarkerShape.DIAMOND -> "diamond"
        MarkerShape.CROSS -> "cross"
    }

    private fun escapeVegaString(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("'", "\\'")

    private fun colorHex(value: Long): String =
        "#%06X".format(value.toInt() and 0xFFFFFF)

    private fun axisJson(
        orient: String,
        scale: String,
        title: String?,
        textColor: String,
        gridColor: String,
        grid: Boolean,
        gridLineColor: String = gridColor,
        gridWidth: Double? = null,
        gridOpacity: Double? = null,
        labelAngle: Int,
        tickCount: Int? = null,
        titleAnchor: AxisTitleAnchor = AxisTitleAnchor.AUTO,
        labelValues: List<String>? = null,
        valueSignal: String? = null,
        format: String? = null,
        labelSignal: String? = null,
        domain: Boolean = true,
        ticks: Boolean = true,
        labels: Boolean = true,
        aria: Boolean? = null,
    ): JSONObject {
        val axis = JSONObject()
            .put("orient", orient)
            .put("scale", scale)
            .put("labelColor", textColor)
            .put("labelFontSize", 12)
            .put("domainColor", gridColor)
            .put("tickColor", gridColor)
            .put("grid", grid)
            .put("gridColor", gridLineColor)
            .put("labelAngle", labelAngle)
        if (!domain) axis.put("domain", false)
        if (!ticks) axis.put("ticks", false)
        if (!labels) axis.put("labels", false)
        if (aria != null) axis.put("aria", aria)
        if (gridWidth != null && grid) {
            axis.put("gridWidth", gridWidth)
        }
        if (gridOpacity != null && grid) {
            axis.put("gridOpacity", gridOpacity)
        }
        if (format != null) {
            axis.put("format", format)
        }
        if (labelSignal != null) {
            axis.put(
                "encode",
                JSONObject().put(
                    "labels",
                    JSONObject().put(
                        "update",
                        JSONObject().put("text", JSONObject().put("signal", labelSignal)),
                    ),
                ),
            )
        }
        if (tickCount != null) {
            axis.put("tickCount", tickCount).put("labelOverlap", "greedy")
        }
        if (titleAnchor != AxisTitleAnchor.AUTO) {
            axis.put("titleAnchor", titleAnchor.name.lowercase())
        }
        if (!labelValues.isNullOrEmpty()) {
            axis.put("values", JSONArray(labelValues)).put("labelOverlap", "greedy")
        }
        if (valueSignal != null) {
            axis.put("values", JSONObject().put("signal", valueSignal))
        }
        if (!title.isNullOrBlank()) {
            axis.put("title", title)
                .put("titleColor", textColor)
                .put("titleFontSize", 12)
                .put("titlePadding", 8)
        }
        return axis
    }

    private fun usesStackedValueDomain(spec: ChartSpec): Boolean =
        isStackedBar(spec) ||
            (spec.kind == ChartKind.AREA && spec.areaStackMode != StackMode.NONE)

    private fun isHorizontalBar(spec: ChartSpec): Boolean =
        spec.kind == ChartKind.BAR && spec.barOrientation == BarOrientation.HORIZONTAL

    private fun isGroupedBar(spec: ChartSpec): Boolean =
        spec.barHasSeries &&
            spec.barStackMode == StackMode.NONE &&
            spec.series.size > 1

    private fun isStackedBar(spec: ChartSpec): Boolean =
        spec.barHasSeries &&
            (spec.barStackMode == StackMode.STACKED ||
                spec.barStackMode == StackMode.PERCENT_STACKED) &&
            spec.series.size > 1

    private fun barXEncode(spec: ChartSpec): JSONObject {
        val count = spec.series.size.coerceAtLeast(1)
        if (isHorizontalBar(spec)) {
            return if (isStackedBar(spec)) {
                JSONObject().put(
                    "signal",
                    "scale('x', min(datum.stackBase, datum.stackBase + datum.y))",
                )
            } else {
                JSONObject().put("signal", "scale('x', 0)")
            }
        }
        return when {
            isGroupedBar(spec) -> JSONObject().put(
                "signal",
                "scale('x', datum.x) + bandwidth('x') * " +
                    "(0.05 + datum.seriesIndex * 0.9 / $count)",
            )
            isStackedBar(spec) -> JSONObject().put(
                "signal",
                "scale('x', datum.x) + bandwidth('x') * 0.05",
            )
            else -> JSONObject().put("scale", "x").put("field", "x")
        }
    }

    private fun barX2Encode(spec: ChartSpec): JSONObject =
        if (isStackedBar(spec)) {
            JSONObject().put(
                "signal",
                "scale('x', max(datum.stackBase, datum.stackBase + datum.y))",
            )
        } else {
            JSONObject().put("scale", "x").put("field", "y")
        }

    private fun barWidthEncode(spec: ChartSpec): JSONObject {
        val count = spec.series.size.coerceAtLeast(1)
        return when {
            isGroupedBar(spec) -> JSONObject().put(
                "signal",
                "bandwidth('x') * 0.9 / $count",
            )
            isStackedBar(spec) -> JSONObject().put("signal", "bandwidth('x') * 0.9")
            else -> JSONObject().put("scale", "x").put("band", 1)
        }
    }

    private fun barYEncode(spec: ChartSpec): JSONObject {
        val count = spec.series.size.coerceAtLeast(1)
        return when {
            isGroupedBar(spec) -> JSONObject().put(
                "signal",
                "scale('y', datum.x) + bandwidth('y') * " +
                    "(0.05 + datum.seriesIndex * 0.9 / $count)",
            )
            isStackedBar(spec) -> JSONObject().put(
                "signal",
                "scale('y', datum.x) + bandwidth('y') * 0.05",
            )
            else -> JSONObject().put("scale", "y").put("field", "x")
        }
    }

    private fun barHeightEncode(spec: ChartSpec): JSONObject {
        val count = spec.series.size.coerceAtLeast(1)
        return when {
            isGroupedBar(spec) -> JSONObject().put(
                "signal",
                "bandwidth('y') * 0.9 / $count",
            )
            isStackedBar(spec) -> JSONObject().put("signal", "bandwidth('y') * 0.9")
            else -> JSONObject().put("scale", "y").put("band", 1)
        }
    }

    private fun barLabelXEncode(spec: ChartSpec): JSONObject {
        val count = spec.series.size.coerceAtLeast(1)
        return if (isGroupedBar(spec)) {
            JSONObject().put(
                "signal",
                "scale('x', datum.x) + bandwidth('x') * " +
                    "(0.05 + (datum.seriesIndex + 0.5) * 0.9 / $count)",
            )
        } else {
            JSONObject().put("scale", "x").put("field", "x").put("band", 0.5)
        }
    }

    private fun barLabelYEncode(spec: ChartSpec): JSONObject {
        val count = spec.series.size.coerceAtLeast(1)
        return if (isGroupedBar(spec)) {
            JSONObject().put(
                "signal",
                "scale('y', datum.x) + bandwidth('y') * " +
                    "(0.05 + (datum.seriesIndex + 0.5) * 0.9 / $count)",
            )
        } else {
            JSONObject().put("scale", "y").put("field", "x").put("band", 0.5)
        }
    }

    private fun barMark(spec: ChartSpec): JSONObject {
        val enter = JSONObject()
        if (isHorizontalBar(spec)) {
            enter.put("x", barXEncode(spec))
                .put("x2", barX2Encode(spec))
                .put("y", barYEncode(spec))
                .put("height", barHeightEncode(spec))
        } else {
            enter.put("x", barXEncode(spec))
                .put("width", barWidthEncode(spec))
            if (isStackedBar(spec)) {
                enter.put(
                    "y",
                    JSONObject().put(
                        "signal",
                        "scale('y', max(datum.stackBase, datum.stackBase + datum.y))",
                    ),
                ).put(
                    "y2",
                    JSONObject().put(
                        "signal",
                        "scale('y', min(datum.stackBase, datum.stackBase + datum.y))",
                    ),
                )
            } else {
                enter.put("y", JSONObject().put("scale", "y").put("field", "y"))
                    .put("y2", JSONObject().put("scale", "y").put("value", 0))
            }
        }
        enter.put(
            "fill",
            JSONObject().put("scale", "color")
                .put("field", if (spec.barHasSeries) "series" else "x"),
        )
        return JSONObject()
            .put("name", "cloudyBar")
            .put("type", "rect")
            .put("from", JSONObject().put("data", "table"))
            .put("clip", true)
            .put("encode", JSONObject().put("enter", enter))
    }

    private fun labelDefined(spec: ChartSpec, fontSize: Int): String {
        val valid = "isValid(datum.y)"
        if (spec.labels.collisionPolicy == LabelCollisionPolicy.SHOW_ALL) return valid
        val spacing = if (spec.valueFormat == ValueDisplayFormat.CURRENCY) {
            fontSize * 7 + 6
        } else {
            fontSize + 36
        }
        return when (spec.kind) {
            ChartKind.BAR -> {
                val count = if (isGroupedBar(spec)) spec.series.size else 1
                val axis = if (isHorizontalBar(spec)) "y" else "x"
                "$valid && bandwidth('$axis') >= ${spacing * count}"
            }
            ChartKind.LINE, ChartKind.AREA -> "$valid && bandwidth('x') >= $spacing"
        }
    }

    private fun valueText(spec: ChartSpec, fontSize: Int): JSONObject {
        val visible = labelDefined(spec, fontSize)
        if (spec.valueFormat == ValueDisplayFormat.CURRENCY) {
            return JSONObject().put(
                "signal",
                "($visible) ? ${currencyValueExpression(spec.currencyCode, "datum.y")} : ''",
            )
        }
        val pattern = when (spec.valueFormat) {
            ValueDisplayFormat.NUMBER -> spec.valueDecimals?.let { ",.${it}f" }
            ValueDisplayFormat.PERCENT -> ",.${spec.valueDecimals ?: 1}%"
            ValueDisplayFormat.CURRENCY -> null
        }
        if (pattern == null && spec.labels.collisionPolicy == LabelCollisionPolicy.SHOW_ALL) {
            return JSONObject().put("field", "y")
        }
        val value = if (pattern == null) {
            "datum.y"
        } else {
            "format(datum.y, '$pattern')"
        }
        // Vega text marks ignore format/defined on field references; guard explicitly.
        return JSONObject().put("signal", "($visible) ? $value : ''")
    }

    private fun barLabelMark(spec: ChartSpec, textColor: String, dark: Boolean): JSONObject {
        if (isHorizontalBar(spec)) return horizontalBarLabelMark(spec, textColor, dark)
        if (isStackedBar(spec)) return stackedBarLabelMark(spec, textColor, dark)
        val fontSize = spec.labels.fontSize ?: 11
        val endpoint = "scale('y', datum.y)"
        val delta = "(scale('y', 0) - $endpoint)"
        val active = "(isValid(datum.y) && datum.y !== 0 && abs($delta) >= ${fontSize + 18})"
        val inside = if (spec.labels.position == LabelPosition.INSIDE_END) "($active)" else "false"
        val rawOffset = "($inside) ? (($delta) >= 0 ? 6 : -6) : (($delta) >= 0 ? -6 : 6)"
        val minY = fontSize + 2
        val maxY = "cloudyPlotHeight - ${fontSize + 2}"
        val clamped = "(($endpoint + ($rawOffset) < $minY) ? ($minY - $endpoint) : " +
            "(($endpoint + ($rawOffset) > $maxY) ? ($maxY - $endpoint) : ($rawOffset)))"
        val baseline = "($clamped) > 0 ? 'top' : 'bottom'"
        val anchor = "($endpoint + ($clamped))"
        val barTop = "min(scale('y', 0), $endpoint)"
        val barBottom = "max(scale('y', 0), $endpoint)"
        val overBar = "(datum.y !== 0 && $anchor >= $barTop && $anchor <= $barBottom)"
        val insideFill = if (dark) "#101A1D" else "#FFFFFF"
        val insideStroke = if (dark) "#FFFFFF" else "#101A1D"
        return JSONObject()
            .put("type", "text")
            .put("from", JSONObject().put("data", "table"))
            .put("clip", true)
            .put("interactive", false)
            .put(
                "encode",
                JSONObject().put(
                    "enter",
                    JSONObject()
                        .put("x", barLabelXEncode(spec))
                        .put(
                            "y",
                            JSONObject().put("scale", "y").put("field", "y")
                                .put("offset", JSONObject().put("signal", clamped)),
                        )
                        .put("text", valueText(spec, fontSize))
                        .put("align", JSONObject().put("value", "center"))
                        .put("baseline", JSONObject().put("signal", baseline))
                        .put(
                            "fill",
                            JSONObject().put(
                                "signal",
                                "($overBar) ? '$insideFill' : '$textColor'",
                            ),
                        )
                        .put(
                            "stroke",
                            JSONObject().put(
                                "signal",
                                "($overBar) ? '$insideStroke' : 'transparent'",
                            ),
                        )
                        .put("strokeWidth", JSONObject().put("signal", "($overBar) ? 1.25 : 0"))
                        .put("fontSize", JSONObject().put("value", fontSize))
                        .put(
                            "defined",
                            JSONObject().put("signal", labelDefined(spec, fontSize)),
                        ),
                ),
            )
    }

    private fun horizontalBarLabelMark(spec: ChartSpec, textColor: String, dark: Boolean): JSONObject {
        val fontSize = spec.labels.fontSize ?: 11
        val endpoint = if (isStackedBar(spec)) {
            "scale('x', datum.stackBase + datum.y)"
        } else {
            "scale('x', datum.y)"
        }
        val inside = spec.labels.position == LabelPosition.INSIDE_END
        val startPixel = if (isStackedBar(spec)) {
            "scale('x', datum.stackBase)"
        } else {
            "scale('x', 0)"
        }
        val towardEnd = "($endpoint >= $startPixel)"
        val rawOffset = if (inside) {
            "($towardEnd ? -6 : 6)"
        } else {
            "($towardEnd ? 6 : -6)"
        }
        val reserve = fontSize * 6 + 12
        val minX = reserve
        val maxX = "cloudyPlotWidth - $reserve"
        val clamped = "(($endpoint + ($rawOffset) < $minX) ? ($minX - $endpoint) : " +
            "(($endpoint + ($rawOffset) > $maxX) ? ($maxX - $endpoint) : ($rawOffset)))"
        val align = if (inside) {
            "($towardEnd ? 'right' : 'left')"
        } else {
            "($towardEnd ? 'left' : 'right')"
        }
        val insideFill = if (dark) "#101A1D" else "#FFFFFF"
        val insideStroke = if (dark) "#FFFFFF" else "#101A1D"
        val valueStart = if (isStackedBar(spec)) "datum.stackBase" else "0"
        val valueEnd = if (isStackedBar(spec)) "datum.stackBase + datum.y" else "datum.y"
        val labelActive = "(${labelDefined(spec, fontSize)}) && " +
            "abs(scale('x', $valueStart) - scale('x', $valueEnd)) >= ${fontSize + 18}"
        return JSONObject()
            .put("type", "text")
            .put("from", JSONObject().put("data", "table"))
            .put("clip", true)
            .put("interactive", false)
            .put(
                "encode",
                JSONObject().put(
                    "enter",
                    JSONObject()
                        .put(
                            "x",
                            JSONObject().put("signal", "($endpoint) + ($clamped)"),
                        )
                        .put("y", barLabelYEncode(spec))
                        .put("text", valueText(spec, fontSize))
                        .put("align", JSONObject().put("signal", align))
                        .put("baseline", JSONObject().put("value", "middle"))
                        .put("fill", JSONObject().put("value", if (inside) insideFill else textColor))
                        .put("stroke", JSONObject().put("value", if (inside) insideStroke else "transparent"))
                        .put("strokeWidth", JSONObject().put("value", if (inside) 1.25 else 0))
                        .put("fontSize", JSONObject().put("value", fontSize))
                        .put("defined", JSONObject().put("signal", labelActive)),
                ),
            )
    }

    private fun stackedBarLabelMark(spec: ChartSpec, textColor: String, dark: Boolean): JSONObject {
        val fontSize = spec.labels.fontSize ?: 11
        val endpoint = "scale('y', datum.stackBase + datum.y)"
        val segmentStart = "scale('y', datum.stackBase)"
        val segmentPixels = "abs($segmentStart - $endpoint)"
        val active = "(isValid(datum.y) && datum.y !== 0 && $segmentPixels >= ${fontSize + 12})"
        val inside = spec.labels.position == LabelPosition.INSIDE_END
        val rawOffset = if (inside) {
            "(datum.y >= 0 ? 6 : -6)"
        } else {
            "(datum.y >= 0 ? -6 : 6)"
        }
        val minY = fontSize + 2
        val maxY = "cloudyPlotHeight - ${fontSize + 2}"
        val clamped = "(($endpoint + ($rawOffset) < $minY) ? ($minY - $endpoint) : " +
            "(($endpoint + ($rawOffset) > $maxY) ? ($maxY - $endpoint) : ($rawOffset)))"
        return JSONObject()
            .put("type", "text")
            .put("from", JSONObject().put("data", "table"))
            .put("clip", true)
            .put("interactive", false)
            .put(
                "encode",
                JSONObject().put(
                    "enter",
                    JSONObject()
                        .put("x", barLabelXEncode(spec))
                        .put(
                            "y",
                            JSONObject().put("signal", "($endpoint) + ($clamped)"),
                        )
                        .put("text", valueText(spec, fontSize))
                        .put("align", JSONObject().put("value", "center"))
                        .put(
                            "baseline",
                            JSONObject().put(
                                "signal",
                                if (inside) {
                                    "(datum.y >= 0) ? 'top' : 'bottom'"
                                } else {
                                    "(datum.y >= 0) ? 'bottom' : 'top'"
                                },
                            ),
                        )
                        .put(
                            "fill",
                            JSONObject().put(
                                "value",
                                if (inside) {
                                    if (dark) "#101A1D" else "#FFFFFF"
                                } else {
                                    textColor
                                },
                            ),
                        )
                        .put(
                            "stroke",
                            JSONObject().put(
                                "value",
                                if (inside) {
                                    if (dark) "#FFFFFF" else "#101A1D"
                                } else {
                                    "transparent"
                                },
                            ),
                        )
                        .put(
                            "strokeWidth",
                            JSONObject().put("value", if (inside) 1.25 else 0),
                        )
                        .put("fontSize", JSONObject().put("value", fontSize))
                        .put("defined", JSONObject().put("signal", active)),
                ),
            )
    }

    private fun lineGroup(spec: ChartSpec, textColor: String, visibleExpr: String): JSONObject {
        val childMarks = JSONArray()
        childMarks.put(
            JSONObject()
                .put("type", "line")
                .put("from", JSONObject().put("data", "series"))
                .put("clip", true)
                .put(
                    "encode",
                    JSONObject().put(
                        "enter",
                        JSONObject()
                            .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                            .put("y", JSONObject().put("scale", "y").put("field", "y"))
                            .put("stroke", JSONObject().put("scale", "color").put("field", "series"))
                            .put("strokeWidth", JSONObject().put("value", spec.lineWidth ?: 2.0))
                            .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
                    ),
                )
                .also { applyLineStroke(spec, it, 2.0) },
        )
        if (spec.pointCount <= 200 && spec.markers.show) {
            val markerEnter = JSONObject()
                .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                .put("y", JSONObject().put("scale", "y").put("field", "y"))
                .put("fill", JSONObject().put("scale", "color").put("field", "series"))
                .put("size", JSONObject().put("value", markerArea(spec, 30.0)))
                .put("defined", JSONObject().put("signal", "isValid(datum.y)"))
            spec.markers.opacity?.let {
                markerEnter.put("opacity", JSONObject().put("value", it))
            }
            if (spec.markers.border) {
                markerEnter
                    .put("stroke", JSONObject().put("value", textColor))
                    .put("strokeWidth", JSONObject().put("value", spec.markers.borderWidth ?: 1.0))
            }
            if (spec.markers.shape != MarkerShape.CIRCLE) {
                markerEnter.put("shape", JSONObject().put("value", markerShapeName(spec.markers.shape)))
            }
            childMarks.put(
                JSONObject()
                    .put("type", "symbol")
                    .put("from", JSONObject().put("data", "series"))
                    .put("clip", true)
                    .put("encode", JSONObject().put("enter", markerEnter)),
            )
        }
        if (spec.labels.show) {
            childMarks.put(
                JSONObject()
                    .put("type", "text")
                    .put("from", JSONObject().put("data", "series"))
                    .put("clip", true)
                    .put("interactive", false)
                    .put(
                        "encode",
                        JSONObject().put(
                            "enter",
                            JSONObject()
                                .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                                .put("y", JSONObject().put("scale", "y").put("field", "y").put("offset", -8))
                                .put("text", valueText(spec, spec.labels.fontSize ?: 10))
                                .put("align", JSONObject().put("value", "center"))
                                .put("baseline", JSONObject().put("value", "bottom"))
                                .put("fill", JSONObject().put("value", textColor))
                                .put("fontSize", JSONObject().put("value", spec.labels.fontSize ?: 10))
                                .put(
                                    "defined",
                                    JSONObject().put(
                                        "signal",
                                        labelDefined(spec, spec.labels.fontSize ?: 10),
                                    ),
                                ),
                        ),
                    ),
            )
        }
        return JSONObject()
            .put("type", "group")
            .put(
                "from",
                JSONObject().put(
                    "facet",
                    JSONObject()
                        .put("name", "series")
                        .put("data", "visible")
                        .put("groupby", JSONArray().put("series")),
                ),
            )
            .put("clip", true)
            .put(
                "encode",
                JSONObject().put(
                    "enter",
                    JSONObject()
                        .put("width", JSONObject().put("signal", "width"))
                        .put("height", JSONObject().put("signal", "height")),
                ),
            )
            .put("marks", childMarks)
    }

    private fun areaStackEnd(spec: ChartSpec): String =
        if (spec.areaStackMode == StackMode.NONE) "datum.y" else "datum.stackBase + datum.y"

    private fun areaStackStart(spec: ChartSpec): String =
        if (spec.areaStackMode == StackMode.NONE) "0" else "datum.stackBase"

    private fun areaMark(spec: ChartSpec): JSONObject = JSONObject()
        .put("type", "area")
        .put("from", JSONObject().put("data", "visible"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put(
                        "y",
                        JSONObject().put(
                            "signal",
                            "scale('y', ${areaStackEnd(spec)})",
                        ),
                    )
                    .put(
                        "y2",
                        JSONObject().put(
                            "signal",
                            "scale('y', ${areaStackStart(spec)})",
                        ),
                    )
                    .put("fill", JSONObject().put("scale", "color").put("field", "series"))
                    .put(
                        "opacity",
                        JSONObject().put("value", 0.32 * (spec.lineOpacity ?: 1.0)),
                    )
                    .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
            ),
        )
        .also { mark ->
            if (spec.lineInterpolation != LineInterpolation.LINEAR) {
                mark.getJSONObject("encode").getJSONObject("enter").put(
                    "interpolate",
                    JSONObject().put("value", lineInterpolationName(spec.lineInterpolation)),
                )
            }
        }

    private fun areaLineMark(spec: ChartSpec): JSONObject = JSONObject()
        .put("type", "line")
        .put("from", JSONObject().put("data", "visible"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put(
                        "y",
                        JSONObject().put(
                            "signal",
                            "scale('y', ${areaStackEnd(spec)})",
                        ),
                    )
                    .put("stroke", JSONObject().put("scale", "color").put("field", "series"))
                    .put("strokeWidth", JSONObject().put("value", spec.lineWidth ?: 1.5))
                    .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
            ),
        )
        .also { applyLineStroke(spec, it, 1.5) }

    private fun areaSymbolMark(spec: ChartSpec, textColor: String): JSONObject = JSONObject()
        .put("type", "symbol")
        .put("from", JSONObject().put("data", "visible"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put("y", JSONObject().put("scale", "y").put("field", "displayY"))
                    .put("fill", JSONObject().put("scale", "color").put("field", "series"))
                    .put("size", JSONObject().put("value", markerArea(spec, 24.0)))
                    .put("defined", JSONObject().put("signal", "isValid(datum.y)"))
                    .apply {
                        spec.markers.opacity?.let {
                            put("opacity", JSONObject().put("value", it))
                        }
                        if (spec.markers.border) {
                            put("stroke", JSONObject().put("value", textColor))
                                .put(
                                    "strokeWidth",
                                    JSONObject().put("value", spec.markers.borderWidth ?: 1.0),
                                )
                        }
                        if (spec.markers.shape != MarkerShape.CIRCLE) {
                            put(
                                "shape",
                                JSONObject().put("value", markerShapeName(spec.markers.shape)),
                            )
                        }
                    },
            ),
        )

    private fun areaLabelMark(spec: ChartSpec, textColor: String): JSONObject =
        JSONObject()
        .put("type", "text")
        .put("from", JSONObject().put("data", "visible"))
        .put("clip", true)
        .put("interactive", false)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put("y", JSONObject().put("scale", "y").put("field", "displayY").put("offset", -8))
                    .put("text", valueText(spec, spec.labels.fontSize ?: 10))
                    .put("align", JSONObject().put("value", "center"))
                    .put("baseline", JSONObject().put("value", "bottom"))
                    .put("fill", JSONObject().put("value", textColor))
                    .put("fontSize", JSONObject().put("value", spec.labels.fontSize ?: 10))
                    .put(
                        "defined",
                        JSONObject().put(
                            "signal",
                            labelDefined(spec, spec.labels.fontSize ?: 10),
                        ),
                    ),
            ),
        )

    private fun barSelectionMark(spec: ChartSpec, textColor: String): JSONObject {
        val contrastingStroke = if (textColor == "#E1EBED") "#182427" else "#FFFFFF"
        val enter = JSONObject()
        if (isHorizontalBar(spec)) {
            enter.put("x", barXEncode(spec))
                .put("x2", barX2Encode(spec))
                .put("y", barYEncode(spec))
                .put("height", barHeightEncode(spec))
        } else {
            enter.put("x", barXEncode(spec))
                .put("width", barWidthEncode(spec))
            if (isStackedBar(spec)) {
                enter.put(
                    "y",
                    JSONObject().put(
                        "signal",
                        "scale('y', max(datum.stackBase, datum.stackBase + datum.y))",
                    ),
                ).put(
                    "y2",
                    JSONObject().put(
                        "signal",
                        "scale('y', min(datum.stackBase, datum.stackBase + datum.y))",
                    ),
                )
            } else {
                enter.put("y", JSONObject().put("scale", "y").put("field", "y"))
                    .put("y2", JSONObject().put("scale", "y").put("value", 0))
            }
        }
        enter.put("fill", JSONObject().put("value", textColor))
            .put("fillOpacity", JSONObject().put("value", 0.22))
            .put("stroke", JSONObject().put("value", contrastingStroke))
            .put("strokeWidth", JSONObject().put("value", 3))
        return JSONObject()
            .put("name", "cloudySelectedBar")
            .put("type", "rect")
            .put("interactive", false)
            .put("from", JSONObject().put("data", "selected"))
            .put("clip", true)
            .put("encode", JSONObject().put("enter", enter))
    }

    private fun selectedPointMark(spec: ChartSpec, textColor: String, visibleExpr: String): JSONObject {
        val contrastingStroke = if (textColor == "#E1EBED") "#182427" else "#FFFFFF"
        return JSONObject()
            .put("name", "cloudySelectedPoints")
            .put("type", "symbol")
            .put("interactive", false)
            .put("from", JSONObject().put("data", "selected"))
            .put("clip", true)
            .put(
                "encode",
                JSONObject().put(
                    "enter",
                    JSONObject()
                        .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                        .put(
                            "y",
                            JSONObject().put("scale", "y")
                                .put(
                                    "field",
                                    if (spec.kind == ChartKind.AREA &&
                                        spec.areaStackMode != StackMode.NONE
                                    ) {
                                        "displayY"
                                    } else {
                                        "y"
                                    },
                                ),
                        )
                        .put("fill", JSONObject().put("value", textColor))
                        .put("stroke", JSONObject().put("value", contrastingStroke))
                        .put("strokeWidth", JSONObject().put("value", 2))
                        .put("size", JSONObject().put("value", 240))
                        .put(
                            "defined",
                            JSONObject().put(
                                "signal",
                                "$visibleExpr && isValid(datum.y)",
                            ),
                        ),
                ),
            )
    }

    private fun lineHitMark(spec: ChartSpec, visibleExpr: String): JSONObject = JSONObject()
        .put("name", "cloudyHit")
        .put("type", "symbol")
        .put("from", JSONObject().put("data", "visible"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put(
                        "y",
                        JSONObject().put("scale", "y")
                            .put(
                                "field",
                                if (spec.kind == ChartKind.AREA &&
                                    spec.areaStackMode != StackMode.NONE
                                ) {
                                    "displayY"
                                } else {
                                    "y"
                                },
                            ),
                    )
                    .put("fill", JSONObject().put("value", "#000000"))
                    .put("size", JSONObject().put("value", 180))
                    .put("opacity", JSONObject().put("value", 0.001))
                    .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
            ),
        )

    private fun selectionSignal(): JSONObject = JSONObject()
        .put("name", "cloudySelection")
        .put("value", JSONArray().put(-1).put("").put(""))
        .put(
            "on",
            JSONArray()
                .put(
                    JSONObject()
                        .put("events", "@cloudyBar:click")
                        .put("update", "[datum.sourceRowIndex, datum.series, datum.x, datum.exactY, datum.y]"),
                )
                .put(
                    JSONObject()
                        .put("events", "@cloudyHit:click")
                        .put("update", "[datum.sourceRowIndex, datum.series, datum.x, datum.exactY, datum.y]"),
                ),
        )

    private fun hoverSignal(): JSONObject = JSONObject()
        .put("name", "cloudyHover")
        .put("value", JSONArray().put(-1).put("").put("").put("").put(JSONObject.NULL))
        .put(
            "on",
            JSONArray()
                .put(hoverEvent("@cloudyBar:pointermove"))
                .put(hoverEvent("@cloudyHit:pointermove"))
                .put(
                    JSONObject()
                        .put("events", "@cloudyBar:pointerout")
                        .put("update", "[-1, '', '', '', null]"),
                )
                .put(
                    JSONObject()
                        .put("events", "@cloudyHit:pointerout")
                        .put("update", "[-1, '', '', '', null]"),
                ),
        )

    private fun hoverEvent(selector: String): JSONObject = JSONObject()
        .put("events", selector)
        .put("update", "[datum.sourceRowIndex, datum.series, datum.x, datum.exactY, datum.y]")

    private fun legendJson(spec: ChartSpec, textColor: String, widthDp: Float): JSONObject? {
        if (!spec.legend.visible) return null
        if (spec.kind != ChartKind.LINE &&
            !(spec.kind == ChartKind.BAR && spec.barHasSeries) &&
            spec.kind != ChartKind.AREA
        ) {
            return null
        }
        val orient = when (spec.legend.position) {
            LegendPosition.BOTTOM -> "bottom"
            LegendPosition.RIGHT -> "right"
        }
        val direction = spec.legend.orientation?.name?.lowercase() ?: when (spec.legend.position) {
            LegendPosition.BOTTOM -> "horizontal"
            LegendPosition.RIGHT -> "vertical"
        }
        val legend = JSONObject()
            .put(if (spec.kind == ChartKind.BAR || spec.kind == ChartKind.AREA) "fill" else "stroke", "color")
            .put("title", spec.legend.title ?: "")
            .put("orient", orient)
            .put("labelColor", textColor)
            .put("titleColor", textColor)
            .put("labelFontSize", 11)
            .put("titleFontSize", 11)
        legend.put("direction", direction)
        legend.put("labelLimit", LegendLayout.labelLimitDp(widthDp))
        if (spec.kind == ChartKind.BAR || spec.kind == ChartKind.AREA) {
            legend.put("symbolType", "square")
        } else {
            when (spec.legend.markSample) {
                LegendMarkSample.LINE -> {
                    legend.put("symbolType", "stroke")
                        .put("symbolSize", 200)
                        .put("symbolStrokeWidth", spec.lineWidth ?: 2.0)
                    spec.lineOpacity?.let { legend.put("symbolOpacity", it) }
                    lineDashArray(spec, 2.0)?.let { legend.put("symbolDash", it) }
                }
                LegendMarkSample.MARKER -> {
                    if (spec.markers.shape != MarkerShape.CIRCLE) {
                        legend.put("symbolType", markerShapeName(spec.markers.shape))
                    }
                    spec.markers.opacity?.let { legend.put("symbolOpacity", it) }
                }
            }
        }
        if (direction == "horizontal") {
            legend.put(
                "columns",
                LegendLayout.columns(
                    widthDp = widthDp,
                    position = spec.legend.position,
                    labels = spec.series.map { it.key },
                    seriesCount = spec.series.size,
                ),
            )
        }
        return legend
    }
}
