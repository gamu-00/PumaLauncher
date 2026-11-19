package com.openlauncher.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.openlauncher.app.R

// Mini AA night palette (Material 3 automotive dark)
val AaBackground   = Color(0xFF0A0B0E)   // deep black base
val AaPanel        = Color(0xFF13161B)   // opaque charcoal panel
val AaPanelHigh    = Color(0xFF1B1F26)   // raised panel / card
val AaBorder       = Color(0xFF272C35)   // thin border
val AaAmber        = Color(0xFF2979FF)   // default Blu Performance primary accent
val AaAmberAlt     = Color(0xFFFF8F00)   // Ambra Mini AA
val AaAmberLight   = Color(0xFF00E5FF)   // Ciano Ghiaccio
val AaTextPrimary  = Color(0xFFF2F4F7)
val AaTextSecondary = Color(0xFF9AA3B2)

// Modern OEM Cockpit palette
val CockpitCardBg             = Color(0xFF131720)
val CockpitBorderTop          = Color(0xFF323B4D)
val CockpitBorderBottom       = Color(0xFF181D28)
val CockpitOverlayBg          = Color(0xD912161F)
val CockpitCardBorderBrush    = Brush.verticalGradient(listOf(CockpitBorderTop, CockpitBorderBottom))
val CockpitOverlayBorderBrush = Brush.verticalGradient(
    listOf(
        CockpitBorderTop.copy(alpha = 0.85f),
        CockpitBorderBottom.copy(alpha = 0.85f)
    )
)

// Music card playback progress accent
val MediaMusicAccent          = Color(0xFF38BDF8)

val Black       = Color(0xFF000000)
val White       = Color(0xFFFFFFFF)
val DimSurface  = AaPanel
val CardSurface = AaPanelHigh
val DividerGray = AaBorder
val TextMuted   = AaTextSecondary

// Preset accent colours
val AccentBlack    = Color(0xFF000000)
val AccentWhite    = Color(0xFFE0E0E0)
val AccentBlue     = Color(0xFF2979FF)
val AccentCyan     = Color(0xFF00E5FF)
val AccentGreen    = Color(0xFF00E676)
val AccentAmber    = Color(0xFFFF8F00)
val AccentRed      = Color(0xFFFF1744)

val accentPresets = listOf(AccentBlue, AccentCyan, AccentGreen, AccentAmber, AccentRed, AccentWhite)
val accentPresetLabelRes = listOf(
    R.string.theme_preset_blue,
    R.string.theme_preset_cyan,
    R.string.theme_preset_green,
    R.string.theme_preset_amber,
    R.string.theme_preset_red,
    R.string.theme_preset_white
)
