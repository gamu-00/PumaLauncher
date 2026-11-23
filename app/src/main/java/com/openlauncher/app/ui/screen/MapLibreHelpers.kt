package com.openlauncher.app.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import com.openlauncher.app.R
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.concurrent.CopyOnWriteArraySet

internal const val NAV_LOG_TAG = "PumaNav"

internal const val SOURCE_ROUTE_TRAVELED = "puma-route-traveled-source"
internal const val LAYER_ROUTE_TRAVELED_CASING = "puma-route-traveled-casing-layer"
internal const val LAYER_ROUTE_TRAVELED = "puma-route-traveled-layer"
internal const val SOURCE_ROUTE_ALTS = "puma-route-alts-source"
internal const val LAYER_ROUTE_ALTS_CASING = "puma-route-alts-casing-layer"
internal const val LAYER_ROUTE_ALTS = "puma-route-alts-layer"
internal const val SOURCE_ROUTE = "puma-route-source"
internal const val LAYER_ROUTE_CASING = "puma-route-casing-layer"
internal const val LAYER_ROUTE = "puma-route-layer"
internal const val SOURCE_DEST = "puma-dest-source"
internal const val LAYER_DEST_DOT = "puma-dest-dot-layer"
internal const val SOURCE_WAYPOINTS = "puma-waypoints-source"
internal const val LAYER_WAYPOINT_DOTS = "puma-waypoint-dots-layer"
internal const val LAYER_WAYPOINT_LABELS = "puma-waypoint-labels-layer"
internal const val DRIVING_ZOOM = 16.5
internal const val DRIVING_TILT = 45.0
internal const val STATIONARY_TILT = 0.0

// ─────────────────────────────────────────────────────────────────────────────
// Public Indicator Position & Bearing Listeners (Zero Reflection)
// ─────────────────────────────────────────────────────────────────────────────

internal fun interface OnIndicatorPositionChangedListener {
    fun onIndicatorPositionChanged(latLng: LatLng)
}

internal fun interface OnIndicatorBearingChangedListener {
    fun onIndicatorBearingChanged(bearing: Float)
}

private val indicatorPositionListeners = CopyOnWriteArraySet<OnIndicatorPositionChangedListener>()
private val indicatorBearingListeners = CopyOnWriteArraySet<OnIndicatorBearingChangedListener>()

internal fun LocationComponent.addOnIndicatorPositionChangedListener(
    listener: OnIndicatorPositionChangedListener
) {
    indicatorPositionListeners.add(listener)
    if (isLocationComponentActivated) {
        runCatching { lastKnownLocation }.getOrNull()?.let { loc ->
            listener.onIndicatorPositionChanged(LatLng(loc.latitude, loc.longitude))
        }
    }
}

internal fun LocationComponent.removeOnIndicatorPositionChangedListener(
    listener: OnIndicatorPositionChangedListener
) {
    indicatorPositionListeners.remove(listener)
}

internal fun LocationComponent.addOnIndicatorBearingChangedListener(
    listener: OnIndicatorBearingChangedListener
) {
    indicatorBearingListeners.add(listener)
    if (isLocationComponentActivated) {
        runCatching { lastKnownLocation }.getOrNull()?.let { loc ->
            if (loc.hasBearing()) {
                listener.onIndicatorBearingChanged(loc.bearing)
            }
        }
    }
}

internal fun LocationComponent.removeOnIndicatorBearingChangedListener(
    listener: OnIndicatorBearingChangedListener
) {
    indicatorBearingListeners.remove(listener)
}

