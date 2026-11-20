package com.openlauncher.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val VEHICLE_NAME              = stringPreferencesKey("vehicle_name")
        val ACCENT_COLOR_HEX          = stringPreferencesKey("accent_color_hex")
        val ACCENT_COLOR              = intPreferencesKey("accent_color")
        val BG_COLOR                  = intPreferencesKey("bg_color")
        val FONT_COLOR                = intPreferencesKey("font_color")
        val CUSTOM_DARK_FONT_COLOR    = intPreferencesKey("custom_dark_font_color")
        val CUSTOM_LIGHT_FONT_COLOR   = intPreferencesKey("custom_light_font_color")
        val WALLPAPER_URI             = stringPreferencesKey("wallpaper_uri")
        val FONT_BOLD                 = booleanPreferencesKey("font_bold")
        val TEXT_SCALE                = floatPreferencesKey("text_scale")
        val UI_SCALE                  = floatPreferencesKey("ui_scale")
        val APP_FONT                  = stringPreferencesKey("app_font")
        val USE_GRADIENT              = booleanPreferencesKey("use_gradient")
        val GRADIENT_END_COLOR        = intPreferencesKey("gradient_end_color")
        val WALLPAPER_DIM             = floatPreferencesKey("wallpaper_dim")
        val DAY_NIGHT_MODE            = stringPreferencesKey("day_night_mode")
        val ONBOARDING_COMPLETED      = booleanPreferencesKey("onboarding_completed")
        val GRADIENT_DIRECTION        = stringPreferencesKey("gradient_direction")
        val USE_CUSTOM_BG_COLOR       = booleanPreferencesKey("use_custom_bg_color")
        val COLD_BOOT_AUTO_LAUNCH     = booleanPreferencesKey("cold_boot_auto_launch")
        val COLD_BOOT_DELAY_SECONDS   = intPreferencesKey("cold_boot_delay_seconds")
        val PINNED_APPS_JSON          = stringPreferencesKey("pinned_apps_json")
        val RECENT_APPS_JSON          = stringPreferencesKey("recent_apps_json")
        val PINNED_INITIALIZED        = booleanPreferencesKey("pinned_initialized")
        val HIDDEN_PACKAGES_JSON      = stringPreferencesKey("hidden_packages_json")
        val SHOW_HIDDEN_APPS          = booleanPreferencesKey("show_hidden_apps")
        val AVOID_TOLLS                 = booleanPreferencesKey("avoid_tolls")
        val VOICE_GUIDANCE_ENABLED      = booleanPreferencesKey("voice_guidance_enabled")
        val MEDIA_PAUSE_TIMEOUT_MINUTES = intPreferencesKey("media_pause_timeout_minutes")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> readSettings(prefs) }

    private fun readSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        val accentHex = prefs[Keys.ACCENT_COLOR_HEX] ?: defaults.accentColorHex
        val parsedAccentInt = runCatching { android.graphics.Color.parseColor(accentHex) }.getOrNull()
            ?: prefs[Keys.ACCENT_COLOR]
            ?: defaults.accentColor

        val legacyFontColor = prefs[Keys.FONT_COLOR]
        val customDarkFontColor = prefs[Keys.CUSTOM_DARK_FONT_COLOR]
            ?: legacyFontColor?.takeIf {
                it != android.graphics.Color.WHITE && it != 0xFFF8FAFC.toInt()
            }
        val customLightFontColor = prefs[Keys.CUSTOM_LIGHT_FONT_COLOR]

        return AppSettings(
            vehicleName              = prefs[Keys.VEHICLE_NAME] ?: defaults.vehicleName,
            accentColorHex           = accentHex,
            accentColor              = parsedAccentInt,
            backgroundColor          = prefs[Keys.BG_COLOR] ?: defaults.backgroundColor,
            fontColor                = legacyFontColor ?: defaults.fontColor,
            customDarkFontColor      = customDarkFontColor,
            customLightFontColor     = customLightFontColor,
            wallpaperUri             = prefs[Keys.WALLPAPER_URI] ?: defaults.wallpaperUri,
            fontBold                 = prefs[Keys.FONT_BOLD] ?: defaults.fontBold,
            textScale                = (prefs[Keys.TEXT_SCALE] ?: defaults.textScale).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
            uiScale                  = snapUiScalePreset(prefs[Keys.UI_SCALE] ?: defaults.uiScale),
            appFont                  = prefs[Keys.APP_FONT]?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: defaults.appFont,
            useGradient              = prefs[Keys.USE_GRADIENT] ?: defaults.useGradient,
            gradientEndColor         = prefs[Keys.GRADIENT_END_COLOR] ?: defaults.gradientEndColor,
            wallpaperDim             = prefs[Keys.WALLPAPER_DIM] ?: defaults.wallpaperDim,
            dayNightMode             = prefs[Keys.DAY_NIGHT_MODE]?.let { runCatching { DayNightMode.valueOf(it) }.getOrNull() } ?: defaults.dayNightMode,
            onboardingCompleted      = prefs[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            gradientDirection        = prefs[Keys.GRADIENT_DIRECTION]?.let { runCatching { GradientDirection.valueOf(it) }.getOrNull() } ?: defaults.gradientDirection,
            useCustomBackgroundColor = prefs[Keys.USE_CUSTOM_BG_COLOR] ?: defaults.useCustomBackgroundColor,
            coldBootAutoLaunch       = prefs[Keys.COLD_BOOT_AUTO_LAUNCH] ?: defaults.coldBootAutoLaunch,
            coldBootDelaySeconds     = (prefs[Keys.COLD_BOOT_DELAY_SECONDS] ?: defaults.coldBootDelaySeconds).coerceIn(1, 30),
            pinnedApps               = readStringList(prefs[Keys.PINNED_APPS_JSON]),
            recentApps               = readStringList(prefs[Keys.RECENT_APPS_JSON]),
            pinnedInitialized        = prefs[Keys.PINNED_INITIALIZED] ?: defaults.pinnedInitialized,
            hiddenPackages           = readStringList(prefs[Keys.HIDDEN_PACKAGES_JSON]),
            showHiddenApps           = prefs[Keys.SHOW_HIDDEN_APPS] ?: defaults.showHiddenApps,
            avoidTolls                 = prefs[Keys.AVOID_TOLLS] ?: defaults.avoidTolls,
            voiceGuidanceEnabled       = prefs[Keys.VOICE_GUIDANCE_ENABLED] ?: defaults.voiceGuidanceEnabled,
            mediaPauseTimeoutMinutes   = (prefs[Keys.MEDIA_PAUSE_TIMEOUT_MINUTES] ?: defaults.mediaPauseTimeoutMinutes)
                .coerceIn(MIN_MEDIA_PAUSE_TIMEOUT_MINUTES, MAX_MEDIA_PAUSE_TIMEOUT_MINUTES)
        )
    }

    private fun readStringList(json: String?): List<String> {
        if (json == null) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
        }.getOrNull()?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun saveSettings(s: AppSettings) {
        context.dataStore.edit { prefs -> writeSettings(prefs, s) }
    }

    /**
     * Atomic read-modify-write. DataStore serializes edit blocks, so concurrent
     * updates can't overwrite each other (unlike transforming a stale snapshot).
     */
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> writeSettings(prefs, transform(readSettings(prefs))) }
    }

    private fun writeSettings(prefs: MutablePreferences, s: AppSettings) {
        prefs[Keys.VEHICLE_NAME]            = s.vehicleName
        prefs[Keys.ACCENT_COLOR_HEX]        = s.accentColorHex
        prefs[Keys.ACCENT_COLOR]            = s.accentColor
        prefs[Keys.BG_COLOR]                = s.backgroundColor
        if (s.customDarkFontColor != null) {
            prefs[Keys.CUSTOM_DARK_FONT_COLOR] = s.customDarkFontColor
            prefs[Keys.FONT_COLOR]             = s.customDarkFontColor
        } else {
            prefs.remove(Keys.CUSTOM_DARK_FONT_COLOR)
            prefs.remove(Keys.FONT_COLOR)
        }
        if (s.customLightFontColor != null) {
            prefs[Keys.CUSTOM_LIGHT_FONT_COLOR] = s.customLightFontColor
        } else {
            prefs.remove(Keys.CUSTOM_LIGHT_FONT_COLOR)
        }
        prefs[Keys.WALLPAPER_URI]           = s.wallpaperUri
        prefs[Keys.FONT_BOLD]               = s.fontBold
        prefs[Keys.TEXT_SCALE]              = s.textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        prefs[Keys.UI_SCALE]                = snapUiScalePreset(s.uiScale)
        prefs[Keys.APP_FONT]                = s.appFont.name
        prefs[Keys.USE_GRADIENT]            = s.useGradient
        prefs[Keys.GRADIENT_END_COLOR]      = s.gradientEndColor
        prefs[Keys.WALLPAPER_DIM]           = s.wallpaperDim
        prefs[Keys.DAY_NIGHT_MODE]          = s.dayNightMode.name
        prefs[Keys.ONBOARDING_COMPLETED]    = s.onboardingCompleted
        prefs[Keys.GRADIENT_DIRECTION]      = s.gradientDirection.name
        prefs[Keys.USE_CUSTOM_BG_COLOR]     = s.useCustomBackgroundColor
        prefs[Keys.COLD_BOOT_AUTO_LAUNCH]   = s.coldBootAutoLaunch
        prefs[Keys.COLD_BOOT_DELAY_SECONDS] = s.coldBootDelaySeconds.coerceIn(1, 30)
        prefs[Keys.PINNED_APPS_JSON]        = gson.toJson(s.pinnedApps)
        prefs[Keys.RECENT_APPS_JSON]        = gson.toJson(s.recentApps)
        prefs[Keys.PINNED_INITIALIZED]      = s.pinnedInitialized
        prefs[Keys.HIDDEN_PACKAGES_JSON]    = gson.toJson(s.hiddenPackages)
        prefs[Keys.SHOW_HIDDEN_APPS]        = s.showHiddenApps
        prefs[Keys.AVOID_TOLLS]                  = s.avoidTolls
        prefs[Keys.VOICE_GUIDANCE_ENABLED]       = s.voiceGuidanceEnabled
        prefs[Keys.MEDIA_PAUSE_TIMEOUT_MINUTES]  = s.mediaPauseTimeoutMinutes
            .coerceIn(MIN_MEDIA_PAUSE_TIMEOUT_MINUTES, MAX_MEDIA_PAUSE_TIMEOUT_MINUTES)
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
