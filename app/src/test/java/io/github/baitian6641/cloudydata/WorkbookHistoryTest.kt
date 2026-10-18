package io.github.baitian6641.cloudydata

import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorkbookHistory
import io.github.baitian6641.cloudydata.model.WorksheetDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbookHistoryTest {
    private fun document(name: String = "Book", revision: Long = 1) = WorkbookDocument(
        id = "workbook",
        name = name,
        revision = revision,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
        snapshot = null,
        worksheets = listOf(
            WorksheetDef(
                id = "sheet",
                name = "Sheet 1",
                rowFieldId = null,
                measureFieldId = null,
                aggregation = io.github.baitian6641.cloudydata.model.Aggregation.SUM,
                markType = io.github.baitian6641.cloudydata.model.MarkType.TABLE,
                sortDescending = true,
            ),
        ),
        activeWorksheetId = "sheet",
    )

    @Test
    fun undoRedoRestoresDocumentStateAndClearsRedoOnNewEdit() {
        val history = WorkbookHistory(document())
        history.apply { it.copy(name = "First") }
        history.apply { it.copy(name = "Second") }
        assertEquals("Second", history.current.name)
        assertTrue(history.canUndo)

        assertEquals("First", history.undo()?.name)
        assertEquals("Book", history.undo()?.name)
        assertEquals("Book", history.current.name)
        assertFalse(history.canUndo)
        assertEquals("First", history.redo()?.name)

        history.apply { it.copy(name = "Branch") }
        assertFalse(history.canRedo)
        assertEquals("Branch", history.current.name)
    }

    @Test
    fun historyIsBounded() {
        val history = WorkbookHistory(document(), limit = 3)
        for (index in 1..6) history.apply { it.copy(name = "Edit $index") }
        var undos = 0
        while (history.undo() != null) undos++
        assertEquals(3, undos)
        assertEquals("Edit 3", history.current.name)
    }

    @Test
    fun chartOptionsParticipateInUndoRedoWithoutRequery() {
        val base = document()
        val history = WorkbookHistory(base)
        val changed = history.apply { doc ->
            doc.copy(
                worksheets = listOf(doc.worksheets.single().copy(chart = ChartOptions(title = "Custom"))),
            )
        }
        assertEquals("Custom", changed.worksheets.single().chart.title)
        assertEquals(null, history.undo()?.worksheets?.single()?.chart?.title)
        assertEquals("Custom", history.redo()?.worksheets?.single()?.chart?.title)
    }

    @Test
    fun resetDropsHistoryAndSourceEffectsAreNotReplayed() {
        val history = WorkbookHistory(document())
        history.apply { it.copy(name = "Changed") }
        history.reset(document(name = "Imported", revision = 9))
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals("Imported", history.current.name)
        assertEquals(9, history.current.revision)
    }
}
