package com.openlauncher.app.mediastation.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.mediaStationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "media_station_prefs"
)

data class MediaStationSettings(
    val stremioAuthKey: String = "",
    val addonBaseUrl: String = ""
)

/**
 * Strips whitespace, trailing `/manifest.json`, and trailing `/` characters from an addon URL.
 */
fun normalizeAddonBaseUrl(rawUrl: String): String {
    var normalized = rawUrl.trim()
    while (normalized.endsWith("/")) {
        normalized = normalized.dropLast(1)
    }
    if (normalized.endsWith("/manifest.json", ignoreCase = true)) {
        normalized = normalized.dropLast("/manifest.json".length)
    }
    while (normalized.endsWith("/")) {
        normalized = normalized.dropLast(1)
    }
    return normalized
}

/**
 * DataStore-backed persistence for Stremio credentials, addon resolver URL,
 * and offline JSON cache of the "Continue Watching" list.
 */
class MediaStationStorage(private val context: Context) {

    private val gson = Gson()
    private val posterListType = object : TypeToken<List<MediaPosterItem>>() {}.type

    private object Keys {
        val STREMIO_AUTH_KEY = stringPreferencesKey("stremio_auth_key")
        val ADDON_BASE_URL = stringPreferencesKey("addon_base_url")
        val CONTINUE_WATCHING_CACHE_JSON = stringPreferencesKey("continue_watching_cache_json")
    }

    val settingsFlow: Flow<MediaStationSettings> = context.mediaStationDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            MediaStationSettings(
                stremioAuthKey = prefs[Keys.STREMIO_AUTH_KEY].orEmpty().trim(),
                addonBaseUrl = normalizeAddonBaseUrl(prefs[Keys.ADDON_BASE_URL].orEmpty())
            )
        }

    val continueWatchingCacheFlow: Flow<List<MediaPosterItem>> = context.mediaStationDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val json = prefs[Keys.CONTINUE_WATCHING_CACHE_JSON]
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    gson.fromJson<List<MediaPosterItem>>(json, posterListType) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

    suspend fun saveSettings(authKey: String, addonBaseUrl: String) {
        val cleanAuth = authKey.trim()
        val cleanAddon = normalizeAddonBaseUrl(addonBaseUrl)
        context.mediaStationDataStore.edit { prefs ->
            prefs[Keys.STREMIO_AUTH_KEY] = cleanAuth
            prefs[Keys.ADDON_BASE_URL] = cleanAddon
        }
    }

    suspend fun saveContinueWatchingCache(items: List<MediaPosterItem>) {
        val json = gson.toJson(items, posterListType)
        context.mediaStationDataStore.edit { prefs ->
            prefs[Keys.CONTINUE_WATCHING_CACHE_JSON] = json
        }
    }
}
