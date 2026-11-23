package com.openlauncher.app.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.openlauncher.app.data.DrivingAvatar
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.exp
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Vehicle Color Palettes (Desert Island Blue #1565C0 & Pearl Sport White #F8F9FA)
// ─────────────────────────────────────────────────────────────────────────────

internal data class Vehicle25DPalette(
    val headlightBeam: Color,
    val chassisSkirt: Color,
    val bodyMain: Color,
    val bodyLeftHighlight: Color,
    val bodyRightShade: Color,
    val mirrorCap: Color,
    val mirrorArm: Color,
    val hoodBase: Color,
    val hoodDomeHighlight: Color,
    val headlightOuter: Color,
    val pillarPaint: Color,
    val roofBase: Color,
    val roofInner: Color,
    val roofRib: Color,
    val spoilerPaint: Color,
    val spoilerLip: Color,
    val taillightOuter: Color,
    val taillightCore: Color
)

internal val PumaStBluePalette = Vehicle25DPalette(
    headlightBeam = Color(0x3880D8FF),
    chassisSkirt = Color(0xFF092E6B),
    bodyMain = Color(0xFF1565C0),          // Desert Island Blue
    bodyLeftHighlight = Color(0xFF1E88E5),
    bodyRightShade = Color(0xFF0D47A1),
    mirrorCap = Color(0xFF1E88E5),
    mirrorArm = Color(0xFF0D47A1),
    hoodBase = Color(0xFF1976D2),
    hoodDomeHighlight = Color(0xFF42A5F5),
    headlightOuter = Color(0xFFE1F5FE),
    pillarPaint = Color(0xFF1976D2),
    roofBase = Color(0xFF132238),
    roofInner = Color(0xFF1E3452),
    roofRib = Color(0xFF42A5F5),
    spoilerPaint = Color(0xFF1E88E5),
    spoilerLip = Color(0xFF0D47A1),
    taillightOuter = Color(0xFFFF5252),
    taillightCore = Color(0xFFFFEBEE)
)

internal val SportWhitePalette = Vehicle25DPalette(
    headlightBeam = Color(0x38FFF59D),
    chassisSkirt = Color(0xFF78909C),
    bodyMain = Color(0xFFF8F9FA),          // Sportiva Bianca
    bodyLeftHighlight = Color(0xFFFFFFFF),
    bodyRightShade = Color(0xFFCFD8DC),
    mirrorCap = Color(0xFFFFFFFF),
    mirrorArm = Color(0xFF90A4AE),
    hoodBase = Color(0xFFE2E8F0),
    hoodDomeHighlight = Color(0xFFFFFFFF),
    headlightOuter = Color(0xFFFFF9C4),
    pillarPaint = Color(0xFFF8F9FA),
    roofBase = Color(0xFF0F172A),
    roofInner = Color(0xFF1E293B),
    roofRib = Color(0xFF94A3B8),
    spoilerPaint = Color(0xFFFFFFFF),
    spoilerLip = Color(0xFFCBD5E1),
    taillightOuter = Color(0xFFE53935),
    taillightCore = Color(0xFFFFCDD2)
)

internal fun DrivingAvatar.paletteOrNull(): Vehicle25DPalette? = when (this) {
    DrivingAvatar.PUMA_ST_BLUE -> PumaStBluePalette
    DrivingAvatar.SPORT_RED -> SportWhitePalette
    DrivingAvatar.CLASSIC_ARROW -> null
}

/**
 * Lightweight mutable render-state holder updated synchronously on every MapLibre render frame
 * without triggering Compose recompositions or layout passes.
 */
internal class AvatarRenderState {
    var screenX: Float = Float.NaN
    var screenY: Float = Float.NaN
    var indicatorLat: Double = Double.NaN
    var indicatorLon: Double = Double.NaN
    var hasIndicatorPos: Boolean = false
    var targetCarBearing: Float = 0f
    var smoothedCarBearing: Float = 0f
    var hasBearingInit: Boolean = false
    var cameraBearing: Float = 0f
    var cameraTilt: Float = 0f
    var lastFrameNanos: Long = 0L
}

/**
 * Shortest-arc angular delta in `[-180f, +180f]` preventing reverse spins across the 0°/360° wrap.
 */
internal fun shortestAngleDelta(currentDeg: Float, targetDeg: Float): Float {
    val normCurrent = ((currentDeg % 360f) + 360f) % 360f
    val normTarget = ((targetDeg % 360f) + 360f) % 360f
    return ((normTarget - normCurrent + 540f) % 360f) - 180f
}

/**
 * Frame-rate independent exponential angular smoothing along the shortest arc:
 * `current += shortestAngleDelta * (1f - exp(-12f * dtSeconds))`.
 */
internal fun stepSmoothedBearing(currentDeg: Float, targetDeg: Float, dtSeconds: Float): Float {
    val delta = shortestAngleDelta(currentDeg, targetDeg)
    val clampedDt = dtSeconds.coerceIn(0.001f, 0.1f)
    val alpha = (1f - exp(-12f * clampedDt)).coerceIn(0f, 1f)
    return (((currentDeg + delta * alpha) % 360f) + 360f) % 360f
}

/**
 * 2.5D Multi-Layer extruded vehicle overlay rendered in viewport space on top of [MapView].
 *
 * Updates synchronously on every MapLibre render frame via [MapView.OnDidFinishRenderingFrameListener],
 * projecting the active GPS coordinate with `mapboxMap.projection.toScreenLocation(latLng)` and
 * reading `frameCounter` exclusively inside the [Canvas] draw lambda for zero-recomposition 60 fps draw.
 */
@Composable
internal fun VehicleAvatarOverlay(
    mapView: MapView,
    mapLibreMap: MapLibreMap?,
    styleReady: Boolean,
    engine: NavigationEngine,
    drivingAvatar: DrivingAvatar,
    modifier: Modifier = Modifier
) {
    val renderState = remember { AvatarRenderState() }
    var frameCounter by remember { mutableIntStateOf(0) }

    val reusableLatLng = remember { LatLng(0.0, 0.0) }
    val reusablePath = remember { Path() }

    fun syncFromMap(map: MapLibreMap) {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val dtSeconds = if (renderState.lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((nowNanos - renderState.lastFrameNanos) * 1e-9f).coerceIn(0.001f, 0.1f)
        }
        renderState.lastFrameNanos = nowNanos

        val camPos = map.cameraPosition
        notifyIndicatorListenersFromPublicState(map, engine)

        val lat: Double
        val lon: Double
        if (renderState.hasIndicatorPos &&
            !renderState.indicatorLat.isNaN() &&
            !renderState.indicatorLon.isNaN()
        ) {
            lat = renderState.indicatorLat
            lon = renderState.indicatorLon
        } else {
            lat = engine.currentLat
            lon = engine.currentLon
        }

        if (!renderState.hasBearingInit) {
            val normInit = ((engine.currentBearing % 360f) + 360f) % 360f
            renderState.targetCarBearing = normInit
            renderState.smoothedCarBearing = normInit
            renderState.hasBearingInit = true
        }

        renderState.smoothedCarBearing = stepSmoothedBearing(
            currentDeg = renderState.smoothedCarBearing,
            targetDeg = renderState.targetCarBearing,
            dtSeconds = dtSeconds
        )

        reusableLatLng.latitude = lat
        reusableLatLng.longitude = lon
        val pt = map.projection.toScreenLocation(reusableLatLng)

        renderState.screenX = pt.x
        renderState.screenY = pt.y
        renderState.cameraBearing = camPos.bearing.toFloat()
        renderState.cameraTilt = camPos.tilt.toFloat()

        // Mutate the single draw-only tick; read ONLY inside Canvas { ... }
        frameCounter++
    }

    DisposableEffect(mapView, mapLibreMap, styleReady) {
        val map = mapLibreMap
        if (map == null) {
            onDispose {}
        } else {
            val lc = map.locationComponent
            val positionListener = OnIndicatorPositionChangedListener { latLng ->
                renderState.indicatorLat = latLng.latitude
                renderState.indicatorLon = latLng.longitude
                renderState.hasIndicatorPos = true
            }
            val bearingListener = OnIndicatorBearingChangedListener { bearing ->
                val norm = ((bearing % 360f) + 360f) % 360f
                renderState.targetCarBearing = norm
                if (!renderState.hasBearingInit) {
                    renderState.smoothedCarBearing = norm
                    renderState.hasBearingInit = true
                }
            }
            lc.addOnIndicatorPositionChangedListener(positionListener)
            lc.addOnIndicatorBearingChangedListener(bearingListener)

            val frameListener = MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
                runCatching { syncFromMap(map) }
            }
            mapView.addOnDidFinishRenderingFrameListener(frameListener)
            runCatching { syncFromMap(map) }
            onDispose {
                mapView.removeOnDidFinishRenderingFrameListener(frameListener)
                lc.removeOnIndicatorPositionChangedListener(positionListener)
                lc.removeOnIndicatorBearingChangedListener(bearingListener)
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read frameCounter exclusively inside the draw scope so Compose skips recomposition & layout
        val tick = frameCounter
        if (tick < 0) return@Canvas

        val cx = renderState.screenX
        val cy = renderState.screenY
        if (cx.isNaN() || cy.isNaN()) return@Canvas
        if (cx < -120f || cy < -120f || cx > size.width + 120f || cy > size.height + 120f) return@Canvas

        val relativeHeading = ((renderState.smoothedCarBearing - renderState.cameraBearing) % 360f + 360f) % 360f
        val tiltFactor = sin(Math.toRadians(renderState.cameraTilt.toDouble())).toFloat().coerceIn(0f, 1f)

        // Reference viewport is 64x64 virtual units scaled to 58.dp on screen
        val avatarSizePx = 58f * density
        val unit = avatarSizePx / 64f

        drawVehicleAvatar25D(
            avatar = drivingAvatar,
            centerX = cx,
            centerY = cy,
            unit = unit,
            densityPx = density,
            relativeHeading = relativeHeading,
            tiltFactor = tiltFactor,
            path = reusablePath
        )
    }
}

/**
 * Shared 2.5D multi-layer extrusion renderer used both by [VehicleAvatarOverlay] on the live map
 * and by [DrivingAvatarPickerSection] for the interactive 3D preview cards.
 */
internal fun DrawScope.drawVehicleAvatar25D(
    avatar: DrivingAvatar,
    centerX: Float,
    centerY: Float,
    unit: Float,
    densityPx: Float,
    relativeHeading: Float,
    tiltFactor: Float,
    path: Path
) {
    val palette = avatar.paletteOrNull()
    if (palette == null) {
        drawClassicArrow25D(
            centerX = centerX,
            centerY = centerY,
            unit = unit,
            densityPx = densityPx,
            relativeHeading = relativeHeading,
            tiltFactor = tiltFactor,
            path = path
        )
        return
    }

    val l1ShiftPx = 3f * densityPx * tiltFactor
    val l2ShiftPx = 6f * densityPx * tiltFactor
    val l3ShiftPx = 8.5f * densityPx * tiltFactor

    // ── Livello 0 (base: 0.dp): Ombra morbida scura a terra + fasci luminosi dei fari LED anteriori ──
    withTransform({
        translate(left = centerX, top = centerY)
        rotate(degrees = relativeHeading, pivot = Offset.Zero)
    }) {
        drawCarLayer0GroundShadowAndBeams(unit, palette, path)
    }

    // Sub-step extrusion slice at +1.5.dp * tiltFactor for solid lower-chassis side thickness at 45°
    if (tiltFactor > 0.05f) {
        val midChassisShift = 1.5f * densityPx * tiltFactor
        withTransform({
            translate(left = centerX, top = centerY - midChassisShift)
            rotate(degrees = relativeHeading, pivot = Offset.Zero)
        }) {
            drawCarChassisSkirtOnly(unit, palette)
        }
    }

    // ── Livello 1 (+3.dp * tiltFactor): Telaio inferiore, paraurti sagomati, passaruota e specchietti laterali ──
    withTransform({
        translate(left = centerX, top = centerY - l1ShiftPx)
        rotate(degrees = relativeHeading, pivot = Offset.Zero)
    }) {
        drawCarLayer1ChassisAndBody(unit, palette, path)
    }

    // Sub-step extrusion slice at +4.5.dp * tiltFactor connecting body beltline to cabin greenhouse
    if (tiltFactor > 0.05f) {
        val midBeltShift = 4.5f * densityPx * tiltFactor
        withTransform({
            translate(left = centerX, top = centerY - midBeltShift)
            rotate(degrees = relativeHeading, pivot = Offset.Zero)
        }) {
            drawCarBeltlineBridge(unit, palette)
        }
    }

    // ── Livello 2 (+6.dp * tiltFactor): Vetri fumé (parabrezza, finestrini laterali, lunotto) con riflesso azzurrino e montanti verniciati ──
    withTransform({
        translate(left = centerX, top = centerY - l2ShiftPx)
        rotate(degrees = relativeHeading, pivot = Offset.Zero)
    }) {
        drawCarLayer2GreenhouseAndPillars(unit, palette, path)
    }

    // ── Livello 3 (+8.5.dp * tiltFactor): Tetto con nervature e spoiler posteriore ──
    withTransform({
        translate(left = centerX, top = centerY - l3ShiftPx)
        rotate(degrees = relativeHeading, pivot = Offset.Zero)
    }) {
        drawCarLayer3RoofAndSpoiler(unit, palette, path)
    }
}

private fun DrawScope.drawCarLayer0GroundShadowAndBeams(
    u: Float,
    palette: Vehicle25DPalette,
    path: Path
) {
    // Outer soft ambient ground shadow
    drawRoundRect(
        color = Color(0x24000000),
        topLeft = Offset(-19.5f * u, -25.5f * u),
        size = Size(39f * u, 54.5f * u),
        cornerRadius = CornerRadius(14f * u, 14f * u)
    )
    // Mid ground shadow
    drawRoundRect(
        color = Color(0x3D000000),
        topLeft = Offset(-17.2f * u, -23.5f * u),
        size = Size(34.4f * u, 50.5f * u),
        cornerRadius = CornerRadius(12f * u, 12f * u)
    )
    // Contact shadow directly under tires & chassis
    drawRoundRect(
        color = Color(0x59000000),
        topLeft = Offset(-15.4f * u, -21.5f * u),
        size = Size(30.8f * u, 46.5f * u),
        cornerRadius = CornerRadius(10f * u, 10f * u)
    )

    // Left & right forward LED headlight beam cones
    path.reset()
    // Left beam cone
    path.moveTo(-14.5f * u, -30.5f * u)
    path.lineTo(-6.8f * u, -30.5f * u)
    path.lineTo(-6.2f * u, -19.5f * u)
    path.lineTo(-13.0f * u, -18.5f * u)
    path.close()
    // Right beam cone
    path.moveTo(14.5f * u, -30.5f * u)
    path.lineTo(6.8f * u, -30.5f * u)
    path.lineTo(6.2f * u, -19.5f * u)
    path.lineTo(13.0f * u, -18.5f * u)
    path.close()
    drawPath(path = path, color = palette.headlightBeam)
}

private fun DrawScope.drawCarChassisSkirtOnly(
    u: Float,
    palette: Vehicle25DPalette
) {
    drawRoundRect(
        color = palette.chassisSkirt,
        topLeft = Offset(-15.0f * u, -23.5f * u),
        size = Size(30.0f * u, 48.0f * u),
        cornerRadius = CornerRadius(10.5f * u, 10.5f * u)
    )
}

private fun DrawScope.drawCarLayer1ChassisAndBody(
    u: Float,
    palette: Vehicle25DPalette,
    path: Path
) {
    val wheelColor = Color(0xFF14181F)
    val wheelRimHighlight = Color(0xFF374151)

    // 4 sculpted wheel arches / tires protruding slightly from chassis
    // Front-Left tire
    drawRoundRect(
        color = wheelColor,
        topLeft = Offset(-17.2f * u, -19.0f * u),
        size = Size(4.2f * u, 9.5f * u),
        cornerRadius = CornerRadius(1.8f * u, 1.8f * u)
    )
    // Front-Right tire
    drawRoundRect(
        color = wheelColor,
        topLeft = Offset(13.0f * u, -19.0f * u),
        size = Size(4.2f * u, 9.5f * u),
        cornerRadius = CornerRadius(1.8f * u, 1.8f * u)
    )
    // Rear-Left tire
    drawRoundRect(
        color = wheelColor,
        topLeft = Offset(-17.2f * u, 7.0f * u),
        size = Size(4.2f * u, 10.0f * u),
        cornerRadius = CornerRadius(1.8f * u, 1.8f * u)
    )
    // Rear-Right tire
    drawRoundRect(
        color = wheelColor,
        topLeft = Offset(13.0f * u, 7.0f * u),
        size = Size(4.2f * u, 10.0f * u),
        cornerRadius = CornerRadius(1.8f * u, 1.8f * u)
    )

    // Subtle wheel arch lip accents
    drawRoundRect(
        color = wheelRimHighlight,
        topLeft = Offset(-16.8f * u, -17.5f * u),
        size = Size(1.3f * u, 6.5f * u),
        cornerRadius = CornerRadius(0.6f * u, 0.6f * u)
    )
    drawRoundRect(
        color = wheelRimHighlight,
        topLeft = Offset(15.5f * u, -17.5f * u),
        size = Size(1.3f * u, 6.5f * u),
        cornerRadius = CornerRadius(0.6f * u, 0.6f * u)
    )

    // Lower chassis skirt & rear diffuser
    drawRoundRect(
        color = palette.chassisSkirt,
        topLeft = Offset(-15.0f * u, -23.5f * u),
        size = Size(30.0f * u, 48.2f * u),
        cornerRadius = CornerRadius(10.5f * u, 10.5f * u)
    )

    // Aerodynamic Side Mirrors (arms + painted caps)
    path.reset()
    // Left mirror arm
    path.moveTo(-13.8f * u, -11.8f * u)
    path.lineTo(-19.5f * u, -10.8f * u)
    path.lineTo(-19.2f * u, -8.2f * u)
    path.lineTo(-13.8f * u, -8.2f * u)
    path.close()
    // Right mirror arm
    path.moveTo(13.8f * u, -11.8f * u)
    path.lineTo(19.5f * u, -10.8f * u)
    path.lineTo(19.2f * u, -8.2f * u)
    path.lineTo(13.8f * u, -8.2f * u)
    path.close()
    drawPath(path = path, color = palette.mirrorArm)

    path.reset()
    // Left mirror painted cap
    path.moveTo(-14.2f * u, -11.2f * u)
    path.lineTo(-18.8f * u, -10.4f * u)
    path.lineTo(-18.6f * u, -9.0f * u)
    path.lineTo(-14.2f * u, -9.0f * u)
    path.close()
    // Right mirror painted cap
    path.moveTo(14.2f * u, -11.2f * u)
    path.lineTo(18.8f * u, -10.4f * u)
    path.lineTo(18.6f * u, -9.0f * u)
    path.lineTo(14.2f * u, -9.0f * u)
    path.close()
    drawPath(path = path, color = palette.mirrorCap)

    // Main sculpted vehicle body shell
    drawRoundRect(
        color = palette.bodyMain,
        topLeft = Offset(-14.6f * u, -24.5f * u),
        size = Size(29.2f * u, 47.4f * u),
        cornerRadius = CornerRadius(10.2f * u, 10.2f * u)
    )

    // Left volumetric fender & shoulder highlight
    path.reset()
    path.moveTo(-14.4f * u, -15.5f * u)
    path.lineTo(-7.5f * u, -23.8f * u)
    path.lineTo(-10.8f * u, 12.0f * u)
    path.lineTo(-14.4f * u, 11.0f * u)
    path.close()
    drawPath(path = path, color = palette.bodyLeftHighlight)

    // Right volumetric fender & shoulder shading
    path.reset()
    path.moveTo(14.4f * u, -15.5f * u)
    path.lineTo(7.5f * u, -23.8f * u)
    path.lineTo(10.8f * u, 12.0f * u)
    path.lineTo(14.4f * u, 11.0f * u)
    path.close()
    drawPath(path = path, color = palette.bodyRightShade)

    // Front ST black honeycomb splitter lip
    path.reset()
    path.moveTo(-10.2f * u, -23.4f * u)
    path.lineTo(10.2f * u, -23.4f * u)
    path.lineTo(8.5f * u, -21.4f * u)
    path.lineTo(-8.5f * u, -21.4f * u)
    path.close()
    drawPath(path = path, color = Color(0xFF1A202C))

    // Sculpted bonnet / hood with power dome creases
    path.reset()
    path.moveTo(-11.2f * u, -20.0f * u)
    path.lineTo(11.2f * u, -20.0f * u)
    path.lineTo(9.5f * u, -11.2f * u)
    path.lineTo(-9.5f * u, -11.2f * u)
    path.close()
    drawPath(path = path, color = palette.hoodBase)

    path.reset()
    path.moveTo(-7.8f * u, -20.8f * u)
    path.lineTo(7.8f * u, -20.8f * u)
    path.lineTo(6.6f * u, -12.6f * u)
    path.lineTo(-6.6f * u, -12.6f * u)
    path.close()
    drawPath(path = path, color = palette.hoodDomeHighlight)

    // Center hood power dome inset
    drawRoundRect(
        color = palette.bodyMain,
        topLeft = Offset(-4.6f * u, -20.2f * u),
        size = Size(9.2f * u, 7.0f * u),
        cornerRadius = CornerRadius(2.0f * u, 2.0f * u)
    )

    // Signature LED Headlights (left & right front clusters)
    path.reset()
    // Left headlight housing
    path.moveTo(-13.4f * u, -19.2f * u)
    path.lineTo(-8.2f * u, -22.2f * u)
    path.lineTo(-9.6f * u, -17.6f * u)
    path.lineTo(-13.0f * u, -16.8f * u)
    path.close()
    // Right headlight housing
    path.moveTo(13.4f * u, -19.2f * u)
    path.lineTo(8.2f * u, -22.2f * u)
    path.lineTo(9.6f * u, -17.6f * u)
    path.lineTo(13.0f * u, -16.8f * u)
    path.close()
    drawPath(path = path, color = palette.headlightOuter)

    path.reset()
    // Left LED DRL core
    path.moveTo(-12.6f * u, -19.2f * u)
    path.lineTo(-9.1f * u, -21.2f * u)
    path.lineTo(-10.0f * u, -18.4f * u)
    path.lineTo(-12.4f * u, -17.9f * u)
    path.close()
    // Right LED DRL core
    path.moveTo(12.6f * u, -19.2f * u)
    path.lineTo(9.1f * u, -21.2f * u)
    path.lineTo(10.0f * u, -18.4f * u)
    path.lineTo(12.4f * u, -17.9f * u)
    path.close()
    drawPath(path = path, color = Color.White)

    // Bright LED Taillights at rear bumper corners
    path.reset()
    path.moveTo(-13.5f * u, 18.8f * u)
    path.lineTo(-6.5f * u, 20.6f * u)
    path.lineTo(-7.0f * u, 22.5f * u)
    path.lineTo(-13.2f * u, 20.8f * u)
    path.close()
    path.moveTo(13.5f * u, 18.8f * u)
    path.lineTo(6.5f * u, 20.6f * u)
    path.lineTo(7.0f * u, 22.5f * u)
    path.lineTo(13.2f * u, 20.8f * u)
    path.close()
    drawPath(path = path, color = palette.taillightOuter)

    path.reset()
    path.moveTo(-12.5f * u, 19.4f * u)
    path.lineTo(-7.8f * u, 20.6f * u)
    path.lineTo(-8.0f * u, 21.5f * u)
    path.lineTo(-12.4f * u, 20.3f * u)
    path.close()
    path.moveTo(12.5f * u, 19.4f * u)
    path.lineTo(7.8f * u, 20.6f * u)
    path.lineTo(8.0f * u, 21.5f * u)
    path.lineTo(12.4f * u, 20.3f * u)
    path.close()
    drawPath(path = path, color = palette.taillightCore)
}

private fun DrawScope.drawCarBeltlineBridge(
    u: Float,
    palette: Vehicle25DPalette
) {
    drawRoundRect(
        color = palette.bodyRightShade,
        topLeft = Offset(-13.0f * u, -13.0f * u),
        size = Size(26.0f * u, 31.8f * u),
        cornerRadius = CornerRadius(7.5f * u, 7.5f * u)
    )
    drawRoundRect(
        color = palette.bodyMain,
        topLeft = Offset(-12.6f * u, -12.6f * u),
        size = Size(25.2f * u, 31.0f * u),
        cornerRadius = CornerRadius(7.2f * u, 7.2f * u)
    )
}

private fun DrawScope.drawCarLayer2GreenhouseAndPillars(
    u: Float,
    palette: Vehicle25DPalette,
    path: Path
) {
    // Wrap-around smoked cabin glass base
    drawRoundRect(
        color = Color(0xFF111927),
        topLeft = Offset(-11.8f * u, -11.8f * u),
        size = Size(23.6f * u, 29.8f * u),
        cornerRadius = CornerRadius(6.2f * u, 6.2f * u)
    )

    // Smoked Front Windshield (Parabrezza)
    path.reset()
    path.moveTo(-10.6f * u, -10.2f * u)
    path.lineTo(10.6f * u, -10.2f * u)
    path.lineTo(8.6f * u, -3.5f * u)
    path.lineTo(-8.6f * u, -3.5f * u)
    path.close()
    drawPath(path = path, color = Color(0xFF22344A))

    // Bluish specular reflection on windshield (riflesso azzurrino)
    path.reset()
    path.moveTo(-9.4f * u, -9.8f * u)
    path.lineTo(3.2f * u, -10.0f * u)
    path.lineTo(0.4f * u, -4.4f * u)
    path.lineTo(-8.2f * u, -4.2f * u)
    path.close()
    drawPath(path = path, color = Color(0xFF5C8BB8))

    // Secondary crisp cyan-blue glass glint
    path.reset()
    path.moveTo(-7.8f * u, -9.2f * u)
    path.lineTo(-1.5f * u, -9.3f * u)
    path.lineTo(-3.2f * u, -5.0f * u)
    path.lineTo(-7.2f * u, -4.9f * u)
    path.close()
    drawPath(path = path, color = Color(0xFF8CD2F5))

    // Smoked Side Windows (Left & Right) with subtle bluish tint
    path.reset()
    // Left side window
    path.moveTo(-11.2f * u, -7.5f * u)
    path.lineTo(-9.2f * u, -3.2f * u)
    path.lineTo(-9.0f * u, 9.8f * u)
    path.lineTo(-11.2f * u, 11.2f * u)
    path.close()
    // Right side window
    path.moveTo(11.2f * u, -7.5f * u)
    path.lineTo(9.2f * u, -3.2f * u)
    path.lineTo(9.0f * u, 9.8f * u)
    path.lineTo(11.2f * u, 11.2f * u)
    path.close()
    drawPath(path = path, color = Color(0xFF1D3047))

    // Side window cyan reflection strips
    path.reset()
    path.moveTo(-10.8f * u, -5.8f * u)
    path.lineTo(-9.6f * u, -2.8f * u)
    path.lineTo(-9.6f * u, 3.2f * u)
    path.lineTo(-10.8f * u, 2.2f * u)
    path.close()
    drawPath(path = path, color = Color(0xFF4F7CA8))

    // Smoked Rear Window (Lunotto) + bluish reflection
    path.reset()
    path.moveTo(-7.8f * u, 10.4f * u)
    path.lineTo(7.8f * u, 10.4f * u)
    path.lineTo(9.0f * u, 15.4f * u)
    path.lineTo(-9.0f * u, 15.4f * u)
    path.close()
    drawPath(path = path, color = Color(0xFF22344A))

    path.reset()
    path.moveTo(-6.4f * u, 11.1f * u)
    path.lineTo(1.8f * u, 11.3f * u)
    path.lineTo(0.0f * u, 14.8f * u)
    path.lineTo(-7.4f * u, 14.4f * u)
    path.close()
    drawPath(path = path, color = Color(0xFF5683B0))

    // Painted A, B, and C Pillars (montanti verniciati)
    path.reset()
    // Left A-pillar
    path.moveTo(-11.8f * u, -10.8f * u)
    path.lineTo(-10.2f * u, -10.2f * u)
    path.lineTo(-8.2f * u, -3.4f * u)
    path.lineTo(-9.8f * u, -3.2f * u)
    path.close()
    // Right A-pillar
    path.moveTo(11.8f * u, -10.8f * u)
    path.lineTo(10.2f * u, -10.2f * u)
    path.lineTo(8.2f * u, -3.4f * u)
    path.lineTo(9.8f * u, -3.2f * u)
    path.close()
    // Left B-pillar
    path.moveTo(-11.5f * u, 2.2f * u)
    path.lineTo(-9.0f * u, 2.2f * u)
    path.lineTo(-9.0f * u, 4.2f * u)
    path.lineTo(-11.5f * u, 4.2f * u)
    path.close()
    // Right B-pillar
    path.moveTo(11.5f * u, 2.2f * u)
    path.lineTo(9.0f * u, 2.2f * u)
    path.lineTo(9.0f * u, 4.2f * u)
    path.lineTo(11.5f * u, 4.2f * u)
    path.close()
    // Left C-pillar
    path.moveTo(-11.2f * u, 11.2f * u)
    path.lineTo(-8.6f * u, 9.5f * u)
    path.lineTo(-7.6f * u, 14.8f * u)
    path.lineTo(-10.6f * u, 15.6f * u)
    path.close()
    // Right C-pillar
    path.moveTo(11.2f * u, 11.2f * u)
    path.lineTo(8.6f * u, 9.5f * u)
    path.lineTo(7.6f * u, 14.8f * u)
    path.lineTo(10.6f * u, 15.6f * u)
    path.close()
    drawPath(path = path, color = palette.pillarPaint)
}

private fun DrawScope.drawCarLayer3RoofAndSpoiler(
    u: Float,
    palette: Vehicle25DPalette,
    path: Path
) {
    // Painted outer roof rails framing the roof panel
    drawRoundRect(
        color = palette.pillarPaint,
        topLeft = Offset(-9.2f * u, -3.8f * u),
        size = Size(18.4f * u, 14.6f * u),
        cornerRadius = CornerRadius(3.4f * u, 3.4f * u)
    )

    // Contrast panoramic / sport roof base
    drawRoundRect(
        color = palette.roofBase,
        topLeft = Offset(-8.2f * u, -3.4f * u),
        size = Size(16.4f * u, 13.6f * u),
        cornerRadius = CornerRadius(2.8f * u, 2.8f * u)
    )

    // Inner roof highlight panel
    drawRoundRect(
        color = palette.roofInner,
        topLeft = Offset(-6.8f * u, -2.0f * u),
        size = Size(13.6f * u, 10.6f * u),
        cornerRadius = CornerRadius(2.0f * u, 2.0f * u)
    )

    // Longitudinal aerodynamic roof ribs (nervature sul tetto)
    drawLine(
        color = palette.roofRib.copy(alpha = 0.55f),
        start = Offset(-4.2f * u, -1.5f * u),
        end = Offset(-4.0f * u, 7.8f * u),
        strokeWidth = 1.1f * u
    )
    drawLine(
        color = palette.roofRib.copy(alpha = 0.40f),
        start = Offset(0f, -1.8f * u),
        end = Offset(0f, 7.5f * u),
        strokeWidth = 0.9f * u
    )
    drawLine(
        color = palette.roofRib.copy(alpha = 0.55f),
        start = Offset(4.2f * u, -1.5f * u),
        end = Offset(4.0f * u, 7.8f * u),
        strokeWidth = 1.1f * u
    )

    // Shark-fin roof antenna at rear center of roof
    path.reset()
    path.moveTo(0f, 6.2f * u)
    path.lineTo(1.2f * u, 9.4f * u)
    path.lineTo(-1.2f * u, 9.4f * u)
    path.close()
    drawPath(path = path, color = palette.pillarPaint)

    // Sculpted Rear ST Wing / Spoiler (spoiler posteriore) with 3D shadow lip
    path.reset()
    path.moveTo(-11.4f * u, 15.2f * u)
    path.lineTo(11.4f * u, 15.2f * u)
    path.lineTo(10.5f * u, 19.2f * u)
    path.lineTo(-10.5f * u, 19.2f * u)
    path.close()
    drawPath(path = path, color = palette.spoilerPaint)

    // Trailing aerodynamic Gurney flap / spoiler lip
    path.reset()
    path.moveTo(-10.5f * u, 18.2f * u)
    path.lineTo(10.5f * u, 18.2f * u)
    path.lineTo(9.8f * u, 19.6f * u)
    path.lineTo(-9.8f * u, 19.6f * u)
    path.close()
    drawPath(path = path, color = palette.spoilerLip)
}

/**
 * Classic Arrow (Freccia Classica): white disc with soft ground shadow and extruded 2.5D
 * Google Maps Blue (`#1A73E8`) directional chevron.
 */
private fun DrawScope.drawClassicArrow25D(
    centerX: Float,
    centerY: Float,
    unit: Float,
    densityPx: Float,
    relativeHeading: Float,
    tiltFactor: Float,
    path: Path
) {
    val discShiftPx = 2.5f * densityPx * tiltFactor
    val chevronBaseShiftPx = 4.2f * densityPx * tiltFactor
    val chevronTopShiftPx = 5.6f * densityPx * tiltFactor

    // Layer 0 (Ground): soft layered ground shadow
    drawCircle(
        color = Color(0x28000000),
        radius = 25.0f * unit,
        center = Offset(centerX, centerY + 1.5f * unit)
    )
    drawCircle(
        color = Color(0x42000000),
        radius = 22.8f * unit,
        center = Offset(centerX, centerY + 0.8f * unit)
    )

    // Extruded lower rim of the white puck when tilted
    if (tiltFactor > 0.05f) {
        val rimMidShift = 1.2f * densityPx * tiltFactor
        drawCircle(
            color = Color(0xFFDADCE0),
            radius = 22.4f * unit,
            center = Offset(centerX, centerY - rimMidShift)
        )
    }

    // Layer 1: White puck disc with subtle border & inner bevel
    val discCenter = Offset(centerX, centerY - discShiftPx)
    drawCircle(
        color = Color.White,
        radius = 22.4f * unit,
        center = discCenter
    )
    drawCircle(
        color = Color(0xFFDADCE0),
        radius = 22.4f * unit,
        center = discCenter,
        style = Stroke(width = 2.0f * unit)
    )
    drawCircle(
        color = Color(0xFFF8F9FA),
        radius = 19.8f * unit,
        center = discCenter
    )

    // Layer 2: Extruded 3D chevron base / side thickness (#0D47A1)
    withTransform({
        translate(left = centerX, top = centerY - chevronBaseShiftPx)
        rotate(degrees = relativeHeading, pivot = Offset.Zero)
    }) {
        path.reset()
        path.moveTo(0f, -17.5f * unit)
        path.lineTo(13.2f * unit, 10.2f * unit)
        path.lineTo(0f, 4.4f * unit)
        path.lineTo(-13.2f * unit, 10.2f * unit)
        path.close()
        drawPath(path = path, color = Color(0xFF0D47A1))
    }

    // Layer 3: Top directional chevron in #1A73E8 with 3D right facet (#1557B0)
    withTransform({
        translate(left = centerX, top = centerY - chevronTopShiftPx)
        rotate(degrees = relativeHeading, pivot = Offset.Zero)
    }) {
        path.reset()
        path.moveTo(0f, -17.5f * unit)
        path.lineTo(13.0f * unit, 10.0f * unit)
        path.lineTo(0f, 4.2f * unit)
        path.lineTo(-13.0f * unit, 10.0f * unit)
        path.close()
        drawPath(path = path, color = Color(0xFF1A73E8))

        // Right 3D shaded facet
        path.reset()
        path.moveTo(0f, -17.5f * unit)
        path.lineTo(13.0f * unit, 10.0f * unit)
        path.lineTo(0f, 4.2f * unit)
        path.close()
        drawPath(path = path, color = Color(0xFF1557B0))
    }
}
