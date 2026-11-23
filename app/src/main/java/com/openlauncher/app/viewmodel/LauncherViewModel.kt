package com.openlauncher.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.RAIL_SLOTS
import com.openlauncher.app.data.SettingsRepository
import com.openlauncher.app.data.railPackages
import com.openlauncher.app.mediastation.MediaStationState
import com.openlauncher.app.model.AppInfo
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.service.MediaNotificationListenerService
import com.openlauncher.app.ui.screen.NavigationEngine
import com.openlauncher.app.ui.screen.launchInFullscreen
import com.openlauncher.app.util.LocationCompassManager
import com.openlauncher.app.util.SunriseSunset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val locationMgr = LocationCompassManager(application)
    val navigationEngine = NavigationEngine(application)

    // ── Settings ──────────────────────────────────────────────────────────────
    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .onEach { _settingsLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun updateSettings(block: AppSettings.() -> AppSettings) {
        viewModelScope.launch { settingsRepo.updateSettings { it.block() } }
    }

    fun resetSettings() {
        viewModelScope.launch { settingsRepo.resetToDefaults() }
    }

    fun setHomeAddress(label: String, lat: Double, lon: Double) {
        viewModelScope.launch { settingsRepo.setHomeAddress(label, lat, lon) }
    }

    fun clearHomeAddress() {
        viewModelScope.launch { settingsRepo.clearHomeAddress() }
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    private val _nav = MutableStateFlow(NavDestination.HOME)
    val nav: StateFlow<NavDestination> = _nav

    fun navigate(dest: NavDestination) { _nav.value = dest }

    private val _mediaStationState = MutableStateFlow(MediaStationState.IDLE)
    val mediaStationState: StateFlow<MediaStationState> = _mediaStationState

    fun setMediaStationState(state: MediaStationState) {
        _mediaStationState.value = state
    }

    // ── Nav Rail Pickers & App Pinning / Hiding ───────────────────────────────
    private val _shortcutPickerSlot = MutableStateFlow<Int?>(null)
    val shortcutPickerSlot: StateFlow<Int?> = _shortcutPickerSlot

    fun startShortcutPicker(slot: Int) {
        _shortcutPickerSlot.value = slot
        _nav.value = NavDestination.APP_LIBRARY
    }

    /** Replaces the app shown at Nav Rail position [slot] with [app] (keeps pinned/recent status). */
    fun assignShortcut(slot: Int, app: AppInfo) {
        val newPkg = app.packageName
        updateSettings {
            val old = railPackages().getOrNull(slot)
            if (old == null) {
                copy(pinnedApps = (pinnedApps + newPkg).distinct().take(RAIL_SLOTS))
            } else if (old in pinnedApps) {
                copy(pinnedApps = pinnedApps.map { if (it == old) newPkg else it }.distinct())
            } else {
                copy(recentApps = recentApps.map { if (it == old) newPkg else it }.distinct())
            }
        }
        _shortcutPickerSlot.value = null
        _nav.value = NavDestination.HOME
    }

    fun cancelShortcutPicker() {
        _shortcutPickerSlot.value = null
    }

    /** Pins the app (max [RAIL_SLOTS] pinned) or unpins it if already pinned. */
    fun togglePinned(pkg: String) {
        updateSettings {
            if (pkg in pinnedApps) copy(pinnedApps = pinnedApps - pkg)
            else copy(
                pinnedApps = (pinnedApps + pkg).takeLast(RAIL_SLOTS),
                recentApps = recentApps - pkg
            )
        }
    }

    /** Hides or unhides [pkg] in the App Drawer. */
    fun toggleHiddenApp(pkg: String) {
        updateSettings {
            if (pkg in hiddenPackages) {
                copy(hiddenPackages = hiddenPackages - pkg)
            } else {
                copy(hiddenPackages = (hiddenPackages + pkg).distinct())
            }
        }
    }

    fun restoreAllHiddenApps() {
        updateSettings { copy(hiddenPackages = emptyList(), showHiddenApps = false) }
    }

    private fun recordRecent(pkg: String) {
        if (pkg == getApplication<Application>().packageName) return
        updateSettings {
            if (pkg in pinnedApps) this
            else copy(recentApps = (listOf(pkg) + recentApps.filter { it != pkg }).take(8))
        }
    }

    private fun ensureDefaultPins() {
        viewModelScope.launch {
            settings.first { _settingsLoaded.value }
            val s = settings.value
            if (s.pinnedInitialized) return@launch
            val pm = getApplication<Application>().packageManager
            fun installed(p: String) = runCatching { pm.getPackageInfo(p, 0); true }.getOrDefault(false)
            val sync = listOf(
                "cn.manstep.phonemirrorBox", "com.carlinkit.car", "com.autokit", "com.carletter.car"
            ).firstOrNull { installed(it) }
            val defaults = listOfNotNull("com.google.android.apps.maps".takeIf { installed(it) }, sync)
            updateSettings { if (pinnedInitialized) this else copy(pinnedApps = defaults, pinnedInitialized = true) }
        }
    }

    // ── App Drawer Loading (ACTION_MAIN + CATEGORY_LAUNCHER) ──────────────────
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading

    fun loadInstalledApps() {
        if (_appsLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _appsLoading.value = true
            val appContext = getApplication<Application>()
            val pm = appContext.packageManager
            val selfPackageName = appContext.packageName
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

            _apps.value = pm.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { resolveInfo ->
                    try {
                        val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                        val pkg = activityInfo.packageName
                        if (pkg.isNullOrBlank() || pkg == selfPackageName) return@mapNotNull null
                        val appInfo = activityInfo.applicationInfo
                        val label = resolveInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                            ?: pm.getApplicationLabel(appInfo).toString()
                        if (label.isBlank()) return@mapNotNull null
                        val icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                            ?: pm.getApplicationIcon(appInfo)
                        AppInfo(
                            packageName = pkg,
                            appName     = label,
                            icon        = icon,
                            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    } catch (_: Exception) { null }
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
            _appsLoading.value = false
        }
    }

    fun recordRecentApp(pkg: String) {
        recordRecent(pkg)
    }

    fun launchApp(packageName: String) {
        val app = getApplication<Application>()
        if (launchInFullscreen(app, packageName)) {
            recordRecent(packageName)
        }
    }

    // ── Media Session ─────────────────────────────────────────────────────────
    val nowPlaying: StateFlow<NowPlayingState?> = MediaNotificationListenerService.nowPlaying
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun refreshMedia() {
        MediaNotificationListenerService.requestRefresh(getApplication())
    }

    // ── Location & Day/Night ──────────────────────────────────────────────────
    private val minuteTicker = flow { while (true) { emit(Unit); delay(60_000L) } }

    val isDayMode: StateFlow<Boolean> = combine(settings, locationMgr.location, minuteTicker) { s, loc, _ ->
        when (s.dayNightMode) {
            DayNightMode.DARK   -> false
            DayNightMode.LIGHT  -> true
            DayNightMode.AUTO   -> if (loc != null) {
                SunriseSunset.isDay(loc.latitude, loc.longitude)
            } else {
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 7..18
            }
            DayNightMode.SYSTEM -> false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun startLocationUpdates() = locationMgr.start()
    fun stopLocationUpdates()  = locationMgr.stop()

    // ── Connectivity ──────────────────────────────────────────────────────────
    private val _isWifi = MutableStateFlow(false)
    private val _isData = MutableStateFlow(false)
    val isWifi: StateFlow<Boolean> = _isWifi
    val isData: StateFlow<Boolean> = _isData

    fun refreshConnectivity() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            _isWifi.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            _isData.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            val connected = info?.isConnected == true
            @Suppress("DEPRECATION")
            val type = info?.type
            _isWifi.value = connected && type == ConnectivityManager.TYPE_WIFI
            _isData.value = connected && type == ConnectivityManager.TYPE_MOBILE
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationMgr.stop()
        navigationEngine.shutdown()
    }

    init {
        ensureDefaultPins()
        loadInstalledApps()
        refreshConnectivity()
    }
}
