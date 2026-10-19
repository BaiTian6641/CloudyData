package io.github.baitian6641.cloudydata

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartSpecBuilder
import io.github.baitian6641.cloudydata.engine.EngineColumn
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.LogicalType
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.renderer.VegaSpecBuilder
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                legendVisible = true,
                legendPosition = LegendPosition.RIGHT,
            ),
            "derived",
        )
        val json = VegaSpecBuilder.build(spec, dark = false, palette = listOf(0x006A6A, 0x3559C7), widthDp = 600f, heightDp = 300f)
        assertEquals(1, json.getInt("version"))
        val group = json.getJSONArray("marks").getJSONObject(0)
        assertEquals("group", group.getString("type"))
        val facet = group.getJSONObject("from").getJSONObject("facet")
        assertEquals("table", facet.getString("data"))
        assertEquals("series", facet.getJSONArray("groupby").getString(0))
        val line = group.getJSONArray("marks").getJSONObject(0)
        assertEquals("line", line.getString("type"))
        assertEquals("series", line.getJSONObject("from").getString("data"))
        assertEquals(
            "isValid(datum.y)",
            line.getJSONObject("encode").getJSONObject("enter").getJSONObject("defined").getString("signal"),
        )
        assertTrue("legend missing for multi-series line", json.has("legends"))
        assertEquals("right", json.getJSONArray("legends").getJSONObject(0).getString("orient"))
        val xDomain = json.getJSONArray("scales").getJSONObject(0).getJSONArray("domain")
        assertEquals(4, xDomain.length())
        assertEquals("2026-01-01", xDomain.getString(0))
        assertEquals("2026-04-01", xDomain.getString(3))
        assertTrue("tooltip visibility missing from the renderer spec", json.getJSONObject("usermeta").getBoolean("tooltip"))
        val dataNames = (0 until json.getJSONArray("data").length())
            .map { json.getJSONArray("data").getJSONObject(it).getString("name") }
        assertTrue("selected-row dataset missing: $dataNames", "selected" in dataNames)
        val selectedMarks = (0 until json.getJSONArray("marks").length())
            .map { json.getJSONArray("marks").getJSONObject(it) }
        val selectedOverlay = selectedMarks.first { it.optString("name") == "cloudySelectedPoints" }
        assertEquals("selected", selectedOverlay.getJSONObject("from").getString("data"))
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
        val spec = ChartSpecBuilder.build("bar", 0, result, ChartOptions(legendVisible = true), "derived")
        val json = VegaSpecBuilder.build(spec, false, listOf(0x006A6A), 400f, 300f)
        assertFalse(json.has("legends"))
        val bar = json.getJSONArray("marks").getJSONObject(0)
        assertEquals("rect", bar.getString("type"))
        assertTrue(bar.has("clip"))
        assertTrue(
            "selected bar overlay missing",
            (0 until json.getJSONArray("marks").length()).any {
                json.getJSONArray("marks").getJSONObject(it).optString("name") == "cloudySelectedBar"
            },
        )
    }
}
