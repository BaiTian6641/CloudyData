package io.github.baitian6641.cloudydata.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.baitian6641.cloudydata.data.DisplayPreferencesStore
import io.github.baitian6641.cloudydata.design.CloudyAppearance
import io.github.baitian6641.cloudydata.design.CloudyDensity
import io.github.baitian6641.cloudydata.design.CloudyDivider
import io.github.baitian6641.cloudydata.design.CloudyIconButton
import io.github.baitian6641.cloudydata.design.CloudySectionLabel
import io.github.baitian6641.cloudydata.design.CloudyToolbar
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    store: DisplayPreferencesStore,
    onBack: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val preferences by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf("Saved preferences apply to open windows.") }
    var outcomeIsError by remember { mutableStateOf(false) }

    fun save(action: suspend () -> Result<Unit>) {
        if (saving) return
        scope.launch {
            saving = true
            val result = action()
            saving = false
            outcomeIsError = result.isFailure
            outcome = result.fold(
                onSuccess = { "Saved. This setting applies to open windows and future launches." },
                onFailure = { "Not saved: ${it.message ?: "the preference store did not confirm the write."}" },
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        CloudyToolbar {
            CloudyIconButton(Icons.Default.ArrowBack, "Back to Home", onBack)
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    saving -> "Saving…"
                    outcomeIsError -> "Not saved"
                    else -> "Saved"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (outcomeIsError) colors.danger else colors.textSecondary,
            )
        }
        CloudyDivider()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(density.spaceLarge),
            ) {
                Text(
                    "Appearance and density",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Text(
                    "These preferences belong to Cloudy Data on this device. Changing them does not alter workbook data or recalculate a query.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(density.spaceLarge))
                SettingsSection(
                    title = "Appearance",
                    description = "System follows the current Android appearance. Explicit choices apply to every open Cloudy Data window.",
                ) {
                    Column(Modifier.fillMaxWidth().selectableGroup()) {
                        CloudyAppearance.entries.forEach { appearance ->
                            SettingsRadioRow(
                                title = appearanceTitle(appearance),
                                description = appearanceDescription(appearance),
                                selected = preferences.appearance == appearance,
                                enabled = !saving,
                                onClick = { save { store.setAppearance(appearance) } },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(density.spaceLarge))
                SettingsSection(
                    title = "Density",
                    description = "Density changes row height and spacing. Text remains at the Cloudy body size; it is not scaled down.",
                ) {
                    Column(Modifier.fillMaxWidth().selectableGroup()) {
                        CloudyDensity.entries.forEach { mode ->
                            SettingsRadioRow(
                                title = densityTitle(mode),
                                description = densityDescription(mode),
                                selected = preferences.density == mode,
                                enabled = !saving,
                                onClick = { save { store.setDensity(mode) } },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(density.spaceLarge))
                SettingsSection(
                    title = "Storage",
                    description = "Workbooks and imported snapshots are stored in Cloudy Data's app-private storage. Android backup is disabled for this build.",
                ) {
                    Text(
                        "No remote account or network source is configured. Imported data is used locally by the on-device engine.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(density.spaceLarge))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        outcome,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (outcomeIsError) colors.danger else colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    CloudySectionLabel(title)
    Text(
        description,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        modifier = Modifier.padding(bottom = density.spaceSmall),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(io.github.baitian6641.cloudydata.design.CloudyTokens.Shape.menu.dp))
            .background(colors.surface)
            .padding(vertical = density.spaceSmall),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsRadioRow(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = maxOf(48.dp, density.minTarget))
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = density.spaceMedium, vertical = density.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.primary,
                unselectedColor = colors.outline,
                disabledSelectedColor = colors.disabledForeground,
                disabledUnselectedColor = colors.disabledBorder,
            ),
        )
        Column(Modifier.padding(start = density.spaceMedium)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) colors.textPrimary else colors.disabledForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) colors.textSecondary else colors.disabledForeground,
            )
        }
    }
}

private fun appearanceTitle(value: CloudyAppearance): String = when (value) {
    CloudyAppearance.System -> "System"
    CloudyAppearance.Light -> "Light"
    CloudyAppearance.Dark -> "Dark"
}

private fun appearanceDescription(value: CloudyAppearance): String = when (value) {
    CloudyAppearance.System -> "Follow the Android appearance setting"
    CloudyAppearance.Light -> "Use the light mineral surfaces"
    CloudyAppearance.Dark -> "Use the deep blue-green appearance"
}

private fun densityTitle(value: CloudyDensity): String = when (value) {
    CloudyDensity.Comfortable -> "Comfortable"
    CloudyDensity.StandardPointer -> "Standard pointer"
    CloudyDensity.CompactPointer -> "Compact pointer"
}

private fun densityDescription(value: CloudyDensity): String = when (value) {
    CloudyDensity.Comfortable -> "48 dp minimum targets · default"
    CloudyDensity.StandardPointer -> "40 dp minimum targets · precise pointer"
    CloudyDensity.CompactPointer -> "32 dp minimum targets · explicit high-density preference"
}
