package com.openlauncher.app.mediastation.ui

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.openlauncher.app.R
import com.openlauncher.app.mediastation.data.PlayableStream
import com.openlauncher.app.ui.theme.AaAmber
import com.openlauncher.app.ui.theme.AaAmberAlt
import com.openlauncher.app.ui.theme.AaBorder
import com.openlauncher.app.ui.theme.AaPanel
import com.openlauncher.app.ui.theme.AaPanelHigh
import com.openlauncher.app.ui.theme.AaTextPrimary
import com.openlauncher.app.ui.theme.AaTextSecondary
import com.openlauncher.app.util.AppLanguageManager
import kotlinx.coroutines.delay
import java.util.Locale

internal data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val shortBadge: String,
    val isSelected: Boolean
)

@OptIn(UnstableApi::class)
@Composable
internal fun MediaPlayerView(
    title: String,
    streams: List<PlayableStream>,
    currentStreamIndex: Int,
    resumeOffsetMs: Long,
    isResolvingStreams: Boolean,
    streamError: String?,
    isCompactHeight: Boolean = false,
    isHomeVisible: Boolean = true,
    isYouTubeMode: Boolean = false,
    onCycleStream: () -> Unit,
    onStreamError: () -> Unit,
    onRetryResolve: () -> Unit,
    onSyncProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedStreamIndex by remember(streams) {
        mutableIntStateOf(currentStreamIndex.coerceIn(0, (streams.size - 1).coerceAtLeast(0)))
    }
    LaunchedEffect(currentStreamIndex) {
        if (streams.isNotEmpty()) {
            selectedStreamIndex = currentStreamIndex.coerceIn(0, streams.size - 1)
        }
    }

    val currentStream = streams.getOrNull(selectedStreamIndex)
    val streamUrl = currentStream?.url?.takeIf { it.isNotBlank() }

    val latestOnStreamError by rememberUpdatedState(onStreamError)
    val latestOnSyncProgress by rememberUpdatedState(onSyncProgress)
    val latestIsHomeVisible by rememberUpdatedState(isHomeVisible)

    var overlayVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var videoAspectRatio by remember(streams) { mutableFloatStateOf(16f / 9f) }
    var shouldPlay by remember(streams) { mutableStateOf(true) }
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var currentPositionMs by remember { mutableLongStateOf(resumeOffsetMs) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var pendingSeekMs by remember { mutableLongStateOf(resumeOffsetMs) }
    var initialSeekApplied by remember { mutableStateOf(false) }
    var failedStreamKey by remember { mutableStateOf<String?>(null) }
    var playerErrorDetail by remember(streams) { mutableStateOf<String?>(null) }
    var audioTrackOverrideApplied by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var showAudioSelector by remember { mutableStateOf(false) }
    var showSourceSelector by remember { mutableStateOf(false) }

    val preferredAudioCodes = remember(context) {
        preferredAudioLanguageCodes(AppLanguageManager.getSavedLanguage(context))
    }

    val playerAudioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    DisposableEffect(lifecycleOwner) {
        isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isLifecycleResumed = true
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    isLifecycleResumed = false
                    shouldPlay = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isHomeVisible) {
        if (!isHomeVisible) {
            shouldPlay = false
        }
    }

    val hasStream = streamUrl != null
    val isPlayerActive = hasStream && streamError == null && isHomeVisible && isLifecycleResumed
    val exoPlayer = remember(context, isPlayerActive) {
        if (!isPlayerActive) {
            null
        } else {
            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
                .setAllowedVideoJoiningTimeMs(2_000L)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 45_000, 1_500, 1_000)
                .build()
            val defaultHttpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(30_000)
            ExoPlayer.Builder(context, renderersFactory)
                .setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
                .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(DefaultMediaSourceFactory(defaultHttpFactory))
                .build()
                .apply {
                    setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
                    videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setPreferredAudioLanguages(*preferredAudioCodes)
                        .build()
                }
        }
    }

    val playerView = remember(context) {
        (LayoutInflater.from(context)
            .inflate(R.layout.view_exo_texture_player, null, false) as PlayerView).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    val audioFocusRequest = remember(exoPlayer, audioManager, playerAudioAttributes) {
        val platformAttributes = PlatformAudioAttributes.Builder()
            .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
            .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        lateinit var focusRequest: AudioFocusRequest
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(platformAttributes)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        shouldPlay = false
                        val player = exoPlayer
                        if (player != null) {
                            player.playWhenReady = false
                            player.pause()
                            player.setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
                        }
                        audioManager?.abandonAudioFocusRequest(focusRequest)
                    }
                }
            }
            .build()
        focusRequest
    }

    val requestAudioFocus = remember(audioManager, audioFocusRequest) {
        { audioManager?.requestAudioFocus(audioFocusRequest) }
    }

    val abandonAudioFocus = remember(audioManager, audioFocusRequest) {
        { audioManager?.abandonAudioFocusRequest(audioFocusRequest) }
    }

    val pauseAndAbandonFocus: (ExoPlayer?) -> Unit = { player ->
        shouldPlay = false
        if (player != null) {
            player.playWhenReady = false
            player.pause()
            player.setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
        }
        abandonAudioFocus()
    }

    val latestOverlayVisible by rememberUpdatedState(overlayVisible)
    var loadedStreamKey by remember(exoPlayer) { mutableStateOf<String?>(null) }

    // Auto-hide overlay after 3 seconds of inactivity (keep visible while audio or source selector is open).
    LaunchedEffect(overlayVisible, interactionTick, showAudioSelector, showSourceSelector) {
        if (overlayVisible && !showAudioSelector && !showSourceSelector) {
            delay(3_000L)
            overlayVisible = false
        }
    }

    // Periodic UI position refresh while playing (keyed solely on exoPlayer so overlay/play toggles never restart it).
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.takeIf { it > 0L } ?: 0L
            currentPositionMs = pos
            totalDurationMs = dur
            if (pos > 0L) {
                pendingSeekMs = pos
            }
            delay(if (latestOverlayVisible) 500L else 1_500L)
        }
    }

    // Load or switch MediaSource only when exoPlayer, streamUrl, or selectedStreamIndex changes.
    LaunchedEffect(exoPlayer, streamUrl, selectedStreamIndex) {
        val player = exoPlayer ?: return@LaunchedEffect
        val stream = currentStream ?: return@LaunchedEffect
        val resolvedUrl = streamUrl ?: return@LaunchedEffect
        val streamKey = "$selectedStreamIndex:$resolvedUrl"
        if (loadedStreamKey == streamKey) return@LaunchedEffect
        loadedStreamKey = streamKey
        failedStreamKey = null
        audioTrackOverrideApplied = false
        audioTracks = emptyList()
        showAudioSelector = false
        showSourceSelector = false
        isBuffering = true

        val targetSeek = if (!initialSeekApplied && resumeOffsetMs > 0L) {
            initialSeekApplied = true
            resumeOffsetMs
        } else {
            pendingSeekMs
        }

        val customHeaders = stream.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(customHeaders)
        val sourceFactory = DefaultMediaSourceFactory(httpFactory)
        val audioUrl = stream.audioUrl?.takeIf { it.isNotBlank() }
        val mediaSource = if (audioUrl != null) {
            MergingMediaSource(
                sourceFactory.createMediaSource(MediaItem.fromUri(resolvedUrl)),
                sourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
            )
        } else {
            sourceFactory.createMediaSource(MediaItem.fromUri(resolvedUrl))
        }

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setPreferredAudioLanguages(*preferredAudioCodes)
            .build()
        player.setMediaSource(mediaSource)
        if (targetSeek > 0L) {
            player.seekTo(targetSeek)
        }
        player.prepare()
        if (shouldPlay && latestIsHomeVisible && isLifecycleResumed) {
            requestAudioFocus()
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
            player.setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
            abandonAudioFocus()
        }
    }

    // Attach Player.Listener, AnalyticsListener, and PlayerView binding; manage strict release on dispose.
    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        if (player == null) {
            playerView.player = null
            isPlaying = false
            isBuffering = false
            abandonAudioFocus()
            onDispose {
                playerView.player = null
                abandonAudioFocus()
            }
        } else {
            playerView.player = player
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoAspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    val activeAppLang = AppLanguageManager.getSavedLanguage(context)
                    val preferredShortTag = soberAudioBadge(activeAppLang)

                    val extracted = mutableListOf<AudioTrackOption>()
                    var optionCounter = 1
                    tracks.groups.forEachIndexed { groupIndex, group ->
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (trackIndex in 0 until group.length) {
                                val format = group.getTrackFormat(trackIndex)
                                val selected = group.isTrackSelected(trackIndex)
                                val isPreferred = matchesAudioLanguage(activeAppLang, format.language, format.label)
                                val rawLang = format.language?.trim()
                                    ?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
                                val rawLabel = format.label?.trim()?.takeIf { it.isNotEmpty() }
                                val codec = formatAudioCodec(format.sampleMimeType)

                                val langPart = when {
                                    isPreferred -> if (rawLabel != null && !rawLabel.equals(preferredShortTag, ignoreCase = true)) {
                                        "$preferredShortTag - $rawLabel"
                                    } else {
                                        preferredShortTag
                                    }
                                    rawLang != null && rawLabel != null && !rawLabel.equals(rawLang, ignoreCase = true) ->
                                        "${rawLang.uppercase(Locale.ROOT)} - $rawLabel"
                                    rawLang != null -> rawLang.uppercase(Locale.ROOT)
                                    rawLabel != null -> rawLabel
                                    else -> AppLanguageManager.localizedString(context, R.string.common_unknown)
                                }
                                val details = if (codec != null) "$langPart - $codec" else langPart
                                val readableLabel = AppLanguageManager.localizedString(
                                    context,
                                    R.string.player_track_label,
                                    optionCounter,
                                    details
                                )
                                val shortBadge = when {
                                    isPreferred -> preferredShortTag
                                    rawLang != null -> rawLang.uppercase(Locale.ROOT).take(3)
                                    rawLabel != null -> rawLabel.take(10)
                                    else -> AppLanguageManager.localizedString(
                                        context,
                                        R.string.player_track_short,
                                        optionCounter
                                    )
                                }

                                extracted.add(
                                    AudioTrackOption(
                                        groupIndex = groupIndex,
                                        trackIndex = trackIndex,
                                        label = readableLabel,
                                        shortBadge = shortBadge,
                                        isSelected = selected
                                    )
                                )
                                optionCounter++
                            }
                        }
                    }
                    audioTracks = extracted

                    if (audioTrackOverrideApplied) return
                    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                    if (audioGroups.isEmpty()) return

                    val selectedIsPreferred = audioGroups.any { group ->
                        (0 until group.length).any { trackIndex ->
                            val format = group.getTrackFormat(trackIndex)
                            group.isTrackSelected(trackIndex) && matchesAudioLanguage(activeAppLang, format.language, format.label)
                        }
                    }
                    if (selectedIsPreferred) {
                        audioTrackOverrideApplied = true
                        return
                    }

                    for (group in audioGroups) {
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            if (matchesAudioLanguage(activeAppLang, format.language, format.label)) {
                                audioTrackOverrideApplied = true
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                                    )
                                    .build()
                                return
                            }
                        }
                    }
                    audioTrackOverrideApplied = true
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady && shouldPlay && latestIsHomeVisible && isLifecycleResumed) {
                        requestAudioFocus()
                    } else {
                        if (playWhenReady) {
                            player.playWhenReady = false
                        }
                        player.setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
                        abandonAudioFocus()
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing && shouldPlay && latestIsHomeVisible && isLifecycleResumed) {
                        requestAudioFocus()
                    } else {
                        if (!player.playWhenReady || !shouldPlay || !latestIsHomeVisible || !isLifecycleResumed) {
                            player.setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
                            abandonAudioFocus()
                        }
                        if (player.playbackState != Player.STATE_IDLE) {
                            val pos = player.currentPosition.coerceAtLeast(0L)
                            val dur = player.duration.takeIf { it > 0L } ?: 0L
                            if (pos > 0L) {
                                pendingSeekMs = pos
                                latestOnSyncProgress(pos, dur)
                            }
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "STATE_IDLE"
                        Player.STATE_BUFFERING -> "STATE_BUFFERING"
                        Player.STATE_READY -> "STATE_READY"
                        Player.STATE_ENDED -> "STATE_ENDED"
                        else -> "UNKNOWN($playbackState)"
                    }
                    Log.d(
                        "MediaPlayerSync",
                        "onPlaybackStateChanged state=$stateName ts=${SystemClock.elapsedRealtime()} pos=${player.currentPosition} playWhenReady=${player.playWhenReady}"
                    )
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        playerErrorDetail = null
                        val dur = player.duration.takeIf { it > 0L } ?: 0L
                        if (dur > 0L) totalDurationMs = dur
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val httpResponseCode = generateSequence(error as Throwable?) { it.cause }
                        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()
                        ?.responseCode
                    val errorDetails = if (httpResponseCode != null) {
                        "${error.errorCodeName} (HTTP $httpResponseCode)"
                    } else {
                        error.errorCodeName
                    }
                    val failedUrl = currentStream?.url.orEmpty()
                    Log.e("MediaPlayer", "Playback error on $failedUrl: $errorDetails", error)
                    playerErrorDetail = AppLanguageManager.localizedString(
                        context,
                        R.string.player_error_code,
                        errorDetails
                    )

                    val key = "${selectedStreamIndex}:$failedUrl"
                    if (failedStreamKey != key) {
                        failedStreamKey = key
                        isBuffering = false
                        abandonAudioFocus()
                        if (selectedStreamIndex + 1 < streams.size && shouldPlay && latestIsHomeVisible && isLifecycleResumed) {
                            selectedStreamIndex += 1
                        } else {
                            shouldPlay = false
                            player.playWhenReady = false
                            player.setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
                            abandonAudioFocus()
                            latestOnStreamError()
                        }
                    }
                }
            }
            player.addListener(listener)

            val analyticsListener = object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long
                ) {
                    Log.d(
                        "MediaPlayerSync",
                        "onVideoDecoderInitialized decoder=$decoderName initDurationMs=$initializationDurationMs ts=${SystemClock.elapsedRealtime()} pos=${eventTime.currentPlaybackPositionMs}"
                    )
                }

                override fun onAudioUnderrun(
                    eventTime: AnalyticsListener.EventTime,
                    bufferSize: Int,
                    bufferSizeMs: Long,
                    elapsedSinceLastFeedMs: Long
                ) {
                    Log.d(
                        "MediaPlayerSync",
                        "onAudioUnderrun bufferSize=$bufferSize bufferSizeMs=$bufferSizeMs elapsedSinceLastFeedMs=$elapsedSinceLastFeedMs ts=${SystemClock.elapsedRealtime()} pos=${eventTime.currentPlaybackPositionMs}"
                    )
                }

                override fun onAudioPositionAdvancing(
                    eventTime: AnalyticsListener.EventTime,
                    playoutStartSystemTimeMs: Long
                ) {
                    Log.d(
                        "MediaPlayerSync",
                        "onAudioPositionAdvancing playoutStartSystemTimeMs=$playoutStartSystemTimeMs ts=${SystemClock.elapsedRealtime()} pos=${eventTime.currentPlaybackPositionMs}"
                    )
                }
            }
            player.addAnalyticsListener(analyticsListener)

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it > 0L } ?: 0L
                    pauseAndAbandonFocus(player)
                    if (pos > 0L) {
                        pendingSeekMs = pos
                        latestOnSyncProgress(pos, dur)
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                player.removeAnalyticsListener(analyticsListener)
                player.removeListener(listener)
                playerView.player = null
                player.playWhenReady = false
                player.pause()
                player.setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
                abandonAudioFocus()
                isPlaying = false
                isBuffering = false
                val finalPos = player.currentPosition.coerceAtLeast(0L)
                val finalDur = player.duration.takeIf { it > 0L } ?: 0L
                if (finalPos > 0L) {
                    pendingSeekMs = finalPos
                    latestOnSyncProgress(finalPos, finalDur)
                }
                player.release()
            }
        }
    }

    val bumpOverlay = {
        overlayVisible = true
        interactionTick++
    }

    BackHandler(enabled = isHomeVisible && (isYouTubeMode || showAudioSelector || showSourceSelector)) {
        if (showAudioSelector || showSourceSelector) {
            showAudioSelector = false
            showSourceSelector = false
            bumpOverlay()
        } else {
            pauseAndAbandonFocus(exoPlayer)
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (overlayVisible) {
                    overlayVisible = false
                } else {
                    bumpOverlay()
                }
            }
    ) {
        if (currentStream != null && exoPlayer != null && streamError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(videoAspectRatio.coerceAtLeast(0.1f))
                        .background(Color.Black),
                    factory = {
                        (playerView.parent as? ViewGroup)?.removeView(playerView)
                        playerView
                    },
                    update = { view ->
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                )
            }
        }
        // Loading indicator while resolving streams or buffering.
        if ((isResolvingStreams || isBuffering) && streamError == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = AaAmberAlt, strokeWidth = 3.dp)
                    if (isResolvingStreams) {
                        Text(
                            text = stringResource(R.string.player_resolving_streams),
                            color = AaTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (!playerErrorDetail.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.player_switching_source, playerErrorDetail!!),
                            color = AaAmberAlt,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Error fallback state when no playable streams remain.
        if (streamError != null) {
            val fullErrorText = if (!playerErrorDetail.isNullOrBlank() &&
                !streamError.contains(playerErrorDetail!!)
            ) {
                "$streamError ($playerErrorDetail)"
            } else {
                streamError
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = fullErrorText,
                        color = AaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                playerErrorDetail = null
                                loadedStreamKey = null
                                onRetryResolve()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AaAmber)
                        ) {
                            Text(stringResource(R.string.action_retry), color = Color.White, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                pauseAndAbandonFocus(exoPlayer)
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AaPanelHigh)
                        ) {
                            Text(stringResource(R.string.action_back), color = AaTextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Minimal auto-hiding Compose overlay.
        AnimatedVisibility(
            visible = overlayVisible && streamError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.78f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            ) {
                // Top Overlay Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isYouTubeMode) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
                                .clickable {
                                    bumpOverlay()
                                    pauseAndAbandonFocus(exoPlayer)
                                    onBack()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.youtube_back_to_youtube),
                                tint = AaTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.youtube_back_to_youtube),
                                color = AaTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = title,
                            color = AaTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        val badgeText = currentStream?.qualityBadge
                            ?.takeIf { it.isNotBlank() }
                            ?: currentStream?.name?.takeIf { it.isNotBlank() }
                            ?: "720p"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AaAmberAlt.copy(alpha = 0.22f))
                                .border(1.dp, AaAmberAlt, RoundedCornerShape(10.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = badgeText,
                                color = AaAmberAlt,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    bumpOverlay()
                                    pauseAndAbandonFocus(exoPlayer)
                                    onBack()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = AaTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = AaTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentStream != null) {
                                Text(
                                    text = currentStream.displayName,
                                    color = AaTextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (audioTracks.isNotEmpty()) {
                            val activeAudioBadge = audioTracks.firstOrNull { it.isSelected }?.shortBadge
                                ?: audioTracks.first().shortBadge
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AaPanelHigh.copy(alpha = 0.9f))
                                    .border(1.dp, AaBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        bumpOverlay()
                                        showSourceSelector = false
                                        showAudioSelector = !showAudioSelector
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Audiotrack,
                                    contentDescription = stringResource(R.string.player_select_audio),
                                    tint = AaAmberAlt,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.player_audio_badge, activeAudioBadge),
                                    color = AaTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        if (streams.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AaPanelHigh.copy(alpha = 0.9f))
                                    .border(1.dp, AaBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        bumpOverlay()
                                        showAudioSelector = false
                                        showSourceSelector = !showSourceSelector
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.player_source_counter, selectedStreamIndex + 1, streams.size),
                                    color = AaAmberAlt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    pauseAndAbandonFocus(exoPlayer)
                                    onClose()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "\u2715",
                                color = AaTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Center Playback Controls: -15s, Play/Pause, +15s
                if (currentStream != null && exoPlayer != null) {
                    val centerSpacing = if (isCompactHeight) 16.dp else 22.dp
                    val playBtnSize = if (isCompactHeight) 46.dp else 56.dp
                    val playIconSize = if (isCompactHeight) 26.dp else 32.dp
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(centerSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerSeekPill(label = "-15s") {
                            bumpOverlay()
                            val target = (exoPlayer.currentPosition - 15_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            pendingSeekMs = target
                        }

                        Box(
                            modifier = Modifier
                                .size(playBtnSize)
                                .clip(CircleShape)
                                .background(AaAmberAlt)
                                .clickable {
                                    bumpOverlay()
                                    if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                                        pauseAndAbandonFocus(exoPlayer)
                                    } else {
                                        shouldPlay = true
                                        exoPlayer.setAudioAttributes(playerAudioAttributes, /* handleAudioFocus= */ false)
                                        requestAudioFocus()
                                        exoPlayer.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.action_pause else R.string.action_play
                                ),
                                tint = Color.Black,
                                modifier = Modifier.size(playIconSize)
                            )
                        }

                        PlayerSeekPill(label = "+15s") {
                            bumpOverlay()
                            val dur = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                            val target = (exoPlayer.currentPosition + 15_000L).coerceAtMost(dur)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                            pendingSeekMs = target
                        }
                    }
                }

                // Bottom minimal timeline bar
                if (currentStream != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val progress = if (totalDurationMs > 0L) {
                            (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AaAmberAlt,
                            trackColor = Color.White.copy(alpha = 0.22f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDurationMs(currentPositionMs),
                                color = AaTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (totalDurationMs > 0L) formatDurationMs(totalDurationMs) else "--:--",
                                color = AaTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                // High-contrast automotive Audio Track Selector panel
                if (showAudioSelector && audioTracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.72f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showAudioSelector = false
                                bumpOverlay()
                            }
                            .padding(
                                horizontal = if (isCompactHeight) 14.dp else 24.dp,
                                vertical = if (isCompactHeight) 10.dp else 16.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(min = 280.dp, max = 420.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AaPanel)
                                .border(1.5.dp, AaAmberAlt, RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { /* Consume clicks inside dialog */ }
                                .padding(if (isCompactHeight) 10.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.player_select_audio),
                                    color = AaTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.action_close),
                                    color = AaAmberAlt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            showAudioSelector = false
                                            bumpOverlay()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = if (isCompactHeight) 130.dp else 220.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                audioTracks.forEach { option ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (option.isSelected) {
                                                    AaAmberAlt.copy(alpha = 0.2f)
                                                } else {
                                                    AaPanelHigh
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (option.isSelected) AaAmberAlt else AaBorder,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                val player = exoPlayer
                                                if (player != null) {
                                                    val targetGroup = player.currentTracks.groups
                                                        .getOrNull(option.groupIndex)
                                                    if (targetGroup != null) {
                                                        audioTrackOverrideApplied = true
                                                        player.trackSelectionParameters =
                                                            player.trackSelectionParameters
                                                                .buildUpon()
                                                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                                .setOverrideForType(
                                                                    TrackSelectionOverride(
                                                                        targetGroup.mediaTrackGroup,
                                                                        option.trackIndex
                                                                    )
                                                                )
                                                                .build()
                                                    }
                                                }
                                                showAudioSelector = false
                                                bumpOverlay()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = option.label,
                                            color = if (option.isSelected) AaAmberAlt else AaTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (option.isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = stringResource(R.string.player_active),
                                                tint = AaAmberAlt,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // High-contrast automotive Source Selector panel
                if (showSourceSelector && streams.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.76f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showSourceSelector = false
                                bumpOverlay()
                            }
                            .padding(
                                horizontal = if (isCompactHeight) 14.dp else 20.dp,
                                vertical = if (isCompactHeight) 10.dp else 14.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AaPanel)
                                .border(1.5.dp, AaAmberAlt, RoundedCornerShape(14.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { /* Consume clicks inside dialog */ }
                                .padding(if (isCompactHeight) 10.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.player_select_source, streams.size),
                                    color = AaTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.action_close),
                                    color = AaAmberAlt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            showSourceSelector = false
                                            bumpOverlay()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = if (isCompactHeight) 130.dp else 220.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                streams.forEachIndexed { index, streamItem ->
                                    val isCurrent = index == selectedStreamIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isCurrent) {
                                                    AaAmberAlt.copy(alpha = 0.2f)
                                                } else {
                                                    AaPanelHigh
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isCurrent) AaAmberAlt else AaBorder,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                if (index != selectedStreamIndex) {
                                                    val pos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
                                                    val dur = exoPlayer?.duration?.takeIf { it > 0L } ?: 0L
                                                    if (pos > 0L) {
                                                        pendingSeekMs = pos
                                                        latestOnSyncProgress(pos, dur)
                                                    }
                                                    loadedStreamKey = null
                                                    selectedStreamIndex = index
                                                }
                                                showSourceSelector = false
                                                bumpOverlay()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "#${index + 1} • ${streamItem.name.ifBlank { stringResource(R.string.player_source_fallback, index + 1) }}",
                                                    color = if (isCurrent) AaAmberAlt else AaTextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (!streamItem.languageTag.isNullOrBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(AaPanel)
                                                            .border(1.dp, AaBorder, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = streamItem.languageTag,
                                                            color = AaTextSecondary,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            if (streamItem.title.isNotBlank()) {
                                                Text(
                                                    text = streamItem.title,
                                                    color = AaTextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = stringResource(R.string.media_playing),
                                                tint = AaAmberAlt,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun preferredAudioLanguageCodes(appLanguage: String): Array<String> = when (appLanguage) {
    "it" -> arrayOf("it", "ita")
    "de" -> arrayOf("de", "deu", "ger")
    "fr" -> arrayOf("fr", "fra", "fre")
    "es" -> arrayOf("es", "spa")
    "pt" -> arrayOf("pt", "por")
    else -> arrayOf("en", "eng")
}

private fun soberAudioBadge(appLanguage: String): String = when (appLanguage) {
    "it" -> "ITA"
    "de" -> "GER"
    "fr" -> "FRA"
    "es" -> "ESP"
    "pt" -> "POR"
    else -> "ENG"
}

private fun matchesAudioLanguage(appLanguage: String, language: String?, label: String?): Boolean {
    val lang = language?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val lbl = label?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when (appLanguage) {
        "it" -> lang.startsWith("it") || lbl.contains("ita") || lbl.contains("italian")
        "de" -> lang == "de" || lang.startsWith("de-") || lang == "deu" || lang == "ger" ||
            lbl.contains("ger") || lbl.contains("deu") || lbl.contains("deutsch") || lbl.contains("german")
        "fr" -> lang == "fr" || lang.startsWith("fr-") || lang == "fra" || lang == "fre" ||
            lbl.contains("fre") || lbl.contains("fra") || lbl.contains("french") || lbl.contains("français") || lbl.contains("francais")
        "es" -> lang == "es" || lang.startsWith("es-") || lang == "spa" ||
            lbl.contains("esp") || lbl.contains("spa") || lbl.contains("spanish") || lbl.contains("español") || lbl.contains("espanol") || lbl.contains("latino") || lbl.contains("castellano")
        "pt" -> lang == "pt" || lang.startsWith("pt-") || lang == "por" ||
            lbl.contains("por") || lbl.contains("portug")
        else -> lang == "en" || lang.startsWith("en-") || lang == "eng" ||
            lbl.contains("eng") || lbl.contains("english")
    }
}

private fun formatAudioCodec(mimeType: String?): String? {
    val mime = mimeType?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        mime.contains("eac3") || mime.contains("ec-3") -> "EAC3"
        mime.contains("ac3") || mime.contains("ac-3") -> "AC3"
        mime.contains("mp4a") || mime.contains("aac") -> "AAC"
        mime.contains("opus") -> "OPUS"
        mime.contains("vorbis") -> "VORBIS"
        mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
        mime.contains("flac") -> "FLAC"
        mime.contains("dts") -> "DTS"
        mime.contains("true-hd") || mime.contains("truehd") -> "TRUEHD"
        mime.startsWith("audio/") -> mime.removePrefix("audio/").uppercase(Locale.ROOT)
        else -> null
    }
}

@Composable
private fun PlayerSeekPill(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = AaTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
