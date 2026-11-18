package com.openlauncher.app.mediastation.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.openlauncher.app.mediastation.data.PlayableStream
import com.openlauncher.app.ui.theme.AaAmber
import com.openlauncher.app.ui.theme.AaAmberAlt
import com.openlauncher.app.ui.theme.AaBorder
import com.openlauncher.app.ui.theme.AaPanel
import com.openlauncher.app.ui.theme.AaPanelHigh
import com.openlauncher.app.ui.theme.AaTextPrimary
import com.openlauncher.app.ui.theme.AaTextSecondary
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
    onCycleStream: () -> Unit,
    onStreamError: () -> Unit,
    onRetryResolve: () -> Unit,
    onSyncProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentStream = streams.getOrNull(currentStreamIndex)

    val latestOnStreamError by rememberUpdatedState(onStreamError)
    val latestOnSyncProgress by rememberUpdatedState(onSyncProgress)

    var overlayVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(resumeOffsetMs) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var pendingSeekMs by remember { mutableLongStateOf(resumeOffsetMs) }
    var initialSeekApplied by remember { mutableStateOf(false) }
    var failedStreamKey by remember { mutableStateOf<String?>(null) }
    var audioTrackOverrideApplied by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var showAudioSelector by remember { mutableStateOf(false) }

    val exoPlayer = remember(context) {
        val defaultHttpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(defaultHttpFactory))
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setPreferredAudioLanguages("it", "ita")
                    .build()
            }
    }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    val audioFocusRequest = remember(exoPlayer) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                            exoPlayer.pause()
                        }
                    }
                }
            }
            .build()
    }

    val requestAudioFocus = remember(audioManager, audioFocusRequest) {
        { audioManager?.requestAudioFocus(audioFocusRequest) }
    }

    val abandonAudioFocus = remember(audioManager, audioFocusRequest) {
        { audioManager?.abandonAudioFocusRequest(audioFocusRequest) }
    }

    // Auto-hide overlay after 3 seconds of inactivity (keep visible while audio selector is open).
    LaunchedEffect(overlayVisible, interactionTick, showAudioSelector) {
        if (overlayVisible && !showAudioSelector) {
            delay(3_000L)
            overlayVisible = false
        }
    }

    // Periodic UI position refresh while playing.
    LaunchedEffect(exoPlayer, isPlaying, overlayVisible) {
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            currentPositionMs = pos
            totalDurationMs = dur
            if (pos > 0L) {
                pendingSeekMs = pos
            }
            delay(if (overlayVisible) 500L else 1_500L)
        }
    }

    // Load or switch MediaSource whenever resolved streams or selected index change.
    LaunchedEffect(streams, currentStreamIndex) {
        val stream = currentStream ?: return@LaunchedEffect
        failedStreamKey = null
        audioTrackOverrideApplied = false
        audioTracks = emptyList()
        showAudioSelector = false
        isBuffering = true

        val targetSeek = if (!initialSeekApplied && resumeOffsetMs > 0L) {
            initialSeekApplied = true
            resumeOffsetMs
        } else {
            pendingSeekMs
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(stream.headers)
        val mediaSource = DefaultMediaSourceFactory(httpFactory)
            .createMediaSource(MediaItem.fromUri(stream.url))

        requestAudioFocus()
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setPreferredAudioLanguages("it", "ita")
            .build()
        exoPlayer.setMediaSource(mediaSource)
        if (targetSeek > 0L) {
            exoPlayer.seekTo(targetSeek)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Attach Player.Listener and manage strict release + progress sync on dispose.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                fun isItalianTrack(language: String?, label: String?): Boolean {
                    val lang = language?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    val lbl = label?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    return lang.startsWith("it") || lbl.contains("ita") || lbl.contains("italian")
                }

                val extracted = mutableListOf<AudioTrackOption>()
                var optionCounter = 1
                tracks.groups.forEachIndexed { groupIndex, group ->
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val selected = group.isTrackSelected(trackIndex)
                            val isIta = isItalianTrack(format.language, format.label)
                            val rawLang = format.language?.trim()
                                ?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
                            val rawLabel = format.label?.trim()?.takeIf { it.isNotEmpty() }
                            val codec = formatAudioCodec(format.sampleMimeType)

                            val langPart = when {
                                isIta -> if (rawLabel != null && !rawLabel.equals("ita", ignoreCase = true)) {
                                    "ITA - $rawLabel"
                                } else {
                                    "ITA"
                                }
                                rawLang != null && rawLabel != null && !rawLabel.equals(rawLang, ignoreCase = true) ->
                                    "${rawLang.uppercase(Locale.ROOT)} - $rawLabel"
                                rawLang != null -> rawLang.uppercase(Locale.ROOT)
                                rawLabel != null -> rawLabel
                                else -> "Sconosciuta"
                            }
                            val details = if (codec != null) "$langPart - $codec" else langPart
                            val readableLabel = "Traccia $optionCounter ($details)"
                            val shortBadge = when {
                                isIta -> "ITA"
                                rawLang != null -> rawLang.uppercase(Locale.ROOT).take(3)
                                rawLabel != null -> rawLabel.take(10)
                                else -> "Traccia $optionCounter"
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

                val selectedIsItalian = audioGroups.any { group ->
                    (0 until group.length).any { trackIndex ->
                        val format = group.getTrackFormat(trackIndex)
                        group.isTrackSelected(trackIndex) && isItalianTrack(format.language, format.label)
                    }
                }
                if (selectedIsItalian) {
                    audioTrackOverrideApplied = true
                    return
                }

                for (group in audioGroups) {
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        if (isItalianTrack(format.language, format.label)) {
                            audioTrackOverrideApplied = true
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
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
                if (playWhenReady) {
                    requestAudioFocus()
                } else {
                    abandonAudioFocus()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    requestAudioFocus()
                } else {
                    if (!exoPlayer.playWhenReady) {
                        abandonAudioFocus()
                    }
                    if (exoPlayer.playbackState != Player.STATE_IDLE) {
                        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
                        if (pos > 0L) {
                            pendingSeekMs = pos
                            latestOnSyncProgress(pos, dur)
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
                    if (dur > 0L) totalDurationMs = dur
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val key = "${currentStreamIndex}:${currentStream?.url.orEmpty()}"
                if (failedStreamKey != key) {
                    failedStreamKey = key
                    isBuffering = false
                    latestOnStreamError()
                }
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
                exoPlayer.pause()
                abandonAudioFocus()
                if (pos > 0L) {
                    latestOnSyncProgress(pos, dur)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            abandonAudioFocus()
            val finalPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val finalDur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            if (finalPos > 0L) {
                latestOnSyncProgress(finalPos, finalDur)
            }
            exoPlayer.release()
        }
    }

    val bumpOverlay = {
        overlayVisible = true
        interactionTick++
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
        if (currentStream != null && streamError == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.BLACK)
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                }
            )
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
                            text = "Ricerca sorgenti...",
                            color = AaTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Error fallback state when no playable streams remain.
        if (streamError != null) {
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
                        text = streamError,
                        color = AaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRetryResolve,
                            colors = ButtonDefaults.buttonColors(containerColor = AaAmber)
                        ) {
                            Text("Riprova", color = Color.White, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = AaPanelHigh)
                        ) {
                            Text("Indietro", color = AaTextPrimary, fontSize = 12.sp)
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
                // Top Overlay Bar: Back, Title + Source, "Sorgente X/Y", Close "✕"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                bumpOverlay()
                                onBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
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
                                    showAudioSelector = !showAudioSelector
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Audiotrack,
                                contentDescription = "Seleziona traccia audio",
                                tint = AaAmberAlt,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Audio: $activeAudioBadge",
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
                                .clickable {
                                    bumpOverlay()
                                    showAudioSelector = false
                                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                                    val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
                                    if (pos > 0L) {
                                        pendingSeekMs = pos
                                        latestOnSyncProgress(pos, dur)
                                    }
                                    onCycleStream()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sorgente ${currentStreamIndex + 1}/${streams.size}",
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
                            .clickable { onClose() },
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

                // Center Playback Controls: -15s, Play/Pause, +15s
                if (currentStream != null) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
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
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(AaAmberAlt)
                                .clickable {
                                    bumpOverlay()
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                        abandonAudioFocus()
                                    } else {
                                        requestAudioFocus()
                                        exoPlayer.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
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
                            .padding(horizontal = 24.dp, vertical = 16.dp),
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
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Seleziona traccia audio",
                                    color = AaTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Chiudi",
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
                                    .heightIn(max = 220.dp)
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
                                                val targetGroup = exoPlayer.currentTracks.groups
                                                    .getOrNull(option.groupIndex)
                                                if (targetGroup != null) {
                                                    audioTrackOverrideApplied = true
                                                    exoPlayer.trackSelectionParameters =
                                                        exoPlayer.trackSelectionParameters
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
                                                contentDescription = "Attiva",
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
