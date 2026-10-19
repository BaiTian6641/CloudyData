package io.github.baitian6641.cloudydata

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.GridlineContrast
import io.github.baitian6641.cloudydata.model.LabelCollisionPolicy
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.LegendOrientation
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.LegendMarkSample
import io.github.baitian6641.cloudydata.model.LineDashStyle
import io.github.baitian6641.cloudydata.model.LineInterpolation
import io.github.baitian6641.cloudydata.model.MarkerShape
import io.github.baitian6641.cloudydata.model.ValueDisplayFormat
import org.json.JSONObject
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartSettingsPersistenceTest {
    private lateinit var root: File
    private lateinit var store: WorkbookStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.filesDir, "chart-settings-test-${UUID.randomUUID()}")
        store = WorkbookStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun chartCustomizationSurvivesSaveAndReopenWithDefaultsForOldDocuments() {
        val document = store.create("Chart settings")
        val sheet = document.worksheets.single()
        assertEquals(null, sheet.chart.title)
        assertTrue(sheet.chart.showGridlines)
        assertFalse(sheet.chart.showValueLabels)
        assertTrue("tooltip defaults on for old options", sheet.chart.showTooltip)

        val saved = store.save(
            document.copy(
                worksheets = listOf(
                    sheet.copy(
                        markType = MarkType.LINE,
                        seriesFieldId = "region-field",
                        chart = ChartOptions(
                            title = "Regional sales",
                            subtitle = "Quarter to date",
                            caption = "Source rows are preserved in the workbook snapshot.",
                            showGridlines = false,
                            showValueLabels = true,
                            xAxisTitle = "Month",
                            yAxisTitle = "Sales",
                            seriesTitle = "Region",
                            yMin = 0.0,
                            yMax = 100.0,
                            zeroBaseline = false,
                            logScale = true,
                            reverseY = true,
                            legendVisible = true,
                            legendPosition = LegendPosition.RIGHT,
                            legendOrientation = LegendOrientation.VERTICAL,
                            showTooltip = false,
                            yTickCount = 6,
                            labelFontSize = 14,
                            lineWidth = 3.5,
                            labelPosition = LabelPosition.INSIDE_END,
                            units = "USD",
                            axisTitleAnchor = AxisTitleAnchor.END,
                            chartPadding = 12,
                            xLabelCount = 2,
                            gridlineWidth = 2.5,
                            valueDecimals = 2,
                            showChartBorder = true,
                            transparentExportBackground = true,
                            gridlineContrast = io.github.baitian6641.cloudydata.model.GridlineContrast.STRONG,
                            minorGridlines = true,
                            labelCollisionPolicy = LabelCollisionPolicy.HIDE_CROWDED,
                            valueFormat = ValueDisplayFormat.PERCENT,
                            hiddenSeries = setOf("North", "South"),
                            showMarkers = true,
                            markerSize = 6.0,
                            markerShape = MarkerShape.DIAMOND,
                            lineInterpolation = LineInterpolation.STEP,
                            lineDashStyle = LineDashStyle.DASHED,
                            lineOpacity = 0.5,
                            legendMarkSample = LegendMarkSample.LINE,
                            markerOpacity = 0.5,
                            showTableRowDividers = false,
                            showTableColumnDividers = true,
                        ),
                    ),
                ),
            ),
        )
        val reopened = store.load(saved.id)
        val reopenedSheet = reopened.worksheets.single()
        assertEquals("region-field", reopenedSheet.seriesFieldId)
        assertEquals("Regional sales", reopenedSheet.chart.title)
        assertEquals("Quarter to date", reopenedSheet.chart.subtitle)
        assertEquals("Source rows are preserved in the workbook snapshot.", reopenedSheet.chart.caption)
        assertFalse(reopenedSheet.chart.showGridlines)
        assertTrue(reopenedSheet.chart.showValueLabels)
        assertEquals("Month", reopenedSheet.chart.xAxisTitle)
        assertEquals("Sales", reopenedSheet.chart.yAxisTitle)
        assertEquals("Region", reopenedSheet.chart.seriesTitle)
        assertEquals(0.0, reopenedSheet.chart.yMin)
        assertEquals(100.0, reopenedSheet.chart.yMax)
        assertFalse(reopenedSheet.chart.zeroBaseline)
        assertTrue(reopenedSheet.chart.logScale)
        assertTrue(reopenedSheet.chart.reverseY)
        assertEquals(LegendPosition.RIGHT, reopenedSheet.chart.legendPosition)
        assertEquals(LegendOrientation.VERTICAL, reopenedSheet.chart.legendOrientation)
        assertFalse(reopenedSheet.chart.showTooltip)
        assertEquals(6, reopenedSheet.chart.yTickCount)
        assertEquals(14, reopenedSheet.chart.labelFontSize)
        assertEquals(3.5, reopenedSheet.chart.lineWidth!!, 0.0)
        assertEquals(LabelPosition.INSIDE_END, reopenedSheet.chart.labelPosition)
        assertEquals("USD", reopenedSheet.chart.units)
        assertEquals(AxisTitleAnchor.END, reopenedSheet.chart.axisTitleAnchor)
        assertEquals(12, reopenedSheet.chart.chartPadding)
        assertEquals(2, reopenedSheet.chart.xLabelCount)
        assertEquals(2.5, reopenedSheet.chart.gridlineWidth!!, 0.0)
        assertEquals(2, reopenedSheet.chart.valueDecimals)
        assertEquals(true, reopenedSheet.chart.showChartBorder)
        assertEquals(true, reopenedSheet.chart.transparentExportBackground)
        assertEquals(
            io.github.baitian6641.cloudydata.model.GridlineContrast.STRONG,
            reopenedSheet.chart.gridlineContrast,
        )
        assertEquals(true, reopenedSheet.chart.minorGridlines)
        assertEquals(LabelCollisionPolicy.HIDE_CROWDED, reopenedSheet.chart.labelCollisionPolicy)
        assertEquals(ValueDisplayFormat.PERCENT, reopenedSheet.chart.valueFormat)
        assertEquals(setOf("North", "South"), reopenedSheet.chart.hiddenSeries)
        assertEquals(true, reopenedSheet.chart.showMarkers)
        assertEquals(6.0, reopenedSheet.chart.markerSize!!, 0.0)
        assertEquals(MarkerShape.DIAMOND, reopenedSheet.chart.markerShape)
        assertEquals(LineInterpolation.STEP, reopenedSheet.chart.lineInterpolation)
        assertEquals(LineDashStyle.DASHED, reopenedSheet.chart.lineDashStyle)
        assertEquals(0.5, reopenedSheet.chart.lineOpacity!!, 0.0)
        assertEquals(LegendMarkSample.LINE, reopenedSheet.chart.legendMarkSample)
        assertEquals(0.5, reopenedSheet.chart.markerOpacity!!, 0.0)
        assertEquals(false, reopenedSheet.chart.showTableRowDividers)
        assertEquals(true, reopenedSheet.chart.showTableColumnDividers)
        assertEquals(2, reopened.formatVersion)
        val raw = JSONObject(File(root, "workbooks/${saved.id}/workbook.json").readText())
        assertEquals(2, raw.getInt("formatVersion"))
        val rawSheet = raw.getJSONArray("worksheets").getJSONObject(0)
        assertTrue(rawSheet.has("chart"))
        assertFalse(rawSheet.has("chartTitle"))
        assertFalse(rawSheet.has("showGridlines"))
        assertFalse(rawSheet.has("showValueLabels"))
    }

    @Test
    fun invalidGridlineContrastFallsBackToDefault() {
        assertEquals(GridlineContrast.DEFAULT, ChartOptions.fromJson(JSONObject()).gridlineContrast)
        assertEquals(
            GridlineContrast.DEFAULT,
            ChartOptions.fromJson(JSONObject().put("gridlineContrast", "NOT_A_CONTRAST")).gridlineContrast,
        )
        assertEquals(
            GridlineContrast.STRONG,
            ChartOptions.fromJson(JSONObject().put("gridlineContrast", "STRONG")).gridlineContrast,
        )
    }

    @Test
    fun tableDividerDefaultsAreStable() {
        val options = ChartOptions.fromJson(JSONObject())
        assertEquals(true, options.showTableRowDividers)
        assertEquals(false, options.showTableColumnDividers)
    }

    @Test
    fun invalidMarkerSizeFallsBackToAutomatic() {
        assertEquals(6.0, ChartOptions.fromJson(JSONObject().put("markerSize", 6.0)).markerSize!!, 0.0)
        assertEquals(null, ChartOptions.fromJson(JSONObject().put("markerSize", 1.0)).markerSize)
        assertEquals(null, ChartOptions.fromJson(JSONObject().put("markerSize", 13.0)).markerSize)
        assertEquals(true, ChartOptions.fromJson(JSONObject()).showMarkers)
        assertEquals(
            false,
            ChartOptions.fromJson(JSONObject().put("showMarkers", false)).showMarkers,
        )
        assertEquals(
            MarkerShape.CIRCLE,
            ChartOptions.fromJson(JSONObject().put("markerShape", "NOT_A_SHAPE")).markerShape,
        )
        assertEquals(
            MarkerShape.SQUARE,
            ChartOptions.fromJson(JSONObject().put("markerShape", "SQUARE")).markerShape,
        )
        assertEquals(
            LineInterpolation.LINEAR,
            ChartOptions.fromJson(JSONObject().put("lineInterpolation", "NOT_A_LINE")).lineInterpolation,
        )
        assertEquals(
            LineInterpolation.MONOTONE,
            ChartOptions.fromJson(JSONObject().put("lineInterpolation", "MONOTONE")).lineInterpolation,
        )
        assertEquals(
            LineDashStyle.SOLID,
            ChartOptions.fromJson(JSONObject().put("lineDashStyle", "NOT_A_DASH")).lineDashStyle,
        )
        assertEquals(
            LineDashStyle.DOTTED,
            ChartOptions.fromJson(JSONObject().put("lineDashStyle", "DOTTED")).lineDashStyle,
        )
        assertEquals(null, ChartOptions.fromJson(JSONObject().put("lineOpacity", 0.0)).lineOpacity)
        assertEquals(null, ChartOptions.fromJson(JSONObject().put("lineOpacity", 1.1)).lineOpacity)
        assertEquals(
            0.5,
            ChartOptions.fromJson(JSONObject().put("lineOpacity", 0.5)).lineOpacity!!,
            0.0,
        )
        assertEquals(
            LegendMarkSample.MARKER,
            ChartOptions.fromJson(JSONObject().put("legendMarkSample", "NOT_A_SAMPLE")).legendMarkSample,
        )
        assertEquals(
            LegendMarkSample.LINE,
            ChartOptions.fromJson(JSONObject().put("legendMarkSample", "LINE")).legendMarkSample,
        )
        assertEquals(null, ChartOptions.fromJson(JSONObject().put("markerOpacity", 0.0)).markerOpacity)
        assertEquals(null, ChartOptions.fromJson(JSONObject().put("markerOpacity", 1.1)).markerOpacity)
        assertEquals(
            0.5,
            ChartOptions.fromJson(JSONObject().put("markerOpacity", 0.5)).markerOpacity!!,
            0.0,
        )
    }

    @Test
    fun invalidValueFormatFallsBackToNumber() {
        assertEquals(ValueDisplayFormat.NUMBER, ChartOptions.fromJson(JSONObject()).valueFormat)
        assertEquals(
            ValueDisplayFormat.NUMBER,
            ChartOptions.fromJson(JSONObject().put("valueFormat", "NOT_A_FORMAT")).valueFormat,
        )
        assertEquals(
            ValueDisplayFormat.PERCENT,
            ChartOptions.fromJson(JSONObject().put("valueFormat", "PERCENT")).valueFormat,
        )
    }

    @Test
    fun hiddenSeriesJsonPreservesKeysAndBounds() {
        val options = ChartOptions.fromJson(
            JSONObject().put(
                "hiddenSeries",
                org.json.JSONArray().put(" North ").put("").put("x".repeat(300)),
            ),
        )
        assertEquals(setOf(" North "), options.hiddenSeries)
        assertEquals(emptySet<String>(), ChartOptions.fromJson(JSONObject()).hiddenSeries)
        val written = ChartOptions(hiddenSeries = setOf("South", " North "))
            .toJson().getJSONArray("hiddenSeries")
        assertEquals(2, written.length())
        assertEquals(" North ", written.getString(0))
        assertEquals("South", written.getString(1))
    }

    @Test
    fun invalidLabelCollisionPolicyFallsBackToShowAll() {
        assertEquals(
            LabelCollisionPolicy.SHOW_ALL,
            ChartOptions.fromJson(JSONObject()).labelCollisionPolicy,
        )
        assertEquals(
            LabelCollisionPolicy.SHOW_ALL,
            ChartOptions.fromJson(
                JSONObject().put("labelCollisionPolicy", "NOT_A_POLICY"),
            ).labelCollisionPolicy,
        )
        assertEquals(
            LabelCollisionPolicy.HIDE_CROWDED,
            ChartOptions.fromJson(
                JSONObject().put("labelCollisionPolicy", "HIDE_CROWDED"),
            ).labelCollisionPolicy,
        )
    }

    @Test
    fun barDocumentCannotKeepLineOnlyLogScale() {
        val document = store.create("Bar log normalization")
        val sheet = document.worksheets.single()
        val saved = store.save(
            document.copy(
                worksheets = listOf(
                    sheet.copy(
                        markType = MarkType.BAR,
                        chart = ChartOptions(logScale = true),
                    ),
                ),
            ),
        )
        assertFalse(store.load(saved.id).worksheets.single().chart.logScale)
    }

    @Test
    fun documentLevelV1LoadNormalizesAndPreservesLegacyChartOptions() {
        val document = store.create("Legacy document")
        val raw = JSONObject(File(root, "workbooks/${document.id}/workbook.json").readText())
        raw.put("formatVersion", 1)
        val sheet = raw.getJSONArray("worksheets").getJSONObject(0)
        sheet.remove("chart")
        sheet.remove("seriesFieldId")
        sheet.put("chartTitle", "Legacy document title")
        sheet.put("showGridlines", false)
        sheet.put("showValueLabels", true)
        File(root, "workbooks/${document.id}/workbook.json").writeText(raw.toString())

        val loaded = store.load(document.id)
        assertEquals(2, loaded.formatVersion)
        assertEquals("Legacy document title", loaded.worksheets.single().chart.title)
        assertFalse(loaded.worksheets.single().chart.showGridlines)
        assertTrue(loaded.worksheets.single().chart.showValueLabels)
        assertTrue("legacy documents default the tooltip on", loaded.worksheets.single().chart.showTooltip)

        store.save(loaded)
        val rewritten = JSONObject(File(root, "workbooks/${document.id}/workbook.json").readText())
        assertEquals(2, rewritten.getInt("formatVersion"))
        val rewrittenSheet = rewritten.getJSONArray("worksheets").getJSONObject(0)
        assertTrue(rewrittenSheet.has("chart"))
        assertFalse(rewrittenSheet.has("chartTitle"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureWorkbookVersionIsRejectedBeforeMigration() {
        val base = store.create("Future")
        val json = JSONObject(File(root, "workbooks/${base.id}/workbook.json").readText())
        json.put("formatVersion", 3)
        io.github.baitian6641.cloudydata.model.WorkbookDocument.fromJson(json)
    }

    @Test
    fun legacyTopLevelChartFieldsMigrateToNestedOptions() {
        val legacy = JSONObject()
            .put("id", "sheet")
            .put("name", "Sheet 1")
            .put("rowFieldId", JSONObject.NULL)
            .put("measureFieldId", JSONObject.NULL)
            .put("aggregation", "SUM")
            .put("markType", "BAR")
            .put("sortDescending", true)
            .put("chartTitle", "Legacy title")
            .put("showGridlines", false)
            .put("showValueLabels", true)
        val sheet = io.github.baitian6641.cloudydata.model.WorksheetDef.fromJson(legacy)
        assertEquals("Legacy title", sheet.chart.title)
        assertFalse(sheet.chart.showGridlines)
        assertTrue(sheet.chart.showValueLabels)

        legacy.put(
            "chart",
            JSONObject()
                .put("title", "Nested wins")
                .put("showGridlines", true)
                .put("showValueLabels", false),
        )
        val nested = io.github.baitian6641.cloudydata.model.WorksheetDef.fromJson(legacy)
        assertEquals("Nested wins", nested.chart.title)
        assertTrue(nested.chart.showGridlines)
        assertFalse(nested.chart.showValueLabels)
    }
}
