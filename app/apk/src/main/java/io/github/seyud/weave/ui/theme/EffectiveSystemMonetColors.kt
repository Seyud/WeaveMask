package io.github.seyud.weave.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.Colors

private data class FrameworkMonetRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryFixed: Color,
    val onPrimaryFixed: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color,
    val onSurfaceVariant: Color,
)

@Composable
internal fun rememberEffectiveSystemMonetColors(dark: Boolean): Colors? {
    val context = LocalContext.current
    return remember(context, dark) {
        resolveEffectiveSystemMonetColors(context, dark)
    }
}

private fun resolveEffectiveSystemMonetColors(context: Context, dark: Boolean): Colors? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return null
    }

    val suffix = if (dark) "dark" else "light"

    val roles = FrameworkMonetRoles(
        primary = context.frameworkColor("system_primary_$suffix") ?: return null,
        onPrimary = context.frameworkColor("system_on_primary_$suffix") ?: return null,
        primaryFixed = context.frameworkColor("system_primary_fixed") ?: return null,
        onPrimaryFixed = context.frameworkColor("system_on_primary_fixed") ?: return null,
        error = context.frameworkColor("system_error_$suffix") ?: return null,
        onError = context.frameworkColor("system_on_error_$suffix") ?: return null,
        errorContainer = context.frameworkColor("system_error_container_$suffix") ?: return null,
        onErrorContainer = context.frameworkColor("system_on_error_container_$suffix") ?: return null,
        primaryContainer = context.frameworkColor("system_primary_container_$suffix") ?: return null,
        onPrimaryContainer = context.frameworkColor("system_on_primary_container_$suffix") ?: return null,
        secondaryContainer = context.frameworkColor("system_secondary_container_$suffix") ?: return null,
        onSecondaryContainer = context.frameworkColor("system_on_secondary_container_$suffix") ?: return null,
        tertiaryContainer = context.frameworkColor("system_tertiary_container_$suffix") ?: return null,
        onTertiaryContainer = context.frameworkColor("system_on_tertiary_container_$suffix") ?: return null,
        background = context.frameworkColor("system_background_$suffix") ?: return null,
        onBackground = context.frameworkColor("system_on_background_$suffix") ?: return null,
        surface = context.frameworkColor("system_surface_$suffix") ?: return null,
        onSurface = context.frameworkColor("system_on_surface_$suffix") ?: return null,
        surfaceVariant = context.frameworkColor("system_surface_variant_$suffix") ?: return null,
        surfaceContainer = context.frameworkColor("system_surface_container_$suffix") ?: return null,
        surfaceContainerHigh = context.frameworkColor("system_surface_container_high_$suffix") ?: return null,
        surfaceContainerHighest = context.frameworkColor("system_surface_container_highest_$suffix") ?: return null,
        outline = context.frameworkColor("system_outline_$suffix") ?: return null,
        outlineVariant = context.frameworkColor("system_outline_variant_$suffix") ?: return null,
        onSurfaceVariant = context.frameworkColor("system_on_surface_variant_$suffix") ?: return null,
    )

    return mapFrameworkRolesToMiuixColors(roles, dark)
}

private fun Context.frameworkColor(name: String): Color? {
    val resId = resources.getIdentifier(name, "color", "android")
    if (resId == 0) {
        return null
    }
    return Color(resources.getColor(resId, theme))
}

private fun compositeOver(fg: Color, bg: Color): Color {
    val fa = fg.alpha
    val ba = bg.alpha
    val outA = fa + ba * (1f - fa)
    if (outA == 0f) return Color(0f, 0f, 0f, 0f)
    val r = (fg.red * fa + bg.red * ba * (1f - fa)) / outA
    val g = (fg.green * fa + bg.green * ba * (1f - fa)) / outA
    val b = (fg.blue * fa + bg.blue * ba * (1f - fa)) / outA
    return Color(r, g, b, outA)
}

private fun opaqueOver(fg: Color, bg: Color): Color {
    val composed = compositeOver(fg, bg)
    return Color(composed.red, composed.green, composed.blue, 1f)
}

private fun ensureOpaqueOver(fg: Color, bg: Color): Color {
    return if (fg.alpha >= 1f) fg else opaqueOver(fg, bg)
}

