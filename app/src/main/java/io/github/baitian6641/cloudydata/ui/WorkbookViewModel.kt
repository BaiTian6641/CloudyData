package io.github.baitian6641.cloudydata.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.PendingCsvImport
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.data.WorkbookCoordinator
import io.github.baitian6641.cloudydata.data.WorkbookConflictException
import io.github.baitian6641.cloudydata.data.CoordinatedWorkbook
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.MarkFilters
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsQuery
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsResult
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.engine.WorksheetReferences
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.StackMode
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface WorksheetQueryState {
    data object Empty : WorksheetQueryState
    data class Loading(val previous: WorksheetResult?) : WorksheetQueryState
    data class Ready(val result: WorksheetResult) : WorksheetQueryState
    data class Failed(val message: String, val previous: WorksheetResult?) : WorksheetQueryState
    data class Cancelled(val previous: WorksheetResult?) : WorksheetQueryState
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Inspecting(val displayName: String) : ImportUiState
    data class Ready(val pending: PendingCsvImport) : ImportUiState
    data class Applying(val pending: PendingCsvImport) : ImportUiState
    data class Failed(val message: String, val pending: PendingCsvImport?) : ImportUiState
}

sealed interface SaveState {
    data object Saved : SaveState
    data object Saving : SaveState
    data class Failed(val message: String) : SaveState
    data class Conflict(val message: String) : SaveState
}

sealed interface ViewDataState {
    data object Idle : ViewDataState
    data object Loading : ViewDataState
    data class Ready(val result: UnderlyingRowsResult) : ViewDataState
    data class Failed(val message: String) : ViewDataState
}

