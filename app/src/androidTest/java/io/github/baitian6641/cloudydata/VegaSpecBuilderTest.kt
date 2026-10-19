package io.github.baitian6641.cloudydata

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartSpecBuilder
import io.github.baitian6641.cloudydata.engine.EngineColumn
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.LabelCollisionPolicy
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.ValueDisplayFormat
import io.github.baitian6641.cloudydata.model.LegendOrientation
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.LogicalType
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.renderer.VegaSpecBuilder
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VegaSpecBuilderTest {
    private val month = FieldDef("month", "month", "VARCHAR", LogicalType.STRING, FieldRole.DIMENSION)
    private val region = FieldDef("region", "region", "VARCHAR", LogicalType.STRING, FieldRole.DIMENSION)
    private val sales = FieldDef("sales", "sales", "BIGINT", LogicalType.INTEGER, FieldRole.MEASURE)

    @Test
    fun multiSeriesLineUsesSeriesFacetDefinedSignalAndOrderedDomain() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "HUGEINT"),
                ),
                rows = listOf(
                    listOf<Any?>("2026-01-01", "North", 10L),
                    listOf<Any?>("2026-01-01", "South", 1L),
                    listOf<Any?>("2026-02-01", "North", 20L),
                    listOf<Any?>("2026-02-01", "South", null),
                    listOf<Any?>("2026-03-01", "North", 30L),
                    listOf<Any?>("2026-04-01", "South", 4L),
                ),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = region,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build(
            "line", 3, result,
            ChartOptions(
                xAxisTitle = "Month",
                yAxisTitle = "Sales",
                seriesTitle = "Region",
                yMin = 0.0,
                yMax = 40.0,
                showValueLabels = true,
                gridlineWidth = 2.5,
                gridlineContrast = io.github.baitian6641.cloudydata.model.GridlineContrast.STRONG,
                valueDecimals = 2,
                showChartBorder = true,
                transparentExportBackground = true,
                legendVisible = true,
                legendPosition = LegendPosition.RIGHT,
                legendOrientation = LegendOrientation.VERTICAL,
                lineWidth = 3.5,
                units = "USD",
                axisTitleAnchor = AxisTitleAnchor.END,
                chartPadding = 12,
                xLabelCount = 2,
            ),
            "derived",
        )
        val json = VegaSpecBuilder.build(spec, dark = false, palette = listOf(0x006A6A, 0x3559C7), widthDp = 600f, heightDp = 300f)
        assertEquals(1, json.getInt("version"))
        assertEquals(12, json.getInt("padding"))
        assertEquals("end", json.getJSONArray("axes").getJSONObject(1).getString("titleAnchor"))
        assertEquals(
            listOf("2026-01-01", "2026-04-01"),
            (0 until json.getJSONArray("axes").getJSONObject(0).getJSONArray("values").length())
                .map { json.getJSONArray("axes").getJSONObject(0).getJSONArray("values").getString(it) },
        )
        assertEquals("USD", json.getJSONObject("usermeta").getString("units"))
        assertEquals(true, json.getJSONObject("usermeta").getBoolean("chartBorder"))
        assertEquals(
            true,
            json.getJSONObject("usermeta").getBoolean("transparentExportBackground"),
        )
        assertEquals(
            2.5,
            json.getJSONArray("axes").getJSONObject(1).getDouble("gridWidth"),
            0.0,
        )
        assertEquals(",.2f", json.getJSONArray("axes").getJSONObject(1).getString("format"))
        assertEquals("#667A80", json.getJSONArray("axes").getJSONObject(1).getString("gridColor"))
        assertEquals("#D3DFE2", json.getJSONArray("axes").getJSONObject(1).getString("domainColor"))
        assertEquals("#D3DFE2", json.getJSONArray("axes").getJSONObject(1).getString("tickColor"))
        val defaultJson = VegaSpecBuilder.build(
            ChartSpecBuilder.build("line-default", 0, result, ChartOptions(), "derived"),
            false,
            listOf(0x006A6A),
            600f,
            300f,
        )
        assertEquals(
            "#D3DFE2",
            defaultJson.getJSONArray("axes").getJSONObject(1).getString("gridColor"),
        )
        val labelMarks = json.getJSONArray("marks").getJSONObject(0).getJSONArray("marks")
        val valueLabel = (0 until labelMarks.length())
            .map { labelMarks.getJSONObject(it) }
            .first { it.getString("type") == "text" }
        assertEquals(
            "(isValid(datum.y)) ? format(datum.y, ',.2f') : ''",
            valueLabel.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("text").getString("signal"),
        )
        assertEquals(
            "isValid(datum.y)",
            valueLabel.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("defined").getString("signal"),
        )
        val group = json.getJSONArray("marks").getJSONObject(0)
        assertEquals("group", group.getString("type"))
        assertEquals(
            3.5,
            group.getJSONArray("marks").getJSONObject(0)
                .getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("strokeWidth").getDouble("value"),
            0.0,
        )
        val facet = group.getJSONObject("from").getJSONObject("facet")
        assertEquals("visible", facet.getString("data"))
        assertEquals("series", facet.getJSONArray("groupby").getString(0))
        val line = group.getJSONArray("marks").getJSONObject(0)
        assertEquals("line", line.getString("type"))
        assertEquals("series", line.getJSONObject("from").getString("data"))
        assertEquals(
            "isValid(datum.y)",
            line.getJSONObject("encode").getJSONObject("enter").getJSONObject("defined").getString("signal"),
        )
        assertFalse(
            "automatic Y axis must not emit an explicit tick count",
            json.getJSONArray("axes").getJSONObject(1).has("tickCount"),
        )
        assertFalse(
            "automatic Y axis must not emit labelOverlap",
            json.getJSONArray("axes").getJSONObject(1).has("labelOverlap"),
        )
        assertTrue("legend missing for multi-series line", json.has("legends"))
        assertEquals("right", json.getJSONArray("legends").getJSONObject(0).getString("orient"))
        assertEquals("vertical", json.getJSONArray("legends").getJSONObject(0).getString("direction"))
        assertFalse(json.getJSONArray("legends").getJSONObject(0).has("columns"))
        val xDomain = json.getJSONArray("scales").getJSONObject(0).getJSONArray("domain")
        assertEquals(4, xDomain.length())
        assertEquals("2026-01-01", xDomain.getString(0))
        assertEquals("2026-04-01", xDomain.getString(3))
        assertTrue("tooltip visibility missing from the renderer spec", json.getJSONObject("usermeta").getBoolean("tooltip"))
        val dataNames = (0 until json.getJSONArray("data").length())
            .map { json.getJSONArray("data").getJSONObject(it).getString("name") }
        assertTrue("selected-row dataset missing: $dataNames", "selected" in dataNames)
        assertTrue("visible series dataset missing: $dataNames", "visible" in dataNames)
        val visibleData = (0 until json.getJSONArray("data").length())
            .map { json.getJSONArray("data").getJSONObject(it) }
            .first { it.getString("name") == "visible" }
        assertEquals(
            "true",
            visibleData.getJSONArray("transform").getJSONObject(0).getString("expr"),
        )
        val selectedMarks = (0 until json.getJSONArray("marks").length())
            .map { json.getJSONArray("marks").getJSONObject(it) }
        val selectedOverlay = selectedMarks.first { it.optString("name") == "cloudySelectedPoints" }
        assertEquals("selected", selectedOverlay.getJSONObject("from").getString("data"))
        assertFalse("selected overlay must not intercept marks", selectedOverlay.getBoolean("interactive"))
        assertFalse("renderer must not receive analytical aggregation transforms", json.toString().contains("\"aggregate\""))
        val values = json.getJSONArray("data").getJSONObject(0).getJSONArray("values")
        assertEquals(6, values.length())
        val nullPoint = (0 until values.length())
            .map { values.getJSONObject(it) }
            .first { it.isNull("y") }
        assertEquals(false, nullPoint.getBoolean("defined"))
        assertEquals("10", values.getJSONObject(0).getString("exactY"))
        assertEquals(
            "source row indices must survive lowering for exact selection",
            listOf(0, 2, 4, 1, 3, 5),
            (0 until values.length()).map { values.getJSONObject(it).getInt("sourceRowIndex") },
        )
    }

    @Test
    fun percentFormatUsesD3PercentForAxisAndLabels() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "DOUBLE"),
                ),
                rows = listOf(listOf<Any?>("A", 0.125), listOf<Any?>("B", 0.25)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build(
            "percent", 0, result,
            ChartOptions(
                showValueLabels = true,
                valueDecimals = 1,
                valueFormat = ValueDisplayFormat.PERCENT,
            ),
            "derived",
        )
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A), 600f, 300f)
        assertEquals(",.1%", json.getJSONArray("axes").getJSONObject(1).getString("format"))
        val label = json.getJSONArray("marks").getJSONObject(1)
            .getJSONObject("encode").getJSONObject("enter")
        assertTrue(
            label.getJSONObject("text").getString("signal")
                .contains("format(datum.y, ',.1%')"),
        )
        assertEquals(0.125, json.getJSONArray("data").getJSONObject(0)
            .getJSONArray("values").getJSONObject(0).getDouble("y"), 0.0)
    }

    @Test
    fun minorGridlinesEmitASecondUnlabelledValueAxis() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(listOf<Any?>("A", 40L), listOf<Any?>("B", 60L)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build(
            "minor", 0, result,
            ChartOptions(minorGridlines = true, yTickCount = 7, yAxisTitle = "Sales"),
            "derived",
        )
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A), 600f, 300f)
        val axes = json.getJSONArray("axes")
        assertEquals(3, axes.length())
        val minor = axes.getJSONObject(1)
        assertEquals("y", minor.getString("scale"))
        assertEquals(true, minor.getBoolean("grid"))
        assertEquals(0.5, minor.getDouble("gridWidth"), 0.0)
        assertEquals(0.55, minor.getDouble("gridOpacity"), 0.0)
        assertFalse(minor.has("tickCount"))
        val minorSignal = minor.getJSONObject("values").getString("signal")
        assertTrue(minorSignal.contains("sequence(domain('y')[0], domain('y')[1]"))
        assertTrue(minorSignal.contains("/ 20"))
        assertEquals(false, minor.getBoolean("domain"))
        assertEquals(false, minor.getBoolean("ticks"))
        assertEquals(false, minor.getBoolean("labels"))
        assertEquals(false, minor.getBoolean("aria"))
        assertFalse(minor.has("title"))
        val major = axes.getJSONObject(2)
        assertEquals("Sales", major.getString("title"))
        assertEquals(true, major.getBoolean("grid"))
        assertEquals(2, json.getJSONArray("data").getJSONObject(0).getJSONArray("values").length())
    }

    @Test
    fun hiddenSeriesFiltersMarksButKeepsFullLegendDomain() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("A", "North", 10L),
                    listOf<Any?>("A", "South", 20L),
                    listOf<Any?>("B", "North", 30L),
                    listOf<Any?>("B", "South", 40L),
                ),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = region,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build(
            "hidden", 0, result,
            ChartOptions(legendVisible = true, legendPosition = LegendPosition.RIGHT),
            "derived",
        ).copy(hiddenSeries = setOf("South"))
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A, 0x3559C7), 600f, 300f)
        val data = (0 until json.getJSONArray("data").length())
            .map { json.getJSONArray("data").getJSONObject(it) }
        val visible = data.first { it.getString("name") == "visible" }
        assertEquals(
            "datum.series !== 'South'",
            visible.getJSONArray("transform").getJSONObject(0).getString("expr"),
        )
        assertEquals(4, data.first { it.getString("name") == "table" }
            .getJSONArray("values").length())
        assertEquals(
            "South",
            json.getJSONObject("usermeta").getJSONArray("hiddenSeries").getString(0),
        )
        assertEquals(
            "North",
            json.getJSONObject("usermeta").getJSONArray("series").getString(0),
        )
        val legendDomain = json.getJSONArray("scales").getJSONObject(2)
            .getJSONArray("domain")
        assertEquals(2, legendDomain.length())
        assertEquals("visible", json.getJSONArray("marks").getJSONObject(0)
            .getJSONObject("from").getJSONObject("facet").getString("data"))
        val selected = (0 until json.getJSONArray("marks").length())
            .map { json.getJSONArray("marks").getJSONObject(it) }
            .first { it.optString("name") == "cloudySelectedPoints" }
        assertEquals(
            "datum.series !== 'South' && isValid(datum.y)",
            selected.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("defined").getString("signal"),
        )
    }

    @Test
    fun hiddenSeriesEscapesControlCharacters() {
        val spec = ChartSpecBuilder.build(
            "hidden", 0,
            WorksheetResult(
                result = EngineResult(
                    columns = listOf(
                        EngineColumn("row", "VARCHAR"),
                        EngineColumn("value", "BIGINT"),
                    ),
                    rows = listOf(listOf<Any?>("A", 1L)),
                    truncated = false,
                ),
                rowField = region,
                measureField = sales,
                seriesField = null,
                aggregation = Aggregation.SUM,
                markType = MarkType.LINE,
                sql = "test",
            ),
            ChartOptions(),
            "derived",
        ).copy(hiddenSeries = setOf("a\nb"))
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A), 600f, 300f)
        val visible = (0 until json.getJSONArray("data").length())
            .map { json.getJSONArray("data").getJSONObject(it) }
            .first { it.getString("name") == "visible" }
        assertEquals(
            "datum.series !== 'a\\nb'",
            visible.getJSONArray("transform").getJSONObject(0).getString("expr"),
        )
    }

    @Test
    fun crowdedLabelPolicyEmitsBoundedBarAndLineRules() {
        val barResult = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(listOf<Any?>("A", 40L), listOf<Any?>("B", 60L)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        val barSpec = ChartSpecBuilder.build(
            "crowded-bar", 0, barResult,
            ChartOptions(
                showValueLabels = true,
                labelCollisionPolicy = LabelCollisionPolicy.HIDE_CROWDED,
            ),
            "derived",
        )
        val barJson = VegaSpecBuilder.build(barSpec, false, listOf(0x006A6A), 600f, 300f)
        assertEquals(
            "isValid(datum.y) && bandwidth('x') >= 47",
            barJson.getJSONArray("marks").getJSONObject(1)
                .getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("defined").getString("signal"),
        )

        val lineSpec = ChartSpecBuilder.build(
            "crowded-line", 0,
            WorksheetResult(
                result = EngineResult(
                    columns = listOf(
                        EngineColumn("row", "VARCHAR"),
                        EngineColumn("value", "BIGINT"),
                    ),
                    rows = (1..12).map { listOf<Any?>("M$it", it.toLong()) },
                    truncated = false,
                ),
                rowField = region,
                measureField = sales,
                seriesField = null,
                aggregation = Aggregation.SUM,
                markType = MarkType.LINE,
                sql = "test",
            ),
            ChartOptions(
                showValueLabels = true,
                labelCollisionPolicy = LabelCollisionPolicy.HIDE_CROWDED,
            ),
            "derived",
        )
        val lineJson = VegaSpecBuilder.build(lineSpec, false, listOf(0x006A6A), 600f, 300f)
        val lineMarks = lineJson.getJSONArray("marks").getJSONObject(0).getJSONArray("marks")
        val lineLabel = (0 until lineMarks.length())
            .map { lineMarks.getJSONObject(it) }
            .first { it.getString("type") == "text" }
        assertEquals(
            "isValid(datum.y) && bandwidth('x') >= 46",
            lineLabel.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("defined").getString("signal"),
        )
    }

    @Test
    fun bottomLegendAutomaticIsHorizontalAndBoundedByChartWidth() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("2026-01-01", "North", 10L),
                    listOf<Any?>("2026-01-01", "South", 1L),
                    listOf<Any?>("2026-02-01", "North", 20L),
                    listOf<Any?>("2026-02-01", "South", 2L),
                ),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = region,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build(
            "line", 0, result,
            ChartOptions(legendVisible = true, legendPosition = LegendPosition.BOTTOM),
            "derived",
        )
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A), 600f, 300f)
        assertEquals(
            2.0,
            json.getJSONArray("marks").getJSONObject(0).getJSONArray("marks").getJSONObject(0)
                .getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("strokeWidth").getDouble("value"),
            0.0,
        )
        assertFalse(
            "automatic gridline width must not emit an override",
            json.getJSONArray("axes").getJSONObject(1).has("gridWidth"),
        )
        val hiddenGrid = ChartSpecBuilder.build(
            "no-grid", 0, result,
            ChartOptions(showGridlines = false, gridlineWidth = 2.5),
            "derived",
        )
        val hiddenJson = VegaSpecBuilder.build(hiddenGrid, false, listOf(0x006A6A), 600f, 300f)
        assertFalse(
            "hidden gridlines must not emit gridWidth",
            hiddenJson.getJSONArray("axes").getJSONObject(1).has("gridWidth"),
        )
        val legend = json.getJSONArray("legends").getJSONObject(0)
        assertEquals("horizontal", legend.getString("direction"))
        assertEquals(2, legend.getInt("columns"))
        assertEquals(180, legend.getInt("labelLimit"))

        val explicitRight = ChartSpecBuilder.build(
            "line-right", 0, result,
            ChartOptions(
                legendVisible = true,
                legendPosition = LegendPosition.RIGHT,
                legendOrientation = LegendOrientation.HORIZONTAL,
            ),
            "derived",
        )
        val rightJson = VegaSpecBuilder.build(explicitRight, false, listOf(0x006A6A), 600f, 300f)
        val rightLegend = rightJson.getJSONArray("legends").getJSONObject(0)
        assertEquals("horizontal", rightLegend.getString("direction"))
        assertEquals(1, rightLegend.getInt("columns"))
    }

    @Test
    fun areaSpecEmitsAreaLineAndSelectionHitWithoutLegend() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = listOf(
                    listOf<Any?>("2026-01-01", 10L),
                    listOf<Any?>("2026-02-01", 20L),
                    listOf<Any?>("2026-03-01", 30L),
                ),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.AREA,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build("area", 2, result, ChartOptions(legendVisible = true), "derived")
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A), 640f, 400f)
        assertEquals(ChartKind.AREA, spec.kind)
        assertFalse("area has no color-series legend", json.has("legends"))
        val marks = json.getJSONArray("marks")
        assertEquals("area", marks.getJSONObject(0).getString("type"))
        assertEquals("line", marks.getJSONObject(1).getString("type"))
        assertEquals(
            1.5,
            marks.getJSONObject(1).getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("strokeWidth").getDouble("value"),
            0.0,
        )
        assertEquals("cloudyHit", marks.getJSONObject(marks.length() - 1).getString("name"))
        val yScale = json.getJSONArray("scales").getJSONObject(1)
        assertEquals(true, yScale.getBoolean("zero"))
    }

    @Test
    fun explicitYRangeIsAuthoritativeAndNotNicedOrZeroExpanded() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("Jan", "North", 13L),
                    listOf<Any?>("Feb", "North", 37L),
                ),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = region,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build(
            "line", 0, result,
            ChartOptions(yMin = 13.0, yMax = 37.0, zeroBaseline = false),
            "derived",
        )
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A), 400f, 300f)
        val y = json.getJSONArray("scales").getJSONObject(1)
        assertEquals(13.0, y.getJSONArray("domain").getDouble(0), 0.0)
        assertEquals(37.0, y.getJSONArray("domain").getDouble(1), 0.0)
        assertEquals(false, y.getBoolean("zero"))
        assertEquals(false, y.getBoolean("nice"))
    }

    @Test
    fun singleCategoryBarHasNoCategoryLegend() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = listOf(listOf<Any?>("A", 10L)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build(
            "bar", 0, result,
            ChartOptions(
                legendVisible = true,
                yTickCount = 6,
                showValueLabels = true,
                labelFontSize = 14,
                labelPosition = LabelPosition.INSIDE_END,
            ),
            "derived",
        )
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A), 400f, 300f)
        assertEquals(6, json.getJSONArray("axes").getJSONObject(1).getInt("tickCount"))
        assertEquals("greedy", json.getJSONArray("axes").getJSONObject(1).getString("labelOverlap"))
        assertFalse(json.has("legends"))
        val bar = json.getJSONArray("marks").getJSONObject(0)
        assertEquals("rect", bar.getString("type"))
        assertTrue(bar.has("clip"))
        val label = json.getJSONArray("marks").getJSONObject(1)
        assertFalse("labels must not intercept bar hit testing", label.getBoolean("interactive"))
        val labelEncode = label.getJSONObject("encode").getJSONObject("enter")
        assertEquals(14, labelEncode.getJSONObject("fontSize").getInt("value"))
        assertTrue(labelEncode.getJSONObject("baseline").getString("signal").contains("cloudyPlotHeight"))
        assertTrue(
            labelEncode.getJSONObject("y").getJSONObject("offset")
                .getString("signal").contains("datum.y !== 0"),
        )
        assertTrue(labelEncode.getJSONObject("fill").getString("signal").contains("#FFFFFF"))
        val selectedBar = (0 until json.getJSONArray("marks").length())
            .map { json.getJSONArray("marks").getJSONObject(it) }
            .firstOrNull { it.optString("name") == "cloudySelectedBar" }
        assertNotNull("selected bar overlay missing", selectedBar)
        assertFalse("selected bar overlay must not intercept marks", selectedBar!!.getBoolean("interactive"))
    }
}
