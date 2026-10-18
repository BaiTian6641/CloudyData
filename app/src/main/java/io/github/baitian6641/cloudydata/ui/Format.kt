package io.github.baitian6641.cloudydata.ui

import java.math.BigDecimal
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.Locale

fun formatTimestamp(epochMs: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
}

fun formatCount(value: Long): String = "%,d".format(value)

fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = arrayOf("KiB", "MiB", "GiB")
    var amount = value.toDouble() / 1024.0
    var unit = 0
    while (amount >= 1024.0 && unit < units.lastIndex) {
        amount /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", amount, units[unit])
}

private val displayDoubleFormat = DecimalFormat("0.######", DecimalFormatSymbols(Locale.US))

/**
 * Display formatting. Exact values remain available to tooltip/export paths via
 * [exactValueText]; display rounding never mutates a stored result.
 */
fun displayValue(value: Any?): String = when (value) {
    null -> "NULL"
    is Long -> formatCount(value)
    is Int -> formatCount(value.toLong())
    is Double -> if (value.isFinite()) displayDoubleFormat.format(BigDecimal.valueOf(value)) else value.toString()
    is Float -> displayValue(value.toDouble())
    is Boolean -> if (value) "True" else "False"
    is ByteArray -> "Binary · ${value.size} bytes"
    else -> value.toString()
}

fun exactValueText(value: Any?): String = when (value) {
    null -> "NULL"
    is ByteArray -> value.joinToString(" ") { "%02X".format(it) }
    else -> value.toString()
}
