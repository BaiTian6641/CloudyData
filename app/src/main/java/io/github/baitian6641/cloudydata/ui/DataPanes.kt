package io.github.baitian6641.cloudydata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baitian6641.cloudydata.design.CloudyBadge
import io.github.baitian6641.cloudydata.design.CloudyBadgeTone
import io.github.baitian6641.cloudydata.design.CloudyDivider
import io.github.baitian6641.cloudydata.design.CloudyIconButton
import io.github.baitian6641.cloudydata.design.CloudyPrimaryButton
import io.github.baitian6641.cloudydata.design.CloudySectionLabel
import io.github.baitian6641.cloudydata.design.CloudyTextField
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import io.github.baitian6641.cloudydata.model.FieldDef
import io.github.baitian6641.cloudydata.model.FieldRole
import io.github.baitian6641.cloudydata.model.LogicalType
import io.github.baitian6641.cloudydata.model.WorkbookDocument

@Composable
fun DataPane(
    document: WorkbookDocument,
    selectedFieldId: String?,
    onFieldSelected: (FieldDef) -> Unit,
    onAddField: (FieldDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val columns = document.snapshot?.columns.orEmpty()
    var search by remember { mutableStateOf("") }
    val filtered = remember(columns, search) {
        if (search.isBlank()) columns else columns.filter { it.name.contains(search.trim(), ignoreCase = true) }
    }
    val dimensions = filtered.filter { it.role == FieldRole.DIMENSION }
    val measures = filtered.filter { it.role == FieldRole.MEASURE }

    Column(modifier.background(colors.surfaceMuted)) {
        Column(Modifier.padding(density.spaceMedium)) {
            Text(
                "Data",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                document.snapshot?.displayName ?: "No source",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(density.spaceSmall))
            CloudyTextField(
                value = search,
                onValueChange = { search = it },
                label = "Search fields",
            )
        }
        CloudyDivider()
        if (columns.isEmpty()) {
            Column(Modifier.padding(density.spaceMedium)) {
                Text(
                    "No fields yet",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Import a CSV from the toolbar to create a local snapshot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    CloudySectionLabel("Dimensions · ${dimensions.size}")
                }
                items(dimensions, key = { it.id }) { field ->
                    FieldRow(
                        field = field,
                        selected = field.id == selectedFieldId,
                        onSelect = { onFieldSelected(field) },
                        onAdd = { onAddField(field) },
                    )
                }
                item {
                    Spacer(Modifier.height(density.spaceSmall))
                    CloudySectionLabel("Measures · ${measures.size}")
                }
                items(measures, key = { it.id }) { field ->
                    FieldRow(
                        field = field,
                        selected = field.id == selectedFieldId,
                        onSelect = { onFieldSelected(field) },
                        onAdd = { onAddField(field) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    field: FieldDef,
    selected: Boolean,
    onSelect: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = density.minTarget)
            .background(if (selected) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = density.spaceSmall, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(CloudyTokens.Shape.field.dp))
                .background(colors.surface)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = typeGlyph(field.logicalType),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(density.spaceSmall))
        Column(Modifier.weight(1f)) {
            Text(
                field.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimaryContainer else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                field.physicalType.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.onPrimaryContainer else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CloudyIconButton(
            icon = Icons.Default.Add,
            contentDescription = if (field.role == FieldRole.MEASURE) {
                "Add ${field.name} to values"
            } else {
                "Add ${field.name} to rows"
            },
            onClick = onAdd,
        )
    }
}

private fun typeGlyph(type: LogicalType): String = when (type) {
    LogicalType.BOOLEAN -> "T/F"
    LogicalType.INTEGER -> "123"
    LogicalType.DECIMAL -> "1.2"
    LogicalType.DOUBLE -> "1.2"
    LogicalType.STRING -> "Abc"
    LogicalType.DATE -> "Dt"
    LogicalType.TIMESTAMP -> "Ts"
    LogicalType.OTHER -> "{}"
}

@Composable
fun DataSourceView(
    document: WorkbookDocument,
    onImport: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val snapshot = document.snapshot
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = density.spaceLarge, vertical = density.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Data source", style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
                Text(
                    if (snapshot == null) "No local snapshot has been created" else "Local snapshot and field schema",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            CloudyPrimaryButton(
                text = if (snapshot == null) "Import CSV" else "Replace data",
                onClick = onImport,
                icon = Icons.Default.UploadFile,
            )
        }
        CloudyDivider()
        if (snapshot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(density.spaceLarge),
                ) {
                    Text(
                        "Import a CSV file",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(density.spaceSmall))
                    Text(
                        "Cloudy Data preserves the original file and writes a local Parquet snapshot. Plain decimal text is imported as exact DECIMAL values; scientific notation remains DOUBLE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.width(420.dp),
                    )
                    Spacer(Modifier.height(density.spaceLarge))
                    CloudyPrimaryButton("Choose CSV", onImport, icon = Icons.Default.UploadFile)
                }
            }
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = density.spaceLarge, vertical = density.spaceMedium),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(density.spaceSmall)) {
                CloudyBadge("Local Parquet", tone = CloudyBadgeTone.Neutral)
                CloudyBadge("Original preserved", tone = CloudyBadgeTone.Neutral)
            }
            Spacer(Modifier.height(density.spaceMedium))
            ProvenanceRow("Source", snapshot.displayName)
            ProvenanceRow("Imported", formatTimestamp(snapshot.importedAtEpochMs))
            ProvenanceRow("Rows", formatCount(snapshot.rowCount))
            ProvenanceRow("Fields", snapshot.columns.size.toString())
            ProvenanceRow("Snapshot", snapshot.snapshotFile)
            ProvenanceRow("Original", snapshot.originalFile ?: "not preserved")
            Spacer(Modifier.height(density.spaceMedium))
            Text(
                "Schema",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(density.spaceSmall))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .background(colors.surfaceMuted)
                    .padding(horizontal = density.spaceSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Field", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(2f))
                Text("DuckDB type", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1.2f))
                Text("Role", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(0.8f))
            }
            CloudyDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                items(snapshot.columns, key = { it.id }) { field ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .padding(horizontal = density.spaceSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            field.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            field.physicalType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1.2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (field.role == FieldRole.MEASURE) "Measure" else "Dimension",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(0.8f),
                        )
                    }
                    CloudyDivider()
                }
            }
        }
    }
}

@Composable
private fun ProvenanceRow(label: String, value: String) {
    val colors = LocalCloudyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
