package com.openlauncher.app.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.openlauncher.app.R
import com.openlauncher.app.util.AppLanguageManager
import com.openlauncher.app.util.FilteredGpsLocationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val NAV_TAG = "PumaNav"
private const val HTTP_USER_AGENT = "PumaLauncher/1.0 (Android)"

@Keep
data class PhotonPlace(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double
) {
    val displayName: String
        get() = if (subtitle.isNotBlank()) "$name, $subtitle" else name
}

enum class TurnIconType {
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    UTURN,
    ROUNDABOUT,
    DESTINATION
}

@Keep
data class ValhallaManeuver(
    val type: Int,
    val instruction: String,
    val streetName: String,
    val lengthKm: Double,
    val timeSec: Double,
    val beginShapeIndex: Int,
    val endShapeIndex: Int,
    val turnIcon: TurnIconType
)

@Keep
data class ValhallaRoute(
    val shapePoints: List<LatLng>,
    val maneuvers: List<ValhallaManeuver>,
    val totalLengthKm: Double,
    val totalTimeSec: Double,
    val destination: PhotonPlace,
    val hasToll: Boolean = false
)

@Keep
enum class RouteOptionType {
    FASTEST,
    ALTERNATIVE,
    SCENIC
}

@Keep
data class RoutePreviewOption(
    val type: RouteOptionType,
    val route: ValhallaRoute,
    val hasTolls: Boolean = route.hasToll
)

private data class GeocodingOutcome(
    val places: List<PhotonPlace>,
    val networkError: Boolean
)

/**
 * Decodes encoded polyline6 geometry (precision 1e6, NOT 1e5).
 */
fun decodePolyline6(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20 && index < len)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lat += dlat

        shift = 0
        result = 0
        do {
            if (index >= len) break
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20 && index < len)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lng += dlng

        poly.add(LatLng(lat / 1e6, lng / 1e6))
    }
    return poly
}

private fun mapValhallaManeuverIcon(type: Int, instruction: String): TurnIconType {
    return when (type) {
        4, 5, 6 -> TurnIconType.DESTINATION
        9, 18, 20, 23 -> TurnIconType.SLIGHT_RIGHT
        10 -> TurnIconType.RIGHT
        11 -> TurnIconType.SHARP_RIGHT
        12, 13 -> TurnIconType.UTURN
        14 -> TurnIconType.SHARP_LEFT
        15 -> TurnIconType.LEFT
        16, 19, 21, 24 -> TurnIconType.SLIGHT_LEFT
        26, 27 -> TurnIconType.ROUNDABOUT
        else -> {
            val lower = instruction.lowercase(Locale.ITALIAN)
            when {
                "rotonda" in lower || "rotatoria" in lower -> TurnIconType.ROUNDABOUT
                "inversione" in lower -> TurnIconType.UTURN
                "destinazione" in lower || "arrivato" in lower -> TurnIconType.DESTINATION
                "leggermente a sinistra" in lower || "tieni la sinistra" in lower -> TurnIconType.SLIGHT_LEFT
                "leggermente a destra" in lower || "tieni la destra" in lower -> TurnIconType.SLIGHT_RIGHT
                "sinistra" in lower -> TurnIconType.LEFT
                "destra" in lower -> TurnIconType.RIGHT
                else -> TurnIconType.STRAIGHT
            }
        }
    }
}

private fun mapOsrmManeuver(
    context: Context,
    type: String,
    modifier: String,
    street: String
): Pair<TurnIconType, String> {
    fun loc(resId: Int, streetResId: Int): String = if (street.isNotBlank()) {
        AppLanguageManager.localizedString(context, streetResId, street)
    } else {
        AppLanguageManager.localizedString(context, resId)
    }

    return when (type.lowercase(Locale.ROOT)) {
        "arrive" -> TurnIconType.DESTINATION to AppLanguageManager.localizedString(context, R.string.nav_maneuver_arrive)
        "depart" -> TurnIconType.STRAIGHT to loc(R.string.nav_maneuver_depart, R.string.nav_maneuver_depart_street)
        "roundabout", "rotary", "roundabout turn" ->
            TurnIconType.ROUNDABOUT to loc(R.string.nav_maneuver_roundabout, R.string.nav_maneuver_roundabout_street)
        else -> when (modifier.lowercase(Locale.ROOT)) {
            "uturn" -> TurnIconType.UTURN to loc(R.string.nav_maneuver_uturn, R.string.nav_maneuver_uturn_street)
            "sharp left" -> TurnIconType.SHARP_LEFT to loc(R.string.nav_maneuver_sharp_left, R.string.nav_maneuver_sharp_left_street)
            "left" -> TurnIconType.LEFT to loc(R.string.nav_maneuver_left, R.string.nav_maneuver_left_street)
            "slight left" -> TurnIconType.SLIGHT_LEFT to loc(R.string.nav_maneuver_slight_left, R.string.nav_maneuver_slight_left_street)
            "sharp right" -> TurnIconType.SHARP_RIGHT to loc(R.string.nav_maneuver_sharp_right, R.string.nav_maneuver_sharp_right_street)
            "right" -> TurnIconType.RIGHT to loc(R.string.nav_maneuver_right, R.string.nav_maneuver_right_street)
            "slight right" -> TurnIconType.SLIGHT_RIGHT to loc(R.string.nav_maneuver_slight_right, R.string.nav_maneuver_slight_right_street)
            else -> TurnIconType.STRAIGHT to loc(R.string.nav_maneuver_continue, R.string.nav_maneuver_continue_street)
        }
    }
}

