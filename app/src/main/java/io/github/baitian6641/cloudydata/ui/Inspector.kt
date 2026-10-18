package io.github.baitian6641.cloudydata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.baitian6641.cloudydata.data.PendingCsvImport
import io.github.baitian6641.cloudydata.design.CloudyBadge
import io.github.baitian6641.cloudydata.design.CloudyBadgeTone
import io.github.baitian6641.cloudydata.design.CloudyDivider
import io.github.baitian6641.cloudydata.design.CloudyPrimaryButton
import io.github.baitian6641.cloudydata.design.CloudySecondaryButton
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef

@Composable
fun WorkbookInspector(
    document: WorkbookDocument,
    sheet: WorksheetDef?,
    selectedField: FieldDef?,
    selectedRowIndex: Int,
    queryState: WorksheetQueryState,
    engineVersion: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val scroll = rememberScrollState()
    val result = when (queryState) {
        is WorksheetQueryState.Ready -> queryState.result
        is WorksheetQueryState.Loading -> queryState.previous
        is WorksheetQueryState.Failed -> queryState.previous
        else -> null
    }
    Column(
        modifier
            .background(colors.surfaceMuted)
            .verticalScroll(scroll)
            .padding(density.spaceMedium),
    ) {
        Text("Inspector", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(
            "Selection and provenance",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(density.spaceMedium))
        if (selectedField != null) {
            InspectorSection("Field")
            InspectorRow("Name", selectedField.name)
            InspectorRow("Type", selectedField.physicalType)
            InspectorRow("Logical", selectedField.logicalType.name.lowercase())
            InspectorRow("Role", if (selectedField.role == FieldRole.MEASURE) "Measure" else "Dimension")
            InspectorRow("Stable ID", selectedField.id)
            Spacer(Modifier.height(density.spaceSmall))
            CloudyBadge(
                if (selectedField.role == FieldRole.MEASURE) "Numeric measure" else "Dimension",
                tone = CloudyBadgeTone.Neutral,
            )
            Spacer(Modifier.height(density.spaceMedium))
        }

        if (result != null && selectedRowIndex >= 0 && selectedRowIndex < result.result.rows.size) {
            val row = result.result.rows[selectedRowIndex]
            InspectorSection("Selected row")
            result.result.columns.forEachIndexed { index, column ->
                val label = when {
                    result.rowField != null && index == 0 -> result.rowField.name
                    result.measureField != null && index == 1 -> "${aggregationName(result.aggregation)} of ${result.measureField.name}"
                    else -> column.name
                }
                InspectorRow(label, displayValue(row.getOrNull(index)), exact = exactValueText(row.getOrNull(index)))
            }
            Spacer(Modifier.height(density.spaceMedium))
        }

        InspectorSection("Workbook")
        InspectorRow("Name", document.name)
        InspectorRow("ID", document.id)
        InspectorRow("Format", "v${document.formatVersion}")
        InspectorRow("Revision", document.revision.toString())
        document.snapshot?.let { snapshot ->
            InspectorRow("Snapshot", snapshot.id)
            InspectorRow("Source", snapshot.displayName)
            InspectorRow("Rows", formatCount(snapshot.rowCount))
            InspectorRow("Imported", formatTimestamp(snapshot.importedAtEpochMs))
            InspectorRow("Snapshot file", snapshot.snapshotFile)
            InspectorRow("Original", snapshot.originalFile ?: "not preserved")
        }
        InspectorRow("Engine", engineVersion?.let { "DuckDB $it" } ?: "starting")
        sheet?.let {
            InspectorRow("Worksheet", it.name)
            InspectorRow("Mark", it.markType.name.lowercase())
        }
        Spacer(Modifier.height(density.spaceMedium))
        InspectorSection("Query")
        Text(
            result?.sql ?: "No query has run for the current view.",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CloudyTokens.Shape.menu.dp))
                .background(colors.surface)
                .padding(density.spaceSmall),
        )
        Spacer(Modifier.height(density.spaceMedium))
        Text(
            "Display rounding never changes the stored result. Select a row for exact values.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun InspectorSection(title: String) {
    val colors = LocalCloudyColors.current
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = colors.textPrimary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun InspectorRow(label: String, value: String, exact: String? = null) {
    val colors = LocalCloudyColors.current
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = if (exact == null) 3 else 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (exact != null && exact != value) {
            Text(
                "Exact: $exact",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ImportFlowDialog(
    state: ImportUiState,
    hasExistingSource: Boolean,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val pending = when (state) {
        is ImportUiState.Ready -> state.pending
        is ImportUiState.Applying -> state.pending
        is ImportUiState.Failed -> state.pending
        else -> null
    }
    Dialog(
        onDismissRequest = {
            when (state) {
                is ImportUiState.Inspecting, is ImportUiState.Applying -> Unit
                else -> {
                    if (pending != null) onDiscard() else onClose()
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is ImportUiState.Inspecting && state !is ImportUiState.Applying,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(CloudyTokens.Shape.dialog.dp))
                .background(colors.surface)
                .padding(density.spaceLarge),
        ) {
            Text(
                when (state) {
                    is ImportUiState.Inspecting -> "Inspecting CSV"
                    is ImportUiState.Applying -> "Importing data"
                    else -> if (hasExistingSource) "Replace data source" else "Import CSV"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(density.spaceSmall))
            when (state) {
                is ImportUiState.Inspecting -> InspectingBody(state.displayName)
                is ImportUiState.Applying -> ApplyingBody(state.pending)
                is ImportUiState.Ready -> ReadyBody(state.pending, hasExistingSource)
                is ImportUiState.Failed -> FailedBody(state)
                ImportUiState.Idle -> Unit
            }
            if (pending != null && state is ImportUiState.Ready) {
                Spacer(Modifier.height(density.spaceMedium))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(density.spaceSmall),
                ) {
                    CloudySecondaryButton("Cancel", onDiscard)
                    Spacer(Modifier.weight(1f))
                    CloudyPrimaryButton(
                        if (hasExistingSource) "Replace data source" else "Apply import",
                        onApply,
                    )
                }
            }
            if (state is ImportUiState.Failed && pending != null) {
                Spacer(Modifier.height(density.spaceMedium))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(density.spaceSmall),
                ) {
                    CloudySecondaryButton("Discard", onDiscard)
                    Spacer(Modifier.weight(1f))
                    CloudyPrimaryButton("Retry import", onApply)
                }
            }
            if (state is ImportUiState.Failed && pending == null) {
                Spacer(Modifier.height(density.spaceMedium))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CloudySecondaryButton("Close", onClose)
                }
            }
        }
    }
}

@Composable
private fun InspectingBody(displayName: String) {
    val colors = LocalCloudyColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = colors.primary,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(displayName, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
            Text(
                "Copying the file into app-owned staging, then reading its schema and a 50-row preview. The staged copy is discarded if you cancel.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ApplyingBody(pending: PendingCsvImport) {
    val colors = LocalCloudyColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.primary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text("Writing and verifying the local Parquet snapshot…", color = colors.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The import cannot be cancelled in this build. The workbook is updated only after the row count is verified.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(pending.displayName, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
    }
}

@Composable
private fun ReadyBody(pending: PendingCsvImport, replace: Boolean) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            pending.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
        )
        Text(
            "${formatBytes(pending.sourceBytes)} · ${pending.columns.size} fields · ${pending.previewRows.size}-row preview",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        if (replace) {
            Spacer(Modifier.height(density.spaceSmall))
            Text(
                "Applying replaces the current snapshot. The previous source and snapshot files are overwritten; undo is not available for data replacement in this build.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.warning,
            )
        }
        if (pending.warnings.isNotEmpty()) {
            Spacer(Modifier.height(density.spaceSmall))
            pending.warnings.forEach {
                Text("• $it", style = MaterialTheme.typography.labelSmall, color = colors.warning)
            }
        }
        Spacer(Modifier.height(density.spaceMedium))
        Text("Detected fields", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            pending.columns.forEach { field ->
                Text(
                    "${field.name} · ${field.physicalType.lowercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(CloudyTokens.Shape.chip.dp))
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(density.spaceMedium))
        Text("Preview (first 50 rows)", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        PreviewTable(pending, Modifier.height(220.dp))
    }
}

@Composable
private fun PreviewTable(pending: PendingCsvImport, modifier: Modifier = Modifier) {
    val colors = LocalCloudyColors.current
    val horizontal = rememberScrollState()
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(CloudyTokens.Shape.menu.dp)).background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .background(colors.surfaceMuted)
                    .horizontalScroll(horizontal)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pending.previewColumns.forEach { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.width(132.dp).padding(horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CloudyDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(pending.previewRows) { _, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp)
                            .horizontalScroll(horizontal)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { value ->
                            Text(
                                displayValue(value),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textPrimary,
                                modifier = Modifier.width(132.dp).padding(horizontal = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    CloudyDivider()
                }
            }
        }
    }
}

@Composable
private fun FailedBody(state: ImportUiState.Failed) {
    val colors = LocalCloudyColors.current
    Column {
        Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.danger,
        )
        if (state.pending != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "The staged copy is retained so the import can be retried or discarded.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

private fun aggregationName(aggregation: io.github.baitian6641.cloudydata.model.Aggregation): String = when (aggregation) {
    io.github.baitian6641.cloudydata.model.Aggregation.SUM -> "Sum"
    io.github.baitian6641.cloudydata.model.Aggregation.AVG -> "Average"
    io.github.baitian6641.cloudydata.model.Aggregation.MIN -> "Minimum"
    io.github.baitian6641.cloudydata.model.Aggregation.MAX -> "Maximum"
    io.github.baitian6641.cloudydata.model.Aggregation.COUNT -> "Count"
    io.github.baitian6641.cloudydata.model.Aggregation.COUNT_DISTINCT -> "Distinct count"
    io.github.baitian6641.cloudydata.model.Aggregation.NONE -> "Value"
}
