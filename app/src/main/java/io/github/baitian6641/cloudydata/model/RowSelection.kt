package io.github.baitian6641.cloudydata.model

enum class SelectionMode { REPLACE, TOGGLE, RANGE }

/** Exact source-row selection with a stable range anchor and endpoint. */
data class RowSelectionState(
    val rows: Set<Int> = emptySet(),
    val anchor: Int = -1,
    val primary: Int = -1,
)

object RowSelectionReducer {
    fun select(current: RowSelectionState, row: Int, mode: SelectionMode): RowSelectionState {
        if (row < 0) return current
        return when (mode) {
            SelectionMode.REPLACE -> RowSelectionState(setOf(row), row, row)
            SelectionMode.TOGGLE -> RowSelectionState(
                rows = if (row in current.rows) current.rows - row else current.rows + row,
                anchor = current.anchor,
                primary = row,
            )
            SelectionMode.RANGE -> {
                if (current.anchor < 0) {
                    RowSelectionState(setOf(row), row, row)
                } else {
                    val start = minOf(current.anchor, row)
                    val end = maxOf(current.anchor, row)
                    RowSelectionState((start..end).toSet(), current.anchor, row)
                }
            }
        }
    }

    /** Keeps selection coherent when a result shrinks; anchors outside are reset. */
    fun clamp(current: RowSelectionState, rowCount: Int): RowSelectionState {
        if (rowCount <= 0) return RowSelectionState()
        val rows = current.rows.filter { it in 0 until rowCount }.toSet()
        val primary = current.primary.takeIf { it in 0 until rowCount } ?: -1
        val anchor = current.anchor.takeIf { it in 0 until rowCount } ?: -1
        return RowSelectionState(rows, anchor, primary)
    }

    /**
     * Applies an exact set from a rectangle/lasso. Additive keeps the existing
     * set; replace clears it, including when [rows] is empty.
     */
    fun selectMany(
        current: RowSelectionState,
        rows: Set<Int>,
        additive: Boolean,
    ): RowSelectionState {
        val incoming = rows.filter { it >= 0 }.toSet()
        if (incoming.isEmpty()) {
            return if (additive) current else RowSelectionState()
        }
        if (additive) {
            val combined = current.rows + incoming
            val anchor = current.anchor.takeIf { it >= 0 } ?: incoming.minOrNull() ?: -1
            return RowSelectionState(combined, anchor, incoming.maxOrNull() ?: current.primary)
        }
        return RowSelectionState(
            rows = incoming,
            anchor = incoming.minOrNull() ?: -1,
            primary = incoming.maxOrNull() ?: -1,
        )
    }
}
