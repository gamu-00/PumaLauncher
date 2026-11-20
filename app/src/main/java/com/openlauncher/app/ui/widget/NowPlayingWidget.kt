package com.openlauncher.app.ui.widget

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.openlauncher.app.R
import com.openlauncher.app.data.WeatherData
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.service.MediaNotificationListenerService
import com.openlauncher.app.ui.components.CockpitWeatherCard
import com.openlauncher.app.ui.theme.LocalCockpitColors
import com.openlauncher.app.ui.theme.MediaMusicAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val LAUNCHER_PREFS_NAME = "launcher_prefs"
private const val KEY_PREFERRED_MUSIC_PACKAGE = "preferred_music_package"

private val KNOWN_MUSIC_PACKAGE_KEYWORDS = listOf(
    "spotify",
    "youtube.music",
    "deezer",
    "tidal",
    "soundcloud",
    "apple.android.music",
    "amazon.mp3",
    "poweramp",
    "musicolet",
    "foobar2000",
    "vlc",
    "plexamp",
    "audible",
    "pandora",
    "qobuz",
    "bandcamp",
    "aimp",
    "tunein",
    "podcast",
    ".music",
    "music."
)

private data class MusicPickerApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isMusicApp: Boolean
)

/**
 * Generic live music card backed by [MediaNotificationListenerService] / MediaSessionManager,
 * with contextual [CockpitWeatherCard] display when triggered during active video playback.
 * - Active session (`hasActiveMedia == true`): shows track title, artist, album art, and
 *   isolated Previous / Play-Pause / Next buttons; tapping outside controls opens the playing app.
 * - Idle (`!hasActiveMedia` or blank title): shows a minimal clean music surface; tapping opens
 *   Notification Listener settings if disabled, else launches the saved preferred music app
 *   (or opens the app selector on first tap). Long-press always opens the preferred music app picker.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingWidget(
    state: NowPlayingState?,
    accent: Color,
    showWeather: Boolean = false,
    weather: WeatherData? = null,
    isWeatherLoading: Boolean = false,
    onRefreshWeather: () -> Unit = {},
    onDismissWeather: () -> Unit = {},
    isCompactHeight: Boolean = false,
    homePressTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var preferredPackage by remember(context) {
        mutableStateOf(readPreferredMusicPackage(context))
    }
    var showAppPicker by remember { mutableStateOf(false) }

    LaunchedEffect(homePressTrigger) {
        if (homePressTrigger > 0) {
            showAppPicker = false
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                MediaNotificationListenerService.requestRefresh(context)
                preferredPackage = readPreferredMusicPackage(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(context) {
        MediaNotificationListenerService.requestRefresh(context)
    }

    val activeController = state?.controller
    val hasActiveMedia = activeController != null && state.title.isNotBlank()
    val colors = LocalCockpitColors.current
    val onAccentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White

    AnimatedContent(
        targetState = showWeather,
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(200)) },
        label = "nowPlayingMode",
        modifier = modifier.fillMaxSize()
    ) { showingWeather ->
        if (showingWeather) {
            CockpitWeatherCard(
                weather = weather,
                isLoading = isWeatherLoading,
                accent = accent,
                returnIcon = Icons.Default.MusicNote,
                returnLabel = stringResource(R.string.weather_return_music),
                returnContentDescription = stringResource(R.string.weather_return_music_desc),
                onReturnClick = onDismissWeather,
                onRefreshClick = onRefreshWeather,
                onLongClick = { showAppPicker = true },
                isCompactHeight = isCompactHeight,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = {
                            if (hasActiveMedia) {
                                launchAppByPackage(context, activeController.packageName)
                            } else if (!MediaNotificationListenerService.isNotificationListenerEnabled(context)) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            } else {
                                val savedPkg = readPreferredMusicPackage(context)
                                preferredPackage = savedPkg
                                if (savedPkg.isEmpty() || !launchAppByPackage(context, savedPkg)) {
                                    showAppPicker = true
                                }
                            }
                        },
                        onLongClick = { showAppPicker = true }
                    )
            ) {
                if (hasActiveMedia) {
            var playbackProgress by remember(activeController, state.title, state.artist) {
                mutableFloatStateOf(readControllerProgress(activeController))
            }
            LaunchedEffect(activeController, state.isPlaying, state.title, state.artist) {
                playbackProgress = readControllerProgress(activeController)
                while (state.isPlaying) {
                    delay(500L)
                    playbackProgress = readControllerProgress(activeController)
                }
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val compact = isCompactHeight || maxHeight < 190.dp || maxWidth < 340.dp
                val pad = if (compact) 10.dp else 14.dp
                val gap = if (compact) 10.dp else 14.dp
                val innerW = (maxWidth - pad * 2).coerceAtLeast(120.dp)
                val innerH = (maxHeight - pad * 2).coerceAtLeast(80.dp)
                val artSize = minOf(innerH, innerW * if (compact) 0.30f else 0.36f)
                    .coerceIn(56.dp, 140.dp)
                val controlsAvailW = (innerW - artSize - gap).coerceAtLeast(120.dp)
                val playSize = minOf(innerH * 0.48f, controlsAvailW * 0.34f).coerceIn(40.dp, 62.dp)
                val skipBtnSize = minOf(innerH * 0.42f, controlsAvailW * 0.28f).coerceIn(34.dp, 52.dp)
                val playIconSize = (playSize * 0.58f).coerceIn(22.dp, 36.dp)
                val skipIconSize = (skipBtnSize * 0.65f).coerceIn(20.dp, 34.dp)
                val titleFontSize = if (compact) 15.sp else 18.sp
                val artistFontSize = if (compact) 12.sp else 13.sp

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(artSize)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.elevatedSurface)
                            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(14.dp))
                    ) {
                        when {
                            state.albumArt != null -> {
                                val imageBitmap = remember(state.albumArt) {
                                    state.albumArt.asImageBitmap()
                                }
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = state.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            !state.artUri.isNullOrBlank() -> {
                                AsyncImage(
                                    model = state.artUri,
                                    contentDescription = state.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                Image(
                                    painter = painterResource(R.drawable.ic_dashboard_music),
                                    contentDescription = null,
                                    modifier = Modifier.size((artSize * 0.56f).coerceIn(28.dp, 46.dp))
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = state.title.ifBlank { stringResource(R.string.media_playing) },
                                color = colors.textPrimary,
                                fontSize = titleFontSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.artist.isNotBlank()) {
                                Text(
                                    text = state.artist,
                                    color = colors.textSecondary,
                                    fontSize = artistFontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { activeController.transportControls.skipToPrevious() },
                                modifier = Modifier.size(skipBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = stringResource(R.string.media_previous),
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(skipIconSize)
                                )
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(playSize)
                                    .clip(CircleShape)
                                    .background(accent)
                                    .clickable {
                                        if (state.isPlaying) {
                                            activeController.transportControls.pause()
                                        } else {
                                            activeController.transportControls.play()
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(
                                        if (state.isPlaying) R.string.action_pause else R.string.action_play
                                    ),
                                    tint = onAccentColor,
                                    modifier = Modifier.size(playIconSize)
                                )
                            }

                            IconButton(
                                onClick = { activeController.transportControls.skipToNext() },
                                modifier = Modifier.size(skipBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = stringResource(R.string.media_next),
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(skipIconSize)
                                )
                            }
                        }
                    }
                }

                // Thin cyan progress bar anchored to the bottom edge during playback
                if (playbackProgress >= 0f) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = playbackProgress,
                        animationSpec = tween(durationMillis = 420, easing = LinearEasing),
                        label = "musicProgress"
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MediaMusicAccent.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MediaMusicAccent.copy(alpha = if (state.isPlaying) 0.78f else 0.35f),
                                            MediaMusicAccent.copy(alpha = if (state.isPlaying) 1f else 0.45f)
                                        )
                                    )
                                )
                        )
                    }
                } else if (state.isPlaying) {
                    val infiniteTransition = rememberInfiniteTransition(label = "musicIndeterminate")
                    val sweepOffset by infiniteTransition.animateFloat(
                        initialValue = -0.35f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "musicIndeterminateOffset"
                    )
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MediaMusicAccent.copy(alpha = 0.14f))
                    ) {
                        val segmentW = maxWidth * 0.35f
                        val xOffset = maxWidth * sweepOffset
                        Box(
                            modifier = Modifier
                                .offset(x = xOffset)
                                .width(segmentW)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MediaMusicAccent,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }
            }
                } else {
                    val defaultMusicTitle = stringResource(R.string.music_title)
                    val idleLabel = remember(preferredPackage, context, defaultMusicTitle) {
                        if (preferredPackage.isBlank()) {
                            defaultMusicTitle
                        } else {
                            runCatching {
                                val pm = context.packageManager
                                pm.getApplicationLabel(pm.getApplicationInfo(preferredPackage, 0)).toString()
                            }.getOrNull()?.takeIf { it.isNotBlank() } ?: defaultMusicTitle
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val compactIdle = isCompactHeight || maxHeight < 160.dp
                        val iconSize = if (maxHeight < 130.dp) 44.dp else 52.dp
                        val labelSize = if (compactIdle) 13.sp else 15.sp
                        val subtitleSize = if (compactIdle) 11.sp else 12.sp
                        val iconSpacer = if (compactIdle) 8.dp else 10.dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_dashboard_music),
                                contentDescription = null,
                                modifier = Modifier.size(iconSize)
                            )
                            Spacer(Modifier.height(iconSpacer))
                            Text(
                                text = idleLabel,
                                color = colors.textPrimary,
                                fontSize = labelSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.music_idle_subtitle),
                                color = colors.textSecondary,
                                fontSize = subtitleSize,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        MusicAppPickerDialog(
            accent = accent,
            currentPreferredPackage = preferredPackage,
            onSelect = { selected ->
                savePreferredMusicPackage(context, selected.packageName)
                preferredPackage = selected.packageName
                showAppPicker = false
                launchAppByPackage(context, selected.packageName)
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

@Composable
private fun MusicAppPickerDialog(
    accent: Color,
    currentPreferredPackage: String,
    onSelect: (MusicPickerApp) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalCockpitColors.current
    var apps by remember { mutableStateOf<List<MusicPickerApp>>(emptyList()) }

    LaunchedEffect(context) {
        apps = withContext(Dispatchers.IO) { loadMusicPickerApps(context) }
    }

    val musicApps = remember(apps) { apps.filter { it.isMusicApp } }
    val otherApps = remember(apps) { apps.filterNot { it.isMusicApp } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 440.dp)
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.cardSurface)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.music_picker_title),
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (musicApps.isNotEmpty()) {
                    item(key = "header_music") {
                        PickerSectionHeader(stringResource(R.string.music_picker_section_music), accent)
                    }
                    items(musicApps, key = { "music:${it.packageName}" }) { app ->
                        MusicPickerAppRow(
                            app = app,
                            isSelected = app.packageName == currentPreferredPackage,
                            accent = accent,
                            onClick = { onSelect(app) }
                        )
                    }
                }

                if (otherApps.isNotEmpty()) {
                    if (musicApps.isNotEmpty()) {
                        item(key = "header_other") {
                            Spacer(Modifier.height(6.dp))
                            PickerSectionHeader(stringResource(R.string.music_picker_section_other), colors.textSecondary)
                        }
                    }
                    items(otherApps, key = { "other:${it.packageName}" }) { app ->
                        MusicPickerAppRow(
                            app = app,
                            isSelected = app.packageName == currentPreferredPackage,
                            accent = accent,
                            onClick = { onSelect(app) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PickerSectionHeader(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun MusicPickerAppRow(
    app: MusicPickerApp,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val colors = LocalCockpitColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        val iconBmp = remember(app.packageName, app.icon) {
            runCatching { app.icon?.toBitmap(72, 72)?.asImageBitmap() }.getOrNull()
        }
        if (iconBmp != null) {
            Image(
                bitmap = iconBmp,
                contentDescription = app.label,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.elevatedSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = app.label,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.common_selected),
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun readControllerProgress(controller: MediaController): Float = runCatching {
    val durationMs = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val pb = controller.playbackState
    if (durationMs <= 0L || pb == null || pb.position < 0L) {
        return@runCatching -1f
    }
    val elapsedSinceUpdate = if (pb.state == PlaybackState.STATE_PLAYING && pb.lastPositionUpdateTime > 0L) {
        ((SystemClock.elapsedRealtime() - pb.lastPositionUpdateTime).coerceAtLeast(0L) * pb.playbackSpeed).toLong()
    } else {
        0L
    }
    ((pb.position + elapsedSinceUpdate).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}.getOrDefault(-1f)

private fun readPreferredMusicPackage(context: Context): String =
    context.getSharedPreferences(LAUNCHER_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PREFERRED_MUSIC_PACKAGE, "")
        .orEmpty()
        .trim()

private fun savePreferredMusicPackage(context: Context, packageName: String) {
    context.getSharedPreferences(LAUNCHER_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_PREFERRED_MUSIC_PACKAGE, packageName)
        .apply()
}

private fun launchAppByPackage(context: Context, packageName: String): Boolean {
    if (packageName.isBlank()) return false
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun loadMusicPickerApps(context: Context): List<MusicPickerApp> {
    val pm = context.packageManager
    val selfPkg = context.packageName

    val musicIntentPkgs = runCatching {
        val musicIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        pm.queryIntentActivities(musicIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcherIntent, 0)
        .mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val pkg = activityInfo.packageName
            if (pkg.isNullOrBlank() || pkg == selfPkg) return@mapNotNull null

            val appInfo = activityInfo.applicationInfo
            val label = runCatching {
                resolveInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: pm.getApplicationLabel(appInfo).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val icon = runCatching {
                resolveInfo.loadIcon(pm) ?: pm.getApplicationIcon(appInfo)
            }.getOrNull()

            val pkgLower = pkg.lowercase()
            val isMusicApp = pkg in musicIntentPkgs ||
                appInfo.category == ApplicationInfo.CATEGORY_AUDIO ||
                KNOWN_MUSIC_PACKAGE_KEYWORDS.any { it in pkgLower }

            MusicPickerApp(
                packageName = pkg,
                label = label,
                icon = icon,
                isMusicApp = isMusicApp
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareByDescending<MusicPickerApp> { it.isMusicApp }.thenBy { it.label.lowercase() })
}
