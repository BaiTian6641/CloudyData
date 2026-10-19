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
import io.github.baitian6641.cloudydata.model.Aggregation
import io.github.baitian6641.cloudydata.model.ChartOptions
import io.github.baitian6641.cloudydata.model.GridlineContrast
import io.github.baitian6641.cloudydata.model.LabelCollisionPolicy
import io.github.baitian6641.cloudydata.model.TableDividerContrast
import io.github.baitian6641.cloudydata.model.CategoryPalette
import io.github.baitian6641.cloudydata.model.BarOrientation
import io.github.baitian6641.cloudydata.model.BarStackMode
import io.github.baitian6641.cloudydata.model.CurrencyCode
import io.github.baitian6641.cloudydata.model.ValueDisplayFormat
import io.github.baitian6641.cloudydata.model.LabelPosition
import io.github.baitian6641.cloudydata.model.LegendMarkSample
import io.github.baitian6641.cloudydata.model.LegendOrientation
import io.github.baitian6641.cloudydata.model.LegendPosition
import io.github.baitian6641.cloudydata.model.LineDashStyle
import io.github.baitian6641.cloudydata.model.LineInterpolation
import io.github.baitian6641.cloudydata.model.MarkerShape
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
    var valueFormatExpanded by remember { mutableStateOf(false) }
    var currencyCode by remember { mutableStateOf(current.currencyCode) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var categoryPalette by remember { mutableStateOf(current.categoryPalette) }
    var categoryPaletteExpanded by remember { mutableStateOf(false) }
    var barStackMode by remember { mutableStateOf(current.barStackMode) }
    var barStackModeExpanded by remember { mutableStateOf(false) }
    var barOrientation by remember { mutableStateOf(current.barOrientation) }
    var barOrientationExpanded by remember { mutableStateOf(false) }
    var labelFontSize by remember { mutableStateOf(current.labelFontSize?.toString().orEmpty()) }
    var labelPosition by remember { mutableStateOf(current.labelPosition) }
    var labelPositionExpanded by remember { mutableStateOf(false) }
    var lineWidth by remember { mutableStateOf(current.lineWidth?.toString().orEmpty()) }
    var lineOpacity by remember { mutableStateOf(current.lineOpacity?.toString().orEmpty()) }
    var lineInterpolation by remember { mutableStateOf(current.lineInterpolation) }
    var lineInterpolationExpanded by remember { mutableStateOf(false) }
    var lineDashStyle by remember { mutableStateOf(current.lineDashStyle) }
    var lineDashStyleExpanded by remember { mutableStateOf(false) }
    var showMarkers by remember { mutableStateOf(current.showMarkers) }
    var markerSize by remember { mutableStateOf(current.markerSize?.toString().orEmpty()) }
    var markerOpacity by remember { mutableStateOf(current.markerOpacity?.toString().orEmpty()) }
    var markerBorder by remember { mutableStateOf(current.markerBorder) }
    var markerBorderWidth by remember { mutableStateOf(current.markerBorderWidth?.toString().orEmpty()) }
    var markerShape by remember { mutableStateOf(current.markerShape) }
    var markerShapeExpanded by remember { mutableStateOf(false) }
    var zeroBaseline by remember { mutableStateOf(current.zeroBaseline) }
    var logScale by remember { mutableStateOf(current.logScale) }
    var reverseY by remember { mutableStateOf(current.reverseY) }
    var showTooltip by remember { mutableStateOf(current.showTooltip) }
    var showTableRowDividers by remember { mutableStateOf(current.showTableRowDividers) }
    var showTableColumnDividers by remember { mutableStateOf(current.showTableColumnDividers) }
    var showTableHeaderDivider by remember { mutableStateOf(current.showTableHeaderDivider) }
    var tableDividerWidth by remember { mutableStateOf(current.tableDividerWidth?.toString().orEmpty()) }
    var tableDividerContrast by remember { mutableStateOf(current.tableDividerContrast) }
    var legendVisible by remember { mutableStateOf(current.legendVisible) }
    var legendMarkSample by remember { mutableStateOf(current.legendMarkSample) }
    var legendMarkSampleExpanded by remember { mutableStateOf(false) }
    var legendPosition by remember { mutableStateOf(current.legendPosition) }
    var legendOrientation by remember { mutableStateOf(current.legendOrientation) }
    var legendExpanded by remember { mutableStateOf(false) }
    var legendOrientationExpanded by remember { mutableStateOf(false) }

    val parsedMin = yMin.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val parsedMax = yMax.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val parsedYTickCount = yTickCount.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedXLabelCount = xLabelCount.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedTableDividerWidth = tableDividerWidth.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val parsedGridlineWidth = if (showGridlines) {
        gridlineWidth.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    } else {
        null
    }
    val parsedValueDecimals = if (valueFormat == ValueDisplayFormat.CURRENCY) {
        null
    } else {
        valueDecimals.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    }
    val parsedLabelFontSize = labelFontSize.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedLineWidth = lineWidth.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val parsedLineOpacity = if (sheet.markType == MarkType.LINE || sheet.markType == MarkType.AREA) {
        lineOpacity.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    } else {
        null
    }
    val parsedMarkerSize = if (showMarkers) {
        markerSize.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    } else {
        null
    }
    val parsedMarkerOpacity = if (showMarkers) {
        markerOpacity.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    } else {
        null
    }
    val parsedMarkerBorderWidth = if (showMarkers && markerBorder) {
        markerBorderWidth.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    } else {
        null
    }
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
        tableDividerWidth.trim().isNotEmpty() && (parsedTableDividerWidth == null ||
            !parsedTableDividerWidth.isFinite() ||
            parsedTableDividerWidth !in ChartOptions.MIN_TABLE_DIVIDER_WIDTH..ChartOptions.MAX_TABLE_DIVIDER_WIDTH) ->
            "Table divider width must be from ${ChartOptions.MIN_TABLE_DIVIDER_WIDTH} to " +
                "${ChartOptions.MAX_TABLE_DIVIDER_WIDTH}."
        showGridlines && gridlineWidth.trim().isNotEmpty() && (parsedGridlineWidth == null ||
            !parsedGridlineWidth.isFinite() ||
            parsedGridlineWidth !in ChartOptions.MIN_GRIDLINE_WIDTH..ChartOptions.MAX_GRIDLINE_WIDTH) ->
            "Gridline width must be from ${ChartOptions.MIN_GRIDLINE_WIDTH} to " +
                "${ChartOptions.MAX_GRIDLINE_WIDTH}."
        valueFormat != ValueDisplayFormat.CURRENCY && valueDecimals.trim().isNotEmpty() &&
            (parsedValueDecimals == null ||
            parsedValueDecimals !in ChartOptions.MIN_VALUE_DECIMALS..ChartOptions.MAX_VALUE_DECIMALS) ->
            "Value decimals must be a whole number from ${ChartOptions.MIN_VALUE_DECIMALS} to " +
                "${ChartOptions.MAX_VALUE_DECIMALS}."
        labelFontSize.trim().isNotEmpty() && (parsedLabelFontSize == null ||
            parsedLabelFontSize !in ChartOptions.MIN_LABEL_FONT_SIZE..ChartOptions.MAX_LABEL_FONT_SIZE) ->
            "Label font size must be a whole number from ${ChartOptions.MIN_LABEL_FONT_SIZE} to " +
                "${ChartOptions.MAX_LABEL_FONT_SIZE}."
        showMarkers && markerBorder && markerBorderWidth.trim().isNotEmpty() &&
            (parsedMarkerBorderWidth == null || !parsedMarkerBorderWidth.isFinite() ||
                parsedMarkerBorderWidth !in
                ChartOptions.MIN_MARKER_BORDER_WIDTH..ChartOptions.MAX_MARKER_BORDER_WIDTH) ->
            "Marker border width must be from ${ChartOptions.MIN_MARKER_BORDER_WIDTH} to " +
                "${ChartOptions.MAX_MARKER_BORDER_WIDTH}."
        showMarkers && markerOpacity.trim().isNotEmpty() && (parsedMarkerOpacity == null ||
            !parsedMarkerOpacity.isFinite() ||
            parsedMarkerOpacity !in ChartOptions.MIN_MARKER_OPACITY..ChartOptions.MAX_MARKER_OPACITY) ->
            "Marker opacity must be from ${ChartOptions.MIN_MARKER_OPACITY} to ${ChartOptions.MAX_MARKER_OPACITY}."
        showMarkers && markerSize.trim().isNotEmpty() && (parsedMarkerSize == null || !parsedMarkerSize.isFinite() ||
            parsedMarkerSize !in ChartOptions.MIN_MARKER_SIZE..ChartOptions.MAX_MARKER_SIZE) ->
            "Marker size must be from ${ChartOptions.MIN_MARKER_SIZE} to ${ChartOptions.MAX_MARKER_SIZE}."
        (sheet.markType == MarkType.LINE || sheet.markType == MarkType.AREA) &&
            lineOpacity.trim().isNotEmpty() && (parsedLineOpacity == null ||
                !parsedLineOpacity.isFinite() ||
                parsedLineOpacity !in ChartOptions.MIN_LINE_OPACITY..ChartOptions.MAX_LINE_OPACITY) ->
            "Line opacity must be from ${ChartOptions.MIN_LINE_OPACITY} to ${ChartOptions.MAX_LINE_OPACITY}."
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
                ChartToggleRow(
                    title = "Table row dividers",
                    description = "Show horizontal rules between result rows.",
                    checked = showTableRowDividers,
                    onCheckedChange = { showTableRowDividers = it },
                )
                ChartToggleRow(
                    title = "Table column dividers",
                    description = "Show vertical rules between result columns.",
                    checked = showTableColumnDividers,
                    onCheckedChange = { showTableColumnDividers = it },
                )
                ChartToggleRow(
                    title = "Table header divider",
                    description = "Show the rule between the header and result rows.",
                    checked = showTableHeaderDivider,
                    onCheckedChange = { showTableHeaderDivider = it },
                )
                CloudyTextField(
                    value = tableDividerWidth,
                    onValueChange = { tableDividerWidth = it },
                    label = "Table divider width",
                    supportingText = "${ChartOptions.MIN_TABLE_DIVIDER_WIDTH}–${ChartOptions.MAX_TABLE_DIVIDER_WIDTH} dp, blank for 1 dp.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ChartToggleRow(
                    title = "Strong table dividers",
                    description = "Use the theme outline color instead of the default divider.",
                    checked = tableDividerContrast == TableDividerContrast.STRONG,
                    onCheckedChange = {
                        tableDividerContrast = if (it) {
                            TableDividerContrast.STRONG
                        } else {
                            TableDividerContrast.DEFAULT
                        }
                    },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Value format", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                        Text(
                            when (valueFormat) {
                                ValueDisplayFormat.NUMBER -> "Plain numeric axis and labels"
                                ValueDisplayFormat.PERCENT -> "Percent axis and labels"
                                ValueDisplayFormat.CURRENCY -> "Currency axis and labels, two decimals"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                    TextButton(onClick = { valueFormatExpanded = true }) {
                        Text(
                            valueFormat.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = colors.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = valueFormatExpanded,
                        onDismissRequest = { valueFormatExpanded = false },
                    ) {
                        ValueDisplayFormat.entries.forEach { format ->
                            DropdownMenuItem(
                                text = {
                                    Text(format.name.lowercase().replaceFirstChar { it.uppercase() })
                                },
                                onClick = {
                                    valueFormat = format
                                    valueFormatExpanded = false
                                },
                            )
                        }
                    }
                }
                if (valueFormat == ValueDisplayFormat.CURRENCY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Currency", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                            Text(
                                currencyCode.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(onClick = { currencyExpanded = true }) {
                            Text(currencyCode.name, color = colors.primary)
                        }
                        DropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false },
                        ) {
                            CurrencyCode.entries.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(code.name) },
                                    onClick = {
                                        currencyCode = code
                                        currencyExpanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
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
                }
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
                    CloudyTextField(
                        value = lineOpacity,
                        onValueChange = { lineOpacity = it },
                        label = "Line opacity",
                        supportingText = "${ChartOptions.MIN_LINE_OPACITY}–${ChartOptions.MAX_LINE_OPACITY}, blank for opaque.",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Line interpolation",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                "Linear, stepped or monotone joining.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(onClick = { lineInterpolationExpanded = true }) {
                            Text(
                                lineInterpolation.name.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                color = colors.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = lineInterpolationExpanded,
                            onDismissRequest = { lineInterpolationExpanded = false },
                        ) {
                            LineInterpolation.entries.forEach { interpolation ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            interpolation.name.lowercase()
                                                .replaceFirstChar { it.uppercase() },
                                        )
                                    },
                                    onClick = {
                                        lineInterpolation = interpolation
                                        lineInterpolationExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Line style",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                "Solid, dashed or dotted stroke.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(onClick = { lineDashStyleExpanded = true }) {
                            Text(
                                lineDashStyle.name.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                color = colors.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = lineDashStyleExpanded,
                            onDismissRequest = { lineDashStyleExpanded = false },
                        ) {
                            LineDashStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            style.name.lowercase()
                                                .replaceFirstChar { it.uppercase() },
                                        )
                                    },
                                    onClick = {
                                        lineDashStyle = style
                                        lineDashStyleExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    ChartToggleRow(
                        title = "Markers",
                        description = "Show observed-point markers up to the 200-point cap.",
                        checked = showMarkers,
                        onCheckedChange = { showMarkers = it },
                    )
                    if (showMarkers) {
                        CloudyTextField(
                            value = markerSize,
                            onValueChange = { markerSize = it },
                            label = "Marker size",
                            supportingText = "${ChartOptions.MIN_MARKER_SIZE}–${ChartOptions.MAX_MARKER_SIZE} px diameter, blank for mark default.",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        CloudyTextField(
                            value = markerOpacity,
                            onValueChange = { markerOpacity = it },
                            label = "Marker opacity",
                            supportingText = "${ChartOptions.MIN_MARKER_OPACITY}–${ChartOptions.MAX_MARKER_OPACITY}, blank for opaque.",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        ChartToggleRow(
                            title = "Marker border",
                            description = "Outline point symbols with the theme text color.",
                            checked = markerBorder,
                            onCheckedChange = { markerBorder = it },
                        )
                        if (markerBorder) {
                            CloudyTextField(
                                value = markerBorderWidth,
                                onValueChange = { markerBorderWidth = it },
                                label = "Marker border width",
                                supportingText = "${ChartOptions.MIN_MARKER_BORDER_WIDTH}–${ChartOptions.MAX_MARKER_BORDER_WIDTH}, blank for 1.",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Marker shape",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    "Circle, square, diamond or cross.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                            TextButton(onClick = { markerShapeExpanded = true }) {
                                Text(
                                    markerShape.name.lowercase().replaceFirstChar { it.uppercase() },
                                    color = colors.primary,
                                )
                            }
                            DropdownMenu(
                                expanded = markerShapeExpanded,
                                onDismissRequest = { markerShapeExpanded = false },
                            ) {
                                MarkerShape.entries.forEach { shape ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                shape.name.lowercase()
                                                    .replaceFirstChar { it.uppercase() },
                                            )
                                        },
                                        onClick = {
                                            markerShape = shape
                                            markerShapeExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
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
                    title = "Reverse value axis",
                    description = if (sheet.markType == MarkType.BAR &&
                        barOrientation == BarOrientation.HORIZONTAL
                    ) {
                        "Put larger values toward the left of the plot."
                    } else {
                        "Put larger values toward the bottom of the plot."
                    },
                    checked = reverseY,
                    onCheckedChange = { reverseY = it },
                )
                if (sheet.markType == MarkType.LINE || sheet.markType == MarkType.BAR) {
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
                    if (sheet.markType == MarkType.LINE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Legend sample", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                            Text(
                                if (legendMarkSample == LegendMarkSample.LINE) {
                                    "Line swatch using current width and dash"
                                } else {
                                    "Marker swatch using current shape"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(onClick = { legendMarkSampleExpanded = true }) {
                            Text(
                                if (legendMarkSample == LegendMarkSample.LINE) "Line" else "Marker",
                                color = colors.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = legendMarkSampleExpanded,
                            onDismissRequest = { legendMarkSampleExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Marker") },
                                onClick = {
                                    legendMarkSample = LegendMarkSample.MARKER
                                    legendMarkSampleExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Line") },
                                onClick = {
                                    legendMarkSample = LegendMarkSample.LINE
                                    legendMarkSampleExpanded = false
                                },
                            )
                        }
                    }
                    }
                }
                if (sheet.markType == MarkType.BAR) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Orientation",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                if (barOrientation == BarOrientation.HORIZONTAL) {
                                    "Categories run down the left"
                                } else {
                                    "Categories run along the bottom"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(onClick = { barOrientationExpanded = true }) {
                            Text(
                                if (barOrientation == BarOrientation.HORIZONTAL) "Horizontal" else "Vertical",
                                color = colors.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = barOrientationExpanded,
                            onDismissRequest = { barOrientationExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Vertical") },
                                onClick = {
                                    barOrientation = BarOrientation.VERTICAL
                                    barOrientationExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Horizontal") },
                                onClick = {
                                    barOrientation = BarOrientation.HORIZONTAL
                                    barOrientationExpanded = false
                                },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Bar layout",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                when {
                                    sheet.rowFieldId == null ||
                                        sheet.measureFieldId == null ||
                                        sheet.seriesFieldId == null ->
                                        "Add Rows, Values and Color to group or stack bars"
                                    sheet.aggregation != Aggregation.SUM &&
                                        sheet.aggregation != Aggregation.COUNT ->
                                        "Stacking requires Sum or Count"
                                    barStackMode == BarStackMode.STACKED ->
                                        "Series segments stack to the category total"
                                    barStackMode == BarStackMode.PERCENT_STACKED ->
                                        "Series segments stack to 100% per category"
                                    else -> "Series bars sit side by side"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(
                            onClick = { barStackModeExpanded = true },
                            enabled = sheet.rowFieldId != null &&
                                sheet.measureFieldId != null &&
                                sheet.seriesFieldId != null &&
                                (sheet.aggregation == Aggregation.SUM ||
                                    sheet.aggregation == Aggregation.COUNT),
                        ) {
                            Text(
                                when (barStackMode) {
                                    BarStackMode.STACKED -> "Stacked"
                                    BarStackMode.PERCENT_STACKED -> "100%"
                                    else -> "Grouped"
                                },
                                color = colors.primary,
                            )
                        }
                        DropdownMenu(
                            expanded = barStackModeExpanded,
                            onDismissRequest = { barStackModeExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Grouped") },
                                onClick = {
                                    barStackMode = BarStackMode.NONE
                                    barStackModeExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Stacked") },
                                onClick = {
                                    barStackMode = BarStackMode.STACKED
                                    barStackModeExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("100% stacked") },
                                onClick = {
                                    barStackMode = BarStackMode.PERCENT_STACKED
                                    barStackModeExpanded = false
                                },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Categorical palette", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                        Text(
                            if (categoryPalette == CategoryPalette.COLORBLIND) {
                                "Colors checked for common color-vision deficiencies"
                            } else {
                                "Cloudy categorical colors"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                    TextButton(onClick = { categoryPaletteExpanded = true }) {
                        Text(
                            categoryPalette.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = colors.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = categoryPaletteExpanded,
                        onDismissRequest = { categoryPaletteExpanded = false },
                    ) {
                        CategoryPalette.entries.forEach { palette ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        palette.name.lowercase()
                                            .replaceFirstChar { it.uppercase() },
                                    )
                                },
                                onClick = {
                                    categoryPalette = palette
                                    categoryPaletteExpanded = false
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
                            legendVisible = legendVisible &&
                                (sheet.markType == MarkType.LINE || sheet.markType == MarkType.BAR),
                            legendMarkSample = if (sheet.markType == MarkType.LINE) {
                                legendMarkSample
                            } else {
                                LegendMarkSample.MARKER
                            },
                            categoryPalette = categoryPalette,
                            barStackMode = barStackMode,
                            barOrientation = barOrientation,
                            legendPosition = legendPosition,
                            legendOrientation = legendOrientation,
                            showTooltip = showTooltip,
                            showTableRowDividers = showTableRowDividers,
                            showTableColumnDividers = showTableColumnDividers,
                            showTableHeaderDivider = showTableHeaderDivider,
                            tableDividerWidth = parsedTableDividerWidth,
                            tableDividerContrast = tableDividerContrast,
                            yTickCount = parsedYTickCount,
                            xLabelCount = parsedXLabelCount,
                            valueDecimals = if (valueFormat == ValueDisplayFormat.CURRENCY) {
                                current.valueDecimals
                            } else {
                                parsedValueDecimals
                            },
                            valueFormat = valueFormat,
                            currencyCode = currencyCode,
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
                            lineInterpolation = if (sheet.markType == MarkType.LINE ||
                                sheet.markType == MarkType.AREA
                            ) {
                                lineInterpolation
                            } else {
                                LineInterpolation.LINEAR
                            },
                            lineDashStyle = if (sheet.markType == MarkType.LINE ||
                                sheet.markType == MarkType.AREA
                            ) {
                                lineDashStyle
                            } else {
                                LineDashStyle.SOLID
                            },
                            lineOpacity = if (sheet.markType == MarkType.LINE ||
                                sheet.markType == MarkType.AREA
                            ) {
                                parsedLineOpacity
                            } else {
                                null
                            },
                            showMarkers = showMarkers,
                            markerSize = if (showMarkers) parsedMarkerSize else null,
                            markerShape = markerShape,
                            markerOpacity = if (showMarkers) parsedMarkerOpacity else null,
                            markerBorder = markerBorder,
                            markerBorderWidth = if (showMarkers && markerBorder) {
                                parsedMarkerBorderWidth
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
