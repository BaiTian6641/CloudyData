package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.chart.ChartKind
import io.github.baitian6641.cloudydata.chart.ChartSpecBuilder
import io.github.baitian6641.cloudydata.chart.ChartSpecException
import io.github.baitian6641.cloudydata.engine.EngineColumn
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
import io.github.baitian6641.cloudydata.model.BarStackMode
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.GridlineContrast
import io.github.baitian6641.cloudydata.model.LabelCollisionPolicy
import io.github.baitian6641.cloudydata.model.ValueDisplayFormat
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.LegendOrientation
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.LogicalType
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.LegendMarkSample
import io.github.baitian6641.cloudydata.model.CurrencyCode
import io.github.baitian6641.cloudydata.model.LineDashStyle
import io.github.baitian6641.cloudydata.model.LineInterpolation
import io.github.baitian6641.cloudydata.model.MarkerShape
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
    fun categoricalXLabelCountIsThinnedAndValidated() {
        val spec = ChartSpecBuilder.build(
            "labels", 0, lineResult(),
            ChartOptions(xLabelCount = 2),
            "derived",
        )
        assertEquals(listOf("2026-01-01", "2026-04-01"), spec.xAxis.labelValues)

        val barSpec = ChartSpecBuilder.build(
            "bar-labels", 0, WorksheetResult(
                result = EngineResult(
                    columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                    rows = listOf(
                        listOf<Any?>("A", 1L),
                        listOf<Any?>("B", 2L),
                        listOf<Any?>("C", 3L),
                        listOf<Any?>("D", 4L),
                    ),
                    truncated = false,
                ),
                rowField = region,
                measureField = sales,
                seriesField = null,
                aggregation = Aggregation.SUM,
                markType = MarkType.BAR,
                sql = "test",
            ),
            ChartOptions(xLabelCount = 3),
            "derived",
        )
        assertEquals(listOf("A", "C", "D"), barSpec.xAxis.labelValues)

        try {
            ChartSpecBuilder.build(
                "labels", 0, lineResult(),
                ChartOptions(xLabelCount = 1),
                "derived",
            )
            throw AssertionError("expected X-label count rejection")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("2 and 50"))
        }
        try {
            ChartSpecBuilder.build(
                "labels", 0, lineResult(),
                ChartOptions(xLabelCount = 51),
                "derived",
            )
            throw AssertionError("expected upper X-label count rejection")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("2 and 50"))
        }
    }

    @Test
    fun hiddenSeriesAreSanitizedAgainstCurrentSeries() {
        val hidden = ChartSpecBuilder.build(
            "hidden", 0, lineResult(),
            ChartOptions(hiddenSeries = setOf("South", "Ghost")),
            "derived",
        ).hiddenSeries
        assertEquals(setOf("South"), hidden)
        val barResult = WorksheetResult(
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
            markType = MarkType.BAR,
            sql = "test",
        )
        assertEquals(
            emptySet<String>(),
            ChartSpecBuilder.build(
                "hidden", 0, barResult,
                ChartOptions(hiddenSeries = setOf("South")),
                "derived",
            ).hiddenSeries,
        )
        val singleLine = WorksheetResult(
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
        assertEquals(
            emptySet<String>(),
            ChartSpecBuilder.build(
                "hidden", 0, singleLine,
                ChartOptions(hiddenSeries = setOf("sales")),
                "derived",
            ).hiddenSeries,
        )
    }

    @Test
    fun legendMarkSampleIsCarriedAndDefaultsToMarker() {
        assertEquals(
            LegendMarkSample.LINE,
            ChartSpecBuilder.build(
                "legend", 0, lineResult(),
                ChartOptions(legendMarkSample = LegendMarkSample.LINE),
                "derived",
            ).legend.markSample,
        )
        assertEquals(
            LegendMarkSample.MARKER,
            ChartSpecBuilder.build(
                "legend", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).legend.markSample,
        )
    }

    @Test
    fun lineOpacityIsCarriedAndBounded() {
        assertEquals(
            0.5,
            ChartSpecBuilder.build(
                "opacity", 0, lineResult(),
                ChartOptions(lineOpacity = 0.5),
                "derived",
            ).lineOpacity!!,
            0.0,
        )
        assertEquals(
            null,
            ChartSpecBuilder.build(
                "opacity", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).lineOpacity,
        )
        for (invalid in listOf(0.0, 1.1, Double.NaN)) {
            try {
                ChartSpecBuilder.build(
                    "opacity", 0, lineResult(),
                    ChartOptions(lineOpacity = invalid),
                    "derived",
                )
                throw AssertionError("expected line opacity rejection: $invalid")
            } catch (expected: ChartSpecException) {
                assertTrue(expected.message!!.contains("Line opacity"))
            }
        }
    }

    @Test
    fun lineDashStyleIsCarriedAndDefaultsToSolid() {
        assertEquals(
            LineDashStyle.DOTTED,
            ChartSpecBuilder.build(
                "dash", 0, lineResult(),
                ChartOptions(lineDashStyle = LineDashStyle.DOTTED),
                "derived",
            ).lineDashStyle,
        )
        assertEquals(
            LineDashStyle.SOLID,
            ChartSpecBuilder.build(
                "dash", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).lineDashStyle,
        )
    }

    @Test
    fun currencyFormatIsCarriedAndDefaultsToUsd() {
        val spec = ChartSpecBuilder.build(
            "currency", 0, lineResult(),
            ChartOptions(valueFormat = ValueDisplayFormat.CURRENCY, currencyCode = CurrencyCode.EUR),
            "derived",
        )
        assertEquals(ValueDisplayFormat.CURRENCY, spec.valueFormat)
        assertEquals(CurrencyCode.EUR, spec.currencyCode)
        assertEquals(
            CurrencyCode.USD,
            ChartSpecBuilder.build(
                "currency", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).currencyCode,
        )
    }

    @Test
    fun lineInterpolationIsCarriedAndDefaultsToLinear() {
        assertEquals(
            LineInterpolation.STEP,
            ChartSpecBuilder.build(
                "interpolation", 0, lineResult(),
                ChartOptions(lineInterpolation = LineInterpolation.STEP),
                "derived",
            ).lineInterpolation,
        )
        assertEquals(
            LineInterpolation.LINEAR,
            ChartSpecBuilder.build(
                "interpolation", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).lineInterpolation,
        )
    }

    @Test
    fun markerOptionsAreCarriedAndBounded() {
        val spec = ChartSpecBuilder.build(
            "markers", 0, lineResult(),
            ChartOptions(
                showMarkers = false,
                markerSize = 6.0,
                markerOpacity = 0.5,
                markerBorder = true,
                markerBorderWidth = 2.0,
            ),
            "derived",
        )
        assertEquals(false, spec.markers.show)
        assertEquals(6.0, spec.markers.size!!, 0.0)
        assertEquals(MarkerShape.CIRCLE, spec.markers.shape)
        assertEquals(0.5, spec.markers.opacity!!, 0.0)
        assertEquals(true, spec.markers.border)
        assertEquals(2.0, spec.markers.borderWidth!!, 0.0)
        assertEquals(
            true,
            ChartSpecBuilder.build(
                "markers", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).markers.show,
        )
        assertEquals(
            MarkerShape.DIAMOND,
            ChartSpecBuilder.build(
                "markers", 0, lineResult(),
                ChartOptions(markerShape = MarkerShape.DIAMOND),
                "derived",
            ).markers.shape,
        )
        assertEquals(
            0.5,
            ChartSpecBuilder.build(
                "markers", 0, lineResult(),
                ChartOptions(markerOpacity = 0.5),
                "derived",
            ).markers.opacity!!,
            0.0,
        )
        for (invalid in listOf(0.0, 4.0, Double.NaN)) {
            try {
                ChartSpecBuilder.build(
                    "markers", 0, lineResult(),
                    ChartOptions(markerBorderWidth = invalid),
                    "derived",
                )
                throw AssertionError("expected marker border rejection: $invalid")
            } catch (expected: ChartSpecException) {
                assertTrue(expected.message!!.contains("Marker border width"))
            }
        }
        for (invalid in listOf(0.0, 1.1, Double.NaN)) {
            try {
                ChartSpecBuilder.build(
                    "markers", 0, lineResult(),
                    ChartOptions(markerOpacity = invalid),
                    "derived",
                )
                throw AssertionError("expected marker opacity rejection: $invalid")
            } catch (expected: ChartSpecException) {
                assertTrue(expected.message!!.contains("Marker opacity"))
            }
        }
        for (invalid in listOf(1.0, 13.0, Double.NaN)) {
            try {
                ChartSpecBuilder.build(
                    "markers", 0, lineResult(),
                    ChartOptions(markerSize = invalid),
                    "derived",
                )
                throw AssertionError("expected marker size rejection: $invalid")
            } catch (expected: ChartSpecException) {
                assertTrue(expected.message!!.contains("Marker size"))
            }
        }
    }

    @Test
    fun valueDisplayFormatIsCarriedAndDefaultsToNumber() {
        assertEquals(
            ValueDisplayFormat.PERCENT,
            ChartSpecBuilder.build(
                "percent", 0, lineResult(),
                ChartOptions(valueFormat = ValueDisplayFormat.PERCENT),
                "derived",
            ).valueFormat,
        )
        assertEquals(
            ValueDisplayFormat.NUMBER,
            ChartSpecBuilder.build(
                "percent", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).valueFormat,
        )
    }

    @Test
    fun labelCollisionPolicyIsCarriedAndDefaultsToShowAll() {
        assertEquals(
            LabelCollisionPolicy.HIDE_CROWDED,
            ChartSpecBuilder.build(
                "labels", 0, lineResult(),
                ChartOptions(labelCollisionPolicy = LabelCollisionPolicy.HIDE_CROWDED),
                "derived",
            ).labels.collisionPolicy,
        )
        assertEquals(
            LabelCollisionPolicy.SHOW_ALL,
            ChartSpecBuilder.build(
                "labels", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).labels.collisionPolicy,
        )
    }

    @Test
    fun minorGridlinesAreCarriedAndDefaultOff() {
        assertEquals(
            true,
            ChartSpecBuilder.build(
                "minor", 0, lineResult(),
                ChartOptions(minorGridlines = true),
                "derived",
            ).gridlines.minor,
        )
        assertEquals(
            false,
            ChartSpecBuilder.build(
                "minor", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).gridlines.minor,
        )
    }

    @Test
    fun gridlineContrastIsCarriedAndDefaultsToDivider() {
        assertEquals(
            GridlineContrast.STRONG,
            ChartSpecBuilder.build(
                "contrast", 0, lineResult(),
                ChartOptions(gridlineContrast = GridlineContrast.STRONG),
                "derived",
            ).gridlines.contrast,
        )
        assertEquals(
            GridlineContrast.DEFAULT,
            ChartSpecBuilder.build(
                "contrast", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).gridlines.contrast,
        )
    }

    @Test
    fun transparentExportBackgroundIsCarriedAndDefaultsOff() {
        assertEquals(
            true,
            ChartSpecBuilder.build(
                "transparent", 0, lineResult(),
                ChartOptions(transparentExportBackground = true),
                "derived",
            ).transparentExportBackground,
        )
        assertEquals(
            false,
            ChartSpecBuilder.build(
                "transparent", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).transparentExportBackground,
        )
    }

    @Test
    fun chartBorderIsCarriedAndDefaultsOff() {
        assertEquals(
            true,
            ChartSpecBuilder.build(
                "border", 0, lineResult(),
                ChartOptions(showChartBorder = true),
                "derived",
            ).chartBorder,
        )
        assertEquals(
            false,
            ChartSpecBuilder.build(
                "border", 0, lineResult(),
                ChartOptions(),
                "derived",
            ).chartBorder,
        )
        assertEquals(
            true,
            ChartSpecBuilder.build(
                "border-bar", 0,
                WorksheetResult(
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
                    markType = MarkType.BAR,
                    sql = "test",
                ),
                ChartOptions(showChartBorder = true),
                "derived",
            ).chartBorder,
        )
    }

    @Test
    fun valueDecimalsAreCarriedAndBounded() {
        val spec = ChartSpecBuilder.build(
            "format", 0, lineResult(),
            ChartOptions(valueDecimals = 2),
            "derived",
        )
        assertEquals(2, spec.valueDecimals)
        assertEquals(null, ChartSpecBuilder.build(
            "format", 0, lineResult(),
            ChartOptions(),
            "derived",
        ).valueDecimals)

        for (invalid in listOf(-1, 7)) {
            try {
                ChartSpecBuilder.build(
                    "format", 0, lineResult(),
                    ChartOptions(valueDecimals = invalid),
                    "derived",
                )
                throw AssertionError("expected value decimals rejection: $invalid")
            } catch (expected: ChartSpecException) {
                assertTrue(expected.message!!.contains("Value decimals"))
            }
        }
    }

    @Test
    fun gridlineWidthIsCarriedAndBounded() {
        val spec = ChartSpecBuilder.build(
            "grid", 0, lineResult(),
            ChartOptions(gridlineWidth = 2.5),
            "derived",
        )
        assertEquals(2.5, spec.gridlines.width!!, 0.0)
        assertTrue(spec.gridlines.show)

        for (invalid in listOf(0.0, 3.5, Double.NaN)) {
            try {
                ChartSpecBuilder.build(
                    "grid", 0, lineResult(),
                    ChartOptions(gridlineWidth = invalid),
                    "derived",
                )
                throw AssertionError("expected gridline width rejection: $invalid")
            } catch (expected: ChartSpecException) {
                assertTrue(expected.message!!.contains("Gridline width"))
            }
        }
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


    @Test
    fun barSeriesBuildsGroupedBarsAndStackedBasesInQueryOrder() {
        val result = barSeriesResult()
        val grouped = ChartSpecBuilder.build(
            "bar-grouped", 0, result,
            ChartOptions(seriesTitle = "Region", legendVisible = true),
            "derived",
        )
        assertEquals(ChartKind.BAR, grouped.kind)
        assertEquals(listOf("North", "South"), grouped.series.map { it.key })
        assertEquals(
            listOf("Mar", "Feb", "Jan", "Apr"),
            grouped.series.flatMap { it.points }.sortedBy { it.xOrder }.map { it.x }.distinct(),
        )
        assertEquals(true, grouped.barHasSeries)
        assertEquals(BarStackMode.NONE, grouped.barStackMode)
        assertTrue(grouped.legend.visible)
        assertEquals("Region", grouped.legend.title)
        val groupedSouth = grouped.series.first { it.key == "South" }
        assertEquals(listOf("Feb", "Jan", "Apr"), groupedSouth.points.map { it.x })
        assertEquals(listOf(0.0, 0.0, 0.0), groupedSouth.points.map { it.stackBase })
        assertEquals(1, groupedSouth.points.first().seriesIndex)
        assertEquals(22.0, grouped.series.flatMap { it.points }.filter { it.x == "Feb" }.sumOf { it.y!! }, 0.0)

        val stacked = ChartSpecBuilder.build(
            "bar-stacked", 0, result,
            ChartOptions(barStackMode = BarStackMode.STACKED),
            "derived",
        )
        assertEquals(BarStackMode.STACKED, stacked.barStackMode)
        val stackedSouth = stacked.series.first { it.key == "South" }
        assertEquals(listOf(20.0, 10.0, 0.0), stackedSouth.points.map { it.stackBase })
        val stackedNorth = stacked.series.first { it.key == "North" }
        assertEquals(listOf(0.0, 0.0, 0.0), stackedNorth.points.map { it.stackBase })
        assertEquals(listOf(30.0, 20.0, 10.0), stackedNorth.points.map { it.y })
        assertEquals(
            listOf("Mar", "Feb", "Jan", "Apr"),
            stacked.series.flatMap { it.points }.sortedBy { it.xOrder }.map { it.x }.distinct(),
        )
    }

    @Test
    fun stackedModeWithoutColorDoesNotFakeSeries() {
        val result = WorksheetResult(
            result = EngineResult(
                columns = listOf(EngineColumn("row", "VARCHAR"), EngineColumn("value", "BIGINT")),
                rows = listOf(listOf<Any?>("A", 2L), listOf<Any?>("B", 3L)),
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
            "bar", 0, result, ChartOptions(barStackMode = BarStackMode.STACKED), "derived",
        )
        assertEquals(false, spec.barHasSeries)
        assertEquals(BarStackMode.NONE, spec.barStackMode)
        assertEquals(1, spec.series.size)
    }


    @Test
    fun stackedBarRequiresAdditiveAggregationButGroupedDoesNot() {
        val average = barSeriesResult().copy(aggregation = Aggregation.AVG)
        val grouped = ChartSpecBuilder.build(
            "bar-grouped-avg", 0, average, ChartOptions(), "derived",
        )
        assertEquals(BarStackMode.NONE, grouped.barStackMode)
        try {
            ChartSpecBuilder.build(
                "bar-stacked-avg", 0, average,
                ChartOptions(barStackMode = BarStackMode.STACKED),
                "derived",
            )
            throw AssertionError("stacked average bar was accepted")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("Sum or Count"))
        }
    }


    @Test
    fun percentStackedBarNormalizesEachCategoryToOneAndForcesPercentAxis() {
        val result = barSeriesResult()
        val spec = ChartSpecBuilder.build(
            "bar-percent", 0, result,
            ChartOptions(barStackMode = BarStackMode.PERCENT_STACKED),
            "derived",
        )
        assertEquals(BarStackMode.PERCENT_STACKED, spec.barStackMode)
        assertEquals(ValueDisplayFormat.PERCENT, spec.valueFormat)
        assertEquals(0.0, spec.yAxis.min!!, 0.0)
        assertEquals(1.0, spec.yAxis.max!!, 0.0)
        val points = spec.series.flatMap { it.points }
        val totals = points.groupBy { it.x }.mapValues { (_, category) ->
            category.sumOf { it.y ?: 0.0 }
        }
        totals.forEach { (category, total) ->
            assertEquals("category $category does not total 100%", 1.0, total, 0.000001)
        }
        val feb = points.filter { it.x == "Feb" }.sortedBy { it.seriesIndex }
        assertEquals(20.0 / 22.0, feb[0].y!!, 0.000001)
        assertEquals(2.0 / 22.0, feb[1].y!!, 0.000001)
        assertEquals(20.0 / 22.0, feb[1].stackBase!!, 0.000001)
        assertEquals(20L.toString(), feb[0].exactY)
        assertEquals(2L.toString(), feb[1].exactY)
    }

    @Test
    fun percentStackedBarRejectsNegativeOrZeroCategoryTotals() {
        val negative = barSeriesResult().copy(
            result = barSeriesResult().result.copy(
                rows = listOf(
                    listOf<Any?>("Jan", "North", 5L),
                    listOf<Any?>("Jan", "South", -1L),
                ),
            ),
        )
        try {
            ChartSpecBuilder.build(
                "bar-percent-negative", 0, negative,
                ChartOptions(barStackMode = BarStackMode.PERCENT_STACKED),
                "derived",
            )
            throw AssertionError("negative 100% stack was accepted")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("non-negative"))
        }

        val zero = barSeriesResult().copy(
            result = barSeriesResult().result.copy(
                rows = listOf(
                    listOf<Any?>("Jan", "North", 0L),
                    listOf<Any?>("Jan", "South", 0L),
                ),
            ),
        )
        try {
            ChartSpecBuilder.build(
                "bar-percent-zero", 0, zero,
                ChartOptions(barStackMode = BarStackMode.PERCENT_STACKED),
                "derived",
            )
            throw AssertionError("zero-total 100% stack was accepted")
        } catch (expected: ChartSpecException) {
            assertTrue(expected.message!!.contains("positive category total"))
        }
    }

    private fun barSeriesResult(): WorksheetResult = WorksheetResult(
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
