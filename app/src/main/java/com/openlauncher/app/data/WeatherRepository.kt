package com.openlauncher.app.data

import android.os.SystemClock
import android.util.Log
import com.openlauncher.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class WmoConditionCategory(val labelResId: Int) {
    CLEAR(R.string.weather_clear),
    PARTLY_CLOUDY(R.string.weather_partly_cloudy),
    CLOUDY(R.string.weather_cloudy),
    FOG(R.string.weather_fog),
    RAIN(R.string.weather_rain),
    SNOW(R.string.weather_snow),
    THUNDERSTORM(R.string.weather_thunderstorm);

    companion object {
        fun fromWeatherCode(code: Int): WmoConditionCategory = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> PARTLY_CLOUDY
        }
    }
}

data class WeatherData(
    val temperatureC: Int,
    val apparentTemperatureC: Int,
    val weatherCode: Int,
    val windSpeedKmh: Int,
    val isDay: Boolean,
    val conditionCategory: WmoConditionCategory,
    val fetchedAtElapsedMs: Long,
    val latitude: Double,
    val longitude: Double
)

/**
 * Lightweight weather repository querying Open-Meteo (free, no API key) via OkHttp
 * using live GPS coordinates from [com.openlauncher.app.ui.screen.NavigationEngine]
 * and an intelligent in-memory cache (30-minute TTL or >10 km vehicle displacement).
 */
object WeatherRepository {

    private const val TAG = "PumaWeather"
    private const val CACHE_TTL_MS = 30L * 60L * 1000L // 30 minutes
    private const val CACHE_DISTANCE_THRESHOLD_KM = 10.0 // 10 km
    private const val USER_AGENT = "PumaLauncher/0.1.1-beta (Android)"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val fetchMutex = Mutex()

    private val _weatherState = MutableStateFlow<WeatherData?>(null)
    val weatherState: StateFlow<WeatherData?> = _weatherState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    /**
     * Fetches current weather if the in-memory cache is empty, older than 30 minutes,
     * the vehicle moved by more than 10 km, or [forceRefresh] is true.
     */
    suspend fun refreshIfNeeded(
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false
    ): WeatherData? = withContext(Dispatchers.IO) {
        if (lat.isNaN() || lon.isNaN()) return@withContext _weatherState.value

        val existing = _weatherState.value
        if (!forceRefresh && existing != null && isCacheValid(existing, lat, lon)) {
            return@withContext existing
        }

        fetchMutex.withLock {
            val current = _weatherState.value
            if (!forceRefresh && current != null && isCacheValid(current, lat, lon)) {
                return@withLock current
            }

            _isLoading.value = true
            try {
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day&timezone=auto",
                    lat,
                    lon
                )
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Open-Meteo HTTP ${response.code}")
                        _hasError.value = true
                        return@withLock _weatherState.value
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        _hasError.value = true
                        return@withLock _weatherState.value
                    }
                    val root = JSONObject(body)
                    val currentObj = root.optJSONObject("current")
                    if (currentObj == null) {
                        _hasError.value = true
                        return@withLock _weatherState.value
                    }

                    val temp = currentObj.optDouble("temperature_2m", Double.NaN)
                    if (temp.isNaN()) {
                        _hasError.value = true
                        return@withLock _weatherState.value
                    }
                    val apparent = currentObj.optDouble("apparent_temperature", temp)
                    val code = currentObj.optInt("weather_code", 0)
                    val wind = currentObj.optDouble("wind_speed_10m", 0.0)
                    val isDay = currentObj.optInt("is_day", 1) == 1

                    val fresh = WeatherData(
                        temperatureC = temp.roundToInt(),
                        apparentTemperatureC = apparent.roundToInt(),
                        weatherCode = code,
                        windSpeedKmh = wind.roundToInt().coerceAtLeast(0),
                        isDay = isDay,
                        conditionCategory = WmoConditionCategory.fromWeatherCode(code),
                        fetchedAtElapsedMs = SystemClock.elapsedRealtime(),
                        latitude = lat,
                        longitude = lon
                    )
                    _weatherState.value = fresh
                    _hasError.value = false
                    fresh
                }
            } catch (e: Exception) {
                Log.w(TAG, "Open-Meteo request failed: ${e.javaClass.simpleName}: ${e.message}")
                _hasError.value = true
                _weatherState.value
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun isCacheValid(cached: WeatherData, currentLat: Double, currentLon: Double): Boolean {
        val elapsedMs = SystemClock.elapsedRealtime() - cached.fetchedAtElapsedMs
        if (elapsedMs < 0L || elapsedMs > CACHE_TTL_MS) return false
        val movedKm = haversineKm(cached.latitude, cached.longitude, currentLat, currentLon)
        return movedKm <= CACHE_DISTANCE_THRESHOLD_KM
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return earthRadiusKm * c
    }
}
