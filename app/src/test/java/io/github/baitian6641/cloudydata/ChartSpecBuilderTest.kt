package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartSpecBuilder
import io.github.baitian6641.cloudydata.chart.ChartSpecException
import io.github.baitian6641.cloudydata.engine.EngineColumn
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.LegendOrientation
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.LogicalType
import io.github.baitian6641.cloudydata.model.MarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartSpecBuilderTest {
    private val region = FieldDef("region", "region", "VARCHAR", LogicalType.STRING, FieldRole.DIMENSION)
    private val month = FieldDef("month", "month", "VARCHAR", LogicalType.STRING, FieldRole.DIMENSION)
    private val sales = FieldDef("sales", "sales", "BIGINT", LogicalType.INTEGER, FieldRole.MEASURE)

    @Test
    fun lineSpecGroupsSeriesAndPreservesObservedOrder() {
        val result = lineResult()
        val spec = ChartSpecBuilder.build(
            chartId = "line-sheet",
            revision = 4,
            result = result,
            options = ChartOptions(
                title = "Regional sales",
                xAxisTitle = "Month",
                yAxisTitle = "Sales",
                seriesTitle = "Region",
                yMin = 0.0,
                yMax = 60.0,
                zeroBaseline = true,
                showGridlines = false,
                showValueLabels = true,
                legendVisible = true,
                legendPosition = LegendPosition.RIGHT,
                legendOrientation = LegendOrientation.VERTICAL,
                lineWidth = 3.5,
            ),
            derivedTitle = "derived",
        )
        assertEquals(ChartKind.LINE, spec.kind)
        assertEquals(4, spec.revision)
        assertEquals("Month", spec.xAxis.title)
        assertEquals("Sales", spec.yAxis.title)
        assertEquals(0.0, spec.yAxis.min!!, 0.0)
        assertEquals(60.0, spec.yAxis.max!!, 0.0)
        assertEquals(false, spec.gridlines.show)
        assertEquals(true, spec.labels.show)
        assertEquals(LegendPosition.RIGHT, spec.legend.position)
        assertEquals(LegendOrientation.VERTICAL, spec.legend.orientation)
        assertEquals(3.5, spec.lineWidth!!, 0.0)
        assertEquals(2, spec.series.size)
        val north = spec.series.first { it.key == "North" }
        val south = spec.series.first { it.key == "South" }
        assertEquals(listOf("2026-01-01", "2026-02-01", "2026-03-01"), north.points.map { it.x })
        assertEquals(listOf(10.0, 20.0, 30.0), north.points.map { it.y })
        assertEquals(60.0, north.points.sumOf { it.y!! }, 0.0)
        assertEquals(listOf("2026-01-01", "2026-02-01", "2026-04-01"), south.points.map { it.x })
        assertEquals(listOf(1.0, 2.0, 4.0), south.points.map { it.y })
        assertEquals(7.0, south.points.sumOf { it.y!! }, 0.0)
        assertEquals(67.0, spec.series.flatMap { it.points }.sumOf { it.y!! }, 0.0)
        assertTrue("March must remain absent for South", south.points.none { it.x == "2026-03-01" })
    }

    @Test
    fun chartPaddingDefaultsAndIsValidated() {
        val default = ChartSpecBuilder.build("padding", 0, lineResult(), ChartOptions(), "derived")
        assertEquals(8, default.padding)
        val custom = ChartSpecBuilder.build(
            "padding", 0, lineResult(),
            ChartOptions(chartPadding = 0),
            "derived",
        )
        assertEquals(0, custom.padding)
        try {
            ChartSpecBuilder.build(
                "padding", 0, lineResult(),
                ChartOptions(chartPadding = 33),
                "derived",
            )
            throw AssertionError("expected chart-padding rejection")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("0 and 32"))
        }
    }

    @Test
    fun unitsAndAxisTitleAnchorAreCarried() {
        val spec = ChartSpecBuilder.build(
            "units", 0, lineResult(),
            ChartOptions(
                units = "USD",
                axisTitleAnchor = AxisTitleAnchor.END,
                yAxisTitle = "Sales",
            ),
            "derived",
        )
        assertEquals("USD", spec.units)
        assertEquals(AxisTitleAnchor.END, spec.xAxis.titleAnchor)
        assertEquals(AxisTitleAnchor.END, spec.yAxis.titleAnchor)
        assertEquals("Sales (USD)", spec.yAxis.title)

        val bounded = ChartSpecBuilder.build(
            "units", 0, lineResult(),
            ChartOptions(units = "U".repeat(40)),
            "derived",
        )
        assertEquals(16, bounded.units?.length)
    }

    @Test
    fun subtitleAndCaptionAreBoundedAndCarried() {
        val subtitle = "S".repeat(200)
        val caption = "C".repeat(300)
        val spec = ChartSpecBuilder.build(
            "titles", 0, lineResult(),
            ChartOptions(subtitle = subtitle, caption = caption),
            "derived",
        )
        assertEquals("S".repeat(160), spec.subtitle)
        assertEquals("C".repeat(240), spec.caption)

        val barResult = WorksheetResult(
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
        val barSpec = ChartSpecBuilder.build(
            "bar", 0, barResult,
            ChartOptions(subtitle = "Bar subtitle", caption = "Bar caption"),
            "derived",
        )
        assertEquals("Bar subtitle", barSpec.subtitle)
        assertEquals("Bar caption", barSpec.caption)
    }

    @Test
    fun tooltipVisibilityIsCarriedOnTheSpec() {
        val enabled = ChartSpecBuilder.build("line", 0, lineResult(), ChartOptions(), "derived")
        assertTrue(enabled.tooltip.show)
        val disabled = ChartSpecBuilder.build(
            "line", 0, lineResult(), ChartOptions(showTooltip = false), "derived",
        )
        assertEquals(false, disabled.tooltip.show)
    }

    @Test
    fun areaSpecIsSingleSeriesAndForcesZeroBaselineWithoutLegend() {
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
        val spec = ChartSpecBuilder.build(
            "area-sheet", 7, result,
            ChartOptions(
                zeroBaseline = false,
                showValueLabels = true,
                legendVisible = true,
                legendPosition = LegendPosition.RIGHT,
            ),
            "derived",
        )
        assertEquals(ChartKind.AREA, spec.kind)
        assertEquals(1, spec.series.size)
        assertEquals(listOf(10.0, 20.0, 30.0), spec.series[0].points.map { it.y })
        assertEquals(listOf(0, 1, 2), spec.series[0].points.map { it.sourceRowIndex })
        assertTrue("area must use a zero baseline", spec.yAxis.zero)
        assertEquals(false, spec.legend.visible)
        assertEquals(3, spec.rowCount)
    }

    @Test
    fun areaExplicitRangeIncludesZeroForTheAreaBaseline() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = listOf(listOf<Any?>("A", 10L), listOf<Any?>("B", 20L)),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.AREA,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build(
            "area", 0, result, ChartOptions(yMin = 10.0, yMax = 20.0, zeroBaseline = false), "derived",
        )
        assertEquals(0.0, spec.yAxis.min!!, 0.0)
        assertEquals(20.0, spec.yAxis.max!!, 0.0)
        assertTrue(spec.yAxis.zero)
    }

    @Test(expected = ChartSpecException::class)
    fun areaColorSeriesIsRejected() {
        ChartSpecBuilder.build(
            "area", 0, lineResult().copy(markType = MarkType.AREA),
            ChartOptions(), "derived",
        )
    }

    @Test(expected = ChartSpecException::class)
    fun areaLogScaleIsRejected() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = listOf(listOf<Any?>("A", 10L), listOf<Any?>("B", 20L)),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.AREA,
            sql = "test",
        )
        ChartSpecBuilder.build("area", 0, result, ChartOptions(logScale = true), "derived")
    }

    @Test(expected = ChartSpecException::class)
    fun invalidYRangeIsRejected() {
        ChartSpecBuilder.build(
            "chart", 0, lineResult(),
            ChartOptions(yMin = 10.0, yMax = 10.0), "derived",
        )
    }

    @Test(expected = ChartSpecException::class)
    fun logarithmicAxisRejectsZero() {
        val zeroResult = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = listOf(listOf<Any?>("A", 0L)),
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        ChartSpecBuilder.build("chart", 0, zeroResult, ChartOptions(logScale = true), "derived")
    }

    @Test
    fun nullAggregateBecomesUndefinedGapWithoutChangingObservedOrder() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(
                    EngineColumn("row", "VARCHAR"),
                    EngineColumn("series", "VARCHAR"),
                    EngineColumn("value", "HUGEINT"),
                ),
                rows = listOf(
                    listOf<Any?>("2026-01-01", "North", null),
                    listOf<Any?>("2026-02-01", "North", 20L),
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
        val spec = ChartSpecBuilder.build("chart", 0, result, ChartOptions(), "derived")
        val north = spec.series.single()
        assertEquals(listOf(false, true), north.points.map { it.defined })
        assertEquals(null, north.points[0].y)
        assertEquals(20.0, north.points[1].y!!, 0.0)
        assertEquals(1, spec.definedPointCount)
    }

    @Test(expected = ChartSpecException::class)
    fun truncatedResultIsNeverDrawnAsPartialLine() {
        val base = lineResult()
        ChartSpecBuilder.build(
            "chart", 0,
            base.copy(result = base.result.copy(truncated = true)),
            ChartOptions(),
            "derived",
        )
    }

    @Test
    fun linePointBoundaryAccepts2000AndRejects2001() {
        fun rows(count: Int) = (0 until count).map { listOf<Any?>("x$it", 1L) }
        fun result(count: Int) = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = rows(count),
                truncated = false,
            ),
            rowField = month,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.LINE,
            sql = "test",
        )
        val accepted = ChartSpecBuilder.build("line", 0, result(2000), ChartOptions(), "derived")
        assertEquals(2000, accepted.pointCount)
        try {
            ChartSpecBuilder.build("line", 0, result(2001), ChartOptions(), "derived")
            throw AssertionError("expected 2001-point rejection")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("2000"))
        }
    }

    @Test(expected = ChartSpecException::class)
    fun barLogScaleIsRejected() {
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
        ChartSpecBuilder.build("chart", 0, result, ChartOptions(logScale = true), "derived")
    }

    @Test
    fun seriesColumnAndValueIndexFollowActualQueryShape() {
        val line = lineResult()
        assertTrue(line.hasSeriesColumn)
        assertEquals(2, line.valueColumnIndex)
        val rowOnlyButColorPlaced = line.copy(
            measureField = null,
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = listOf(listOf<Any?>("2026-01-01", 2L)),
                truncated = false,
            ),
        )
        assertTrue(!rowOnlyButColorPlaced.hasSeriesColumn)
        assertEquals(1, rowOnlyButColorPlaced.valueColumnIndex)
    }

    @Test
    fun barSpecUsesObservedCategoriesAndExceedsCapIsRejected() {
        val rows = listOf(
            listOf<Any?>("B", 60L),
            listOf<Any?>("A", 40L),
        )
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = rows,
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        val spec = ChartSpecBuilder.build("bar", 0, result, ChartOptions(), "derived")
        assertEquals(ChartKind.BAR, spec.kind)
        assertEquals(listOf("B", "A"), spec.series.single().points.map { it.x })
        assertEquals(listOf(60.0, 40.0), spec.series.single().points.map { it.y })

        val tooMany = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = (0..24).map { listOf<Any?>("c$it", it.toLong()) },
                truncated = false,
            ),
            rowField = region,
            measureField = sales,
            seriesField = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.BAR,
            sql = "test",
        )
        try {
            ChartSpecBuilder.build("bar", 0, tooMany, ChartOptions(), "derived")
            throw AssertionError("expected category cap rejection")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("24"))
        }
    }

    @Test
    fun axisTickCountsAreCarriedAndValidated() {
        val spec = ChartSpecBuilder.build(
            "ticks", 2, lineResult(),
            ChartOptions(yTickCount = 7),
            "derived",
        )
        assertEquals(7, spec.yAxis.tickCount)

        try {
            ChartSpecBuilder.build(
                "ticks", 2, lineResult(),
                ChartOptions(yTickCount = 1),
                "derived",
            )
            throw AssertionError("expected tick-count rejection")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("2 and 50"))
        }
    }

    @Test
    fun barLabelPositionIsCarriedAndLineForcesTop() {
        val barResult = WorksheetResult(
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
        val barSpec = ChartSpecBuilder.build(
            "bar", 0, barResult,
            ChartOptions(showValueLabels = true, labelPosition = LabelPosition.INSIDE_END),
            "derived",
        )
        assertEquals(LabelPosition.INSIDE_END, barSpec.labels.position)

        val lineSpec = ChartSpecBuilder.build(
            "line", 0, lineResult(),
            ChartOptions(showValueLabels = true, labelPosition = LabelPosition.INSIDE_END),
            "derived",
        )
        assertEquals(LabelPosition.TOP, lineSpec.labels.position)
    }

    @Test
    fun lineWidthIsValidated() {
        try {
            ChartSpecBuilder.build(
                "line", 0, lineResult(),
                ChartOptions(lineWidth = 0.5),
                "derived",
            )
            throw AssertionError("expected line-width rejection")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("1.0 and 8.0"))
        }
    }

    @Test
    fun labelFontSizeIsCarriedAndValidated() {
        val spec = ChartSpecBuilder.build(
            "labels", 3, lineResult(),
            ChartOptions(
                showValueLabels = true,
                labelFontSize = 14,
            ),
            "derived",
        )
        assertEquals(14, spec.labels.fontSize)

        try {
            ChartSpecBuilder.build(
                "labels", 3, lineResult(),
                ChartOptions(showValueLabels = true, labelFontSize = 7),
                "derived",
            )
            throw AssertionError("expected label font-size rejection")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("8 and 16"))
        }
    }

    @Test
    fun measureHeaderUnitsAreAppendedOnce() {
        assertEquals(
            "Sum of Sales (USD)",
            io.github.baitian6641.cloudydata.ui.measureLabelWithUnits(Aggregation.SUM, "Sales", "USD"),
        )
        assertEquals(
            "Sum of Revenue (USD)",
            io.github.baitian6641.cloudydata.ui.measureLabelWithUnits(Aggregation.SUM, "Revenue (USD)", "USD"),
        )
        assertEquals(
            "Sum of Sales",
            io.github.baitian6641.cloudydata.ui.measureLabelWithUnits(Aggregation.SUM, "Sales", null),
        )
    }

    private fun lineResult(): WorksheetResult = WorksheetResult(
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
}
