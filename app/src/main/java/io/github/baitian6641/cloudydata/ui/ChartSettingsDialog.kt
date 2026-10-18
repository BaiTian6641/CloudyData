package io.github.baitian6641.cloudydata.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.baitian6641.cloudydata.design.CloudyPrimaryButton
import io.github.baitian6641.cloudydata.design.CloudyTextField
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.model.WorksheetDef

@Composable
fun ChartSettingsDialog(
    sheet: WorksheetDef,
    onDismiss: () -> Unit,
    onApply: (title: String?, showGridlines: Boolean, showValueLabels: Boolean) -> Unit,
) {
    val colors = LocalCloudyColors.current
    var title by remember { mutableStateOf(sheet.chartTitle.orEmpty()) }
    var showGridlines by remember { mutableStateOf(sheet.showGridlines) }
    var showValueLabels by remember { mutableStateOf(sheet.showValueLabels) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Chart settings", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        },
        text = {
            Column {
                Text(
                    "These settings are saved with the worksheet. They do not rerun the query.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                CloudyTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Chart title",
                    supportingText = "Leave blank to derive the title from the fields.",
                )
                Spacer(Modifier.height(12.dp))
                ChartToggleRow(
                    title = "Gridlines",
                    description = "Show the value-axis grid behind the marks.",
                    checked = showGridlines,
                    onCheckedChange = { showGridlines = it },
                )
                ChartToggleRow(
                    title = "Value labels",
                    description = "Show the numeric value above each bar. The exact table remains available.",
                    checked = showValueLabels,
                    onCheckedChange = { showValueLabels = it },
                )
            }
        },
        confirmButton = {
            CloudyPrimaryButton(
                text = "Apply",
                onClick = {
                    onApply(title.trim().takeIf { it.isNotEmpty() }, showGridlines, showValueLabels)
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ChartToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalCloudyColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
            Text(description, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.outline,
                uncheckedTrackColor = colors.surfaceMuted,
            ),
        )
    }
}
