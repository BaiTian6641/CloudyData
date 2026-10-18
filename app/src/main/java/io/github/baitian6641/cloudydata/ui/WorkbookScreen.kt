package io.github.baitian6641.cloudydata.ui

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
    var selectedRowIndex by rememberSaveable(workbookId) { mutableIntStateOf(-1) }
    var showInspector by rememberSaveable(workbookId) { mutableStateOf(false) }
    var showFieldsDialog by rememberSaveable(workbookId) { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable(workbookId) { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.inspectCsv(uri)
    }

    val document = state.document
    val snapshot = document?.snapshot
    val resolvedTab = selectedTab?.takeIf { tab ->
        tab == DATA_TAB || document?.worksheets?.any { it.id == tab } == true
    } ?: if (snapshot != null) document?.activeWorksheetId ?: DATA_TAB else DATA_TAB

    LaunchedEffect(snapshot?.id) {
        if (snapshot != null && selectedTab == null) {
            selectedTab = document?.activeWorksheetId
        }
    }
    LaunchedEffect(state.import) {
        if (state.import is ImportUiState.Idle && snapshot != null) {
            selectedTab = document?.activeWorksheetId
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
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
                onToggleInspector = { showInspector = !showInspector },
                inspectorOpen = showInspector,
            )
            CloudyDivider()
            WorkbookTabStrip(
                document = document,
                activeTab = resolvedTab,
                onSelect = { selectedTab = it },
                onAddSheet = viewModel::addWorksheet,
            )
            CloudyDivider()
            if (state.save is SaveState.Failed) {
                SaveFailureBanner(
                    message = (state.save as SaveState.Failed).message,
                    onRetry = viewModel::saveNow,
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
                        onFieldSelected = { selectedFieldId = it.id; selectedRowIndex = -1 },
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
                                selectedRowIndex = selectedRowIndex,
                                onSelectRow = { index -> selectedRowIndex = index; selectedFieldId = null },
                                onSetRowField = viewModel::setRowField,
                                onSetMeasureField = viewModel::setMeasureField,
                                onClearRowField = viewModel::clearRowField,
                                onClearMeasureField = viewModel::clearMeasureField,
                                onAggregation = viewModel::setAggregation,
                                onMarkType = viewModel::setMarkType,
                                onSortDescending = viewModel::setSortDescending,
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
                        selectedRowIndex = selectedRowIndex,
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
                                selectedRowIndex = -1
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
                            selectedRowIndex = selectedRowIndex,
                            queryState = state.query,
                            engineVersion = state.engineVersion,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
    onToggleInspector: () -> Unit,
    inspectorOpen: Boolean,
) {
    val colors = LocalCloudyColors.current
    CloudyToolbar {
        CloudyIconButton(Icons.Default.ArrowBack, "Back to Home", onBack)
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
    }
}

@Composable
private fun WorkbookTabStrip(
    document: WorkbookDocument,
    activeTab: String,
    onSelect: (String) -> Unit,
    onAddSheet: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(colors.surfaceMuted)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = density.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkbookTab(
            label = "Data source",
            selected = activeTab == DATA_TAB,
            onClick = { onSelect(DATA_TAB) },
            leadingIcon = Icons.Default.List,
        )
        document.worksheets.forEach { sheet ->
            WorkbookTab(
                label = sheet.name,
                selected = activeTab == sheet.id,
                onClick = { onSelect(sheet.id) },
                leadingIcon = Icons.Default.List,
            )
        }
        CloudyIconButton(Icons.Default.Add, "New worksheet", onAddSheet)
    }
}

@Composable
private fun WorkbookTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        modifier = Modifier
            .heightIn(min = density.minTarget)
            .clip(RoundedCornerShape(CloudyTokens.Shape.button.dp))
            .background(if (selected) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
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
