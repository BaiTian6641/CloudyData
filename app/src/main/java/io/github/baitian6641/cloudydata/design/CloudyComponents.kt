package io.github.baitian6641.cloudydata.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baitian6641.cloudydata.design.LocalCloudyColors
import io.github.baitian6641.cloudydata.design.LocalCloudyDensity

@Composable
fun CloudyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(CloudyTokens.Shape.button.dp)
    Row(
        modifier = modifier
            .heightIn(min = density.minTarget)
            .clip(shape)
            .background(if (enabled) colors.primary else colors.disabledBackground)
            .border(
                width = if (focused) CloudyTokens.Geometry.focusRingDp.dp else 0.dp,
                color = if (focused) colors.focus else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = density.spaceLarge, vertical = density.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) colors.onPrimary else colors.disabledForeground,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(density.spaceSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.onPrimary else colors.disabledForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CloudySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(CloudyTokens.Shape.button.dp)
    Row(
        modifier = modifier
            .heightIn(min = density.minTarget)
            .clip(shape)
            .background(colors.surface)
            .border(
                width = if (focused) CloudyTokens.Geometry.focusRingDp.dp else 1.dp,
                color = when {
                    focused -> colors.focus
                    enabled -> colors.outline
                    else -> colors.disabledBorder
                },
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = density.spaceLarge, vertical = density.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) colors.textPrimary else colors.disabledForeground,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(density.spaceSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.textPrimary else colors.disabledForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CloudyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .heightIn(min = density.minTarget)
            .clip(RoundedCornerShape(CloudyTokens.Shape.button.dp))
            .background(if (focused) colors.surfaceRaised else Color.Transparent)
            .border(
                width = if (focused) CloudyTokens.Geometry.focusRingDp.dp else 0.dp,
                color = if (focused) colors.focus else Color.Transparent,
                shape = RoundedCornerShape(CloudyTokens.Shape.button.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = density.spaceMedium, vertical = density.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.primary else colors.disabledForeground,
            maxLines = 1,
        )
    }
}

@Composable
fun CloudyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(CloudyTokens.Shape.button.dp)
    Box(
        modifier = modifier
            .size(density.minTarget)
            .clip(shape)
            .background(if (focused) colors.surfaceRaised else Color.Transparent)
            .border(
                width = if (focused) CloudyTokens.Geometry.focusRingDp.dp else 0.dp,
                color = if (focused) colors.focus else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.textPrimary else colors.disabledForeground,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun CloudyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = trailing,
        shape = RoundedCornerShape(CloudyTokens.Shape.field.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            errorContainerColor = colors.surface,
            focusedBorderColor = colors.focus,
            unfocusedBorderColor = colors.outline,
            errorBorderColor = colors.danger,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            errorTextColor = colors.textPrimary,
            focusedLabelColor = colors.focus,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.focus,
            focusedSupportingTextColor = colors.textSecondary,
            unfocusedSupportingTextColor = colors.textSecondary,
            errorSupportingTextColor = colors.danger,
        ),
    )
}

@Composable
fun CloudyNavRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = density.minTarget)
            .clip(RoundedCornerShape(CloudyTokens.Shape.button.dp))
            .background(
                when {
                    focused -> colors.accentContainer
                    selected -> colors.primaryContainer
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused) CloudyTokens.Geometry.focusRingDp.dp else 0.dp,
                color = if (focused) colors.focus else Color.Transparent,
                shape = RoundedCornerShape(CloudyTokens.Shape.button.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = density.spaceMedium, vertical = density.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.onPrimaryContainer else colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(density.spaceSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimaryContainer else colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.onPrimaryContainer else colors.textSecondary,
            )
        }
    }
}

@Composable
fun CloudySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = LocalCloudyColors.current.textSecondary,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun CloudyDivider(modifier: Modifier = Modifier, vertical: Boolean = false) {
    val colors = LocalCloudyColors.current
    if (vertical) {
        Box(modifier.width(1.dp).fillMaxHeight().background(colors.divider))
    } else {
        Box(modifier.height(1.dp).fillMaxWidth().background(colors.divider))
    }
}

@Composable
fun CloudyBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: CloudyBadgeTone = CloudyBadgeTone.Neutral,
) {
    val colors = LocalCloudyColors.current
    val density = LocalCloudyDensity.current
    val background = when (tone) {
        CloudyBadgeTone.Neutral -> colors.surfaceMuted
        CloudyBadgeTone.Primary -> colors.primaryContainer
        CloudyBadgeTone.Success -> colors.successContainer
        CloudyBadgeTone.Warning -> colors.warningContainer
        CloudyBadgeTone.Danger -> colors.dangerContainer
    }
    val foreground = when (tone) {
        CloudyBadgeTone.Neutral -> colors.textSecondary
        CloudyBadgeTone.Primary -> colors.onPrimaryContainer
        CloudyBadgeTone.Success -> colors.success
        CloudyBadgeTone.Warning -> colors.warning
        CloudyBadgeTone.Danger -> colors.danger
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = modifier
            .clip(RoundedCornerShape(CloudyTokens.Shape.chip.dp))
            .background(background)
            .padding(horizontal = density.spaceMedium, vertical = 2.dp),
        maxLines = 1,
    )
}

enum class CloudyBadgeTone { Neutral, Primary, Success, Warning, Danger }

@Composable
fun CloudyPane(
    modifier: Modifier = Modifier,
    background: Color = LocalCloudyColors.current.surface,
    content: @Composable () -> Unit,
) {
    Box(modifier.background(background)) { content() }
}

@Composable
fun CloudyToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalCloudyColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CloudyTokens.Geometry.toolbarDp.dp)
            .background(colors.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
