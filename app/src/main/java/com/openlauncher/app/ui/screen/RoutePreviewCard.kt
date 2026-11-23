package com.openlauncher.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
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

private val PreviewBlueDay = Color(0xFF1A73E8)
private val PreviewBlueNight = Color(0xFF8AB4F8)
private val PreviewBlueTintDay = Color(0xFFE8F0FE)
private val PreviewBlueTintNight = Color(0xFF1E3A5F)
private val PreviewSurfaceDay = Color(0xFFFFFFFF)
private val PreviewSurfaceNight = Color(0xFF1E1F20)
private val PreviewTextDay = Color(0xFF202124)
private val PreviewTextNight = Color(0xFFE8EAED)
private val PreviewBorderDay = Color(0xFFDADCE0)
private val PreviewBorderNight = Color(0xFF3C4043)
private val PreviewPinRed = Color(0xFFEA4335)
private val PreviewDangerDay = Color(0xFFD93025)
private val PreviewDangerNight = Color(0xFFF28B82)

@Composable
internal fun RoutePreviewCard(
    destination: PhotonPlace?,
    waypoints: List<PhotonPlace> = emptyList(),
    options: List<RoutePreviewOption>,
    selectedIndex: Int,
    isRouting: Boolean,
    routeError: String?,
    @Suppress("UNUSED_PARAMETER") accent: Color,
    @Suppress("UNUSED_PARAMETER") onAccent: Color,
    compact: Boolean,
    onSelectOption: (Int) -> Unit,
    onRemoveWaypoint: (Int) -> Unit = {},
    onAddWaypointClick: () -> Unit = {},
    onStartNavigation: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) PreviewSurfaceDay else PreviewSurfaceNight
    val textPrimary = if (isLight) PreviewTextDay else PreviewTextNight
    val borderCol = if (isLight) PreviewBorderDay else PreviewBorderNight
    val blueAccent = if (isLight) PreviewBlueDay else PreviewBlueNight
    val blueChipBg = if (isLight) PreviewBlueTintDay else PreviewBlueTintNight
    val dangerCol = if (isLight) PreviewDangerDay else PreviewDangerNight
    val fastestTimeSec = options.firstOrNull()?.route?.totalTimeSec ?: 0.0

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = surfaceBg,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = PreviewPinRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = destination?.name ?: stringResource(R.string.nav_default_destination),
                    color = textPrimary,
                    fontSize = if (compact) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(blueChipBg)
                        .clickable(onClick = onAddWaypointClick)
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLocationAlt,
                        contentDescription = stringResource(R.string.nav_add_stop_chip),
                        tint = blueAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.nav_add_stop_chip),
                        color = blueAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = surfaceBg,
                    border = BorderStroke(1.dp, borderCol),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onCancel)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.nav_preview_cancel),
                            tint = textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (waypoints.isNotEmpty()) {
                WaypointsChipStrip(
                    waypoints = waypoints,
                    onRemoveWaypoint = onRemoveWaypoint
                )
            }

            when {
                isRouting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = blueAccent,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.nav_preview_calculating),
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                routeError != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = dangerCol,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = routeError,
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                options.isNotEmpty() -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        options.forEachIndexed { idx, option ->
                            RouteOptionCardItem(
                                option = option,
                                isSelected = idx == selectedIndex,
                                fastestTimeSec = fastestTimeSec,
                                compact = compact,
                                onClick = { onSelectOption(idx) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val btnHeight = if (compact) 42.dp else 48.dp
                        val pillShape = RoundedCornerShape(24.dp)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(btnHeight)
                                .clip(pillShape)
                                .background(PreviewBlueDay)
                                .clickable(onClick = onStartNavigation)
                                .padding(horizontal = 18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.nav_start_cta),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(btnHeight)
                                .clip(pillShape)
                                .background(surfaceBg)
                                .border(1.dp, borderCol, pillShape)
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 20.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.nav_preview_cancel),
                                color = blueAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
