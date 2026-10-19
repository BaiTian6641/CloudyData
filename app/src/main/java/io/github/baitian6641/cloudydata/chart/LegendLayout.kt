package io.github.baitian6641.cloudydata.chart

import io.github.baitian6641.cloudydata.model.LegendPosition

/** Bounded, renderer-independent wrapping budget for multi-series legends. */
object LegendLayout {
    private const val ENTRY_BASE_DP = 70f
    private const val CHAR_DP = 6.5f
    private const val RIGHT_AVAILABLE_FRACTION = 0.28f
    private const val MIN_LABEL_LIMIT = 60
    private const val MAX_LABEL_LIMIT = 180

    fun columns(
        widthDp: Float,
        position: LegendPosition,
        labels: List<String>,
        seriesCount: Int,
    ): Int {
        if (seriesCount <= 0) return 1
        val maxChars = labels.maxOfOrNull { it.length } ?: 0
        val estimatedEntry = ENTRY_BASE_DP + maxChars * CHAR_DP
        val available = if (position == LegendPosition.BOTTOM) {
            widthDp
        } else {
            widthDp * RIGHT_AVAILABLE_FRACTION
        }
        return (available / estimatedEntry).toInt().coerceIn(1, seriesCount)
    }

    fun labelLimitDp(widthDp: Float): Int =
        (widthDp * 0.4f).toInt().coerceIn(MIN_LABEL_LIMIT, MAX_LABEL_LIMIT)
}
