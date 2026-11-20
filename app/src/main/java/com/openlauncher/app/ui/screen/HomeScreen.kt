package com.openlauncher.app.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    var telemetryExpanded by remember { mutableStateOf(false) }
    val speedKmh by navigationEngine.currentSpeedKmh.collectAsStateWithLifecycle()

    // NavLab: Fullscreen navigation map with accessible GPS telemetry & test controls HUD
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = outerPad, bottom = outerPad, end = outerPad)
    ) {
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
            uiScale = effectiveUiScale,
            modifier = Modifier.fillMaxSize()
        )

        NavLabTelemetryHud(
            engine = navigationEngine,
            speedKmh = speedKmh,
            accent = accent,
            expanded = telemetryExpanded,
            onToggleExpanded = { telemetryExpanded = !telemetryExpanded },
            onToggleAvoidTolls = {
                val nextVal = !navigationEngine.avoidTolls
                uiScope.launch { settingsRepo.updateSettings { it.copy(avoidTolls = nextVal) } }
                navigationEngine.updateAvoidTolls(nextVal, recalculateIfActive = true)
            },
            onToggleVoiceGuidance = {
                val nextVoice = !navigationEngine.voiceGuidanceEnabled
                navigationEngine.setVoiceGuidance(nextVoice)
                uiScope.launch { settingsRepo.updateSettings { it.copy(voiceGuidanceEnabled = nextVoice) } }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isCompactHeight) 8.dp else 12.dp)
                .zIndex(12f)
        )
    }
}

@Composable
private fun NavLabTelemetryHud(
    engine: NavigationEngine,
    speedKmh: Int,
    accent: Color,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleAvoidTolls: () -> Unit,
    onToggleVoiceGuidance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    val fixColor = if (engine.hasGpsFix) Color(0xFF22C55E) else Color(0xFFF59E0B)
    val latText = String.format(java.util.Locale.US, "%.5f", engine.currentLat)
    val lonText = String.format(java.util.Locale.US, "%.5f", engine.currentLon)
    val bearingText = "${engine.currentBearing.roundToInt()}°"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glassPillSurface)
            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(fixColor)
            )
            Icon(
                imageVector = if (engine.hasGpsFix) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                contentDescription = null,
                tint = fixColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (engine.hasGpsFix) "GPS FIX" else "NO FIX",
                color = fixColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• $speedKmh km/h • HDG $bearingText",
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            Text(
                text = "LAT $latText  LON $lonText  |  ${if (engine.isMoving) "MOVING" else "STATIC"}  |  ${if (engine.isFollowingGps) "FOLLOW" else "FREE"}",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            val routeSummary = if (engine.activeRoute != null) {
                "ROUTE: ${engine.remainingDistanceText} • ETA ${engine.etaText}"
            } else {
                "ROUTE: IDLE"
            }
            Text(
                text = routeSummary,
                color = if (engine.activeRoute != null) accent else colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavLabChip(
                    label = if (engine.avoidTolls) "NO TOLLS: ON" else "NO TOLLS: OFF",
                    active = engine.avoidTolls,
                    accent = accent,
                    onClick = onToggleAvoidTolls
                )
                NavLabChip(
                    label = if (engine.voiceGuidanceEnabled) "TTS: ON" else "TTS: OFF",
                    active = engine.voiceGuidanceEnabled,
                    accent = accent,
                    onClick = onToggleVoiceGuidance
                )
                if (engine.activeRoute != null) {
                    NavLabChip(
                        label = "STOP NAV",
                        active = true,
                        accent = colors.dangerContent,
                        onClick = { engine.stopNavigation() }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavLabChip(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val colors = LocalCockpitColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.22f) else colors.elevatedSurface)
            .border(1.dp, if (active) accent else colors.hairlineBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (active) accent else colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
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
