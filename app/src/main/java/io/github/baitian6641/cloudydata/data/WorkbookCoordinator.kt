package io.github.baitian6641.cloudydata.data

import io.github.baitian6641.cloudydata.model.WorkbookDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WorkbookConflictException(val expectedRevision: Long, val actualRevision: Long) :
    IllegalStateException(
        "The workbook changed in another window (revision $expectedRevision → $actualRevision). " +
            "Your change was not applied. Review the current workbook and apply it again.",
    )

data class CoordinatedWorkbook(
    val document: WorkbookDocument,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val recoveryMessages: List<String> = emptyList(),
)

/** One application-process authority per workbook. No file I/O occurs on the UI thread. */
class WorkbookRegistry(private val store: WorkbookStore) {
    private val mutex = Mutex()
    private val documents = mutableMapOf<String, WorkbookCoordinator>()

    suspend fun open(workbookId: String): WorkbookCoordinator = mutex.withLock {
        documents[workbookId] ?: withContext(Dispatchers.IO) {
            val recovery = ImportRecovery(store).reconcile(workbookId)
            WorkbookCoordinator(
                store, store.load(workbookId), recovery.messages,
                recovery.retainedOperationDirectories.isNotEmpty(),
            )
        }.also { documents[workbookId] = it }
    }
}

/**
 * Serializes document commands, snapshot replacement and snapshot readers.
 * Publish only after the existing atomic metadata save succeeds. History is
 * process-local; this is not a durable command journal or a cross-process lock.
 */
class WorkbookCoordinator internal constructor(
    private val store: WorkbookStore,
    initial: WorkbookDocument,
    recoveryMessages: List<String> = emptyList(),
    recoveryPending: Boolean = false,
) {
    private val mutex = Mutex()
    private val undoStack = ArrayDeque<WorkbookDocument>()
    private val redoStack = ArrayDeque<WorkbookDocument>()
    private var snapshotReadsBlocked = recoveryPending
    private val _state = MutableStateFlow(CoordinatedWorkbook(initial, recoveryMessages = recoveryMessages))
    val state: StateFlow<CoordinatedWorkbook> = _state.asStateFlow()

    suspend fun edit(
        expectedRevision: Long,
        transform: (WorkbookDocument) -> WorkbookDocument,
    ): WorkbookDocument = command {
        val current = requireRevision(expectedRevision)
        val candidate = transform(current)
        require(candidate.id == current.id) { "A command cannot change workbook identity." }
        require(candidate.snapshot == current.snapshot) { "Snapshot changes require an import operation." }
        if (candidate == current) return@command current
        val saved = save(candidate.copy(revision = current.revision))
        undoStack.addLast(current)
        while (undoStack.size > HISTORY_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        publish(saved)
    }

    suspend fun undo(expectedRevision: Long): WorkbookDocument = command {
        val current = requireRevision(expectedRevision)
        val previous = undoStack.lastOrNull() ?: return@command current
        val saved = save(previous.copy(revision = current.revision))
        undoStack.removeLast()
        redoStack.addLast(current)
        publish(saved)
    }

    suspend fun redo(expectedRevision: Long): WorkbookDocument = command {
        val current = requireRevision(expectedRevision)
        val next = redoStack.lastOrNull() ?: return@command current
        val saved = save(next.copy(revision = current.revision))
        redoStack.removeLast()
        undoStack.addLast(current)
        publish(saved)
    }

    /** The receipt-backed import operation saves its own metadata exactly once. */
    suspend fun importSnapshot(
        expectedRevision: Long,
        operation: suspend (WorkbookDocument) -> WorkbookDocument,
    ): WorkbookDocument = command {
        val current = requireRevision(expectedRevision)
        val saved = try {
            withContext(Dispatchers.IO) { operation(current) }.also {
                require(it.id == current.id && it.revision == current.revision + 1) {
                    "The import did not return the next saved workbook revision."
                }
            }
        } catch (error: Throwable) {
            // A receipt/cleanup failure may follow a successful metadata publish.
            // Reconcile reality before releasing the lock or authorizing readers.
            snapshotReadsBlocked = true
            val (reconciled, recovery) = try {
                withContext(Dispatchers.IO) {
                    val result = ImportRecovery(store).reconcile(current.id)
                    store.load(current.id) to result
                }
            } catch (recoveryError: Throwable) {
                recoveryError.addSuppressed(error)
                throw recoveryError
            }
            if (reconciled != current) {
                undoStack.clear()
                redoStack.clear()
                publish(reconciled)
            }
            if (recovery.retainedOperationDirectories.isNotEmpty()) {
                _state.value = _state.value.copy(recoveryMessages = recovery.messages)
                throw IllegalStateException("Snapshot recovery is pending. ${recovery.messages.joinToString(" ")}", error)
            }
            snapshotReadsBlocked = false
            throw error
        }
        undoStack.clear()
        redoStack.clear()
        publish(saved)
    }

    /** Prevent replacing the current fixed snapshot path while a query reads it. */
    suspend fun <T> readSnapshot(snapshotId: String, operation: suspend () -> T): T = mutex.withLock {
        check(!snapshotReadsBlocked) { "Snapshot recovery is pending. Reopen the workbook before querying." }
        check(_state.value.document.snapshot?.id == snapshotId) { "The source snapshot changed. Retry the query." }
        withContext(Dispatchers.IO) { operation() }
    }

    suspend fun reload(): WorkbookDocument = command {
        val (loaded, recovery) = withContext(Dispatchers.IO) {
            val id = _state.value.document.id
            val result = if (snapshotReadsBlocked) ImportRecovery(store).reconcile(id) else null
            store.load(id) to result
        }
        if (recovery != null) {
            _state.value = _state.value.copy(recoveryMessages = recovery.messages)
            check(recovery.retainedOperationDirectories.isEmpty()) {
                "Snapshot recovery is pending. ${recovery.messages.joinToString(" ")}"
            }
        }
        if (loaded != _state.value.document) {
            undoStack.clear()
            redoStack.clear()
            publish(loaded)
        }
        snapshotReadsBlocked = false
        loaded
    }

    // Once a command owns the document, complete persistence and publication even
    // if its initiating Activity closes. Cancellation while waiting acquires no authority.
    private suspend fun <T> command(operation: suspend () -> T): T = mutex.withLock {
        withContext(NonCancellable) { operation() }
    }

    private fun requireRevision(expected: Long): WorkbookDocument {
        check(!snapshotReadsBlocked) { "Snapshot recovery is pending. Reopen the workbook before editing." }
        val current = _state.value.document
        if (expected != current.revision) throw WorkbookConflictException(expected, current.revision)
        return current
    }

    private suspend fun save(document: WorkbookDocument) = withContext(Dispatchers.IO) { store.save(document) }

    private fun publish(saved: WorkbookDocument): WorkbookDocument {
        _state.value = _state.value.copy(document = saved, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
        return saved
    }

    companion object {
        private const val HISTORY_LIMIT = 50
    }
}
