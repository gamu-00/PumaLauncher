package com.openlauncher.app.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.RectF
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
import org.maplibre.android.geometry.LatLngBounds
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
import kotlin.math.roundToInt

private const val NAV_LOG_TAG = "PumaNav"

private const val SOURCE_ROUTE_TRAVELED = "puma-route-traveled-source"
private const val LAYER_ROUTE_TRAVELED_CASING = "puma-route-traveled-casing-layer"
private const val LAYER_ROUTE_TRAVELED = "puma-route-traveled-layer"
private const val SOURCE_ROUTE_ALTS = "puma-route-alts-source"
private const val LAYER_ROUTE_ALTS_CASING = "puma-route-alts-casing-layer"
private const val LAYER_ROUTE_ALTS = "puma-route-alts-layer"
private const val SOURCE_ROUTE = "puma-route-source"
private const val LAYER_ROUTE_CASING = "puma-route-casing-layer"
private const val LAYER_ROUTE = "puma-route-layer"
private const val SOURCE_DEST = "puma-dest-source"
private const val LAYER_DEST_DOT = "puma-dest-dot-layer"
private const val DRIVING_ZOOM = 16.5
private const val DRIVING_TILT = 45.0
private const val STATIONARY_TILT = 0.0

private fun dynamicDrivingZoom(speedKmh: Int, hasActiveRoute: Boolean): Double = when {
    speedKmh >= 100 -> 14.8
    speedKmh >= 70 -> 15.5
    speedKmh >= 40 -> 16.1
    hasActiveRoute || speedKmh > 0 -> 16.6
    else -> DRIVING_ZOOM
}

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
    var previewCardHeightPx by remember { mutableIntStateOf(0) }

    val latestIsLight by rememberUpdatedState(isLightTheme)
    val latestAccentHex by rememberUpdatedState(accentColorHex)
    val latestActiveRoute by rememberUpdatedState(engine.activeRoute)
    val latestTraveledPoints by rememberUpdatedState(engine.traveledRoutePoints)
    val latestRemainingPoints by rememberUpdatedState(engine.remainingRoutePoints)
    val latestPreviewOptions by rememberUpdatedState(engine.previewOptions)
    val latestSelectedPreviewIndex by rememberUpdatedState(engine.selectedPreviewIndex)
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
                if (!engine.isRoutePreview) {
                    recenterCamera(map, engine, latestMapHeightPx)
                }
            } ?: run {
                if (!engine.isRoutePreview) {
                    engine.isFollowingGps = true
                }
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
            setBackgroundColor(
                AndroidColor.parseColor(
                    if (isLightTheme) AutomotiveMapStyle.DAY_BG_HEX else AutomotiveMapStyle.NIGHT_BG_HEX
                )
            )
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
    val isRoutePreview = engine.isRoutePreview
    val previewOptions = engine.previewOptions
    val selectedPreviewIndex = engine.selectedPreviewIndex

    // Stop GNSS tracking if an active route completes while app is backgrounded
    LaunchedEffect(activeRoute) {
        if (activeRoute == null && !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            engine.stopLocationTracking()
        }
    }

    // Synchronize MapLibre automotive vector style with active Day/Night theme while preserving camera & route
    LaunchedEffect(mapLibreMap, isLightTheme) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (appliedStyleIsLight == isLightTheme) return@LaunchedEffect

        val isThemeSwitch = appliedStyleIsLight != null
        val preservedCamera = if (isThemeSwitch) map.cameraPosition else null
        appliedStyleIsLight = isLightTheme
        styleReady = false
        mapView.setBackgroundColor(
            AndroidColor.parseColor(
                if (isLightTheme) AutomotiveMapStyle.DAY_BG_HEX else AutomotiveMapStyle.NIGHT_BG_HEX
            )
        )

        val styleJson = AutomotiveMapStyle.buildAutomotiveStyleJson(isLightTheme)
        map.setStyle(Style.Builder().fromJson(styleJson)) { loadedStyle ->
            if (mapDisposed.value || latestIsLight != isLightTheme) return@setStyle
            AutomotiveMapStyle.applyAutomotiveStyleToLoadedLayers(loadedStyle, isLightTheme)
            setupMapLayers(
                style = loadedStyle,
                accentHex = latestAccentHex,
                route = latestActiveRoute,
                traveledPoints = latestTraveledPoints,
                remainingPoints = latestRemainingPoints,
                previewOptions = latestPreviewOptions,
                selectedPreviewIndex = latestSelectedPreviewIndex,
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
            previewOptions = latestPreviewOptions,
            selectedPreviewIndex = latestSelectedPreviewIndex,
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

    // Smooth dynamic 3D driving perspective: 45.0° pitch + speed-based zoom in FOLLOW, 0.0° top-down in Free Look
    var lastAppliedDrivingZoom by remember { mutableDoubleStateOf(DRIVING_ZOOM) }
    LaunchedEffect(mapLibreMap, styleReady, engine.isFollowingGps, engine.isMoving, activeRoute != null, speedKmh) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        if (!engine.isFollowingGps) {
            // Free Look / route preview: return smoothly to 0.0° top-down view
            delay(150L)
            if (!engine.isFollowingGps && map.cameraPosition.tilt > 0.5) {
                map.easeCamera(CameraUpdateFactory.tiltTo(STATIONARY_TILT), 450)
            }
            return@LaunchedEffect
        }

        val shouldUse3dTilt = engine.isMoving || activeRoute != null
        if (!shouldUse3dTilt) {
            delay(1000L)
            if (!engine.isFollowingGps || engine.isMoving || engine.activeRoute != null) return@LaunchedEffect
        }

        val lc = map.locationComponent
        if (lc.isLocationComponentActivated &&
            lc.isLocationComponentEnabled &&
            lc.cameraMode == CameraMode.TRACKING_GPS
        ) {
            val targetTilt = if (shouldUse3dTilt) DRIVING_TILT else STATIONARY_TILT
            lc.tiltWhileTracking(targetTilt, 600L)

            val targetZoom = dynamicDrivingZoom(speedKmh, activeRoute != null)
            if (abs(targetZoom - lastAppliedDrivingZoom) >= 0.25) {
                lastAppliedDrivingZoom = targetZoom
                lc.zoomWhileTracking(targetZoom, 900L)
            }
        }
    }

    // Update route polyline (traveled vs remaining, or preview alternatives) and accent color on map
    LaunchedEffect(
        mapLibreMap,
        styleReady,
        activeRoute,
        traveledRoutePoints,
        remainingRoutePoints,
        previewOptions,
        selectedPreviewIndex,
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
            previewOptions = previewOptions,
            selectedPreviewIndex = selectedPreviewIndex,
            accentHex = accentColorHex,
            isLightTheme = isLightTheme
        )
    }

    // Auto-zoom camera to fit all preview routes with adequate padding above the preview card
    LaunchedEffect(
        mapLibreMap,
        styleReady,
        isRoutePreview,
        previewOptions,
        mapHeightPx,
        previewCardHeightPx
    ) {
        if (!isRoutePreview || previewOptions.isEmpty() || !styleReady) return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val lc = map.locationComponent
        if (lc.isLocationComponentActivated && lc.cameraMode != CameraMode.NONE) {
            lc.cameraMode = CameraMode.NONE
        }
        engine.isFollowingGps = false
        map.setPadding(0, 0, 0, 0)

        val boundsBuilder = LatLngBounds.Builder()
        var pointCount = 0
        boundsBuilder.include(LatLng(engine.currentLat, engine.currentLon))
        pointCount++
        previewOptions.firstOrNull()?.route?.destination?.let { dest ->
            boundsBuilder.include(LatLng(dest.lat, dest.lon))
            pointCount++
        }
        previewOptions.forEach { opt ->
            val pts = opt.route.shapePoints
            val step = (pts.size / 120).coerceAtLeast(1)
            for (i in pts.indices step step) {
                boundsBuilder.include(pts[i])
                pointCount++
            }
            pts.lastOrNull()?.let { boundsBuilder.include(it) }
        }
        if (pointCount >= 2) {
            val density = context.resources.displayMetrics.density
            val sidePad = (48f * density).toInt()
            val topPad = (36f * density).toInt()
            val rawBottomPad = if (previewCardHeightPx > 0) {
                previewCardHeightPx + (18f * density).toInt()
            } else {
                (190f * density).toInt()
            }
            val maxAllowedBottom = if (mapHeightPx > 120) (mapHeightPx - topPad - 80).coerceAtLeast(topPad) else rawBottomPad
            val bottomPad = rawBottomPad.coerceAtMost(maxAllowedBottom)
            runCatching {
                val bounds = boundsBuilder.build()
                map.easeCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, sidePad, topPad, sidePad, bottomPad),
                    650
                )
            }
        }
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

                    val initZoom = dynamicDrivingZoom(engine.currentSpeedKmh.value, engine.activeRoute != null)
                    val initTilt = if (engine.isFollowingGps && (engine.isMoving || engine.activeRoute != null)) {
                        DRIVING_TILT
                    } else {
                        STATIONARY_TILT
                    }
                    val initPos = CameraPosition.Builder()
                        .target(LatLng(engine.currentLat, engine.currentLon))
                        .zoom(initZoom)
                        .tilt(initTilt)
                        .bearing(if (engine.hasGpsFix && engine.isFollowingGps) engine.currentBearing.toDouble() else 0.0)
                        .build()
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(initPos))

                    // Fallback listener if remote style fails (e.g. offline)
                    mapView.addOnDidFailLoadingMapListener { errorMsg ->
                        if (mapDisposed.value || latestStyleReady) return@addOnDidFailLoadingMapListener
                        val light = latestIsLight
                        val fallbackJson = AutomotiveMapStyle.buildFallbackStyleJson(light)
                        Log.d(NAV_LOG_TAG, "MapLibre style load failed ($errorMsg), attivazione fallback ${if (light) "chiaro" else "scuro"}")
                        map.setStyle(Style.Builder().fromJson(fallbackJson)) { fallbackStyle ->
                            if (mapDisposed.value || latestIsLight != light) return@setStyle
                            setupMapLayers(
                                style = fallbackStyle,
                                accentHex = latestAccentHex,
                                route = latestActiveRoute,
                                traveledPoints = latestTraveledPoints,
                                remainingPoints = latestRemainingPoints,
                                previewOptions = latestPreviewOptions,
                                selectedPreviewIndex = latestSelectedPreviewIndex,
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

                    map.addOnMapClickListener { latLng ->
                        if (!engine.isRoutePreview || engine.previewOptions.size <= 1) {
                            return@addOnMapClickListener false
                        }
                        val screenPt = map.projection.toScreenLocation(latLng)
                        val hitSlopPx = 28f * context.resources.displayMetrics.density
                        val hitRect = RectF(
                            screenPt.x - hitSlopPx,
                            screenPt.y - hitSlopPx,
                            screenPt.x + hitSlopPx,
                            screenPt.y + hitSlopPx
                        )
                        val features = map.queryRenderedFeatures(hitRect, LAYER_ROUTE_ALTS, LAYER_ROUTE_ALTS_CASING)
                        val tappedIdx = features.firstOrNull()?.let { feature ->
                            if (feature.hasProperty("optionIndex")) {
                                feature.getNumberProperty("optionIndex")?.toInt()
                            } else {
                                null
                            }
                        }
                        if (tappedIdx != null && tappedIdx in engine.previewOptions.indices) {
                            engine.selectPreviewOption(tappedIdx)
                            true
                        } else {
                            false
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
                    } else if (!isRoutePreview) {
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
                                                            Log.d(NAV_LOG_TAG, "Destinazione selezionata per anteprima: \"${place.displayName}\" (${place.lat}, ${place.lon})")
                                                            searchQuery = ""
                                                            searchFocused = false
                                                            engine.clearSearchResults()
                                                            focusManager.clearFocus()
                                                            keyboardController?.hide()
                                                            engine.loadRoutePreview(place)
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

                // 3. Digital GPS Speedometer Badge in bottom-left corner (hidden during Route Preview)
                if (!isRoutePreview) {
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
                }

                // 4. Bottom-right controls: Compass + "Ricentra" (hidden during Route Preview)
                val showCompass = !isRoutePreview && (abs(cameraBearing) > 1f || !engine.isFollowingGps)
                val showRecenter = !isRoutePreview && !engine.isFollowingGps
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

                // 5. Route Preview Card (BottomCenter, optimized for 8" landscape split-screen)
                if (isRoutePreview) {
                    RoutePreviewCard(
                        destination = engine.previewDestination,
                        options = previewOptions,
                        selectedIndex = selectedPreviewIndex,
                        isRouting = engine.isRouting,
                        routeError = engine.routeError,
                        accent = accent,
                        onAccent = onAccent,
                        compact = tightOverlay,
                        availableWidth = (mapPanelWidth - overlayPad * 2).coerceAtLeast(240.dp),
                        onSelectOption = { idx -> engine.selectPreviewOption(idx) },
                        onStartNavigation = {
                            engine.startPreviewRoute()
                            mapLibreMap?.let { map ->
                                recenterCamera(map, engine, latestMapHeightPx)
                            }
                        },
                        onCancel = {
                            previewCardHeightPx = 0
                            engine.cancelRoutePreview()
                            mapLibreMap?.let { map ->
                                recenterCamera(map, engine, latestMapHeightPx)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .padding(overlayPad)
                            .onSizeChanged { previewCardHeightPx = it.height }
                            .zIndex(12f)
                    )
                } else if (previewCardHeightPx != 0) {
                    LaunchedEffect(Unit) { previewCardHeightPx = 0 }
                }
            }
        }
    }
}

@Composable
private fun RoutePreviewCard(
    destination: PhotonPlace?,
    options: List<RoutePreviewOption>,
    selectedIndex: Int,
    isRouting: Boolean,
    routeError: String?,
    accent: Color,
    onAccent: Color,
    compact: Boolean,
    availableWidth: Dp = 440.dp,
    onSelectOption: (Int) -> Unit,
    onStartNavigation: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    val cardShape = RoundedCornerShape(16.dp)
    val fastestTimeSec = options.firstOrNull()?.route?.totalTimeSec ?: 0.0
    val tightSplit = compact || availableWidth < 430.dp
    val ultraTightSplit = availableWidth < 365.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(if (tightSplit) 5.dp else 7.dp),
        modifier = modifier
            .clip(cardShape)
            .background(colors.glassPillSurface)
            .border(1.dp, colors.hairlineBorder, cardShape)
            .padding(
                horizontal = if (tightSplit) 8.dp else 12.dp,
                vertical = if (tightSplit) 7.dp else 9.dp
            )
    ) {
        // Header: Destination + Close (X)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = destination?.name ?: stringResource(R.string.nav_default_destination),
                color = colors.textPrimary,
                fontSize = if (tightSplit) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.textSecondary.copy(alpha = 0.14f))
                    .clickable(onClick = onCancel)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.nav_preview_cancel),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        when {
            isRouting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.nav_preview_calculating),
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            routeError != null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = colors.dangerContent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = routeError,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            options.isNotEmpty() -> {
                // 3 selectable route tabs sized to fit inside the split-screen map panel without truncation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (tightSplit) 5.dp else 7.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    options.forEachIndexed { idx, option ->
                        val isSelected = idx == selectedIndex
                        val optionShape = RoundedCornerShape(11.dp)
                        val title = when (option.type) {
                            RouteOptionType.FASTEST -> stringResource(R.string.nav_preview_fastest)
                            RouteOptionType.ALTERNATIVE -> stringResource(R.string.nav_preview_alternative)
                            RouteOptionType.SCENIC -> stringResource(R.string.nav_preview_scenic)
                        }
                        val icon = when (option.type) {
                            RouteOptionType.FASTEST -> Icons.Default.Bolt
                            RouteOptionType.ALTERNATIVE -> Icons.Default.AltRoute
                            RouteOptionType.SCENIC -> Icons.Default.Terrain
                        }
                        val primaryTimeLabel = if (option.type == RouteOptionType.ALTERNATIVE && fastestTimeSec > 0.0) {
                            formatPreviewTimeDiff(option.route.totalTimeSec, fastestTimeSec)
                        } else {
                            formatPreviewDuration(option.route.totalTimeSec)
                        }
                        val distanceLabel = formatPreviewDistance(option.route.totalLengthKm)
                        val borderColor = if (isSelected) accent else colors.hairlineBorder
                        val borderWidth = if (isSelected) 1.75.dp else 1.dp
                        val bg = if (isSelected) accent.copy(alpha = 0.16f) else colors.cardSurface.copy(alpha = 0.55f)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(optionShape)
                                .background(bg)
                                .border(borderWidth, borderColor, optionShape)
                                .clickable { onSelectOption(idx) }
                                .padding(
                                    horizontal = if (tightSplit) 6.dp else 8.dp,
                                    vertical = if (tightSplit) 5.dp else 6.dp
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) accent else colors.textSecondary,
                                    modifier = Modifier.size(if (tightSplit) 12.dp else 13.dp)
                                )
                                Text(
                                    text = title,
                                    color = if (isSelected) accent else colors.textSecondary,
                                    fontSize = if (ultraTightSplit) 9.5.sp else if (tightSplit) 10.sp else 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = primaryTimeLabel,
                                color = colors.textPrimary,
                                fontSize = if (tightSplit) 13.sp else 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = distanceLabel,
                                    color = colors.textSecondary,
                                    fontSize = if (tightSplit) 10.sp else 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (option.hasTolls) {
                                    Text(
                                        text = if (ultraTightSplit) "€" else stringResource(R.string.nav_preview_toll),
                                        color = colors.textSecondary,
                                        fontSize = if (tightSplit) 8.5.sp else 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.textSecondary.copy(alpha = 0.14f))
                                            .padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Row: Cancel + Big START NAVIGATION button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (tightSplit) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val btnHeight = if (tightSplit) 36.dp else 40.dp
                    val btnShape = RoundedCornerShape(11.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(btnHeight)
                            .clip(btnShape)
                            .background(colors.cardSurface.copy(alpha = 0.7f))
                            .border(1.dp, colors.hairlineBorder, btnShape)
                            .clickable(onClick = onCancel)
                            .padding(horizontal = if (tightSplit) 10.dp else 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nav_preview_cancel),
                            color = colors.textPrimary,
                            fontSize = if (tightSplit) 12.sp else 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(btnHeight)
                            .clip(btnShape)
                            .background(accent)
                            .clickable(onClick = onStartNavigation)
                            .padding(horizontal = if (tightSplit) 10.dp else 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = onAccent,
                            modifier = Modifier.size(if (tightSplit) 16.dp else 18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.nav_preview_start_cta),
                            color = onAccent,
                            fontSize = if (tightSplit) 12.sp else 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun formatPreviewDuration(totalSeconds: Double): String {
    val totalMin = (totalSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "${hours}h ${mins}m" else "$mins min"
}

private fun formatPreviewTimeDiff(optionSeconds: Double, fastestSeconds: Double): String {
    val diffMin = ((optionSeconds - fastestSeconds) / 60.0).roundToInt()
    return when {
        diffMin > 0 -> "+$diffMin min"
        diffMin < 0 -> "$diffMin min"
        else -> formatPreviewDuration(optionSeconds)
    }
}

private fun formatPreviewDistance(km: Double): String =
    if (km >= 100.0) "${km.roundToInt()} km" else String.format(Locale.US, "%.1f km", km)

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

    val targetZoom = dynamicDrivingZoom(engine.currentSpeedKmh.value, engine.activeRoute != null)
    val targetTilt = if (engine.isMoving || engine.activeRoute != null) DRIVING_TILT else STATIONARY_TILT
    val lc = map.locationComponent
    val fallbackPos = CameraPosition.Builder()
        .target(LatLng(engine.currentLat, engine.currentLon))
        .zoom(targetZoom)
        .tilt(targetTilt)
        .bearing(if (engine.hasGpsFix) engine.currentBearing.toDouble() else 0.0)
        .build()

    if (lc.isLocationComponentActivated && lc.isLocationComponentEnabled) {
        if (lc.cameraMode != CameraMode.TRACKING_GPS) {
            if (lc.lastKnownLocation != null) {
                lc.setCameraMode(CameraMode.TRACKING_GPS, 600L, targetZoom, null, targetTilt, null)
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
            lc.zoomWhileTracking(targetZoom, 600L)
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
    previewOptions: List<RoutePreviewOption> = emptyList(),
    selectedPreviewIndex: Int = 0,
    isLightTheme: Boolean
) {
    val ribbonCoreColor = AndroidColor.parseColor(AutomotiveMapStyle.ROUTE_RIBBON_CORE_HEX)
    val ribbonCasingHex = if (isLightTheme) {
        AutomotiveMapStyle.ROUTE_RIBBON_CASING_DAY_HEX
    } else {
        AutomotiveMapStyle.ROUTE_RIBBON_CASING_NIGHT_HEX
    }
    val altCoreHex = if (isLightTheme) {
        AutomotiveMapStyle.ROUTE_ALT_CORE_DAY_HEX
    } else {
        AutomotiveMapStyle.ROUTE_ALT_CORE_NIGHT_HEX
    }
    val altCasingHex = if (isLightTheme) {
        AutomotiveMapStyle.ROUTE_ALT_CASING_DAY_HEX
    } else {
        AutomotiveMapStyle.ROUTE_ALT_CASING_NIGHT_HEX
    }

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
                PropertyFactory.lineColor(AndroidColor.parseColor(AutomotiveMapStyle.ROUTE_TRAVELED_CASING_HEX)),
                PropertyFactory.lineOpacity(0.32f),
                PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCasingWidthExpression()),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LAYER_ROUTE_TRAVELED) == null) {
        addLayerBelowLocation(
            LineLayer(LAYER_ROUTE_TRAVELED, SOURCE_ROUTE_TRAVELED).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor(AutomotiveMapStyle.ROUTE_TRAVELED_CORE_HEX)),
                PropertyFactory.lineOpacity(0.45f),
                PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCoreWidthExpression()),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    if (style.getSource(SOURCE_ROUTE_ALTS) == null) {
        style.addSource(GeoJsonSource(SOURCE_ROUTE_ALTS, emptyFeatureCollectionJson()))
    }
    if (style.getLayer(LAYER_ROUTE_ALTS_CASING) == null) {
        addLayerBelowLocation(
            LineLayer(LAYER_ROUTE_ALTS_CASING, SOURCE_ROUTE_ALTS).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor(altCasingHex)),
                PropertyFactory.lineOpacity(0.40f),
                PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCasingWidthExpression()),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LAYER_ROUTE_ALTS) == null) {
        addLayerBelowLocation(
            LineLayer(LAYER_ROUTE_ALTS, SOURCE_ROUTE_ALTS).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor(altCoreHex)),
                PropertyFactory.lineOpacity(0.65f),
                PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCoreWidthExpression()),
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
                PropertyFactory.lineColor(AndroidColor.parseColor(ribbonCasingHex)),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCasingWidthExpression()),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LAYER_ROUTE) == null) {
        addLayerBelowLocation(
            LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(ribbonCoreColor),
                PropertyFactory.lineOpacity(1.0f),
                PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCoreWidthExpression()),
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
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(3.5f),
                PropertyFactory.circleStrokeColor(ribbonCoreColor)
            )
        )
    }

    updateRouteOnStyle(
        style = style,
        route = route,
        traveledPoints = traveledPoints,
        remainingPoints = remainingPoints,
        previewOptions = previewOptions,
        selectedPreviewIndex = selectedPreviewIndex,
        accentHex = accentHex,
        isLightTheme = isLightTheme
    )
}

