package io.github.baitian6641.cloudydata

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.MarkType
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

        val saved = store.save(
            document.copy(
                worksheets = listOf(
                    sheet.copy(
                        markType = MarkType.LINE,
                        seriesFieldId = "region-field",
                        chart = ChartOptions(
                            title = "Regional sales",
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
                        ),
                    ),
                ),
            ),
        )
        val reopened = store.load(saved.id)
        val reopenedSheet = reopened.worksheets.single()
        assertEquals("region-field", reopenedSheet.seriesFieldId)
        assertEquals("Regional sales", reopenedSheet.chart.title)
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