class NavigationEngine(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var searchJob: Job? = null
    private var routeJob: Job? = null

    // Persistent preferences mirrored into engine
    var avoidTolls by mutableStateOf(false)
        private set
    var voiceGuidanceEnabled by mutableStateOf(true)
        private set

    // Live GPS speed in km/h (0 when < 2 km/h to avoid stationary jitter)
    private val _currentSpeedKmh = MutableStateFlow(0)
    val currentSpeedKmh: StateFlow<Int> = _currentSpeedKmh.asStateFlow()

    // Default fallback coordinates (Rome, Italy) until first GPS fix arrives
    var currentLat by mutableDoubleStateOf(41.9028)
        private set
    var currentLon by mutableDoubleStateOf(12.4964)
        private set
    var hasGpsFix by mutableStateOf(false)
        private set
    var isMoving by mutableStateOf(false)
        private set
    var currentBearing by mutableFloatStateOf(0f)
        private set
    var isFollowingGps by mutableStateOf(true)

    // Search state
    var searchResults by mutableStateOf<List<PhotonPlace>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var searchStatusMessage by mutableStateOf<String?>(null)
        private set

    // Route Preview state (3 alternatives before starting Turn-by-Turn)
    var isRoutePreview by mutableStateOf(false)
        private set
    var previewDestination by mutableStateOf<PhotonPlace?>(null)
        private set
    var previewOptions by mutableStateOf<List<RoutePreviewOption>>(emptyList())
        private set
    var selectedPreviewIndex by mutableIntStateOf(0)
        private set

    // Active Turn-by-Turn route state
    var activeRoute by mutableStateOf<ValhallaRoute?>(null)
        private set
    var traveledRoutePoints by mutableStateOf<List<LatLng>>(emptyList())
        private set
    var remainingRoutePoints by mutableStateOf<List<LatLng>>(emptyList())
        private set
    var isRouting by mutableStateOf(false)
        private set
    var routeError by mutableStateOf<String?>(null)
        private set

    var currentManeuverIndex by mutableIntStateOf(0)
        private set
    var nextTurnInstruction by mutableStateOf("")
        private set
    var nextTurnStreet by mutableStateOf("")
        private set
    var nextTurnIcon by mutableStateOf(TurnIconType.STRAIGHT)
        private set
    var nextTurnDistanceText by mutableStateOf("")
        private set
    var remainingDistanceText by mutableStateOf("")
        private set
    var etaText by mutableStateOf("")
        private set

    private var lastRouteCalcElapsedMs: Long = 0L
    private var announced300ForManeuver: Int = -1
    private var announced50ForManeuver: Int = -1
    private var isStarted = false

    val locationEngine: FilteredGpsLocationEngine = FilteredGpsLocationEngine(
        context = appContext,
        routePointsProvider = { activeRoute?.shapePoints },
        onFilteredLocation = { loc -> onNewLocation(loc) }
    )

    init {
        initTts()
    }

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val locale = AppLanguageManager.localeFor(AppLanguageManager.getSavedLanguage(appContext))
                    val res = tts?.setLanguage(locale)
                    ttsReady = res != TextToSpeech.LANG_MISSING_DATA &&
                        res != TextToSpeech.LANG_NOT_SUPPORTED
                    Log.d(NAV_TAG, "NavigationEngine TTS inizializzato (locale=$locale, ready=$ttsReady)")
                }
            }
        }
    }

    fun setVoiceGuidance(enabled: Boolean) {
        voiceGuidanceEnabled = enabled
        if (!enabled) {
            runCatching { tts?.stop() }
            Log.d(NAV_TAG, "Guida vocale TTS disattivata (tts.stop() invocato)")
        } else {
            Log.d(NAV_TAG, "Guida vocale TTS attivata")
        }
    }

    fun updateAvoidTolls(enabled: Boolean, recalculateIfActive: Boolean = true) {
        val changed = avoidTolls != enabled
        avoidTolls = enabled
        if (changed && recalculateIfActive) {
            val currentDest = activeRoute?.destination
            if (currentDest != null) {
                Log.d(NAV_TAG, "Cambio opzione avoidTolls=$enabled con destinazione attiva: ricalcolo immediato percorso")
                calculateRoute(currentDest, avoidTolls = enabled, isRecalculation = false)
            }
        }
    }

    fun speak(text: String) {
        if (!voiceGuidanceEnabled) return
        if (!ttsReady || text.isBlank()) return
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_${SystemClock.elapsedRealtime()}")
        }
    }

    fun speakItalian(text: String) = speak(text)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startLocationTracking() {
        if (!hasLocationPermission()) return
        if (isStarted) return
        isStarted = true
        locationEngine.setEngineTrackingActive(true)
    }

    fun stopLocationTracking() {
        isStarted = false
        locationEngine.setEngineTrackingActive(false)
    }

    fun shutdown() {
        stopLocationTracking()
        locationEngine.release()
        searchJob?.cancel()
        routeJob?.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
    }

    private fun onNewLocation(loc: Location) {
        val speedMps = if (loc.hasSpeed()) loc.speed.coerceAtLeast(0f) else 0f
        val rawKmh = (speedMps * 3.6f).roundToInt()
        _currentSpeedKmh.value = if (rawKmh < 2) 0 else rawKmh
        isMoving = speedMps >= 0.5f

        currentLat = loc.latitude
        currentLon = loc.longitude
        hasGpsFix = true

        if (loc.hasBearing()) {
            currentBearing = ((loc.bearing % 360f) + 360f) % 360f
        }

        val route = activeRoute
        if (route != null) {
            updateNavigationProgress(loc.latitude, loc.longitude, route)
        }
    }

    /**
     * Launches an immediate search (e.g. when pressing ImeAction.Search on the keyboard).
     */
    fun triggerImmediateSearch(query: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            searchPhoton(query)
        }
    }

    /**
     * Queries Photon geocoding API as primary:
     * https://photon.komoot.io/api/?q=<encodedQuery>&lang=it&limit=5
     * If Photon fails or returns 0 results, automatically falls back to Nominatim:
     * https://nominatim.openstreetmap.org/search?q=<encodedQuery>&format=jsonv2&addressdetails=1&limit=5&accept-language=it
     */
    suspend fun searchPhoton(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            searchResults = emptyList()
            isSearching = false
            searchStatusMessage = null
            return
        }
        isSearching = true
        searchStatusMessage = null
        Log.d(NAV_TAG, "Avvio ricerca indirizzo per query=\"$trimmed\"")

        val lang = AppLanguageManager.getSavedLanguage(appContext)
        val outcome = withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val photonOutcome = fetchPhotonResults(encoded, lang)
            if (photonOutcome.places.isNotEmpty()) {
                Log.d(NAV_TAG, "Photon ha restituito ${photonOutcome.places.size} risultati per \"$trimmed\"")
                photonOutcome
            } else {
                Log.d(NAV_TAG, "Photon 0 risultati o errore (networkError=${photonOutcome.networkError}) per \"$trimmed\", eseguo fallback su Nominatim")
                val nominatimOutcome = fetchNominatimResults(encoded, lang)
                if (nominatimOutcome.places.isNotEmpty()) {
                    Log.d(NAV_TAG, "Nominatim fallback ha restituito ${nominatimOutcome.places.size} risultati per \"$trimmed\"")
                    nominatimOutcome
                } else {
                    GeocodingOutcome(
                        places = emptyList(),
                        networkError = photonOutcome.networkError && nominatimOutcome.networkError
                    )
                }
            }
        }

        searchResults = outcome.places
        isSearching = false
        searchStatusMessage = when {
            outcome.places.isNotEmpty() -> null
            outcome.networkError -> AppLanguageManager.localizedString(appContext, R.string.nav_network_error)
            else -> AppLanguageManager.localizedString(appContext, R.string.nav_no_results)
        }
        if (searchStatusMessage != null) {
            Log.d(NAV_TAG, "Stato finale ricerca \"$trimmed\": $searchStatusMessage")
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        searchResults = emptyList()
        isSearching = false
        searchStatusMessage = null
    }

    private fun fetchPhotonResults(encodedQuery: String, lang: String): GeocodingOutcome {
        var conn: HttpURLConnection? = null
        return try {
            val langParam = if (lang in setOf("en", "de", "fr", "it")) "&lang=$lang" else ""
            val urlStr = "https://photon.komoot.io/api/?q=$encodedQuery$langParam&limit=5"
            Log.d(NAV_TAG, "GET Photon: $urlStr")
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", HTTP_USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.d(NAV_TAG, "Photon HTTP $code")
                return GeocodingOutcome(emptyList(), networkError = true)
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: JSONArray()
            val list = ArrayList<PhotonPlace>(features.length())
            for (i in 0 until features.length()) {
                val feat = features.optJSONObject(i) ?: continue
                val geom = feat.optJSONObject("geometry") ?: continue
                val coords = geom.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val lon = coords.optDouble(0, Double.NaN)
                val lat = coords.optDouble(1, Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val props = feat.optJSONObject("properties") ?: JSONObject()
                val rawName = props.optString("name", "").trim()
                val street = props.optString("street", "").trim()
                val houseNumber = props.optString("housenumber", "").trim()
                val city = props.optString("city", "")
                    .ifBlank { props.optString("town", "") }
                    .ifBlank { props.optString("village", "") }
                    .ifBlank { props.optString("county", "") }
                    .trim()
                val state = props.optString("state", "").trim()
                val country = props.optString("country", "").trim()

                val streetFull = if (street.isNotEmpty() && houseNumber.isNotEmpty()) "$street $houseNumber" else street
                val primary = when {
                    rawName.isNotEmpty() -> rawName
                    streetFull.isNotEmpty() -> streetFull
                    city.isNotEmpty() -> city
                    else -> AppLanguageManager.localizedString(appContext, R.string.nav_default_location)
                }
                val subParts = buildList {
                    if (streetFull.isNotEmpty() && streetFull != primary) add(streetFull)
                    if (city.isNotEmpty() && city != primary) add(city)
                    if (state.isNotEmpty() && state != city) add(state)
                    if (country.isNotEmpty()) add(country)
                }
                list.add(
                    PhotonPlace(
                        name = primary,
                        subtitle = subParts.joinToString(", "),
                        lat = lat,
                        lon = lon
                    )
                )
            }
            GeocodingOutcome(list, networkError = false)
        } catch (e: Exception) {
            Log.d(NAV_TAG, "Errore Photon: ${e.javaClass.simpleName}: ${e.message}")
            GeocodingOutcome(emptyList(), networkError = true)
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchNominatimResults(encodedQuery: String, lang: String): GeocodingOutcome {
        var conn: HttpURLConnection? = null
        return try {
            val urlStr = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=jsonv2&addressdetails=1&limit=5&accept-language=$lang"
            Log.d(NAV_TAG, "GET Nominatim fallback: $urlStr")
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", HTTP_USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.d(NAV_TAG, "Nominatim HTTP $code")
                return GeocodingOutcome(emptyList(), networkError = true)
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val arr = JSONArray(body)
            val list = ArrayList<PhotonPlace>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val lat = item.optString("lat", "").toDoubleOrNull() ?: continue
                val lon = item.optString("lon", "").toDoubleOrNull() ?: continue
                val displayName = item.optString("display_name", "").trim()
                val rawName = item.optString("name", "").trim()
                val addr = item.optJSONObject("address")

                val road = addr?.optString("road", "")?.trim().orEmpty()
                val houseNumber = addr?.optString("house_number", "")?.trim().orEmpty()
                val city = addr?.optString("city", "")
                    ?.ifBlank { addr.optString("town", "") }
                    ?.ifBlank { addr.optString("village", "") }
                    ?.ifBlank { addr.optString("municipality", "") }
                    ?.ifBlank { addr.optString("county", "") }
                    ?.trim()
                    .orEmpty()
                val state = addr?.optString("state", "")?.trim().orEmpty()
                val country = addr?.optString("country", "")?.trim().orEmpty()

                val roadFull = if (road.isNotEmpty() && houseNumber.isNotEmpty()) "$road $houseNumber" else road
                val primary = when {
                    rawName.isNotEmpty() -> rawName
                    roadFull.isNotEmpty() -> roadFull
                    city.isNotEmpty() -> city
                    displayName.isNotEmpty() -> displayName.substringBefore(",").trim()
                    else -> AppLanguageManager.localizedString(appContext, R.string.nav_default_destination)
                }
                val subParts = buildList {
                    if (roadFull.isNotEmpty() && roadFull != primary) add(roadFull)
                    if (city.isNotEmpty() && city != primary) add(city)
                    if (state.isNotEmpty() && state != city) add(state)
                    if (country.isNotEmpty()) add(country)
                }
                val subtitle = if (subParts.isNotEmpty()) {
                    subParts.joinToString(", ")
                } else {
                    displayName.substringAfter(",", "").trim()
                }

                list.add(
                    PhotonPlace(
                        name = primary,
                        subtitle = subtitle,
                        lat = lat,
                        lon = lon
                    )
                )
            }
            GeocodingOutcome(list, networkError = false)
        } catch (e: Exception) {
            Log.d(NAV_TAG, "Errore Nominatim: ${e.javaClass.simpleName}: ${e.message}")
            GeocodingOutcome(emptyList(), networkError = true)
        } finally {
            conn?.disconnect()
        }
    }

    fun loadRoutePreview(destination: PhotonPlace) {
        routeJob?.cancel()
        isRouting = true
        routeError = null
        isRoutePreview = true
        previewDestination = destination
        previewOptions = emptyList()
        selectedPreviewIndex = 0
        isFollowingGps = false

        val startLat = currentLat
        val startLon = currentLon
        val standardUseTolls = if (avoidTolls) 0.0 else 1.0

        Log.d(
            NAV_TAG,
            "Calcolo anteprima 3 percorsi (avoidTolls=$avoidTolls) da ($startLat, $startLon) a \"${destination.name}\" (${destination.lat}, ${destination.lon})"
        )

        routeJob = scope.launch {
            val options = withContext(Dispatchers.IO) {
                coroutineScope {
                    val fastestDeferred = async {
                        requestValhallaPreviewPair(
                            startLat = startLat,
                            startLon = startLon,
                            destination = destination,
                            useTolls = standardUseTolls
                        )
                    }
                    val noTollsDeferred = if (!avoidTolls) {
                        async {
                            val payload = buildValhallaPayload(
                                startLat = startLat,
                                startLon = startLon,
                                destination = destination,
                                useTolls = 0.0
                            )
                            postValhallaRouteJson(payload)
                                ?.optJSONObject("trip")
                                ?.let { parseValhallaTrip(it, destination) }
                        }
                    } else {
                        null
                    }
                    val scenicDeferred = async {
                        val payload = buildValhallaPayload(
                            startLat = startLat,
                            startLon = startLon,
                            destination = destination,
                            useTolls = 0.0,
                            useHighways = 0.05,
                            useHills = 0.95
                        )
                        postValhallaRouteJson(payload)
                            ?.optJSONObject("trip")
                            ?.let { parseValhallaTrip(it, destination) }
                    }

                    val fastestPair = fastestDeferred.await()
                    val fastest = fastestPair.firstOrNull()
                        ?: requestOsrmRoute(startLat, startLon, destination, avoidTolls)
                        ?: return@coroutineScope emptyList<RoutePreviewOption>()

                    val result = mutableListOf<RoutePreviewOption>()
                    result.add(
                        RoutePreviewOption(
                            type = RouteOptionType.FASTEST,
                            route = fastest,
                            hasTolls = fastest.hasToll
                        )
                    )

                    val valhallaAlt = fastestPair.getOrNull(1)
                    val noTollsAlt = noTollsDeferred?.await()
                    val chosenAlt = when {
                        valhallaAlt != null && !isSameRoute(valhallaAlt, fastest) -> valhallaAlt
                        noTollsAlt != null && !isSameRoute(noTollsAlt, fastest) -> noTollsAlt
                        else -> null
                    }
                    if (chosenAlt != null) {
                        result.add(
                            RoutePreviewOption(
                                type = RouteOptionType.ALTERNATIVE,
                                route = chosenAlt,
                                hasTolls = chosenAlt.hasToll
                            )
                        )
                    }

                    val scenic = scenicDeferred.await()
                    if (scenic != null &&
                        !isSameRoute(scenic, fastest) &&
                        (chosenAlt == null || !isSameRoute(scenic, chosenAlt))
                    ) {
                        result.add(
                            RoutePreviewOption(
                                type = RouteOptionType.SCENIC,
                                route = scenic,
                                hasTolls = scenic.hasToll
                            )
                        )
                    }

                    result
                }
            }

            isRouting = false
            if (options.isNotEmpty()) {
                previewOptions = options
                selectedPreviewIndex = 0
                Log.d(
                    NAV_TAG,
                    "Anteprima percorso pronta: ${options.size} opzioni (${options.joinToString { "${it.type}:${"%.1f".format(it.route.totalLengthKm)}km" }})"
                )
            } else {
                routeError = AppLanguageManager.localizedString(appContext, R.string.nav_route_error)
                Log.d(NAV_TAG, "Errore: sia Valhalla che OSRM hanno fallito il calcolo anteprima")
            }
        }
    }

    fun selectPreviewOption(index: Int) {
        if (index in previewOptions.indices) {
            selectedPreviewIndex = index
        }
    }

    fun cancelRoutePreview() {
        routeJob?.cancel()
        isRouting = false
        isRoutePreview = false
        previewDestination = null
        previewOptions = emptyList()
        selectedPreviewIndex = 0
        routeError = null
        isFollowingGps = true
        Log.d(NAV_TAG, "Anteprima percorso annullata dall'utente")
    }

    fun startPreviewRoute() {
        val chosen = previewOptions.getOrNull(selectedPreviewIndex)?.route ?: return
        isRoutePreview = false
        previewDestination = null
        previewOptions = emptyList()
        selectedPreviewIndex = 0
        routeError = null
        isFollowingGps = true
        lastRouteCalcElapsedMs = SystemClock.elapsedRealtime()

        activeRoute = chosen
        currentManeuverIndex = 0
        announced300ForManeuver = -1
        announced50ForManeuver = -1
        updateNavigationProgress(currentLat, currentLon, chosen)
        val firstInstruction = chosen.maneuvers.firstOrNull()?.instruction.orEmpty()
        if (firstInstruction.isNotBlank()) {
            speak(firstInstruction)
        }
        Log.d(NAV_TAG, "Navigazione avviata da anteprima: ${"%.1f".format(chosen.totalLengthKm)} km")
    }

    /**
     * Calculates a Turn-by-Turn route via POST to https://valhalla1.openstreetmap.de/route
     * (including costing_options.auto.use_tolls based on avoidTolls, decoding shape with polyline6 1e6),
     * and automatically falls back to OSRM (with &exclude=toll when avoidTolls == true).
     */
    fun calculateRoute(
        destination: PhotonPlace,
        avoidTolls: Boolean = this.avoidTolls,
        isRecalculation: Boolean = false
    ) {
        this.avoidTolls = avoidTolls
        routeJob?.cancel()
        isRouting = true
        routeError = null
        lastRouteCalcElapsedMs = SystemClock.elapsedRealtime()
        isFollowingGps = true

        val startLat = currentLat
        val startLon = currentLon
        Log.d(
            NAV_TAG,
            "Calcolo percorso (avoidTolls=$avoidTolls) da ($startLat, $startLon) a \"${destination.name}\" (${destination.lat}, ${destination.lon})"
        )

        routeJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                requestValhallaRoute(startLat, startLon, destination, avoidTolls)
                    ?: run {
                        Log.d(NAV_TAG, "Valhalla fallito o timeout, eseguo fallback automatico su OSRM polyline6 (avoidTolls=$avoidTolls)")
                        requestOsrmRoute(startLat, startLon, destination, avoidTolls)
                    }
            }
            isRouting = false
            if (result != null) {
                activeRoute = result
                currentManeuverIndex = 0
                announced300ForManeuver = -1
                announced50ForManeuver = -1
                updateNavigationProgress(currentLat, currentLon, result)
                val firstInstruction = result.maneuvers.firstOrNull()?.instruction.orEmpty()
                if (!isRecalculation && firstInstruction.isNotBlank()) {
                    speak(firstInstruction)
                }
            } else {
                routeError = AppLanguageManager.localizedString(appContext, R.string.nav_route_error)
                Log.d(NAV_TAG, "Errore: sia Valhalla che OSRM hanno fallito il calcolo del percorso")
            }
        }
    }

    fun stopNavigation() {
        routeJob?.cancel()
        isRouting = false
        isRoutePreview = false
        previewDestination = null
        previewOptions = emptyList()
        selectedPreviewIndex = 0
        activeRoute = null
        traveledRoutePoints = emptyList()
        remainingRoutePoints = emptyList()
        routeError = null
        currentManeuverIndex = 0
        nextTurnInstruction = ""
        nextTurnStreet = ""
        nextTurnDistanceText = ""
        remainingDistanceText = ""
        etaText = ""
        announced300ForManeuver = -1
        announced50ForManeuver = -1
        runCatching { tts?.stop() }
        Log.d(NAV_TAG, "Navigazione terminata dall'utente")
    }

    private fun isSameRoute(a: ValhallaRoute, b: ValhallaRoute): Boolean {
        val distDiff = abs(a.totalLengthKm - b.totalLengthKm)
        val timeDiff = abs(a.totalTimeSec - b.totalTimeSec)
        return distDiff < 0.15 && timeDiff < 15.0
    }

    private fun buildValhallaPayload(
        startLat: Double,
        startLon: Double,
        destination: PhotonPlace,
        useTolls: Double,
        useHighways: Double? = null,
        useHills: Double? = null,
        alternates: Int = 0
    ): JSONObject {
        val autoOptions = JSONObject().put("use_tolls", useTolls)
        if (useHighways != null) {
            autoOptions.put("use_highways", useHighways)
        }
        if (useHills != null) {
            autoOptions.put("use_hills", useHills)
        }
        val valhallaLang = when (AppLanguageManager.getSavedLanguage(appContext)) {
            "de" -> "de-DE"
            "it" -> "it-IT"
            "fr" -> "fr-FR"
            "es" -> "es-ES"
            "pt" -> "pt-PT"
            else -> "en-US"
        }
        return JSONObject().apply {
            put(
                "locations",
                JSONArray().apply {
                    put(JSONObject().put("lat", startLat).put("lon", startLon))
                    put(JSONObject().put("lat", destination.lat).put("lon", destination.lon))
                }
            )
            put("costing", "auto")
            put("costing_options", JSONObject().put("auto", autoOptions))
            if (alternates > 0) {
                put("alternates", alternates)
            }
            put(
                "directions_options",
                JSONObject()
                    .put("units", "kilometers")
                    .put("language", valhallaLang)
            )
        }
    }

    private fun postValhallaRouteJson(payload: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("https://valhalla1.openstreetmap.de/route").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", HTTP_USER_AGENT)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                Log.d(NAV_TAG, "Valhalla HTTP $code")
                return null
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            JSONObject(body)
        } catch (e: Exception) {
            Log.d(NAV_TAG, "Errore routing Valhalla: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseValhallaTrip(trip: JSONObject, destination: PhotonPlace): ValhallaRoute? {
        val legs = trip.optJSONArray("legs") ?: return null
        if (legs.length() == 0) return null
        val leg0 = legs.getJSONObject(0)

        val shapeEncoded = leg0.optString("shape", "")
        if (shapeEncoded.isBlank()) return null
        val shapePoints = decodePolyline6(shapeEncoded)
        if (shapePoints.isEmpty()) return null

        val rawManeuvers = leg0.optJSONArray("maneuvers") ?: JSONArray()
        val maneuvers = ArrayList<ValhallaManeuver>(rawManeuvers.length())
        for (i in 0 until rawManeuvers.length()) {
            val m = rawManeuvers.getJSONObject(i)
            val type = m.optInt("type", 1)
            val instruction = m.optString("instruction", "").trim()
            val streetNamesArr = m.optJSONArray("street_names")
            val street = if (streetNamesArr != null && streetNamesArr.length() > 0) {
                (0 until streetNamesArr.length()).mapNotNull { idx ->
                    streetNamesArr.optString(idx)?.takeIf { it.isNotBlank() }
                }.joinToString(" / ")
            } else {
                ""
            }
            val lengthKm = m.optDouble("length", 0.0)
            val timeSec = m.optDouble("time", 0.0)
            val beginIdx = m.optInt("begin_shape_index", 0).coerceIn(0, shapePoints.lastIndex)
            val endIdx = m.optInt("end_shape_index", shapePoints.lastIndex).coerceIn(beginIdx, shapePoints.lastIndex)
            maneuvers.add(
                ValhallaManeuver(
                    type = type,
                    instruction = instruction,
                    streetName = street,
                    lengthKm = lengthKm,
                    timeSec = timeSec,
                    beginShapeIndex = beginIdx,
                    endShapeIndex = endIdx,
                    turnIcon = mapValhallaManeuverIcon(type, instruction)
                )
            )
        }

        val summary = trip.optJSONObject("summary") ?: leg0.optJSONObject("summary")
        val totalKm = summary?.optDouble("length", 0.0) ?: maneuvers.sumOf { it.lengthKm }
        val totalSec = summary?.optDouble("time", 0.0) ?: maneuvers.sumOf { it.timeSec }
        val hasToll = summary?.optBoolean("has_toll", false)
            ?: leg0.optJSONObject("summary")?.optBoolean("has_toll", false)
            ?: false

        return ValhallaRoute(
            shapePoints = shapePoints,
            maneuvers = maneuvers,
            totalLengthKm = totalKm,
            totalTimeSec = totalSec,
            destination = destination,
            hasToll = hasToll
        )
    }

    private fun requestValhallaPreviewPair(
        startLat: Double,
        startLon: Double,
        destination: PhotonPlace,
        useTolls: Double
    ): List<ValhallaRoute> {
        val payload = buildValhallaPayload(
            startLat = startLat,
            startLon = startLon,
            destination = destination,
            useTolls = useTolls,
            alternates = 1
        )
        val root = postValhallaRouteJson(payload) ?: return emptyList()
        val results = mutableListOf<ValhallaRoute>()
        root.optJSONObject("trip")?.let { trip ->
            parseValhallaTrip(trip, destination)?.let { results.add(it) }
        }
        val alts = root.optJSONArray("alternates")
        if (alts != null) {
            for (i in 0 until alts.length()) {
                val altTrip = alts.optJSONObject(i)?.optJSONObject("trip") ?: continue
                parseValhallaTrip(altTrip, destination)?.let { results.add(it) }
            }
        }
        return results
    }

    private fun requestValhallaRoute(
        startLat: Double,
        startLon: Double,
        destination: PhotonPlace,
        avoidTolls: Boolean
    ): ValhallaRoute? {
        val payload = buildValhallaPayload(
            startLat = startLat,
            startLon = startLon,
            destination = destination,
            useTolls = if (avoidTolls) 0.0 else 1.0
        )
        val root = postValhallaRouteJson(payload) ?: return null
        val trip = root.optJSONObject("trip") ?: return null
        val route = parseValhallaTrip(trip, destination) ?: return null
        Log.d(NAV_TAG, "Percorso Valhalla calcolato (avoidTolls=$avoidTolls): ${route.shapePoints.size} punti (polyline6), ${route.maneuvers.size} manovre, ${"%.1f".format(route.totalLengthKm)} km")
        return route
    }

    private fun requestOsrmRoute(
        startLat: Double,
        startLon: Double,
        destination: PhotonPlace,
        avoidTolls: Boolean
    ): ValhallaRoute? {
        var conn: HttpURLConnection? = null
        return try {
            val coordsPart = String.format(
                Locale.US,
                "%.6f,%.6f;%.6f,%.6f",
                startLon, startLat,
                destination.lon, destination.lat
            )
            val tollSuffix = if (avoidTolls) "&exclude=toll" else ""
            val urlStr = "https://router.project-osrm.org/route/v1/driving/$coordsPart?overview=full&geometries=polyline6&steps=true$tollSuffix"
            Log.d(NAV_TAG, "GET OSRM fallback: $urlStr")

            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", HTTP_USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                Log.d(NAV_TAG, "OSRM HTTP $code")
                return null
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val root = JSONObject(body)
            val routes = root.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val route0 = routes.getJSONObject(0)

            val shapeEncoded = route0.optString("geometry", "")
            val shapePoints = decodePolyline6(shapeEncoded)
            if (shapePoints.isEmpty()) return null

            val totalKm = route0.optDouble("distance", 0.0) / 1000.0
            val totalSec = route0.optDouble("duration", 0.0)

            val legs = route0.optJSONArray("legs")
            val steps = if (legs != null && legs.length() > 0) {
                legs.getJSONObject(0).optJSONArray("steps") ?: JSONArray()
            } else JSONArray()

            val maneuvers = ArrayList<ValhallaManeuver>(steps.length().coerceAtLeast(1))
            var searchStartIdx = 0

            val stepIndices = IntArray(steps.length())
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val manObj = step.optJSONObject("maneuver")
                val locArr = manObj?.optJSONArray("location")
                if (locArr != null && locArr.length() >= 2) {
                    val mLon = locArr.optDouble(0, startLon)
                    val mLat = locArr.optDouble(1, startLat)
                    var bestIdx = searchStartIdx
                    var bestDist = Double.MAX_VALUE
                    for (ptIdx in searchStartIdx until shapePoints.size) {
                        val d = haversineMeters(mLat, mLon, shapePoints[ptIdx].latitude, shapePoints[ptIdx].longitude)
                        if (d < bestDist) {
                            bestDist = d
                            bestIdx = ptIdx
                        }
                    }
                    stepIndices[i] = bestIdx
                    searchStartIdx = bestIdx
                } else {
                    stepIndices[i] = searchStartIdx
                }
            }

            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val street = step.optString("name", "").trim()
                val distKm = step.optDouble("distance", 0.0) / 1000.0
                val durSec = step.optDouble("duration", 0.0)
                val manObj = step.optJSONObject("maneuver")
                val mType = manObj?.optString("type", "continue").orEmpty()
                val mMod = manObj?.optString("modifier", "straight").orEmpty()

                val (icon, instruction) = mapOsrmManeuver(appContext, mType, mMod, street)
                val beginIdx = stepIndices[i].coerceIn(0, shapePoints.lastIndex)
                val endIdx = if (i + 1 < steps.length()) {
                    stepIndices[i + 1].coerceIn(beginIdx, shapePoints.lastIndex)
                } else {
                    shapePoints.lastIndex
                }

                maneuvers.add(
                    ValhallaManeuver(
                        type = if (icon == TurnIconType.DESTINATION) 4 else 1,
                        instruction = instruction,
                        streetName = street,
                        lengthKm = distKm,
                        timeSec = durSec,
                        beginShapeIndex = beginIdx,
                        endShapeIndex = endIdx,
                        turnIcon = icon
                    )
                )
            }

            if (maneuvers.isEmpty()) {
                maneuvers.add(
                    ValhallaManeuver(
                        type = 4,
                        instruction = AppLanguageManager.localizedString(
                            appContext,
                            R.string.nav_maneuver_proceed_to,
                            destination.name
                        ),
                        streetName = destination.name,
                        lengthKm = totalKm,
                        timeSec = totalSec,
                        beginShapeIndex = 0,
                        endShapeIndex = shapePoints.lastIndex,
                        turnIcon = TurnIconType.DESTINATION
                    )
                )
            }

            Log.d(NAV_TAG, "Percorso OSRM fallback calcolato (avoidTolls=$avoidTolls): ${shapePoints.size} punti (polyline6), ${maneuvers.size} manovre, ${"%.1f".format(totalKm)} km")
            ValhallaRoute(
                shapePoints = shapePoints,
                maneuvers = maneuvers,
                totalLengthKm = totalKm,
                totalTimeSec = totalSec,
                destination = destination
            )
        } catch (e: Exception) {
            Log.d(NAV_TAG, "Errore routing OSRM fallback: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun updateNavigationProgress(lat: Double, lon: Double, route: ValhallaRoute) {
        val pts = route.shapePoints
        if (pts.isEmpty()) return

        // 1. Find closest point/segment on the route polyline and perpendicular distance in meters
        var minDistMeters = Double.MAX_VALUE
        var closestIndex = 0
        for (i in 0 until pts.size) {
            val d = haversineMeters(lat, lon, pts[i].latitude, pts[i].longitude)
            if (d < minDistMeters) {
                minDistMeters = d
                closestIndex = i
            }
        }
        var closestSegIdx = closestIndex.coerceIn(0, (pts.lastIndex - 1).coerceAtLeast(0))
        var projectedPoint = pts[closestSegIdx]
        if (pts.size >= 2) {
            minDistMeters = Double.MAX_VALUE
            val segStart = (closestIndex - 2).coerceAtLeast(0)
            val segEnd = (closestIndex + 2).coerceAtMost(pts.lastIndex - 1)
            for (i in segStart..segEnd) {
                val (proj, segDist) = projectPointOntoSegment(
                    lat, lon,
                    pts[i].latitude, pts[i].longitude,
                    pts[i + 1].latitude, pts[i + 1].longitude
                )
                if (segDist < minDistMeters) {
                    minDistMeters = segDist
                    closestSegIdx = i
                    projectedPoint = proj
                }
            }
            traveledRoutePoints = pts.subList(0, closestSegIdx + 1) + projectedPoint
            remainingRoutePoints = listOf(projectedPoint) + pts.subList(closestSegIdx + 1, pts.size)
        } else {
            traveledRoutePoints = emptyList()
            remainingRoutePoints = pts
        }

        // Align bearing with route segment if navigating and user is on route
        if (closestIndex < pts.lastIndex && minDistMeters <= 45.0) {
            val p1 = pts[closestIndex]
            val p2 = pts[closestIndex + 1]
            val segLen = haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            if (segLen > 1.0) {
                val routeBrg = bearingDegrees(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                if (!hasGpsFix) {
                    currentBearing = routeBrg
                }
            }
        }

        // 2. Off-route check (> 45 meters) with 8-second cooldown
        val nowElapsed = SystemClock.elapsedRealtime()
        if (minDistMeters > 45.0 && nowElapsed - lastRouteCalcElapsedMs > 8000L && !isRouting) {
            Log.d(NAV_TAG, "Fuori traccia di ${minDistMeters.roundToInt()}m (> 45m): avvio ricalcolo percorso")
            speak(AppLanguageManager.localizedString(appContext, R.string.nav_tts_recalculating))
            calculateRoute(route.destination, avoidTolls = avoidTolls, isRecalculation = true)
            return
        }

        // 3. Determine active maneuver and upcoming turn maneuver
        val maneuvers = route.maneuvers
        if (maneuvers.isEmpty()) return

        var activeIdx = currentManeuverIndex.coerceIn(0, maneuvers.lastIndex)
        while (activeIdx < maneuvers.lastIndex && closestIndex > maneuvers[activeIdx].endShapeIndex) {
            activeIdx++
        }
        currentManeuverIndex = activeIdx

        val currentManeuver = maneuvers[activeIdx]
        val upcomingManeuverIdx = if (activeIdx + 1 <= maneuvers.lastIndex) activeIdx + 1 else activeIdx
        val upcomingManeuver = maneuvers[upcomingManeuverIdx]

        // Distance from current position along polyline to the end of currentManeuver (the turn point)
        val turnPointIdx = currentManeuver.endShapeIndex.coerceIn(closestIndex, pts.lastIndex)
        var distToTurnM = haversineMeters(lat, lon, pts[closestIndex].latitude, pts[closestIndex].longitude)
        for (i in closestIndex until turnPointIdx) {
            distToTurnM += haversineMeters(
                pts[i].latitude, pts[i].longitude,
                pts[i + 1].latitude, pts[i + 1].longitude
            )
        }

        nextTurnInstruction = upcomingManeuver.instruction.ifBlank { currentManeuver.instruction }
        nextTurnStreet = upcomingManeuver.streetName
            .ifBlank { currentManeuver.streetName }
            .ifBlank { route.destination.name }
        nextTurnIcon = upcomingManeuver.turnIcon
        nextTurnDistanceText = formatDistance(distToTurnM)

        // Remaining total distance and time
        val currentFractionRemaining = if (currentManeuver.lengthKm > 0.001) {
            ((distToTurnM / 1000.0) / currentManeuver.lengthKm).coerceIn(0.0, 1.0)
        } else 0.0

        var remKm = distToTurnM / 1000.0
        var remSec = currentManeuver.timeSec * currentFractionRemaining
        for (i in (activeIdx + 1)..maneuvers.lastIndex) {
            remKm += maneuvers[i].lengthKm
            remSec += maneuvers[i].timeSec
        }
        remainingDistanceText = formatDistance(remKm * 1000.0)
        val arrivalDate = Date(System.currentTimeMillis() + (remSec * 1000.0).toLong())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        etaText = AppLanguageManager.localizedString(
            appContext,
            R.string.nav_eta_format,
            timeFmt.format(arrivalDate),
            remainingDistanceText
        )

        // 4. Voice guidance announcements at 300m and 50m
        if (distToTurnM <= 300.0 && distToTurnM > 70.0 && announced300ForManeuver != upcomingManeuverIdx) {
            announced300ForManeuver = upcomingManeuverIdx
            speak(AppLanguageManager.localizedString(appContext, R.string.nav_tts_in_300m, upcomingManeuver.instruction))
        } else if (distToTurnM <= 50.0 && announced50ForManeuver != upcomingManeuverIdx) {
            announced50ForManeuver = upcomingManeuverIdx
            if (upcomingManeuver.turnIcon == TurnIconType.DESTINATION || upcomingManeuverIdx == maneuvers.lastIndex) {
                speak(AppLanguageManager.localizedString(appContext, R.string.nav_maneuver_arrive))
            } else {
                speak(AppLanguageManager.localizedString(appContext, R.string.nav_tts_in_50m, upcomingManeuver.instruction))
            }
        }

        // Complete navigation if within 18m of final destination
        if (upcomingManeuverIdx == maneuvers.lastIndex && distToTurnM < 18.0) {
            stopNavigation()
        }
    }

    private fun formatDistance(meters: Double): String {
        val locale = AppLanguageManager.localeFor(AppLanguageManager.getSavedLanguage(appContext))
        return if (meters >= 1000.0) {
            String.format(locale, "%.1f km", meters / 1000.0)
        } else {
            val rounded = ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10)
            "$rounded m"
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun projectPointOntoSegment(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Pair<LatLng, Double> {
        val cosLat = cos(Math.toRadians(pLat)).coerceAtLeast(1e-6)
        val px = pLon * cosLat
        val py = pLat
        val ax = aLon * cosLat
        val ay = aLat
        val bx = bLon * cosLat
        val by = bLat
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq > 1e-12) {
            (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        } else 0.0
        val projLon = (ax + t * dx) / cosLat
        val projLat = ay + t * dy
        return LatLng(projLat, projLon) to haversineMeters(pLat, pLon, projLat, projLon)
    }

    private fun pointToSegmentMeters(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double = projectPointOntoSegment(pLat, pLon, aLat, aLon, bLat, bLon).second

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
