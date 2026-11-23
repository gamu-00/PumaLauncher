package com.openlauncher.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.ui.theme.LocalCockpitColors

private val PillSurfaceNight = Color(0xFF1E1F20)
private val PillSurfaceDay = Color(0xFFFFFFFF)
private val PillBorderNight = Color(0xFF3C4043)
private val PillBorderDay = Color(0xFFDADCE0)

private val SpeedTextNight = Color(0xFFE8EAED)
private val SpeedTextDay = Color(0xFF202124)
private val SpeedUnitNight = Color(0xFF9AA0A6)
private val SpeedUnitDay = Color(0xFF5F6368)
private val SpeedAlertNight = Color(0xFFF28B82)
private val SpeedAlertDay = Color(0xFFD93025)
private val SpeedSignRed = Color(0xFFD93025)

@Composable
internal fun GoogleMapsSpeedIndicators(
    currentSpeedKmhProvider: () -> Int,
    speedLimitKmh: Int,
    @Suppress("UNUSED_PARAMETER") isActiveNavigation: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val speedKmh = currentSpeedKmhProvider().coerceAtLeast(0)
    val showSpeedLimit = speedLimitKmh > 0
    val isOverLimit = showSpeedLimit && speedKmh > speedLimitKmh

    val pillSurface = if (isLight) PillSurfaceDay else PillSurfaceNight
    val pillBorder = if (isLight) PillBorderDay else PillBorderNight
    val speedColor = when {
        isOverLimit -> if (isLight) SpeedAlertDay else SpeedAlertNight
        isLight -> SpeedTextDay
        else -> SpeedTextNight
    }
    val unitColor = if (isLight) SpeedUnitDay else SpeedUnitNight

    val signSize = if (compact) 38.dp else 44.dp
    val signFontSize = if (compact) 14.sp else 16.sp
    val speedFontSize = if (compact) 18.sp else 20.sp
    val speedLineHeight = if (compact) 19.sp else 21.sp

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = pillSurface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, pillBorder),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            modifier = Modifier.padding(
                start = if (showSpeedLimit) 5.dp else 12.dp,
                end = if (compact) 12.dp else 14.dp,
                top = if (compact) 5.dp else 6.dp,
                bottom = if (compact) 5.dp else 6.dp
            )
        ) {
            // Left side: circular European speed-limit sign (white bg, red border, bold black number)
            if (showSpeedLimit) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(signSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(if (compact) 3.dp else 3.5.dp, SpeedSignRed, CircleShape)
                ) {
                    Text(
                        text = speedLimitKmh.toString(),
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        color = SpeedTextDay,
                        fontSize = signFontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Right side: live GPS speed rounded + "km/h" below
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(min = if (compact) 34.dp else 38.dp)
            ) {
                Text(
                    text = speedKmh.toString(),
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    color = speedColor,
                    fontSize = speedFontSize,
                    lineHeight = speedLineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "km/h",
                    color = unitColor,
                    fontSize = if (compact) 9.sp else 10.sp,
                    lineHeight = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
