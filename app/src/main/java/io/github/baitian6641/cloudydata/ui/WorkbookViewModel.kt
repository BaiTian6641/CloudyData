package io.github.baitian6641.cloudydata.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.baitian6641.cloudydata.data.CsvImportService
import io.github.baitian6641.cloudydata.data.PendingCsvImport
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.engine.DuckDbEngine
import io.github.baitian6641.cloudydata.engine.WorksheetQuery
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorkbookHistory
import io.github.baitian6641.cloudydata.model.WorksheetDef
import java.util.UUID
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
}

data class WorkbookUiState(
    val loading: Boolean = true,
    val document: WorkbookDocument? = null,
    val loadError: String? = null,
    val query: WorksheetQueryState = WorksheetQueryState.Empty,
    val import: ImportUiState = ImportUiState.Idle,
    val save: SaveState = SaveState.Saved,
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
    private val saveMutex = Mutex()
    private var engine: DuckDbEngine? = null
    private var history: WorkbookHistory? = null
    private var queryGeneration = 0L

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
                val recovery = withContext(Dispatchers.IO) { importService.reconcile(workbookId) }
                val document = withContext(Dispatchers.IO) { store.load(workbookId) }
                history = WorkbookHistory(document)
                _state.update {
                    it.copy(
                        loading = false,
                        document = document,
                        loadError = null,
                        save = SaveState.Saved,
                        recoveryMessages = recovery.messages,
                        canUndo = false,
                        canRedo = false,
                    )
                }
                val error = ensureEngine()
                if (error != null) {
                    _state.update { it.copy(engineError = error) }
                }
                refreshQuery()
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
        _state.update { it.copy(import = ImportUiState.Applying(pending)) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { importService.apply(document, pending) }
            }.onSuccess { saved ->
                // Import/replace is a source-side effect; it is not replayed by
                // undo. Start a fresh history for the published snapshot.
                history = WorkbookHistory(saved)
                _state.update {
                    it.copy(
                        document = saved,
                        import = ImportUiState.Idle,
                        save = SaveState.Saved,
                        canUndo = false,
                        canRedo = false,
                    )
                }
                autoPlace(snapshotColumns = saved.snapshot?.columns.orEmpty())
                refreshQuery()
            }.onFailure { error ->
                _state.update {
                    it.copy(import = ImportUiState.Failed(error.message ?: "Could not complete the import.", pending))
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
        updateActiveSheet { it.copy(rowFieldId = field.id) }
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
        updateActiveSheet { it.copy(rowFieldId = null) }
    }

    fun clearMeasureField() {
        updateActiveSheet { it.copy(measureFieldId = null) }
    }

    fun setSeriesField(field: FieldDef) {
        updateActiveSheet { it.copy(seriesFieldId = field.id) }
    }

    fun clearSeriesField() {
        updateActiveSheet { it.copy(seriesFieldId = null) }
    }

    fun setAggregation(aggregation: Aggregation) {
        updateActiveSheet { it.copy(aggregation = aggregation) }
    }

    fun setMarkType(markType: MarkType) {
        updateActiveSheet { sheet ->
            if (markType == MarkType.LINE) {
                sheet.copy(markType = markType)
            } else {
                // Color is a Line series channel in this build; switching away
                // clears it instead of silently querying an unused column.
                sheet.copy(
                    markType = markType,
                    seriesFieldId = null,
                    chart = sheet.chart.copy(logScale = false),
                )
            }
        }
    }

    fun setSortDescending(descending: Boolean) {
        updateActiveSheet { it.copy(sortDescending = descending) }
    }

    fun updateChartSettings(options: ChartOptions) {
        // Presentation-only change: persist and recompose without rerunning SQL.
        mutateDocument { document ->
            val active = document.activeWorksheet ?: return@mutateDocument document
            document.copy(
                worksheets = document.worksheets.map {
                    if (it.id != active.id) it else it.copy(chart = options)
                },
            )
        }
    }

    fun undo() {
        val restored = history?.undo() ?: return
        _state.update {
            it.copy(document = restored, canUndo = history?.canUndo == true, canRedo = history?.canRedo == true)
        }
        persistLatest()
        refreshQuery()
    }

    fun redo() {
        val restored = history?.redo() ?: return
        _state.update {
            it.copy(document = restored, canUndo = history?.canUndo == true, canRedo = history?.canRedo == true)
        }
        persistLatest()
        refreshQuery()
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

    fun saveNow() {
        persistLatest()
    }

    fun renameWorkbook(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutateDocument { it.copy(name = trimmed) }
    }

    fun addWorksheet() {
        val document = _state.value.document ?: return
        val sheet = WorksheetDef(
            id = UUID.randomUUID().toString(),
            name = "Sheet ${document.worksheets.size + 1}",
            rowFieldId = null,
            measureFieldId = null,
            aggregation = Aggregation.SUM,
            markType = MarkType.TABLE,
            sortDescending = true,
        )
        mutateDocument {
            it.copy(worksheets = it.worksheets + sheet, activeWorksheetId = sheet.id)
        }
        refreshQuery()
    }

    fun selectWorksheet(id: String) {
        val document = _state.value.document ?: return
        if (document.activeWorksheetId == id) return
        // Active-tab state is not an undoable analytical command.
        val updated = history?.replaceCurrent { it.copy(activeWorksheetId = id) }
            ?: document.copy(activeWorksheetId = id)
        _state.update { it.copy(document = updated) }
        persistLatest()
        refreshQuery()
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
        refreshQuery()
    }

    private fun mutateDocument(transform: (WorkbookDocument) -> WorkbookDocument) {
        val updated = history?.apply(transform) ?: return
        _state.update {
            it.copy(
                document = updated,
                canUndo = history?.canUndo == true,
                canRedo = history?.canRedo == true,
            )
        }
        persistLatest()
    }

    private fun persistLatest() {
        viewModelScope.launch {
            saveMutex.withLock {
                while (true) {
                    val current = _state.value.document ?: return@withLock
                    _state.update { it.copy(save = SaveState.Saving) }
                    val saved = runCatching {
                        withContext(Dispatchers.IO) { store.save(current) }
                    }.getOrElse { error ->
                        _state.update {
                            it.copy(save = SaveState.Failed(error.message ?: "Could not save the workbook."))
                        }
                        return@withLock
                    }
                    val latest = _state.value.document
                    _state.update {
                        if (it.document?.revision == current.revision) {
                            history?.adoptSavedRevision(saved)
                            it.copy(document = saved, save = SaveState.Saved)
                        } else {
                            it.copy(save = SaveState.Saving)
                        }
                    }
                    if (latest?.revision == current.revision) return@withLock
                }
            }
        }
    }

    private fun refreshQuery() {
        val document = _state.value.document
        val snapshot = document?.snapshot
        val sheet = document?.activeWorksheet
        if (document == null || snapshot == null || sheet == null) {
            _state.update { it.copy(query = WorksheetQueryState.Empty) }
            return
        }
        val generation = ++queryGeneration
        val previous = (_state.value.query as? WorksheetQueryState.Ready)?.result
            ?: (_state.value.query as? WorksheetQueryState.Loading)?.previous
            ?: (_state.value.query as? WorksheetQueryState.Failed)?.previous
            ?: (_state.value.query as? WorksheetQueryState.Cancelled)?.previous
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
                _state.update {
                    it.copy(query = WorksheetQueryState.Failed(error.message ?: "Snapshot path is invalid.", previous))
                }
                return@launch
            }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    WorksheetQuery(activeEngine).query(snapshot, sheet, snapshotPath)
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
