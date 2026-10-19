package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.chart.AxisLabelSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AxisLabelSelectionTest {
    @Test
    fun evenlySpacedSubsetKeepsFirstAndLast() {
        val values = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
        assertEquals(listOf("Jan", "Apr", "Jun"), AxisLabelSelection.thin(values, 3))
        assertEquals(listOf("Jan", "Feb", "Apr", "May", "Jun"), AxisLabelSelection.thin(values, 5))
    }

    @Test
    fun automaticOrOversizedRequestKeepsAllLabels() {
        val values = listOf("A", "B", "C")
        assertNull(AxisLabelSelection.thin(values, null))
        assertNull(AxisLabelSelection.thin(values, 3))
        assertNull(AxisLabelSelection.thin(values, 4))
        assertNull(AxisLabelSelection.thin(emptyList(), 2))
        assertNull(AxisLabelSelection.thin(values, 1))
    }
}
