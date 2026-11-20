package com.openlauncher.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

data class AppLanguageOption(
    val code: String,
    val nativeName: String,
    val flagEmoji: String,
    val badgeCode: String = code.uppercase(Locale.ROOT)
)

/**
 * Manages the unified 9-language preference (`en`, `de`, `it`, `fr`, `es`, `pt`, `pl`, `ru`, `uk`)
 * stored in SharedPreferences("launcher_prefs", key="app_language", default="en").
 */
object AppLanguageManager {
    const val PREFS_NAME = "launcher_prefs"
    const val KEY_APP_LANGUAGE = "app_language"
    const val DEFAULT_LANGUAGE = "en"

    val SUPPORTED_LANGUAGES = listOf(
        AppLanguageOption("en", "English", "\uD83C\uDDEC\uD83C\uDDE7"),
        AppLanguageOption("de", "Deutsch", "\uD83C\uDDE9\uD83C\uDDEA"),
        AppLanguageOption("it", "Italiano", "\uD83C\uDDEE\uD83C\uDDF9"),
        AppLanguageOption("fr", "Français", "\uD83C\uDDEB\uD83C\uDDF7"),
        AppLanguageOption("es", "Español", "\uD83C\uDDEA\uD83C\uDDF8"),
        AppLanguageOption("pt", "Português", "\uD83C\uDDF5\uD83C\uDDF9"),
        AppLanguageOption("pl", "Polski", "\uD83C\uDDF5\uD83C\uDDF1"),
        AppLanguageOption("ru", "Русский", "\uD83C\uDDF7\uD83C\uDDFA"),
        AppLanguageOption("uk", "Українська", "\uD83C\uDDFA\uD83C\uDDE6")
    )

    private val supportedCodes = SUPPORTED_LANGUAGES.map { it.code }.toSet()

    fun normalizeCode(raw: String?): String {
        val clean = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return if (clean in supportedCodes) clean else DEFAULT_LANGUAGE
    }

    fun getSavedLanguage(context: Context): String {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE)
        return normalizeCode(raw)
    }

    fun saveLanguage(context: Context, languageCode: String): String {
        val normalized = normalizeCode(languageCode)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, normalized)
            .commit()
        return normalized
    }

    fun localeFor(languageCode: String): Locale =
        Locale.forLanguageTag(normalizeCode(languageCode))

    fun wrapContext(base: Context): Context {
        val lang = getSavedLanguage(base)
        val locale = localeFor(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    fun localizedString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        val localizedCtx = wrapContext(context)
        return if (formatArgs.isEmpty()) {
            localizedCtx.getString(resId)
        } else {
            localizedCtx.getString(resId, *formatArgs)
        }
    }

    fun applyLanguage(activity: Activity?, context: Context, languageCode: String) {
        val normalized = saveLanguage(context, languageCode)
        val locale = localeFor(normalized)
        Locale.setDefault(locale)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
        activity?.recreate()
    }

    tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}
