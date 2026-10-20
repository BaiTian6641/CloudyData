package io.github.baitian6641.cloudydata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import io.github.baitian6641.cloudydata.data.WorkbookStore
import io.github.baitian6641.cloudydata.design.CloudyBadge
import io.github.baitian6641.cloudydata.design.CloudyBadgeTone
import io.github.baitian6641.cloudydata.design.CloudyDivider
import io.github.baitian6641.cloudydata.design.CloudyIconButton
import io.github.baitian6641.cloudydata.design.CloudyNavRow
import io.github.baitian6641.cloudydata.design.CloudyPrimaryButton
import io.github.baitian6641.cloudydata.design.CloudySectionLabel
import io.github.baitian6641.cloudydata.design.CloudyTextField
import io.github.baitian6641.cloudydata.design.CloudyToolbar
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import io.github.baitian6641.cloudydata.model.WorkbookSummary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HomeSection(val label: String, val icon: ImageVector) {
    Recents("Recents", Icons.Default.DateRange),
    Favorites("Favorites", Icons.Default.Star),
    All("All workbooks", Icons.Default.List),
}

@Composable
fun HomeScreen(
    store: WorkbookStore,
    onOpenWorkbook: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val scope = rememberCoroutineScope()
    val libraryController = remember(store) { HomeLibraryController(store) }
    val library by libraryController.state.collectAsStateWithLifecycle()
    val workbooks = library.workbooks
    val loaded = library.loaded
    val loadError = library.loadError
    var search by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(HomeSection.Recents) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var showNewDialog by rememberSaveable { mutableStateOf(false) }
    var showPalette by rememberSaveable { mutableStateOf(false) }
    var narrowNavOpen by remember { mutableStateOf(false) }
    val rootFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(libraryController, reloadToken) { libraryController.reload() }

    val filtered = remember(workbooks, section, search) {
        val base = when (section) {
            HomeSection.Recents -> workbooks.take(20)
            HomeSection.Favorites -> workbooks.filter { it.favorite }
            HomeSection.All -> workbooks
        }
        if (search.isBlank()) base else base.filter {
            it.name.contains(search.trim(), ignoreCase = true) ||
                (it.sourceDisplayName?.contains(search.trim(), ignoreCase = true) == true)
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
                        !event.isShiftPressed && event.key == Key.N -> {
                            showNewDialog = true
                            true
                        }
                        !event.isShiftPressed && event.key == Key.F -> {
                            runCatching { searchFocusRequester.requestFocus() }
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
        val showNavigation = windowWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (showNavigation) {
                HomeNavigation(
                    section = section,
                    onSection = { section = it },
                    workbooks = workbooks,
                    onSettings = onSettings,
                    modifier = Modifier
                        .width(if (windowWidth >= 960.dp) 224.dp else 192.dp)
                        .fillMaxHeight(),
                )
                CloudyDivider(Modifier.fillMaxHeight(), vertical = true)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CloudyToolbar {
                    if (!showNavigation) {
                        CloudyIconButton(
                            icon = Icons.Default.Menu,
                            contentDescription = "Open navigation",
                            onClick = { narrowNavOpen = true },
                        )
                    }
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    CloudyPrimaryButton(
                        text = "New workbook",
                        onClick = { showNewDialog = true },
                        icon = Icons.Default.Add,
                    )
                }
                CloudyDivider()
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = density.spaceLarge, vertical = density.spaceMedium),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(density.spaceLarge),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.headlineLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = when (section) {
                                    HomeSection.Recents -> "Recently updated workbooks on this device"
                                    HomeSection.Favorites -> "Workbooks marked for quick access"
                                    HomeSection.All -> "Every workbook in the local library"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        Box(Modifier.width(if (windowWidth >= 960.dp) 300.dp else 220.dp)) {
                            CloudyTextField(
                                value = search,
                                onValueChange = { search = it },
                                label = "Search workbooks",
                                modifier = Modifier.focusRequester(searchFocusRequester),
                            )
                        }
                    }
                    Spacer(Modifier.height(density.spaceMedium))
                    if (loadError != null) {
                        InlineError(loadError) { reloadToken++ }
                    }
                    val favoriteErrors = library.favoriteActions.values.filter { it.phase != HomeFavoritePhase.SAVING }
                    if (favoriteErrors.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            favoriteErrors.forEach { action ->
                                val name = workbooks.firstOrNull { it.id == action.request.workbookId }?.name
                                    ?: action.workbookName ?: "Workbook ${action.request.workbookId.take(8)}"
                                FavoriteError(
                                    name = name,
                                    action = action,
                                    onRetry = { scope.launch { libraryController.retryFavorite(action.request.workbookId) } },
                                    onDismiss = { scope.launch { libraryController.dismissFavorite(action.request.workbookId) } },
                                )
                            }
                        }
                    }
                    WorkbookTableHeader()
                    CloudyDivider()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            !loaded -> Text(
                                "Reading the local workbook library…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(density.spaceLarge),
                            )
                            filtered.isEmpty() -> EmptyHome(
                                hasAny = workbooks.isNotEmpty(),
                                search = search,
                                onCreate = { showNewDialog = true },
                                onClearSearch = { search = "" },
                            )
                            else -> LazyColumn(Modifier.fillMaxSize()) {
                                items(filtered, key = { it.id }) { summary ->
                                    WorkbookRow(
                                        summary = summary,
                                        favoriteAction = library.favoriteActions[summary.id],
                                        onOpen = { onOpenWorkbook(summary.id) },
                                        onToggleFavorite = {
                                            scope.launch {
                                                libraryController.setFavorite(summary.id, summary.revision, !summary.favorite)
                                            }
                                        },
                                    )
                                    CloudyDivider()
                                }
                            }
                        }
                    }
                }
                CloudyDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp)
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${workbooks.size} workbook${if (workbooks.size == 1) "" else "s"} · local app storage",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    CloudyBadge("Local", tone = CloudyBadgeTone.Neutral)
                }
            }
        }
    }

    if (showNewDialog) {
        NewWorkbookDialog(
            onDismiss = { showNewDialog = false },
            onCreate = { name, onError ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { store.create(name) }
                    }.onSuccess { document ->
                        showNewDialog = false
                        reloadToken++
                        onOpenWorkbook(document.id)
                    }.onFailure {
                        onError(it.message ?: "Could not create the workbook.")
                    }
                }
            },
        )
    }

    if (showPalette) {
        val commands = buildList {
            add(PaletteCommand("New workbook", "Ctrl+N") { showNewDialog = true })
            add(PaletteCommand("Open settings") { onSettings() })
            add(PaletteCommand("Show recents") { section = HomeSection.Recents })
            add(PaletteCommand("Show favorites") { section = HomeSection.Favorites })
            add(PaletteCommand("Show all workbooks") { section = HomeSection.All })
            add(PaletteCommand("Focus search", "Ctrl+F") {
                runCatching { searchFocusRequester.requestFocus() }
            })
        }
        CommandPalette(commands = commands, onDismiss = { showPalette = false })
    }

    if (narrowNavOpen) {
        Dialog(onDismissRequest = { narrowNavOpen = false }) {
            Column(
                Modifier
                    .width(280.dp)
                    .background(colors.surface)
                    .padding(density.spaceMedium),
            ) {
                CloudySectionLabel("Library")
                HomeSection.entries.forEach { entry ->
                    CloudyNavRow(
                        text = entry.label,
                        selected = section == entry,
                        icon = entry.icon,
                        onClick = {
                            section = entry
                            narrowNavOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeNavigation(
    section: HomeSection,
    onSection: (HomeSection) -> Unit,
    workbooks: List<WorkbookSummary>,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Column(
        modifier = modifier
            .background(colors.surfaceMuted)
            .padding(density.spaceMedium),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = density.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(colors.primary, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Text("C", color = colors.onPrimary, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(density.spaceSmall))
            Column {
                Text(
                    "Cloudy Data",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
                Text(
                    "Analytics workbench",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(density.spaceMedium))
        CloudySectionLabel("Library")
        HomeSection.entries.forEach { entry ->
            CloudyNavRow(
                text = entry.label,
                selected = section == entry,
                icon = entry.icon,
                onClick = { onSection(entry) },
            )
        }
        Spacer(Modifier.height(density.spaceMedium))
        CloudySectionLabel("Workbooks")
        Text(
            text = if (workbooks.isEmpty()) "No local workbooks yet" else "${workbooks.size} local workbook${if (workbooks.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        CloudyDivider(Modifier.padding(vertical = density.spaceSmall))
        CloudyNavRow(
            text = "Settings",
            selected = false,
            icon = Icons.Default.Home,
            onClick = onSettings,
        )
    }
}

@Composable
private fun WorkbookTableHeader() {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = density.minTarget)
            .padding(horizontal = density.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Name", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(2.2f))
        Text("Source", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1.6f))
        Text("Rows", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(0.7f))
        Text("Modified", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1.1f))
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun WorkbookRow(
    summary: WorkbookSummary,
    favoriteAction: HomeFavoriteAction?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = density.minTarget)
            .clickable(enabled = favoriteAction == null, onClick = onOpen)
            .padding(horizontal = density.spaceSmall, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2.2f)) {
            Text(
                summary.name,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (favoriteAction?.phase) {
                    HomeFavoritePhase.SAVING -> "Saving favorite change…"
                    HomeFavoritePhase.FAILED -> if (favoriteAction.acceptedRevision != null) "Saved · library refresh needs attention" else "Favorite change needs attention"
                    HomeFavoritePhase.CONFLICT -> "Favorite change not applied"
                    null -> if (summary.hasSnapshot) "Workbook" else "Workbook · no data source"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Text(
            summary.sourceDisplayName ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.6f),
        )
        Text(
            summary.rowCount?.let { "%,d".format(it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(0.7f),
        )
        Text(
            formatTimestamp(summary.updatedAtEpochMs),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1.1f),
        )
        CloudyIconButton(
            icon = if (summary.favorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (summary.favorite) "Remove ${summary.name} from favorites" else "Add ${summary.name} to favorites",
            onClick = onToggleFavorite,
            enabled = favoriteAction == null,
        )
    }
}

@Composable
private fun FavoriteError(name: String, action: HomeFavoriteAction, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.dangerContainer).padding(density.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$name: ${action.message.orEmpty()}", style = MaterialTheme.typography.bodyMedium,
            color = colors.danger, modifier = Modifier.weight(1f))
        if (action.phase == HomeFavoritePhase.FAILED) {
            TextButton(onClick = onRetry, modifier = Modifier.semantics {
                contentDescription = "Retry favorite change for $name"
            }) { Text("Retry favorite change") }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.semantics {
            contentDescription = "Dismiss favorite change for $name and reload"
        }) { Text("Dismiss and reload") }
    }
}

@Composable
private fun EmptyHome(
    hasAny: Boolean,
    search: String,
    onCreate: () -> Unit,
    onClearSearch: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(density.spaceLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (search.isNotBlank()) Icons.Default.List else Icons.Default.Home,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(density.spaceMedium))
        Text(
            text = when {
                !hasAny -> "No workbooks yet"
                search.isNotBlank() -> "No matching workbooks"
                else -> "Nothing in this section"
            },
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(density.spaceSmall))
        Text(
            text = when {
                !hasAny -> "Create a workbook, then import a CSV file to begin."
                search.isNotBlank() -> "Clear the search to see the local library."
                else -> "Choose another library section."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(density.spaceLarge))
        if (search.isNotBlank()) {
            CloudyPrimaryButton("Clear search", onClearSearch)
        } else {
            CloudyPrimaryButton("New workbook", onCreate, icon = Icons.Default.Add)
        }
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.dangerContainer)
            .padding(density.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.danger,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun NewWorkbookDialog(
    onDismiss: () -> Unit,
    onCreate: (String, (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("Untitled workbook") }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = LocalCloudyColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("New workbook", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary) },
        text = {
            Column {
                Text(
                    "A workbook is saved in Cloudy Data's local storage. You can import a data source next.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                CloudyTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = "Workbook name",
                    isError = error != null,
                    supportingText = error,
                )
            }
        },
        confirmButton = {
            CloudyPrimaryButton(
                text = "Create",
                enabled = name.trim().isNotEmpty() && error == null,
                onClick = { onCreate(name) { error = it } },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
