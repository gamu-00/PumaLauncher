package com.openlauncher.app.ui.theme

import androidx.compose.ui.graphics.Color

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

val accentPresets      = listOf(AccentBlue, AccentCyan, AccentGreen, AccentAmber, AccentRed, AccentWhite)
val accentPresetLabels = listOf("Blu Performance", "Ciano Ghiaccio", "Verde Smeraldo", "Ambra Mini AA", "Rosso Racing", "Bianco Titanio")