private fun mapFrameworkRolesToMiuixColors(roles: FrameworkMonetRoles, dark: Boolean): Colors {
    val onSurfaceSecondaryOpaque = ensureOpaqueOver(roles.onSurface.copy(alpha = 0.8f), roles.surface)
    val onSurfaceContainerHighOpaque = ensureOpaqueOver(
        roles.onSurface.copy(alpha = 0.8f),
        roles.surfaceContainerHigh,
    )
    val sliderBackground = ensureOpaqueOver(roles.primary.copy(alpha = 0.2f), roles.surface)
    val disabledPrimaryOpaque = ensureOpaqueOver(roles.primary.copy(alpha = 0.38f), roles.surface)
    val disabledOnPrimaryOpaque = ensureOpaqueOver(roles.onPrimary.copy(alpha = 0.38f), disabledPrimaryOpaque)
    val disabledPrimaryButtonOpaque = ensureOpaqueOver(roles.primary.copy(alpha = 0.38f), roles.surface)
    val disabledOnPrimaryButtonOpaque = ensureOpaqueOver(
        roles.onPrimary.copy(alpha = 0.6f),
        disabledPrimaryButtonOpaque,
    )
    val disabledPrimarySliderOpaque = ensureOpaqueOver(roles.primary.copy(alpha = 0.38f), roles.surface)
    val disabledSecondaryOpaque = ensureOpaqueOver(roles.outlineVariant.copy(alpha = 0.5f), roles.surface)
    val disabledOnSecondaryOpaque = ensureOpaqueOver(
        roles.onSurface.copy(alpha = 0.38f),
        disabledSecondaryOpaque,
    )
    val disabledSecondaryVariantOpaque = ensureOpaqueOver(
        roles.surfaceContainerHigh.copy(alpha = 0.6f),
        roles.surface,
    )
    val disabledOnSecondaryVariantOpaque = ensureOpaqueOver(
        roles.onSurface.copy(alpha = 0.38f),
        disabledSecondaryVariantOpaque,
    )

    return Colors(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryVariant = roles.primaryFixed,
        onPrimaryVariant = roles.onPrimaryFixed,
        error = roles.error,
        onError = roles.onError,
        errorContainer = roles.errorContainer,
        onErrorContainer = roles.onErrorContainer,
        disabledPrimary = disabledPrimaryOpaque,
        disabledOnPrimary = disabledOnPrimaryOpaque,
        disabledPrimaryButton = disabledPrimaryButtonOpaque,
        disabledOnPrimaryButton = disabledOnPrimaryButtonOpaque,
        disabledPrimarySlider = disabledPrimarySliderOpaque,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer,
        secondary = roles.outlineVariant,
        onSecondary = roles.outline,
        secondaryVariant = roles.surfaceContainerHigh,
        onSecondaryVariant = roles.onSurface,
        disabledSecondary = disabledSecondaryOpaque,
        disabledOnSecondary = disabledOnSecondaryOpaque,
        disabledSecondaryVariant = disabledSecondaryVariantOpaque,
        disabledOnSecondaryVariant = disabledOnSecondaryVariantOpaque,
        secondaryContainer = roles.secondaryContainer,
        onSecondaryContainer = roles.onSecondaryContainer,
        secondaryContainerVariant = roles.surfaceContainerHighest,
        onSecondaryContainerVariant = roles.onSurfaceVariant,
        tertiaryContainer = roles.tertiaryContainer,
        onTertiaryContainer = roles.onTertiaryContainer,
        tertiaryContainerVariant = roles.onTertiaryContainer,
        background = roles.background,
        onBackground = roles.onBackground,
        onBackgroundVariant = roles.primary,
        surface = roles.surface,
        onSurface = roles.onSurface,
        surfaceVariant = roles.surfaceVariant,
        onSurfaceSecondary = onSurfaceSecondaryOpaque,
        onSurfaceVariantSummary = roles.onSurfaceVariant,
        onSurfaceVariantActions = roles.onSurfaceVariant,
        disabledOnSurface = roles.onSurface,
        surfaceContainer = roles.surfaceContainer,
        onSurfaceContainer = roles.onSurface,
        onSurfaceContainerVariant = roles.onSurfaceVariant,
        surfaceContainerHigh = roles.surfaceContainerHigh,
        onSurfaceContainerHigh = onSurfaceContainerHighOpaque,
        surfaceContainerHighest = roles.surfaceContainerHighest,
        onSurfaceContainerHighest = roles.onSurface,
        outline = roles.outline,
        dividerLine = roles.outlineVariant,
        windowDimming = if (dark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.3f),
        sliderKeyPoint = roles.primary,
        sliderKeyPointForeground = roles.surfaceContainerHigh,
        sliderBackground = sliderBackground,
    )
}
