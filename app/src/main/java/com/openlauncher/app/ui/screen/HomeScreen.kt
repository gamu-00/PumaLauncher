package com.openlauncher.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.SettingsRepository
import com.openlauncher.app.data.resolvedAccentColor
import com.openlauncher.app.mediastation.MediaStationCard
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.ui.theme.*
import com.openlauncher.app.ui.widget.NowPlayingWidget
import kotlinx.coroutines.launch

const val PKG_YT_MUSIC = "com.google.android.apps.youtube.music"

/**
 * Single-activity proportional layout (responsive across 16:9 Ford Puma and 21:9 Toyota RAV4):
 * - Left column (50%): Native MapLibre Turn-by-Turn Navigator
 * - Right column (50%):
 *   * Top (0.58f): [MediaStationCard]
 *   * Bottom (0.42f): NowPlayingWidget
 */
@Composable
fun HomeScreen(
    settings: AppSettings,
    nowPlaying: NowPlayingState?,
    isDayMode: Boolean = false,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onTapNowPlaying: () -> Unit,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = settings.resolvedAccentColor()
    val settingsRepo = remember(context) { SettingsRepository(context.applicationContext) }
    val uiScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val uiScale = (maxHeight.value / 480f).coerceIn(0.85f, 1.35f)
        val outerPad = (6f * uiScale).dp
        val gap = (10f * uiScale).dp
        val moduleRadius = (18f * uiScale).dp
        val moduleShape = RoundedCornerShape(moduleRadius)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = outerPad, bottom = outerPad, end = outerPad),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            // -- Left column (50%): Native MapLibre Turn-by-Turn Navigator --
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
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
            )

            // -- Right column (50%): Video Slot (58%) + NowPlayingWidget (42%) --
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                MediaStationCard(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxWidth()
                        .clip(moduleShape)
                        .background(AaPanel)
                        .border(1.dp, AaBorder, moduleShape)
                ) {
                    NowPlayingWidget(
                        state = nowPlaying,
                        accent = accent,
                        carPlayPackage = settings.carPlayPackage.ifEmpty { PKG_YT_MUSIC },
                        androidAutoPackage = settings.androidAutoPackage,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrev = onPrev,
                        onLaunchCarPlay = { onLaunchApp(PKG_YT_MUSIC) },
                        onLaunchAndroidAuto = {
                            if (settings.androidAutoPackage.isNotEmpty()) {
                                onLaunchApp(settings.androidAutoPackage)
                            }
                        },
                        onTapToOpenApp = onTapNowPlaying,
                        isDayMode = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
