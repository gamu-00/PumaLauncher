package com.openlauncher.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure GNSS [LocationEngine] with physical outlier rejection, a lightweight 2D constant-velocity
 * Kalman filter (lat/lon/speed), standstill suppression (MapLibre issue #4821), and soft
 * snap-to-route projection when an active Valhalla/OSRM polyline is available.
 */
class FilteredGpsLocationEngine(
    context: Context,
    private val routePointsProvider: () -> List<LatLng>? = { null },
    private val onFilteredLocation: ((Location) -> Unit)? = null
) : LocationEngine {

    private companion object {
        private const val TAG = "PumaGpsEngine"
        private const val GPS_MIN_TIME_MS = 1000L
        private const val GPS_MIN_DISTANCE_M = 0f
        private const val MAX_ACCURACY_METERS = 35f
        private const val STALE_RESET_MS = 5000L
        private const val MAX_SPEED_MPS = 70.0
        private const val MAX_INSTANT_JUMP_METERS = 80f
        private const val INSTANT_JUMP_WINDOW_MS = 1000L
        private const val STANDSTILL_SPEED_MPS = 0.5f
        private const val STANDSTILL_DISTANCE_M = 2.0f
        private const val SNAP_MAX_DISTANCE_M = 25.0
        private const val SNAP_FULL_DISTANCE_M = 15.0
        private const val KALMAN_PROCESS_ACCEL_STD = 3.0
        private const val REORIGIN_DISTANCE_M = 50_000.0
        private const val METERS_PER_DEG_LAT = 111_132.92
        private const val METERS_PER_DEG_LON_EQUATOR = 111_319.49
    }

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val callbacks = CopyOnWriteArraySet<LocationEngineCallback<LocationEngineResult>>()

    private var engineTrackingActive = false
    private var isHardwareListening = false

    // Outlier & staleness tracking
    private var lastValidRawFix: Location? = null
    private var lastValidFixTimeMs: Long = 0L
    private var lastInternalUpdateTimeMs: Long = 0L
    private var lastMapLibreEmittedLocation: Location? = null
    private var lastStableBearing: Float? = null

    // 2D Constant-Velocity Kalman state in local East-North meters around (originLat, originLon)
    private var kalmanInitialized = false
    private var originLat = 0.0
    private var originLon = 0.0
    private var metersPerDegLon = METERS_PER_DEG_LON_EQUATOR
    private var lastKalmanTimeMs = 0L

    private var x = 0.0
    private var vx = 0.0
    private var px00 = 0.0
    private var px01 = 0.0
    private var px11 = 0.0

    private var y = 0.0
    private var vy = 0.0
    private var py00 = 0.0
    private var py01 = 0.0
    private var py11 = 0.0

    private var kalmanSpeedMps = 0.0
    private var speedVar = 4.0

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            processIncomingGpsFix(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        // Required explicitly on API 29 head units where LocationListener had no default methods.
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun setEngineTrackingActive(active: Boolean) {
        engineTrackingActive = active
        syncHardwareListener()
    }

    fun release() {
        callbacks.clear()
        engineTrackingActive = false
        syncHardwareListener()
        resetFilterState()
        lastStableBearing = null
        lastInternalUpdateTimeMs = 0L
    }

    @SuppressLint("MissingPermission")
    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        val current = lastMapLibreEmittedLocation
        if (current != null) {
            callback.onSuccess(LocationEngineResult.create(Location(current)))
            return
        }
        if (!hasLocationPermission()) {
            callback.onSuccess(LocationEngineResult.create(emptyList()))
            return
        }
        val lastGps = runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }.getOrNull()
        if (lastGps != null && isBasicFixValid(lastGps)) {
            callback.onSuccess(LocationEngineResult.create(Location(lastGps)))
        } else {
            callback.onSuccess(LocationEngineResult.create(emptyList()))
        }
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?
    ) {
        callbacks.add(callback)
        syncHardwareListener()
        lastMapLibreEmittedLocation?.let { cached ->
            callback.onSuccess(LocationEngineResult.create(Location(cached)))
        }
    }

    override fun requestLocationUpdates(request: LocationEngineRequest, pendingIntent: PendingIntent?) {
        // PendingIntent updates are not used by MapLibre LocationComponent in this app.
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        callbacks.remove(callback)
        syncHardwareListener()
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent?) {
        // PendingIntent updates are not used.
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private fun syncHardwareListener() {
        val shouldListen = (engineTrackingActive || callbacks.isNotEmpty()) && hasLocationPermission()
        if (shouldListen == isHardwareListening) return

        if (shouldListen) {
            runCatching {
                if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        GPS_MIN_TIME_MS,
                        GPS_MIN_DISTANCE_M,
                        gpsListener,
                        Looper.getMainLooper()
                    )
                    isHardwareListening = true
                    Log.d(TAG, "GPS_PROVIDER listener avviato (minTime=${GPS_MIN_TIME_MS}ms, minDist=${GPS_MIN_DISTANCE_M}m)")
                } else {
                    Log.w(TAG, "GPS_PROVIDER non presente su questo dispositivo")
                }
            }.onFailure { e ->
                Log.w(TAG, "Errore avvio GPS_PROVIDER: ${e.message}")
            }
        } else {
            runCatching {
                locationManager.removeUpdates(gpsListener)
            }
            isHardwareListening = false
            Log.d(TAG, "GPS_PROVIDER listener fermato")
        }
    }

    private fun isBasicFixValid(loc: Location): Boolean {
        if (loc.latitude.isNaN() || loc.longitude.isNaN()) return false
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return false
        if (!loc.hasAccuracy() || loc.accuracy.isNaN() || loc.accuracy > MAX_ACCURACY_METERS) return false
        return true
    }

    private fun resetFilterState() {
        lastValidRawFix = null
        lastValidFixTimeMs = 0L
        lastMapLibreEmittedLocation = null
        kalmanInitialized = false
        originLat = 0.0
        originLon = 0.0
        x = 0.0
        vx = 0.0
        px00 = 0.0
        px01 = 0.0
        px11 = 0.0
        y = 0.0
        vy = 0.0
        py00 = 0.0
        py01 = 0.0
        py11 = 0.0
        kalmanSpeedMps = 0.0
        speedVar = 4.0
        lastKalmanTimeMs = 0L
    }

    private fun processIncomingGpsFix(rawLoc: Location) {
        // (a) Basic validity & accuracy gate (<= 35m)
        if (!isBasicFixValid(rawLoc)) return

        val nowMs = SystemClock.elapsedRealtime()

        // (b) Physical anti-outlier + 5000ms stale reset
        val prevRaw = lastValidRawFix
        if (prevRaw != null) {
            val dtMs = nowMs - lastValidFixTimeMs
            if (dtMs > STALE_RESET_MS) {
                Log.d(TAG, "Gap GPS (${dtMs}ms > ${STALE_RESET_MS}ms): reset stato Kalman per riaggancio immediato")
                resetFilterState()
            } else {
                val distM = prevRaw.distanceTo(rawLoc)
                val dtSec = (dtMs / 1000.0).coerceAtLeast(0.05)
                if ((dtMs <= INSTANT_JUMP_WINDOW_MS && distM > MAX_INSTANT_JUMP_METERS) ||
                    (distM / dtSec > MAX_SPEED_MPS)
                ) {
                    Log.d(TAG, "Outlier GPS scartato: dist=${distM}m in ${dtMs}ms")
                    return
                }
            }
        }

        lastValidRawFix = Location(rawLoc)
        lastValidFixTimeMs = nowMs

        // (c) Lightweight 2D Kalman filter on latitude, longitude, and speed
        updateKalmanFilter(rawLoc, nowMs)

        val filteredLat = originLat + (y / METERS_PER_DEG_LAT)
        val filteredLon = originLon + (x / metersPerDegLon)
        val filteredSpeedMps = kalmanSpeedMps.toFloat().coerceAtLeast(0f)

        val outputLoc = Location(rawLoc).apply {
            latitude = filteredLat
            longitude = filteredLon
            speed = filteredSpeedMps
        }

        // Bearing stabilization (avoid jumping to 0° when stationary at a traffic light)
        if (filteredSpeedMps >= 0.8f) {
            val rawOrKinematicBearing = if (rawLoc.hasBearing()) {
                normalizeBearing(rawLoc.bearing)
            } else {
                val kinSpeed = hypot(vx, vy)
                if (kinSpeed > 0.5) {
                    normalizeBearing(Math.toDegrees(atan2(vx, vy)).toFloat())
                } else {
                    lastStableBearing
                }
            }
            if (rawOrKinematicBearing != null) {
                lastStableBearing = rawOrKinematicBearing
                outputLoc.bearing = rawOrKinematicBearing
            }
        } else {
            lastStableBearing?.let { stable ->
                outputLoc.bearing = stable
            }
        }

        // Soft snap-to-route on outputLoc only (never mutates Kalman state)
        applySoftSnapToRoute(outputLoc)

        lastInternalUpdateTimeMs = nowMs

        // (d) Standstill optimization (MapLibre issue #4821): suppress MapLibre camera/symbol jitter
        val prevEmitted = lastMapLibreEmittedLocation
        if (prevEmitted != null &&
            filteredSpeedMps < STANDSTILL_SPEED_MPS &&
            prevEmitted.distanceTo(outputLoc) < STANDSTILL_DISTANCE_M
        ) {
            val stationaryLoc = Location(prevEmitted).apply {
                time = outputLoc.time
                elapsedRealtimeNanos = outputLoc.elapsedRealtimeNanos
                speed = 0f
            }
            onFilteredLocation?.invoke(stationaryLoc)
            return
        }

        lastMapLibreEmittedLocation = Location(outputLoc)
        onFilteredLocation?.invoke(Location(outputLoc))

        if (callbacks.isNotEmpty()) {
            val result = LocationEngineResult.create(Location(outputLoc))
            for (cb in callbacks) {
                cb.onSuccess(result)
            }
        }
    }

    private fun updateKalmanFilter(rawLoc: Location, nowMs: Long) {
        val accMeters = rawLoc.accuracy.coerceIn(3f, MAX_ACCURACY_METERS).toDouble()
        val measVarPos = accMeters * accMeters

        if (!kalmanInitialized) {
            originLat = rawLoc.latitude
            originLon = rawLoc.longitude
            metersPerDegLon = (METERS_PER_DEG_LON_EQUATOR * cos(Math.toRadians(originLat))).coerceAtLeast(1e-6)
            x = 0.0
            vx = 0.0
            px00 = measVarPos
            px01 = 0.0
            px11 = 25.0

            y = 0.0
            vy = 0.0
            py00 = measVarPos
            py01 = 0.0
            py11 = 25.0

            kalmanSpeedMps = if (rawLoc.hasSpeed()) rawLoc.speed.coerceAtLeast(0f).toDouble() else 0.0
            speedVar = 4.0
            lastKalmanTimeMs = nowMs
            kalmanInitialized = true
            return
        }

        val dt = ((nowMs - lastKalmanTimeMs) / 1000.0).coerceIn(0.1, 5.0)
        lastKalmanTimeMs = nowMs

        val dt2 = dt * dt
        val dt3 = dt2 * dt
        val dt4 = dt2 * dt2
        val sigmaA2 = KALMAN_PROCESS_ACCEL_STD * KALMAN_PROCESS_ACCEL_STD
        val q00 = 0.25 * dt4 * sigmaA2
        val q01 = 0.5 * dt3 * sigmaA2
        val q11 = dt2 * sigmaA2

        // Predict & update X (East)
        val measX = (rawLoc.longitude - originLon) * metersPerDegLon
        val predX = x + vx * dt
        val ppx00 = px00 + 2.0 * dt * px01 + dt2 * px11 + q00
        val ppx01 = px01 + dt * px11 + q01
        val ppx11 = px11 + q11
        val sX = ppx00 + measVarPos
        val kx0 = ppx00 / sX
        val kx1 = ppx01 / sX
        val innovX = measX - predX
        x = predX + kx0 * innovX
        vx += kx1 * innovX
        px00 = ((1.0 - kx0) * ppx00).coerceAtLeast(1e-4)
        px01 = (1.0 - kx0) * ppx01
        px11 = (ppx11 - kx1 * ppx01).coerceAtLeast(1e-4)

        // Predict & update Y (North)
        val measY = (rawLoc.latitude - originLat) * METERS_PER_DEG_LAT
        val predY = y + vy * dt
        val ppy00 = py00 + 2.0 * dt * py01 + dt2 * py11 + q00
        val ppy01 = py01 + dt * py11 + q01
        val ppy11 = py11 + q11
        val sY = ppy00 + measVarPos
        val ky0 = ppy00 / sY
        val ky1 = ppy01 / sY
        val innovY = measY - predY
        y = predY + ky0 * innovY
        vy += ky1 * innovY
        py00 = ((1.0 - ky0) * ppy00).coerceAtLeast(1e-4)
        py01 = (1.0 - ky0) * ppy01
        py11 = (ppy11 - ky1 * ppy01).coerceAtLeast(1e-4)

        // Damp residual velocity when GNSS already reports standstill to avoid braking overshoot
        if (rawLoc.hasSpeed() && rawLoc.speed < 0.3f) {
            vx *= 0.3
            vy *= 0.3
        }

        // 1D Kalman update on scalar speed
        val measSpeed = if (rawLoc.hasSpeed()) {
            rawLoc.speed.coerceAtLeast(0f).toDouble()
        } else {
            hypot(vx, vy)
        }
        val measSpeedVar = if (rawLoc.hasSpeed()) 0.64 else 4.0
        val predSpeedVar = speedVar + (KALMAN_PROCESS_ACCEL_STD * dt).let { it * it }
        val kSpeed = predSpeedVar / (predSpeedVar + measSpeedVar)
        kalmanSpeedMps = (kalmanSpeedMps + kSpeed * (measSpeed - kalmanSpeedMps)).coerceAtLeast(0.0)
        speedVar = ((1.0 - kSpeed) * predSpeedVar).coerceAtLeast(1e-4)

        // Re-center tangent plane origin on long drives (> 50km)
        if (hypot(x, y) > REORIGIN_DISTANCE_M) {
            originLat += y / METERS_PER_DEG_LAT
            originLon += x / metersPerDegLon
            metersPerDegLon = (METERS_PER_DEG_LON_EQUATOR * cos(Math.toRadians(originLat))).coerceAtLeast(1e-6)
            x = 0.0
            y = 0.0
        }
    }

    private fun applySoftSnapToRoute(loc: Location) {
        val pts = routePointsProvider() ?: return
        if (pts.size < 2) return

        val pLat = loc.latitude
        val pLon = loc.longitude
        val cosLat = cos(Math.toRadians(pLat)).coerceAtLeast(1e-6)
        val px = pLon * cosLat
        val py = pLat

        var bestDistMeters = Double.MAX_VALUE
        var bestProjLat = pLat
        var bestProjLon = pLon
        var bestSegIdx = -1

        for (i in 0 until pts.lastIndex) {
            val a = pts[i]
            val b = pts[i + 1]
            // Fast bounding pre-filter (~500m margin)
            val minLat = minOf(a.latitude, b.latitude) - 0.005
            val maxLat = maxOf(a.latitude, b.latitude) + 0.005
            if (pLat < minLat || pLat > maxLat) continue
            val minLon = minOf(a.longitude, b.longitude) - 0.007
            val maxLon = maxOf(a.longitude, b.longitude) + 0.007
            if (pLon < minLon || pLon > maxLon) continue

            val ax = a.longitude * cosLat
            val ay = a.latitude
            val bx = b.longitude * cosLat
            val by = b.latitude
            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq > 1e-14) {
                (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
            } else 0.0
            val projLat = ay + t * dy
            val projLon = (ax + t * dx) / cosLat
            val d = haversineMeters(pLat, pLon, projLat, projLon)
            if (d < bestDistMeters) {
                bestDistMeters = d
                bestProjLat = projLat
                bestProjLon = projLon
                bestSegIdx = i
            }
        }

        if (bestSegIdx < 0 || bestDistMeters >= SNAP_MAX_DISTANCE_M) return

        val blend = if (bestDistMeters <= SNAP_FULL_DISTANCE_M) {
            1.0
        } else {
            ((SNAP_MAX_DISTANCE_M - bestDistMeters) / (SNAP_MAX_DISTANCE_M - SNAP_FULL_DISTANCE_M))
                .coerceIn(0.0, 1.0)
        }

        loc.latitude = pLat + (bestProjLat - pLat) * blend
        loc.longitude = pLon + (bestProjLon - pLon) * blend

        val segBearing = resolveSegmentBearing(pts, bestSegIdx) ?: return
        val alignedBearing = if (loc.hasBearing()) {
            lerpAngleDegrees(loc.bearing, segBearing, blend.toFloat())
        } else {
            segBearing
        }
        loc.bearing = alignedBearing
        lastStableBearing = alignedBearing
    }

    private fun resolveSegmentBearing(pts: List<LatLng>, segIdx: Int): Float? {
        val p1 = pts[segIdx]
        val p2 = pts[segIdx + 1]
        if (haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude) >= 1.0) {
            return bearingDegrees(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }
        // Fallback across adjacent vertices if segment is degenerate
        val start = (segIdx - 1).coerceAtLeast(0)
        val end = (segIdx + 2).coerceAtMost(pts.lastIndex)
        val a = pts[start]
        val b = pts[end]
        return if (haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) >= 1.0) {
            bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
        } else null
    }

    private fun normalizeBearing(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    private fun lerpAngleDegrees(fromDeg: Float, toDeg: Float, fraction: Float): Float {
        var delta = ((toDeg - fromDeg + 540f) % 360f) - 180f
        if (abs(delta) > 180f) delta -= 360f
        return normalizeBearing(fromDeg + delta * fraction)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return r * c
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val yComp = sin(dLon) * cos(phi2)
        val xComp = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return normalizeBearing(Math.toDegrees(atan2(yComp, xComp)).toFloat())
    }
}
