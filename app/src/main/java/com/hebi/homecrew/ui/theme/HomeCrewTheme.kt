package com.hebi.homecrew.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import com.hebi.homecrew.R

enum class ThemeMode(val label: String) {
    SYSTEM("시스템"),
    LIGHT("라이트"),
    DARK("다크")
}

@Composable
internal fun homeCrewPlatformPickerTheme(): Int =
    if (LocalHomeCrewColors.current.background.luminance() < 0.5f) {
        R.style.Theme_HomeCrew_Picker_Dark
    } else {
        R.style.Theme_HomeCrew_Picker_Light
    }

private val LightScheme = lightColorScheme(
    primary = LightHomeCrewColors.ink,
    onPrimary = LightHomeCrewColors.surface,
    background = LightHomeCrewColors.background,
    onBackground = LightHomeCrewColors.ink,
    surface = LightHomeCrewColors.surface,
    onSurface = LightHomeCrewColors.ink,
    surfaceVariant = LightHomeCrewColors.subtle,
    onSurfaceVariant = LightHomeCrewColors.muted,
    outline = LightHomeCrewColors.border,
    error = LightHomeCrewColors.error,
    outlineVariant = LightHomeCrewColors.border,
    surfaceDim = LightHomeCrewColors.border,
    surfaceBright = LightHomeCrewColors.surface,
    surfaceContainerLowest = LightHomeCrewColors.surface,
    surfaceContainerLow = LightHomeCrewColors.background,
    surfaceContainer = LightHomeCrewColors.subtle,
    surfaceContainerHigh = LightHomeCrewColors.subtle,
    surfaceContainerHighest = LightHomeCrewColors.border,
    surfaceTint = LightHomeCrewColors.ink,
    primaryContainer = LightHomeCrewColors.subtle,
    onPrimaryContainer = LightHomeCrewColors.ink,
    secondary = LightHomeCrewColors.ink,
    onSecondary = LightHomeCrewColors.surface,
    secondaryContainer = LightHomeCrewColors.subtle,
    onSecondaryContainer = LightHomeCrewColors.ink,
    tertiary = LightHomeCrewColors.muted,
    onTertiary = LightHomeCrewColors.surface,
    tertiaryContainer = LightHomeCrewColors.subtle,
    onTertiaryContainer = LightHomeCrewColors.ink,
    inverseSurface = DarkHomeCrewColors.surface,
    inverseOnSurface = DarkHomeCrewColors.ink,
    inversePrimary = DarkHomeCrewColors.ink
)

private val DarkScheme = darkColorScheme(
    primary = DarkHomeCrewColors.ink,
    onPrimary = DarkHomeCrewColors.surface,
    background = DarkHomeCrewColors.background,
    onBackground = DarkHomeCrewColors.ink,
    surface = DarkHomeCrewColors.surface,
    onSurface = DarkHomeCrewColors.ink,
    surfaceVariant = DarkHomeCrewColors.subtle,
    onSurfaceVariant = DarkHomeCrewColors.muted,
    outline = DarkHomeCrewColors.border,
    error = DarkHomeCrewColors.error,
    outlineVariant = DarkHomeCrewColors.border,
    surfaceDim = DarkHomeCrewColors.background,
    surfaceBright = DarkHomeCrewColors.border,
    surfaceContainerLowest = DarkHomeCrewColors.background,
    surfaceContainerLow = DarkHomeCrewColors.surface,
    surfaceContainer = DarkHomeCrewColors.subtle,
    surfaceContainerHigh = DarkHomeCrewColors.subtle,
    surfaceContainerHighest = DarkHomeCrewColors.border,
    surfaceTint = DarkHomeCrewColors.ink,
    primaryContainer = DarkHomeCrewColors.subtle,
    onPrimaryContainer = DarkHomeCrewColors.ink,
    secondary = DarkHomeCrewColors.ink,
    onSecondary = DarkHomeCrewColors.surface,
    secondaryContainer = DarkHomeCrewColors.subtle,
    onSecondaryContainer = DarkHomeCrewColors.ink,
    tertiary = DarkHomeCrewColors.muted,
    onTertiary = DarkHomeCrewColors.surface,
    tertiaryContainer = DarkHomeCrewColors.subtle,
    onTertiaryContainer = DarkHomeCrewColors.ink,
    inverseSurface = LightHomeCrewColors.surface,
    inverseOnSurface = LightHomeCrewColors.ink,
    inversePrimary = LightHomeCrewColors.ink
)

private val HomeCrewShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(6.dp)
)

@Composable
fun HomeCrewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = if (darkTheme) DarkHomeCrewColors else LightHomeCrewColors
    val scheme = if (darkTheme) DarkScheme else LightScheme

    CompositionLocalProvider(LocalHomeCrewColors provides tokens) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HomeCrewTypography,
            shapes = HomeCrewShapes,
            content = content
        )
    }
}
