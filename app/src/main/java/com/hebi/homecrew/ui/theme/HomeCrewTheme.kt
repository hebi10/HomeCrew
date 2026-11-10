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

enum class ThemeMode(val label: String) {
    SYSTEM("시스템"),
    LIGHT("라이트"),
    DARK("다크")
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
    error = LightHomeCrewColors.error
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
    error = DarkHomeCrewColors.error
)

private val HomeCrewShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp)
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
