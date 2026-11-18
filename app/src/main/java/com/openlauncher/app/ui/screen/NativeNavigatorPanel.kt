package com.openlauncher.app.ui.screen

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openlauncher.app.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

private const val NAV_LOG_TAG = "PumaNav"
private const val DARK_STYLE_URI = "https://tiles.openfreemap.org/styles/dark"

private const val FALLBACK_DARK_STYLE_JSON = """
{
  "version": 8,
  "name": "PumaDarkFallback",
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": {
        "background-color": "#111318"
      }
    }
  ]
}
"""

private const val SOURCE_ROUTE = "puma-route-source"
private const val LAYER_ROUTE_CASING = "puma-route-casing-layer"
private const val LAYER_ROUTE = "puma-route-layer"
private const val SOURCE_GPS = "puma-gps-source"
private const val LAYER_GPS_HALO = "puma-gps-halo-layer"
private const val LAYER_GPS_DOT = "puma-gps-dot-layer"
private const val SOURCE_DEST = "puma-dest-source"
private const val LAYER_DEST_DOT = "puma-dest-dot-layer"

@SuppressLint("ClickableViewAccessibility")
@Composable
fun NativeNavigatorPanel(
    accent: Color,
    accentColorHex: String,
    avoidTolls: Boolean = false,
    voiceGuidanceEnabled: Boolean = true,
    onToggleAvoidTolls: (Boolean) -> Unit = {},
    onToggleVoiceGuidance: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Ensure MapLibre singleton is initialized before constructing MapView
    remember(context) {
        MapLibre.getInstance(context)
    }

    val engine = remember(context) {
        NavigationEngine(context)
    }

    // Keep persistent settings synced with NavigationEngine
    LaunchedEffect(avoidTolls) {
        engine.updateAvoidTolls(avoidTolls, recalculateIfActive = false)
    }
    LaunchedEffect(voiceGuidanceEnabled) {
        engine.setVoiceGuidance(voiceGuidanceEnabled)
    }

    val speedKmh by engine.currentSpeedKmh.collectAsState()

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }

    val mapView = remember(context) {
        val options = MapLibreMapOptions.createFromAttributes(context, null)
            .textureMode(true)
        MapView(context, options).apply {
            setBackgroundColor(AndroidColor.parseColor("#111318"))
            onCreate(null)
            // Intercept ACTION_UP / ACTION_CANCEL without consuming so video window is restored after map touch
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    restoreActiveVideoWindow(context)
                }
                false
            }
        }
    }

    // Lifecycle management for MapView + GPS tracking
    DisposableEffect(lifecycleOwner, mapView) {
        engine.startLocationTracking()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    engine.startLocationTracking()
                }
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    engine.startLocationTracking()
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    engine.shutdown()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            HomeFreeformState.isNavigatorSearchFocused = false
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
            engine.shutdown()
        }
    }

    // Debounce 400ms for Photon + Nominatim fallback search
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            engine.clearSearchResults()
        } else {
            delay(400L)
            engine.searchPhoton(q)
        }
    }

    // Update camera when following GPS (zoom = 16.5, tilt = 45.0, bearing aligned to travel direction in navigation)
    val lat = engine.currentLat
    val lon = engine.currentLon
    val brg = engine.currentBearing
    val isFollowing = engine.isFollowingGps
    val activeRoute = engine.activeRoute

    LaunchedEffect(mapLibreMap, styleReady, lat, lon, brg, isFollowing, activeRoute) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style
        if (style != null && style.isFullyLoaded) {
            updateGpsSource(style, lat, lon)
        }
        if (isFollowing) {
            val targetBearing = if (activeRoute != null) brg.toDouble() else 0.0
            val camPos = CameraPosition.Builder()
                .target(LatLng(lat, lon))
                .zoom(16.5)
                .tilt(45.0)
                .bearing(targetBearing)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(camPos), 650)
        }
    }

    // Update route polyline and accent color on map
    LaunchedEffect(mapLibreMap, styleReady, activeRoute, accentColorHex) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        updateRouteOnStyle(style, activeRoute, accentColorHex)
    }

    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = modifier
            .clip(AaModuleShape)
            .background(Color(0xFF111318))
            .border(1.dp, AaBorder, AaModuleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.changes.isNotEmpty() && event.changes.all { !it.pressed }) {
                            restoreActiveVideoWindow(context)
                        }
                    }
                }
            }
    ) {
        // 1. Native MapLibre MapView (textureMode = true so Compose overlays stay on top)
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    mapLibreMap = map
                    map.uiSettings.apply {
                        isCompassEnabled = false
                        isLogoEnabled = false
                        isAttributionEnabled = false
                        isRotateGesturesEnabled = true
                        isTiltGesturesEnabled = true
                        isZoomGesturesEnabled = true
                        isScrollGesturesEnabled = true
                    }

                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            engine.isFollowingGps = false
                        }
                    }

                    // Fallback listener if remote style fails (e.g. offline)
                    mapView.addOnDidFailLoadingMapListener { errorMsg ->
                        Log.d(NAV_LOG_TAG, "MapLibre style load failed ($errorMsg), attivazione fallback scuro #111318")
                        map.setStyle(Style.Builder().fromJson(FALLBACK_DARK_STYLE_JSON)) { fallbackStyle ->
                            setupMapLayers(fallbackStyle, accentColorHex, engine.currentLat, engine.currentLon, engine.activeRoute)
                            styleReady = true
                        }
                    }

                    // Load OpenFreeMap dark vector style and boost road/label contrast
                    map.setStyle(DARK_STYLE_URI) { loadedStyle ->
                        applyHighContrastDarkRoadsAndLabels(loadedStyle)
                        setupMapLayers(loadedStyle, accentColorHex, engine.currentLat, engine.currentLon, engine.activeRoute)
                        styleReady = true
                    }

                    val initPos = CameraPosition.Builder()
                        .target(LatLng(engine.currentLat, engine.currentLon))
                        .zoom(16.5)
                        .tilt(45.0)
                        .bearing(0.0)
                        .build()
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(initPos))
                }
                mapView
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        )

        // 2. Top overlay (zIndex = 10f): Turn-by-Turn Banner OR Search Bar + "No pedaggi" chip + Dropdown
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(10.dp)
                .zIndex(10f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (activeRoute != null) {
                TurnByTurnBanner(
                    iconType = engine.nextTurnIcon,
                    distanceText = engine.nextTurnDistanceText,
                    streetText = engine.nextTurnStreet,
                    instructionText = engine.nextTurnInstruction,
                    etaText = engine.etaText,
                    avoidTolls = engine.avoidTolls,
                    voiceGuidanceEnabled = engine.voiceGuidanceEnabled,
                    accent = accent,
                    onToggleAvoidTolls = {
                        val nextVal = !engine.avoidTolls
                        onToggleAvoidTolls(nextVal)
                        engine.updateAvoidTolls(nextVal, recalculateIfActive = true)
                        restoreActiveVideoWindow(context)
                    },
                    onToggleVoiceGuidance = {
                        val nextVoice = !engine.voiceGuidanceEnabled
                        engine.setVoiceGuidance(nextVoice)
                        onToggleVoiceGuidance(nextVoice)
                        restoreActiveVideoWindow(context)
                    },
                    onTerminate = {
                        engine.stopNavigation()
                        restoreActiveVideoWindow(context)
                    }
                )
            } else {
                // Compact Search Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .zIndex(10f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF0161920))
                        .border(
                            width = 1.dp,
                            color = if (searchFocused) accent else AaBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Cerca destinazione",
                        tint = if (searchFocused) accent else AaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AaTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                Log.d(NAV_LOG_TAG, "ImeAction.Search premuto con query=\"$searchQuery\"")
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                HomeFreeformState.isNavigatorSearchFocused = false
                                engine.triggerImmediateSearch(searchQuery)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                searchFocused = state.isFocused
                                HomeFreeformState.isNavigatorSearchFocused = state.isFocused
                                if (!state.isFocused && engine.searchResults.isEmpty() && !engine.isSearching) {
                                    restoreActiveVideoWindow(context)
                                }
                            },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Cerca indirizzo o destinazione...",
                                        color = AaTextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    if (engine.isSearching || engine.isRouting) {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (searchQuery.isNotEmpty() || searchFocused) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                engine.clearSearchResults()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                HomeFreeformState.isNavigatorSearchFocused = false
                                restoreActiveVideoWindow(context)
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Chiudi ricerca",
                                tint = AaTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Compact "No pedaggi" toggle chip row right below the search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(10f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoTollsChip(
                        avoidTolls = engine.avoidTolls,
                        accent = accent,
                        onClick = {
                            val nextVal = !engine.avoidTolls
                            onToggleAvoidTolls(nextVal)
                            engine.updateAvoidTolls(nextVal, recalculateIfActive = true)
                            restoreActiveVideoWindow(context)
                        }
                    )
                }

                // Explicit dropdown below search bar (zIndex = 10f):
                // 1) Spinner while searching, 2) Clickable results list, or 3) Status/error message
                val showDropdown = searchQuery.trim().length >= 2 &&
                    (engine.isSearching || engine.searchResults.isNotEmpty() || engine.searchStatusMessage != null)

                if (showDropdown) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(10f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFA161920))
                            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    ) {
                        when {
                            engine.isSearching && engine.searchResults.isEmpty() -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Ricerca in corso...",
                                        color = AaTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            engine.searchResults.isNotEmpty() -> {
                                engine.searchResults.forEachIndexed { idx, place ->
                                    if (idx > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(AaBorder.copy(alpha = 0.5f))
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                Log.d(NAV_LOG_TAG, "Destinazione selezionata: \"${place.displayName}\" (${place.lat}, ${place.lon})")
                                                searchQuery = ""
                                                engine.clearSearchResults()
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                HomeFreeformState.isNavigatorSearchFocused = false
                                                engine.calculateRoute(place, avoidTolls = engine.avoidTolls)
                                                restoreActiveVideoWindow(context)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = place.name,
                                                color = AaTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (place.subtitle.isNotBlank()) {
                                                Text(
                                                    text = place.subtitle,
                                                    color = AaTextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Directions,
                                            contentDescription = "Avvia navigazione",
                                            tint = accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            engine.searchStatusMessage != null -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = engine.searchStatusMessage ?: "Nessun risultato trovato",
                                        color = AaTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Digital GPS Speedometer Badge in bottom-left corner (aligned with "Ricentra" on bottom-right)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .zIndex(10f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xD9111318))
                .border(1.dp, accent, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = speedKmh.toString(),
                color = AaTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "km/h",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }

        // 4. "Ricentra" button — visible ONLY when user manually moves the map (!engine.isFollowingGps)
        if (!engine.isFollowingGps) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .zIndex(10f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent)
                    .clickable {
                        engine.isFollowingGps = true
                        restoreActiveVideoWindow(context)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Ricentra",
                    tint = onAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Ricentra",
                    color = onAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NoTollsChip(
    avoidTolls: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val bgColor = if (avoidTolls) accent else Color(0xD9111318)
    val borderColor = if (avoidTolls) accent else AaBorder
    val contentColor = if (avoidTolls) onAccent else AaTextSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = if (avoidTolls) Icons.Default.CheckCircle else Icons.Default.MoneyOff,
            contentDescription = "No pedaggi",
            tint = if (avoidTolls) onAccent else accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "No pedaggi",
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Enhances road and label contrast on OpenFreeMap's dark style (`https://tiles.openfreemap.org/styles/dark`)
 * while preserving clean intersection casings.
 */
private fun applyHighContrastDarkRoadsAndLabels(style: Style) {
    val roadKeywords = listOf(
        "road", "highway", "street", "motorway", "trunk",
        "primary", "secondary", "tertiary", "bridge", "tunnel"
    )
    val excludedRoadKeywords = listOf("rail", "ferry", "boundary", "border")
    val labelKeywords = listOf("road", "highway", "street", "place", "label")

    var updatedRoads = 0
    var updatedLabels = 0

    for (layer in style.layers) {
        val idLower = layer.id.lowercase(Locale.ROOT)
        if (idLower.startsWith("puma-")) continue

        when (layer) {
            is LineLayer -> {
                val isRoad = roadKeywords.any { it in idLower } &&
                    excludedRoadKeywords.none { it in idLower }
                if (isRoad) {
                    val targetColorHex = when {
                        "casing" in idLower || "outline" in idLower -> "#07090D"
                        "motorway" in idLower || "trunk" in idLower || "primary" in idLower -> "#F1F5F9"
                        "secondary" in idLower || "tertiary" in idLower -> "#94A3B8"
                        else -> "#64748B"
                    }
                    runCatching {
                        layer.setProperties(PropertyFactory.lineColor(targetColorHex))
                        updatedRoads++
                    }
                }
            }

            is SymbolLayer -> {
                val isLabel = labelKeywords.any { it in idLower }
                if (isLabel) {
                    runCatching {
                        layer.setProperties(
                            PropertyFactory.textColor("#F8FAFC"),
                            PropertyFactory.textHaloColor("#090B10"),
                            PropertyFactory.textHaloWidth(1.5f)
                        )
                        updatedLabels++
                    }
                }
            }
        }
    }
    Log.d(NAV_LOG_TAG, "Alto contrasto mappa applicato: $updatedRoads LineLayer stradali e $updatedLabels SymbolLayer etichette aggiornati")
}

@Composable
private fun TurnByTurnBanner(
    iconType: TurnIconType,
    distanceText: String,
    streetText: String,
    instructionText: String,
    etaText: String,
    avoidTolls: Boolean,
    voiceGuidanceEnabled: Boolean,
    accent: Color,
    onToggleAvoidTolls: () -> Unit,
    onToggleVoiceGuidance: () -> Unit,
    onTerminate: () -> Unit
) {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF2141820))
            .border(1.5.dp, accent, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent)
            ) {
                Icon(
                    imageVector = turnIconVector(iconType),
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = distanceText.ifBlank { "---" },
                        color = accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (streetText.isNotBlank()) {
                        Text(
                            text = streetText,
                            color = AaTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                }
                Text(
                    text = instructionText,
                    color = AaTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = etaText,
                    color = AaTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                NoTollsChip(
                    avoidTolls = avoidTolls,
                    accent = accent,
                    onClick = onToggleAvoidTolls
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Touch-friendly TTS Mute / Unmute icon button next to "Termina"
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (voiceGuidanceEnabled) accent.copy(alpha = 0.2f) else Color(0xFF272C35))
                        .border(
                            width = 1.dp,
                            color = if (voiceGuidanceEnabled) accent else AaBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onToggleVoiceGuidance)
                ) {
                    Icon(
                        imageVector = if (voiceGuidanceEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        contentDescription = if (voiceGuidanceEnabled) "Disattiva voce guida" else "Attiva voce guida",
                        tint = if (voiceGuidanceEnabled) accent else AaTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFD32F2F))
                        .clickable(onClick = onTerminate)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Termina",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun turnIconVector(type: TurnIconType): ImageVector = when (type) {
    TurnIconType.STRAIGHT -> Icons.Default.Straight
    TurnIconType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    TurnIconType.LEFT -> Icons.Default.TurnLeft
    TurnIconType.SHARP_LEFT -> Icons.Default.TurnSharpLeft
    TurnIconType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    TurnIconType.RIGHT -> Icons.Default.TurnRight
    TurnIconType.SHARP_RIGHT -> Icons.Default.TurnSharpRight
    TurnIconType.UTURN -> Icons.Default.UTurnLeft
    TurnIconType.ROUNDABOUT -> Icons.Default.RoundaboutRight
    TurnIconType.DESTINATION -> Icons.Default.Flag
}

private fun setupMapLayers(
    style: Style,
    accentHex: String,
    lat: Double,
    lon: Double,
    route: ValhallaRoute?
) {
    val safeColor = parseSafeColor(accentHex)

    if (style.getSource(SOURCE_ROUTE) == null) {
        style.addSource(GeoJsonSource(SOURCE_ROUTE, emptyFeatureCollectionJson()))
    }
    if (style.getLayer(LAYER_ROUTE_CASING) == null) {
        style.addLayer(
            LineLayer(LAYER_ROUTE_CASING, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor("#0A0D12")),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LAYER_ROUTE) == null) {
        style.addLayer(
            LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(safeColor),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    if (style.getSource(SOURCE_DEST) == null) {
        style.addSource(GeoJsonSource(SOURCE_DEST, emptyFeatureCollectionJson()))
    }
    if (style.getLayer(LAYER_DEST_DOT) == null) {
        style.addLayer(
            CircleLayer(LAYER_DEST_DOT, SOURCE_DEST).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(safeColor)
            )
        )
    }

    if (style.getSource(SOURCE_GPS) == null) {
        style.addSource(GeoJsonSource(SOURCE_GPS, pointFeatureJson(lat, lon)))
    }
    if (style.getLayer(LAYER_GPS_HALO) == null) {
        style.addLayer(
            CircleLayer(LAYER_GPS_HALO, SOURCE_GPS).withProperties(
                PropertyFactory.circleRadius(14f),
                PropertyFactory.circleColor(safeColor),
                PropertyFactory.circleOpacity(0.25f)
            )
        )
    }
    if (style.getLayer(LAYER_GPS_DOT) == null) {
        style.addLayer(
            CircleLayer(LAYER_GPS_DOT, SOURCE_GPS).withProperties(
                PropertyFactory.circleRadius(7.5f),
                PropertyFactory.circleColor(safeColor),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE)
            )
        )
    }

    updateRouteOnStyle(style, route, accentHex)
}

private fun updateGpsSource(style: Style, lat: Double, lon: Double) {
    val src = style.getSourceAs<GeoJsonSource>(SOURCE_GPS) ?: return
    src.setGeoJson(pointFeatureJson(lat, lon))
}

private fun updateRouteOnStyle(style: Style, route: ValhallaRoute?, accentHex: String) {
    val safeColor = parseSafeColor(accentHex)
    (style.getLayer(LAYER_ROUTE) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(safeColor)
    )
    (style.getLayer(LAYER_GPS_HALO) as? CircleLayer)?.setProperties(
        PropertyFactory.circleColor(safeColor)
    )
    (style.getLayer(LAYER_GPS_DOT) as? CircleLayer)?.setProperties(
        PropertyFactory.circleColor(safeColor)
    )
    (style.getLayer(LAYER_DEST_DOT) as? CircleLayer)?.setProperties(
        PropertyFactory.circleStrokeColor(safeColor)
    )

    val routeSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE) ?: return
    val destSrc = style.getSourceAs<GeoJsonSource>(SOURCE_DEST) ?: return

    if (route == null || route.shapePoints.isEmpty()) {
        routeSrc.setGeoJson(emptyFeatureCollectionJson())
        destSrc.setGeoJson(emptyFeatureCollectionJson())
    } else {
        routeSrc.setGeoJson(lineStringFeatureJson(route.shapePoints))
        destSrc.setGeoJson(pointFeatureJson(route.destination.lat, route.destination.lon))
    }
}

private fun parseSafeColor(hex: String): Int =
    runCatching { AndroidColor.parseColor(hex) }.getOrElse { AndroidColor.parseColor("#2979FF") }

private fun emptyFeatureCollectionJson(): String =
    """{"type":"FeatureCollection","features":[]}"""

private fun pointFeatureJson(lat: Double, lon: Double): String {
    return JSONObject().apply {
        put("type", "Feature")
        put(
            "geometry",
            JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().put(lon).put(lat))
            }
        )
        put("properties", JSONObject())
    }.toString()
}

private fun lineStringFeatureJson(points: List<LatLng>): String {
    val coords = JSONArray()
    for (pt in points) {
        coords.put(JSONArray().put(pt.longitude).put(pt.latitude))
    }
    return JSONObject().apply {
        put("type", "Feature")
        put(
            "geometry",
            JSONObject().apply {
                put("type", "LineString")
                put("coordinates", coords)
            }
        )
        put("properties", JSONObject())
    }.toString()
}
