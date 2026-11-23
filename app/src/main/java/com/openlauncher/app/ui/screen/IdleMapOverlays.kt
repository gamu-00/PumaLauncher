package com.openlauncher.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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

private val MapsBlueDay = Color(0xFF1A73E8)
private val MapsBlueNight = Color(0xFF8AB4F8)
private val MapsSurfaceDay = Color(0xFFFFFFFF)
private val MapsSurfaceNight = Color(0xFF1E1F20)
private val MapsBorderDay = Color(0xFFDADCE0)
private val MapsBorderNight = Color(0xFF3C4043)
private val MapsTextDay = Color(0xFF202124)
private val MapsTextNight = Color(0xFFE8EAED)
private val MapsDirectionsTealDay = Color(0xFF00677F)
private val MapsDirectionsTealNight = Color(0xFF0077B6)
private val MapsHandleDay = Color(0xFFDADCE0)
private val MapsHandleNight = Color(0xFF3C4043)

@Composable
internal fun IdleBottomRightFabs(
    isFollowingGps: Boolean,
    onRecenter: () -> Unit,
    onOpenRoutePlanning: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) MapsSurfaceDay else MapsSurfaceNight
    val borderCol = if (isLight) MapsBorderDay else MapsBorderNight
    val blueCol = if (isLight) MapsBlueDay else MapsBlueNight
    val textCol = if (isLight) MapsTextDay else MapsTextNight
    val directionsBg = if (isLight) MapsDirectionsTealDay else MapsDirectionsTealNight

    val mainFabSize = if (compact) 46.dp else 52.dp
    val fabRadius = if (compact) 14.dp else 16.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(fabRadius),
            color = surfaceBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, borderCol),
            modifier = Modifier.size(mainFabSize)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onRecenter)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.nav_center_position),
                    tint = if (isFollowingGps) blueCol else textCol,
                    modifier = Modifier.size(if (compact) 22.dp else 24.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(fabRadius),
            color = directionsBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, borderCol.copy(alpha = 0.45f)),
            modifier = Modifier.size(mainFabSize)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onOpenRoutePlanning)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = stringResource(R.string.nav_plan_route),
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 23.dp else 26.dp)
                )
            }
        }
    }
}

@Composable
internal fun IdleLocationBottomSheet(
    placeLabel: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) MapsSurfaceDay else MapsSurfaceNight
    val borderCol = if (isLight) MapsBorderDay else MapsBorderNight
    val handleCol = if (isLight) MapsHandleDay else MapsHandleNight
    val textCol = if (isLight) MapsTextDay else MapsTextNight

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = surfaceBg,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 8.dp,
                    bottom = if (compact) 10.dp else 14.dp,
                    start = 20.dp,
                    end = 20.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(handleCol)
            )
            Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))
            Text(
                text = placeLabel.ifBlank { stringResource(R.string.nav_current_position) },
                color = textCol,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
