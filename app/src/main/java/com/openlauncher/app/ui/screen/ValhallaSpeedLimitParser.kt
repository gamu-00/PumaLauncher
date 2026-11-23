package com.openlauncher.app.ui.screen

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val LEADING_INT_REGEX = Regex("""^(\d{1,3})""")

/**
 * Converts numeric or OSM textual speed limits (e.g. "IT:urban", "IT:rural", "IT:motorway", "50", "30 mph")
 * into km/h integers, returning -1 when absent or unlimited.
 */
internal fun parseValhallaSpeedLimit(raw: Any?): Int {
    if (raw == null || raw === JSONObject.NULL) return -1
    if (raw is Number) {
        val v = raw.toInt()
        return if (v in 5..250) v else -1
    }
    val text = raw.toString().trim()
    if (text.isEmpty()) return -1
    val lower = text.lowercase(Locale.ROOT)
    when (lower) {
        "none", "unlimited", "signals", "variable", "0", "-1" -> return -1
        "it:urban", "urban" -> return 50
        "it:rural", "rural" -> return 90
        "it:motorway", "motorway" -> return 130
        "it:trunk", "trunk" -> return 110
        "it:living_street", "living_street", "walk" -> return 10
        "de:urban", "fr:urban", "es:urban", "ch:urban", "at:urban" -> return 50
        "de:rural", "at:rural" -> return 100
        "fr:rural" -> return 80
        "es:rural", "ch:rural" -> return 90
        "fr:motorway", "es:motorway", "ch:motorway" -> return 120
        "at:motorway" -> return 130
    }
    if (lower.endsWith(":urban")) return 50
    if (lower.endsWith(":rural")) return 90
    if (lower.endsWith(":motorway")) return 130
    if (lower.endsWith(":trunk")) return 110
    if (lower.endsWith(":living_street")) return 10

    val numericMatch = LEADING_INT_REGEX.find(lower) ?: return -1
    val value = numericMatch.groupValues[1].toIntOrNull() ?: return -1
    if (value !in 5..250) return -1
    return if ("mph" in lower) {
        (((value * 1.60934) / 5.0).roundToInt() * 5).coerceIn(10, 200)
    } else {
        value
    }
}

/**
 * Extracts speed limits directly from a Valhalla `leg` JSON object into a primitive `IntArray`
 * aligned with decoded polyline points (`-1` when unavailable).
 */
internal fun extractLegSpeedLimits(
    leg: JSONObject,
    pointCount: Int
): IntArray {
    val limits = IntArray(pointCount) { -1 }
    if (pointCount <= 0) return limits

    val shapeAttrsArr = leg.optJSONObject("shape_attributes")?.optJSONArray("speed_limit")
        ?: leg.optJSONArray("speed_limit")
        ?: leg.optJSONArray("speed_limits")
    if (shapeAttrsArr != null) {
        val count = minOf(shapeAttrsArr.length(), pointCount)
        for (i in 0 until count) {
            val parsed = parseValhallaSpeedLimit(shapeAttrsArr.opt(i))
            if (parsed > 0) limits[i] = parsed
        }
    }

    val edges = leg.optJSONArray("edges")
    if (edges != null) {
        applyEdgesArrayToLimits(edges, limits, indexMapper = null)
    }

    val maneuvers = leg.optJSONArray("maneuvers")
    if (maneuvers != null) {
        for (i in 0 until maneuvers.length()) {
            val m = maneuvers.optJSONObject(i) ?: continue
            val limit = parseValhallaSpeedLimit(m.opt("speed_limit"))
            if (limit <= 0) continue
            val beginIdx = m.optInt("begin_shape_index", 0).coerceIn(0, pointCount - 1)
            val endIdx = m.optInt("end_shape_index", beginIdx).coerceIn(beginIdx, pointCount - 1)
            for (ptIdx in beginIdx..endIdx) {
                if (limits[ptIdx] <= 0) limits[ptIdx] = limit
            }
        }
    }

    return limits
}

/**
 * Populates [targetLimits] from Valhalla `/trace_attributes` `edges` array.
 */
internal fun applyTraceAttributesEdges(
    root: JSONObject,
    targetLimits: IntArray,
    sampledToOriginalIndex: IntArray?
): Boolean {
    val edges = root.optJSONArray("edges") ?: return false
    return applyEdgesArrayToLimits(edges, targetLimits, sampledToOriginalIndex)
}

private fun applyEdgesArrayToLimits(
    edges: JSONArray,
    targetLimits: IntArray,
    indexMapper: IntArray?
): Boolean {
    if (targetLimits.isEmpty()) return false
    val lastIdx = targetLimits.lastIndex
    var updated = false
    for (i in 0 until edges.length()) {
        val edge = edges.optJSONObject(i) ?: continue
        val limit = parseValhallaSpeedLimit(edge.opt("speed_limit"))
        if (limit <= 0) continue

        val rawBegin = edge.optInt("begin_shape_index", -1)
        val rawEnd = edge.optInt("end_shape_index", -1)
        if (rawBegin < 0 || rawEnd < rawBegin) continue

        val mappedBegin = if (indexMapper != null) {
            indexMapper[rawBegin.coerceIn(0, indexMapper.lastIndex)]
        } else {
            rawBegin
        }.coerceIn(0, lastIdx)
        val mappedEnd = if (indexMapper != null) {
            indexMapper[rawEnd.coerceIn(0, indexMapper.lastIndex)]
        } else {
            rawEnd
        }.coerceIn(mappedBegin, lastIdx)

        for (ptIdx in mappedBegin..mappedEnd) {
            targetLimits[ptIdx] = limit
        }
        updated = true
    }
    return updated
}

/**
 * Encodes polyline6 (precision 1e6) directly without allocating intermediate point JSON objects.
 */
internal fun encodePolyline6(
    points: List<LatLng>,
    sampledToOriginalIndex: IntArray? = null
): String {
    val sb = StringBuilder(points.size * 6)
    var prevLat = 0L
    var prevLng = 0L

    val count = sampledToOriginalIndex?.size ?: points.size
    for (i in 0 until count) {
        val pt = points[sampledToOriginalIndex?.get(i) ?: i]
        val lat = (pt.latitude * 1e6).roundToLong()
        val lng = (pt.longitude * 1e6).roundToLong()
        encodeSignedValue(lat - prevLat, sb)
        encodeSignedValue(lng - prevLng, sb)
        prevLat = lat
        prevLng = lng
    }
    return sb.toString()
}

private fun encodeSignedValue(value: Long, sb: StringBuilder) {
    var v = if (value < 0) (value shl 1).inv() else (value shl 1)
    while (v >= 0x20) {
        sb.append(((0x20L or (v and 0x1fL)) + 63L).toInt().toChar())
        v = v shr 5
    }
    sb.append((v + 63L).toInt().toChar())
}
