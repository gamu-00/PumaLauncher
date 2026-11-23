package com.openlauncher.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors

private val FabSurfaceDay = Color(0xFFFFFFFF)
private val FabSurfaceNight = Color(0xFF1E1F20)
private val FabBorderDay = Color(0xFFDADCE0)
private val FabBorderNight = Color(0xFF3C4043)
private val FabIconDay = Color(0xFF202124)
private val FabIconNight = Color(0xFFE8EAED)
private val RecenterBlueDay = Color(0xFF1A73E8)
private val RecenterBlueNight = Color(0xFF8AB4F8)

@Composable
internal fun ActiveNavRightFabs(
    cameraBearingProvider: () -> Float,
    voiceGuidanceEnabled: Boolean,
    compact: Boolean,
    onResetNorth: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleVoiceGuidance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) FabSurfaceDay else FabSurfaceNight
    val borderCol = if (isLight) FabBorderDay else FabBorderNight
    val iconCol = if (isLight) FabIconDay else FabIconNight
    val compassPivotCol = if (isLight) Color.White else FabSurfaceNight

    val compassDesc = stringResource(R.string.nav_compass_reset)
    val searchDesc = stringResource(R.string.nav_search_content_desc)
    val voiceDesc = stringResource(
        if (voiceGuidanceEnabled) R.string.nav_mute_voice else R.string.nav_unmute_voice
    )

    val fabSize = if (compact) 42.dp else 48.dp
    val fabIconSize = if (compact) 20.dp else 24.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(end = if (compact) 10.dp else 12.dp)
            .zIndex(10f)
    ) {
        // 1. Compass (red/gray needle)
        Surface(
            shape = CircleShape,
            color = surfaceBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, borderCol),
            modifier = Modifier
                .size(fabSize)
                .semantics { contentDescription = compassDesc }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onResetNorth)
            ) {
                Canvas(
                    modifier = Modifier
                        .size(fabIconSize)
                        .graphicsLayer { rotationZ = -cameraBearingProvider() }
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val tipOffset = size.height * 0.42f
                    val halfWidth = size.width * 0.17f

                    val northPath = Path().apply {
                        moveTo(cx, cy - tipOffset)
                        lineTo(cx + halfWidth, cy)
                        lineTo(cx - halfWidth, cy)
                        close()
                    }
                    drawPath(northPath, color = Color(0xFFEA4335))

                    val southPath = Path().apply {
                        moveTo(cx, cy + tipOffset)
                        lineTo(cx + halfWidth, cy)
                        lineTo(cx - halfWidth, cy)
                        close()
                    }
                    drawPath(southPath, color = Color(0xFF9AA0A6))

                    drawCircle(
                        color = compassPivotCol,
                        radius = size.minDimension * 0.07f
                    )
                }
            }
        }

        // 2. Search (magnifying glass)
        Surface(
            shape = CircleShape,
            color = surfaceBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, borderCol),
            modifier = Modifier.size(fabSize)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onSearchClick)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = searchDesc,
                    tint = iconCol,
                    modifier = Modifier.size(fabIconSize)
                )
            }
        }

        // 3. Audio/Mute (speaker)
        Surface(
            shape = CircleShape,
            color = surfaceBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, borderCol),
            modifier = Modifier.size(fabSize)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onToggleVoiceGuidance)
            ) {
                Icon(
                    imageVector = if (voiceGuidanceEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = voiceDesc,
                    tint = iconCol,
                    modifier = Modifier.size(fabIconSize)
                )
            }
        }
    }
}

@Composable
internal fun RecenterPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) FabSurfaceDay else FabSurfaceNight
    val borderCol = if (isLight) FabBorderDay else FabBorderNight
    val blue = if (isLight) RecenterBlueDay else RecenterBlueNight

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surfaceBg,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NearMe,
                contentDescription = stringResource(R.string.nav_recenter),
                tint = blue,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.nav_recenter),
                color = blue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
