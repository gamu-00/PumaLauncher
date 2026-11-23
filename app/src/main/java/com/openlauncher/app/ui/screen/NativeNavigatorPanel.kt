package com.openlauncher.app.ui.screen

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openlauncher.app.R
import com.openlauncher.app.data.DEFAULT_UI_SCALE
import com.openlauncher.app.data.DrivingAvatar
import com.openlauncher.app.ui.theme.*
import com.openlauncher.app.util.AppLanguageManager
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
@Composable
fun NativeNavigatorPanel(
    accent: Color,
    accentColorHex: String,
    avoidTolls: Boolean = false,
    voiceGuidanceEnabled: Boolean = true,
    nav3dViewEnabled: Boolean = true,
    drivingAvatar: DrivingAvatar = DrivingAvatar.CLASSIC_ARROW,
    homeAddressLabel: String? = null,
    homeLat: Double? = null,
    homeLon: Double? = null,
    onSaveHomeAddress: (String, Double, Double) -> Unit = { _, _, _ -> },
    onClearHomeAddress: () -> Unit = {},
    onToggleAvoidTolls: (Boolean) -> Unit = {},
    onToggleVoiceGuidance: (Boolean) -> Unit = {},
    onToggleNav3dView: (Boolean) -> Unit = {},
    onSelectDrivingAvatar: (DrivingAvatar) -> Unit = {},
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
    LaunchedEffect(engine, avoidTolls, voiceGuidanceEnabled) {
        engine.updateAvoidTolls(avoidTolls, recalculateIfActive = false)
        engine.setVoiceGuidance(voiceGuidanceEnabled)
    }

    val speedKmhState = engine.currentSpeedKmh.collectAsState()
    val speedKmh = speedKmhState.value
    val colors = LocalCockpitColors.current
    val isLightTheme = colors.isLight

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var appliedStyleIsLight by remember { mutableStateOf<Boolean?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var isSettingHome by remember { mutableStateOf(false) }
    var isAddingWaypoint by remember { mutableStateOf(false) }
    var showValhallaMenu by remember { mutableStateOf(false) }
    var tripSheetExpanded by remember { mutableStateOf(false) }
    var focusSearchTrigger by remember { mutableIntStateOf(0) }
    var homeFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var trackingListenerAttached by remember { mutableStateOf(false) }
    var cameraBearing by remember { mutableFloatStateOf(0f) }
    var mapHeightPx by remember { mutableIntStateOf(0) }
    var previewCardHeightPx by remember { mutableIntStateOf(0) }

    val hasHomeAddress = remember(homeLat, homeLon, showSearchSheet, homeFeedbackMessage) {
        (homeLat != null && homeLon != null) ||
            NavSearchPrefsStore.loadHomePlace(context, homeAddressLabel, homeLat, homeLon) != null
    }

    LaunchedEffect(homeFeedbackMessage) {
        if (homeFeedbackMessage != null) {
            delay(2500L)
            homeFeedbackMessage = null
        }
    }

    val latestIsLight by rememberUpdatedState(isLightTheme)
    val latestAccentHex by rememberUpdatedState(accentColorHex)
    val latestActiveRoute by rememberUpdatedState(engine.activeRoute)
    val latestTraveledPoints by rememberUpdatedState(engine.traveledRoutePoints)
    val latestRemainingPoints by rememberUpdatedState(engine.remainingRoutePoints)
    val latestPreviewOptions by rememberUpdatedState(engine.previewOptions)
    val latestSelectedPreviewIndex by rememberUpdatedState(engine.selectedPreviewIndex)
    val latestWaypoints by rememberUpdatedState(engine.waypoints)
    val latestStyleReady by rememberUpdatedState(styleReady)
    val latestMapHeightPx by rememberUpdatedState(mapHeightPx)
    val latestAvatarDrawableRes by rememberUpdatedState(drivingAvatar.drawableResId())
    val latestNav3dViewEnabled by rememberUpdatedState(nav3dViewEnabled)

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

    val hasOpenSearchOverlay = showSearchSheet || searchFocused || searchQuery.isNotEmpty() ||
        engine.isSearching || engine.searchResults.isNotEmpty() || engine.searchStatusMessage != null
    LaunchedEffect(hasOpenSearchOverlay) {
        onSearchOverlayChanged(hasOpenSearchOverlay)
    }

    LaunchedEffect(homePressTrigger) {
        if (homePressTrigger > 0) {
            searchQuery = ""
            searchFocused = false
            showSearchSheet = false
            isSettingHome = false
            isAddingWaypoint = false
            showValhallaMenu = false
            tripSheetExpanded = false
            engine.clearSearchResults()
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            mapLibreMap?.let { map ->
                if (!engine.isRoutePreview) {
                    recenterCamera(map, engine, latestMapHeightPx, use3dTilt = latestNav3dViewEnabled)
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
            showSearchSheet = false
            isSettingHome = false
            isAddingWaypoint = false
            showValhallaMenu = false
            engine.clearSearchResults()
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(mapLibreMap, styleReady, nav3dViewEnabled) {
        val style = mapLibreMap?.style ?: return@LaunchedEffect
        if (!style.isFullyLoaded) return@LaunchedEffect
        runCatching {
            style.getLayer("building-3d")?.setProperties(
                PropertyFactory.visibility(if (nav3dViewEnabled) Property.VISIBLE else Property.NONE)
            )
        }
    }

    LaunchedEffect(mapLibreMap, styleReady, drivingAvatar) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        updateLocationComponentAvatar(context, map, drivingAvatar.drawableResId())
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
    val waypoints = engine.waypoints

    // Stop GNSS tracking if an active route completes while app is backgrounded
    LaunchedEffect(activeRoute, isRoutePreview) {
        if (activeRoute == null || isRoutePreview) {
            tripSheetExpanded = false
        }
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
                waypoints = latestWaypoints,
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
                onListenerAttached = { trackingListenerAttached = true },
                avatarDrawableRes = latestAvatarDrawableRes
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
                onListenerAttached = { trackingListenerAttached = true },
                avatarDrawableRes = latestAvatarDrawableRes
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
                    onListenerAttached = { trackingListenerAttached = true },
                    avatarDrawableRes = latestAvatarDrawableRes
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
            waypoints = latestWaypoints,
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

    // Smooth dynamic 3D driving perspective: 45.0° pitch (when nav3dViewEnabled) + speed-based zoom in FOLLOW, 0.0° top-down in Free Look or 2D mode
    var lastAppliedDrivingZoom by remember { mutableDoubleStateOf(DRIVING_ZOOM) }
    LaunchedEffect(mapLibreMap, styleReady, engine.isFollowingGps, engine.isMoving, activeRoute != null, speedKmh, nav3dViewEnabled) {
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

        val shouldUse3dTilt = nav3dViewEnabled && (engine.isMoving || activeRoute != null)
        if (!shouldUse3dTilt && nav3dViewEnabled) {
            delay(1000L)
            if (!engine.isFollowingGps || engine.isMoving || engine.activeRoute != null) return@LaunchedEffect
        }

        val lc = map.locationComponent
        if (lc.isLocationComponentActivated &&
            lc.isLocationComponentEnabled &&
            lc.cameraMode == CameraMode.TRACKING_GPS
        ) {
            val targetTilt = if (shouldUse3dTilt) DRIVING_TILT else STATIONARY_TILT
            lc.tiltWhileTracking(targetTilt, if (nav3dViewEnabled) 600L else 450L)

            val targetZoom = dynamicDrivingZoom(speedKmh, activeRoute != null)
            if (abs(targetZoom - lastAppliedDrivingZoom) >= 0.25) {
                lastAppliedDrivingZoom = targetZoom
                lc.zoomWhileTracking(targetZoom, 900L)
            }
        } else if (!nav3dViewEnabled && map.cameraPosition.tilt > 0.5) {
            map.easeCamera(CameraUpdateFactory.tiltTo(STATIONARY_TILT), 450)
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
        waypoints,
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
            waypoints = waypoints,
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
        waypoints.forEach { wp ->
            boundsBuilder.include(LatLng(wp.lat, wp.lon))
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
                    val initTilt = if (latestNav3dViewEnabled && engine.isFollowingGps && (engine.isMoving || engine.activeRoute != null)) {
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
                                waypoints = latestWaypoints,
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
                                onListenerAttached = { trackingListenerAttached = true },
                                avatarDrawableRes = latestAvatarDrawableRes
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

        // 1b. Viewport-space 2.5D Multi-Layer Vehicle Avatar Overlay (synchronized per frame via OnDidRenderFrameListener)
        VehicleAvatarOverlay(
            mapView = mapView,
            mapLibreMap = mapLibreMap,
            styleReady = styleReady,
            engine = engine,
            drivingAvatar = drivingAvatar,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
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
                val mapPanelWidth = maxWidth
                val mapPanelHeight = maxHeight
                val isNarrowPanel = mapPanelWidth < 460.dp
                val tightOverlay = isCompactHeight || mapPanelHeight < 420.dp || isNarrowPanel
                val overlayPad = if (tightOverlay) 8.dp else 10.dp
                val isActiveNavigation = activeRoute != null && !isRoutePreview

                if (showValhallaMenu && (!isActiveNavigation || isAddingWaypoint)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(9f)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                showValhallaMenu = false
                            }
                    )
                }

                // 2. Top overlay (zIndex = 10f): Turn-by-Turn Banner OR Google Maps Search Pill + Options Menu
                if (isActiveNavigation && !isAddingWaypoint) {
                    TurnByTurnBanner(
                        iconType = engine.nextTurnIcon,
                        roundaboutExitAngle = engine.nextTurnRoundaboutExitAngle,
                        distanceText = engine.nextTurnDistanceText,
                        streetText = engine.nextTurnStreet,
                        instructionText = engine.nextTurnInstruction,
                        roadBadges = engine.nextTurnRoadBadges,
                        isCompactHeight = tightOverlay,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                } else if (!isRoutePreview || isAddingWaypoint || homeFeedbackMessage != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(overlayPad)
                            .zIndex(10f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (tightOverlay) 4.dp else 6.dp)
                    ) {
                        if (!isRoutePreview || isAddingWaypoint) {
                            NavigationSearchOverlay(
                                searchQueryProvider = { searchQuery },
                                onSearchQueryChange = { searchQuery = it },
                                searchFocused = searchFocused,
                                onSearchFocusedChange = { searchFocused = it },
                                isSearching = engine.isSearching,
                                isRouting = engine.isRouting,
                                searchResults = engine.searchResults,
                                searchStatusMessage = engine.searchStatusMessage,
                                avoidTolls = engine.avoidTolls,
                                avoidHighways = engine.avoidHighways,
                                preferScenicRoute = engine.preferScenicRoute,
                                avoidFerries = engine.avoidFerries,
                                showOptionsMenu = showValhallaMenu,
                                onToggleOptionsMenu = { showValhallaMenu = !showValhallaMenu },
                                onDismissOptionsMenu = { showValhallaMenu = false },
                                tightOverlay = tightOverlay,
                                hasHomeAddress = hasHomeAddress,
                                isSettingHome = isSettingHome,
                                hasActiveOrPreviewDestination = isRoutePreview || activeRoute != null,
                                focusSearchTrigger = focusSearchTrigger,
                                onOpenSearchSheet = {
                                    showValhallaMenu = false
                                    showSearchSheet = true
                                    searchFocused = true
                                },
                                onHomeClick = {
                                    showValhallaMenu = false
                                    val savedHome = NavSearchPrefsStore.loadHomePlace(
                                        context,
                                        homeAddressLabel,
                                        homeLat,
                                        homeLon
                                    )
                                    if (savedHome != null) {
                                        searchQuery = ""
                                        searchFocused = false
                                        showSearchSheet = false
                                        isAddingWaypoint = false
                                        engine.clearSearchResults()
                                        val homePlace = savedHome.copy(
                                            name = AppLanguageManager.localizedString(context, R.string.nav_home),
                                            subtitle = savedHome.displayName
                                        )
                                        if (activeRoute != null) {
                                            engine.calculateRoute(homePlace)
                                        } else {
                                            engine.loadRoutePreview(homePlace)
                                        }
                                    } else {
                                        isSettingHome = true
                                        showSearchSheet = true
                                        searchFocused = true
                                    }
                                },
                                onHomeLongClick = {
                                    showValhallaMenu = false
                                    NavSearchPrefsStore.clearHomePlace(context)
                                    onClearHomeAddress()
                                    homeFeedbackMessage = AppLanguageManager.localizedString(context, R.string.nav_home_removed)
                                },
                                onCancelSetHome = {
                                    isSettingHome = false
                                    searchQuery = ""
                                    searchFocused = false
                                    showSearchSheet = false
                                    engine.clearSearchResults()
                                },
                                onImmediateSearch = { query -> engine.triggerImmediateSearch(query) },
                                onClearSearch = {
                                    searchQuery = ""
                                    isAddingWaypoint = false
                                    showSearchSheet = false
                                    engine.clearSearchResults()
                                },
                                onToggleAvoidTolls = {
                                    val nextVal = !engine.avoidTolls
                                    onToggleAvoidTolls(nextVal)
                                    engine.updateAvoidTolls(nextVal, recalculateIfActive = true)
                                },
                                onToggleAvoidHighways = {
                                    engine.updateAvoidHighways(!engine.avoidHighways, recalculateIfActive = true)
                                },
                                onTogglePreferScenic = {
                                    engine.updatePreferScenicRoute(!engine.preferScenicRoute, recalculateIfActive = true)
                                },
                                onToggleAvoidFerries = {
                                    engine.updateAvoidFerries(!engine.avoidFerries, recalculateIfActive = true)
                                },
                                onSelectPlace = { place ->
                                    showValhallaMenu = false
                                    searchQuery = ""
                                    searchFocused = false
                                    showSearchSheet = false
                                    engine.clearSearchResults()
                                    NavSearchPrefsStore.addRecentPlace(context, place)
                                    if (isSettingHome) {
                                        isSettingHome = false
                                        NavSearchPrefsStore.saveHomePlace(context, place)
                                        onSaveHomeAddress(place.displayName, place.lat, place.lon)
                                        homeFeedbackMessage = AppLanguageManager.localizedString(context, R.string.nav_home_saved_format, place.name)
                                    } else if (isAddingWaypoint && (activeRoute != null || isRoutePreview)) {
                                        isAddingWaypoint = false
                                        engine.addWaypoint(place)
                                    } else if (activeRoute != null) {
                                        isAddingWaypoint = false
                                        engine.calculateRoute(place)
                                    } else {
                                        isAddingWaypoint = false
                                        engine.loadRoutePreview(place)
                                    }
                                },
                                onAddWaypoint = { place ->
                                    showValhallaMenu = false
                                    searchQuery = ""
                                    searchFocused = false
                                    showSearchSheet = false
                                    isAddingWaypoint = false
                                    engine.clearSearchResults()
                                    NavSearchPrefsStore.addRecentPlace(context, place)
                                    engine.addWaypoint(place)
                                }
                            )
                        }

                        homeFeedbackMessage?.let { msg ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.overlayPanelSurface)
                                    .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .zIndex(11f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = msg,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 3. Active Navigation Overlays: Right FAB Stack + Recenter Pill + Google Maps Bottom Sheet
                if (isActiveNavigation) {
                    ActiveNavRightFabs(
                        cameraBearingProvider = { cameraBearing },
                        voiceGuidanceEnabled = engine.voiceGuidanceEnabled,
                        compact = tightOverlay,
                        onResetNorth = {
                            mapLibreMap?.easeCamera(CameraUpdateFactory.bearingTo(0.0), 400)
                        },
                        onSearchClick = {
                            tripSheetExpanded = false
                            isSettingHome = false
                            isAddingWaypoint = true
                            showSearchSheet = true
                            searchFocused = true
                            focusSearchTrigger++
                        },
                        onToggleVoiceGuidance = {
                            val nextVoice = !engine.voiceGuidanceEnabled
                            engine.setVoiceGuidance(nextVoice)
                            onToggleVoiceGuidance(nextVoice)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = if (tightOverlay) 36.dp else 20.dp)
                    )

                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .zIndex(11f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 12.dp, bottom = 10.dp)
                        ) {
                            if (!engine.isFollowingGps) {
                                RecenterPill(
                                    onClick = {
                                        mapLibreMap?.let { map ->
                                            recenterCamera(map, engine, latestMapHeightPx, use3dTilt = latestNav3dViewEnabled)
                                        } ?: run {
                                            engine.isFollowingGps = true
                                        }
                                    }
                                )
                            }

                            GoogleMapsSpeedIndicators(
                                currentSpeedKmhProvider = { speedKmhState.value },
                                speedLimitKmh = engine.currentSpeedLimitKmh,
                                isActiveNavigation = true,
                                compact = tightOverlay
                            )
                        }

                        TripSummarySheet(
                            durationText = engine.remainingDurationText,
                            distanceText = engine.remainingDistanceText,
                            arrivalTimeText = engine.arrivalTimeText,
                            expanded = tripSheetExpanded,
                            onExpandedChange = { tripSheetExpanded = it },
                            nav3dViewEnabled = nav3dViewEnabled,
                            drivingAvatar = drivingAvatar,
                            compact = tightOverlay,
                            onCloseNavigation = {
                                tripSheetExpanded = false
                                isAddingWaypoint = false
                                engine.stopNavigation()
                            },
                            onOpenRouteOverview = {
                                tripSheetExpanded = false
                                activeRoute?.let { route ->
                                    isAddingWaypoint = false
                                    engine.loadRoutePreview(route.destination, engine.waypoints)
                                }
                            },
                            onAddWaypointClick = {
                                tripSheetExpanded = false
                                showValhallaMenu = false
                                isSettingHome = false
                                isAddingWaypoint = true
                                showSearchSheet = true
                                searchFocused = true
                                focusSearchTrigger++
                            },
                            onToggleNav3dView = onToggleNav3dView,
                            onSelectDrivingAvatar = onSelectDrivingAvatar
                        )
                    }
                }

                // 4. Idle Google Maps controls: Bottom-Left Unified Speed + Limit Pill, Bottom-Right Recenter + Directions FABs, Bottom Sheet
                if (!isRoutePreview && !isActiveNavigation) {
                    GoogleMapsSpeedIndicators(
                        currentSpeedKmhProvider = { speedKmhState.value },
                        speedLimitKmh = engine.currentSpeedLimitKmh,
                        isActiveNavigation = false,
                        compact = tightOverlay,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = if (tightOverlay) 54.dp else 66.dp)
                            .zIndex(8f)
                    )

                    IdleBottomRightFabs(
                        isFollowingGps = engine.isFollowingGps,
                        compact = tightOverlay,
                        onRecenter = {
                            mapLibreMap?.let { map ->
                                recenterCamera(map, engine, latestMapHeightPx, use3dTilt = latestNav3dViewEnabled)
                            } ?: run {
                                engine.isFollowingGps = true
                            }
                        },
                        onOpenRoutePlanning = {
                            showValhallaMenu = false
                            isSettingHome = false
                            isAddingWaypoint = false
                            showSearchSheet = true
                            searchFocused = true
                            focusSearchTrigger++
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = if (tightOverlay) 54.dp else 66.dp)
                            .zIndex(8f)
                    )

                    IdleLocationBottomSheet(
                        placeLabel = engine.currentPlaceLabel,
                        compact = tightOverlay,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(8f)
                    )
                }

                // 5. Route Preview Card (BottomCenter Google Maps sheet)
                if (isRoutePreview) {
                    RoutePreviewCard(
                        destination = engine.previewDestination,
                        waypoints = waypoints,
                        options = previewOptions,
                        selectedIndex = selectedPreviewIndex,
                        isRouting = engine.isRouting,
                        routeError = engine.routeError,
                        accent = accent,
                        onAccent = onAccent,
                        compact = tightOverlay,
                        onSelectOption = { idx -> engine.selectPreviewOption(idx) },
                        onRemoveWaypoint = { idx -> engine.removeWaypoint(idx) },
                        onAddWaypointClick = {
                            showValhallaMenu = false
                            isSettingHome = false
                            isAddingWaypoint = true
                            showSearchSheet = true
                            searchFocused = true
                            focusSearchTrigger++
                        },
                        onStartNavigation = {
                            showValhallaMenu = false
                            isAddingWaypoint = false
                            showSearchSheet = false
                            tripSheetExpanded = false
                            engine.startPreviewRoute()
                            mapLibreMap?.let { map ->
                                recenterCamera(map, engine, latestMapHeightPx, use3dTilt = latestNav3dViewEnabled)
                            }
                        },
                        onCancel = {
                            showValhallaMenu = false
                            isAddingWaypoint = false
                            showSearchSheet = false
                            tripSheetExpanded = false
                            previewCardHeightPx = 0
                            engine.cancelRoutePreview()
                            mapLibreMap?.let { map ->
                                recenterCamera(map, engine, latestMapHeightPx, use3dTilt = latestNav3dViewEnabled)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { previewCardHeightPx = it.height }
                            .zIndex(12f)
                    )
                } else if (previewCardHeightPx != 0) {
                    LaunchedEffect(Unit) { previewCardHeightPx = 0 }
                }

                // 6. Full Search Overlay Sheet (Casa, Lavoro, Recenti & Photon Live Search)
                if (showSearchSheet) {
                    SearchOverlaySheet(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isSearching = engine.isSearching,
                        searchResults = engine.searchResults,
                        searchStatusMessage = engine.searchStatusMessage,
                        initialEditTarget = if (isSettingHome) QuickSlotEditTarget.HOME else QuickSlotEditTarget.NONE,
                        isAddingWaypoint = isAddingWaypoint,
                        hasActiveOrPreviewDestination = isRoutePreview || activeRoute != null,
                        fallbackHomeLabel = homeAddressLabel,
                        fallbackHomeLat = homeLat,
                        fallbackHomeLon = homeLon,
                        onSaveHomeToSettings = onSaveHomeAddress,
                        onClearHomeFromSettings = onClearHomeAddress,
                        onImmediateSearch = { query -> engine.triggerImmediateSearch(query) },
                        onSelectDestination = { place ->
                            showSearchSheet = false
                            searchFocused = false
                            isSettingHome = false
                            searchQuery = ""
                            engine.clearSearchResults()
                            if (isAddingWaypoint && (activeRoute != null || isRoutePreview)) {
                                isAddingWaypoint = false
                                engine.addWaypoint(place)
                            } else if (activeRoute != null) {
                                isAddingWaypoint = false
                                engine.calculateRoute(place)
                            } else {
                                isAddingWaypoint = false
                                engine.loadRoutePreview(place)
                            }
                        },
                        onAddWaypoint = { place ->
                            showSearchSheet = false
                            searchFocused = false
                            isSettingHome = false
                            isAddingWaypoint = false
                            searchQuery = ""
                            engine.clearSearchResults()
                            engine.addWaypoint(place)
                        },
                        onClose = {
                            showSearchSheet = false
                            searchFocused = false
                            isSettingHome = false
                            isAddingWaypoint = false
                            searchQuery = ""
                            engine.clearSearchResults()
                        },
                        onFeedbackMessage = { msg ->
                            homeFeedbackMessage = msg
                        },
                        tightOverlay = tightOverlay
                    )
                }
            }
        }
    }
}
