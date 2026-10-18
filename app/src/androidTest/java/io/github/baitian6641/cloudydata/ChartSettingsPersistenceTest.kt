package io.github.baitian6641.cloudydata

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudydata.data.WorkbookStore
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
        assertEquals(null, sheet.chartTitle)
        assertTrue(sheet.showGridlines)
        assertFalse(sheet.showValueLabels)

        val saved = store.save(
            document.copy(
                worksheets = listOf(
                    sheet.copy(
                        chartTitle = "Regional sales",
                        showGridlines = false,
                        showValueLabels = true,
                    ),
                ),
            ),
        )
        val reopened = store.load(saved.id)
        val reopenedSheet = reopened.worksheets.single()
        assertEquals("Regional sales", reopenedSheet.chartTitle)
        assertFalse(reopenedSheet.showGridlines)
        assertTrue(reopenedSheet.showValueLabels)
    }
}
