package com.openlauncher.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.R
import com.openlauncher.app.data.DrivingAvatar
import com.openlauncher.app.ui.theme.LocalCockpitColors

private val GoogleSheetSurfaceDay = Color(0xFFFFFFFF)
private val GoogleSheetSurfaceNight = Color(0xFF1E1F20)
private val GoogleSheetBtnNight = Color(0xFF282A2C)
private val GoogleSheetBorderDay = Color(0xFFDADCE0)
private val GoogleSheetBorderNight = Color(0xFF3C4043)
private val GoogleSheetIconDay = Color(0xFF202124)
private val GoogleSheetIconNight = Color(0xFFE8EAED)
private val GoogleSheetDurationGreenDay = Color(0xFF188038)
private val GoogleSheetDurationGreenNight = Color(0xFF81C995)
private val GoogleSheetDurationOrangeDay = Color(0xFFE37400)
private val GoogleSheetDurationOrangeNight = Color(0xFFFDD663)
private val GoogleSheetSubtitleDay = Color(0xFF5F6368)
private val GoogleSheetSubtitleNight = Color(0xFF9AA0A6)

@Composable
internal fun TripSummarySheet(
    durationText: String,
    distanceText: String,
    arrivalTimeText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    nav3dViewEnabled: Boolean,
    drivingAvatar: DrivingAvatar,
    isTrafficDelayed: Boolean = false,
    compact: Boolean = false,
    onCloseNavigation: () -> Unit,
    onOpenRouteOverview: () -> Unit,
    onAddWaypointClick: () -> Unit,
    onToggleNav3dView: (Boolean) -> Unit,
    onSelectDrivingAvatar: (DrivingAvatar) -> Unit,
    onVoiceAssistantClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) GoogleSheetSurfaceDay else GoogleSheetSurfaceNight
    val borderCol = if (isLight) GoogleSheetBorderDay else GoogleSheetBorderNight
    val durationGreen = if (isLight) GoogleSheetDurationGreenDay else GoogleSheetDurationGreenNight
    val durationOrange = if (isLight) GoogleSheetDurationOrangeDay else GoogleSheetDurationOrangeNight
    val subtitleCol = if (isLight) GoogleSheetSubtitleDay else GoogleSheetSubtitleNight

    val verticalPad = if (compact) 8.dp else 12.dp
    val subtitle = listOf(distanceText, arrivalTimeText)
        .filter { it.isNotBlank() }
        .joinToString(" • ")

    val dragThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        dragTotalPx += delta
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = surfaceBg,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {}
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Drag handle + Header row (captures swipe-up/down & tap before MapView)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { dragTotalPx = 0f },
                        onDragStopped = { velocity ->
                            when {
                                dragTotalPx < -dragThresholdPx || velocity < -240f -> onExpandedChange(true)
                                dragTotalPx > dragThresholdPx || velocity > 240f -> onExpandedChange(false)
                            }
                            dragTotalPx = 0f
                        }
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExpandedChange(!expanded) }
                        .padding(top = 8.dp, bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(borderCol)
                    )
                }

                val btnSize = if (compact) 42.dp else 48.dp
                val btnIconSize = if (compact) 20.dp else 24.dp

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (compact) 12.dp else 16.dp,
                            end = if (compact) 12.dp else 16.dp,
                            top = 2.dp,
                            bottom = verticalPad
                        )
                ) {
                    SheetCircleButton(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.nav_end),
                        size = btnSize,
                        iconSize = btnIconSize,
                        onClick = onCloseNavigation
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onExpandedChange(!expanded) }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = durationText.ifBlank { "--" },
                            color = if (isTrafficDelayed) durationOrange else durationGreen,
                            fontSize = if (compact) 22.sp else 26.sp,
                            lineHeight = if (compact) 24.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = subtitleCol,
                                fontSize = if (compact) 13.sp else 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    GeminiVoiceAssistantButton(
                        size = btnSize,
                        iconSize = if (compact) 19.dp else 22.dp,
                        shadowElevation = 0.dp,
                        onClick = {
                            onVoiceAssistantClick()
                            launchGeminiVoiceAssistant(context)
                        }
                    )

                    SheetCircleButton(
                        icon = Icons.Default.AltRoute,
                        contentDescription = stringResource(R.string.nav_preview_alternative),
                        size = btnSize,
                        iconSize = btnIconSize,
                        onClick = onOpenRouteOverview
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                TripSheetExpandedOptions(
                    nav3dViewEnabled = nav3dViewEnabled,
                    drivingAvatar = drivingAvatar,
                    compact = compact,
                    onAddWaypointClick = onAddWaypointClick,
                    onToggleNav3dView = onToggleNav3dView,
                    onSelectDrivingAvatar = onSelectDrivingAvatar
                )
            }
        }
    }
}

@Composable
private fun SheetCircleButton(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: () -> Unit
) {
    val isLight = LocalCockpitColors.current.isLight
    val btnBg = if (isLight) GoogleSheetSurfaceDay else GoogleSheetBtnNight
    val borderCol = if (isLight) GoogleSheetBorderDay else GoogleSheetBorderNight
    val iconTint = if (isLight) GoogleSheetIconDay else GoogleSheetIconNight

    Surface(
        shape = CircleShape,
        color = btnBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = Modifier.size(size)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
