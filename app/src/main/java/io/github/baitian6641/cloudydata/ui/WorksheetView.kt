package io.github.baitian6641.cloudydata.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baitian6641.cloudydata.design.CloudyBadge
import io.github.baitian6641.cloudydata.design.CloudyBadgeTone
import io.github.baitian6641.cloudydata.design.CloudyDivider
import io.github.baitian6641.cloudydata.design.CloudyIconButton
import io.github.baitian6641.cloudydata.design.CloudySecondaryButton
import io.github.baitian6641.cloudydata.design.CloudyTextButton
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import io.github.baitian6641.cloudydata.engine.EngineResult
import io.github.baitian6641.cloudydata.engine.WorksheetResult
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.FilterMode
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.SelectionMode
import io.github.baitian6641.cloudydata.model.WorkbookDocument
import io.github.baitian6641.cloudydata.model.WorksheetDef
import io.github.baitian6641.cloudydata.model.valueAsDouble

@Composable
fun WorksheetView(
    document: WorkbookDocument,
    sheet: WorksheetDef,
    queryState: WorksheetQueryState,
    selectedRowIndex: Int,
    selectedRowIndices: Set<Int>,
    onSelectRow: (Int) -> Unit,
    onSelection: (Int, SelectionMode) -> Unit,
    onClampSelection: (Int) -> Unit,
    onSetRowField: (FieldDef) -> Unit,
    onSetMeasureField: (FieldDef) -> Unit,
    onSetSeriesField: (FieldDef) -> Unit,
    onClearRowField: () -> Unit,
    onClearMeasureField: () -> Unit,
    onClearSeriesField: () -> Unit,
    onAggregation: (Aggregation) -> Unit,
    onMarkType: (MarkType) -> Unit,
    onSortDescending: (Boolean) -> Unit,
    onKeepSelection: (Set<Int>) -> Unit,
    onExcludeSelection: (Set<Int>) -> Unit,
    onClearFilter: () -> Unit,
    onChartSettings: () -> Unit,
    onRetry: () -> Unit,
    onCancelQuery: () -> Unit,
    onImport: () -> Unit,
    onOpenFields: () -> Unit,
    compact: Boolean,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val snapshot = document.snapshot
    val columns = snapshot?.columns.orEmpty()
    val rowField = sheet.rowFieldId?.let { id -> columns.firstOrNull { it.id == id } }
    val measureField = sheet.measureFieldId?.let { id -> columns.firstOrNull { it.id == id } }
    val seriesField = sheet.seriesFieldId?.let { id -> columns.firstOrNull { it.id == id } }

    val ready = (queryState as? WorksheetQueryState.Ready)?.result
    val previous = when (queryState) {
        is WorksheetQueryState.Loading -> queryState.previous
        is WorksheetQueryState.Failed -> queryState.previous
        is WorksheetQueryState.Cancelled -> queryState.previous
        else -> null
    }
    val current = ready ?: previous
    val stale = ready == null && previous != null

    LaunchedEffect(current?.result?.rows?.size, current?.sql) {
        current?.let { onClampSelection(it.result.rows.size) }
    }

    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        if (snapshot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This worksheet has no data", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Import a CSV file to create a local snapshot, then place fields on the shelves.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    CloudySecondaryButton("Import CSV", onImport)
                }
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = density.spaceMedium, vertical = density.spaceSmall),
        ) {
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Worksheet",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    CloudyTextButton("Fields", onOpenFields)
                }
            }
            ShelfRow(
                label = "Rows",
                field = rowField,
                emptyHint = "Add a dimension",
                fields = columns.filter { it.role == FieldRole.DIMENSION },
                onSelect = onSetRowField,
                onClear = onClearRowField,
            )
            ShelfRow(
                label = "Values",
                field = measureField,
                emptyHint = "Add a measure",
                fields = columns.filter { it.role == FieldRole.MEASURE || it.role == FieldRole.DIMENSION },
                onSelect = onSetMeasureField,
                onClear = onClearMeasureField,
                aggregation = sheet.aggregation,
                onAggregation = onAggregation,
            )
            if (sheet.markType == MarkType.LINE) {
                ShelfRow(
                    label = "Color",
                    field = seriesField,
                    emptyHint = "Add a series (optional)",
                    fields = columns.filter { it.role == FieldRole.DIMENSION && it.id != sheet.rowFieldId },
                    onSelect = onSetSeriesField,
                    onClear = onClearSeriesField,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(density.spaceSmall),
            ) {
                Text(
                    "Mark",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.width(56.dp),
                )
                MarkChoice("Table", sheet.markType == MarkType.TABLE) { onMarkType(MarkType.TABLE) }
                MarkChoice("Bar", sheet.markType == MarkType.BAR) { onMarkType(MarkType.BAR) }
                MarkChoice("Line", sheet.markType == MarkType.LINE) { onMarkType(MarkType.LINE) }
                MarkChoice("Area", sheet.markType == MarkType.AREA) { onMarkType(MarkType.AREA) }
                Spacer(Modifier.weight(1f))
                CloudyTextButton("Chart settings", onChartSettings)
                if (sheet.markType != MarkType.LINE && sheet.markType != MarkType.AREA) {
                    CloudyTextButton(
                        text = if (sheet.sortDescending) "Sort descending" else "Sort ascending",
                        onClick = { onSortDescending(!sheet.sortDescending) },
                    )
                }
            }
            if (ready != null && sheet.filter == null && sheet.rowFieldId != null &&
                selectedRowIndices.isNotEmpty()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .background(colors.surfaceMuted)
                        .padding(horizontal = density.spaceSmall, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(density.spaceSmall),
                ) {
                    Text(
                        "${selectedRowIndices.size} selected ${if (selectedRowIndices.size == 1) "mark" else "marks"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    CloudyTextButton("Keep only", { onKeepSelection(selectedRowIndices) })
                    CloudyTextButton("Exclude", { onExcludeSelection(selectedRowIndices) })
                }
            }
            sheet.filter?.let { filter ->
                val kept = filter.mode == FilterMode.KEEP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .background(
                            if (kept) colors.primaryContainer else colors.warningContainer,
                        )
                        .padding(horizontal = density.spaceSmall, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(density.spaceSmall),
                ) {
                    CloudyBadge(
                        "${if (kept) "Kept" else "Excluded"} ${filter.marks.size} " +
                            if (filter.marks.size == 1) "mark" else "marks",
                        tone = if (kept) CloudyBadgeTone.Primary else CloudyBadgeTone.Warning,
                    )
                    Text(
                        if (kept) "Showing only selected marks" else "Hiding selected marks",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CloudyTextButton("Clear filter", onClearFilter)
                }
            }
        }
        CloudyDivider()

        if (stale || queryState is WorksheetQueryState.Failed ||
            queryState is WorksheetQueryState.Loading || queryState is WorksheetQueryState.Cancelled) {
            QueryNotice(
                message = when (queryState) {
                    is WorksheetQueryState.Loading -> "Updating the view for the current fields…"
                    is WorksheetQueryState.Failed -> "The last query failed. Values below are from the previous accepted result."
                    is WorksheetQueryState.Cancelled -> "Query cancelled. Values below are from the previous accepted result."
                    else -> "The view is stale."
                },
                detail = (queryState as? WorksheetQueryState.Failed)?.message,
                onRetry = onRetry,
                onCancel = if (queryState is WorksheetQueryState.Loading) onCancelQuery else null,
            )
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val chartHeight = if (maxHeight < 420.dp) 0.dp else minOf(300.dp, maxHeight * 0.48f)
            Column(Modifier.fillMaxSize()) {
                if ((sheet.markType == MarkType.BAR || sheet.markType == MarkType.LINE ||
                        sheet.markType == MarkType.AREA) &&
                    current != null && chartHeight > 0.dp) {
                    VegaChartCard(
                        result = current,
                        options = sheet.chart,
                        selectedRowIndex = selectedRowIndex,
                        selectedRowIndices = selectedRowIndices,
                        onSelectRow = onSelectRow,
                        onSelection = onSelection,
                        modifier = Modifier.fillMaxWidth().height(chartHeight),
                        chartId = "sheet-${sheet.id}",
                        title = sheet.chart.title?.takeIf { it.isNotBlank() } ?: chartTitle(current),
                        sourceNote = current.sourceNote,
                    )
                    CloudyDivider()
                }
                if (current == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (queryState is WorksheetQueryState.Loading) {
                            Text("Querying the local snapshot…", color = colors.textSecondary)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Nothing to show yet", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Place a dimension on Rows or a measure on Values.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(12.dp))
                                CloudyTextButton("Choose fields", onOpenFields)
                            }
                        }
                    }
                } else {
                    ResultTable(
                        result = current,
                        selectedRowIndex = selectedRowIndex,
                        selectedRowIndices = selectedRowIndices,
                        onSelectRow = onSelectRow,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        modifier = Modifier
            .heightIn(min = density.minTarget)
            .clip(RoundedCornerShape(CloudyTokens.Shape.button.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) colors.primaryContainer else colors.divider,
                shape = RoundedCornerShape(CloudyTokens.Shape.button.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = density.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onPrimaryContainer else colors.textSecondary,
        )
    }
}

@Composable
private fun ShelfRow(
    label: String,
    field: FieldDef?,
    emptyHint: String,
    fields: List<FieldDef>,
    onSelect: (FieldDef) -> Unit,
    onClear: () -> Unit,
    aggregation: Aggregation? = null,
    onAggregation: ((Aggregation) -> Unit)? = null,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    var expanded by remember { mutableStateOf(false) }
    var aggregationExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = density.minTarget)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.width(56.dp),
        )
        if (field == null) {
            Box {
                CloudyTextButton(text = emptyHint, onClick = { expanded = true })
                FieldMenu(
                    expanded = expanded,
                    fields = fields,
                    onDismiss = { expanded = false },
                    onSelect = { onSelect(it); expanded = false },
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(CloudyTokens.Shape.field.dp))
                    .background(colors.primaryContainer)
                    .padding(start = density.spaceSmall, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    field.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (aggregation != null && onAggregation != null) {
                    Box {
                        TextButton(onClick = { aggregationExpanded = true }) {
                            Text(
                                aggregationLabel(aggregation),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimaryContainer,
                            )
                        }
                        DropdownMenu(expanded = aggregationExpanded, onDismissRequest = { aggregationExpanded = false }) {
                            Aggregation.entries.filter { it != Aggregation.NONE }.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(aggregationLabel(option)) },
                                    onClick = {
                                        onAggregation(option)
                                        aggregationExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                CloudyIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Remove $label field",
                    onClick = onClear,
                )
            }
            Spacer(Modifier.width(density.spaceSmall))
            Box {
                CloudyTextButton("Change", onClick = { expanded = true })
                FieldMenu(
                    expanded = expanded,
                    fields = fields,
                    onDismiss = { expanded = false },
                    onSelect = { onSelect(it); expanded = false },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (field == null) {
            Text(
                "Optional",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun FieldMenu(
    expanded: Boolean,
    fields: List<FieldDef>,
    onDismiss: () -> Unit,
    onSelect: (FieldDef) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (fields.isEmpty()) {
            DropdownMenuItem(
                text = { Text("No eligible fields") },
                onClick = { },
                enabled = false,
            )
        } else {
            fields.forEach { field ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(field.name)
                            Text(
                                field.physicalType.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalCloudyColors.current.textSecondary,
                            )
                        }
                    },
                    onClick = { onSelect(field) },
                )
            }
        }
    }
}

@Composable
private fun QueryNotice(
    message: String,
    detail: String?,
    onRetry: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val colors = LocalCloudyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(message, style = MaterialTheme.typography.labelMedium, color = colors.warning)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.warning,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text("Cancel query", color = colors.warning, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry", color = colors.warning, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun NativeBarChartCard(
    result: WorksheetResult,
    selectedRowIndex: Int,
    selectedRowIndices: Set<Int>,
    onSelectRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = chartTitle(result),
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val engineResult = result.result
    val rows = engineResult.rows
    val valueColumn = if (result.rowField != null) 1 else 0
    val labelColumn = if (result.rowField != null) 0 else null
    val numeric = rows.mapNotNull { row -> valueAsDouble(row.getOrNull(valueColumn)) }
    val hasNegative = numeric.any { it < 0.0 }
    val dark = colors.canvas.luminance() < 0.5f
    val palette = if (dark) CloudyTokens.Chart.categoricalDark else CloudyTokens.Chart.categoricalLight
    val visible = rows.take(MAX_CHART_BARS)

    Column(modifier.background(colors.surface).padding(horizontal = density.spaceMedium, vertical = density.spaceSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rows.size > visible.size) {
                CloudyBadge("${visible.size} of ${rows.size} categories", tone = CloudyBadgeTone.Warning)
            }
        }
        if (hasNegative) {
            Text(
                "This provisional bar chart does not draw negative values. Exact negative values remain in the table and inspector.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.warning,
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chart unavailable for negative values", color = colors.textSecondary)
            }
            return@Column
        }
        if (numeric.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("The value field is not numeric; use the table view.", color = colors.textSecondary)
            }
            return@Column
        }
        val maxValue = numeric.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize()) {
            visible.forEachIndexed { index, row ->
                val value = valueAsDouble(row.getOrNull(valueColumn))
                val label = labelColumn?.let { displayValue(row.getOrNull(it)) } ?: "Total"
                val fraction = ((value ?: 0.0) / maxValue).coerceIn(0.0, 1.0).toFloat()
                val isSelected = index in selectedRowIndices
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp)
                        .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                        .clickable { onSelectRow(index) }
                        .semantics {
                            contentDescription = "$label: ${displayValue(row.getOrNull(valueColumn))}"
                            selected = isSelected
                        }
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp).weight(0.35f),
                    )
                    Box(
                        Modifier
                            .weight(0.5f)
                            .height(14.dp)
                            .background(colors.surfaceMuted),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(Color(palette[stableColorIndex(label, palette.size)])),
                        )
                    }
                    Spacer(Modifier.width(density.spaceSmall))
                    Text(
                        displayValue(row.getOrNull(valueColumn)),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(92.dp),
                    )
                }
            }
            if (rows.size > visible.size) {
                Text(
                    "Chart shows the first ${visible.size} categories. Full result remains below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ResultTable(
    result: WorksheetResult,
    selectedRowIndex: Int,
    selectedRowIndices: Set<Int>,
    onSelectRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val engineResult = result.result
    val columns = engineResult.columns
    val hasSeriesColumn = result.hasSeriesColumn
    val valueColumn = result.valueColumnIndex
    val horizontal = rememberScrollState()
    Column(modifier.background(colors.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted)
                .horizontalScroll(horizontal)
                .padding(horizontal = density.spaceSmall, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEachIndexed { index, column ->
                Text(
                    when {
                        result.rowField != null && index == 0 -> result.rowField.name
                        hasSeriesColumn && index == 1 -> result.seriesField?.name ?: column.name
                        result.measureField != null && index == valueColumn ->
                            "${aggregationLabel(result.aggregation)} of ${result.measureField.name}"
                        else -> column.name
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.width(tableColumnWidth(index, columns.size)).padding(horizontal = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CloudyDivider()
        if (engineResult.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("The current view returned no rows.", color = colors.textSecondary)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(engineResult.rows) { index, row ->
                val isSelected = index in selectedRowIndices
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                        .clickable { onSelectRow(index) }
                        .semantics { selected = isSelected }
                        .horizontalScroll(horizontal)
                        .padding(horizontal = density.spaceSmall, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEachIndexed { columnIndex, value ->
                        Text(
                            displayValue(value),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (columnIndex == valueColumn) FontWeight.Medium else FontWeight.Normal,
                            ),
                            color = if (value == null) colors.textSecondary else colors.textPrimary,
                            textAlign = if (columnIndex == valueColumn) TextAlign.End else TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(tableColumnWidth(columnIndex, row.size)).padding(horizontal = 4.dp),
                        )
                    }
                }
                CloudyDivider()
            }
        }
    }
}

/** Stable by category label rather than sort position; the workbook does not yet persist a palette-version map. */
private fun stableColorIndex(label: String, paletteSize: Int): Int {
    val hash = label.hashCode()
    return ((hash % paletteSize) + paletteSize) % paletteSize
}

private fun tableColumnWidth(index: Int, count: Int) = if (count <= 2) {
    if (index == 0) 280.dp else 160.dp
} else {
    140.dp
}

private const val MAX_CHART_BARS = 24

internal fun chartTitle(result: WorksheetResult): String {
    val measure = result.measureField?.name
    val dimension = result.rowField?.name ?: "view"
    return when {
        measure != null && result.seriesField != null ->
            "${aggregationLabel(result.aggregation)} of $measure by $dimension and ${result.seriesField.name}"
        measure != null -> "${aggregationLabel(result.aggregation)} of $measure by $dimension"
        else -> "Rows by $dimension"
    }
}

private fun aggregationLabel(aggregation: Aggregation): String = when (aggregation) {
    Aggregation.SUM -> "Sum"
    Aggregation.AVG -> "Average"
    Aggregation.MIN -> "Minimum"
    Aggregation.MAX -> "Maximum"
    Aggregation.COUNT -> "Count"
    Aggregation.COUNT_DISTINCT -> "Distinct count"
    Aggregation.NONE -> "Value"
}
