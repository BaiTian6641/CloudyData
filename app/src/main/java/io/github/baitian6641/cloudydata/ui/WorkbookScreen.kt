package io.github.baitian6641.cloudydata.ui

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.design.CloudyBadge
import io.github.baitian6641.cloudydata.design.CloudyBadgeTone
import io.github.baitian6641.cloudydata.design.CloudyDivider
import io.github.baitian6641.cloudydata.design.CloudyIconButton
import io.github.baitian6641.cloudydata.design.CloudyPrimaryButton
import io.github.baitian6641.cloudydata.design.CloudySecondaryButton
import io.github.baitian6641.cloudydata.design.CloudyTextField
import io.github.baitian6641.cloudydata.design.CloudyToolbar
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.RowSelectionReducer
import io.github.baitian6641.cloudydata.model.RowSelectionState
import io.github.baitian6641.cloudydata.model.SelectionMode
import io.github.baitian6641.cloudydata.model.WorksheetDef

@Composable
fun WorkbookScreen(
    store: WorkbookStore,
    workbookId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: WorkbookViewModel = viewModel(
        key = workbookId,
        factory = WorkbookViewModelFactory(application, store, workbookId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current

    var selectedTab by rememberSaveable(workbookId) { mutableStateOf<String?>(null) }
    var selectedFieldId by rememberSaveable(workbookId) { mutableStateOf<String?>(null) }
    var rowSelection by remember(workbookId) { mutableStateOf(RowSelectionState()) }
    var showInspector by rememberSaveable(workbookId) { mutableStateOf(false) }
    var showFieldsDialog by rememberSaveable(workbookId) { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable(workbookId) { mutableStateOf(false) }
    var showPalette by rememberSaveable(workbookId) { mutableStateOf(false) }
    var showChartSettings by rememberSaveable(workbookId) { mutableStateOf(false) }
    val rootFocusRequester = remember { FocusRequester() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.inspectCsv(uri)
    }

    val document = state.document
    val snapshot = document?.snapshot
    val resolvedTab = selectedTab?.takeIf { tab ->
        tab == DATA_TAB || document?.worksheets?.any { it.id == tab } == true
    } ?: if (snapshot != null) document?.activeWorksheetId ?: DATA_TAB else DATA_TAB

    LaunchedEffect(resolvedTab) {
        rowSelection = RowSelectionState()
    }
    LaunchedEffect(snapshot?.id) {
        if (snapshot != null && selectedTab == null) {
            selectedTab = document?.activeWorksheetId
        }
    }
    LaunchedEffect(document?.activeWorksheetId) {
        val active = document?.activeWorksheetId
        if (selectedTab != DATA_TAB && active != null && selectedTab != active) {
            selectedTab = active
        }
    }
    LaunchedEffect(state.import) {
        if (state.import is ImportUiState.Idle && snapshot != null) {
            selectedTab = document?.activeWorksheetId
        }
    }

    LaunchedEffect(Unit) {
        runCatching { rootFocusRequester.requestFocus() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                    when {
                        event.isShiftPressed && event.key == Key.P -> {
                            showPalette = true
                            true
                        }
                        !event.isShiftPressed && event.key == Key.S -> {
                            viewModel.saveNow()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        val windowWidth = maxWidth
        val compact = windowWidth < 720.dp
        val showSideInspector = windowWidth >= 1100.dp && showInspector
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Opening workbook…", color = colors.textSecondary)
                }
            }
            return@BoxWithConstraints
        }
        if (document == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        "This workbook could not be opened",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.loadError ?: "The local workbook file is missing or invalid.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CloudySecondaryButton("Back to Home", onBack)
                        CloudyPrimaryButton("Retry", { viewModel.load() })
                    }
                }
            }
            return@BoxWithConstraints
        }

        Column(Modifier.fillMaxSize()) {
            WorkbookToolbar(
                document = document,
                state = state,
                compact = compact,
                onBack = onBack,
                onRename = { showRenameDialog = true },
                onImport = {
                    // Providers disagree about CSV MIME types (text/csv,
                    // text/comma-separated-values, application/octet-stream).
                    // Accept any document here and validate it as delimited text
                    // in the importer rather than silently hiding valid files.
                    importLauncher.launch(arrayOf("*/*"))
                },
                onAddSheet = viewModel::addWorksheet,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onToggleInspector = { showInspector = !showInspector },
                inspectorOpen = showInspector,
            )
            CloudyDivider()
            WorkbookTabStrip(
                document = document,
                activeTab = resolvedTab,
                onSelect = { tab ->
                    selectedTab = tab
                    if (tab != DATA_TAB) viewModel.selectWorksheet(tab)
                },
                onAddSheet = viewModel::addWorksheet,
                onRemoveWorksheet = viewModel::removeWorksheet,
            )
            CloudyDivider()
            if (state.save is SaveState.Failed) {
                SaveFailureBanner(
                    message = (state.save as SaveState.Failed).message,
                    onRetry = viewModel::saveNow,
                )
            }
            if (state.save is SaveState.Conflict) {
                SaveFailureBanner(
                    message = (state.save as SaveState.Conflict).message,
                    onRetry = viewModel::load,
                )
            }
            if (state.engineError != null) {
                SaveFailureBanner(
                    message = "Local engine unavailable: ${state.engineError}",
                    onRetry = viewModel::retryQuery,
                )
            }
            if (state.recoveryMessages.isNotEmpty()) {
                RecoveryBanner(state.recoveryMessages.joinToString(" "))
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (resolvedTab != DATA_TAB && !compact) {
                    DataPane(
                        document = document,
                        selectedFieldId = selectedFieldId,
                        onFieldSelected = { selectedFieldId = it.id; rowSelection = RowSelectionState() },
                        onAddField = { field ->
                            selectedFieldId = field.id
                            if (field.role == io.github.baitian6641.cloudydata.model.FieldRole.MEASURE) {
                                viewModel.setMeasureField(field)
                            } else {
                                viewModel.setRowField(field)
                            }
                        },
                        modifier = Modifier
                            .width(if (windowWidth >= 1200.dp) 248.dp else 216.dp)
                            .fillMaxHeight(),
                    )
                    CloudyDivider(Modifier.fillMaxHeight(), vertical = true)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (resolvedTab == DATA_TAB) {
                        DataSourceView(
                            document = document,
                            onImport = {
                                importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                    } else {
                        val sheet = document.worksheets.firstOrNull { it.id == resolvedTab }
                        if (sheet == null) {
                            Text("This worksheet no longer exists.", modifier = Modifier.padding(24.dp))
                        } else {
                            WorksheetView(
                                document = document,
                                sheet = sheet,
                                queryState = state.query,
                                selectedRowIndex = rowSelection.primary,
                                selectedRowIndices = rowSelection.rows,
                                onSelectRow = { index ->
                                    rowSelection = RowSelectionReducer.select(rowSelection, index, SelectionMode.REPLACE)
                                    selectedFieldId = null
                                },
                                onSelection = { index, mode ->
                                    rowSelection = RowSelectionReducer.select(rowSelection, index, mode)
                                    selectedFieldId = null
                                },
                                onSelectionSet = { rows, additive ->
                                    rowSelection = RowSelectionReducer.selectMany(rowSelection, rows, additive)
                                    selectedFieldId = null
                                },
                                onClampSelection = { rowCount ->
                                    rowSelection = RowSelectionReducer.clamp(rowSelection, rowCount)
                                },
                                onSetRowField = viewModel::setRowField,
                                onSetMeasureField = viewModel::setMeasureField,
                                onSetSeriesField = viewModel::setSeriesField,
                                onSetSecondaryMeasureField = viewModel::setSecondaryMeasureField,
                                onSetSecondaryAggregation = viewModel::setSecondaryAggregation,
                                onClearRowField = viewModel::clearRowField,
                                onClearMeasureField = viewModel::clearMeasureField,
                                onClearSeriesField = viewModel::clearSeriesField,
                                onClearSecondaryMeasureField = viewModel::clearSecondaryMeasureField,
                                onAggregation = viewModel::setAggregation,
                                onMarkType = viewModel::setMarkType,
                                onSortDescending = viewModel::setSortDescending,
                                onKeepSelection = { rows ->
                                    viewModel.keepSelection(rows)
                                    rowSelection = RowSelectionState()
                                },
                                onExcludeSelection = { rows ->
                                    viewModel.excludeSelection(rows)
                                    rowSelection = RowSelectionState()
                                },
                                onClearFilter = {
                                    viewModel.clearFilter()
                                    rowSelection = RowSelectionState()
                                },
                                onViewData = { rows -> viewModel.openViewData(sheet.id, rows) },
                                onChartOptionsChange = viewModel::updateChartSettings,
                                onChartSettings = { showChartSettings = true },
                                onRetry = viewModel::retryQuery,
                                onCancelQuery = viewModel::cancelQuery,
                                onImport = {
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                                onOpenFields = { showFieldsDialog = true },
                                compact = compact,
                            )
                        }
                    }
                }
                if (showSideInspector) {
                    CloudyDivider(Modifier.fillMaxHeight(), vertical = true)
                    WorkbookInspector(
                        document = document,
                        sheet = document.worksheets.firstOrNull { it.id == resolvedTab },
                        selectedField = document.snapshot?.columns?.firstOrNull { it.id == selectedFieldId },
                        selectedRowIndex = rowSelection.primary,
                        queryState = state.query,
                        engineVersion = state.engineVersion,
                        modifier = Modifier.width(288.dp).fillMaxHeight(),
                    )
                }
            }
            CloudyDivider()
            WorkbookStatusStrip(
                document = document,
                state = state,
                onRetryQuery = viewModel::retryQuery,
                onCancelQuery = viewModel::cancelQuery,
            )
        }

        if (showFieldsDialog || (showInspector && maxWidth < 1100.dp)) {
            Dialog(onDismissRequest = { showFieldsDialog = false; showInspector = false }) {
                Column(
                    Modifier
                        .width(360.dp)
                        .heightIn(max = 560.dp)
                        .background(colors.surface)
                        .padding(density.spaceMedium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (showFieldsDialog) "Fields" else "Inspector",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        CloudyIconButton(Icons.Default.Close, "Close", {
                            showFieldsDialog = false
                            showInspector = false
                        })
                    }
                    if (showFieldsDialog) {
                        DataPane(
                            document = document,
                            selectedFieldId = selectedFieldId,
                            onFieldSelected = { field ->
                                selectedFieldId = field.id
                                rowSelection = RowSelectionState()
                                if (field.role == io.github.baitian6641.cloudydata.model.FieldRole.MEASURE) {
                                    viewModel.setMeasureField(field)
                                } else {
                                    viewModel.setRowField(field)
                                }
                                showFieldsDialog = false
                            },
                            onAddField = { field ->
                                selectedFieldId = field.id
                                if (field.role == io.github.baitian6641.cloudydata.model.FieldRole.MEASURE) {
                                    viewModel.setMeasureField(field)
                                } else {
                                    viewModel.setRowField(field)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        WorkbookInspector(
                            document = document,
                            sheet = document.worksheets.firstOrNull { it.id == resolvedTab },
                            selectedField = document.snapshot?.columns?.firstOrNull { it.id == selectedFieldId },
                            selectedRowIndex = rowSelection.primary,
                            queryState = state.query,
                            engineVersion = state.engineVersion,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showPalette) {
        val paletteSheet = document?.worksheets?.firstOrNull { it.id == resolvedTab }
        val selectionFilterReady = paletteSheet?.filter == null &&
            paletteSheet?.rowFieldId != null &&
            rowSelection.rows.isNotEmpty() &&
            state.query is WorksheetQueryState.Ready
        val commands = buildList {
            add(PaletteCommand("Save workbook", "Ctrl+S", viewModel::saveNow))
            if (state.canUndo) add(PaletteCommand("Undo", "Ctrl+Z", viewModel::undo))
            if (state.canRedo) add(PaletteCommand("Redo", "Ctrl+Shift+Z", viewModel::redo))
            add(PaletteCommand("Import or replace data") { importLauncher.launch(arrayOf("*/*")) })
            add(PaletteCommand("New worksheet", run = viewModel::addWorksheet))
            add(PaletteCommand("Rename workbook") { showRenameDialog = true })
            add(PaletteCommand("Chart settings") { showChartSettings = true })
            add(PaletteCommand("Toggle inspector") { showInspector = !showInspector })
            if (selectionFilterReady) {
                add(
                    PaletteCommand("Keep selected marks") {
                        viewModel.keepSelection(rowSelection.rows)
                        rowSelection = RowSelectionState()
                    },
                )
                add(
                    PaletteCommand("Exclude selected marks") {
                        viewModel.excludeSelection(rowSelection.rows)
                        rowSelection = RowSelectionState()
                    },
                )
            }
            if (paletteSheet?.filter != null) {
                add(
                    PaletteCommand("Clear worksheet filter") {
                        viewModel.clearFilter()
                        rowSelection = RowSelectionState()
                    },
                )
            }
            if (state.query is WorksheetQueryState.Failed) {
                add(PaletteCommand("Retry query") { viewModel.retryQuery() })
            }
            add(PaletteCommand("Back to Home", run = onBack))
        }
        CommandPalette(commands = commands, onDismiss = { showPalette = false })
    }

    if (showChartSettings) {
        val sheet = document?.worksheets?.firstOrNull { it.id == resolvedTab }
        if (sheet != null) {
            ChartSettingsDialog(
                sheet = sheet,
                onDismiss = { showChartSettings = false },
                onApply = { options ->
                    viewModel.updateChartSettings(options)
                    showChartSettings = false
                },
            )
        }
    }

    if (showRenameDialog) {
        RenameWorkbookDialog(
            current = document?.name ?: "",
            onDismiss = { showRenameDialog = false },
            onRename = { name ->
                viewModel.renameWorkbook(name)
                showRenameDialog = false
            },
        )
    }

    if (state.import !is ImportUiState.Idle) {
        ImportFlowDialog(
            state = state.import,
            hasExistingSource = document?.snapshot != null,
            onApply = viewModel::applyImport,
            onDiscard = viewModel::discardImport,
            onClose = viewModel::dismissImportError,
        )
    }

    if (state.viewData !is ViewDataState.Idle) {
        ViewDataDialog(state = state.viewData, onDismiss = viewModel::closeViewData)
    }
}

private const val DATA_TAB = "data"

@Composable
private fun WorkbookToolbar(
    document: WorkbookDocument,
    state: WorkbookUiState,
    compact: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onImport: () -> Unit,
    onAddSheet: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleInspector: () -> Unit,
    inspectorOpen: Boolean,
) {
    val colors = LocalCloudyColors.current
    CloudyToolbar {
        CloudyIconButton(Icons.Default.ArrowBack, "Back to Home", onBack)
        CloudyIconButton(Icons.Default.Undo, "Undo", onUndo, enabled = state.canUndo)
        CloudyIconButton(Icons.Default.Redo, "Redo", onRedo, enabled = state.canRedo)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    document.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                CloudyIconButton(Icons.Default.Edit, "Rename workbook", onRename)
            }
            Text(
                document.snapshot?.displayName ?: "No data source",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!compact) {
            CloudySecondaryButton(
                text = if (document.snapshot == null) "Import CSV" else "Replace data",
                onClick = onImport,
                icon = Icons.Default.UploadFile,
            )
            CloudySecondaryButton("New sheet", onAddSheet, icon = Icons.Default.Add)
            CloudySecondaryButton(
                "Inspector",
                onToggleInspector,
                icon = Icons.Default.Info,
            )
        } else {
            CloudyIconButton(Icons.Default.UploadFile, "Import or replace data", onImport)
            CloudyIconButton(Icons.Default.Add, "New worksheet", onAddSheet)
            CloudyIconButton(Icons.Default.Info, "Toggle inspector", onToggleInspector)
        }
        SaveBadge(state.save)
    }
}

@Composable
private fun SaveBadge(save: SaveState) {
    when (save) {
        SaveState.Saved -> CloudyBadge("Saved locally", tone = CloudyBadgeTone.Success)
        SaveState.Saving -> CloudyBadge("Saving…", tone = CloudyBadgeTone.Neutral)
        is SaveState.Failed -> CloudyBadge("Not saved", tone = CloudyBadgeTone.Danger)
        is SaveState.Conflict -> CloudyBadge("Change not applied", tone = CloudyBadgeTone.Warning)
    }
}

private data class WorkbookTabItem(val id: String, val label: String)

@Composable
private fun WorkbookTabStrip(
    document: WorkbookDocument,
    activeTab: String,
    onSelect: (String) -> Unit,
    onAddSheet: () -> Unit,
    onRemoveWorksheet: (String) -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val tabs = remember(document.worksheets) {
        listOf(WorkbookTabItem(DATA_TAB, "Data source")) +
            document.worksheets.map { WorkbookTabItem(it.id, it.name) }
    }
    val selectedIndex = tabs.indexOfFirst { it.id == activeTab }.coerceAtLeast(0)
    var rovingIndex by remember { mutableIntStateOf(selectedIndex) }
    var focusableIndex by remember { mutableIntStateOf(selectedIndex) }
    var focusTransitionIndex by remember { mutableStateOf<Int?>(null) }
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
    var stripHasFocus by remember { mutableStateOf(false) }
    var focusedTabIndex by remember { mutableStateOf<Int?>(null) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var closeFocus by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastTabIds by remember { mutableStateOf(tabs.map { it.id }) }
    LaunchedEffect(selectedIndex) {
        rovingIndex = selectedIndex
        if (stripHasFocus) {
            focusTransitionIndex = selectedIndex
            pendingFocusIndex = selectedIndex
        } else {
            focusableIndex = selectedIndex
            focusTransitionIndex = null
        }
    }
    LaunchedEffect(tabs) {
        // Roving/focus state is index-based, so any worksheet add/remove (from
        // this window's close action or another window's command) must re-anchor
        // every index by tab identity; otherwise a shrink can leave no tab
        // focusable or land the next Tab press on the wrong worksheet.
        val oldIds = lastTabIds
        val newIds = tabs.map { it.id }
        if (oldIds == newIds) return@LaunchedEffect
        lastTabIds = newIds
        if (closeFocus != null) return@LaunchedEffect // the close transfer owns the indices
        fun remap(index: Int): Int {
            val id = oldIds.getOrNull(index) ?: return selectedIndex
            val next = newIds.indexOf(id)
            return if (next >= 0) next else selectedIndex
        }
        fun remapNullable(index: Int?): Int? = index?.let { remap(it) }
        rovingIndex = remap(rovingIndex)
        focusableIndex = remap(focusableIndex)
        focusTransitionIndex = remapNullable(focusTransitionIndex)
        pendingFocusIndex = remapNullable(pendingFocusIndex)
    }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val bringIntoViewRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }
    LaunchedEffect(pendingFocusIndex, rovingIndex) {
        pendingFocusIndex?.let { index ->
            if (index == rovingIndex) {
                tabs.getOrNull(index)?.let { focusRequesters[it.id]?.requestFocus() }
            }
            pendingFocusIndex = null
        }
    }
    LaunchedEffect(closeFocus, tabs) {
        val (removedId, focusId) = closeFocus ?: return@LaunchedEffect
        if (tabs.any { it.id == removedId }) return@LaunchedEffect
        val index = tabs.indexOfFirst { it.id == focusId }
        if (index >= 0) {
            rovingIndex = index
            focusableIndex = index
            focusTransitionIndex = index
            pendingFocusIndex = index
        }
        closeFocus = null
    }
    LaunchedEffect(rovingIndex, tabs.size) {
        tabs.getOrNull(rovingIndex)?.let { tab ->
            bringIntoViewRequesters[tab.id]?.bringIntoView()
        }
    }
    fun focusTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        rovingIndex = index
        focusableIndex = index
        focusTransitionIndex = index
        pendingFocusIndex = index
    }
    fun closeTab(tabId: String) {
        overflowExpanded = false
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index <= 0) return
        if (tabs.size <= 2) return // the final worksheet cannot be removed
        val focusId = when {
            tabId == activeTab -> {
                // Mirror the coordinator's selected-tab fallback so focus follows
                // the neighbor that becomes selected after the removal.
                val worksheetIndex = index - 1
                val sheets = document.worksheets
                (if (worksheetIndex > 0) sheets[worksheetIndex - 1] else sheets.getOrNull(worksheetIndex + 1))?.id
                    ?: DATA_TAB
            }
            else -> activeTab
        }
        closeFocus = tabId to focusId
        onRemoveWorksheet(tabId)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(colors.surfaceMuted)
            .padding(horizontal = density.spaceSmall)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Delete || event.key == Key.Backspace) {
                    val index = focusedTabIndex
                    if (index != null && index > 0) {
                        closeTab(tabs[index].id)
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                val currentIndex = focusedTabIndex?.coerceIn(0, tabs.lastIndex) ?: rovingIndex.coerceIn(0, tabs.lastIndex)
                val nextIndex = when {
                    focusedTabIndex == null && event.key == Key.DirectionLeft -> currentIndex
                    focusedTabIndex == null -> return@onPreviewKeyEvent false
                    event.key == Key.DirectionRight -> (currentIndex + 1).coerceAtMost(tabs.lastIndex)
                    event.key == Key.DirectionLeft -> (currentIndex - 1).coerceAtLeast(0)
                    else -> return@onPreviewKeyEvent false
                }
                if (nextIndex != rovingIndex || focusedTabIndex == null) {
                    rovingIndex = nextIndex
                    focusTransitionIndex = nextIndex
                    pendingFocusIndex = nextIndex
                }
                true
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .focusGroup()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                key(tab.id) {
                    val requester = remember(tab.id) {
                        focusRequesters.getOrPut(tab.id) { FocusRequester() }
                    }
                    val scrollRequester = remember(tab.id) {
                        bringIntoViewRequesters.getOrPut(tab.id) { BringIntoViewRequester() }
                    }
                    WorkbookTab(
                        label = tab.label,
                        selected = activeTab == tab.id,
                        canFocus = index == focusableIndex || index == focusTransitionIndex,
                        focusRequester = requester,
                        bringIntoViewRequester = scrollRequester,
                        onFocused = { focused ->
                            if (focused) {
                                stripHasFocus = true
                                focusedTabIndex = index
                                rovingIndex = index
                                focusableIndex = index
                                focusTransitionIndex = null
                            } else if (index == focusableIndex) {
                                stripHasFocus = false
                                focusedTabIndex = null
                                pendingFocusIndex = null
                                focusTransitionIndex = null
                            }
                        },
                        onClick = {
                            rovingIndex = index
                            if (stripHasFocus) {
                                focusTransitionIndex = index
                                pendingFocusIndex = index
                            } else {
                                focusableIndex = index
                            }
                            onSelect(tab.id)
                        },
                        leadingIcon = Icons.Default.List,
                    )
                }
            }
        }
        Box {
            CloudyIconButton(
                Icons.Default.MoreVert,
                "Worksheet overflow menu",
                { overflowExpanded = true },
            )
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
            ) {
                tabs.drop(1).forEach { tab ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tab.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                TextButton(
                                    onClick = { closeTab(tab.id) },
                                    enabled = tabs.size > 2,
                                    // Keyboard close lives on the strip Delete
                                    // shortcut; the menu stays one stop per row.
                                    modifier = Modifier.focusProperties { canFocus = false },
                                ) {
                                    Text("Close")
                                }
                            }
                        },
                        onClick = {
                            overflowExpanded = false
                            focusTab(tab.id)
                            onSelect(tab.id)
                        },
                    )
                }
            }
        }
        // The exit action stays visible and focusable while worksheets scroll.
        CloudyIconButton(Icons.Default.Add, "New worksheet", onAddSheet)
    }
}

@Composable
private fun WorkbookTab(
    label: String,
    selected: Boolean,
    canFocus: Boolean,
    focusRequester: FocusRequester,
    bringIntoViewRequester: BringIntoViewRequester,
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val shape = RoundedCornerShape(CloudyTokens.Shape.button.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .heightIn(min = density.minTarget)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = if (focused) CloudyTokens.Geometry.focusRingDp.dp else 0.dp,
                color = if (focused) colors.focus else androidx.compose.ui.graphics.Color.Transparent,
                shape = shape,
            )
            .focusProperties { this.canFocus = canFocus }
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = density.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            leadingIcon,
            contentDescription = null,
            tint = if (selected) colors.onPrimaryContainer else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimaryContainer else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun RecoveryBanner(message: String) {
    val colors = LocalCloudyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Import recovery: $message",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.warning,
        )
    }
}

@Composable
private fun SaveFailureBanner(message: String, onRetry: () -> Unit) {
    val colors = LocalCloudyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.warning,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry", color = colors.warning) }
    }
}

@Composable
private fun WorkbookStatusStrip(
    document: WorkbookDocument,
    state: WorkbookUiState,
    onRetryQuery: () -> Unit,
    onCancelQuery: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .background(colors.surfaceMuted)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = document.snapshot?.let {
                "${it.displayName} · ${formatCount(it.rowCount)} rows · local Parquet snapshot"
            } ?: "No data source",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when (val query = state.query) {
            is WorksheetQueryState.Ready -> CloudyBadge(
                if (query.result.result.truncated) "Result capped" else "Query ready",
                tone = if (query.result.result.truncated) CloudyBadgeTone.Warning else CloudyBadgeTone.Neutral,
            )
            is WorksheetQueryState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Querying…",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                TextButton(onClick = onCancelQuery) {
                    Text("Cancel", color = colors.primary, style = MaterialTheme.typography.labelSmall)
                }
            }
            is WorksheetQueryState.Failed -> TextButton(onClick = onRetryQuery) {
                Text("Query failed · Retry", color = colors.danger, style = MaterialTheme.typography.labelSmall)
            }
            is WorksheetQueryState.Cancelled -> TextButton(onClick = onRetryQuery) {
                Text("Query cancelled · Retry", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            WorksheetQueryState.Empty -> Text(
                "No view",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Text(
            state.engineVersion?.let { "DuckDB $it" } ?: "Engine starting",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun RenameWorkbookDialog(
    current: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    val colors = LocalCloudyColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Rename workbook", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary) },
        text = {
            CloudyTextField(
                value = name,
                onValueChange = { name = it },
                label = "Workbook name",
            )
        },
        confirmButton = {
            CloudyPrimaryButton("Rename", enabled = name.trim().isNotEmpty(), onClick = { onRename(name) })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
