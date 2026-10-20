package io.github.baitian6641.cloudydata.ui

import io.github.baitian6641.cloudydata.data.WorkbookConflictException
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.model.CommandOutcome
import io.github.baitian6641.cloudydata.model.WorkbookCommand
import io.github.baitian6641.cloudydata.model.WorkbookSummary
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class HomeFavoritePhase { SAVING, FAILED, CONFLICT }

data class HomeFavoriteAction(
    val request: WorkbookCommand.SetFavorite,
    val phase: HomeFavoritePhase,
    val message: String? = null,
    val acceptedRevision: Long? = null,
    val workbookName: String? = null,
)

data class HomeLibraryState(
    val workbooks: List<WorkbookSummary> = emptyList(),
    val loaded: Boolean = false,
    val loadError: String? = null,
    val favoriteActions: Map<String, HomeFavoriteAction> = emptyMap(),
)

/** A Home view's projection and pending intents; accepted content belongs to the coordinator. */
class HomeLibraryController(
    private val store: WorkbookStore,
    private val originWindowId: String = UUID.randomUUID().toString(),
) {
    private val stateMutex = Mutex()
    private val readMutex = Mutex()
    private val _state = MutableStateFlow(HomeLibraryState())
    val state: StateFlow<HomeLibraryState> = _state.asStateFlow()

    suspend fun reload() {
        try {
            refreshLibrary()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            stateMutex.withLock {
                _state.value = _state.value.copy(loaded = true,
                    loadError = error.message ?: "Could not read the local workbook library.")
            }
        }
    }

    suspend fun setFavorite(workbookId: String, expectedRevision: Long, value: Boolean): CommandOutcome? {
        val request = WorkbookCommand.SetFavorite(
            UUID.randomUUID().toString(), workbookId, originWindowId, expectedRevision, value,
        )
        val admitted = stateMutex.withLock {
            if (workbookId in _state.value.favoriteActions) false else {
                putAction(HomeFavoriteAction(request, HomeFavoritePhase.SAVING,
                    workbookName = _state.value.workbooks.firstOrNull { it.id == workbookId }?.name))
                true
            }
        }
        return if (admitted) dispatch(request) else null
    }

    suspend fun retryFavorite(workbookId: String): CommandOutcome? {
        val request = stateMutex.withLock {
            val action = _state.value.favoriteActions[workbookId]
            if (action?.phase != HomeFavoritePhase.FAILED) null else {
                putAction(action.copy(phase = HomeFavoritePhase.SAVING, message = null))
                action.request
            }
        } ?: return null
        return dispatch(request)
    }

    /** Dismisses only this view's proposal; it never undoes an accepted change. */
    suspend fun dismissFavorite(workbookId: String) {
        val dismissed = stateMutex.withLock {
            val action = _state.value.favoriteActions[workbookId]
            if (action == null || action.phase == HomeFavoritePhase.SAVING) false else {
                _state.value = _state.value.copy(favoriteActions = _state.value.favoriteActions - workbookId)
                true
            }
        }
        if (dismissed) reload()
    }

    private suspend fun dispatch(request: WorkbookCommand.SetFavorite): CommandOutcome? {
        val previousAction = stateMutex.withLock {
            _state.value.favoriteActions[request.workbookId]?.takeIf { it.request == request }
        }
        var accepted: CommandOutcome? = null
        try {
            val outcome = store.coordinators.open(request.workbookId).execute(request)
            accepted = outcome
            // Keep the row disabled until its actual library projection is current.
            refreshLibrary(request, outcome.document.revision)
            return outcome
        } catch (cancelled: CancellationException) {
            // Waiting acquires no authority. If the coordinator already committed,
            // a later Home instance reads reality; pending intent restoration is separate.
            throw cancelled
        } catch (error: Exception) {
            val acceptedRevision = accepted?.acceptedRevision ?: previousAction?.acceptedRevision
            val conflict = acceptedRevision == null && (error is WorkbookConflictException || error is IllegalArgumentException)
            val message = if (acceptedRevision != null) {
                "The favorite change was saved locally, but the library could not refresh. ${error.message.orEmpty()}"
            } else if (conflict) {
                "The favorite change was not applied. ${error.message.orEmpty()}"
            } else {
                "Could not confirm the favorite change. ${error.message.orEmpty()}"
            }
            stateMutex.withLock {
                putAction(HomeFavoriteAction(request,
                    if (conflict) HomeFavoritePhase.CONFLICT else HomeFavoritePhase.FAILED,
                    message, acceptedRevision, previousAction?.workbookName))
            }
            if (conflict) reload()
            return null
        }
    }

    // All callers release stateMutex before entering readMutex. A read stays
    // serialized through publication, so an older read cannot publish last.
    private suspend fun refreshLibrary(request: WorkbookCommand.SetFavorite? = null, minimumRevision: Long? = null) = readMutex.withLock {
        val workbooks = withContext(Dispatchers.IO) { store.list() }
        if (request != null) {
            check(workbooks.any { it.id == request.workbookId && it.revision >= checkNotNull(minimumRevision) }) {
                "The saved workbook is missing or unreadable in the local library."
            }
        }
        stateMutex.withLock {
            val actions = if (request != null && _state.value.favoriteActions[request.workbookId]?.request == request) {
                _state.value.favoriteActions - request.workbookId
            } else _state.value.favoriteActions
            _state.value = _state.value.copy(workbooks = workbooks, loaded = true,
                loadError = null, favoriteActions = actions)
        }
    }

    /** Caller holds stateMutex. */
    private fun putAction(action: HomeFavoriteAction) {
        _state.value = _state.value.copy(favoriteActions = _state.value.favoriteActions + (action.request.workbookId to action))
    }
}
