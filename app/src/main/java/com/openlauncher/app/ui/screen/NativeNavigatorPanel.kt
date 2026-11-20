package com.openlauncher.app.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openlauncher.app.R
import com.openlauncher.app.data.DEFAULT_UI_SCALE
import com.openlauncher.app.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import kotlin.math.abs

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

private const val SOURCE_ROUTE_TRAVELED = "puma-route-traveled-source"
private const val LAYER_ROUTE_TRAVELED_CASING = "puma-route-traveled-casing-layer"
private const val LAYER_ROUTE_TRAVELED = "puma-route-traveled-layer"
private const val SOURCE_ROUTE = "puma-route-source"
private const val LAYER_ROUTE_CASING = "puma-route-casing-layer"
private const val LAYER_ROUTE = "puma-route-layer"
private const val SOURCE_DEST = "puma-dest-source"
private const val LAYER_DEST_DOT = "puma-dest-dot-layer"
private const val DRIVING_ZOOM = 16.5
private const val DRIVING_TILT = 40.0
private const val STATIONARY_TILT = 0.0

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
    locationPermissionTrigger: Int = 0,
    navigationEngine: NavigationEngine,
    isHomeVisible: Boolean = true,
    onSearchOverlayChanged: (Boolean) -> Unit = {},
    uiScale: Float = DEFAULT_UI_SCALE,
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

    val engine = navigationEngine

    // Keep persistent settings synced with NavigationEngine
    LaunchedEffect(engine, avoidTolls) {
        engine.updateAvoidTolls(avoidTolls, recalculateIfActive = false)
    }
    LaunchedEffect(engine, voiceGuidanceEnabled) {
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
    var trackingListenerAttached by remember { mutableStateOf(false) }
    var cameraBearing by remember { mutableFloatStateOf(0f) }
    var mapHeightPx by remember { mutableIntStateOf(0) }

    val latestIsLight by rememberUpdatedState(isLightTheme)
    val latestAccentHex by rememberUpdatedState(accentColorHex)
    val latestActiveRoute by rememberUpdatedState(engine.activeRoute)
    val latestTraveledPoints by rememberUpdatedState(engine.traveledRoutePoints)
    val latestRemainingPoints by rememberUpdatedState(engine.remainingRoutePoints)
    val latestStyleReady by rememberUpdatedState(styleReady)
    val latestMapHeightPx by rememberUpdatedState(mapHeightPx)

    val cameraTrackingListener = remember(engine) {
        object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                engine.isFollowingGps = false
            }

            override fun onCameraTrackingChanged(currentMode: Int) {
                if (currentMode == CameraMode.TRACKING_GPS ||
                    currentMode == CameraMode.TRACKING ||
                    currentMode == CameraMode.TRACKING_GPS_NORTH
                ) {
                    engine.isFollowingGps = true
                }
            }
        }
    }

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
            mapLibreMap?.let { map ->
                recenterCamera(map, engine, latestMapHeightPx)
            } ?: run {
                engine.isFollowingGps = true
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
    val mapDisposed = remember(mapView) { mutableStateOf(false) }

    // Lifecycle management for MapView + GPS tracking (NavigationEngine outlives panel composition)
    DisposableEffect(lifecycleOwner, mapView, engine) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            engine.startLocationTracking()
        }
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
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    if (engine.activeRoute == null) {
                        engine.stopLocationTracking()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mapDisposed.value = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            detachNativeLocationComponent(
                map = mapLibreMap,
                cameraTrackingListener = cameraTrackingListener,
                isListenerAttached = trackingListenerAttached
            )
            trackingListenerAttached = false
            mapLibreMap = null
            styleReady = false
            appliedStyleIsLight = null
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
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

    val activeRoute = engine.activeRoute
    val traveledRoutePoints = engine.traveledRoutePoints
    val remainingRoutePoints = engine.remainingRoutePoints

    // Stop GNSS tracking if an active route completes while app is backgrounded
    LaunchedEffect(activeRoute) {
        if (activeRoute == null && !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            engine.stopLocationTracking()
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
            if (mapDisposed.value || latestIsLight != isLightTheme) return@setStyle
            if (!isLightTheme) {
                applyHighContrastDarkRoadsAndLabels(loadedStyle)
            }
            setupMapLayers(
                style = loadedStyle,
                accentHex = latestAccentHex,
                route = latestActiveRoute,
                traveledPoints = latestTraveledPoints,
                remainingPoints = latestRemainingPoints,
                isLightTheme = isLightTheme
            )
            activateNativeLocationComponent(
                context = context,
                map = map,
                style = loadedStyle,
                engine = engine,
                mapHeightPx = latestMapHeightPx,
                cameraTrackingListener = cameraTrackingListener,
                isListenerAttached = trackingListenerAttached,
                onListenerAttached = { trackingListenerAttached = true }
            )
            if (preservedCamera != null && !engine.isFollowingGps) {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(preservedCamera))
            }
            styleReady = true
        }
    }

    // Activate LocationComponent and start GPS if runtime permission is granted after map loaded
    LaunchedEffect(locationPermissionTrigger, mapLibreMap, styleReady) {
        if (locationPermissionTrigger <= 0) return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded || !engine.hasLocationPermission()) return@LaunchedEffect

        engine.startLocationTracking()
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated || !lc.isLocationComponentEnabled) {
            activateNativeLocationComponent(
                context = context,
                map = map,
                style = style,
                engine = engine,
                mapHeightPx = latestMapHeightPx,
                cameraTrackingListener = cameraTrackingListener,
                isListenerAttached = trackingListenerAttached,
                onListenerAttached = { trackingListenerAttached = true }
            )
        }
    }

    // Re-attach FilteredGpsLocationEngine and redraw active route & destination marker when returning to Home
    LaunchedEffect(isHomeVisible, mapLibreMap, styleReady, engine) {
        if (!isHomeVisible || !styleReady) return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            engine.startLocationTracking()
        }
        if (engine.hasLocationPermission()) {
            val lc = map.locationComponent
            if (!lc.isLocationComponentActivated ||
                !lc.isLocationComponentEnabled ||
                lc.locationEngine !== engine.locationEngine
            ) {
                activateNativeLocationComponent(
                    context = context,
                    map = map,
                    style = style,
                    engine = engine,
                    mapHeightPx = latestMapHeightPx,
                    cameraTrackingListener = cameraTrackingListener,
                    isListenerAttached = trackingListenerAttached,
                    onListenerAttached = { trackingListenerAttached = true }
                )
            }
        }
        setupMapLayers(
            style = style,
            accentHex = latestAccentHex,
            route = latestActiveRoute,
            traveledPoints = latestTraveledPoints,
            remainingPoints = latestRemainingPoints,
            isLightTheme = latestIsLight
        )
        applyDrivingPadding(map, engine, latestMapHeightPx)
    }

    // Keep padding synchronized so the vehicle chevron sits in the lower third during driving/guidance
    LaunchedEffect(
        mapLibreMap,
        styleReady,
        mapHeightPx,
        engine.isFollowingGps,
        engine.isMoving,
        activeRoute != null
    ) {
        val map = mapLibreMap ?: return@LaunchedEffect
        applyDrivingPadding(map, engine, mapHeightPx)
    }

    // Smooth dynamic tilt: 40.0 when moving, 0.0 when stationary (with 1s debounce to avoid stop-and-go bobbing)
    LaunchedEffect(mapLibreMap, styleReady, engine.isFollowingGps, engine.isMoving) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady || !engine.isFollowingGps) return@LaunchedEffect
        if (!engine.isMoving) {
            delay(1000L)
        }
        if (!engine.isFollowingGps) return@LaunchedEffect
        val lc = map.locationComponent
        if (lc.isLocationComponentActivated &&
            lc.isLocationComponentEnabled &&
            lc.cameraMode == CameraMode.TRACKING_GPS
        ) {
            val targetTilt = if (engine.isMoving) DRIVING_TILT else STATIONARY_TILT
            lc.tiltWhileTracking(targetTilt, 600L)
        }
    }

    // Update route polyline (traveled vs remaining) and accent color on map
    LaunchedEffect(
        mapLibreMap,
        styleReady,
        activeRoute,
        traveledRoutePoints,
        remainingRoutePoints,
        accentColorHex,
        isLightTheme
    ) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        updateRouteOnStyle(
            style = style,
            route = activeRoute,
            traveledPoints = traveledRoutePoints,
            remainingPoints = remainingRoutePoints,
            accentHex = accentColorHex,
            isLightTheme = isLightTheme
        )
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
                    if (mapDisposed.value) return@getMapAsync
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
                            val lc = map.locationComponent
                            if (lc.isLocationComponentActivated && lc.cameraMode != CameraMode.NONE) {
                                lc.cameraMode = CameraMode.NONE
                            }
                        }
                    }

                    map.addOnCameraMoveListener {
                        val raw = map.cameraPosition.bearing.toFloat()
                        val norm = ((raw % 360f) + 360f) % 360f
                        val signed = if (norm > 180f) norm - 360f else norm
                        val crossedThreshold = (abs(signed) <= 1f) != (abs(cameraBearing) <= 1f)
                        if (crossedThreshold || abs(cameraBearing - signed) > 0.25f) {
                            cameraBearing = signed
                        }
                    }

                    val initPos = CameraPosition.Builder()
                        .target(LatLng(engine.currentLat, engine.currentLon))
                        .zoom(DRIVING_ZOOM)
                        .tilt(if (engine.isFollowingGps && engine.isMoving) DRIVING_TILT else STATIONARY_TILT)
                        .bearing(if (engine.hasGpsFix && engine.isFollowingGps) engine.currentBearing.toDouble() else 0.0)
                        .build()
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(initPos))

                    // Fallback listener if remote style fails (e.g. offline)
                    mapView.addOnDidFailLoadingMapListener { errorMsg ->
                        if (mapDisposed.value || latestStyleReady) return@addOnDidFailLoadingMapListener
                        val light = latestIsLight
                        val fallbackJson = if (light) FALLBACK_LIGHT_STYLE_JSON else FALLBACK_DARK_STYLE_JSON
                        Log.d(NAV_LOG_TAG, "MapLibre style load failed ($errorMsg), attivazione fallback ${if (light) "chiaro" else "scuro"}")
                        map.setStyle(Style.Builder().fromJson(fallbackJson)) { fallbackStyle ->
                            if (mapDisposed.value || latestIsLight != light) return@setStyle
                            setupMapLayers(
                                style = fallbackStyle,
                                accentHex = latestAccentHex,
                                route = latestActiveRoute,
                                traveledPoints = latestTraveledPoints,
                                remainingPoints = latestRemainingPoints,
                                isLightTheme = light
                            )
                            activateNativeLocationComponent(
                                context = context,
                                map = map,
                                style = fallbackStyle,
                                engine = engine,
                                mapHeightPx = latestMapHeightPx,
                                cameraTrackingListener = cameraTrackingListener,
                                isListenerAttached = trackingListenerAttached,
                                onListenerAttached = { trackingListenerAttached = true }
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
                .onSizeChanged { mapHeightPx = it.height }
                .zIndex(0f)
        )

        // Isolated density for navigation overlays & banners (anti-explosion at 140%/160% and readable at 65%)
        val physicalMetrics = LocalPhysicalScreenMetrics.current
        val baseDensity = physicalMetrics.baseDensity
        val currentDensity = LocalDensity.current
        val effectiveBannerScale = uiScale.coerceIn(0.80f, 1.12f)
        val relativeFontScale = if (baseDensity.fontScale > 0f) {
            (currentDensity.fontScale / baseDensity.fontScale).coerceIn(0.88f, 1.08f)
        } else {
            1.0f
        }
        val overlayDensity = Density(
            density = baseDensity.density * effectiveBannerScale,
            fontScale = baseDensity.fontScale * relativeFontScale
        )

        CompositionLocalProvider(LocalDensity provides overlayDensity) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                val mapPanelHeight = maxHeight
                val mapPanelWidth = maxWidth
                val tightOverlay = isCompactHeight || mapPanelHeight < 380.dp
                val overlayPad = if (tightOverlay) 8.dp else 10.dp
                val availableBannerWidth = (mapPanelWidth - overlayPad * 2).coerceAtMost(520.dp)
                val maxBannerHeight = (mapPanelHeight * 0.29f).coerceAtLeast(76.dp)

                // 2. Top overlay (zIndex = 10f): Turn-by-Turn Banner OR Search Bar + "No pedaggi" chip + Dropdown
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(overlayPad)
                        .zIndex(10f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (tightOverlay) 4.dp else 6.dp)
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
                            isCompactHeight = tightOverlay,
                            availableWidth = availableBannerWidth,
                            maxBannerHeight = maxBannerHeight,
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
                        Column(
                            modifier = Modifier
                                .widthIn(max = 520.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(if (tightOverlay) 4.dp else 6.dp)
                        ) {
                            // Compact Search Bar
                            val searchBarShape = RoundedCornerShape(14.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (tightOverlay) 36.dp else 42.dp)
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
                                    compact = tightOverlay,
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
                                        .heightIn(max = if (tightOverlay) 180.dp else 260.dp)
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
                                                            mapLibreMap?.let { map ->
                                                                recenterCamera(map, engine, latestMapHeightPx)
                                                            }
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
                }

                // 3. Digital GPS Speedometer Badge in bottom-left corner (aligned with "Ricentra" on bottom-right)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(if (tightOverlay) 8.dp else 12.dp)
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
                        fontSize = if (tightOverlay) 18.sp else 22.sp,
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

                // 4. Bottom-right controls: Compass (when rotated or in Free Look) + "Ricentra" (when !isFollowingGps)
                val showCompass = abs(cameraBearing) > 1f || !engine.isFollowingGps
                val showRecenter = !engine.isFollowingGps
                if (showCompass || showRecenter) {
                    val compassDesc = stringResource(R.string.nav_compass_reset)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(if (tightOverlay) 8.dp else 12.dp)
                            .zIndex(10f)
                    ) {
                        if (showCompass) {
                            val compassSize = if (tightOverlay) 38.dp else 42.dp
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(compassSize)
                                    .clip(CircleShape)
                                    .background(colors.glassPillSurface)
                                    .border(1.dp, colors.hairlineBorder, CircleShape)
                                    .semantics { contentDescription = compassDesc }
                                    .clickable {
                                        mapLibreMap?.easeCamera(CameraUpdateFactory.bearingTo(0.0), 400)
                                    }
                            ) {
                                val northColor = colors.dangerContent
                                val southColor = colors.textSecondary
                                val pivotColor = colors.textPrimary
                                Canvas(
                                    modifier = Modifier
                                        .size(if (tightOverlay) 22.dp else 24.dp)
                                        .graphicsLayer { rotationZ = -cameraBearing }
                                ) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val tipOffset = size.height * 0.42f
                                    val halfWidth = size.width * 0.16f

                                    val northPath = Path().apply {
                                        moveTo(cx, cy - tipOffset)
                                        lineTo(cx + halfWidth, cy)
                                        lineTo(cx - halfWidth, cy)
                                        close()
                                    }
                                    drawPath(northPath, color = northColor)

                                    val southPath = Path().apply {
                                        moveTo(cx, cy + tipOffset)
                                        lineTo(cx + halfWidth, cy)
                                        lineTo(cx - halfWidth, cy)
                                        close()
                                    }
                                    drawPath(southPath, color = southColor.copy(alpha = 0.75f))

                                    drawCircle(
                                        color = pivotColor,
                                        radius = size.minDimension * 0.08f
                                    )
                                }
                            }
                        }

                        if (showRecenter) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(accent)
                                    .clickable {
                                        mapLibreMap?.let { map ->
                                            recenterCamera(map, engine, latestMapHeightPx)
                                        } ?: run {
                                            engine.isFollowingGps = true
                                        }
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
            }
        }
    }
}

