package io.github.baitian6641.cloudydata.model

import java.math.BigDecimal

/** Numeric parsing for chart geometry only; exact values remain in the table. */
fun valueAsDouble(value: Any?): Double? = when (value) {
    is Long -> value.toDouble()
    is Int -> value.toDouble()
    is Double -> value
    is Float -> value.toDouble()
    is BigDecimal -> value.toDouble()
    is String -> value.toDoubleOrNull()
    else -> null
}
