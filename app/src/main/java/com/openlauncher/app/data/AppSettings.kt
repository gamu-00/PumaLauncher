package com.openlauncher.app.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.openlauncher.app.R
import kotlin.math.abs

enum class AppFont { SYSTEM, JETBRAINS_MONO, SOURCE_CODE_PRO }
enum class DayNightMode { DARK, LIGHT, AUTO, SYSTEM }
enum class GradientDirection { TOP_TO_BOTTOM, LEFT_TO_RIGHT, DIAGONAL, RADIAL }
enum class DrivingAvatar { CLASSIC_ARROW, PUMA_ST_BLUE, SPORT_RED }

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

val UI_SCALE_PRESETS: List<Float> = listOf(0.65f, 0.80f, 1.00f, 1.20f, 1.40f, 1.60f)
const val DEFAULT_UI_SCALE: Float = 1.00f
const val DEFAULT_TEXT_SCALE: Float = 1.00f
const val MIN_TEXT_SCALE: Float = 0.85f
const val MAX_TEXT_SCALE: Float = 1.20f
const val DEFAULT_MEDIA_PAUSE_TIMEOUT_MINUTES: Int = 5
const val MIN_MEDIA_PAUSE_TIMEOUT_MINUTES: Int = 1
const val MAX_MEDIA_PAUSE_TIMEOUT_MINUTES: Int = 30

fun snapUiScalePreset(value: Float): Float {
    if (!value.isFinite()) return DEFAULT_UI_SCALE
    return UI_SCALE_PRESETS.minByOrNull { abs(it - value) } ?: DEFAULT_UI_SCALE
}

fun harmonizedTextScaleForUiScale(uiScale: Float): Float = when (snapUiScalePreset(uiScale)) {
    0.65f -> 1.08f
    0.80f -> 1.04f
    1.00f -> 1.00f
    1.20f -> 0.97f
    1.40f -> 0.94f
    1.60f -> 0.90f
    else  -> 1.00f
}

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
    val textScale: Float = DEFAULT_TEXT_SCALE,
    val uiScale: Float = DEFAULT_UI_SCALE,
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
    val voiceGuidanceEnabled: Boolean = true,
    val nav3dViewEnabled: Boolean = true,
    val drivingAvatar: DrivingAvatar = DrivingAvatar.CLASSIC_ARROW,
    val homeAddressLabel: String? = null,
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    // Media Station & weather contextual pause timeout
    val mediaPauseTimeoutMinutes: Int = DEFAULT_MEDIA_PAUSE_TIMEOUT_MINUTES
)

fun AppSettings.effectiveUiScale(): Float = snapUiScalePreset(uiScale)

fun AppSettings.effectiveFontScale(): Float =
    (textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE) * harmonizedTextScaleForUiScale(uiScale))
        .coerceIn(0.85f, 1.18f)

fun AppSettings.resolvedAccentColor(isLightTheme: Boolean = false): Color {
    if (isLightTheme && accentColorHex.equals("#2979FF", ignoreCase = true)) {
        return Color(0xFF1D4ED8)
    }
    return runCatching { Color(android.graphics.Color.parseColor(accentColorHex)) }
        .getOrElse { if (isLightTheme) Color(0xFF1D4ED8) else Color(0xFF2979FF) }
}

const val RAIL_SLOTS = 4

/** Nav Rail content: pinned apps first, then recents without duplicates, capped at [RAIL_SLOTS]. */
fun AppSettings.railPackages(): List<String> =
    (pinnedApps + recentApps).distinct().take(RAIL_SLOTS)
