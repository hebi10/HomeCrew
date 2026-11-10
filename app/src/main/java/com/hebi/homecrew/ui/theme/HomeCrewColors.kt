package com.hebi.homecrew.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class HomeCrewColors(
    val ink: Color,
    val surface: Color,
    val background: Color,
    val subtle: Color,
    val border: Color,
    val muted: Color,
    val success: Color,
    val warning: Color,
    val error: Color
)

val LightHomeCrewColors = HomeCrewColors(
    ink = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    subtle = Color(0xFFF5F5F5),
    border = Color(0xFFE5E5E5),
    muted = Color(0xFF717171),
    success = Color(0xFF24834A),
    warning = Color(0xFFAD5D15),
    error = Color(0xFFC52D35)
)

val DarkHomeCrewColors = HomeCrewColors(
    ink = Color(0xFFF5F5F5),
    surface = Color(0xFF1E1E1E),
    background = Color(0xFF121212),
    subtle = Color(0xFF262626),
    border = Color(0xFF404040),
    muted = Color(0xFFA3A3A3),
    success = Color(0xFF72D49A),
    warning = Color(0xFFFFBC78),
    error = Color(0xFFFF8B91)
)

val LocalHomeCrewColors = staticCompositionLocalOf { LightHomeCrewColors }
