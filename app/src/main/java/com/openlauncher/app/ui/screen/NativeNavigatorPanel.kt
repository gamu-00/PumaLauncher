package com.openlauncher.app.ui.screen

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
import com.openlauncher.app.R
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
private const val LIGHT_STYLE_URI = "https://tiles.openfreemap.org/styles/positron"

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

private const val FALLBACK_LIGHT_STYLE_JSON = """
{
  "version": 8,
  "name": "PumaLightFallback",
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": {
        "background-color": "#F2F3F0"
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
    isCompactHeight: Boolean = false,
    moduleShape: RoundedCornerShape = AaModuleShape,
    homePressTrigger: Int = 0,
    closeSearchTrigger: Int = 0,
    onSearchOverlayChanged: (Boolean) -> Unit = {},
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
    val colors = LocalCockpitColors.current
    val isLightTheme = colors.isLight

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var appliedStyleIsLight by remember { mutableStateOf<Boolean?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }

    val latestIsLight by rememberUpdatedState(isLightTheme)
    val latestAccentHex by rememberUpdatedState(accentColorHex)
    val latestLat by rememberUpdatedState(engine.currentLat)
    val latestLon by rememberUpdatedState(engine.currentLon)
    val latestActiveRoute by rememberUpdatedState(engine.activeRoute)
    val latestStyleReady by rememberUpdatedState(styleReady)

    val hasOpenSearchOverlay = searchFocused || searchQuery.isNotEmpty() ||
        engine.isSearching || engine.searchResults.isNotEmpty() || engine.searchStatusMessage != null
    LaunchedEffect(hasOpenSearchOverlay) {
        onSearchOverlayChanged(hasOpenSearchOverlay)
    }

    LaunchedEffect(homePressTrigger) {
        if (homePressTrigger > 0) {
            searchQuery = ""
            searchFocused = false
            engine.clearSearchResults()
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            engine.isFollowingGps = true
            mapLibreMap?.let { map ->
                val targetBearing = if (engine.activeRoute != null) engine.currentBearing.toDouble() else 0.0
                val camPos = CameraPosition.Builder()
                    .target(LatLng(engine.currentLat, engine.currentLon))
                    .zoom(16.5)
                    .tilt(45.0)
                    .bearing(targetBearing)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(camPos), 650)
            }
        }
    }

    LaunchedEffect(closeSearchTrigger) {
        if (closeSearchTrigger > 0) {
            searchQuery = ""
            searchFocused = false
            engine.clearSearchResults()
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val mapView = remember(context) {
        val options = MapLibreMapOptions.createFromAttributes(context, null)
            .textureMode(true)
        MapView(context, options).apply {
            setBackgroundColor(AndroidColor.parseColor(if (isLightTheme) "#F2F3F0" else "#111318"))
            onCreate(null)
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

    // Synchronize MapLibre style URI with active Day/Night theme while preserving camera & route
    LaunchedEffect(mapLibreMap, isLightTheme) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (appliedStyleIsLight == isLightTheme) return@LaunchedEffect

        val isThemeSwitch = appliedStyleIsLight != null
        val preservedCamera = if (isThemeSwitch) map.cameraPosition else null
        appliedStyleIsLight = isLightTheme
        styleReady = false
        mapView.setBackgroundColor(AndroidColor.parseColor(if (isLightTheme) "#F2F3F0" else "#111318"))

        val targetUri = if (isLightTheme) LIGHT_STYLE_URI else DARK_STYLE_URI
        map.setStyle(targetUri) { loadedStyle ->
            if (latestIsLight != isLightTheme) return@setStyle
            if (!isLightTheme) {
                applyHighContrastDarkRoadsAndLabels(loadedStyle)
            }
            setupMapLayers(
                style = loadedStyle,
                accentHex = latestAccentHex,
                lat = latestLat,
                lon = latestLon,
                route = latestActiveRoute,
                isLightTheme = isLightTheme
            )
            if (preservedCamera != null && !engine.isFollowingGps) {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(preservedCamera))
            }
            styleReady = true
        }
    }

    // Update route polyline and accent color on map
    LaunchedEffect(mapLibreMap, styleReady, activeRoute, accentColorHex, isLightTheme) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        updateRouteOnStyle(style, activeRoute, accentColorHex, isLightTheme)
    }

    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = modifier
            .clip(moduleShape)
            .background(colors.cardSurface)
            .border(1.dp, colors.cardBorderBrush, moduleShape)
    ) {
        // 1. Native MapLibre MapView (textureMode = true so Compose overlays stay on top)
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
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

                    val initPos = CameraPosition.Builder()
                        .target(LatLng(engine.currentLat, engine.currentLon))
                        .zoom(16.5)
                        .tilt(45.0)
                        .bearing(0.0)
                        .build()
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(initPos))

                    // Fallback listener if remote style fails (e.g. offline)
                    mapView.addOnDidFailLoadingMapListener { errorMsg ->
                        if (latestStyleReady) return@addOnDidFailLoadingMapListener
                        val light = latestIsLight
                        val fallbackJson = if (light) FALLBACK_LIGHT_STYLE_JSON else FALLBACK_DARK_STYLE_JSON
                        Log.d(NAV_LOG_TAG, "MapLibre style load failed ($errorMsg), attivazione fallback ${if (light) "chiaro" else "scuro"}")
                        map.setStyle(Style.Builder().fromJson(fallbackJson)) { fallbackStyle ->
                            if (latestIsLight != light) return@setStyle
                            setupMapLayers(
                                style = fallbackStyle,
                                accentHex = latestAccentHex,
                                lat = latestLat,
                                lon = latestLon,
                                route = latestActiveRoute,
                                isLightTheme = light
                            )
                            styleReady = true
                        }
                    }

                    mapLibreMap = map
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
                .padding(if (isCompactHeight) 8.dp else 10.dp)
                .zIndex(10f),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 4.dp else 6.dp)
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
                    isCompactHeight = isCompactHeight,
                    onToggleAvoidTolls = {
                        val nextVal = !engine.avoidTolls
                        onToggleAvoidTolls(nextVal)
                        engine.updateAvoidTolls(nextVal, recalculateIfActive = true)
                    },
                    onToggleVoiceGuidance = {
                        val nextVoice = !engine.voiceGuidanceEnabled
                        engine.setVoiceGuidance(nextVoice)
                        onToggleVoiceGuidance(nextVoice)
                    },
                    onTerminate = {
                        engine.stopNavigation()
                    }
                )
            } else {
                // Compact Search Bar
                val searchBarShape = RoundedCornerShape(14.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompactHeight) 36.dp else 42.dp)
                        .zIndex(10f)
                        .clip(searchBarShape)
                        .background(colors.glassPillSurface)
                        .then(
                            if (searchFocused) {
                                Modifier.border(1.dp, accent.copy(alpha = 0.85f), searchBarShape)
                            } else {
                                Modifier.border(1.dp, colors.hairlineBorder, searchBarShape)
                            }
                        )
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.nav_search_content_desc),
                        tint = if (searchFocused) accent else colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.textPrimary,
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
                                engine.triggerImmediateSearch(searchQuery)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                searchFocused = state.isFocused
                            },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.nav_search_placeholder),
                                        color = colors.textSecondary,
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
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.nav_search_close),
                                tint = colors.textSecondary,
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
                            .heightIn(max = if (isCompactHeight) 180.dp else 260.dp)
                            .verticalScroll(rememberScrollState())
                            .zIndex(10f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.overlayPanelSurface)
                            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(14.dp))
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
                                        text = stringResource(R.string.nav_searching),
                                        color = colors.textPrimary,
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
                                                .background(colors.hairlineBorder.copy(alpha = 0.6f))
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
                                                engine.calculateRoute(place, avoidTolls = engine.avoidTolls)
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
                                                color = colors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (place.subtitle.isNotBlank()) {
                                                Text(
                                                    text = place.subtitle,
                                                    color = colors.textSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Directions,
                                            contentDescription = stringResource(R.string.nav_start_navigation),
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
                                        text = engine.searchStatusMessage ?: stringResource(R.string.nav_no_results),
                                        color = colors.textPrimary,
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
                .padding(if (isCompactHeight) 8.dp else 12.dp)
                .zIndex(10f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.glassPillSurface)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = speedKmh.toString(),
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                color = colors.textPrimary,
                fontSize = if (isCompactHeight) 18.sp else 22.sp,
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
                    .padding(if (isCompactHeight) 8.dp else 12.dp)
                    .zIndex(10f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent)
                    .clickable {
                        engine.isFollowingGps = true
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.nav_recenter),
                    tint = onAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.nav_recenter),
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
    val colors = LocalCockpitColors.current
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val bgColor = if (avoidTolls) accent else colors.glassPillSurface
    val contentColor = if (avoidTolls) onAccent else colors.textSecondary
    val chipShape = RoundedCornerShape(16.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(chipShape)
            .background(bgColor)
            .border(1.dp, if (avoidTolls) accent else colors.hairlineBorder, chipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = if (avoidTolls) Icons.Default.CheckCircle else Icons.Default.MoneyOff,
            contentDescription = stringResource(R.string.nav_avoid_tolls),
            tint = if (avoidTolls) onAccent else accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(R.string.nav_avoid_tolls),
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
    isCompactHeight: Boolean = false,
    onToggleAvoidTolls: () -> Unit,
    onToggleVoiceGuidance: () -> Unit,
    onTerminate: () -> Unit
) {
    val colors = LocalCockpitColors.current
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.overlayPanelSurface)
            .border(1.5.dp, accent, RoundedCornerShape(14.dp))
            .padding(
                horizontal = if (isCompactHeight) 10.dp else 12.dp,
                vertical = if (isCompactHeight) 7.dp else 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 5.dp else 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isCompactHeight) 38.dp else 46.dp)
                    .clip(CircleShape)
                    .background(accent)
            ) {
                Icon(
                    imageVector = turnIconVector(iconType),
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(if (isCompactHeight) 22.dp else 28.dp)
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
                            color = colors.textPrimary,
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
                    color = colors.textSecondary,
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
                    color = colors.textPrimary,
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
                        .background(if (voiceGuidanceEnabled) accent.copy(alpha = 0.2f) else colors.elevatedSurface)
                        .border(
                            width = 1.dp,
                            color = if (voiceGuidanceEnabled) accent else colors.hairlineBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onToggleVoiceGuidance)
                ) {
                    Icon(
                        imageVector = if (voiceGuidanceEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        contentDescription = stringResource(
                            if (voiceGuidanceEnabled) R.string.nav_mute_voice else R.string.nav_unmute_voice
                        ),
                        tint = if (voiceGuidanceEnabled) accent else colors.textSecondary,
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
                        text = stringResource(R.string.nav_end),
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
    route: ValhallaRoute?,
    isLightTheme: Boolean
) {
    val safeColor = parseSafeColor(accentHex)
    val routeCasingHex = if (isLightTheme) "#1E293B" else "#0A0D12"
    val gpsStrokeColor = if (isLightTheme && Color(safeColor).luminance() > 0.7f) {
        AndroidColor.parseColor("#0F172A")
    } else {
        AndroidColor.WHITE
    }

    if (style.getSource(SOURCE_ROUTE) == null) {
        style.addSource(GeoJsonSource(SOURCE_ROUTE, emptyFeatureCollectionJson()))
    }
    if (style.getLayer(LAYER_ROUTE_CASING) == null) {
        style.addLayer(
            LineLayer(LAYER_ROUTE_CASING, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor(routeCasingHex)),
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
                PropertyFactory.circleStrokeColor(gpsStrokeColor)
            )
        )
    }

    updateRouteOnStyle(style, route, accentHex, isLightTheme)
}

private fun updateGpsSource(style: Style, lat: Double, lon: Double) {
    val src = style.getSourceAs<GeoJsonSource>(SOURCE_GPS) ?: return
    src.setGeoJson(pointFeatureJson(lat, lon))
}

private fun updateRouteOnStyle(style: Style, route: ValhallaRoute?, accentHex: String, isLightTheme: Boolean) {
    val safeColor = parseSafeColor(accentHex)
    val routeCasingHex = if (isLightTheme) "#1E293B" else "#0A0D12"
    val gpsStrokeColor = if (isLightTheme && Color(safeColor).luminance() > 0.7f) {
        AndroidColor.parseColor("#0F172A")
    } else {
        AndroidColor.WHITE
    }
    (style.getLayer(LAYER_ROUTE_CASING) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(AndroidColor.parseColor(routeCasingHex))
    )
    (style.getLayer(LAYER_ROUTE) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(safeColor)
    )
    (style.getLayer(LAYER_GPS_HALO) as? CircleLayer)?.setProperties(
        PropertyFactory.circleColor(safeColor)
    )
    (style.getLayer(LAYER_GPS_DOT) as? CircleLayer)?.setProperties(
        PropertyFactory.circleColor(safeColor),
        PropertyFactory.circleStrokeColor(gpsStrokeColor)
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
