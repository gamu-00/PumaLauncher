package com.openlauncher.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.SettingsRepository
import com.openlauncher.app.data.resolvedAccentColor
import com.openlauncher.app.mediastation.MediaStationCard
import com.openlauncher.app.mediastation.MediaStationState
import com.openlauncher.app.mediastation.ui.MediaStationViewModel
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.ui.components.CockpitCardShape
import com.openlauncher.app.ui.theme.*
import com.openlauncher.app.ui.widget.NowPlayingWidget
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DashboardLayoutMode {
    PORTRAIT,
    LANDSCAPE_STANDARD,
    ULTRAWIDE
}

/**
 * Single-activity universal adaptive layout preserving active Map, ExoPlayer, and MediaSession
 * instances across all aspect ratios (Portrait < 1.1, Landscape 1.1..2.1, Ultrawide >= 2.1).
 */
@Composable
fun HomeScreen(
    settings: AppSettings,
    nowPlaying: NowPlayingState?,
    mediaStationState: MediaStationState,
    mediaStationViewModel: MediaStationViewModel,
    onMediaStationStateChange: (MediaStationState) -> Unit,
    homePressTrigger: Int = 0,
    closeMapSearchTrigger: Int = 0,
    isHomeVisible: Boolean = true,
    onMapSearchOpenChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = settings.resolvedAccentColor()
    val colors = LocalCockpitColors.current
    val settingsRepo = remember(context) { SettingsRepository(context.applicationContext) }
    val uiScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val aspectRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 1.77f
        val isCompactHeight = maxHeight < 520.dp
        val layoutMode = when {
            aspectRatio < 1.1f -> DashboardLayoutMode.PORTRAIT
            aspectRatio >= 2.1f -> DashboardLayoutMode.ULTRAWIDE
            else -> DashboardLayoutMode.LANDSCAPE_STANDARD
        }

        val uiScale = (minOf(maxHeight.value, maxWidth.value) / 480f).coerceIn(0.75f, 1.30f)
        val outerPad = if (isCompactHeight) (4f * uiScale).dp.coerceAtLeast(4.dp) else (6f * uiScale).dp
        val gap = if (isCompactHeight) (6f * uiScale).dp.coerceAtLeast(6.dp) else (10f * uiScale).dp
        val moduleShape = CockpitCardShape

        Layout(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = outerPad, bottom = outerPad, end = outerPad),
            content = {
                // Child 0: Native MapLibre Turn-by-Turn Navigator
                NativeNavigatorPanel(
                    accent = accent,
                    accentColorHex = settings.accentColorHex,
                    avoidTolls = settings.avoidTolls,
                    voiceGuidanceEnabled = settings.voiceGuidanceEnabled,
                    onToggleAvoidTolls = { enabled ->
                        uiScope.launch {
                            settingsRepo.updateSettings { it.copy(avoidTolls = enabled) }
                        }
                    },
                    onToggleVoiceGuidance = { enabled ->
                        uiScope.launch {
                            settingsRepo.updateSettings { it.copy(voiceGuidanceEnabled = enabled) }
                        }
                    },
                    isCompactHeight = isCompactHeight,
                    moduleShape = moduleShape,
                    homePressTrigger = homePressTrigger,
                    closeSearchTrigger = closeMapSearchTrigger,
                    onSearchOverlayChanged = onMapSearchOpenChanged
                )

                // Child 1: Top/Upper Media Station Card (Movies & TV / YouTube)
                MediaStationCard(
                    state = mediaStationState,
                    onStateChange = onMediaStationStateChange,
                    mediaStationViewModel = mediaStationViewModel,
                    isCompactHeight = isCompactHeight,
                    isHomeVisible = isHomeVisible,
                    cardShape = moduleShape
                )

                // Child 2: Bottom Music Card (NowPlayingWidget)
                Box(
                    modifier = Modifier
                        .clip(moduleShape)
                        .background(colors.cardSurface)
                        .border(1.dp, colors.cardBorderBrush, moduleShape)
                ) {
                    NowPlayingWidget(
                        state = nowPlaying,
                        accent = accent,
                        isCompactHeight = isCompactHeight,
                        homePressTrigger = homePressTrigger,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        ) { measurables, constraints ->
            val totalW = constraints.maxWidth
            val totalH = constraints.maxHeight
            val gapPx = gap.roundToPx()

            when (layoutMode) {
                DashboardLayoutMode.PORTRAIT -> {
                    val availH = (totalH - gapPx).coerceAtLeast(2)
                    val mapH = (availH * 0.54f).roundToInt()
                    val mediaAreaH = (availH - mapH).coerceAtLeast(2)
                    val mediaAvailH = (mediaAreaH - gapPx).coerceAtLeast(2)
                    val stationH = (mediaAvailH * 0.58f).roundToInt()
                    val nowPlayingH = (mediaAvailH - stationH).coerceAtLeast(1)

                    val mapPlaceable = measurables[0].measure(Constraints.fixed(totalW, mapH))
                    val stationPlaceable = measurables[1].measure(Constraints.fixed(totalW, stationH))
                    val nowPlayingPlaceable = measurables[2].measure(Constraints.fixed(totalW, nowPlayingH))

                    layout(totalW, totalH) {
                        mapPlaceable.placeRelative(0, 0)
                        val mediaTopY = mapH + gapPx
                        stationPlaceable.placeRelative(0, mediaTopY)
                        nowPlayingPlaceable.placeRelative(0, mediaTopY + stationH + gapPx)
                    }
                }

                DashboardLayoutMode.ULTRAWIDE,
                DashboardLayoutMode.LANDSCAPE_STANDARD -> {
                    val mapFraction = if (layoutMode == DashboardLayoutMode.ULTRAWIDE) 0.62f else 0.50f
                    val availW = (totalW - gapPx).coerceAtLeast(2)
                    val mapW = (availW * mapFraction).roundToInt()
                    val rightW = (availW - mapW).coerceAtLeast(1)
                    val rightAvailH = (totalH - gapPx).coerceAtLeast(2)
                    val stationH = (rightAvailH * 0.58f).roundToInt()
                    val nowPlayingH = (rightAvailH - stationH).coerceAtLeast(1)

                    val mapPlaceable = measurables[0].measure(Constraints.fixed(mapW, totalH))
                    val stationPlaceable = measurables[1].measure(Constraints.fixed(rightW, stationH))
                    val nowPlayingPlaceable = measurables[2].measure(Constraints.fixed(rightW, nowPlayingH))

                    layout(totalW, totalH) {
                        mapPlaceable.placeRelative(0, 0)
                        val rightX = mapW + gapPx
                        stationPlaceable.placeRelative(rightX, 0)
                        nowPlayingPlaceable.placeRelative(rightX, stationH + gapPx)
                    }
                }
            }
        }
    }
}
