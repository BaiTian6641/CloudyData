package io.github.baitian6641.cloudydata

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartSpecBuilder
import io.github.baitian6641.cloudydata.engine.EngineColumn
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
import io.github.baitian6641.cloudydata.model.BarOrientation
import io.github.baitian6641.cloudydata.model.StackMode
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
import io.github.baitian6641.cloudydata.model.LegendMarkSample
import io.github.baitian6641.cloudydata.model.CurrencyCode
import io.github.baitian6641.cloudydata.model.LineDashStyle
import io.github.baitian6641.cloudydata.model.LineInterpolation
import io.github.baitian6641.cloudydata.model.MarkerShape
import io.github.baitian6641.cloudydata.renderer.VegaSpecBuilder
import org.json.JSONArray
import org.json.JSONObject
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
        assertEquals(
            30.0,
            group.getJSONArray("marks").getJSONObject(1)
                .getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("size").getDouble("value"),
            0.0,
        )
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
    fun legendMarkSampleIsLowered() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("A", "North", 1L),
                    listOf<Any?>("A", "South", 2L),
                    listOf<Any?>("B", "North", 3L),
                    listOf<Any?>("B", "South", 4L),
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
        fun legend(options: ChartOptions): JSONObject {
            val json = VegaSpecBuilder.build(
                ChartSpecBuilder.build("legend", 0, result, options, "derived"),
                false,
                listOf(0x006A6A),
                600f,
                300f,
            )
            return json.getJSONArray("legends").getJSONObject(0)
        }
        val defaultLegend = legend(ChartOptions())
        assertFalse("default marker legend must omit symbolType", defaultLegend.has("symbolType"))
        assertFalse("default legend must omit symbolOpacity", defaultLegend.has("symbolOpacity"))
        assertEquals(
            0.5,
            legend(ChartOptions(markerOpacity = 0.5)).getDouble("symbolOpacity"),
            0.0,
        )
        val line = legend(
            ChartOptions(
                legendMarkSample = LegendMarkSample.LINE,
                lineDashStyle = LineDashStyle.DASHED,
            ),
        )
        assertEquals("stroke", line.getString("symbolType"))
        assertEquals(200.0, line.getDouble("symbolSize"), 0.0)
        assertEquals(2.0, line.getDouble("symbolStrokeWidth"), 0.0)
        assertEquals(6.0, line.getJSONArray("symbolDash").getDouble(0), 0.0)
        val dottedWide = legend(
            ChartOptions(
                legendMarkSample = LegendMarkSample.LINE,
                lineDashStyle = LineDashStyle.DOTTED,
                lineWidth = 8.0,
            ),
        )
        assertEquals(8.0, dottedWide.getDouble("symbolStrokeWidth"), 0.0)
        assertEquals(1.0, dottedWide.getJSONArray("symbolDash").getDouble(0), 0.0)
        assertEquals(11.0, dottedWide.getJSONArray("symbolDash").getDouble(1), 0.0)
        val translucentLine = legend(
            ChartOptions(
                legendMarkSample = LegendMarkSample.LINE,
                lineOpacity = 0.4,
            ),
        )
        assertEquals(0.4, translucentLine.getDouble("symbolOpacity"), 0.0)
        val diamond = legend(ChartOptions(markerShape = MarkerShape.DIAMOND))
        assertEquals("diamond", diamond.getString("symbolType"))
    }

    @Test
    fun lineOpacityIsLoweredOnLineAndAreaPaint() {
        val lineResult = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(listOf<Any?>("A", 1L), listOf<Any?>("B", 2L)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        fun lineEnter(options: ChartOptions): JSONObject {
            val json = VegaSpecBuilder.build(
                ChartSpecBuilder.build("opacity", 0, lineResult, options, "derived"),
                false,
                listOf(0x006A6A),
                600f,
                300f,
            )
            return json.getJSONArray("marks").getJSONObject(0)
                .getJSONArray("marks").getJSONObject(0)
                .getJSONObject("encode").getJSONObject("enter")
        }
        assertFalse("default line must omit opacity", lineEnter(ChartOptions()).has("opacity"))
        assertEquals(
            0.5,
            lineEnter(ChartOptions(lineOpacity = 0.5)).getJSONObject("opacity").getDouble("value"),
            0.0,
        )
        fun areaFillOpacity(options: ChartOptions): Double {
            val json = VegaSpecBuilder.build(
                ChartSpecBuilder.build(
                    "area-opacity", 0, lineResult.copy(markType = MarkType.AREA),
                    options,
                    "derived",
                ),
                false,
                listOf(0x006A6A),
                600f,
                300f,
            )
            val fill = (0 until json.getJSONArray("marks").length())
                .map { json.getJSONArray("marks").getJSONObject(it) }
                .first { it.getString("type") == "area" }
            return fill.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("opacity").getDouble("value")
        }
        assertEquals(0.32, areaFillOpacity(ChartOptions()), 0.0001)
        assertEquals(0.16, areaFillOpacity(ChartOptions(lineOpacity = 0.5)), 0.0001)
        val areaJson = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "area-opacity", 0, lineResult.copy(markType = MarkType.AREA),
                ChartOptions(lineOpacity = 0.5),
                "derived",
            ),
            false,
            listOf(0x006A6A),
            600f,
            300f,
        )
        val areaMarks = (0 until areaJson.getJSONArray("marks").length())
            .map { areaJson.getJSONArray("marks").getJSONObject(it) }
        val fill = areaMarks.first { it.getString("type") == "area" }
        assertEquals(
            0.16,
            fill.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("opacity").getDouble("value"),
            0.0001,
        )
        val outline = areaMarks.first { it.getString("type") == "line" }
        assertEquals(
            0.5,
            outline.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("opacity").getDouble("value"),
            0.0,
        )
    }

    @Test
    fun currencyFormattingIsLoweredForAxisAndLabels() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "DOUBLE"),
                ),
                rows = listOf(listOf<Any?>("A", -1234.5), listOf<Any?>("B", 2000.0)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        fun json(format: ValueDisplayFormat, code: CurrencyCode): JSONObject =
            VegaSpecBuilder.build(
                ChartSpecBuilder.build(
                    "currency", 0, result,
                    ChartOptions(
                        showValueLabels = true,
                        valueFormat = format,
                        currencyCode = code,
                    ),
                    "derived",
                ),
                false,
                listOf(0x006A6A),
                600f,
                300f,
            )
        val usd = json(ValueDisplayFormat.CURRENCY, CurrencyCode.USD)
        assertEquals("$,.2f", usd.getJSONArray("axes").getJSONObject(1).getString("format"))
        val usdLabel = usd.getJSONArray("marks").getJSONObject(1)
            .getJSONObject("encode").getJSONObject("enter")
            .getJSONObject("text").getString("signal")
        assertTrue(usdLabel.contains("format(datum.y, '$,.2f')"))

        val eur = json(ValueDisplayFormat.CURRENCY, CurrencyCode.EUR)
        val eurAxis = eur.getJSONArray("axes").getJSONObject(1)
        assertEquals(",.2f", eurAxis.getString("format"))
        val axisSignal = eurAxis.getJSONObject("encode").getJSONObject("labels")
            .getJSONObject("update").getJSONObject("text").getString("signal")
        assertTrue(axisSignal.contains("€"))
        assertTrue(axisSignal.contains("abs(datum.value)"))
        val eurLabel = eur.getJSONArray("marks").getJSONObject(1)
            .getJSONObject("encode").getJSONObject("enter")
            .getJSONObject("text").getString("signal")
        assertTrue(eurLabel.contains("€"))
        assertTrue(eurLabel.contains("abs(datum.y)"))
        assertTrue(eurLabel.contains("\u2212€"))
        val crowdedCurrency = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "currency-crowded", 0, result,
                ChartOptions(
                    showValueLabels = true,
                    valueFormat = ValueDisplayFormat.CURRENCY,
                    labelCollisionPolicy = LabelCollisionPolicy.HIDE_CROWDED,
                ),
                "derived",
            ),
            false,
            listOf(0x006A6A),
            600f,
            300f,
        )
        val crowdedLabel = crowdedCurrency.getJSONArray("marks").getJSONObject(1)
            .getJSONObject("encode").getJSONObject("enter")
            .getJSONObject("defined").getString("signal")
        assertTrue(
            "currency crowded threshold missing: $crowdedLabel",
            crowdedLabel.contains("bandwidth('x') >= 83"),
        )
    }

    @Test
    fun lineDashStyleIsLoweredOnLineAndAreaOutlines() {
        val lineResult = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(listOf<Any?>("A", 1L), listOf<Any?>("B", 2L)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        fun lineEnter(options: ChartOptions): JSONObject {
            val json = VegaSpecBuilder.build(
                ChartSpecBuilder.build("dash", 0, lineResult, options, "derived"),
                false,
                listOf(0x006A6A),
                600f,
                300f,
            )
            return json.getJSONArray("marks").getJSONObject(0)
                .getJSONArray("marks").getJSONObject(0)
                .getJSONObject("encode").getJSONObject("enter")
        }
        assertFalse("default solid must not emit strokeDash", lineEnter(ChartOptions()).has("strokeDash"))
        val dashed = lineEnter(ChartOptions(lineDashStyle = LineDashStyle.DASHED))
            .getJSONObject("strokeDash").getJSONArray("value")
        assertEquals(6.0, dashed.getDouble(0), 0.0)
        assertEquals(4.0, dashed.getDouble(1), 0.0)
        val dotted = lineEnter(
            ChartOptions(lineDashStyle = LineDashStyle.DOTTED, lineWidth = 8.0),
        )
        val dottedValues = dotted.getJSONObject("strokeDash").getJSONArray("value")
        assertEquals(1.0, dottedValues.getDouble(0), 0.0)
        assertEquals(11.0, dottedValues.getDouble(1), 0.0)
        assertEquals("round", dotted.getJSONObject("strokeCap").getString("value"))

        val areaJson = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "area-dash", 0, lineResult.copy(markType = MarkType.AREA),
                ChartOptions(lineDashStyle = LineDashStyle.DASHED),
                "derived",
            ),
            false,
            listOf(0x006A6A),
            600f,
            300f,
        )
        val outline = (0 until areaJson.getJSONArray("marks").length())
            .map { areaJson.getJSONArray("marks").getJSONObject(it) }
            .first { it.getString("type") == "line" }
        assertEquals(
            6.0,
            outline.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("strokeDash").getJSONArray("value").getDouble(0),
            0.0,
        )
    }

    @Test
    fun lineInterpolationIsLoweredOnlyWhenNonLinear() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(listOf<Any?>("A", 1L), listOf<Any?>("B", 2L)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        fun lineMark(options: ChartOptions): JSONObject {
            val json = VegaSpecBuilder.build(
                ChartSpecBuilder.build("line", 0, result, options, "derived"),
                false,
                listOf(0x006A6A),
                600f,
                300f,
            )
            return json.getJSONArray("marks").getJSONObject(0)
                .getJSONArray("marks").getJSONObject(0)
        }
        assertFalse(
            "default linear line must not emit interpolate",
            lineMark(ChartOptions()).getJSONObject("encode").getJSONObject("enter")
                .has("interpolate"),
        )
        assertEquals(
            "step",
            lineMark(ChartOptions(lineInterpolation = LineInterpolation.STEP))
                .getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("interpolate").getString("value"),
        )
    }

    @Test
    fun markerVisibilityAndSizeAreLowered() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("A", "North", 1L),
                    listOf<Any?>("A", "South", 2L),
                    listOf<Any?>("B", "North", 3L),
                    listOf<Any?>("B", "South", 4L),
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
        fun childMarks(options: ChartOptions): List<JSONObject> {
            val json = VegaSpecBuilder.build(
                ChartSpecBuilder.build("markers", 0, result, options, "derived"),
                false,
                listOf(0x006A6A),
                600f,
                300f,
            )
            val marks = json.getJSONArray("marks").getJSONObject(0).getJSONArray("marks")
            return (0 until marks.length()).map { marks.getJSONObject(it) }
        }
        assertTrue(
            "default line markers missing",
            childMarks(ChartOptions()).any { it.getString("type") == "symbol" },
        )
        assertTrue(
            "markers were not removed",
            childMarks(ChartOptions(showMarkers = false)).none { it.getString("type") == "symbol" },
        )
        val sized = childMarks(ChartOptions(markerSize = 6.0))
            .first { it.getString("type") == "symbol" }
        assertEquals(
            36.0,
            sized.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("size").getDouble("value"),
            0.0,
        )
        val translucent = childMarks(ChartOptions(markerOpacity = 0.5))
            .first { it.getString("type") == "symbol" }
        assertEquals(
            0.5,
            translucent.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("opacity").getDouble("value"),
            0.0,
        )
        assertFalse(
            "default markers must omit stroke",
            childMarks(ChartOptions()).first { it.getString("type") == "symbol" }
                .getJSONObject("encode").getJSONObject("enter").has("stroke"),
        )
        val bordered = childMarks(ChartOptions(markerBorder = true, markerBorderWidth = 2.0))
            .first { it.getString("type") == "symbol" }
            .getJSONObject("encode").getJSONObject("enter")
        assertEquals("#182427", bordered.getJSONObject("stroke").getString("value"))
        assertEquals(2.0, bordered.getJSONObject("strokeWidth").getDouble("value"), 0.0)
        val defaultSymbol = childMarks(ChartOptions()).first { it.getString("type") == "symbol" }
        assertFalse(
            "default circle must not emit a shape override",
            defaultSymbol.getJSONObject("encode").getJSONObject("enter").has("shape"),
        )
        val diamond = childMarks(ChartOptions(markerShape = MarkerShape.DIAMOND))
            .first { it.getString("type") == "symbol" }
        assertEquals(
            "diamond",
            diamond.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("shape").getString("value"),
        )
        val defaultJson = VegaSpecBuilder.build(
            ChartSpecBuilder.build("markers", 0, result, ChartOptions(), "derived"),
            false,
            listOf(0x006A6A),
            600f,
            300f,
        )
        assertFalse(
            "default circle legend must not emit a symbolType override",
            defaultJson.getJSONArray("legends").getJSONObject(0).has("symbolType"),
        )
        val diamondJson = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "markers", 0, result,
                ChartOptions(markerShape = MarkerShape.DIAMOND),
                "derived",
            ),
            false,
            listOf(0x006A6A),
            600f,
            300f,
        )
        assertEquals("diamond", diamondJson.getJSONArray("legends").getJSONObject(0).getString("symbolType"))
    }

    @Test
    fun areaInterpolationIsLoweredOnAreaAndOutline() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("A", 1L),
                    listOf<Any?>("B", 2L),
                    listOf<Any?>("C", 3L),
                ),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.AREA,
            sql = "test",
        )
        val json = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "area-interp", 0, result,
                ChartOptions(lineInterpolation = LineInterpolation.MONOTONE),
                "derived",
            ),
            false,
            listOf(0x006A6A),
            600f,
            300f,
        )
        val marks = (0 until json.getJSONArray("marks").length())
            .map { json.getJSONArray("marks").getJSONObject(it) }
        val area = marks.first { it.getString("type") == "area" }
        assertEquals(
            "monotone",
            area.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("interpolate").getString("value"),
        )
        val outline = marks.first { it.getString("type") == "line" }
        assertEquals(
            "monotone",
            outline.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("interpolate").getString("value"),
        )
    }

    @Test
    fun areaMarkerShapeIsLowered() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(listOf<Any?>("A", 1L), listOf<Any?>("B", 2L)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.AREA,
            sql = "test",
        )
        val json = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "area-shape", 0, result,
                ChartOptions(
                    markerShape = MarkerShape.CROSS,
                    markerOpacity = 0.5,
                    markerBorder = true,
                    markerBorderWidth = 1.5,
                ),
                "derived",
            ),
            false,
            listOf(0x006A6A),
            600f,
            300f,
        )
        val marks = json.getJSONArray("marks")
        val areaSymbol = (0 until marks.length())
            .map { marks.getJSONObject(it) }
            .first { it.getString("type") == "symbol" && !it.has("name") }
        assertEquals(
            "cross",
            areaSymbol.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("shape").getString("value"),
        )
        assertEquals(
            0.5,
            areaSymbol.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("opacity").getDouble("value"),
            0.0,
        )
        assertEquals(
            1.5,
            areaSymbol.getJSONObject("encode").getJSONObject("enter")
                .getJSONObject("strokeWidth").getDouble("value"),
            0.0,
        )
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
            ChartOptions(
                legendVisible = true,
                legendPosition = LegendPosition.RIGHT,
                hiddenSeries = setOf("South", "Ghost"),
            ),
            "derived",
        )
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
                        EngineColumn("series", "VARCHAR"),
                        EngineColumn("value", "BIGINT"),
                    ),
                    rows = listOf(
                        listOf<Any?>("A", "a\nb", 1L),
                        listOf<Any?>("A", "Other", 2L),
                    ),
                    truncated = false,
                ),
                rowField = month,
                measureField = sales,
                seriesField = region,
                aggregation = Aggregation.SUM,
                markType = MarkType.LINE,
                sql = "test",
            ),
            ChartOptions(hiddenSeries = setOf("a\nb")),
            "derived",
        )
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
    @Test
    fun groupedAndStackedBarSeriesUseSeriesColorDomainAndStackGeometry() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("Mar", "North", 30L),
                    listOf<Any?>("Feb", "North", 20L),
                    listOf<Any?>("Feb", "South", 2L),
                    listOf<Any?>("Jan", "North", 10L),
                    listOf<Any?>("Jan", "South", 1L),
                    listOf<Any?>("Apr", "South", 4L),
                ),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = region,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        val groupedSpec = ChartSpecBuilder.build(
            "bar-grouped", 0, result,
            ChartOptions(seriesTitle = "Region", showValueLabels = true),
            "derived",
        )
        val grouped = VegaSpecBuilder.build(
            groupedSpec, false, listOf(0x006A6A, 0x3559C7), 600f, 320f,
        )
        assertEquals("NONE", grouped.getJSONObject("usermeta").getString("barStackMode"))
        assertFalse(grouped.getJSONObject("usermeta").getBoolean("legendInteractive"))
        assertEquals(
            listOf("North", "South"),
            (0 until grouped.getJSONArray("scales").getJSONObject(2).getJSONArray("domain").length())
                .map { grouped.getJSONArray("scales").getJSONObject(2).getJSONArray("domain").getString(it) },
        )
        assertEquals("color", grouped.getJSONArray("legends").getJSONObject(0).getString("fill"))
        assertEquals("square", grouped.getJSONArray("legends").getJSONObject(0).getString("symbolType"))
        val groupedBar = grouped.getJSONArray("marks").getJSONObject(0)
            .getJSONObject("encode").getJSONObject("enter")
        assertTrue(groupedBar.getJSONObject("x").has("signal"))
        assertTrue(groupedBar.getJSONObject("width").getString("signal").contains("0.9"))

        val stackedSpec = ChartSpecBuilder.build(
            "bar-stacked", 0, result,
            ChartOptions(barStackMode = StackMode.STACKED),
            "derived",
        )
        val stacked = VegaSpecBuilder.build(
            stackedSpec, false, listOf(0x006A6A, 0x3559C7), 600f, 320f,
        )
        assertEquals("STACKED", stacked.getJSONObject("usermeta").getString("barStackMode"))
        assertEquals(
            "displayY",
            stacked.getJSONArray("scales").getJSONObject(1)
                .getJSONObject("domain").getString("field"),
        )
        val stackedBar = stacked.getJSONArray("marks").getJSONObject(0)
            .getJSONObject("encode").getJSONObject("enter")
        assertTrue(stackedBar.getJSONObject("y").getString("signal").contains("datum.stackBase"))
        assertTrue(stackedBar.getJSONObject("y2").getString("signal").contains("datum.stackBase"))
        assertTrue(stackedBar.getJSONObject("x").getString("signal").contains("bandwidth"))
        val stackedRows = stacked.getJSONArray("data").getJSONObject(0).getJSONArray("values")
        assertEquals(6, stackedRows.length())
        val southFebruary = (0 until stackedRows.length())
            .map { stackedRows.getJSONObject(it) }
            .single { it.getString("series") == "South" && it.getString("x") == "Feb" }
        assertEquals(20.0, southFebruary.getDouble("stackBase"), 0.0)
        assertEquals(22.0, southFebruary.getDouble("displayY"), 0.0)

        val percentSpec = ChartSpecBuilder.build(
            "bar-percent", 0, result,
            ChartOptions(barStackMode = StackMode.PERCENT_STACKED),
            "derived",
        )
        val percent = VegaSpecBuilder.build(
            percentSpec, false, listOf(0x006A6A, 0x3559C7), 600f, 320f,
        )
        assertEquals(
            "PERCENT_STACKED",
            percent.getJSONObject("usermeta").getString("barStackMode"),
        )
        assertEquals(",.1%", percent.getJSONArray("axes").getJSONObject(1).getString("format"))
        assertEquals(1.0, percent.getJSONArray("scales").getJSONObject(1)
            .getJSONArray("domain").getDouble(1), 0.0)
        val percentBar = percent.getJSONArray("marks").getJSONObject(0)
            .getJSONObject("encode").getJSONObject("enter")
        assertTrue(
            "100% bars must use the stacked x offset",
            percentBar.getJSONObject("x").getString("signal").contains("0.05"),
        )
        assertTrue(
            "100% bars must use the stacked width",
            percentBar.getJSONObject("width").getString("signal").contains("0.9"),
        )

        val horizontalSpec = ChartSpecBuilder.build(
            "bar-horizontal", 0, result,
            ChartOptions(
                barOrientation = BarOrientation.HORIZONTAL,
                barStackMode = StackMode.STACKED,
                xAxisTitle = "Category",
                yAxisTitle = "Value",
                showValueLabels = true,
            ),
            "derived",
        )
        val horizontal = VegaSpecBuilder.build(
            horizontalSpec, false, listOf(0x006A6A, 0x3559C7), 600f, 320f,
        )
        assertEquals(
            "HORIZONTAL",
            horizontal.getJSONObject("usermeta").getString("barOrientation"),
        )
        assertEquals("x", horizontal.getJSONArray("scales").getJSONObject(0).getString("name"))
        assertEquals("linear", horizontal.getJSONArray("scales").getJSONObject(0).getString("type"))
        assertEquals("y", horizontal.getJSONArray("scales").getJSONObject(1).getString("name"))
        assertEquals("band", horizontal.getJSONArray("scales").getJSONObject(1).getString("type"))
        assertEquals("left", horizontal.getJSONArray("axes").getJSONObject(0).getString("orient"))
        assertEquals("bottom", horizontal.getJSONArray("axes").getJSONObject(1).getString("orient"))
        val horizontalBar = horizontal.getJSONArray("marks").getJSONObject(0)
            .getJSONObject("encode").getJSONObject("enter")
        assertTrue(horizontalBar.getJSONObject("x").getString("signal").contains("scale('x'"))
        assertTrue(horizontalBar.getJSONObject("x2").getString("signal").contains("scale('x'"))
        assertTrue(horizontalBar.getJSONObject("y").getString("signal").contains("scale('y'"))
        assertTrue(horizontalBar.getJSONObject("height").has("signal"))
        val horizontalLabel = horizontal.getJSONArray("marks").getJSONObject(1)
            .getJSONObject("encode").getJSONObject("enter")
        assertTrue(
            "horizontal label x must follow the value endpoint",
            horizontalLabel.getJSONObject("x").getString("signal").contains("scale('x'"),
        )
        assertTrue(
            "horizontal label y must follow the category band",
            horizontalLabel.getJSONObject("y").has("field") ||
                horizontalLabel.getJSONObject("y").getString("signal").contains("scale('y'"),
        )
        val labelAlign = horizontalLabel.getJSONObject("align").getString("signal")
        assertTrue(
            "horizontal label direction must follow screen geometry: $labelAlign",
            labelAlign.contains("scale('x'") && !labelAlign.contains("datum.y >= 0"),
        )
        assertTrue(
            "horizontal label clamp must reserve text width",
            horizontalLabel.getJSONObject("x").getString("signal").contains("cloudyPlotWidth"),
        )
    }



    @Test
    fun stackedAreaUsesBasesAndPercentAxis() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("Jan", "North", 10L),
                    listOf<Any?>("Jan", "South", 1L),
                    listOf<Any?>("Feb", "North", 20L),
                    listOf<Any?>("Feb", "South", 2L),
                    listOf<Any?>("Mar", "North", 30L),
                    listOf<Any?>("Apr", "South", 4L),
                ),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = region,
            aggregation = Aggregation.SUM,
            markType = MarkType.AREA,
            sql = "test",
        )
        val stackedSpec = ChartSpecBuilder.build(
            "area-stacked", 0, result,
            ChartOptions(
                areaStackMode = StackMode.STACKED,
                showValueLabels = true,
                legendVisible = true,
            ),
            "derived",
        )
        assertEquals(StackMode.STACKED, stackedSpec.areaStackMode)
        assertTrue(stackedSpec.legend.visible)
        val stacked = VegaSpecBuilder.build(
            stackedSpec, false, listOf(0x006A6A, 0x3559C7), 600f, 320f,
        )
        assertEquals(
            "displayY",
            stacked.getJSONArray("scales").getJSONObject(1)
                .getJSONObject("domain").getString("field"),
        )
        assertEquals("color", stacked.getJSONArray("legends").getJSONObject(0).getString("fill"))
        assertEquals("square", stacked.getJSONArray("legends").getJSONObject(0).getString("symbolType"))
        val areaMark = stacked.getJSONArray("marks").getJSONObject(0)
            .getJSONObject("encode").getJSONObject("enter")
        assertTrue(areaMark.getJSONObject("y").getString("signal").contains("datum.stackBase + datum.y"))
        assertTrue(areaMark.getJSONObject("y2").getString("signal").contains("datum.stackBase"))
        val areaLabel = stacked.getJSONArray("marks").getJSONObject(3)
            .getJSONObject("encode").getJSONObject("enter")
        assertEquals("displayY", areaLabel.getJSONObject("y").getString("field"))

        val percentSpec = ChartSpecBuilder.build(
            "area-percent", 0, result,
            ChartOptions(areaStackMode = StackMode.PERCENT_STACKED),
            "derived",
        )
        val percent = VegaSpecBuilder.build(
            percentSpec, false, listOf(0x006A6A, 0x3559C7), 600f, 320f,
        )
        assertEquals("PERCENT_STACKED", percent.getJSONObject("usermeta").getString("areaStackMode"))
        assertEquals(",.1%", percent.getJSONArray("axes").getJSONObject(1).getString("format"))
        assertEquals(1.0, percent.getJSONArray("scales").getJSONObject(1)
            .getJSONArray("domain").getDouble(1), 0.0)
    }


    @Test
    fun dualAxisSecondaryLineUsesSeparateScaleAxisAndData() {
        val secondary = FieldDef("cost", "cost", "BIGINT", LogicalType.INTEGER, FieldRole.MEASURE)
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("value", "BIGINT"),
                    EngineColumn("secondary_value", "BIGINT"),
                ),
                rows = listOf(
                    listOf<Any?>("Jan", 10L, 7L),
                    listOf<Any?>("Feb", 20L, 5L),
                    listOf<Any?>("Mar", 30L, 9L),
                ),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
            secondaryMeasureField = secondary,
            secondaryAggregation = Aggregation.SUM,
        )
        val spec = ChartSpecBuilder.build(
            "dual", 0, result,
            ChartOptions(
                secondaryAxisTitle = "Cost",
                secondaryShowLabels = true,
                secondaryValueFormat = ValueDisplayFormat.PERCENT,
                secondaryValueDecimals = 1,
                secondaryUnits = "USD",
            ),
            "derived",
        )
        val json = VegaSpecBuilder.build(
            spec, false, listOf(0x006A6A), 600f, 320f,
        )
        assertNotNull(json.getJSONArray("data").optJSONObject(3))
        assertEquals("table2", json.getJSONArray("data").getJSONObject(3).getString("name"))
        assertEquals(5, json.getJSONArray("scales").length())
        assertEquals("y2", json.getJSONArray("scales").getJSONObject(3).getString("name"))
        val rightAxis = json.getJSONArray("axes").getJSONObject(json.getJSONArray("axes").length() - 1)
        assertEquals("right", rightAxis.getString("orient"))
        assertEquals("Cost (USD)", rightAxis.getString("title"))
        assertEquals(",.1%", rightAxis.getString("format"))
        assertEquals(true, json.getJSONObject("usermeta").getBoolean("legendInteractive"))
        assertEquals("USD", json.getJSONObject("usermeta").getString("secondaryUnits"))
        assertEquals(
            listOf("Values", "cost"),
            (0 until json.getJSONObject("usermeta").getJSONArray("series").length())
                .map { json.getJSONObject("usermeta").getJSONArray("series").getString(it) },
        )
        val markNames = (0 until json.getJSONArray("marks").length())
            .map { json.getJSONArray("marks").getJSONObject(it).optString("name") }
        assertTrue(markNames.contains("cloudySecondaryLine"))
        assertTrue(markNames.contains("cloudySecondaryHit"))
        assertTrue(markNames.contains("cloudySecondaryMarker"))
        assertTrue(markNames.contains("cloudySecondaryLabel"))
        val secondaryMarker = json.getJSONArray("marks")
            .getJSONObject(markNames.indexOf("cloudySecondaryMarker"))
            .getJSONObject("encode").getJSONObject("enter")
        assertEquals("y2", secondaryMarker.getJSONObject("y").getString("scale"))
        val secondaryLabel = json.getJSONArray("marks")
            .getJSONObject(markNames.indexOf("cloudySecondaryLabel"))
            .getJSONObject("encode").getJSONObject("enter")
        assertTrue(secondaryLabel.getJSONObject("y").getString("signal").contains("scale('y2'"))
        assertTrue(secondaryLabel.getJSONObject("text").getString("signal").contains("format(datum.y"))
        assertTrue(
            (0 until json.getJSONArray("scales").length()).any {
                json.getJSONArray("scales").getJSONObject(it).optString("name") == "secondaryLegend"
            },
        )
        assertEquals("secondaryLegend", json.getJSONArray("legends").getJSONObject(0).getString("stroke"))
        val secondaryLine = json.getJSONArray("marks")
            .getJSONObject(markNames.indexOf("cloudySecondaryLine"))
            .getJSONObject("encode").getJSONObject("enter")
        assertEquals("y2", secondaryLine.getJSONObject("y").getString("scale"))
        val secondaryHit = json.getJSONArray("marks")
            .getJSONObject(markNames.indexOf("cloudySecondaryHit"))
            .getJSONObject("encode").getJSONObject("enter")
        assertEquals("y2", secondaryHit.getJSONObject("y").getString("scale"))
        val primaryBar = json.getJSONArray("marks").getJSONObject(0)
            .getJSONObject("encode").getJSONObject("enter")
        assertEquals("y", primaryBar.getJSONObject("y").getString("scale"))
        assertEquals(
            true,
            json.getJSONArray("data").getJSONObject(3).getJSONArray("values")
                .getJSONObject(0).getBoolean("secondary"),
        )
        assertEquals(
            "table2",
            json.getJSONArray("scales").getJSONObject(3)
                .getJSONObject("domain").getString("data"),
        )

        val synchronized = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "dual-sync", 0, result,
                ChartOptions(
                    secondaryAxisTitle = "Cost",
                    secondaryAxisSynchronized = true,
                ),
                "derived",
            ),
            false, listOf(0x006A6A), 600f, 320f,
        )
        assertEquals(
            "table",
            synchronized.getJSONArray("scales").getJSONObject(3)
                .getJSONObject("domain").getString("data"),
        )
        val legendOff = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "dual-no-legend", 0, result,
                ChartOptions(legendVisible = false),
                "derived",
            ),
            false, listOf(0x006A6A), 600f, 320f,
        )
        assertFalse(legendOff.has("legends"))
        val noMarks = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "dual-no-marks", 0, result,
                ChartOptions(secondaryShowMarkers = false, secondaryShowLabels = false),
                "derived",
            ),
            false, listOf(0x006A6A), 600f, 320f,
        )
        val noMarkNames = (0 until noMarks.getJSONArray("marks").length())
            .map { noMarks.getJSONArray("marks").getJSONObject(it).optString("name") }
        assertFalse(noMarkNames.contains("cloudySecondaryMarker"))
        assertFalse(noMarkNames.contains("cloudySecondaryLabel"))
        val hidden = VegaSpecBuilder.build(
            ChartSpecBuilder.build(
                "dual-hidden", 0, result,
                ChartOptions(hiddenSeries = setOf("cost"), secondaryShowLabels = true),
                "derived",
            ),
            false, listOf(0x006A6A), 600f, 320f,
        )
        val hiddenNames = (0 until hidden.getJSONArray("marks").length())
            .map { hidden.getJSONArray("marks").getJSONObject(it).optString("name") }
        assertFalse(hiddenNames.contains("cloudySecondaryLine"))
        assertFalse(hiddenNames.contains("cloudySecondaryHit"))
        assertFalse(hiddenNames.contains("cloudySecondaryMarker"))
        assertFalse(hiddenNames.contains("cloudySecondaryLabel"))
        assertTrue(hidden.has("legends"))
    }


}
