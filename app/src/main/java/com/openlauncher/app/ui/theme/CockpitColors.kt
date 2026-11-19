package com.openlauncher.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Centralized semantic color palette for PumaLauncher's automotive cockpit UI.
 * Provided at the root via [LocalCockpitColors] and updated automatically for Dark/Light modes.
 */
@Immutable
data class CockpitColors(
    val isLight: Boolean,
    val canvasBackground: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val dockSurface: Color,
    val glassPillSurface: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val hairlineBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color
) {
    val cardBorderBrush: Brush
        get() = Brush.verticalGradient(listOf(borderTop, borderBottom))

    val overlayPanelSurface: Color
        get() = if (isLight) Color(0xF5FFFFFF) else Color(0xEB12161F)

    val dangerContainer: Color
        get() = if (isLight) Color(0xFFFEE2E2) else Color(0xFF2B0B0E)

    val dangerContent: Color
        get() = if (isLight) Color(0xFFDC2626) else Color(0xFFFF5252)
}

val DarkCockpitPalette = CockpitColors(
    isLight = false,
    canvasBackground = Color(0xFF0B0E14),
    cardSurface = Color(0xFF131720),
    elevatedSurface = Color(0xFF1A202C),
    dockSurface = Color(0xFF0E1219),
    glassPillSurface = Color(0xD912161F),
    borderTop = Color(0xFF323B4D),
    borderBottom = Color(0xFF181D28),
    hairlineBorder = Color(0xFF2A3241),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8)
)

val LightCockpitPalette = CockpitColors(
    isLight = true,
    canvasBackground = Color(0xFFEEF2F6),
    cardSurface = Color(0xFFFFFFFF),
    elevatedSurface = Color(0xFFF1F5F9),
    dockSurface = Color(0xFFF8FAFC),
    glassPillSurface = Color(0xEBFFFFFF),
    borderTop = Color(0xFFFFFFFF),
    borderBottom = Color(0xFFCBD5E1),
    hairlineBorder = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569)
)

val LocalCockpitColors = staticCompositionLocalOf { DarkCockpitPalette }
