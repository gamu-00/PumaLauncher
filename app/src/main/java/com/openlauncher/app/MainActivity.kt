package com.openlauncher.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.openlauncher.app.data.effectiveFontScale
import com.openlauncher.app.data.effectiveUiScale
import com.openlauncher.app.data.railPackages
import com.openlauncher.app.data.resolvedAccentColor
import com.openlauncher.app.mediastation.MediaStationState
import com.openlauncher.app.mediastation.ui.MediaStationViewModel
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.components.Sidebar
import com.openlauncher.app.ui.screen.*
import com.openlauncher.app.ui.theme.DarkCockpitPalette
import com.openlauncher.app.ui.theme.LightCockpitPalette
import com.openlauncher.app.ui.theme.LocalPhysicalScreenMetrics
import com.openlauncher.app.ui.theme.OpenLauncherTheme
import com.openlauncher.app.ui.theme.PhysicalScreenMetrics
import com.openlauncher.app.util.AppLanguageManager
import com.openlauncher.app.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private val mediaStationVm: MediaStationViewModel by viewModels()
    private var homePressTrigger by mutableIntStateOf(0)
    private var locationPermissionTrigger by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN) {
            homePressTrigger++
        }
    }

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            vm.startLocationUpdates()
            vm.navigationEngine.startLocationTracking()
            locationPermissionTrigger++
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
            val isLightTheme   = when (settings.dayNightMode) {
                DayNightMode.DARK   -> false
                DayNightMode.LIGHT  -> true
                DayNightMode.AUTO   -> isDayModeVM
                DayNightMode.SYSTEM -> !systemIsDark
            }
            val pickerSlot     by vm.shortcutPickerSlot.collectAsStateWithLifecycle()

            val accent = settings.resolvedAccentColor(isLightTheme)
            val basePalette = if (isLightTheme) LightCockpitPalette else DarkCockpitPalette
            val resolvedCanvasBg = if (settings.useCustomBackgroundColor) {
                Color(settings.backgroundColor)
            } else {
                basePalette.canvasBackground
            }
            val resolvedTextPrimary = (if (isLightTheme) settings.customLightFontColor else settings.customDarkFontColor)
                ?.let { Color(it) }
                ?: basePalette.textPrimary
            val cockpitColors = basePalette.copy(
                canvasBackground = resolvedCanvasBg,
                textPrimary = resolvedTextPrimary
            )
            val bgGradientEnd = Color(settings.gradientEndColor)
            val bgBrush       = if (settings.useGradient) {
                val colors = listOf(resolvedCanvasBg, bgGradientEnd)
                when (settings.gradientDirection) {
                    GradientDirection.TOP_TO_BOTTOM -> androidx.compose.ui.graphics.Brush.verticalGradient(colors)
                    GradientDirection.LEFT_TO_RIGHT -> androidx.compose.ui.graphics.Brush.horizontalGradient(colors)
                    GradientDirection.DIAGONAL      -> androidx.compose.ui.graphics.Brush.linearGradient(colors)
                    GradientDirection.RADIAL        -> androidx.compose.ui.graphics.Brush.radialGradient(colors)
                }
            } else null

            SideEffect {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                val lightBars = if (settingsLoaded) isLightTheme else !systemIsDark
                controller.isAppearanceLightStatusBars = lightBars
                controller.isAppearanceLightNavigationBars = lightBars
            }

            val baseDensity = LocalDensity.current
            val effectiveUiScale = settings.effectiveUiScale()
            val effectiveFontScale = settings.effectiveFontScale()

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val physicalMetrics = PhysicalScreenMetrics(
                    screenWidthDp = maxWidth,
                    screenHeightDp = maxHeight,
                    baseDensity = baseDensity,
                    uiScale = effectiveUiScale
                )
                val isCompactHeight = physicalMetrics.isCompactHeight
                val isPortrait = physicalMetrics.isPortrait
                val dashboardLayoutMode = resolveDashboardLayoutMode(maxWidth, maxHeight)

                val basePhysicalDockWidth = when {
                    isCompactHeight -> (maxWidth * 0.08f).coerceIn(58.dp, 74.dp)
                    isPortrait      -> (maxWidth * 0.13f).coerceIn(60.dp, 82.dp)
                    else            -> (maxWidth * 0.075f).coerceIn(74.dp, 96.dp)
                }
                val dockPhysicalScale = effectiveUiScale.coerceIn(0.84f, 1.24f)
                val maxPhysicalDockWidth = if (isPortrait) {
                    minOf(92.dp, maxWidth * 0.20f)
                } else {
                    minOf(108.dp, maxWidth * 0.13f)
                }
                val targetPhysicalDockWidth = (basePhysicalDockWidth * dockPhysicalScale)
                    .coerceIn(58.dp, maxPhysicalDockWidth.coerceAtLeast(58.dp))
                val dockWidthScaledDp = (targetPhysicalDockWidth.value / effectiveUiScale).dp

                CompositionLocalProvider(
                    LocalPhysicalScreenMetrics provides physicalMetrics,
                    LocalDensity provides Density(
                        density   = baseDensity.density * effectiveUiScale,
                        fontScale = baseDensity.fontScale * effectiveFontScale
                    )
                ) {
                    if (!settingsLoaded) {
                        val loadingBg = if (systemIsDark) {
                            DarkCockpitPalette.canvasBackground
                        } else {
                            LightCockpitPalette.canvasBackground
                        }
                        Box(modifier = Modifier.fillMaxSize().background(loadingBg))
                    } else OpenLauncherTheme(
                        accent        = accent,
                        background    = resolvedCanvasBg,
                        textColor     = resolvedTextPrimary,
                        fontBold      = settings.fontBold,
                        textScale     = 1.0f,
                        appFont       = settings.appFont,
                        isDayMode     = isLightTheme,
                        useCustomBg   = settings.useCustomBackgroundColor,
                        cockpitColors = cockpitColors
                    ) {
                        if (!settings.onboardingCompleted) {
                            OnboardingScreen(
                                accent = accent,
                                onComplete = {
                                    vm.updateSettings { copy(onboardingCompleted = true) }
                                    vm.startLocationUpdates()
                                    vm.navigationEngine.startLocationTracking()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val mediaStationState by vm.mediaStationState.collectAsStateWithLifecycle()
                            var isMapSearchOpen by remember { mutableStateOf(false) }
                            var closeMapSearchTrigger by remember { mutableIntStateOf(0) }
                            val focusManager = LocalFocusManager.current
                            val keyboardController = LocalSoftwareKeyboardController.current

                            LaunchedEffect(homePressTrigger) {
                                if (homePressTrigger > 0) {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    vm.cancelShortcutPicker()
                                    vm.navigate(NavDestination.HOME)
                                    vm.setMediaStationState(MediaStationState.IDLE)
                                    mediaStationVm.resetToCatalog()
                                    mediaStationVm.resetYouTubeState()
                                }
                            }

                            LaunchedEffect(nav) {
                                if (nav != NavDestination.HOME) {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            }

                            BackHandler(enabled = true) {
                                when {
                                    nav != NavDestination.HOME -> {
                                        vm.cancelShortcutPicker()
                                        vm.navigate(NavDestination.HOME)
                                    }
                                    mediaStationState != MediaStationState.IDLE -> {
                                        mediaStationVm.resetToCatalog()
                                        mediaStationVm.resetYouTubeState()
                                        vm.setMediaStationState(MediaStationState.IDLE)
                                    }
                                    isMapSearchOpen -> {
                                        closeMapSearchTrigger++
                                    }
                                    else -> {
                                        // On root dashboard: intercept Back without calling finish()
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .let { m ->
                                        if (bgBrush != null) m.background(bgBrush) else m.background(cockpitColors.canvasBackground)
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
                                        isCompactHeight      = isCompactHeight,
                                        homePressTrigger     = homePressTrigger,
                                        modifier             = sidebarModifier
                                    )
                                }

                                var lastOverlayDest by remember { mutableStateOf(NavDestination.APP_LIBRARY) }
                                LaunchedEffect(nav) {
                                    if (nav != NavDestination.HOME) {
                                        lastOverlayDest = nav
                                    }
                                }

                                val mainPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                    Box(modifier = paneModifier) {
                                        HomeScreen(
                                            settings                  = settings,
                                            nowPlaying                = nowPlaying,
                                            mediaStationState         = mediaStationState,
                                            mediaStationViewModel     = mediaStationVm,
                                            onMediaStationStateChange = { next ->
                                                if (next != MediaStationState.MOVIES) {
                                                    mediaStationVm.resetToCatalog()
                                                }
                                                if (next != MediaStationState.YOUTUBE) {
                                                    mediaStationVm.resetYouTubeState()
                                                }
                                                vm.setMediaStationState(next)
                                            },
                                            homePressTrigger          = homePressTrigger,
                                            closeMapSearchTrigger     = closeMapSearchTrigger,
                                            locationPermissionTrigger = locationPermissionTrigger,
                                            navigationEngine          = vm.navigationEngine,
                                            isHomeVisible             = nav == NavDestination.HOME,
                                            onMapSearchOpenChanged    = { open -> isMapSearchOpen = open },
                                            isCompactHeight           = isCompactHeight,
                                            layoutMode                = dashboardLayoutMode,
                                            modifier                  = Modifier.fillMaxSize()
                                        )

                                        AnimatedVisibility(
                                            visible  = nav != NavDestination.HOME,
                                            enter    = fadeIn() + slideInHorizontally { it / 10 },
                                            exit     = fadeOut() + slideOutHorizontally { it / 10 },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .pointerInput(Unit) {}
                                            ) {
                                                when (lastOverlayDest) {
                                                    NavDestination.APP_LIBRARY -> AppLibraryScreen(
                                                        apps           = apps,
                                                        isLoading      = appsLoading,
                                                        isPickerMode   = pickerSlot != null,
                                                        pickerSlot     = pickerSlot,
                                                        accent         = accent,
                                                        onAppClick     = { app -> vm.recordRecentApp(app.packageName) },
                                                        onPickerSelect = { slot, app -> vm.assignShortcut(slot, app) },
                                                        pinnedPackages = settings.pinnedApps.toSet(),
                                                        hiddenPackages = settings.hiddenPackages.toSet(),
                                                        showHiddenApps = settings.showHiddenApps,
                                                        onPinApp       = { app -> vm.togglePinned(app.packageName) },
                                                        onHideApp      = { app -> vm.toggleHiddenApp(app.packageName) }
                                                    )

                                                    NavDestination.SETTINGS -> SettingsScreen(
                                                        settings = settings,
                                                        accent   = accent,
                                                        onUpdate = { block -> vm.updateSettings(block) },
                                                        onReset  = { vm.resetSettings() }
                                                    )

                                                    NavDestination.HOME -> Unit
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    sidebarContent(
                                        Modifier
                                            .width(dockWidthScaledDp)
                                            .fillMaxHeight()
                                    )
                                    mainPane(Modifier.weight(1f).fillMaxHeight())
                                }
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
        if (vm.navigationEngine.activeRoute == null) {
            vm.navigationEngine.stopLocationTracking()
        }
    }

    override fun onStart() {
        super.onStart()
        vm.startLocationUpdates()
        vm.navigationEngine.startLocationTracking()
    }
}
