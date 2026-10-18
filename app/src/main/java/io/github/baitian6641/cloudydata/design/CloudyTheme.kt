package io.github.baitian6641.cloudydata.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.baitian6641.cloudydata.R

enum class CloudyAppearance { System, Light, Dark }

enum class CloudyDensity { Comfortable, StandardPointer, CompactPointer }

@Immutable
data class CloudyDensityMetrics(
    val minTargetDp: Float,
    val spaceSmallDp: Float,
    val spaceMediumDp: Float,
    val spaceLargeDp: Float,
) {
    val minTarget = minTargetDp.dp
    val spaceSmall = spaceSmallDp.dp
    val spaceMedium = spaceMediumDp.dp
    val spaceLarge = spaceLargeDp.dp
}

@Immutable
data class CloudySemanticColors(
    val canvas: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val outline: Color,
    val divider: Color,
    val focus: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val disabledForeground: Color,
    val disabledBackground: Color,
    val disabledBorder: Color,
)

val LocalCloudyColors = staticCompositionLocalOf<CloudySemanticColors> {
    error("CloudyColors not provided")
}

val LocalCloudyDensity = staticCompositionLocalOf {
    metricsFor(CloudyDensity.Comfortable)
}

fun metricsFor(density: CloudyDensity): CloudyDensityMetrics = when (density) {
    CloudyDensity.Comfortable -> CloudyDensityMetrics(
        CloudyTokens.Density.Comfortable.minTargetDp,
        CloudyTokens.Density.Comfortable.spaceSmallDp,
        CloudyTokens.Density.Comfortable.spaceMediumDp,
        CloudyTokens.Density.Comfortable.spaceLargeDp,
    )
    CloudyDensity.StandardPointer -> CloudyDensityMetrics(
        CloudyTokens.Density.StandardPointer.minTargetDp,
        CloudyTokens.Density.StandardPointer.spaceSmallDp,
        CloudyTokens.Density.StandardPointer.spaceMediumDp,
        CloudyTokens.Density.StandardPointer.spaceLargeDp,
    )
    CloudyDensity.CompactPointer -> CloudyDensityMetrics(
        CloudyTokens.Density.CompactPointer.minTargetDp,
        CloudyTokens.Density.CompactPointer.spaceSmallDp,
        CloudyTokens.Density.CompactPointer.spaceMediumDp,
        CloudyTokens.Density.CompactPointer.spaceLargeDp,
    )
}

private fun semanticColors(dark: Boolean): CloudySemanticColors = if (dark) {
    CloudySemanticColors(
        canvas = Color(CloudyTokens.Dark.canvas),
        surface = Color(CloudyTokens.Dark.surface),
        surfaceMuted = Color(CloudyTokens.Dark.surfaceMuted),
        surfaceRaised = Color(CloudyTokens.Dark.surfaceRaised),
        textPrimary = Color(CloudyTokens.Dark.textPrimary),
        textSecondary = Color(CloudyTokens.Dark.textSecondary),
        primary = Color(CloudyTokens.Dark.primary),
        onPrimary = Color(CloudyTokens.Dark.onPrimary),
        primaryContainer = Color(CloudyTokens.Dark.primaryContainer),
        onPrimaryContainer = Color(CloudyTokens.Dark.onPrimaryContainer),
        accent = Color(CloudyTokens.Dark.accent),
        accentContainer = Color(CloudyTokens.Dark.accentContainer),
        onAccentContainer = Color(CloudyTokens.Dark.onAccentContainer),
        outline = Color(CloudyTokens.Dark.outline),
        divider = Color(CloudyTokens.Dark.divider),
        focus = Color(CloudyTokens.Dark.focus),
        success = Color(CloudyTokens.Dark.success),
        successContainer = Color(CloudyTokens.Dark.successContainer),
        warning = Color(CloudyTokens.Dark.warning),
        warningContainer = Color(CloudyTokens.Dark.warningContainer),
        danger = Color(CloudyTokens.Dark.danger),
        dangerContainer = Color(CloudyTokens.Dark.dangerContainer),
        disabledForeground = Color(CloudyTokens.Dark.disabledForeground),
        disabledBackground = Color(CloudyTokens.Dark.disabledBackground),
        disabledBorder = Color(CloudyTokens.Dark.disabledBorder),
    )
} else {
    CloudySemanticColors(
        canvas = Color(CloudyTokens.Light.canvas),
        surface = Color(CloudyTokens.Light.surface),
        surfaceMuted = Color(CloudyTokens.Light.surfaceMuted),
        surfaceRaised = Color(CloudyTokens.Light.surfaceRaised),
        textPrimary = Color(CloudyTokens.Light.textPrimary),
        textSecondary = Color(CloudyTokens.Light.textSecondary),
        primary = Color(CloudyTokens.Light.primary),
        onPrimary = Color(CloudyTokens.Light.onPrimary),
        primaryContainer = Color(CloudyTokens.Light.primaryContainer),
        onPrimaryContainer = Color(CloudyTokens.Light.onPrimaryContainer),
        accent = Color(CloudyTokens.Light.accent),
        accentContainer = Color(CloudyTokens.Light.accentContainer),
        onAccentContainer = Color(CloudyTokens.Light.onAccentContainer),
        outline = Color(CloudyTokens.Light.outline),
        divider = Color(CloudyTokens.Light.divider),
        focus = Color(CloudyTokens.Light.focus),
        success = Color(CloudyTokens.Light.success),
        successContainer = Color(CloudyTokens.Light.successContainer),
        warning = Color(CloudyTokens.Light.warning),
        warningContainer = Color(CloudyTokens.Light.warningContainer),
        danger = Color(CloudyTokens.Light.danger),
        dangerContainer = Color(CloudyTokens.Light.dangerContainer),
        disabledForeground = Color(CloudyTokens.Light.disabledForeground),
        disabledBackground = Color(CloudyTokens.Light.disabledBackground),
        disabledBorder = Color(CloudyTokens.Light.disabledBorder),
    )
}

val CloudyFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

val CloudyMonoFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
)

private fun cloudTypography(): Typography {
    fun role(size: Float, lineHeight: Float, weight: FontWeight) = TextStyle(
        fontFamily = CloudyFontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = 0.sp,
    )
    val page = role(CloudyTokens.Type.pageSizeSp, CloudyTokens.Type.pageLineHeightSp, FontWeight.Medium)
    val section = role(CloudyTokens.Type.sectionSizeSp, CloudyTokens.Type.sectionLineHeightSp, FontWeight.Medium)
    val body = role(CloudyTokens.Type.bodySizeSp, CloudyTokens.Type.bodyLineHeightSp, FontWeight.Normal)
    val label = role(CloudyTokens.Type.labelSizeSp, CloudyTokens.Type.labelLineHeightSp, FontWeight.Medium)
    val metadata = role(CloudyTokens.Type.metadataSizeSp, CloudyTokens.Type.metadataLineHeightSp, FontWeight.Normal)
    return Typography(
        displayLarge = page,
        displayMedium = page,
        displaySmall = section,
        headlineLarge = page,
        headlineMedium = section,
        headlineSmall = section,
        titleLarge = section,
        titleMedium = label,
        titleSmall = label,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = metadata,
        labelLarge = label,
        labelMedium = label,
        labelSmall = metadata,
    )
}

private val CloudyShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(CloudyTokens.Shape.field.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(CloudyTokens.Shape.button.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(CloudyTokens.Shape.menu.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(CloudyTokens.Shape.dialog.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(CloudyTokens.Shape.dialog.dp),
)

@Composable
fun CloudyTheme(
    appearance: CloudyAppearance = CloudyAppearance.System,
    density: CloudyDensity = CloudyDensity.Comfortable,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        CloudyAppearance.System -> isSystemInDarkTheme()
        CloudyAppearance.Light -> false
        CloudyAppearance.Dark -> true
    }
    val colors = semanticColors(dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.accent,
            onSecondary = colors.surface,
            secondaryContainer = colors.accentContainer,
            onSecondaryContainer = colors.onAccentContainer,
            tertiary = colors.primary,
            onTertiary = colors.onPrimary,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.canvas,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surfaceMuted,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceRaised,
            outline = colors.outline,
            outlineVariant = colors.divider,
            error = colors.danger,
            onError = colors.surface,
            errorContainer = colors.dangerContainer,
            onErrorContainer = colors.danger,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.accent,
            onSecondary = Color.White,
            secondaryContainer = colors.accentContainer,
            onSecondaryContainer = colors.onAccentContainer,
            tertiary = colors.primary,
            onTertiary = colors.onPrimary,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.surface,
            surfaceContainerLow = colors.surfaceMuted,
            surfaceContainer = colors.surfaceMuted,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceRaised,
            outline = colors.outline,
            outlineVariant = colors.divider,
            error = colors.danger,
            onError = Color.White,
            errorContainer = colors.dangerContainer,
            onErrorContainer = colors.danger,
            scrim = Color.Black,
        )
    }
    CompositionLocalProvider(
        LocalCloudyColors provides colors,
        LocalCloudyDensity provides metricsFor(density),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = cloudTypography(),
            shapes = CloudyShapes,
            content = content,
        )
    }
}
