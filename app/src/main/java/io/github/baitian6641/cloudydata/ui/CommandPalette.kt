package io.github.baitian6641.cloudydata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.baitian6641.cloudydata.design.CloudyTextField
import io.github.baitian6641.cloudydata.design.CloudyTokens
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity

data class PaletteCommand(
    val label: String,
    val shortcut: String? = null,
    val run: () -> Unit,
)

@Composable
fun CommandPalette(
    commands: List<PaletteCommand>,
    onDismiss: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val focusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val filtered = remember(commands, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) commands else commands.filter {
            it.label.contains(trimmed, ignoreCase = true) ||
                it.shortcut?.contains(trimmed, ignoreCase = true) == true
        }
    }
    LaunchedEffect(filtered.size) {
        if (selectedIndex > filtered.lastIndex) selectedIndex = filtered.lastIndex.coerceAtLeast(0)
    }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    fun execute(index: Int) {
        val command = filtered.getOrNull(index) ?: return
        onDismiss()
        command.run()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 88.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CloudyTokens.Shape.menu.dp))
                    .background(colors.surface)
                    .border(
                        width = 1.dp,
                        color = colors.outline,
                        shape = RoundedCornerShape(CloudyTokens.Shape.menu.dp),
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (filtered.isNotEmpty()) {
                                    selectedIndex = (selectedIndex + 1).coerceAtMost(filtered.lastIndex)
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (filtered.isNotEmpty()) {
                                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                }
                                true
                            }
                            Key.Enter -> {
                                execute(selectedIndex)
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    }
                    .padding(density.spaceMedium),
            ) {
                Text(
                    "Command palette",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(density.spaceSmall))
                CloudyTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedIndex = 0
                    },
                    label = "Search commands",
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { execute(selectedIndex) },
                    ),
                )
                Spacer(Modifier.height(density.spaceSmall))
                if (filtered.isEmpty()) {
                    Text(
                        "No matching commands",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(density.spaceMedium),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        itemsIndexed(filtered, key = { _, command -> command.label }) { index, command ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = maxOf(40.dp, density.minTarget))
                                    .clip(RoundedCornerShape(CloudyTokens.Shape.field.dp))
                                    .background(
                                        if (index == selectedIndex) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    )
                                    .clickable { execute(index) }
                                    .padding(horizontal = density.spaceMedium, vertical = density.spaceSmall),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(density.spaceSmall),
                            ) {
                                Text(
                                    command.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (index == selectedIndex) colors.onPrimaryContainer else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (command.shortcut != null) {
                                    Text(
                                        command.shortcut,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (index == selectedIndex) colors.onPrimaryContainer else colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(density.spaceSmall))
                Text(
                    "Up/Down to navigate · Enter to run · Escape to close",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
