package io.github.baitian6641.cloudydata.chart

/**
 * Selects an evenly spaced subset of band-domain labels without changing the
 * domain or data. First and last labels are always retained.
 */
object AxisLabelSelection {
    fun thin(values: List<String>, requested: Int?): List<String>? {
        if (requested == null || requested < 2 || values.size <= requested) return null
        if (values.isEmpty()) return null
        return (0 until requested)
            .map { index ->
                ((values.lastIndex.toLong() * index + (requested - 1) / 2) / (requested - 1)).toInt()
            }
            .distinct()
            .map(values::get)
    }
}
