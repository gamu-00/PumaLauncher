package com.openlauncher.app.ui.widget

import android.content.Intent
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.service.MediaListenerService
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun NowPlayingWidget(
    state: NowPlayingState?,
    accent: Color,
    carPlayPackage: String,
    androidAutoPackage: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onLaunchCarPlay: () -> Unit,
    onLaunchAndroidAuto: () -> Unit,
    onTapToOpenApp: () -> Unit,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    isDayMode: Boolean = false,
    hardwareRadio: com.openlauncher.app.viewmodel.LauncherViewModel.HardwareRadioState? = null,
    onLaunchHardwareRadio: () -> Unit = {},
    onStopHardwareRadio: () -> Unit = {},
    onRadioSeekUp: () -> Unit = {},
    onRadioSeekDown: () -> Unit = {},
    onRadioCycleFm: () -> Unit = {},
    onRadioSwitchAm: () -> Unit = {},
    onRadioTune: (band: String, freq: Float) -> Unit = { _, _ -> },
    onAssignRadio: () -> Unit = {}
) {
    var selectedSource by rememberSaveable { mutableStateOf("Any Player") }

    // Auto-switch to radio view when hardware radio is detected.
    val hasHardwareRadio = hardwareRadio != null
    LaunchedEffect(hasHardwareRadio) {
        if (hasHardwareRadio) selectedSource = "FM/AM Radio"
        else if (selectedSource == "FM/AM Radio") selectedSource = "Any Player"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
    ) {
        // 1. CONDITIONAL VIEW TOGGLE
        if (selectedSource == "FM/AM Radio") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
            ) {
                RadioDeck(
                    accent = accent,
                    isDayMode = isDayMode,
                    hardwareRadio = hardwareRadio,
                    onLaunchHardwareRadio = onLaunchHardwareRadio,
                    onStopHardwareRadio = onStopHardwareRadio,
                    onRadioSeekUp = onRadioSeekUp,
                    onRadioSeekDown = onRadioSeekDown,
                    onRadioCycleFm = onRadioCycleFm,
                    onRadioSwitchAm = onRadioSwitchAm,
                    onRadioTune = onRadioTune,
                    onAssignRadio = onAssignRadio,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            StandardMinimalPlayer(
                state = state,
                accent = accent,
                isEditing = isEditing,
                isDayMode = isDayMode,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrev = onPrev,
                onTapToOpenApp = onTapToOpenApp,
                onLaunchMusicApp = onLaunchCarPlay,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. FLOATING MULTI-SOURCE SELECTOR (Top-Right, always overlayed)
        var menuExpanded by remember { mutableStateOf(false) }
        val selectorIconColor = if (isDayMode) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 4.dp)
        ) {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Source Selector",
                    tint = selectorIconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            val dropdownBg   = if (isDayMode) Color(0xFFF0F0F0) else MaterialTheme.colorScheme.background
            val dropdownText = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(dropdownBg)
            ) {
                DropdownMenuItem(
                    text = { Text("Any Player", color = dropdownText, fontSize = 11.sp) },
                    onClick = {
                        selectedSource = "Any Player"
                        menuExpanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(14.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("FM/AM Radio", color = dropdownText, fontSize = 11.sp) },
                    onClick = {
                        selectedSource = "FM/AM Radio"
                        menuExpanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Radio, null, tint = accent, modifier = Modifier.size(14.dp)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RadioDeck(
    accent: Color,
    isDayMode: Boolean,
    hardwareRadio: com.openlauncher.app.viewmodel.LauncherViewModel.HardwareRadioState?,
    onLaunchHardwareRadio: () -> Unit,
    onStopHardwareRadio: () -> Unit,
    onRadioSeekUp: () -> Unit,
    onRadioSeekDown: () -> Unit,
    onRadioCycleFm: () -> Unit,
    onRadioSwitchAm: () -> Unit,
    onRadioTune: (band: String, freq: Float) -> Unit,
    onAssignRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
    val dimColor     = if (isDayMode) Color(0xFF444444) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    val borderColor  = if (isDayMode) Color(0xFF777777) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

    if (hardwareRadio == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Radio, null, tint = dimColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "NO RADIO SOURCE",
                color = contentColor.copy(alpha = 0.85f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Start your head unit's radio app - or assign it below so Open Launcher can mirror and control it",
                color = dimColor, fontSize = 7.sp, fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 10.sp,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onAssignRadio() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "ASSIGN RADIO APP",
                    color = accent, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
            }
        }
        return
    }

    var isManuallyOff by remember { mutableStateOf(false) }
    LaunchedEffect(hardwareRadio) {
        if (isManuallyOff) isManuallyOff = false
    }
    val powerOn = !isManuallyOff

    val displayBand = hardwareRadio.band.uppercase()
    val freqClean   = hardwareRadio.freq.replace(Regex("[^0-9.]"), "")
    val freqFloat   = freqClean.toFloatOrNull()
    val displayFreq = freqClean.ifEmpty { hardwareRadio.freq }
    val displayUnit = if (hardwareRadio.isAm) "kHz" else "MHz"

    val chipInactiveBg = if (isDayMode) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val chipActiveBg   = if (isDayMode) Color(0xFF222222) else Color(0xFFDDDDDD)
    val chipActiveText = if (isDayMode) Color.White else Color(0xFF111111)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hardwareRadio.canTune) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("FM1", "FM2", "FM3", "AM").forEach { b ->
                        val active = displayBand == b
                        Box(
                            modifier = Modifier
                                .height(22.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (active) chipActiveBg else chipInactiveBg)
                                .border(1.dp, if (active) borderColor else borderColor.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                .clickable { if (b == "AM") onRadioSwitchAm() else onRadioCycleFm() }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                b,
                                color = if (active) chipActiveText else dimColor,
                                fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .height(22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(chipActiveBg)
                        .border(1.dp, borderColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        displayBand,
                        color = chipActiveText,
                        fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (powerOn) chipActiveBg else chipInactiveBg)
                    .border(1.5.dp, borderColor, CircleShape)
                    .clickable {
                        if (powerOn) { onStopHardwareRadio(); isManuallyOff = true }
                        else { onLaunchHardwareRadio(); isManuallyOff = false }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew, "PWR",
                    tint = if (powerOn) chipActiveText else dimColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    displayFreq,
                    color = if (powerOn) contentColor else contentColor.copy(alpha = 0.3f),
                    fontSize = 34.sp, fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace, letterSpacing = 0.sp
                )
                Text(
                    displayUnit,
                    color = dimColor, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = (if (!powerOn) "RADIO OFF" else hardwareRadio.stationName ?: "LIVE").uppercase(),
                    color = if (powerOn) accent else dimColor.copy(alpha = 0.5f),
                    fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (powerOn) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accent))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RadioFlatButton(
                label = "◄ SEEK", enabled = powerOn, active = false,
                accent = accent, borderColor = borderColor, dimColor = dimColor,
                modifier = Modifier.weight(1f),
                onClick = onRadioSeekDown
            )
            RadioFlatButton(
                label = "OPEN", enabled = true, active = false,
                accent = accent, borderColor = borderColor, dimColor = dimColor,
                modifier = Modifier.weight(1f),
                onClick = onLaunchHardwareRadio
            )
            RadioFlatButton(
                label = "SEEK ►", enabled = powerOn, active = false,
                accent = accent, borderColor = borderColor, dimColor = dimColor,
                modifier = Modifier.weight(1f),
                onClick = onRadioSeekUp
            )
        }

        if (hardwareRadio.canTune) {
            var fmPresets by rememberSaveable { mutableStateOf(listOf(88.5f, 91.5f, 98.1f, 101.9f, 104.3f, 107.5f)) }
            var amPresets by rememberSaveable { mutableStateOf(listOf(540f, 680f, 820f, 1040f, 1260f, 1420f)) }
            val isFm           = !hardwareRadio.isAm
            val currentPresets = if (isFm) fmPresets else amPresets
            val tolerance      = if (isFm) 0.15f else 5f

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (pIdx in 0 until 6) {
                    val presetFreq = currentPresets[pIdx]
                    val isTuned    = freqFloat != null && abs(freqFloat - presetFreq) < tolerance
                    val presetBg = when {
                        isTuned && isDayMode -> Color(0xFF222222)
                        isTuned              -> accent.copy(alpha = 0.12f)
                        else                 -> Color.Transparent
                    }
                    val presetBorderColor = when {
                        isTuned && isDayMode -> Color(0xFF222222)
                        isTuned              -> accent
                        isDayMode            -> Color(0xFFCCCCCC)
                        else                 -> Color(0xFF1D2024)
                    }
                    val presetNumColor = when {
                        isTuned && isDayMode -> Color.White
                        isTuned              -> accent
                        isDayMode            -> Color(0xFF444444)
                        else                 -> Color(0xFF777777)
                    }
                    val presetFreqColor = when {
                        isTuned && isDayMode -> Color.White.copy(alpha = 0.9f)
                        isTuned              -> accent.copy(alpha = 0.9f)
                        isDayMode            -> Color(0xFF666666)
                        else                 -> Color(0xFF777777).copy(alpha = 0.7f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .border(1.dp, presetBorderColor, RoundedCornerShape(2.dp))
                            .clip(RoundedCornerShape(2.dp))
                            .background(presetBg)
                            .combinedClickable(
                                enabled = powerOn,
                                onClick = { onRadioTune(displayBand, presetFreq) },
                                onLongClick = {
                                    freqFloat?.let { f ->
                                        if (isFm) fmPresets = fmPresets.toMutableList().also { it[pIdx] = f }
                                        else      amPresets = amPresets.toMutableList().also { it[pIdx] = f }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text("${pIdx + 1}", color = presetNumColor, fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(
                                if (isFm) "%.1f".format(presetFreq) else "%.0f".format(presetFreq),
                                color = presetFreqColor, fontSize = 6.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioFlatButton(
    label: String,
    enabled: Boolean,
    active: Boolean,
    accent: Color,
    borderColor: Color,
    dimColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .border(1.dp, if (active) accent else borderColor, RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) accent.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = when {
                !enabled -> dimColor.copy(alpha = 0.35f)
                active   -> accent
                else     -> dimColor
            },
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun StandardMinimalPlayer(
    state: NowPlayingState?,
    accent: Color,
    isDayMode: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onTapToOpenApp: () -> Unit,
    onLaunchMusicApp: () -> Unit,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionCheckTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionCheckTrigger++
                MediaListenerService.requestRefresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(context) {
        MediaListenerService.requestRefresh(context)
    }

    val isNotificationAccessGranted = remember(permissionCheckTrigger, context) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    val hasContent = state != null && state.title.isNotEmpty()
    val ringColor = accent
    val onAccentColor = if (ringColor.luminance() > 0.5f) Color.Black else Color.White
    val textPrimary = Color(0xFFF2F4F7)
    val textSecondary = Color(0xFF9AA3B2)
    val trackColor = Color(0xFF272C35)

    Box(modifier = modifier) {
        if (!isNotificationAccessGranted) {
            // Permesso notifiche NON attivo: avviso chiaro e cliccabile che apre ACTION_NOTIFICATION_LISTENER_SETTINGS
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1F26))
                    .border(1.5.dp, ringColor, RoundedCornerShape(12.dp))
                    .let {
                        if (!isEditing) {
                            it.clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                        } else it
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(ringColor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Attiva accesso notifiche",
                            tint = onAccentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Attiva accesso notifiche per YouTube Music",
                            color = textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tocca qui per abilitare il controllo multimediale nelle impostazioni",
                            color = ringColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else if (!hasContent) {
            // Permesso attivo e nessun brano in riproduzione: riquadro pulito per avviare YouTube Music
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1F26))
                    .border(1.dp, trackColor, RoundedCornerShape(12.dp))
                    .let { if (!isEditing) it.clickable { onLaunchMusicApp() } else it },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ringColor)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = onAccentColor, modifier = Modifier.size(40.dp))
                    }
                    Column {
                        Text("YouTube Music", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Tocca per avviare", color = textSecondary, fontSize = 14.sp)
                    }
                }
            }
        } else {
            val nonNullState = state!!
            var durationMs by remember(nonNullState.title, nonNullState.artist) {
                mutableLongStateOf(
                    (nonNullState.controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
                        .coerceAtLeast(0L)
                )
            }
            var positionMs by remember(nonNullState.title, nonNullState.artist) {
                mutableLongStateOf(
                    computeLivePositionMs(nonNullState.controller?.playbackState, durationMs)
                )
            }

            // Coroutine ticker every 500ms while isPlaying == true to update the circular progress ring in real time
            LaunchedEffect(nonNullState.isPlaying, nonNullState.title, nonNullState.controller?.sessionToken) {
                durationMs = (nonNullState.controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: durationMs)
                    .coerceAtLeast(0L)
                positionMs = computeLivePositionMs(nonNullState.controller?.playbackState, durationMs)
                while (nonNullState.isPlaying) {
                    delay(500L)
                    durationMs = (nonNullState.controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: durationMs)
                        .coerceAtLeast(0L)
                    positionMs = computeLivePositionMs(nonNullState.controller?.playbackState, durationMs)
                }
            }

            val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val ringSize = minOf(maxHeight, maxWidth * 0.42f)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Album art with circular progress ring in dynamic theme accent color
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(ringSize)
                            .let { if (!isEditing) it.clickable { onTapToOpenApp() } else it }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 6.dp.toPx()
                            val inset = stroke / 2f
                            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                            drawArc(
                                color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                            )
                            drawArc(
                                color = ringColor, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B1F26))
                        ) {
                            if (nonNullState.albumArt != null) {
                                val imageBitmap = remember(nonNullState.albumArt) {
                                    nonNullState.albumArt.asImageBitmap()
                                }
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = nonNullState.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (!nonNullState.artUri.isNullOrEmpty()) {
                                coil.compose.AsyncImage(
                                    model = nonNullState.artUri,
                                    contentDescription = nonNullState.title,
                                    contentScale = ContentScale.Crop,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.MusicNote, null, tint = ringColor, modifier = Modifier.size(40.dp))
                            }
                        }
                    }

                    // Track info + large controls
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            modifier = Modifier.let { if (!isEditing) it.clickable { onTapToOpenApp() } else it }
                        ) {
                            Text(
                                text = nonNullState.title,
                                color = textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = nonNullState.artist.ifEmpty { "Sconosciuto" },
                                color = textSecondary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (durationMs > 0) {
                                Text(
                                    text = "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                                    color = textSecondary.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isEditing) {
                                        onPrev()
                                        com.openlauncher.app.ui.screen.restoreActiveVideoWindow(context)
                                    }
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, "Prev", tint = textPrimary, modifier = Modifier.size(38.dp))
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(68.dp).clip(CircleShape).background(ringColor)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (!isEditing) {
                                            onPlayPause()
                                            com.openlauncher.app.ui.screen.restoreActiveVideoWindow(context)
                                        }
                                    },
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Icon(
                                        imageVector = if (nonNullState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (nonNullState.isPlaying) "Pause" else "Play",
                                        tint = onAccentColor,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (!isEditing) {
                                        onNext()
                                        com.openlauncher.app.ui.screen.restoreActiveVideoWindow(context)
                                    }
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, "Next", tint = textPrimary, modifier = Modifier.size(38.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun computeLivePositionMs(playbackState: PlaybackState?, durationMs: Long): Long {
    if (playbackState == null) return 0L
    val basePos = playbackState.position.coerceAtLeast(0L)
    val extrapolated = if (playbackState.state == PlaybackState.STATE_PLAYING) {
        val lastUpdate = playbackState.lastPositionUpdateTime
        val deltaMs = if (lastUpdate > 0L) (SystemClock.elapsedRealtime() - lastUpdate).coerceAtLeast(0L) else 0L
        val speed = if (playbackState.playbackSpeed > 0f) playbackState.playbackSpeed else 1f
        basePos + (deltaMs * speed).toLong()
    } else {
        basePos
    }
    return if (durationMs > 0L) extrapolated.coerceIn(0L, durationMs) else extrapolated
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
