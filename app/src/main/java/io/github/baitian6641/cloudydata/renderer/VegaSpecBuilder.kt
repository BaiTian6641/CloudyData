package io.github.baitian6641.cloudydata.renderer

import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartPoint
import io.github.baitian6641.cloudydata.chart.ChartSpec
import io.github.baitian6641.cloudydata.chart.LegendLayout
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
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
        val gridColor = if (dark) "#35515A" else "#D3DFE2"
        val rows = JSONArray()
        val xOrder = TreeMap<Int, String>()
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
            ChartKind.BAR -> xDomain
        }
        val colorScale = JSONObject()
            .put("name", "color")
            .put("type", "ordinal")
            .put("domain", colorDomain)
            .put("range", JSONArray(palette.map { "#%06X".format(0xFFFFFF and it) }))

        val hasExplicitRange = spec.yAxis.min != null || spec.yAxis.max != null
        val yScale = JSONObject()
            .put("name", "y")
            .put("type", if (spec.yAxis.log) "log" else "linear")
            .put("range", "height")
            // Explicit bounds are authoritative; Vega's default zero/nice would
            // silently expand them and misreport the configured range.
            .put("nice", !hasExplicitRange)
            .put("reverse", spec.yAxis.reverse)
        val bothBounds = spec.yAxis.min != null && spec.yAxis.max != null
        if (bothBounds) {
            yScale.put("domain", JSONArray().put(spec.yAxis.min).put(spec.yAxis.max))
            yScale.put("zero", false)
        } else {
            yScale.put("domain", JSONObject().put("data", "table").put("field", "y"))
            spec.yAxis.min?.let { yScale.put("domainMin", it) }
            spec.yAxis.max?.let { yScale.put("domainMax", it) }
            if (!spec.yAxis.log) yScale.put("zero", spec.yAxis.zero)
        }
        if (spec.yAxis.log) yScale.put("zero", false)

        val xScale = JSONObject()
            .put("name", "x")
            .put("type", "band")
            .put("domain", xDomain)
            .put("range", "width")
            .put("paddingInner", if (spec.kind == ChartKind.BAR) 0.2 else 0.05)

        val axes = JSONArray()
            .put(
                axisJson(
                    orient = "bottom",
                    scale = "x",
                    title = spec.xAxis.title,
                    textColor = textColor,
                    gridColor = gridColor,
                    grid = false,
                    labelAngle = if (spec.kind != ChartKind.BAR && xDomain.length() > 8) -35 else 0,
                    titleAnchor = spec.xAxis.titleAnchor,
                ),
            )
            .put(
                axisJson(
                    orient = "left",
                    scale = "y",
                    title = spec.yAxis.title,
                    textColor = textColor,
                    gridColor = gridColor,
                    grid = spec.gridlines.show,
                    labelAngle = 0,
                    tickCount = spec.yAxis.tickCount,
                    titleAnchor = spec.yAxis.titleAnchor,
                ),
            )

        val marks = JSONArray()
        when (spec.kind) {
            ChartKind.BAR -> {
                marks.put(barMark())
                if (spec.labels.show) marks.put(barLabelMark(spec, textColor, dark))
                marks.put(barSelectionMark(textColor))
            }
            ChartKind.LINE -> {
                marks.put(lineGroup(spec, textColor))
                // Selected points are drawn beneath the transparent hit target so
                // the hit target keeps receiving clicks.
                marks.put(selectedPointMark(textColor))
                // Facet groups do not reliably surface the child point datum on a
                // click. A top-level, nearly transparent symbol gives every observed
                // point a stable hit target that carries sourceRowIndex.
                marks.put(lineHitMark())
            }
            ChartKind.AREA -> {
                marks.put(areaMark())
                marks.put(areaLineMark(spec))
                if (spec.pointCount <= 200) marks.put(areaSymbolMark())
                if (spec.labels.show) marks.put(areaLabelMark(spec, textColor))
                marks.put(selectedPointMark(textColor))
                marks.put(lineHitMark())
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
                    .put(JSONObject().put("name", "selected").put("values", JSONArray())),
            )
            .put("scales", JSONArray().put(xScale).put(yScale).put(colorScale))
            .put("axes", axes)
            .put(
                "signals",
                JSONArray()
                    .put(JSONObject().put("name", "cloudyPlotHeight").put("value", heightDp))
                    .put(selectionSignal())
                    .put(hoverSignal()),
            )
            .put(
                "usermeta",
                JSONObject()
                    .put("tooltip", spec.tooltip.show)
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

    private fun axisJson(
        orient: String,
        scale: String,
        title: String?,
        textColor: String,
        gridColor: String,
        grid: Boolean,
        labelAngle: Int,
        tickCount: Int? = null,
        titleAnchor: AxisTitleAnchor = AxisTitleAnchor.AUTO,
    ): JSONObject {
        val axis = JSONObject()
            .put("orient", orient)
            .put("scale", scale)
            .put("labelColor", textColor)
            .put("labelFontSize", 12)
            .put("domainColor", gridColor)
            .put("tickColor", gridColor)
            .put("grid", grid)
            .put("gridColor", gridColor)
            .put("labelAngle", labelAngle)
        if (tickCount != null) {
            axis.put("tickCount", tickCount).put("labelOverlap", "greedy")
        }
        if (titleAnchor != AxisTitleAnchor.AUTO) {
            axis.put("titleAnchor", titleAnchor.name.lowercase())
        }
        if (!title.isNullOrBlank()) {
            axis.put("title", title)
                .put("titleColor", textColor)
                .put("titleFontSize", 12)
                .put("titlePadding", 8)
        }
        return axis
    }

    private fun barMark(): JSONObject = JSONObject()
        .put("name", "cloudyBar")
        .put("type", "rect")
        .put("from", JSONObject().put("data", "table"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x"))
                    .put("width", JSONObject().put("scale", "x").put("band", 1))
                    .put("y", JSONObject().put("scale", "y").put("field", "y"))
                    .put("y2", JSONObject().put("scale", "y").put("value", 0))
                    .put("fill", JSONObject().put("scale", "color").put("field", "x")),
            ),
        )

    private fun barLabelMark(spec: ChartSpec, textColor: String, dark: Boolean): JSONObject {
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
                        .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                        .put(
                            "y",
                            JSONObject().put("scale", "y").put("field", "y")
                                .put("offset", JSONObject().put("signal", clamped)),
                        )
                        .put("text", JSONObject().put("field", "y"))
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
                        .put("fontSize", JSONObject().put("value", fontSize)),
                ),
            )
    }

    private fun lineGroup(spec: ChartSpec, textColor: String): JSONObject {
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
                ),
        )
        if (spec.pointCount <= 200) {
            childMarks.put(
                JSONObject()
                    .put("type", "symbol")
                    .put("from", JSONObject().put("data", "series"))
                    .put("clip", true)
                    .put(
                        "encode",
                        JSONObject().put(
                            "enter",
                            JSONObject()
                                .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                                .put("y", JSONObject().put("scale", "y").put("field", "y"))
                                .put("fill", JSONObject().put("scale", "color").put("field", "series"))
                                .put("size", JSONObject().put("value", 30))
                                .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
                        ),
                    ),
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
                                .put("text", JSONObject().put("field", "y"))
                                .put("align", JSONObject().put("value", "center"))
                                .put("baseline", JSONObject().put("value", "bottom"))
                                .put("fill", JSONObject().put("value", textColor))
                                .put("fontSize", JSONObject().put("value", spec.labels.fontSize ?: 10))
                                .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
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
                        .put("data", "table")
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

    private fun areaMark(): JSONObject = JSONObject()
        .put("type", "area")
        .put("from", JSONObject().put("data", "table"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put("y", JSONObject().put("scale", "y").put("field", "y"))
                    .put("y2", JSONObject().put("scale", "y").put("value", 0))
                    .put("fill", JSONObject().put("scale", "color").put("field", "series"))
                    .put("opacity", JSONObject().put("value", 0.32))
                    .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
            ),
        )

    private fun areaLineMark(spec: ChartSpec): JSONObject = JSONObject()
        .put("type", "line")
        .put("from", JSONObject().put("data", "table"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put("y", JSONObject().put("scale", "y").put("field", "y"))
                    .put("stroke", JSONObject().put("scale", "color").put("field", "series"))
                    .put("strokeWidth", JSONObject().put("value", spec.lineWidth ?: 1.5))
                    .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
            ),
        )

    private fun areaSymbolMark(): JSONObject = JSONObject()
        .put("type", "symbol")
        .put("from", JSONObject().put("data", "table"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put("y", JSONObject().put("scale", "y").put("field", "y"))
                    .put("fill", JSONObject().put("scale", "color").put("field", "series"))
                    .put("size", JSONObject().put("value", 24))
                    .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
            ),
        )

    private fun areaLabelMark(spec: ChartSpec, textColor: String): JSONObject =
        JSONObject()
        .put("type", "text")
        .put("from", JSONObject().put("data", "table"))
        .put("clip", true)
        .put("interactive", false)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put("y", JSONObject().put("scale", "y").put("field", "y").put("offset", -8))
                    .put("text", JSONObject().put("field", "y"))
                    .put("align", JSONObject().put("value", "center"))
                    .put("baseline", JSONObject().put("value", "bottom"))
                    .put("fill", JSONObject().put("value", textColor))
                    .put("fontSize", JSONObject().put("value", spec.labels.fontSize ?: 10))
                    .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
            ),
        )

    private fun barSelectionMark(textColor: String): JSONObject {
        val contrastingStroke = if (textColor == "#E1EBED") "#182427" else "#FFFFFF"
        return JSONObject()
            .put("name", "cloudySelectedBar")
            .put("type", "rect")
            .put("interactive", false)
            .put("from", JSONObject().put("data", "selected"))
            .put("clip", true)
            .put(
                "encode",
                JSONObject().put(
                    "enter",
                    JSONObject()
                        .put("x", JSONObject().put("scale", "x").put("field", "x"))
                        .put("width", JSONObject().put("scale", "x").put("band", 1))
                        .put("y", JSONObject().put("scale", "y").put("field", "y"))
                        .put("y2", JSONObject().put("scale", "y").put("value", 0))
                        .put("fill", JSONObject().put("value", textColor))
                        .put("fillOpacity", JSONObject().put("value", 0.22))
                        .put("stroke", JSONObject().put("value", contrastingStroke))
                        .put("strokeWidth", JSONObject().put("value", 3)),
                ),
            )
    }

    private fun selectedPointMark(textColor: String): JSONObject {
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
                        .put("y", JSONObject().put("scale", "y").put("field", "y"))
                        .put("fill", JSONObject().put("value", textColor))
                        .put("stroke", JSONObject().put("value", contrastingStroke))
                        .put("strokeWidth", JSONObject().put("value", 2))
                        .put("size", JSONObject().put("value", 240))
                        .put("defined", JSONObject().put("signal", "isValid(datum.y)")),
                ),
            )
    }

    private fun lineHitMark(): JSONObject = JSONObject()
        .put("name", "cloudyHit")
        .put("type", "symbol")
        .put("from", JSONObject().put("data", "table"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put("y", JSONObject().put("scale", "y").put("field", "y"))
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
        if (!spec.legend.visible || spec.kind != ChartKind.LINE) return null
        val orient = when (spec.legend.position) {
            LegendPosition.BOTTOM -> "bottom"
            LegendPosition.RIGHT -> "right"
        }
        val direction = spec.legend.orientation?.name?.lowercase() ?: when (spec.legend.position) {
            LegendPosition.BOTTOM -> "horizontal"
            LegendPosition.RIGHT -> "vertical"
        }
        val legend = JSONObject()
            .put("stroke", "color")
            .put("title", spec.legend.title ?: "")
            .put("orient", orient)
            .put("labelColor", textColor)
            .put("titleColor", textColor)
            .put("labelFontSize", 11)
            .put("titleFontSize", 11)
        legend.put("direction", direction)
        legend.put("labelLimit", LegendLayout.labelLimitDp(widthDp))
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
