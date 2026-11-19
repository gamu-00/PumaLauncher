package com.openlauncher.app.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.openlauncher.app.R

enum class AppFont { SYSTEM, JETBRAINS_MONO, SOURCE_CODE_PRO }
enum class DayNightMode { DARK, LIGHT, AUTO, SYSTEM }
enum class GradientDirection { TOP_TO_BOTTOM, LEFT_TO_RIGHT, DIAGONAL, RADIAL }

data class ThemeColorPreset(
    @StringRes val nameRes: Int,
    val hex: String,
    val color: Color
)

val THEME_COLOR_PRESETS = listOf(
    ThemeColorPreset(R.string.theme_preset_blue,  "#2979FF", Color(0xFF2979FF)),
    ThemeColorPreset(R.string.theme_preset_cyan,  "#00E5FF", Color(0xFF00E5FF)),
    ThemeColorPreset(R.string.theme_preset_green, "#00E676", Color(0xFF00E676)),
    ThemeColorPreset(R.string.theme_preset_amber, "#FF8F00", Color(0xFFFF8F00)),
    ThemeColorPreset(R.string.theme_preset_red,   "#FF1744", Color(0xFFFF1744)),
    ThemeColorPreset(R.string.theme_preset_white, "#E0E0E0", Color(0xFFE0E0E0))
)

data class AppSettings(
    val vehicleName: String = "MY CAR",
    val accentColorHex: String = "#2979FF",
    val accentColor: Int = Color(0xFF2979FF).toArgb(),
    val backgroundColor: Int = Color(0xFF0B0E14).toArgb(),
    val fontColor: Int = Color(0xFFF8FAFC).toArgb(),
    val customDarkFontColor: Int? = null,
    val customLightFontColor: Int? = null,
    val wallpaperUri: String = "",
    val fontBold: Boolean = false,
    val textScale: Float = 1.2f,
    val uiScale: Float = 1.0f,
    val appFont: AppFont = AppFont.SYSTEM,
    val useGradient: Boolean = false,
    val gradientEndColor: Int = Color.Black.toArgb(),
    val wallpaperDim: Float = 0.55f,
    val dayNightMode: DayNightMode = DayNightMode.DARK,
    val onboardingCompleted: Boolean = false,
    val gradientDirection: GradientDirection = GradientDirection.DIAGONAL,
    val useCustomBackgroundColor: Boolean = false,
    val coldBootAutoLaunch: Boolean = false,
    val coldBootDelaySeconds: Int = 4,
    // Nav Rail: pinned apps (persisted), recent apps launched from launcher/drawer
    val pinnedApps: List<String> = emptyList(),
    val recentApps: List<String> = emptyList(),
    val pinnedInitialized: Boolean = false,
    // App Drawer: hidden packages
    val hiddenPackages: List<String> = emptyList(),
    val showHiddenApps: Boolean = false,
    // Native Navigator preferences
    val avoidTolls: Boolean = false,
    val voiceGuidanceEnabled: Boolean = true
)

fun AppSettings.resolvedAccentColor(): Color =
    runCatching { Color(android.graphics.Color.parseColor(accentColorHex)) }
        .getOrElse { Color(0xFF2979FF) }

const val RAIL_SLOTS = 4

/** Nav Rail content: pinned apps first, then recents without duplicates, capped at [RAIL_SLOTS]. */
fun AppSettings.railPackages(): List<String> =
    (pinnedApps + recentApps).distinct().take(RAIL_SLOTS)
