package com.openlauncher.app.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.MAX_MEDIA_PAUSE_TIMEOUT_MINUTES
import com.openlauncher.app.data.MIN_MEDIA_PAUSE_TIMEOUT_MINUTES
import com.openlauncher.app.data.SettingsRepository
import com.openlauncher.app.data.WeatherRepository
import com.openlauncher.app.data.effectiveUiScale
import com.openlauncher.app.data.resolvedAccentColor
import com.openlauncher.app.mediastation.MediaStationCard
import com.openlauncher.app.mediastation.MediaStationState
import com.openlauncher.app.mediastation.ui.MediaStationViewModel
import com.openlauncher.app.mediastation.ui.MoviesScreen
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.ui.components.CockpitCardShape
import com.openlauncher.app.ui.theme.*
import com.openlauncher.app.ui.widget.NowPlayingWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val WEATHER_TRIGGER_DELAY_MS = 15_000L
private const val WEATHER_TIMER_POLL_MS = 500L

enum class DashboardLayoutMode {
    PORTRAIT,
    LANDSCAPE_STANDARD,
    ULTRAWIDE
}

fun resolveDashboardLayoutMode(widthDp: Dp, heightDp: Dp): DashboardLayoutMode {
    val aspectRatio = if (heightDp > 0.dp) widthDp / heightDp else 1.77f
    return when {
        aspectRatio < 1.1f -> DashboardLayoutMode.PORTRAIT
        aspectRatio >= 2.1f -> DashboardLayoutMode.ULTRAWIDE
        else -> DashboardLayoutMode.LANDSCAPE_STANDARD
    }
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
    locationPermissionTrigger: Int = 0,
    navigationEngine: NavigationEngine,
    isHomeVisible: Boolean = true,
    onMapSearchOpenChanged: (Boolean) -> Unit = {},
    isCompactHeight: Boolean = LocalPhysicalScreenMetrics.current.isCompactHeight,
    layoutMode: DashboardLayoutMode = resolveDashboardLayoutMode(
        LocalPhysicalScreenMetrics.current.screenWidthDp,
        LocalPhysicalScreenMetrics.current.screenHeightDp
    ),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalCockpitColors.current
    val accent = settings.resolvedAccentColor(colors.isLight)
    val settingsRepo = remember(context) { SettingsRepository(context.applicationContext) }
    val uiScope = rememberCoroutineScope()

    val weatherData by WeatherRepository.weatherState.collectAsStateWithLifecycle()
    val weatherLoading by WeatherRepository.isLoading.collectAsStateWithLifecycle()
    val moviesUiState by mediaStationViewModel.uiState.collectAsStateWithLifecycle()
    val youTubeUiState by mediaStationViewModel.youTubeState.collectAsStateWithLifecycle()
    val isVideoPlayingRaw by mediaStationViewModel.isVideoPlaying.collectAsStateWithLifecycle()

    val isVideoPlayerOpen = (mediaStationState == MediaStationState.MOVIES && moviesUiState.screen == MoviesScreen.PLAYER) ||
        (mediaStationState == MediaStationState.YOUTUBE && youTubeUiState.activeStream != null)
    val isVideoActivelyPlaying = isVideoPlayerOpen && isVideoPlayingRaw
    val isMediaHubEligible = mediaStationState == MediaStationState.IDLE && !isVideoPlayerOpen && !isVideoPlayingRaw
    val isMusicActivelyPlaying = nowPlaying?.controller != null && nowPlaying.title.isNotBlank() && nowPlaying.isPlaying

    val clampedPauseTimeoutMinutes = settings.mediaPauseTimeoutMinutes.coerceIn(
        MIN_MEDIA_PAUSE_TIMEOUT_MINUTES,
        MAX_MEDIA_PAUSE_TIMEOUT_MINUTES
    )
    val latestPauseTimeoutMs = rememberUpdatedState(clampedPauseTimeoutMinutes * 60_000L)
    val latestOnMediaStationStateChange by rememberUpdatedState(onMediaStationStateChange)

    var videoWeatherTriggered by remember { mutableStateOf(false) }
    var videoWeatherDismissed by remember { mutableStateOf(false) }
    val latestIsVideoPlaying = rememberUpdatedState(isVideoActivelyPlaying)

    LaunchedEffect(isVideoPlayerOpen) {
        if (!isVideoPlayerOpen) {
            videoWeatherTriggered = false
            videoWeatherDismissed = false
            return@LaunchedEffect
        }
        runPlaybackWeatherTimer(
            isPlayingProvider = { latestIsVideoPlaying.value },
            pauseTimeoutMsProvider = { latestPauseTimeoutMs.value },
            onTriggeredChanged = { videoWeatherTriggered = it },
            onDismissedReset = { videoWeatherDismissed = false },
            onPauseTimeoutExpired = {
                mediaStationViewModel.resetAllToIdle()
                latestOnMediaStationStateChange(MediaStationState.IDLE)
            }
        )
    }

    var musicWeatherTriggered by remember { mutableStateOf(false) }
    var musicWeatherDismissed by remember { mutableStateOf(false) }
    val latestIsMusicPlaying = rememberUpdatedState(isMusicActivelyPlaying)

    LaunchedEffect(isMediaHubEligible) {
        if (!isMediaHubEligible) {
            musicWeatherTriggered = false
            musicWeatherDismissed = false
            return@LaunchedEffect
        }
        runPlaybackWeatherTimer(
            isPlayingProvider = { latestIsMusicPlaying.value },
            pauseTimeoutMsProvider = { latestPauseTimeoutMs.value },
            onTriggeredChanged = { musicWeatherTriggered = it },
            onDismissedReset = { musicWeatherDismissed = false }
        )
    }

    val showWeatherInNowPlaying = isVideoPlayerOpen && videoWeatherTriggered && !videoWeatherDismissed
    val showWeatherInMediaHub = isMediaHubEligible && musicWeatherTriggered && !musicWeatherDismissed

    val refreshWeather: (Boolean) -> Unit = { force ->
        val lat = navigationEngine.currentLat
        val lon = navigationEngine.currentLon
        uiScope.launch {
            WeatherRepository.refreshIfNeeded(
                lat = lat,
                lon = lon,
                forceRefresh = force
            )
        }
    }

    val hasGpsFix = navigationEngine.hasGpsFix
    LaunchedEffect(navigationEngine, hasGpsFix) {
        while (true) {
            val lat = navigationEngine.currentLat
            val lon = navigationEngine.currentLon
            WeatherRepository.refreshIfNeeded(lat, lon, forceRefresh = false)
            delay(60_000L)
        }
    }

    LaunchedEffect(showWeatherInNowPlaying, showWeatherInMediaHub) {
        if (showWeatherInNowPlaying || showWeatherInMediaHub) {
            val lat = navigationEngine.currentLat
            val lon = navigationEngine.currentLon
            WeatherRepository.refreshIfNeeded(lat, lon, forceRefresh = false)
        }
    }

    val physicalMetrics = LocalPhysicalScreenMetrics.current
    val baseDensity = physicalMetrics.baseDensity
    val effectiveUiScale = settings.effectiveUiScale()
    val physicalScreenScale = (minOf(physicalMetrics.screenHeightDp.value, physicalMetrics.screenWidthDp.value) / 480f)
        .coerceIn(0.75f, 1.30f)
    val outerPadPhysical = if (isCompactHeight) (4f * physicalScreenScale).dp.coerceAtLeast(4.dp) else (6f * physicalScreenScale).dp
    val gapPhysical = if (isCompactHeight) (6f * physicalScreenScale).dp.coerceAtLeast(6.dp) else (10f * physicalScreenScale).dp
    val outerPad = (outerPadPhysical.value / effectiveUiScale).dp
    val gapPx = with(baseDensity) { gapPhysical.roundToPx() }
    val maxRightPanelPhysical = when (layoutMode) {
        DashboardLayoutMode.ULTRAWIDE -> 460.dp
        DashboardLayoutMode.LANDSCAPE_STANDARD -> 500.dp
        DashboardLayoutMode.PORTRAIT -> physicalMetrics.screenWidthDp
    }
    val maxRightPanelScaled = (maxRightPanelPhysical.value / effectiveUiScale).dp
    val maxRightPanelPx = with(baseDensity) { maxRightPanelPhysical.roundToPx() }
    val moduleShape = CockpitCardShape

    Layout(
        modifier = modifier
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
                locationPermissionTrigger = locationPermissionTrigger,
                navigationEngine = navigationEngine,
                isHomeVisible = isHomeVisible,
                onSearchOverlayChanged = onMapSearchOpenChanged,
                uiScale = effectiveUiScale
            )

            // Child 1: Top/Upper Media Station Card (Movies & TV / YouTube / Contextual Weather)
            MediaStationCard(
                state = mediaStationState,
                onStateChange = onMediaStationStateChange,
                mediaStationViewModel = mediaStationViewModel,
                showWeather = showWeatherInMediaHub,
                weather = weatherData,
                isWeatherLoading = weatherLoading,
                accent = accent,
                onRefreshWeather = { refreshWeather(true) },
                onDismissWeather = { musicWeatherDismissed = true },
                mediaPauseTimeoutMinutes = clampedPauseTimeoutMinutes,
                onMediaPauseTimeoutChange = { minutes ->
                    val clamped = minutes.coerceIn(
                        MIN_MEDIA_PAUSE_TIMEOUT_MINUTES,
                        MAX_MEDIA_PAUSE_TIMEOUT_MINUTES
                    )
                    uiScope.launch {
                        settingsRepo.updateSettings { it.copy(mediaPauseTimeoutMinutes = clamped) }
                    }
                },
                isCompactHeight = isCompactHeight,
                isHomeVisible = isHomeVisible,
                cardShape = moduleShape,
                modifier = Modifier.widthIn(max = maxRightPanelScaled)
            )

            // Child 2: Bottom Music Card (NowPlayingWidget / Contextual Weather)
            Box(
                modifier = Modifier
                    .widthIn(max = maxRightPanelScaled)
                    .clip(moduleShape)
                    .background(colors.cardSurface)
                    .border(1.dp, colors.cardBorderBrush, moduleShape)
            ) {
                NowPlayingWidget(
                    state = nowPlaying,
                    accent = accent,
                    showWeather = showWeatherInNowPlaying,
                    weather = weatherData,
                    isWeatherLoading = weatherLoading,
                    onRefreshWeather = { refreshWeather(true) },
                    onDismissWeather = { videoWeatherDismissed = true },
                    isCompactHeight = isCompactHeight,
                    homePressTrigger = homePressTrigger,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) { measurables, constraints ->
        val totalW = constraints.maxWidth
        val totalH = constraints.maxHeight

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
                val rawRightW = (availW * (1f - mapFraction)).roundToInt()
                val rightW = rawRightW.coerceAtMost(maxRightPanelPx).coerceIn(1, (availW - 1).coerceAtLeast(1))
                val mapW = (availW - rightW).coerceAtLeast(1)
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

/**
 * Accumulates active playback time up to [WEATHER_TRIGGER_DELAY_MS] while keeping paused media
 * and contextual weather alive up to the configured timeout before resetting back to idle.
 */
private suspend fun runPlaybackWeatherTimer(
    isPlayingProvider: () -> Boolean,
    pauseTimeoutMsProvider: () -> Long,
    onTriggeredChanged: (Boolean) -> Unit,
    onDismissedReset: () -> Unit,
    onPauseTimeoutExpired: () -> Unit = {}
) {
    var activeAccumMs = 0L
    var pausedAccumMs = 0L
    var triggered = false
    var lastTickMs = SystemClock.elapsedRealtime()

    while (true) {
        if (isPlayingProvider()) {
            if (!triggered) {
                val now = SystemClock.elapsedRealtime()
                val delta = (now - lastTickMs).coerceIn(0L, 2_000L)
                lastTickMs = now
                pausedAccumMs = 0L
                activeAccumMs += delta
                if (activeAccumMs >= WEATHER_TRIGGER_DELAY_MS) {
                    triggered = true
                    onTriggeredChanged(true)
                } else {
                    delay(WEATHER_TIMER_POLL_MS)
                }
            } else {
                pausedAccumMs = 0L
                snapshotFlow { isPlayingProvider() }.first { !it }
                lastTickMs = SystemClock.elapsedRealtime()
            }
        } else {
            val now = SystemClock.elapsedRealtime()
            if (activeAccumMs == 0L && !triggered) {
                snapshotFlow { isPlayingProvider() }.first { it }
                lastTickMs = SystemClock.elapsedRealtime()
                pausedAccumMs = 0L
            } else {
                val rawDelta = (now - lastTickMs).coerceAtLeast(0L)
                lastTickMs = now
                pausedAccumMs += rawDelta
                if (pausedAccumMs >= pauseTimeoutMsProvider()) {
                    activeAccumMs = 0L
                    pausedAccumMs = 0L
                    if (triggered) {
                        triggered = false
                        onTriggeredChanged(false)
                    }
                    onDismissedReset()
                    onPauseTimeoutExpired()
                } else {
                    delay(WEATHER_TIMER_POLL_MS)
                }
            }
        }
    }
}
