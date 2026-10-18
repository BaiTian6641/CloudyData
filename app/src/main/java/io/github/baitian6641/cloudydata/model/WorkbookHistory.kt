package io.github.baitian6641.cloudydata.model

/**
 * Pure bounded undo/redo history for workbook document edits. Source-side
 * effects (imports, refreshes) are not replayed by undo; the caller resets this
 * history when a data snapshot changes.
 */
class WorkbookHistory(
    initial: WorkbookDocument,
    private val limit: Int = 50,
) {
    var current: WorkbookDocument = initial
        private set

    private val undoStack = ArrayDeque<WorkbookDocument>()
    private val redoStack = ArrayDeque<WorkbookDocument>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun apply(transform: (WorkbookDocument) -> WorkbookDocument): WorkbookDocument {
        val next = transform(current)
        if (next == current) return current
        undoStack.addLast(current)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        current = next
        return current
    }

    /** Metadata/edit that should not create an undo entry (for example active-tab state). */
    fun replaceCurrent(transform: (WorkbookDocument) -> WorkbookDocument): WorkbookDocument {
        current = transform(current)
        return current
    }

    /** Keep the current document's saved revision while preserving undo entries. */
    fun adoptSavedRevision(saved: WorkbookDocument) {
        current = saved
    }

    fun undo(): WorkbookDocument? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): WorkbookDocument? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        return current
    }

    fun reset(document: WorkbookDocument) {
        current = document
        undoStack.clear()
        redoStack.clear()
    }
}
