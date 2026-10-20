package io.github.baitian6641.cloudydata.data

import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.CommandOutcome
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef
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
                store, store.loadCheckpoint(workbookId), recovery.messages,
                recovery.retainedOperationDirectories.isNotEmpty(),
            )
        }.also { documents[workbookId] = it }
    }
}

/**
 * Serializes document commands, snapshot replacement and snapshot readers.
 * Publish only after the existing atomic metadata save succeeds. History and retained command receipts share the atomic metadata checkpoint;
 * this is not a cross-process lock or an executable replay log.
 */
class WorkbookCoordinator internal constructor(
    private val store: WorkbookStore,
    initial: WorkbookCheckpoint,
    recoveryMessages: List<String> = emptyList(),
    recoveryPending: Boolean = false,
) {
    private val mutex = Mutex()
    private var checkpoint = initial
    private var snapshotReadsBlocked = recoveryPending
    private val _state = MutableStateFlow(CoordinatedWorkbook(initial.document, initial.undo.isNotEmpty(), initial.redo.isNotEmpty(), recoveryMessages))
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
        commit(checkpoint.copy(
            document = candidate.copy(revision = current.revision),
            undo = (checkpoint.undo + current).takeLast(WorkbookCheckpoint.HISTORY_LIMIT),
            redo = emptyList(),
        ))
    }

    suspend fun execute(request: WorkbookCommand): CommandOutcome = command {
        request.validate()
        check(!snapshotReadsBlocked) { "Workbook recovery is pending. Reopen before editing." }
        val current = checkpoint.document
        require(request.workbookId == current.id) { "Command belongs to another workbook." }
        checkpoint.receipts.firstOrNull { it.command.commandId == request.commandId }?.let {
            require(it.command == request) { "Command ID was already used for a different request." }
            return@command CommandOutcome(current, it.acceptedRevision, duplicate = true)
        }
        requireRevision(request.expectedRevision)
        require(checkpoint.receipts.size < WorkbookCheckpoint.RECEIPT_LIMIT) {
            "Workbook command receipt limit reached; no new command was accepted."
        }
        val candidate = when (request) {
            is WorkbookCommand.Rename -> current.copy(name = request.name)
            is WorkbookCommand.SetFavorite -> current.copy(favorite = request.value)
            is WorkbookCommand.SetChart -> {
                require(current.worksheets.any { it.id == request.worksheetId }) { "Command worksheet is missing." }
                current.copy(worksheets = current.worksheets.map {
                    if (it.id == request.worksheetId) it.copy(chart = request.options) else it
                })
            }
            is WorkbookCommand.AddWorksheet -> {
                // Stable worksheet identity is never recycled through undo or a
                // fresh command ID while its history/receipt is retained.
                require((listOf(current) + checkpoint.undo + checkpoint.redo).none { document ->
                    document.worksheets.any { it.id == request.worksheetId }
                } && checkpoint.receipts.none {
                    (it.command as? WorkbookCommand.AddWorksheet)?.worksheetId == request.worksheetId
                }) { "Worksheet identity is already in use or retained by history." }
                current.copy(worksheets = current.worksheets + WorksheetDef(
                    id = request.worksheetId,
                    name = request.name,
                    rowFieldId = null,
                    measureFieldId = null,
                    aggregation = Aggregation.SUM,
                    markType = MarkType.TABLE,
                    sortDescending = true,
                ))
            }
        }
        require(WorkbookDocument.fromJson(candidate.toJson()) == candidate) { "The command contains invalid or unsupported metadata." }
        val receipt = CommandReceipt(request, Math.addExact(current.revision, 1))
        val changed = candidate != current
        val saved = try {
            commit(checkpoint.copy(
                document = candidate,
                undo = if (changed) (checkpoint.undo + current).takeLast(WorkbookCheckpoint.HISTORY_LIMIT) else checkpoint.undo,
                redo = if (changed) emptyList() else checkpoint.redo,
                receipts = checkpoint.receipts + receipt,
            ))
        } catch (error: Throwable) {
            // If reconciliation proved this exact receipt was committed, report
            // that acceptance; do not treat acknowledgement loss as a second edit.
            checkpoint.receipts.firstOrNull { it.command == request }?.let {
                return@command CommandOutcome(checkpoint.document, it.acceptedRevision, duplicate = true)
            }
            throw error
        }
        CommandOutcome(saved, receipt.acceptedRevision, duplicate = false)
    }

    suspend fun undo(expectedRevision: Long): WorkbookDocument = command {
        val current = requireRevision(expectedRevision)
        val previous = checkpoint.undo.lastOrNull() ?: return@command current
        commit(checkpoint.copy(
            document = previous.copy(revision = current.revision),
            undo = checkpoint.undo.dropLast(1),
            redo = checkpoint.redo + current,
        ))
    }

    suspend fun redo(expectedRevision: Long): WorkbookDocument = command {
        val current = requireRevision(expectedRevision)
        val next = checkpoint.redo.lastOrNull() ?: return@command current
        commit(checkpoint.copy(
            document = next.copy(revision = current.revision),
            undo = checkpoint.undo + current,
            redo = checkpoint.redo.dropLast(1),
        ))
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
                    store.loadCheckpoint(current.id) to result
                }
            } catch (recoveryError: Throwable) {
                recoveryError.addSuppressed(error)
                throw recoveryError
            }
            if (reconciled != checkpoint) adopt(reconciled)
            if (recovery.retainedOperationDirectories.isNotEmpty()) {
                _state.value = _state.value.copy(recoveryMessages = recovery.messages)
                throw IllegalStateException("Snapshot recovery is pending. ${recovery.messages.joinToString(" ")}", error)
            }
            snapshotReadsBlocked = false
            throw error
        }
        adopt(withContext(Dispatchers.IO) { store.loadCheckpoint(saved.id) })
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
            store.loadCheckpoint(id) to result
        }
        if (recovery != null) {
            _state.value = _state.value.copy(recoveryMessages = recovery.messages)
            check(recovery.retainedOperationDirectories.isEmpty()) {
                "Snapshot recovery is pending. ${recovery.messages.joinToString(" ")}"
            }
        }
        if (loaded != checkpoint) adopt(loaded)
        snapshotReadsBlocked = false
        loaded.document
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

    private suspend fun commit(candidate: WorkbookCheckpoint): WorkbookDocument {
        val saved = try {
            withContext(Dispatchers.IO) { store.saveCheckpoint(candidate) }
        } catch (error: Throwable) {
            // A writer may have replaced the file before acknowledgement was lost.
            // Reconcile without replay; retained IDs make a typed retry idempotent.
            try {
                val disk = withContext(Dispatchers.IO) { store.loadCheckpoint(checkpoint.document.id) }
                if (disk != checkpoint) adopt(disk)
            } catch (recoveryError: Throwable) {
                snapshotReadsBlocked = true
                error.addSuppressed(recoveryError)
            }
            throw error
        }
        return adopt(saved)
    }

    private fun adopt(saved: WorkbookCheckpoint): WorkbookDocument {
        checkpoint = saved
        _state.value = _state.value.copy(document = saved.document, canUndo = saved.undo.isNotEmpty(), canRedo = saved.redo.isNotEmpty())
        return saved.document
    }
}