data class WorkbookUiState(
    val loading: Boolean = true,
    val document: WorkbookDocument? = null,
    val loadError: String? = null,
    val query: WorksheetQueryState = WorksheetQueryState.Empty,
    val import: ImportUiState = ImportUiState.Idle,
    val save: SaveState = SaveState.Saved,
    val viewData: ViewDataState = ViewDataState.Idle,
    val engineVersion: String? = null,
    val engineError: String? = null,
    val recoveryMessages: List<String> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

class WorkbookViewModel(
    application: Application,
    private val store: WorkbookStore,
    private val workbookId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(WorkbookUiState())
    val state: StateFlow<WorkbookUiState> = _state.asStateFlow()

    private val importService = CsvImportService(application, store)
    private val originWindowId = UUID.randomUUID().toString()
    private var coordinator: WorkbookCoordinator? = null
    private var coordinatorObserver: Job? = null
    private var activeWorksheetId: String? = null
    private var retryEdit: (() -> Unit)? = null
    private var failedEditId: Any? = null
    private var retryFailureMessage: String? = null
    private val commandMutex = Mutex()
    private val ownRevisions = linkedMapOf<Long, Long>()
    private var engine: DuckDbEngine? = null
    private var queryGeneration = 0L
    private var viewDataGeneration = 0L

    init {
        viewModelScope.launch {
            val version = withContext(Dispatchers.IO) {
                runCatching { DuckDbEngine.version() }
            }
            _state.update {
                it.copy(
                    engineVersion = version.getOrNull(),
                    engineError = version.exceptionOrNull()?.message,
                )
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            try {
                val shared = store.coordinators.open(workbookId)
                if (coordinator != null) shared.reload()
                coordinator = shared
                retryEdit = null
                failedEditId = null
                retryFailureMessage = null
                _state.update { it.copy(save = SaveState.Saved) }
                coordinatorObserver?.cancel()
                acceptDocument(shared.state.value)
                coordinatorObserver = viewModelScope.launch {
                    shared.state.collect { acceptDocument(it) }
                }
                val error = ensureEngine()
                if (error != null) {
                    _state.update { it.copy(engineError = error) }
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadError = error.message ?: "Could not open the workbook.",
                    )
                }
            }
        }
    }

    fun inspectCsv(uri: Uri) {
        val document = _state.value.document ?: return
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected CSV"
        _state.update { it.copy(import = ImportUiState.Inspecting(name)) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { importService.stageAndInspect(document, uri) }
            }.onSuccess { pending ->
                _state.update { it.copy(import = ImportUiState.Ready(pending)) }
            }.onFailure { error ->
                _state.update {
                    it.copy(import = ImportUiState.Failed(error.message ?: "Could not inspect the CSV file.", null))
                }
            }
        }
    }

    fun applyImport() {
        val document = _state.value.document ?: return
        val pending = when (val import = _state.value.import) {
            is ImportUiState.Ready -> import.pending
            is ImportUiState.Failed -> import.pending
            else -> null
        } ?: return
        val shared = coordinator ?: return
        viewModelScope.launch {
            commandMutex.withLock {
                val currentPending = when (val current = _state.value.import) {
                    is ImportUiState.Ready -> current.pending
                    is ImportUiState.Failed -> current.pending
                    else -> null
                }
                // Applying may wait behind an edit. Do not resurrect a discarded,
                // replaced or already-completed staged import when it dequeues.
                if (currentPending?.operationId != pending.operationId) return@withLock
                val blocked = refuseWhileSaveFailed()
                if (blocked != null) {
                    _state.update { it.copy(import = ImportUiState.Failed("This import was not applied. $blocked", pending)) }
                    return@withLock
                }
                _state.update { it.copy(import = ImportUiState.Applying(pending)) }
                runCatching {
                    shared.importSnapshot(document.revision) { current -> importService.apply(current, pending) }
                }.onSuccess { saved ->
                    acceptDocument(shared.state.value)
                    _state.update { it.copy(import = ImportUiState.Idle, save = SaveState.Saved) }
                    autoPlace(snapshotColumns = saved.snapshot?.columns.orEmpty())
                }.onFailure { error ->
                    acceptDocument(shared.state.value)
                    val published = shared.state.value.document.snapshot?.id != document.snapshot?.id
                    _state.update {
                        it.copy(import = ImportUiState.Failed(
                            (if (published) "The imported snapshot was saved locally, but finalization failed. " else "") +
                                (error.message ?: "Could not complete the import."),
                            if (published) null else pending,
                        ))
                    }
                }
            }
        }
    }

    fun discardImport() {
        val pending = when (val import = _state.value.import) {
            is ImportUiState.Ready -> import.pending
            is ImportUiState.Failed -> import.pending
            is ImportUiState.Applying -> import.pending
            else -> null
        }
        if (pending != null) importService.discard(pending)
        _state.update { it.copy(import = ImportUiState.Idle) }
    }

    fun dismissImportError() {
        _state.update { it.copy(import = ImportUiState.Idle) }
    }

    fun setRowField(field: FieldDef) {
        updateActiveSheet { it.copy(rowFieldId = field.id, filter = null) }
    }

    fun setMeasureField(field: FieldDef) {
        updateActiveSheet {
            it.copy(
                measureFieldId = field.id,
                markType = if (it.markType == MarkType.TABLE) MarkType.BAR else it.markType,
            )
        }
    }

    fun clearRowField() {
        updateActiveSheet { it.copy(rowFieldId = null, filter = null) }
    }

    fun clearMeasureField() {
        updateActiveSheet { sheet ->
            sheet.copy(
                measureFieldId = null,
                seriesFieldId = null,
                secondaryMeasureFieldId = null,
                filter = sheet.filter?.takeIf { it.seriesFieldId == null },
            )
        }
    }

    fun setSeriesField(field: FieldDef) {
        updateActiveSheet {
            it.copy(seriesFieldId = field.id, secondaryMeasureFieldId = null, filter = null)
        }
    }

    fun setSecondaryMeasureField(field: FieldDef) {
        updateActiveSheet {
            it.copy(
                secondaryMeasureFieldId = field.id,
                secondaryAggregation = it.aggregation,
                seriesFieldId = null,
                filter = null,
            )
        }
    }

    fun setSecondaryAggregation(aggregation: Aggregation) {
        updateActiveSheet { it.copy(secondaryAggregation = aggregation) }
    }

    fun clearSecondaryMeasureField() {
        updateActiveSheet { it.copy(secondaryMeasureFieldId = null, filter = null) }
    }

    fun clearSeriesField() {
        updateActiveSheet { it.copy(seriesFieldId = null, filter = null) }
    }

    fun setAggregation(aggregation: Aggregation) {
        updateActiveSheet { sheet ->
            sheet.copy(
                aggregation = aggregation,
                chart = if (aggregation == Aggregation.SUM || aggregation == Aggregation.COUNT) {
                    sheet.chart
                } else {
                    // A stacked total is only meaningful for additive measures.
                    sheet.chart.copy(
                        barStackMode = StackMode.NONE,
                        areaStackMode = StackMode.NONE,
                    )
                },
            )
        }
    }

    fun setMarkType(markType: MarkType) {
        updateActiveSheet { sheet ->
            if (markType == MarkType.LINE || markType == MarkType.BAR || markType == MarkType.AREA) {
                sheet.copy(
                    markType = markType,
                    secondaryMeasureFieldId = if (markType == MarkType.BAR) {
                        sheet.secondaryMeasureFieldId
                    } else {
                        null
                    },
                    chart = if (markType == MarkType.BAR || markType == MarkType.AREA) {
                        sheet.chart.copy(logScale = false)
                    } else {
                        sheet.chart
                    },
                )
            } else {
                // Color is a Line/Bar/Area channel in this build; switching away
                // clears it instead of silently querying an unused column. A
                // row-only filter remains meaningful; a pair filter does not.
                sheet.copy(
                    markType = markType,
                    seriesFieldId = null,
                    secondaryMeasureFieldId = null,
                    chart = sheet.chart.copy(logScale = false),
                    filter = sheet.filter?.takeIf { it.seriesFieldId == null },
                )
            }
        }
    }

    fun setSortDescending(descending: Boolean) {
        updateActiveSheet { it.copy(sortDescending = descending) }
    }

    fun keepSelection(rows: Set<Int>) = applySelectionFilter(rows, FilterMode.KEEP)

    fun excludeSelection(rows: Set<Int>) = applySelectionFilter(rows, FilterMode.EXCLUDE)

    fun clearFilter() {
        updateActiveSheet { it.copy(filter = null) }
    }

    private fun applySelectionFilter(rows: Set<Int>, mode: FilterMode) {
        val sheet = _state.value.document?.activeWorksheet ?: return
        if (sheet.filter != null || rows.isEmpty()) return
        val result = (_state.value.query as? WorksheetQueryState.Ready)?.result ?: return
        val snapshot = _state.value.document?.snapshot ?: return
        if (runCatching { WorksheetReferences.requireCurrentResult(snapshot, sheet, result) }.isFailure) return
        val filter = MarkFilters.fromResult(sheet, result, rows, mode) ?: return
        updateActiveSheet { it.copy(filter = filter) }
    }

    fun updateChartSettings(options: ChartOptions) {
        val worksheetId = _state.value.document?.activeWorksheet?.id ?: return
        submitTyped { id, revision ->
            WorkbookCommand.SetChart(id, workbookId, originWindowId, revision, worksheetId, options)
        }
    }

    fun undo() {
        val revision = _state.value.document?.revision ?: return
        val shared = coordinator ?: return
        runEdit(revision, { shared.undo(it) })
    }

    fun redo() {
        val revision = _state.value.document?.revision ?: return
        val shared = coordinator ?: return
        runEdit(revision, { shared.redo(it) })
    }

    fun retryQuery() {
        refreshQuery()
    }

    fun cancelQuery() {
        if (_state.value.query !is WorksheetQueryState.Loading) return
        queryGeneration++
        engine?.interrupt()
        val previous = (_state.value.query as? WorksheetQueryState.Loading)?.previous
        _state.update { it.copy(query = WorksheetQueryState.Cancelled(previous)) }
    }

    fun openViewData(sheetId: String, selectedRows: Set<Int>) {
        val document = _state.value.document ?: return
        val snapshot = document.snapshot ?: return
        val sheet = document.worksheets.firstOrNull { it.id == sheetId } ?: return
        // The accepted result belongs to the active sheet; refuse a tab/query
        // mismatch rather than reading another sheet's rows under this UI.
        if (document.activeWorksheetId != sheetId) return
        if (selectedRows.isEmpty()) return
        val accepted = (_state.value.query as? WorksheetQueryState.Ready)?.result ?: return
        val referenceError = runCatching {
            WorksheetReferences.requireCurrentResult(snapshot, sheet, accepted)
        }.exceptionOrNull()
        if (referenceError != null) {
            viewDataGeneration++
            _state.update { it.copy(viewData = ViewDataState.Failed(referenceError.message ?: "The selected result is stale.")) }
            return
        }
        val generation = ++viewDataGeneration
        _state.update { it.copy(viewData = ViewDataState.Loading) }
        viewModelScope.launch {
            val engineError = ensureEngine()
            if (engineError != null) {
                if (generation == viewDataGeneration) {
                    _state.update { it.copy(viewData = ViewDataState.Failed(engineError)) }
                }
                return@launch
            }
            val activeEngine = engine ?: return@launch
            val path = runCatching {
                store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
            }.getOrElse { error ->
                if (generation == viewDataGeneration) {
                    _state.update {
                        it.copy(viewData = ViewDataState.Failed(error.message ?: "Snapshot path is invalid."))
                    }
                }
                return@launch
            }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    coordinator!!.readSnapshot(snapshot.id) { UnderlyingRowsQuery(activeEngine).query(
                        snapshot = snapshot,
                        sheet = sheet,
                        worksheetResult = accepted,
                        selectedRows = selectedRows,
                        snapshotPath = path,
                    ) }
                }
            }
            if (generation != viewDataGeneration) return@launch
            _state.update {
                it.copy(
                    viewData = result.fold(
                        onSuccess = { rows ->
                            if (rows == null) {
                                ViewDataState.Failed("The selected marks do not identify source rows.")
                            } else {
                                ViewDataState.Ready(rows)
                            }
                        },
                        onFailure = { error ->
                            ViewDataState.Failed(error.message ?: "Could not read the underlying rows.")
                        },
                    ),
                )
            }
        }
    }

    fun closeViewData() {
        viewDataGeneration++
        _state.update { it.copy(viewData = ViewDataState.Idle) }
    }

    fun saveNow() {
        val retry = retryEdit
        if (retry != null) retry() else if (_state.value.save is SaveState.Conflict) load()
        // Accepted commands are already persisted; an extra Save creates no revision.
    }

    fun renameWorkbook(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        submitTyped { id, revision ->
            WorkbookCommand.Rename(id, workbookId, originWindowId, revision, trimmed)
        }
    }

    fun addWorksheet() {
        if (_state.value.document == null) return
        val shared = coordinator ?: return
        val sheetId = UUID.randomUUID().toString()
        submitTyped(afterAccepted = {
            // A duplicate can return a newer document in which this addition
            // has already been undone. Do not recreate or activate that sheet.
            if (shared.state.value.document.worksheets.any { it.id == sheetId }) activeWorksheetId = sheetId
        }) { id, revision ->
            val sheets = shared.state.value.document.worksheets
            val names = sheets.map { it.name }.toSet()
            var number = 1
            while ("Sheet $number" in names) number++
            WorkbookCommand.AddWorksheet(id, workbookId, originWindowId, revision, sheetId, "Sheet $number")
        }
    }

    fun selectWorksheet(id: String) {
        val document = _state.value.document ?: return
        if (document.activeWorksheetId == id) return
        if (document.worksheets.none { it.id == id }) return
        activeWorksheetId = id
        _state.update { it.copy(document = document.copy(activeWorksheetId = id)) }
        refreshQuery(keepPrevious = false)
    }

    private fun autoPlace(snapshotColumns: List<FieldDef>) {
        val current = _state.value.document ?: return
        val sheet = current.activeWorksheet ?: return
        if (sheet.rowFieldId != null || sheet.measureFieldId != null) return
        val dimension = snapshotColumns.firstOrNull { it.role == io.github.baitian6641.cloudydata.model.FieldRole.DIMENSION }
        val measure = snapshotColumns.firstOrNull { it.role == io.github.baitian6641.cloudydata.model.FieldRole.MEASURE }
        mutateDocument { document ->
            document.copy(
                worksheets = document.worksheets.map {
                    if (it.id != sheet.id) it else it.copy(
                        rowFieldId = dimension?.id,
                        measureFieldId = measure?.id,
                        markType = if (measure != null) MarkType.BAR else MarkType.TABLE,
                    )
                },
            )
        }
    }

    private fun updateActiveSheet(transform: (WorksheetDef) -> WorksheetDef) {
        mutateDocument { document ->
            val active = document.activeWorksheet ?: return@mutateDocument document
            document.copy(
                worksheets = document.worksheets.map { if (it.id == active.id) transform(it) else it },
            )
        }
    }

    private fun mutateDocument(transform: (WorkbookDocument) -> WorkbookDocument) {
        val visible = _state.value.document ?: return
        val shared = coordinator ?: return
        runEdit(
            expectedRevision = visible.revision,
            operation = { revision ->
                shared.edit(revision) { current ->
                    val next = transform(current.copy(activeWorksheetId = visible.activeWorksheetId))
                    // Active sheet/focus are local to this view. Keep the document's reopen default.
                    next.copy(activeWorksheetId = current.activeWorksheetId)
                }
            },
        )
    }

    private fun submitTyped(
        afterAccepted: (WorkbookDocument) -> Unit = {},
        factory: (String, Long) -> WorkbookCommand,
    ) {
        val revision = _state.value.document?.revision ?: return
        val shared = coordinator ?: return
        val id = UUID.randomUUID().toString()
        var request: WorkbookCommand? = null
        var acceptedEdge: Pair<Long, Long>? = null
        runEdit(
            expectedRevision = revision,
            operation = { effectiveRevision ->
                // Freeze the complete request at first dispatch; retries retain its
                // ID, payload, window and expected revision across lost acknowledgements.
                val frozen = request ?: factory(id, effectiveRevision).also { request = it }
                val outcome = shared.execute(frozen)
                acceptedEdge = frozen.expectedRevision to outcome.acceptedRevision
                outcome.document
            },
            afterAccepted = afterAccepted,
            revisionEdge = { acceptedEdge },
        )
    }

    private fun runEdit(
        expectedRevision: Long,
        operation: suspend (Long) -> WorkbookDocument,
        afterAccepted: (WorkbookDocument) -> Unit = {},
        revisionEdge: (() -> Pair<Long, Long>?)? = null,
        retryOf: Any? = null,
    ) {
        val editId = retryOf ?: Any()
        viewModelScope.launch {
            commandMutex.withLock {
                // Keep the first failed intent visible. A later queued action
                // cannot overwrite its retry slot or claim all work was saved.
                if (retryOf != null && retryOf !== failedEditId) return@withLock
                if (failedEditId != null && retryOf !== failedEditId) {
                    refuseWhileSaveFailed()
                    return@withLock
                }
                retryEdit = null
                failedEditId = null
                retryFailureMessage = null
                _state.update { it.copy(save = SaveState.Saving) }
                // Rebase only through this view's own preceding accepted commands.
                // Any intervening command from another view still produces a conflict.
                var expected = expectedRevision
                while (ownRevisions.containsKey(expected)) expected = ownRevisions.getValue(expected)
                runCatching { operation(expected) }.onSuccess { saved ->
                    val edge = revisionEdge?.invoke() ?: if (revisionEdge == null && saved.revision != expected) expected to saved.revision else null
                    if (edge != null && edge.second > edge.first) {
                        ownRevisions[edge.first] = edge.second
                        if (ownRevisions.size > 100) ownRevisions.remove(ownRevisions.keys.first())
                    }
                    afterAccepted(saved)
                    coordinator?.state?.value?.let(::acceptDocument)
                    _state.update { it.copy(save = SaveState.Saved) }
                }.onFailure { error ->
                    if (error is WorkbookConflictException || error is IllegalArgumentException) {
                        coordinator?.state?.value?.let(::acceptDocument)
                        _state.update { it.copy(save = SaveState.Conflict(error.message.orEmpty())) }
                    } else {
                        val message = error.message ?: "Could not save the workbook."
                        failedEditId = editId
                        retryFailureMessage = message
                        retryEdit = { runEdit(expectedRevision, operation, afterAccepted, revisionEdge, retryOf = editId) }
                        _state.update { it.copy(save = SaveState.Failed(message)) }
                    }
                }
            }
        }
    }

    /** Called under commandMutex; later refused actions are never automatically replayed. */
    private fun refuseWhileSaveFailed(): String? {
        if (failedEditId == null) return null
        val message = "${retryFailureMessage.orEmpty()} A later change was not applied. " +
            "Retry the previous change with Save before applying the later change again."
        _state.update { it.copy(save = SaveState.Failed(message)) }
        return message
    }

    private fun acceptDocument(shared: CoordinatedWorkbook) {
        val previous = _state.value.document
        val canonical = shared.document
        val active = activeWorksheetId?.takeIf { id -> canonical.worksheets.any { it.id == id } }
            ?: canonical.activeWorksheetId
        activeWorksheetId = active
        val visible = canonical.copy(activeWorksheetId = active)
        _state.update {
            it.copy(
                loading = false,
                document = visible,
                loadError = null,
                canUndo = shared.canUndo,
                canRedo = shared.canRedo,
                recoveryMessages = shared.recoveryMessages,
            )
        }
        // Presentation edits and renames do not change query semantics.
        if (previous == null || previous.snapshot != visible.snapshot ||
            previous.activeWorksheet?.copy(chart = ChartOptions()) != visible.activeWorksheet?.copy(chart = ChartOptions())
        ) {
            refreshQuery(
                keepPrevious = previous?.activeWorksheetId == visible.activeWorksheetId &&
                    previous?.snapshot?.id == visible.snapshot?.id,
            )
        }
    }

    private fun refreshQuery(keepPrevious: Boolean = true) {
        if (_state.value.viewData !is ViewDataState.Idle) {
            viewDataGeneration++
            _state.update { it.copy(viewData = ViewDataState.Idle) }
        }
        val generation = ++queryGeneration
        val document = _state.value.document
        val snapshot = document?.snapshot
        val sheet = document?.activeWorksheet
        if (document == null || snapshot == null || sheet == null) {
            _state.update { it.copy(query = WorksheetQueryState.Empty) }
            return
        }
        val previous = if (keepPrevious) {
            (_state.value.query as? WorksheetQueryState.Ready)?.result
                ?: (_state.value.query as? WorksheetQueryState.Loading)?.previous
                ?: (_state.value.query as? WorksheetQueryState.Failed)?.previous
                ?: (_state.value.query as? WorksheetQueryState.Cancelled)?.previous
        } else {
            null
        }
        _state.update { it.copy(query = WorksheetQueryState.Loading(previous)) }
        viewModelScope.launch {
            val engineError = ensureEngine()
            if (engineError != null) {
                if (generation == queryGeneration) {
                    _state.update { it.copy(query = WorksheetQueryState.Failed(engineError, previous)) }
                }
                return@launch
            }
            val activeEngine = engine ?: return@launch
            val snapshotPath = runCatching {
                store.resolveInsideWorkbook(document.id, snapshot.snapshotFile).absolutePath
            }.getOrElse { error ->
                if (generation == queryGeneration) _state.update {
                    it.copy(query = WorksheetQueryState.Failed(error.message ?: "Snapshot path is invalid.", previous))
                }
                return@launch
            }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    coordinator!!.readSnapshot(snapshot.id) {
                        WorksheetQuery(activeEngine).query(snapshot, sheet, snapshotPath)
                    }
                }
            }
            if (generation != queryGeneration) return@launch
            _state.update {
                it.copy(
                    query = result.fold(
                        onSuccess = { value -> WorksheetQueryState.Ready(value) },
                        onFailure = { error ->
                            WorksheetQueryState.Failed(error.message ?: "The query failed.", previous)
                        },
                    ),
                )
            }
        }
    }

    @Synchronized
    private fun ensureEngine(): String? {
        if (engine != null) return null
        return runCatching {
            engine = DuckDbEngine.open(":memory:", memoryLimitMb = 256, threads = 2)
            null
        }.getOrElse { error ->
            error.message ?: "Could not start the local analytical engine."
        }
    }

    override fun onCleared() {
        engine?.close()
        engine = null
    }
}

class WorkbookViewModelFactory(
    private val application: Application,
    private val store: WorkbookStore,
    private val workbookId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WorkbookViewModel(application, store, workbookId) as T
    }
}
