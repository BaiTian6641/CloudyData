package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.chart.categoryPaletteColors
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.model.CategoryPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

class CategoryPaletteTest {
    @Test
    fun paletteResolverKeepsCloudyAndColorblindMappingsSeparate() {
        assertEquals(
            CloudyTokens.Chart.categoricalLight.toList(),
            categoryPaletteColors(CategoryPalette.CLOUDY, dark = false).toList(),
        )
        assertEquals(
            CloudyTokens.Chart.categoricalDark.toList(),
            categoryPaletteColors(CategoryPalette.CLOUDY, dark = true).toList(),
        )
        assertEquals(
            CloudyTokens.Chart.categoricalColorblindLight.toList(),
            categoryPaletteColors(CategoryPalette.COLORBLIND, dark = false).toList(),
        )
        assertEquals(
            CloudyTokens.Chart.categoricalColorblindDark.toList(),
            categoryPaletteColors(CategoryPalette.COLORBLIND, dark = true).toList(),
        )
    }

    @Test
    fun colorblindPalettesAreDistinctUnderSimulatedColorVisionAndContrasting() {
        val light = CloudyTokens.Chart.categoricalColorblindLight
        val dark = CloudyTokens.Chart.categoricalColorblindDark
        assertEquals(8, light.size)
        assertEquals(8, dark.size)
        assertEquals(8, light.toSet().size)
        assertEquals(8, dark.toSet().size)

        assertPairwiseDistinct(light)
        assertPairwiseDistinct(dark)

        assertSurfaceContrast(CloudyTokens.Chart.categoricalLight, dark = false)
        assertSurfaceContrast(CloudyTokens.Chart.categoricalDark, dark = true)
        assertSurfaceContrast(light, dark = false)
        assertSurfaceContrast(dark, dark = true)
    }

    private fun assertSurfaceContrast(palette: LongArray, dark: Boolean) {
        for (color in palette) {
            if (dark) {
                assertContrast(color, CloudyTokens.Dark.canvas, "dark canvas")
                assertContrast(color, CloudyTokens.Dark.surface, "dark surface")
            } else {
                assertContrast(color, CloudyTokens.Light.canvas, "light canvas")
                assertContrast(color, CloudyTokens.Light.surface, "light surface")
            }
        }
    }

    private fun assertPairwiseDistinct(palette: LongArray) {
        for (simulation in listOf("normal", "protan", "deutan", "tritan")) {
            for (i in palette.indices) {
                for (j in i + 1 until palette.size) {
                    val distance = deltaE76(
                        simulatedLab(palette[i], simulation),
                        simulatedLab(palette[j], simulation),
                    )
                    assertTrue(
                        "${hex(palette[i])} vs ${hex(palette[j])} too close under $simulation: " +
                            String.format("%.2f", distance),
                        distance >= 15.0,
                    )
                }
            }
        }
    }

    private fun simulatedLab(color: Long, simulation: String): DoubleArray {
        val linear = doubleArrayOf(
            channel((color shr 16) and 0xFF),
            channel((color shr 8) and 0xFF),
            channel(color and 0xFF),
        )
        val matrix = when (simulation) {
            "normal" -> null
            "protan" -> arrayOf(
                doubleArrayOf(0.152286, 1.052583, -0.204868),
                doubleArrayOf(0.114503, 0.786281, 0.099216),
                doubleArrayOf(-0.003882, -0.048116, 1.051998),
            )
            "deutan" -> arrayOf(
                doubleArrayOf(0.367322, 0.860646, -0.227968),
                doubleArrayOf(0.280085, 0.672501, 0.047413),
                doubleArrayOf(-0.011820, 0.042940, 0.968881),
            )
            "tritan" -> arrayOf(
                doubleArrayOf(1.255528, -0.076749, -0.178779),
                doubleArrayOf(-0.078411, 0.930809, 0.147602),
                doubleArrayOf(0.004733, 0.691367, 0.303900),
            )
            else -> error("unknown simulation $simulation")
        }
        val rgb = if (matrix == null) linear else DoubleArray(3) { row ->
            matrix[row][0] * linear[0] + matrix[row][1] * linear[1] + matrix[row][2] * linear[2]
        }
        val x = 0.4124564 * rgb[0] + 0.3575761 * rgb[1] + 0.1804375 * rgb[2]
        val y = 0.2126729 * rgb[0] + 0.7151522 * rgb[1] + 0.0721750 * rgb[2]
        val z = 0.0193339 * rgb[0] + 0.1191920 * rgb[1] + 0.9503041 * rgb[2]
        val fx = labCurve(x / 0.95047)
        val fy = labCurve(y)
        val fz = labCurve(z / 1.08883)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun labCurve(value: Double): Double =
        if (value > 0.008856) value.pow(1.0 / 3.0) else 7.787 * value + 16.0 / 116.0

    private fun deltaE76(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    private fun assertContrast(color: Long, background: Long, label: String) {
        val ratio = contrast(color, background)
        assertTrue("$label: ${hex(color)} contrast ${"%.2f".format(ratio)} < 3", ratio >= 3.0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val high = maxOf(luminance(a), luminance(b))
        val low = minOf(luminance(a), luminance(b))
        return (high + 0.05) / (low + 0.05)
    }

    private fun luminance(color: Long): Double {
        val r = channel((color shr 16) and 0xFF)
        val g = channel((color shr 8) and 0xFF)
        val b = channel(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(value: Long): Double {
        val s = value / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun hex(color: Long): String = "#%06X".format(color and 0xFFFFFF)
}