@Composable
private fun NoTollsChip(
    avoidTolls: Boolean,
    accent: Color,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalCockpitColors.current
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val bgColor = if (avoidTolls) accent else colors.glassPillSurface
    val contentColor = if (avoidTolls) onAccent else colors.textSecondary
    val chipShape = RoundedCornerShape(16.dp)
    val chipHeight = if (compact) 26.dp else 30.dp

    if (iconOnly) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(chipHeight)
                .clip(chipShape)
                .background(bgColor)
                .border(1.dp, if (avoidTolls) accent else colors.hairlineBorder, chipShape)
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = if (avoidTolls) Icons.Default.CheckCircle else Icons.Default.MoneyOff,
                contentDescription = stringResource(R.string.nav_avoid_tolls),
                tint = if (avoidTolls) onAccent else accent,
                modifier = Modifier.size(14.dp)
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .height(chipHeight)
                .widthIn(max = if (compact) 112.dp else 148.dp)
                .clip(chipShape)
                .background(bgColor)
                .border(1.dp, if (avoidTolls) accent else colors.hairlineBorder, chipShape)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 8.dp else 10.dp)
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
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    availableWidth: Dp = 480.dp,
    maxBannerHeight: Dp = 120.dp,
    onToggleAvoidTolls: () -> Unit,
    onToggleVoiceGuidance: () -> Unit,
    onTerminate: () -> Unit
) {
    val colors = LocalCockpitColors.current
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val compactRow = isCompactHeight || availableWidth < 360.dp || maxBannerHeight < 96.dp
    val iconOnlyTolls = availableWidth < 280.dp
    val actionHeight = if (compactRow) 26.dp else 32.dp

    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .heightIn(max = maxBannerHeight)
            .zIndex(10f)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.overlayPanelSurface)
            .border(1.5.dp, accent, RoundedCornerShape(14.dp))
            .padding(
                horizontal = if (compactRow) 8.dp else 12.dp,
                vertical = if (compactRow) 5.dp else 8.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (compactRow) 4.dp else 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactRow) 8.dp else 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (compactRow) 34.dp else 42.dp)
                    .clip(CircleShape)
                    .background(accent)
            ) {
                Icon(
                    imageVector = turnIconVector(iconType),
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(if (compactRow) 20.dp else 26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = distanceText.ifBlank { "---" },
                        color = accent,
                        fontSize = if (compactRow) 18.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (streetText.isNotBlank()) {
                        Text(
                            text = streetText,
                            color = colors.textPrimary,
                            fontSize = if (compactRow) 14.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 1.dp)
                        )
                    }
                }
                Text(
                    text = instructionText,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactRow) 5.dp else 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = etaText,
                color = colors.textPrimary,
                fontSize = if (compactRow) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            NoTollsChip(
                avoidTolls = avoidTolls,
                accent = accent,
                compact = compactRow,
                iconOnly = iconOnlyTolls,
                onClick = onToggleAvoidTolls
            )

            // Touch-friendly TTS Mute / Unmute icon button next to "Termina"
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(actionHeight)
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
                    modifier = Modifier.size(if (compactRow) 18.dp else 20.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(actionHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFD32F2F))
                    .clickable(onClick = onTerminate)
                    .padding(horizontal = if (compactRow) 9.dp else 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_end),
                    color = Color.White,
                    fontSize = if (compactRow) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
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

@SuppressLint("MissingPermission")
private fun activateNativeLocationComponent(
    context: Context,
    map: MapLibreMap,
    style: Style,
    engine: NavigationEngine,
    mapHeightPx: Int,
    cameraTrackingListener: OnCameraTrackingChangedListener,
    isListenerAttached: Boolean,
    onListenerAttached: () -> Unit
) {
    if (!engine.hasLocationPermission() || !style.isFullyLoaded) return

    engine.startLocationTracking()

    val locationOptions = LocationComponentOptions.builder(context)
        .gpsDrawable(R.drawable.ic_nav_arrow)
        .foregroundDrawable(R.drawable.ic_nav_arrow)
        .foregroundTintColor(null)
        .foregroundStaleTintColor(null)
        .backgroundTintColor(AndroidColor.TRANSPARENT)
        .backgroundStaleTintColor(AndroidColor.TRANSPARENT)
        .elevation(0f)
        .accuracyAlpha(0f)
        .enableStaleState(false)
        .minZoomIconScale(0.85f)
        .maxZoomIconScale(1.15f)
        .build()

    val engineRequest = LocationEngineRequest.Builder(1000L)
        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
        .setFastestInterval(500L)
        .setDisplacement(0f)
        .build()

    val activationOptions = LocationComponentActivationOptions.builder(context, style)
        .locationEngine(engine.locationEngine)
        .locationEngineRequest(engineRequest)
        .locationComponentOptions(locationOptions)
        .useDefaultLocationEngine(false)
        .build()

    val lc = map.locationComponent
    lc.activateLocationComponent(activationOptions)
    lc.isLocationComponentEnabled = true
    lc.renderMode = RenderMode.GPS
    lc.cameraMode = if (engine.isFollowingGps) CameraMode.TRACKING_GPS else CameraMode.NONE

    if (!isListenerAttached) {
        lc.addOnCameraTrackingChangedListener(cameraTrackingListener)
        onListenerAttached()
    }

    // Re-apply driving padding after LocationComponent activation (which resets content padding)
    applyDrivingPadding(map, engine, mapHeightPx)
}

@SuppressLint("MissingPermission")
private fun detachNativeLocationComponent(
    map: MapLibreMap?,
    cameraTrackingListener: OnCameraTrackingChangedListener,
    isListenerAttached: Boolean
) {
    runCatching {
        val lc = map?.locationComponent ?: return
        if (lc.isLocationComponentActivated) {
            if (isListenerAttached) {
                lc.removeOnCameraTrackingChangedListener(cameraTrackingListener)
            }
            lc.isLocationComponentEnabled = false
        }
    }
}

private fun applyDrivingPadding(
    map: MapLibreMap,
    engine: NavigationEngine,
    mapHeightPx: Int
) {
    val topPad = if (engine.isFollowingGps && (engine.isMoving || engine.activeRoute != null) && mapHeightPx > 0) {
        mapHeightPx / 3
    } else {
        0
    }
    val currentPad = map.padding
    if (currentPad.size < 4 || currentPad[0] != 0 || currentPad[1] != topPad || currentPad[2] != 0 || currentPad[3] != 0) {
        map.setPadding(0, topPad, 0, 0)
    }
}

private fun recenterCamera(
    map: MapLibreMap,
    engine: NavigationEngine,
    mapHeightPx: Int
) {
    engine.isFollowingGps = true
    applyDrivingPadding(map, engine, mapHeightPx)

    val targetTilt = if (engine.isMoving) DRIVING_TILT else STATIONARY_TILT
    val lc = map.locationComponent
    val fallbackPos = CameraPosition.Builder()
        .target(LatLng(engine.currentLat, engine.currentLon))
        .zoom(DRIVING_ZOOM)
        .tilt(targetTilt)
        .bearing(if (engine.hasGpsFix) engine.currentBearing.toDouble() else 0.0)
        .build()

    if (lc.isLocationComponentActivated && lc.isLocationComponentEnabled) {
        if (lc.cameraMode != CameraMode.TRACKING_GPS) {
            if (lc.lastKnownLocation != null) {
                lc.setCameraMode(CameraMode.TRACKING_GPS, 600L, DRIVING_ZOOM, null, targetTilt, null)
            } else {
                map.easeCamera(
                    CameraUpdateFactory.newCameraPosition(fallbackPos),
                    600,
                    object : MapLibreMap.CancelableCallback {
                        override fun onCancel() {}
                        override fun onFinish() {
                            if (engine.isFollowingGps && lc.isLocationComponentActivated) {
                                lc.cameraMode = CameraMode.TRACKING_GPS
                            }
                        }
                    }
                )
            }
        } else {
            lc.zoomWhileTracking(DRIVING_ZOOM, 600L)
            lc.tiltWhileTracking(targetTilt, 600L)
        }
    } else {
        map.easeCamera(CameraUpdateFactory.newCameraPosition(fallbackPos), 600)
    }
}

private fun setupMapLayers(
    style: Style,
    accentHex: String,
    route: ValhallaRoute?,
    traveledPoints: List<LatLng>,
    remainingPoints: List<LatLng>,
    isLightTheme: Boolean
) {
    val safeColor = parseSafeColor(accentHex, isLightTheme)
    val routeCasingHex = if (isLightTheme) "#1E293B" else "#0A0D12"

    // Anchor route & destination layers below MapLibre's LocationComponent layers so the arrow stays on top
    val locationAnchor = listOf(
        "mapbox-location-pulsing-circle-layer",
        "mapbox-location-accuracy-layer",
        "mapbox-location-shadow-layer",
        "mapbox-location-background-layer",
        "mapbox-location-foreground-layer"
    ).firstOrNull { style.getLayer(it) != null }

    fun addLayerBelowLocation(layer: Layer) {
        if (locationAnchor != null) {
            style.addLayerBelow(layer, locationAnchor)
        } else {
            style.addLayer(layer)
        }
    }

    if (style.getSource(SOURCE_ROUTE_TRAVELED) == null) {
        style.addSource(GeoJsonSource(SOURCE_ROUTE_TRAVELED, emptyFeatureCollectionJson()))
    }
    if (style.getLayer(LAYER_ROUTE_TRAVELED_CASING) == null) {
        addLayerBelowLocation(
            LineLayer(LAYER_ROUTE_TRAVELED_CASING, SOURCE_ROUTE_TRAVELED).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor("#475569")),
                PropertyFactory.lineOpacity(0.25f),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LAYER_ROUTE_TRAVELED) == null) {
        addLayerBelowLocation(
            LineLayer(LAYER_ROUTE_TRAVELED, SOURCE_ROUTE_TRAVELED).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor("#64748B")),
                PropertyFactory.lineOpacity(0.42f),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    if (style.getSource(SOURCE_ROUTE) == null) {
        style.addSource(GeoJsonSource(SOURCE_ROUTE, emptyFeatureCollectionJson()))
    }
    if (style.getLayer(LAYER_ROUTE_CASING) == null) {
        addLayerBelowLocation(
            LineLayer(LAYER_ROUTE_CASING, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor(routeCasingHex)),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LAYER_ROUTE) == null) {
        addLayerBelowLocation(
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
        addLayerBelowLocation(
            CircleLayer(LAYER_DEST_DOT, SOURCE_DEST).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(safeColor)
            )
        )
    }

    updateRouteOnStyle(style, route, traveledPoints, remainingPoints, accentHex, isLightTheme)
}

private fun updateRouteOnStyle(
    style: Style,
    route: ValhallaRoute?,
    traveledPoints: List<LatLng>,
    remainingPoints: List<LatLng>,
    accentHex: String,
    isLightTheme: Boolean
) {
    val safeColor = parseSafeColor(accentHex, isLightTheme)
    val routeCasingHex = if (isLightTheme) "#1E293B" else "#0A0D12"
    (style.getLayer(LAYER_ROUTE_CASING) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(AndroidColor.parseColor(routeCasingHex))
    )
    (style.getLayer(LAYER_ROUTE) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(safeColor)
    )
    (style.getLayer(LAYER_DEST_DOT) as? CircleLayer)?.setProperties(
        PropertyFactory.circleStrokeColor(safeColor)
    )

    val traveledSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_TRAVELED)
    val routeSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE) ?: return
    val destSrc = style.getSourceAs<GeoJsonSource>(SOURCE_DEST) ?: return

    if (route == null || route.shapePoints.isEmpty()) {
        traveledSrc?.setGeoJson(emptyFeatureCollectionJson())
        routeSrc.setGeoJson(emptyFeatureCollectionJson())
        destSrc.setGeoJson(emptyFeatureCollectionJson())
    } else {
        if (traveledPoints.size >= 2) {
            traveledSrc?.setGeoJson(lineStringFeatureJson(traveledPoints))
        } else {
            traveledSrc?.setGeoJson(emptyFeatureCollectionJson())
        }
        val activeRemaining = if (remainingPoints.size >= 2) remainingPoints else route.shapePoints
        routeSrc.setGeoJson(lineStringFeatureJson(activeRemaining))
        destSrc.setGeoJson(pointFeatureJson(route.destination.lat, route.destination.lon))
    }
}

private fun parseSafeColor(hex: String, isLightTheme: Boolean = false): Int {
    val effectiveHex = if (isLightTheme && hex.equals("#2979FF", ignoreCase = true)) "#1D4ED8" else hex
    return runCatching { AndroidColor.parseColor(effectiveHex) }
        .getOrElse { AndroidColor.parseColor(if (isLightTheme) "#1D4ED8" else "#2979FF") }
}

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
