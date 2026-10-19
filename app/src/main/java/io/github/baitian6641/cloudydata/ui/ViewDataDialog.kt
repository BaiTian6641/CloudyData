package io.github.baitian6641.cloudydata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.baitian6641.cloudydata.design.CloudyDivider
import io.github.baitian6641.cloudydata.design.CloudyIconButton
import io.github.baitian6641.cloudydata.design.CloudySecondaryButton
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import io.github.baitian6641.cloudydata.engine.UnderlyingRowsResult

@Composable
fun ViewDataDialog(
    state: ViewDataState,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val colors = LocalCloudyColors.current
        val density = LocalCloudyDensity.current
        Column(
            Modifier
                .widthIn(min = 360.dp, max = 760.dp)
                .heightIn(max = 620.dp)
                .background(colors.surface)
                .padding(density.spaceMedium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "View data",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                CloudyIconButton(Icons.Default.Close, "Close view data", onDismiss)
            }
            CloudyDivider()
            when (state) {
                ViewDataState.Idle -> Unit
                ViewDataState.Loading -> {
                    Column(
                        Modifier.fillMaxWidth().height(220.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading underlying rows…", color = colors.textSecondary)
                    }
                }
                is ViewDataState.Failed -> {
                    Column(Modifier.padding(vertical = density.spaceMedium)) {
                        Text(
                            "The underlying rows could not be read.",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, color = colors.textSecondary)
                    }
                }
                is ViewDataState.Ready -> ViewDataBody(state.result)
            }
            Spacer(Modifier.height(density.spaceMedium))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                CloudySecondaryButton("Close", onDismiss)
            }
        }
    }
}

@Composable
private fun ColumnScope.ViewDataBody(result: UnderlyingRowsResult) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val engineResult = result.result
    val horizontal = rememberScrollState()
    val columnWidth = if (engineResult.columns.size <= 3) 180.dp else 140.dp

    Text(
        "${result.selectedMarkCount} selected ${if (result.selectedMarkCount == 1) "mark" else "marks"} · " +
            result.sourceName,
        style = MaterialTheme.typography.labelLarge,
        color = colors.textPrimary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        when {
            result.truncated -> "Showing the first ${result.result.rows.size} source rows."
            result.result.rows.size == 1 -> "1 source row."
            else -> "${result.result.rows.size} source rows."
        } + if (result.worksheetFilterApplied) " The worksheet filter is applied." else "",
        style = MaterialTheme.typography.labelSmall,
        color = if (result.truncated) colors.warning else colors.textSecondary,
    )
    Spacer(Modifier.height(density.spaceSmall))
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted)
            .horizontalScroll(horizontal)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        engineResult.columns.forEach { column ->
            Text(
                column.name,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(columnWidth).padding(horizontal = 4.dp),
            )
        }
    }
    CloudyDivider()
    Box(Modifier.weight(1f, fill = false)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            itemsIndexed(engineResult.rows) { index, row ->
                val description = engineResult.columns.mapIndexed { columnIndex, column ->
                    "${column.name}: ${exactValueText(row.getOrNull(columnIndex))}"
                }.joinToString(" · ")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Source row ${index + 1}: $description"
                        }
                        .horizontalScroll(horizontal)
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEachIndexed { columnIndex, value ->
                        Text(
                            displayValue(value),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (columnIndex == row.lastIndex) FontWeight.Medium else FontWeight.Normal,
                            ),
                            color = if (value == null) colors.textSecondary else colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(columnWidth).padding(horizontal = 4.dp),
                        )
                    }
                }
                CloudyDivider()
            }
        }
    }
}
