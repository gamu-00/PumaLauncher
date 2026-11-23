package com.openlauncher.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

private val GoogleMapsBannerGreen = Color(0xFF0B6343)

@Composable
internal fun TurnByTurnBanner(
    iconType: TurnIconType,
    roundaboutExitAngle: Float,
    distanceText: String,
    streetText: String,
    instructionText: String,
    roadBadges: List<String> = emptyList(),
    isCompactHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val iconSize = if (isCompactHeight) 38.dp else 48.dp
    val verticalPad = if (isCompactHeight) 9.dp else 14.dp
    val innerHorizontalPad = if (isCompactHeight) 12.dp else 16.dp
    val outerHorizontalPad = if (isCompactHeight) 10.dp else 14.dp
    val outerVerticalPad = if (isCompactHeight) 8.dp else 12.dp
    val distanceFontSize = if (isCompactHeight) 18.sp else 20.sp
    val streetFontSize = if (isCompactHeight) 19.sp else 24.sp
    val streetLineHeight = if (isCompactHeight) 23.sp else 28.sp

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = GoogleMapsBannerGreen,
        shadowElevation = 4.dp,
        modifier = modifier
            .padding(horizontal = outerHorizontalPad, vertical = outerVerticalPad)
            .fillMaxWidth()
            .zIndex(10f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = innerHorizontalPad, vertical = verticalPad)
        ) {
            if (iconType == TurnIconType.ROUNDABOUT) {
                RoundaboutIcon(
                    exitAngle = roundaboutExitAngle,
                    modifier = Modifier.size(iconSize)
                )
            } else {
                Icon(
                    imageVector = turnIconVector(iconType),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 2.dp else 4.dp)
            ) {
                Text(
                    text = distanceText.ifBlank { "---" },
                    color = Color.White,
                    fontSize = distanceFontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    roadBadges.take(2).forEach { badge ->
                        RoadShieldBadge(code = badge)
                    }
                    Text(
                        text = streetText.ifBlank { instructionText },
                        color = Color.White,
                        fontSize = streetFontSize,
                        lineHeight = streetLineHeight,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoadShieldBadge(code: String) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.20f))
            .border(1.dp, Color.White.copy(alpha = 0.72f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = code,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
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
