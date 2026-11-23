package com.openlauncher.app.ui.screen

import android.content.Context
import com.openlauncher.app.R
import com.openlauncher.app.util.AppLanguageManager
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.util.Locale

private val ROAD_BADGE_REGEX = Regex(
    """^(?:[A-Z]{1,2}\s*-?\s*\d{1,4}[A-Z]{0,3}|(?:NSA|EX\s*SS)\s*-?\s*\d{1,4}|(?:SS|SR|SP|SC|RA)\s*-?\s*\d{1,4}(?:\s*(?:BIS|TER|QUATER|DIR|VAR|RACC|R))?)$"""
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

internal fun isRoadBadgeCode(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.length < 2 || trimmed.length > 14) return false
    if (!trimmed.any { it.isDigit() }) return false
    return ROAD_BADGE_REGEX.matches(trimmed.uppercase(Locale.ROOT))
}

/**
 * Separates compact highway/road ref shields (e.g. "B 2", "SS36", "A22", "SP 14")
 * from full street names across all `street_names` / `begin_street_names` entries,
 * falling back to `sign.exit_toward_elements` / `sign.exit_branch_elements` on junctions.
 */
internal fun extractValhallaStreetAndBadges(maneuver: JSONObject): Pair<String, List<String>> {
    val badges = LinkedHashSet<String>()
    val extendedNames = LinkedHashSet<String>()

    fun classifyToken(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        val parts = if (" / " in clean) clean.split(" / ") else listOf(clean)
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isBlank()) continue
            if (isRoadBadgeCode(trimmed)) {
                badges.add(trimmed)
            } else {
                extendedNames.add(trimmed)
            }
        }
    }

    fun collectArrayStrings(arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val raw = arr.optString(i, "").trim()
            if (raw.isNotBlank()) classifyToken(raw)
        }
    }

    collectArrayStrings(maneuver.optJSONArray("street_names"))
    if (extendedNames.isEmpty() && badges.isEmpty()) {
        collectArrayStrings(maneuver.optJSONArray("begin_street_names"))
    }

    val sign = maneuver.optJSONObject("sign")
    if (sign != null) {
        val branchTexts = extractSignElementTexts(sign, "exit_branch_elements")
        val towardTexts = extractSignElementTexts(sign, "exit_toward_elements")
        val nameTexts = extractSignElementTexts(sign, "exit_name_elements")

        for (text in branchTexts) {
            if (isRoadBadgeCode(text)) {
                badges.add(text)
            } else if (extendedNames.isEmpty()) {
                extendedNames.add(text)
            }
        }
        for (text in towardTexts) {
            if (isRoadBadgeCode(text)) {
                badges.add(text)
            } else if (extendedNames.isEmpty()) {
                extendedNames.add(text)
            }
        }
        if (extendedNames.isEmpty()) {
            for (text in nameTexts) {
                if (isRoadBadgeCode(text)) {
                    badges.add(text)
                } else {
                    extendedNames.add(text)
                }
            }
        }
    }

    return extendedNames.joinToString(" / ") to badges.take(3)
}

private fun extractSignElementTexts(sign: JSONObject, key: String): List<String> {
    val arr = sign.optJSONArray(key) ?: return emptyList()
    val out = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i)
        val text = obj?.optString("text", "")?.trim()
            ?: arr.optString(i, "").trim()
        if (text.isNotBlank()) {
            out.add(text)
        }
    }
    return out
}

internal fun mapValhallaManeuverIcon(type: Int, instruction: String): TurnIconType {
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

internal fun mapOsrmManeuver(
    context: Context,
    type: String,
    modifier: String,
    street: String
): Pair<TurnIconType, String> {
    fun loc(resId: Int, streetResId: Int): String = if (street.isNotBlank()) {
        AppLanguageManager.localizedString(context, streetResId, street)
    } else {
        AppLanguageManager.localizedString(context, resId)
    }

    return when (type.lowercase(Locale.ROOT)) {
        "arrive" -> TurnIconType.DESTINATION to AppLanguageManager.localizedString(context, R.string.nav_maneuver_arrive)
        "depart" -> TurnIconType.STRAIGHT to loc(R.string.nav_maneuver_depart, R.string.nav_maneuver_depart_street)
        "roundabout", "rotary", "roundabout turn" ->
            TurnIconType.ROUNDABOUT to loc(R.string.nav_maneuver_roundabout, R.string.nav_maneuver_roundabout_street)
        else -> when (modifier.lowercase(Locale.ROOT)) {
            "uturn" -> TurnIconType.UTURN to loc(R.string.nav_maneuver_uturn, R.string.nav_maneuver_uturn_street)
            "sharp left" -> TurnIconType.SHARP_LEFT to loc(R.string.nav_maneuver_sharp_left, R.string.nav_maneuver_sharp_left_street)
            "left" -> TurnIconType.LEFT to loc(R.string.nav_maneuver_left, R.string.nav_maneuver_left_street)
            "slight left" -> TurnIconType.SLIGHT_LEFT to loc(R.string.nav_maneuver_slight_left, R.string.nav_maneuver_slight_left_street)
            "sharp right" -> TurnIconType.SHARP_RIGHT to loc(R.string.nav_maneuver_sharp_right, R.string.nav_maneuver_sharp_right_street)
            "right" -> TurnIconType.RIGHT to loc(R.string.nav_maneuver_right, R.string.nav_maneuver_right_street)
            "slight right" -> TurnIconType.SLIGHT_RIGHT to loc(R.string.nav_maneuver_slight_right, R.string.nav_maneuver_slight_right_street)
            else -> TurnIconType.STRAIGHT to loc(R.string.nav_maneuver_continue, R.string.nav_maneuver_continue_street)
        }
    }
}