private fun updateRouteOnStyle(
    style: Style,
    route: ValhallaRoute?,
    traveledPoints: List<LatLng>,
    remainingPoints: List<LatLng>,
    previewOptions: List<RoutePreviewOption> = emptyList(),
    selectedPreviewIndex: Int = 0,
    @Suppress("UNUSED_PARAMETER") accentHex: String,
    isLightTheme: Boolean
) {
    val ribbonCoreColor = AndroidColor.parseColor(AutomotiveMapStyle.ROUTE_RIBBON_CORE_HEX)
    val ribbonCasingHex = if (isLightTheme) {
        AutomotiveMapStyle.ROUTE_RIBBON_CASING_DAY_HEX
    } else {
        AutomotiveMapStyle.ROUTE_RIBBON_CASING_NIGHT_HEX
    }
    val altCoreHex = if (isLightTheme) {
        AutomotiveMapStyle.ROUTE_ALT_CORE_DAY_HEX
    } else {
        AutomotiveMapStyle.ROUTE_ALT_CORE_NIGHT_HEX
    }
    val altCasingHex = if (isLightTheme) {
        AutomotiveMapStyle.ROUTE_ALT_CASING_DAY_HEX
    } else {
        AutomotiveMapStyle.ROUTE_ALT_CASING_NIGHT_HEX
    }
    (style.getLayer(LAYER_ROUTE_ALTS_CASING) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(AndroidColor.parseColor(altCasingHex)),
        PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCasingWidthExpression())
    )
    (style.getLayer(LAYER_ROUTE_ALTS) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(AndroidColor.parseColor(altCoreHex)),
        PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCoreWidthExpression())
    )
    (style.getLayer(LAYER_ROUTE_CASING) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(AndroidColor.parseColor(ribbonCasingHex)),
        PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCasingWidthExpression())
    )
    (style.getLayer(LAYER_ROUTE) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(ribbonCoreColor),
        PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCoreWidthExpression())
    )
    (style.getLayer(LAYER_DEST_DOT) as? CircleLayer)?.setProperties(
        PropertyFactory.circleStrokeColor(ribbonCoreColor)
    )

    val traveledSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_TRAVELED)
    val altsSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_ALTS)
    val routeSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE) ?: return
    val destSrc = style.getSourceAs<GeoJsonSource>(SOURCE_DEST) ?: return

    if (route != null && route.shapePoints.isNotEmpty()) {
        altsSrc?.setGeoJson(emptyFeatureCollectionJson())
        if (traveledPoints.size >= 2) {
            traveledSrc?.setGeoJson(lineStringFeatureJson(traveledPoints))
        } else {
            traveledSrc?.setGeoJson(emptyFeatureCollectionJson())
        }
        val activeRemaining = if (remainingPoints.size >= 2) remainingPoints else route.shapePoints
        routeSrc.setGeoJson(lineStringFeatureJson(activeRemaining))
        destSrc.setGeoJson(pointFeatureJson(route.destination.lat, route.destination.lon))
    } else if (previewOptions.isNotEmpty()) {
        traveledSrc?.setGeoJson(emptyFeatureCollectionJson())
        val selected = previewOptions.getOrNull(selectedPreviewIndex) ?: previewOptions.first()
        val unselected = previewOptions.mapIndexedNotNull { idx, opt ->
            if (idx != selectedPreviewIndex && opt.route.shapePoints.size >= 2) idx to opt.route.shapePoints else null
        }
        altsSrc?.setGeoJson(
            if (unselected.isNotEmpty()) featureCollectionFromIndexedLines(unselected) else emptyFeatureCollectionJson()
        )
        routeSrc.setGeoJson(lineStringFeatureJson(selected.route.shapePoints))
        destSrc.setGeoJson(pointFeatureJson(selected.route.destination.lat, selected.route.destination.lon))
    } else {
        traveledSrc?.setGeoJson(emptyFeatureCollectionJson())
        altsSrc?.setGeoJson(emptyFeatureCollectionJson())
        routeSrc.setGeoJson(emptyFeatureCollectionJson())
        destSrc.setGeoJson(emptyFeatureCollectionJson())
    }
}

private fun featureCollectionFromIndexedLines(lines: List<Pair<Int, List<LatLng>>>): String {
    val features = JSONArray()
    for ((idx, points) in lines) {
        val coords = JSONArray()
        for (pt in points) {
            coords.put(JSONArray().put(pt.longitude).put(pt.latitude))
        }
        features.put(
            JSONObject().apply {
                put("type", "Feature")
                put(
                    "geometry",
                    JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", coords)
                    }
                )
                put("properties", JSONObject().put("optionIndex", idx))
            }
        )
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
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
