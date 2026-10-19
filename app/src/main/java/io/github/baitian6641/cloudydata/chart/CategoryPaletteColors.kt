package io.github.baitian6641.cloudydata.chart

import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.model.CategoryPalette

/**
 * Resolves the chart category palette for the active surface. Kept outside
 * Compose so the same mapping serves the preview, fallback chart and probes.
 */
fun categoryPaletteColors(palette: CategoryPalette, dark: Boolean): LongArray = when (palette) {
    CategoryPalette.CLOUDY ->
        if (dark) CloudyTokens.Chart.categoricalDark else CloudyTokens.Chart.categoricalLight
    CategoryPalette.COLORBLIND ->
        if (dark) {
            CloudyTokens.Chart.categoricalColorblindDark
        } else {
            CloudyTokens.Chart.categoricalColorblindLight
        }
}
