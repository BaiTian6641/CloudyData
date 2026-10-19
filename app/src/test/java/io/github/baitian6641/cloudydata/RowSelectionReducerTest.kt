package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.model.RowSelectionReducer
import io.github.baitian6641.cloudydata.model.RowSelectionState
import io.github.baitian6641.cloudydata.model.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RowSelectionReducerTest {
    @Test
    fun replaceThenTogglePreservesOrAddsExactRows() {
        var state = RowSelectionReducer.select(RowSelectionState(), 3, SelectionMode.REPLACE)
        assertEquals(setOf(3), state.rows)
        assertEquals(3, state.anchor)
        state = RowSelectionReducer.select(state, 5, SelectionMode.TOGGLE)
        assertEquals(setOf(3, 5), state.rows)
        assertEquals(5, state.primary)
        state = RowSelectionReducer.select(state, 3, SelectionMode.TOGGLE)
        assertEquals(setOf(5), state.rows)
        assertEquals(3, state.anchor)
    }

    @Test
    fun rangeUsesTheLastReplaceAnchorInEitherDirection() {
        var state = RowSelectionReducer.select(RowSelectionState(), 2, SelectionMode.REPLACE)
        state = RowSelectionReducer.select(state, 5, SelectionMode.RANGE)
        assertEquals((2..5).toSet(), state.rows)
        assertEquals(2, state.anchor)
        assertEquals(5, state.primary)
        state = RowSelectionReducer.select(state, 1, SelectionMode.RANGE)
        assertEquals((1..2).toSet(), state.rows)
        assertEquals(2, state.anchor)
        assertEquals(1, state.primary)
    }

    @Test
    fun rangeWithoutAnchorBehavesAsReplace() {
        val state = RowSelectionReducer.select(RowSelectionState(), 4, SelectionMode.RANGE)
        assertEquals(setOf(4), state.rows)
        assertEquals(4, state.anchor)
    }

    @Test
    fun clampingDropsRowsOutsideAShrunkResult() {
        val state = RowSelectionState(setOf(0, 2, 9), anchor = 9, primary = 9)
        val clamped = RowSelectionReducer.clamp(state, 3)
        assertEquals(setOf(0, 2), clamped.rows)
        assertEquals(-1, clamped.anchor)
        assertEquals(-1, clamped.primary)
        assertTrue(RowSelectionReducer.clamp(state, 0).rows.isEmpty())
    }
}
