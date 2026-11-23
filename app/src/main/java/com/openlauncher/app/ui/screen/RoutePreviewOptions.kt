package com.openlauncher.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors
import java.util.Locale
import kotlin.math.roundToInt

private val OptBlueDay = Color(0xFF1A73E8)
private val OptBlueNight = Color(0xFF8AB4F8)
private val OptBlueTintDay = Color(0xFFE8F0FE)
private val OptBlueTintNight = Color(0xFF1E3A5F)
private val OptTextDay = Color(0xFF202124)
private val OptTextNight = Color(0xFFE8EAED)
private val OptTextMutedDay = Color(0xFF5F6368)
private val OptTextMutedNight = Color(0xFF9AA0A6)
private val OptBorderDay = Color(0xFFDADCE0)
private val OptBorderNight = Color(0xFF3C4043)
private val OptIdleBgDay = Color(0xFFF8F9FA)
private val OptIdleBgNight = Color(0xFF282A2C)
private val OptChipBgDay = Color(0xFFF1F3F4)
private val OptChipBgNight = Color(0xFF282A2C)

@Composable
internal fun WaypointsChipStrip(
    waypoints: List<PhotonPlace>,
    onRemoveWaypoint: (Int) -> Unit
) {
    val isLight = LocalCockpitColors.current.isLight
    val chipBg = if (isLight) OptChipBgDay else OptChipBgNight
    val borderCol = if (isLight) OptBorderDay else OptBorderNight
    val textPrimary = if (isLight) OptTextDay else OptTextNight
    val textMuted = if (isLight) OptTextMutedDay else OptTextMutedNight

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        waypoints.forEachIndexed { idx, wp ->
            val chipShape = RoundedCornerShape(12.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(chipShape)
                    .background(chipBg)
                    .border(1.dp, borderCol, chipShape)
                    .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "${idx + 1}. ${wp.name}",
                    color = textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onRemoveWaypoint(idx) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.nav_remove_stop),
                        tint = textMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun RouteOptionCardItem(
    option: RoutePreviewOption,
    isSelected: Boolean,
    fastestTimeSec: Double,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val optBlue = if (isLight) OptBlueDay else OptBlueNight
    val optBlueTint = if (isLight) OptBlueTintDay else OptBlueTintNight
    val optIdleBg = if (isLight) OptIdleBgDay else OptIdleBgNight
    val optBorder = if (isLight) OptBorderDay else OptBorderNight
    val textPrimary = if (isLight) OptTextDay else OptTextNight
    val textMuted = if (isLight) OptTextMutedDay else OptTextMutedNight
    val tollBadgeBg = if (isLight) Color(0xFFE8EAED) else Color(0xFF3C4043)

    val optionShape = RoundedCornerShape(14.dp)
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
    val borderColor = if (isSelected) optBlue else optBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val bg = if (isSelected) optBlueTint else optIdleBg

    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(optionShape)
            .background(bg)
            .border(borderWidth, borderColor, optionShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 7.dp else 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) optBlue else textMuted,
                modifier = Modifier.size(if (compact) 13.dp else 14.dp)
            )
            Text(
                text = title,
                color = if (isSelected) optBlue else textMuted,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = primaryTimeLabel,
            color = textPrimary,
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = distanceLabel,
                color = textMuted,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (option.hasTolls) {
                Text(
                    text = stringResource(R.string.nav_preview_toll),
                    color = textMuted,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tollBadgeBg)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
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