internal fun notifyIndicatorListenersFromPublicState(
    map: MapLibreMap,
    engine: NavigationEngine
) {
    val lc = map.locationComponent
    val camPos = map.cameraPosition
    val loc = if (lc.isLocationComponentActivated && lc.isLocationComponentEnabled) {
        runCatching { lc.lastKnownLocation }.getOrNull()
    } else {
        null
    }

    val isTrackingGps = engine.isFollowingGps &&
        lc.isLocationComponentActivated &&
        lc.isLocationComponentEnabled &&
        lc.cameraMode == CameraMode.TRACKING_GPS &&
        camPos.target != null

    val currentLatLng = when {
        isTrackingGps && camPos.target != null -> camPos.target!!
        loc != null -> LatLng(loc.latitude, loc.longitude)
        else -> LatLng(engine.currentLat, engine.currentLon)
    }

    for (l in indicatorPositionListeners) {
        l.onIndicatorPositionChanged(currentLatLng)
    }

    val currentBearing = when {
        loc != null && loc.hasBearing() -> loc.bearing
        else -> engine.currentBearing
    }
    if (currentBearing.isFinite()) {
        val normBearing = ((currentBearing % 360f) + 360f) % 360f
        for (l in indicatorBearingListeners) {
            l.onIndicatorBearingChanged(normBearing)
        }
    }
}

internal fun dynamicDrivingZoom(speedKmh: Int, hasActiveRoute: Boolean): Double = when {
    speedKmh >= 100 -> 14.8
    speedKmh >= 70 -> 15.5
    speedKmh >= 40 -> 16.1
    hasActiveRoute || speedKmh > 0 -> 16.6
    else -> DRIVING_ZOOM
}

internal fun buildLocationComponentOptions(
    context: Context,
    @Suppress("UNUSED_PARAMETER") drawableRes: Int = R.drawable.ic_nav_puck_transparent
): LocationComponentOptions = LocationComponentOptions.builder(context)
    .gpsDrawable(R.drawable.ic_nav_puck_transparent)
    .foregroundDrawable(R.drawable.ic_nav_puck_transparent)
    .foregroundTintColor(AndroidColor.TRANSPARENT)
    .foregroundStaleTintColor(AndroidColor.TRANSPARENT)
    .backgroundTintColor(AndroidColor.TRANSPARENT)
    .backgroundStaleTintColor(AndroidColor.TRANSPARENT)
    .elevation(0f)
    .accuracyAlpha(0f)
    .pulseEnabled(false)
    .enableStaleState(false)
    .minZoomIconScale(0.92f)
    .maxZoomIconScale(1.22f)
    .build()

@SuppressLint("MissingPermission")
internal fun updateLocationComponentAvatar(
    context: Context,
    map: MapLibreMap,
    drawableRes: Int = R.drawable.ic_nav_puck_transparent
) {
    runCatching {
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated) return
        lc.applyStyle(buildLocationComponentOptions(context, drawableRes))
    }
}

