package io.github.baitian6641.cloudydata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.baitian6641.cloudydata.design.CloudyPrimaryButton
import io.github.baitian6641.cloudydata.design.CloudyTextField
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.model.AxisTitleAnchor
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.GridlineContrast
import io.github.baitian6641.cloudydata.model.LabelCollisionPolicy
import io.github.baitian6641.cloudydata.model.ValueDisplayFormat
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.LegendOrientation
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.MarkType
import io.github.baitian6641.cloudydata.model.WorksheetDef

@Composable
fun ChartSettingsDialog(
    sheet: WorksheetDef,
    onDismiss: () -> Unit,
    onApply: (ChartOptions) -> Unit,
) {
    val colors = LocalCloudyColors.current
    val current = sheet.chart
    var title by remember { mutableStateOf(current.title.orEmpty()) }
    var subtitle by remember { mutableStateOf(current.subtitle.orEmpty()) }
    var caption by remember { mutableStateOf(current.caption.orEmpty()) }
    var units by remember { mutableStateOf(current.units.orEmpty()) }
    var axisTitleAnchor by remember { mutableStateOf(current.axisTitleAnchor) }
    var axisAnchorExpanded by remember { mutableStateOf(false) }
    var chartPadding by remember { mutableStateOf(current.chartPadding.toString()) }
    var showChartBorder by remember { mutableStateOf(current.showChartBorder) }
    var transparentExportBackground by remember { mutableStateOf(current.transparentExportBackground) }
    var xAxisTitle by remember { mutableStateOf(current.xAxisTitle.orEmpty()) }
    var yAxisTitle by remember { mutableStateOf(current.yAxisTitle.orEmpty()) }
    var seriesTitle by remember { mutableStateOf(current.seriesTitle.orEmpty()) }
    var yMin by remember { mutableStateOf(current.yMin?.toString().orEmpty()) }
    var yMax by remember { mutableStateOf(current.yMax?.toString().orEmpty()) }
    var yTickCount by remember { mutableStateOf(current.yTickCount?.toString().orEmpty()) }
    var xLabelCount by remember { mutableStateOf(current.xLabelCount?.toString().orEmpty()) }
    var showGridlines by remember { mutableStateOf(current.showGridlines) }
    var gridlineWidth by remember { mutableStateOf(current.gridlineWidth?.toString().orEmpty()) }
    var gridlineContrast by remember { mutableStateOf(current.gridlineContrast) }
    var minorGridlines by remember { mutableStateOf(current.minorGridlines) }
    var showValueLabels by remember { mutableStateOf(current.showValueLabels) }
    var labelCollisionPolicy by remember { mutableStateOf(current.labelCollisionPolicy) }
    var valueDecimals by remember { mutableStateOf(current.valueDecimals?.toString().orEmpty()) }
    var valueFormat by remember { mutableStateOf(current.valueFormat) }
    var labelFontSize by remember { mutableStateOf(current.labelFontSize?.toString().orEmpty()) }
    var labelPosition by remember { mutableStateOf(current.labelPosition) }
    var labelPositionExpanded by remember { mutableStateOf(false) }
    var lineWidth by remember { mutableStateOf(current.lineWidth?.toString().orEmpty()) }
    var zeroBaseline by remember { mutableStateOf(current.zeroBaseline) }
    var logScale by remember { mutableStateOf(current.logScale) }
    var reverseY by remember { mutableStateOf(current.reverseY) }
    var showTooltip by remember { mutableStateOf(current.showTooltip) }
    var legendVisible by remember { mutableStateOf(current.legendVisible) }
    var legendPosition by remember { mutableStateOf(current.legendPosition) }
    var legendOrientation by remember { mutableStateOf(current.legendOrientation) }
    var legendExpanded by remember { mutableStateOf(false) }
    var legendOrientationExpanded by remember { mutableStateOf(false) }

    val parsedMin = yMin.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val parsedMax = yMax.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val parsedYTickCount = yTickCount.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedXLabelCount = xLabelCount.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedGridlineWidth = if (showGridlines) {
        gridlineWidth.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    } else {
        null
    }
    val parsedValueDecimals = valueDecimals.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedLabelFontSize = labelFontSize.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedLineWidth = lineWidth.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val parsedChartPadding = chartPadding.trim().toIntOrNull()
    val numericError = when {
        yMin.trim().isNotEmpty() && (parsedMin == null || !parsedMin.isFinite()) -> "Y minimum must be a finite number."
        yMax.trim().isNotEmpty() && (parsedMax == null || !parsedMax.isFinite()) -> "Y maximum must be a finite number."
        parsedMin != null && parsedMax != null && parsedMin >= parsedMax -> "Y minimum must be less than Y maximum."
        logScale && parsedMin != null && parsedMin <= 0.0 -> "A log Y minimum must be greater than zero."
        logScale && parsedMax != null && parsedMax <= 0.0 -> "A log Y maximum must be greater than zero."
        yTickCount.trim().isNotEmpty() && (parsedYTickCount == null ||
            parsedYTickCount !in ChartOptions.MIN_TICK_COUNT..ChartOptions.MAX_TICK_COUNT) ->
            "Y tick count must be a whole number from ${ChartOptions.MIN_TICK_COUNT} to ${ChartOptions.MAX_TICK_COUNT}."
        xLabelCount.trim().isNotEmpty() && (parsedXLabelCount == null ||
            parsedXLabelCount !in ChartOptions.MIN_TICK_COUNT..ChartOptions.MAX_TICK_COUNT) ->
            "X label count must be a whole number from ${ChartOptions.MIN_TICK_COUNT} to ${ChartOptions.MAX_TICK_COUNT}."
        showGridlines && gridlineWidth.trim().isNotEmpty() && (parsedGridlineWidth == null ||
            !parsedGridlineWidth.isFinite() ||
            parsedGridlineWidth !in ChartOptions.MIN_GRIDLINE_WIDTH..ChartOptions.MAX_GRIDLINE_WIDTH) ->
            "Gridline width must be from ${ChartOptions.MIN_GRIDLINE_WIDTH} to " +
                "${ChartOptions.MAX_GRIDLINE_WIDTH}."
        valueDecimals.trim().isNotEmpty() && (parsedValueDecimals == null ||
            parsedValueDecimals !in ChartOptions.MIN_VALUE_DECIMALS..ChartOptions.MAX_VALUE_DECIMALS) ->
            "Value decimals must be a whole number from ${ChartOptions.MIN_VALUE_DECIMALS} to " +
                "${ChartOptions.MAX_VALUE_DECIMALS}."
        labelFontSize.trim().isNotEmpty() && (parsedLabelFontSize == null ||
            parsedLabelFontSize !in ChartOptions.MIN_LABEL_FONT_SIZE..ChartOptions.MAX_LABEL_FONT_SIZE) ->
            "Label font size must be a whole number from ${ChartOptions.MIN_LABEL_FONT_SIZE} to " +
                "${ChartOptions.MAX_LABEL_FONT_SIZE}."
        lineWidth.trim().isNotEmpty() && (parsedLineWidth == null || !parsedLineWidth.isFinite() ||
            parsedLineWidth !in ChartOptions.MIN_LINE_WIDTH..ChartOptions.MAX_LINE_WIDTH) ->
            "Line width must be from ${ChartOptions.MIN_LINE_WIDTH} to ${ChartOptions.MAX_LINE_WIDTH}."
        parsedChartPadding == null ||
            parsedChartPadding !in ChartOptions.MIN_CHART_PADDING..ChartOptions.MAX_CHART_PADDING ->
            "Chart padding must be a whole number from ${ChartOptions.MIN_CHART_PADDING} to " +
                "${ChartOptions.MAX_CHART_PADDING}."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Chart settings", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                Spacer(Modifier.height(8.dp))
                CloudyTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it.take(160) },
                    label = "Subtitle",
                    supportingText = "Optional, up to 160 characters.",
                )
                Spacer(Modifier.height(8.dp))
                CloudyTextField(
                    value = caption,
                    onValueChange = { caption = it.take(240) },
                    label = "Caption",
                    supportingText = "Optional, up to 240 characters; shown below the chart and in exports.",
                )
                Spacer(Modifier.height(8.dp))
                CloudyTextField(
                    value = units,
                    onValueChange = { units = it.take(16) },
                    label = "Units",
                    supportingText = "Optional short suffix, e.g. USD; up to 16 characters.",
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Axis title alignment", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                        Text(
                            when (axisTitleAnchor) {
                                AxisTitleAnchor.AUTO -> "Automatic"
                                AxisTitleAnchor.START -> "Start of each axis"
                                AxisTitleAnchor.END -> "End of each axis"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                    TextButton(onClick = { axisAnchorExpanded = true }) {
                        Text(
                            when (axisTitleAnchor) {
                                AxisTitleAnchor.AUTO -> "Automatic"
                                AxisTitleAnchor.START -> "Start"
                                AxisTitleAnchor.END -> "End"
                            },
                            color = colors.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = axisAnchorExpanded,
                        onDismissRequest = { axisAnchorExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Automatic") },
                            onClick = {
                                axisTitleAnchor = AxisTitleAnchor.AUTO
                                axisAnchorExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Start") },
                            onClick = {
                                axisTitleAnchor = AxisTitleAnchor.START
                                axisAnchorExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("End") },
                            onClick = {
                                axisTitleAnchor = AxisTitleAnchor.END
                                axisAnchorExpanded = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CloudyTextField(
                    value = chartPadding,
                    onValueChange = { chartPadding = it.filter(Char::isDigit).take(2) },
                    label = "Chart padding",
                    supportingText = "${ChartOptions.MIN_CHART_PADDING}–${ChartOptions.MAX_CHART_PADDING} dp; default ${ChartOptions.DEFAULT_CHART_PADDING}.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ChartToggleRow(
                    title = "Chart border",
                    description = "Draw a subtle outline around the chart and exported scene.",
                    checked = showChartBorder,
                    onCheckedChange = { showChartBorder = it },
                )
                ChartToggleRow(
                    title = "Transparent SVG/PNG",
                    description = "Omit the SVG/PNG background. PDF and print use the chart theme background.",
                    checked = transparentExportBackground,
                    onCheckedChange = { transparentExportBackground = it },
                )
                Spacer(Modifier.height(8.dp))
                CloudyTextField(
                    value = xAxisTitle,
                    onValueChange = { xAxisTitle = it },
                    label = "X-axis title",
                    supportingText = "Leave blank to derive it from the Rows field.",
                )
                Spacer(Modifier.height(8.dp))
                CloudyTextField(
                    value = yAxisTitle,
                    onValueChange = { yAxisTitle = it },
                    label = "Y-axis title",
                    supportingText = "Leave blank to derive it from the Values field.",
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        CloudyTextField(
                            value = yMin,
                            onValueChange = { yMin = it },
                            label = "Y minimum",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Spacer(Modifier.weight(0.05f))
                    Column(Modifier.weight(1f)) {
                        CloudyTextField(
                            value = yMax,
                            onValueChange = { yMax = it },
                            label = "Y maximum",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
                if (numericError != null) {
                    Text(
                        numericError,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.danger,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        CloudyTextField(
                            value = xLabelCount,
                            onValueChange = { xLabelCount = it },
                            label = "X label count",
                            supportingText = "${ChartOptions.MIN_TICK_COUNT}–${ChartOptions.MAX_TICK_COUNT}, blank for all.",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    Spacer(Modifier.weight(0.05f))
                    Column(Modifier.weight(1f)) {
                        CloudyTextField(
                            value = yTickCount,
                            onValueChange = { yTickCount = it },
                            label = "Y tick count",
                            supportingText = "${ChartOptions.MIN_TICK_COUNT}–${ChartOptions.MAX_TICK_COUNT}, blank for automatic.",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CloudyTextField(
                    value = seriesTitle,
                    onValueChange = { seriesTitle = it },
                    label = "Legend title",
                    supportingText = "Leave blank to use the Color field name.",
                )
                Spacer(Modifier.height(8.dp))
                ChartToggleRow(
                    title = "Gridlines",
                    description = "Show the value-axis grid behind the marks.",
                    checked = showGridlines,
                    onCheckedChange = { showGridlines = it },
                )
                if (showGridlines) {
                    Spacer(Modifier.height(8.dp))
                    CloudyTextField(
                        value = gridlineWidth,
                        onValueChange = { gridlineWidth = it },
                        label = "Gridline width",
                        supportingText = "${ChartOptions.MIN_GRIDLINE_WIDTH}–${ChartOptions.MAX_GRIDLINE_WIDTH} dp, blank for the renderer default.",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    ChartToggleRow(
                        title = "Strong gridlines",
                        description = "Use the theme outline color instead of the default divider.",
                        checked = gridlineContrast == GridlineContrast.STRONG,
                        onCheckedChange = {
                            gridlineContrast = if (it) GridlineContrast.STRONG else GridlineContrast.DEFAULT
                        },
                    )
                    if (!logScale) {
                        ChartToggleRow(
                            title = "Minor gridlines",
                            description = "Add muted subdivisions between major linear value-axis lines.",
                            checked = minorGridlines,
                            onCheckedChange = { minorGridlines = it },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CloudyTextField(
                    value = valueDecimals,
                    onValueChange = { valueDecimals = it },
                    label = "Value decimals",
                    supportingText = if (valueFormat == ValueDisplayFormat.PERCENT) {
                        "${ChartOptions.MIN_VALUE_DECIMALS}–${ChartOptions.MAX_VALUE_DECIMALS}; blank uses 1 decimal for percent."
                    } else {
                        "${ChartOptions.MIN_VALUE_DECIMALS}–${ChartOptions.MAX_VALUE_DECIMALS}; blank keeps automatic axis/label formatting."
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ChartToggleRow(
                    title = "Percent values",
                    description = "Show axis values and labels as percentages; tooltips and exact data stay unchanged.",
                    checked = valueFormat == ValueDisplayFormat.PERCENT,
                    onCheckedChange = {
                        valueFormat = if (it) ValueDisplayFormat.PERCENT else ValueDisplayFormat.NUMBER
                    },
                )
                ChartToggleRow(
                    title = "Value labels",
                    description = "Show values on bars or line points. Bar labels can sit above or inside; the exact table remains available.",
                    checked = showValueLabels,
                    onCheckedChange = { showValueLabels = it },
                )
                if (showValueLabels) {
                    ChartToggleRow(
                        title = "Hide crowded labels",
                        description = "Omit labels when bars or points do not have enough horizontal space.",
                        checked = labelCollisionPolicy == LabelCollisionPolicy.HIDE_CROWDED,
                        onCheckedChange = {
                            labelCollisionPolicy = if (it) {
                                LabelCollisionPolicy.HIDE_CROWDED
                            } else {
                                LabelCollisionPolicy.SHOW_ALL
                            }
                        },
                    )
                }
                if (sheet.markType == MarkType.BAR) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Label position", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                            Text(
                                if (labelPosition == LabelPosition.TOP) {
                                    "Above the bar"
                                } else {
                                    "Inside the bar endpoint; falls back when there is no room"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(onClick = { labelPositionExpanded = true }) {
                            Text(
                                if (labelPosition == LabelPosition.TOP) "Top" else "Inside end",
                                color = colors.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = labelPositionExpanded,
                            onDismissRequest = { labelPositionExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Top / above") },
                                onClick = {
                                    labelPosition = LabelPosition.TOP
                                    labelPositionExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Inside end") },
                                onClick = {
                                    labelPosition = LabelPosition.INSIDE_END
                                    labelPositionExpanded = false
                                },
                            )
                        }
                    }
                }
                CloudyTextField(
                    value = labelFontSize,
                    onValueChange = { labelFontSize = it },
                    label = "Label font size",
                    supportingText = "${ChartOptions.MIN_LABEL_FONT_SIZE}–${ChartOptions.MAX_LABEL_FONT_SIZE}, blank for mark default.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (sheet.markType == MarkType.LINE || sheet.markType == MarkType.AREA) {
                    CloudyTextField(
                        value = lineWidth,
                        onValueChange = { lineWidth = it },
                        label = "Line width",
                        supportingText = "${ChartOptions.MIN_LINE_WIDTH}–${ChartOptions.MAX_LINE_WIDTH}, blank for mark default.",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                ChartToggleRow(
                    title = "Tooltip",
                    description = "Show the exact series, category and value on hover, focus or selection.",
                    checked = showTooltip,
                    onCheckedChange = { showTooltip = it },
                )
                if (sheet.markType != MarkType.AREA) {
                    ChartToggleRow(
                        title = "Zero baseline",
                        description = "Include zero in the value-axis domain when no explicit range is set.",
                        checked = zeroBaseline,
                        onCheckedChange = { zeroBaseline = it },
                    )
                }
                if (sheet.markType == MarkType.LINE) {
                    ChartToggleRow(
                        title = "Logarithmic Y axis",
                        description = "Requires every plotted value to be greater than zero.",
                        checked = logScale,
                        onCheckedChange = { logScale = it },
                    )
                }
                ChartToggleRow(
                    title = "Reverse Y axis",
                    description = "Put larger values toward the bottom of the plot.",
                    checked = reverseY,
                    onCheckedChange = { reverseY = it },
                )
                if (sheet.markType == MarkType.LINE) {
                    ChartToggleRow(
                        title = "Legend",
                        description = "Show a legend for the Color series.",
                        checked = legendVisible,
                        onCheckedChange = { legendVisible = it },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Legend position", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                            Text(
                                if (legendPosition == LegendPosition.BOTTOM) "Below the plot" else "Right of the plot",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Row {
                            val positionExpanded = legendExpanded
                            TextButton(onClick = { legendExpanded = true }) {
                                Text(
                                    if (legendPosition == LegendPosition.BOTTOM) "Bottom" else "Right",
                                    color = colors.primary,
                                )
                            }
                            DropdownMenu(
                                expanded = positionExpanded,
                                onDismissRequest = { legendExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Bottom") },
                                    onClick = {
                                        legendPosition = LegendPosition.BOTTOM
                                        legendExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Right") },
                                    onClick = {
                                        legendPosition = LegendPosition.RIGHT
                                        legendExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Legend orientation", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                            Text(
                                when (legendOrientation) {
                                    LegendOrientation.VERTICAL -> "Entries stacked vertically"
                                    LegendOrientation.HORIZONTAL -> "Entries laid out horizontally"
                                    null -> "Automatic from position"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(onClick = { legendOrientationExpanded = true }) {
                            Text(
                                when (legendOrientation) {
                                    LegendOrientation.VERTICAL -> "Vertical"
                                    LegendOrientation.HORIZONTAL -> "Horizontal"
                                    null -> "Automatic"
                                },
                                color = colors.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = legendOrientationExpanded,
                            onDismissRequest = { legendOrientationExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Automatic from position") },
                                onClick = {
                                    legendOrientation = null
                                    legendOrientationExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Vertical") },
                                onClick = {
                                    legendOrientation = LegendOrientation.VERTICAL
                                    legendOrientationExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Horizontal") },
                                onClick = {
                                    legendOrientation = LegendOrientation.HORIZONTAL
                                    legendOrientationExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            CloudyPrimaryButton(
                text = "Apply",
                enabled = numericError == null,
                onClick = {
                    onApply(
                        current.copy(
                            title = clean(title),
                            subtitle = clean(subtitle)?.take(160),
                            caption = clean(caption)?.take(240),
                            units = clean(units)?.take(16),
                            axisTitleAnchor = axisTitleAnchor,
                            chartPadding = parsedChartPadding ?: ChartOptions.DEFAULT_CHART_PADDING,
                            showChartBorder = showChartBorder,
                            transparentExportBackground = transparentExportBackground,
                            xAxisTitle = clean(xAxisTitle),
                            yAxisTitle = clean(yAxisTitle),
                            seriesTitle = clean(seriesTitle),
                            yMin = parsedMin,
                            yMax = parsedMax,
                            showGridlines = showGridlines,
                            gridlineWidth = parsedGridlineWidth,
                            gridlineContrast = gridlineContrast,
                            minorGridlines = minorGridlines,
                            showValueLabels = showValueLabels,
                            zeroBaseline = zeroBaseline || sheet.markType == MarkType.AREA,
                            logScale = logScale && sheet.markType == MarkType.LINE,
                            reverseY = reverseY,
                            legendVisible = legendVisible && sheet.markType == MarkType.LINE,
                            legendPosition = legendPosition,
                            legendOrientation = legendOrientation,
                            showTooltip = showTooltip,
                            yTickCount = parsedYTickCount,
                            xLabelCount = parsedXLabelCount,
                            valueDecimals = parsedValueDecimals,
                            valueFormat = valueFormat,
                            labelCollisionPolicy = labelCollisionPolicy,
                            labelFontSize = parsedLabelFontSize,
                            labelPosition = if (sheet.markType == MarkType.BAR) {
                                labelPosition
                            } else {
                                LabelPosition.TOP
                            },
                            lineWidth = if (sheet.markType == MarkType.LINE || sheet.markType == MarkType.AREA) {
                                parsedLineWidth
                            } else {
                                null
                            },
                        ),
                    )
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun clean(value: String): String? = value.trim().takeIf { it.isNotEmpty() }

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
            .clip(RoundedCornerShape(CloudyTokens.Shape.field.dp))
            .clickable { onCheckedChange(!checked) }
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
