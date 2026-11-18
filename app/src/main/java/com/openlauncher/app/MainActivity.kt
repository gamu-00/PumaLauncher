package com.openlauncher.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.GradientDirection
import com.openlauncher.app.data.railPackages
import com.openlauncher.app.data.resolvedAccentColor
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.components.Sidebar
import com.openlauncher.app.ui.screen.*
import com.openlauncher.app.ui.theme.OpenLauncherTheme
import com.openlauncher.app.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            vm.startLocationUpdates()
        }
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configureSystemBars()

        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            runCatching {
                locationPermissions.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        setContent {
            val settingsLoaded by vm.settingsLoaded.collectAsStateWithLifecycle()
            val settings       by vm.settings.collectAsStateWithLifecycle()
            val nav            by vm.nav.collectAsStateWithLifecycle()
            val apps           by vm.apps.collectAsStateWithLifecycle()
            val appsLoading    by vm.appsLoading.collectAsStateWithLifecycle()
            val nowPlaying     by vm.nowPlaying.collectAsStateWithLifecycle()
            val isWifi         by vm.isWifi.collectAsStateWithLifecycle()
            val isData         by vm.isData.collectAsStateWithLifecycle()
            val isDayModeVM    by vm.isDayMode.collectAsStateWithLifecycle()
            val systemIsDark   = isSystemInDarkTheme()
            val isDayMode      = if (settings.dayNightMode == DayNightMode.SYSTEM) !systemIsDark else isDayModeVM
            val pickerSlot      by vm.shortcutPickerSlot.collectAsStateWithLifecycle()
            val appPickerTarget by vm.appPickerTarget.collectAsStateWithLifecycle()

            val accent        = settings.resolvedAccentColor()
            val bg            = if (settings.useCustomBackgroundColor) {
                Color(settings.backgroundColor)
            } else {
                if (isDayMode) Color(0xFFEEEEEE) else com.openlauncher.app.ui.theme.AaBackground
            }
            val textColor     = if (isDayMode) Color(0xFF111111) else Color(settings.fontColor)
            val bgGradientEnd = Color(settings.gradientEndColor)
            val bgBrush       = if (settings.useCustomBackgroundColor && settings.useGradient) {
                val colors = listOf(bg, bgGradientEnd)
                when (settings.gradientDirection) {
                    GradientDirection.TOP_TO_BOTTOM -> androidx.compose.ui.graphics.Brush.verticalGradient(colors)
                    GradientDirection.LEFT_TO_RIGHT -> androidx.compose.ui.graphics.Brush.horizontalGradient(colors)
                    GradientDirection.DIAGONAL -> androidx.compose.ui.graphics.Brush.linearGradient(colors)
                    GradientDirection.RADIAL -> androidx.compose.ui.graphics.Brush.radialGradient(colors)
                }
            } else null

            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density   = baseDensity.density * settings.uiScale,
                    fontScale = baseDensity.fontScale
                )
            ) {
                if (!settingsLoaded) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                } else OpenLauncherTheme(
                    accent      = accent,
                    background  = bg,
                    textColor   = textColor,
                    fontBold    = settings.fontBold,
                    textScale   = settings.textScale,
                    appFont     = settings.appFont,
                    isDayMode   = isDayMode,
                    useCustomBg = settings.useCustomBackgroundColor
                ) {
                    if (!settings.onboardingCompleted) {
                        OnboardingScreen(
                            accent = accent,
                            onComplete = {
                                vm.updateSettings { copy(onboardingCompleted = true) }
                                vm.startLocationUpdates()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .let { m ->
                                    if (bgBrush != null) m.background(bgBrush) else m.background(bg)
                                }
                        ) {
                            if (settings.wallpaperUri.isNotEmpty()) {
                                AsyncImage(
                                    model              = android.net.Uri.parse(settings.wallpaperUri),
                                    contentDescription = null,
                                    contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier           = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = settings.wallpaperDim))
                                )
                            }

                            val sidebarContent: @Composable (Modifier) -> Unit = { sidebarModifier ->
                                Sidebar(
                                    currentDest          = nav,
                                    railPackages         = settings.railPackages(),
                                    pinnedPackages       = settings.pinnedApps.toSet(),
                                    isWifi               = isWifi,
                                    isData               = isData,
                                    installedIconFor     = { pkg ->
                                        apps.find { it.packageName == pkg }?.icon
                                            ?: runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
                                    },
                                    onNavigate           = { dest ->
                                        vm.cancelShortcutPicker()
                                        vm.cancelCarPlayPicker()
                                        vm.exitRearrangeMode()
                                        vm.navigate(dest)
                                    },
                                    onRailClick          = { pkg -> vm.launchApp(pkg) },
                                    onTogglePin          = { pkg -> vm.togglePinned(pkg) },
                                    onReplace            = { index ->
                                        vm.startShortcutPicker(index)
                                    },
                                    accent               = accent,
                                    coldBootAutoLaunch   = settings.coldBootAutoLaunch,
                                    coldBootDelaySeconds = settings.coldBootDelaySeconds,
                                    onUpdateColdBoot     = { enabled, delaySec ->
                                        vm.updateSettings {
                                            copy(
                                                coldBootAutoLaunch = enabled,
                                                coldBootDelaySeconds = delaySec.coerceIn(1, 30)
                                            )
                                        }
                                    },
                                    modifier             = sidebarModifier
                                )
                            }

                            val mainPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                AnimatedContent(
                                    targetState    = nav,
                                    transitionSpec = {
                                        fadeIn() + slideInHorizontally { it / 10 } togetherWith
                                        fadeOut() + slideOutHorizontally { -it / 10 }
                                    },
                                    modifier = paneModifier,
                                    label    = "pane_transition"
                                ) { destination ->
                                    when (destination) {
                                        NavDestination.HOME -> HomeScreen(
                                            settings        = settings,
                                            nowPlaying      = nowPlaying,
                                            isDayMode       = isDayMode,
                                            onPlayPause     = vm::playPause,
                                            onNext          = vm::skipNext,
                                            onPrev          = vm::skipPrev,
                                            onTapNowPlaying = {
                                                val pkg = nowPlaying?.controller?.packageName
                                                if (!pkg.isNullOrEmpty()) vm.launchApp(pkg)
                                            },
                                            onLaunchApp     = { pkg -> vm.launchApp(pkg) }
                                        )

                                        NavDestination.APP_LIBRARY -> AppLibraryScreen(
                                            apps                = apps,
                                            isLoading           = appsLoading,
                                            isPickerMode        = pickerSlot != null,
                                            pickerSlot          = pickerSlot,
                                            isCarPlayPickerMode = appPickerTarget != null,
                                            carPlayPickerLabel  = when (appPickerTarget) {
                                                LauncherViewModel.AppPickerTarget.ANDROID_AUTO -> "CHOOSE ANDROID AUTO APP"
                                                LauncherViewModel.AppPickerTarget.PIP          -> "CHOOSE PIP APP"
                                                LauncherViewModel.AppPickerTarget.RADIO        -> "CHOOSE RADIO APP"
                                                else -> "CHOOSE CARPLAY APP"
                                            },
                                            accent              = accent,
                                            onAppClick          = { app -> vm.recordRecentApp(app.packageName) },
                                            onPickerSelect      = { slot, app -> vm.assignShortcut(slot, app) },
                                            onCarPlaySelect     = { app -> vm.assignPickerApp(app) },
                                            pinnedPackages      = settings.pinnedApps.toSet(),
                                            hiddenPackages      = settings.hiddenPackages.toSet(),
                                            showHiddenApps      = settings.showHiddenApps,
                                            onPinApp            = { app -> vm.togglePinned(app.packageName) },
                                            onHideApp           = { app -> vm.toggleHiddenApp(app.packageName) }
                                        )

                                        NavDestination.SETTINGS -> SettingsScreen(
                                            settings = settings,
                                            accent   = accent,
                                            onUpdate = { block -> vm.updateSettings(block) },
                                            onReset  = { vm.resetSettings() }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                sidebarContent(
                                    Modifier
                                        .weight(0.08f)
                                        .widthIn(min = 68.dp, max = 104.dp)
                                        .fillMaxHeight()
                                )
                                mainPane(Modifier.weight(0.92f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshConnectivity()
        vm.refreshMedia()
        vm.loadInstalledApps()
    }

    override fun onStop() {
        super.onStop()
        vm.stopLocationUpdates()
    }

    override fun onStart() {
        super.onStart()
        vm.startLocationUpdates()
    }
}
