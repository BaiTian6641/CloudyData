package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.chart.LegendLayout
import io.github.baitian6641.cloudydata.model.LegendPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class LegendLayoutTest {
    @Test
    fun longLabelsReduceBottomColumns() {
        assertEquals(2, LegendLayout.columns(360f, LegendPosition.BOTTOM, listOf("North America"), 4))
        assertEquals(3, LegendLayout.columns(360f, LegendPosition.BOTTOM, listOf("North"), 4))
    }

    @Test
    fun rightLegendsBudgetOnlyTheirOwnPane() {
        assertEquals(1, LegendLayout.columns(600f, LegendPosition.RIGHT, listOf("North"), 4))
        assertEquals(5, LegendLayout.columns(600f, LegendPosition.BOTTOM, listOf("North"), 5))
    }

    @Test
    fun columnsNeverExceedSeriesCountOrZero() {
        assertEquals(1, LegendLayout.columns(360f, LegendPosition.BOTTOM, listOf("North"), 1))
        assertEquals(1, LegendLayout.columns(360f, LegendPosition.BOTTOM, emptyList(), 0))
    }

    @Test
    fun labelLimitTracksWidthWithFloorAndCap() {
        assertEquals(60, LegendLayout.labelLimitDp(100f))
        assertEquals(144, LegendLayout.labelLimitDp(360f))
        assertEquals(180, LegendLayout.labelLimitDp(900f))
    }
}
