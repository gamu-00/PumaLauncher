package com.openlauncher.app.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val NAV_TAG = "PumaNav"
private const val HTTP_USER_AGENT = "PumaLauncher/1.0 (Android)"

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

data class ValhallaRoute(
    val shapePoints: List<LatLng>,
    val maneuvers: List<ValhallaManeuver>,
    val totalLengthKm: Double,
    val totalTimeSec: Double,
    val destination: PhotonPlace
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

private fun mapOsrmManeuver(type: String, modifier: String, street: String): Pair<TurnIconType, String> {
    val intoStreet = if (street.isNotBlank()) " in $street" else ""
    return when (type.lowercase(Locale.ROOT)) {
        "arrive" -> TurnIconType.DESTINATION to "Sei arrivato a destinazione"
        "depart" -> TurnIconType.STRAIGHT to if (street.isNotBlank()) "Procedi su $street" else "Parti e procedi sul percorso"
        "roundabout", "rotary", "roundabout turn" ->
            TurnIconType.ROUNDABOUT to "Alla rotonda prendi l'uscita$intoStreet"
        else -> when (modifier.lowercase(Locale.ROOT)) {
            "uturn" -> TurnIconType.UTURN to "Effettua inversione a U$intoStreet"
            "sharp left" -> TurnIconType.SHARP_LEFT to "Svolta tutto a sinistra$intoStreet"
            "left" -> TurnIconType.LEFT to "Svolta a sinistra$intoStreet"
            "slight left" -> TurnIconType.SLIGHT_LEFT to "Tieni la sinistra$intoStreet"
            "sharp right" -> TurnIconType.SHARP_RIGHT to "Svolta tutto a destra$intoStreet"
            "right" -> TurnIconType.RIGHT to "Svolta a destra$intoStreet"
            "slight right" -> TurnIconType.SLIGHT_RIGHT to "Tieni la destra$intoStreet"
            else -> TurnIconType.STRAIGHT to if (street.isNotBlank()) "Continua su $street" else "Continua dritto"
        }
    }
}

class NavigationEngine(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationManager: LocationManager? = null
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

    // Active Turn-by-Turn route state
    var activeRoute by mutableStateOf<ValhallaRoute?>(null)
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

    private var lastGpsLocation: Location? = null
    private var lastRouteCalcElapsedMs: Long = 0L
    private var announced300ForManeuver: Int = -1
    private var announced50ForManeuver: Int = -1
    private var isStarted = false

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val rawKmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
            _currentSpeedKmh.value = if (rawKmh < 2) 0 else rawKmh
            onNewLocation(location)
        }
    }

    private val platformLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val rawKmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
            _currentSpeedKmh.value = if (rawKmh < 2) 0 else rawKmh
            onNewLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    init {
        initTts()
    }

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val res = tts?.setLanguage(Locale.ITALIAN)
                    ttsReady = res != TextToSpeech.LANG_MISSING_DATA &&
                        res != TextToSpeech.LANG_NOT_SUPPORTED
                    Log.d(NAV_TAG, "NavigationEngine TTS inizializzato (italiano=$ttsReady)")
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

        // 1. Primary: FusedLocationProviderClient every 1000ms
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(appContext)
            fusedClient = client
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) onNewLocation(loc)
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setWaitForAccurateLocation(false)
                .build()
            client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            Log.d(NAV_TAG, "FusedLocationProviderClient avviato (1000ms)")
        }.onFailure { e ->
            Log.d(NAV_TAG, "FusedLocationProviderClient non disponibile: ${e.message}")
        }

        // 2. Fallback: Android LocationManager (1000ms) for AOSP head units
        runCatching {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager = lm
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    platformLocationListener,
                    Looper.getMainLooper()
                )
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onNewLocation(it) }
            }
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    0f,
                    platformLocationListener,
                    Looper.getMainLooper()
                )
                if (!hasGpsFix) {
                    lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { onNewLocation(it) }
                }
            }
        }
    }

    fun stopLocationTracking() {
        isStarted = false
        runCatching { fusedClient?.removeLocationUpdates(fusedCallback) }
        runCatching { locationManager?.removeUpdates(platformLocationListener) }
    }

    fun shutdown() {
        stopLocationTracking()
        searchJob?.cancel()
        routeJob?.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
    }

    private fun onNewLocation(loc: Location) {
        val rawKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt() else 0
        _currentSpeedKmh.value = if (rawKmh < 2) 0 else rawKmh

        currentLat = loc.latitude
        currentLon = loc.longitude
        hasGpsFix = true

        if (loc.hasBearing() && loc.bearing != 0f) {
            currentBearing = (loc.bearing + 360f) % 360f
        } else {
            val prev = lastGpsLocation
            if (prev != null && prev.distanceTo(loc) > 2.0f) {
                currentBearing = (prev.bearingTo(loc) + 360f) % 360f
                lastGpsLocation = loc
            } else if (prev == null) {
                lastGpsLocation = loc
            }
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

        val outcome = withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val photonOutcome = fetchPhotonResults(encoded)
            if (photonOutcome.places.isNotEmpty()) {
                Log.d(NAV_TAG, "Photon ha restituito ${photonOutcome.places.size} risultati per \"$trimmed\"")
                photonOutcome
            } else {
                Log.d(NAV_TAG, "Photon 0 risultati o errore (networkError=${photonOutcome.networkError}) per \"$trimmed\", eseguo fallback su Nominatim")
                val nominatimOutcome = fetchNominatimResults(encoded)
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
            outcome.networkError -> "Errore rete"
            else -> "Nessun risultato trovato"
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

    private fun fetchPhotonResults(encodedQuery: String): GeocodingOutcome {
        var conn: HttpURLConnection? = null
        return try {
            val urlStr = "https://photon.komoot.io/api/?q=$encodedQuery&lang=it&limit=5"
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
                    else -> "Posizione"
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

    private fun fetchNominatimResults(encodedQuery: String): GeocodingOutcome {
        var conn: HttpURLConnection? = null
        return try {
            val urlStr = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=jsonv2&addressdetails=1&limit=5&accept-language=it"
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
                    else -> "Destinazione"
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
                routeError = "Impossibile calcolare il percorso"
                Log.d(NAV_TAG, "Errore: sia Valhalla che OSRM hanno fallito il calcolo del percorso")
            }
        }
    }

    fun stopNavigation() {
        routeJob?.cancel()
        isRouting = false
        activeRoute = null
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

    private fun requestValhallaRoute(
        startLat: Double,
        startLon: Double,
        destination: PhotonPlace,
        avoidTolls: Boolean
    ): ValhallaRoute? {
        var conn: HttpURLConnection? = null
        return try {
            val payload = JSONObject().apply {
                put(
                    "locations",
                    JSONArray().apply {
                        put(JSONObject().put("lat", startLat).put("lon", startLon))
                        put(JSONObject().put("lat", destination.lat).put("lon", destination.lon))
                    }
                )
                put("costing", "auto")
                put(
                    "costing_options",
                    JSONObject().put(
                        "auto",
                        JSONObject().put("use_tolls", if (avoidTolls) 0.0 else 1.0)
                    )
                )
                put(
                    "directions_options",
                    JSONObject()
                        .put("units", "kilometers")
                        .put("language", "it-IT")
                )
            }

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
            val root = JSONObject(body)
            val trip = root.getJSONObject("trip")
            val legs = trip.getJSONArray("legs")
            if (legs.length() == 0) return null
            val leg0 = legs.getJSONObject(0)

            val shapeEncoded = leg0.getString("shape")
            val shapePoints = decodePolyline6(shapeEncoded)
            if (shapePoints.isEmpty()) return null

            val rawManeuvers = leg0.getJSONArray("maneuvers")
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

            Log.d(NAV_TAG, "Percorso Valhalla calcolato (avoidTolls=$avoidTolls): ${shapePoints.size} punti (polyline6), ${maneuvers.size} manovre, ${"%.1f".format(totalKm)} km")
            ValhallaRoute(
                shapePoints = shapePoints,
                maneuvers = maneuvers,
                totalLengthKm = totalKm,
                totalTimeSec = totalSec,
                destination = destination
            )
        } catch (e: Exception) {
            Log.d(NAV_TAG, "Errore routing Valhalla: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
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

                val (icon, instruction) = mapOsrmManeuver(mType, mMod, street)
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
                        instruction = "Procedi verso ${destination.name}",
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
        val segStart = (closestIndex - 2).coerceAtLeast(0)
        val segEnd = (closestIndex + 2).coerceAtMost(pts.lastIndex - 1)
        for (i in segStart..segEnd) {
            val segDist = pointToSegmentMeters(
                lat, lon,
                pts[i].latitude, pts[i].longitude,
                pts[i + 1].latitude, pts[i + 1].longitude
            )
            if (segDist < minDistMeters) {
                minDistMeters = segDist
            }
        }

        // Align bearing with route segment if navigating and user is on route
        if (closestIndex < pts.lastIndex && minDistMeters <= 45.0) {
            val p1 = pts[closestIndex]
            val p2 = pts[closestIndex + 1]
            val segLen = haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            if (segLen > 1.0) {
                val routeBrg = bearingDegrees(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                if (!hasGpsFix || lastGpsLocation == null) {
                    currentBearing = routeBrg
                }
            }
        }

        // 2. Off-route check (> 45 meters) with 8-second cooldown
        val nowElapsed = SystemClock.elapsedRealtime()
        if (minDistMeters > 45.0 && nowElapsed - lastRouteCalcElapsedMs > 8000L && !isRouting) {
            Log.d(NAV_TAG, "Fuori traccia di ${minDistMeters.roundToInt()}m (> 45m): avvio ricalcolo percorso")
            speak("Ricalcolo del percorso")
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
        etaText = "ETA ${timeFmt.format(arrivalDate)} • $remainingDistanceText"

        // 4. Voice guidance announcements at 300m and 50m
        if (distToTurnM <= 300.0 && distToTurnM > 70.0 && announced300ForManeuver != upcomingManeuverIdx) {
            announced300ForManeuver = upcomingManeuverIdx
            speak("Tra 300 metri, ${upcomingManeuver.instruction}")
        } else if (distToTurnM <= 50.0 && announced50ForManeuver != upcomingManeuverIdx) {
            announced50ForManeuver = upcomingManeuverIdx
            if (upcomingManeuver.turnIcon == TurnIconType.DESTINATION || upcomingManeuverIdx == maneuvers.lastIndex) {
                speak("Sei arrivato a destinazione")
            } else {
                speak("Tra 50 metri, ${upcomingManeuver.instruction}")
            }
        }

        // Complete navigation if within 18m of final destination
        if (upcomingManeuverIdx == maneuvers.lastIndex && distToTurnM < 18.0) {
            stopNavigation()
        }
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(Locale.ITALIAN, "%.1f km", meters / 1000.0)
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

    private fun pointToSegmentMeters(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double {
        val cosLat = cos(Math.toRadians(pLat))
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
        return haversineMeters(pLat, pLon, projLat, projLon)
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
