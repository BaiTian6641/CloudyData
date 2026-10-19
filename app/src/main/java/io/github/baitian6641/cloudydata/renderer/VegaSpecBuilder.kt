package io.github.baitian6641.cloudydata.renderer

import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartPoint
import io.github.baitian6641.cloudydata.chart.ChartSpec
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
        val colorDomain = if (spec.kind == ChartKind.LINE) seriesDomain else xDomain
        val colorField = if (spec.kind == ChartKind.LINE) "series" else "x"
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
            .put("paddingInner", if (spec.kind == ChartKind.LINE) 0.05 else 0.2)

        val axes = JSONArray()
            .put(
                axisJson(
                    orient = "bottom",
                    scale = "x",
                    title = spec.xAxis.title,
                    textColor = textColor,
                    gridColor = gridColor,
                    grid = false,
                    labelAngle = if (spec.kind == ChartKind.LINE && xDomain.length() > 8) -35 else 0,
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
                ),
            )

        val marks = JSONArray()
        if (spec.kind == ChartKind.BAR) {
            marks.put(barMark())
            if (spec.labels.show) marks.put(barLabelMark(textColor))
        } else {
            marks.put(lineGroup(spec, textColor))
            // Facet groups do not reliably surface the child point datum on a
            // click. A top-level, nearly transparent symbol gives every observed
            // point a stable hit target that carries sourceRowIndex.
            marks.put(lineHitMark())
        }

        val root = JSONObject()
            .put("\$schema", "https://vega.github.io/schema/vega/v6.json")
            .put("version", spec.version)
            .put("width", widthDp)
            .put("height", heightDp)
            .put("padding", 8)
            .put("data", JSONArray().put(JSONObject().put("name", "table").put("values", rows)))
            .put("scales", JSONArray().put(xScale).put(yScale).put(colorScale))
            .put("axes", axes)
            .put("signals", JSONArray().put(selectionSignal()))
            .put("marks", marks)
        val legend = legendJson(spec, textColor)
        if (legend != null) root.put("legends", JSONArray().put(legend))
        return root
    }

    private fun pointJson(point: ChartPoint): JSONObject = JSONObject()
        .put("x", point.x)
        .put("y", point.y ?: JSONObject.NULL)
        .put("series", point.series)
        .put("sourceRowIndex", point.sourceRowIndex)
        .put("defined", point.defined)

    private fun axisJson(
        orient: String,
        scale: String,
        title: String?,
        textColor: String,
        gridColor: String,
        grid: Boolean,
        labelAngle: Int,
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

    private fun barLabelMark(textColor: String): JSONObject = JSONObject()
        .put("type", "text")
        .put("from", JSONObject().put("data", "table"))
        .put("clip", true)
        .put(
            "encode",
            JSONObject().put(
                "enter",
                JSONObject()
                    .put("x", JSONObject().put("scale", "x").put("field", "x").put("band", 0.5))
                    .put("y", JSONObject().put("scale", "y").put("field", "y").put("offset", -6))
                    .put("text", JSONObject().put("field", "y"))
                    .put("align", JSONObject().put("value", "center"))
                    .put("baseline", JSONObject().put("value", "bottom"))
                    .put("fill", JSONObject().put("value", textColor))
                    .put("fontSize", JSONObject().put("value", 11)),
            ),
        )

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
                            .put("strokeWidth", JSONObject().put("value", 2))
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
                                .put("fontSize", JSONObject().put("value", 10))
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
                        .put("update", "[datum.sourceRowIndex, datum.series, datum.x]"),
                )
                .put(
                    JSONObject()
                        .put("events", "@cloudyHit:click")
                        .put("update", "[datum.sourceRowIndex, datum.series, datum.x]"),
                ),
        )

    private fun legendJson(spec: ChartSpec, textColor: String): JSONObject? {
        if (!spec.legend.visible || spec.kind != ChartKind.LINE) return null
        val orient = when (spec.legend.position) {
            LegendPosition.BOTTOM -> "bottom"
            LegendPosition.RIGHT -> "right"
        }
        return JSONObject()
            .put("stroke", "color")
            .put("title", spec.legend.title ?: "")
            .put("orient", orient)
            .put("labelColor", textColor)
            .put("titleColor", textColor)
            .put("labelFontSize", 11)
            .put("titleFontSize", 11)
    }
}