@SuppressLint("MissingPermission")
internal fun activateNativeLocationComponent(
    context: Context,
    map: MapLibreMap,
    style: Style,
    engine: NavigationEngine,
    mapHeightPx: Int,
    cameraTrackingListener: OnCameraTrackingChangedListener,
    isListenerAttached: Boolean,
    onListenerAttached: () -> Unit,
    avatarDrawableRes: Int = R.drawable.ic_nav_puck_transparent
) {
    if (!engine.hasLocationPermission() || !style.isFullyLoaded) return

    engine.startLocationTracking()

    val locationOptions = buildLocationComponentOptions(context, avatarDrawableRes)

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
internal fun detachNativeLocationComponent(
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

internal fun applyDrivingPadding(
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

internal fun recenterCamera(
    map: MapLibreMap,
    engine: NavigationEngine,
    mapHeightPx: Int,
    use3dTilt: Boolean = true
) {
    engine.isFollowingGps = true
    applyDrivingPadding(map, engine, mapHeightPx)

    val targetZoom = dynamicDrivingZoom(engine.currentSpeedKmh.value, engine.activeRoute != null)
    val targetTilt = if (use3dTilt && (engine.isMoving || engine.activeRoute != null)) DRIVING_TILT else STATIONARY_TILT
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

internal fun setupMapLayers(
    style: Style,
    accentHex: String,
    route: ValhallaRoute?,
    traveledPoints: List<LatLng>,
    remainingPoints: List<LatLng>,
    previewOptions: List<RoutePreviewOption> = emptyList(),
    selectedPreviewIndex: Int = 0,
    waypoints: List<PhotonPlace> = emptyList(),
    isLightTheme: Boolean
) {
    val ribbonCoreColor = AndroidColor.parseColor(
        if (isLightTheme) AutomotiveMapStyle.ROUTE_CORE_DAY_HEX else AutomotiveMapStyle.ROUTE_CORE_NIGHT_HEX
    )
    val ribbonCasingColor = AndroidColor.parseColor(
        if (isLightTheme) AutomotiveMapStyle.ROUTE_CASING_DAY_HEX else AutomotiveMapStyle.ROUTE_CASING_NIGHT_HEX
    )
    val altCoreColor = AndroidColor.parseColor(
        if (isLightTheme) AutomotiveMapStyle.ROUTE_ALT_CORE_DAY_HEX else AutomotiveMapStyle.ROUTE_ALT_CORE_NIGHT_HEX
    )
    val altCasingColor = AndroidColor.parseColor(
        if (isLightTheme) AutomotiveMapStyle.ROUTE_ALT_CASING_DAY_HEX else AutomotiveMapStyle.ROUTE_ALT_CASING_NIGHT_HEX
    )

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
                PropertyFactory.lineColor(altCasingColor),
                PropertyFactory.lineOpacity(0.85f),
                PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCasingWidthExpression()),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LAYER_ROUTE_ALTS) == null) {
        addLayerBelowLocation(
            LineLayer(LAYER_ROUTE_ALTS, SOURCE_ROUTE_ALTS).withProperties(
                PropertyFactory.lineColor(altCoreColor),
                PropertyFactory.lineOpacity(0.90f),
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
                PropertyFactory.lineColor(ribbonCasingColor),
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

    if (style.getSource(SOURCE_WAYPOINTS) == null) {
        style.addSource(GeoJsonSource(SOURCE_WAYPOINTS, emptyFeatureCollectionJson()))
    }
    if (style.getLayer(LAYER_WAYPOINT_DOTS) == null) {
        addLayerBelowLocation(
            CircleLayer(LAYER_WAYPOINT_DOTS, SOURCE_WAYPOINTS).withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(AndroidColor.parseColor("#FF8F00")),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE)
            )
        )
    }
    if (style.getLayer(LAYER_WAYPOINT_LABELS) == null) {
        addLayerBelowLocation(
            SymbolLayer(LAYER_WAYPOINT_LABELS, SOURCE_WAYPOINTS).withProperties(
                PropertyFactory.textField(Expression.get("index")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(AndroidColor.WHITE),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
        )
    }

    if (style.getSource(SOURCE_DEST) == null) {
        style.addSource(GeoJsonSource(SOURCE_DEST, emptyFeatureCollectionJson()))
    }
    if (style.getLayer(LAYER_DEST_DOT) == null) {
        addLayerBelowLocation(
            CircleLayer(LAYER_DEST_DOT, SOURCE_DEST).withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(AndroidColor.parseColor("#E53935")),
                PropertyFactory.circleStrokeWidth(3.5f),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE)
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
        waypoints = waypoints,
        accentHex = accentHex,
        isLightTheme = isLightTheme
    )
}

internal fun updateRouteOnStyle(
    style: Style,
    route: ValhallaRoute?,
    traveledPoints: List<LatLng>,
    remainingPoints: List<LatLng>,
    previewOptions: List<RoutePreviewOption> = emptyList(),
    selectedPreviewIndex: Int = 0,
    waypoints: List<PhotonPlace> = emptyList(),
    @Suppress("UNUSED_PARAMETER") accentHex: String,
    isLightTheme: Boolean
) {
    val ribbonCoreColor = AndroidColor.parseColor(
        if (isLightTheme) AutomotiveMapStyle.ROUTE_CORE_DAY_HEX else AutomotiveMapStyle.ROUTE_CORE_NIGHT_HEX
    )
    val ribbonCasingColor = AndroidColor.parseColor(
        if (isLightTheme) AutomotiveMapStyle.ROUTE_CASING_DAY_HEX else AutomotiveMapStyle.ROUTE_CASING_NIGHT_HEX
    )
    val altCoreColor = AndroidColor.parseColor(
        if (isLightTheme) AutomotiveMapStyle.ROUTE_ALT_CORE_DAY_HEX else AutomotiveMapStyle.ROUTE_ALT_CORE_NIGHT_HEX
    )
    val altCasingColor = AndroidColor.parseColor(
        if (isLightTheme) AutomotiveMapStyle.ROUTE_ALT_CASING_DAY_HEX else AutomotiveMapStyle.ROUTE_ALT_CASING_NIGHT_HEX
    )
    (style.getLayer(LAYER_ROUTE_ALTS_CASING) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(altCasingColor),
        PropertyFactory.lineOpacity(0.85f),
        PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCasingWidthExpression())
    )
    (style.getLayer(LAYER_ROUTE_ALTS) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(altCoreColor),
        PropertyFactory.lineOpacity(0.90f),
        PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCoreWidthExpression())
    )
    (style.getLayer(LAYER_ROUTE_CASING) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(ribbonCasingColor),
        PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCasingWidthExpression())
    )
    (style.getLayer(LAYER_ROUTE) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(ribbonCoreColor),
        PropertyFactory.lineWidth(AutomotiveMapStyle.routeRibbonCoreWidthExpression())
    )
    (style.getLayer(LAYER_DEST_DOT) as? CircleLayer)?.setProperties(
        PropertyFactory.circleStrokeColor(AndroidColor.WHITE)
    )

    val traveledSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_TRAVELED)
    val altsSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_ALTS)
    val routeSrc = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE) ?: return
    val waypointsSrc = style.getSourceAs<GeoJsonSource>(SOURCE_WAYPOINTS)
    val destSrc = style.getSourceAs<GeoJsonSource>(SOURCE_DEST) ?: return

    val waypointsJson = if (waypoints.isNotEmpty()) {
        waypointsFeatureCollectionJson(waypoints)
    } else {
        emptyFeatureCollectionJson()
    }

    if (previewOptions.isNotEmpty()) {
        traveledSrc?.setGeoJson(emptyFeatureCollectionJson())
        val selected = previewOptions.getOrNull(selectedPreviewIndex) ?: previewOptions.first()
        val unselected = previewOptions.mapIndexedNotNull { idx, opt ->
            if (idx != selectedPreviewIndex && opt.route.shapePoints.size >= 2) idx to opt.route.shapePoints else null
        }
        altsSrc?.setGeoJson(
            if (unselected.isNotEmpty()) featureCollectionFromIndexedLines(unselected) else emptyFeatureCollectionJson()
        )
        routeSrc.setGeoJson(lineStringFeatureJson(selected.route.shapePoints))
        waypointsSrc?.setGeoJson(waypointsJson)
        destSrc.setGeoJson(pointFeatureJson(selected.route.destination.lat, selected.route.destination.lon))
    } else if (route != null && route.shapePoints.isNotEmpty()) {
        altsSrc?.setGeoJson(emptyFeatureCollectionJson())
        if (traveledPoints.size >= 2) {
            traveledSrc?.setGeoJson(lineStringFeatureJson(traveledPoints))
        } else {
            traveledSrc?.setGeoJson(emptyFeatureCollectionJson())
        }
        val activeRemaining = if (remainingPoints.size >= 2) remainingPoints else route.shapePoints
        routeSrc.setGeoJson(lineStringFeatureJson(activeRemaining))
        waypointsSrc?.setGeoJson(waypointsJson)
        destSrc.setGeoJson(pointFeatureJson(route.destination.lat, route.destination.lon))
    } else {
        traveledSrc?.setGeoJson(emptyFeatureCollectionJson())
        altsSrc?.setGeoJson(emptyFeatureCollectionJson())
        routeSrc.setGeoJson(emptyFeatureCollectionJson())
        waypointsSrc?.setGeoJson(emptyFeatureCollectionJson())
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

private fun waypointsFeatureCollectionJson(waypoints: List<PhotonPlace>): String {
    val features = JSONArray()
    waypoints.forEachIndexed { idx, wp ->
        features.put(
            JSONObject().apply {
                put("type", "Feature")
                put(
                    "geometry",
                    JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(wp.lon).put(wp.lat))
                    }
                )
                put("properties", JSONObject().put("index", (idx + 1).toString()))
            }
        )
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
}

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
