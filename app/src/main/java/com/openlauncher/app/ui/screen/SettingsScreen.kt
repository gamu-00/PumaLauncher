package com.openlauncher.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.data.AppFont
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.GradientDirection
import com.openlauncher.app.data.SidebarPosition
import com.openlauncher.app.data.THEME_COLOR_PRESETS
import com.openlauncher.app.data.UnitSystem
import com.openlauncher.app.ui.components.ColorPickerDialog
import com.openlauncher.app.ui.components.ConfirmDialog
import com.openlauncher.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    accent: Color,
    onUpdate: (AppSettings.() -> AppSettings) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showResetDialog       by remember { mutableStateOf(false) }
    var showAccentPicker      by remember { mutableStateOf(false) }
    var showBgPicker          by remember { mutableStateOf(false) }
    var showGradientEndPicker by remember { mutableStateOf(false) }
    var showFontColorPicker   by remember { mutableStateOf(false) }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onUpdate { copy(wallpaperUri = it.toString()) }
        }
    }

    val isDayMode = LocalDayMode.current
    val screenBg  = MaterialTheme.colorScheme.background
    val onAccent  = if (accent.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Title ────────────────────────────────────────────────────────────
            Text(
                text          = "IMPOSTAZIONI",
                style         = MaterialTheme.typography.titleLarge,
                color         = AaTextPrimary,
                letterSpacing = 3.sp,
                fontSize      = 18.sp
            )

            Spacer(Modifier.height(4.dp))

            // ── 1. Selettore Colore Tema (in cima) ───────────────────────────────
            SettingsSection("Colore Tema", accent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    THEME_COLOR_PRESETS.forEach { preset ->
                        val isSelected = settings.accentColorHex.equals(preset.hex, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(AaCardShape)
                                .clickable {
                                    onUpdate {
                                        copy(
                                            accentColorHex = preset.hex,
                                            accentColor = preset.color.toArgb()
                                        )
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(preset.color)
                                    .border(
                                        width = if (isSelected) 4.dp else 1.5.dp,
                                        color = if (isSelected) Color.White else AaBorder,
                                        shape = CircleShape
                                    )
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = preset.name,
                                        tint = if (preset.color.luminance() > 0.5f) Color.Black else Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = preset.name,
                                color = if (isSelected) Color.White else AaTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── App Drawer & App Nascoste ────────────────────────────────────────
            SettingsSection("App Drawer", accent) {
                SettingsRow(
                    label    = "Mostra app nascoste",
                    sublabel = if (settings.hiddenPackages.isEmpty())
                        "Nessuna app nascosta (tieni premuto un'app nel drawer per nasconderla)"
                    else
                        "${settings.hiddenPackages.size} app nascoste — attiva per visualizzarle nel drawer",
                    icon     = Icons.Default.Visibility,
                    accent   = accent
                ) {
                    BigSwitch(
                        checked         = settings.showHiddenApps,
                        onCheckedChange = { onUpdate { copy(showHiddenApps = it) } },
                        colors          = switchColors(accent)
                    )
                }

                if (settings.hiddenPackages.isNotEmpty()) {
                    SettingsDivider()
                    SettingsButton(
                        label    = "Ripristina tutte le app nascoste (${settings.hiddenPackages.size})",
                        sublabel = settings.hiddenPackages.joinToString(", "),
                        icon     = Icons.Default.Restore,
                        accent   = accent,
                        onClick  = {
                            onUpdate { copy(hiddenPackages = emptyList(), showHiddenApps = false) }
                        }
                    )
                }
            }

            // ── Permissions ──────────────────────────────────────────────────────
            SettingsSection("Permissions", accent) {
                val isMediaConnected by com.openlauncher.app.service.MediaListenerService.isConnected.collectAsState()

                var permissionRefresh by remember { mutableIntStateOf(0) }
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permissionRefresh++
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val isNotificationListenerEnabled = remember(permissionRefresh) {
                    androidx.core.app.NotificationManagerCompat
                        .getEnabledListenerPackages(context)
                        .contains(context.packageName)
                }

                val canDrawOverlays = remember(permissionRefresh) {
                    android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
                }
                val hasLocation = remember(permissionRefresh) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                val isDefaultLauncher = remember(permissionRefresh) {
                    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    context.packageManager.resolveActivity(
                        home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                    )?.activityInfo?.packageName == context.packageName
                }

                val homeRoleLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { permissionRefresh++ }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissionRefresh++ }

                SettingsButton(
                    label    = "Set as Default Launcher",
                    sublabel = if (isDefaultLauncher) "Active — Open Launcher is the home app"
                               else "Required so the head unit boots into Open Launcher",
                    icon     = Icons.Default.Home,
                    accent   = if (isDefaultLauncher) accent else Color(0xFF993333),
                    onClick  = {
                        var launched = false
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
                                !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
                            ) {
                                launched = runCatching {
                                    homeRoleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                                }.isSuccess
                            }
                        }
                        if (!launched) {
                            launched = runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.isSuccess
                        }
                        if (!launched) {
                            runCatching {
                                context.startActivity(
                                    Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                )
                SettingsDivider()
                SettingsButton(
                    label    = "Notification Access",
                    sublabel = if (isNotificationListenerEnabled || isMediaConnected) "Granted — media controls active" else "Required for Now Playing widget",
                    icon     = if (isNotificationListenerEnabled || isMediaConnected) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    accent   = if (isNotificationListenerEnabled || isMediaConnected) accent else Color(0xFF993333),
                    onClick  = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                SettingsDivider()
                SettingsButton(
                    label    = "Draw Over Other Apps",
                    sublabel = if (canDrawOverlays) "Granted — PIP overlay enabled" else "Required for PIP floating window",
                    icon     = if (canDrawOverlays) Icons.Default.Layers else Icons.Default.LayersClear,
                    accent   = if (canDrawOverlays) accent else Color(0xFF993333),
                    onClick  = {
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                )
                SettingsDivider()
                SettingsButton(
                    label    = "Location Access",
                    sublabel = if (hasLocation) "Granted — GPS, compass & weather active" else "Required for compass, speed & weather",
                    icon     = if (hasLocation) Icons.Default.LocationOn else Icons.Default.LocationOff,
                    accent   = if (hasLocation) accent else Color(0xFF993333),
                    onClick  = {
                        if (!hasLocation) {
                            locationPermissionLauncher.launch(arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        } else {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                )
            }

            // ── Vehicle Name ─────────────────────────────────────────────────────
            SettingsSection("Vehicle", accent) {
                var nameInput by remember(settings.vehicleName) { mutableStateOf(settings.vehicleName) }
                SettingsRow(
                    label    = "Vehicle Name",
                    sublabel = "",
                    icon     = Icons.Default.DirectionsCar,
                    accent   = accent
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value         = nameInput,
                            onValueChange = { nameInput = it },
                            placeholder   = { Text("MY CAR", color = if (isDayMode) Color(0xFF999999) else Color(0xFF444444), fontSize = 12.sp) },
                            singleLine    = true,
                            textStyle     = LocalTextStyle.current.copy(fontSize = 12.sp, color = if (isDayMode) Color(0xFF111111) else Color.White),
                            colors        = outlinedFieldColors(accent),
                            modifier      = Modifier.width(140.dp)
                        )
                        if (nameInput != settings.vehicleName) {
                            IconButton(onClick = { onUpdate { copy(vehicleName = nameInput) } }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, "Save", tint = accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                SettingsDivider()

                SettingsRow(
                    label    = "Sidebar Position",
                    sublabel = when (settings.sidebarPosition) {
                        SidebarPosition.LEFT   -> "Left side"
                        SidebarPosition.RIGHT  -> "Right side"
                        SidebarPosition.BOTTOM -> "Bottom"
                    },
                    icon     = Icons.Default.SwapHoriz,
                    accent   = accent
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SidebarPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = settings.sidebarPosition == pos,
                                onClick  = { onUpdate { copy(sidebarPosition = pos) } },
                                label    = {
                                    Text(
                                        when (pos) {
                                            SidebarPosition.LEFT   -> "Left"
                                            SidebarPosition.RIGHT  -> "Right"
                                            SidebarPosition.BOTTOM -> "Bottom"
                                        },
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent,
                                    selectedLabelColor     = onAccent
                                )
                            )
                        }
                    }
                }

                if (settings.sidebarPosition == SidebarPosition.BOTTOM) {
                    SettingsDivider()
                    SettingsRow(
                        label    = "Shortcuts Side",
                        sublabel = if (settings.bottomBarShortcutsRight) "Right — nav buttons on left" else "Left — nav buttons on right",
                        icon     = Icons.Default.FormatAlignRight,
                        accent   = accent
                    ) {
                        BigSwitch(
                            checked         = settings.bottomBarShortcutsRight,
                            onCheckedChange = { onUpdate { copy(bottomBarShortcutsRight = it) } },
                            colors          = switchColors(accent)
                        )
                    }
                }

                SettingsDivider()

                SettingsRow(
                    label    = "Unit System",
                    sublabel = if (settings.unitSystem == UnitSystem.METRIC) "Metric (°C, km)" else "Imperial (°F, mi)",
                    icon     = Icons.Default.Straighten,
                    accent   = accent
                ) {
                    Row {
                        FilterChip(
                            selected = settings.unitSystem == UnitSystem.METRIC,
                            onClick  = { onUpdate { copy(unitSystem = UnitSystem.METRIC) } },
                            label    = { Text("Metric", fontSize = 11.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor     = onAccent
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        FilterChip(
                            selected = settings.unitSystem == UnitSystem.IMPERIAL,
                            onClick  = { onUpdate { copy(unitSystem = UnitSystem.IMPERIAL) } },
                            label    = { Text("Imperial", fontSize = 11.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor     = onAccent
                            )
                        )
                    }
                }
            }

            // ── Sidebar Shortcuts ─────────────────────────────────────────────────
            SettingsSection("Nav Rail", accent) {
                if (settings.pinnedApps.isEmpty()) {
                    SettingsRow(
                        label    = "Nessuna app fissata",
                        sublabel = "Tieni premuto un'app nel drawer o nella rail per fissarla",
                        icon     = Icons.Default.PushPin,
                        accent   = accent
                    ) {}
                }
                settings.pinnedApps.forEachIndexed { index, pkg ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(
                        label    = "Fissata ${index + 1}",
                        sublabel = pkg,
                        icon     = Icons.Default.PushPin,
                        accent   = accent
                    ) {
                        IconButton(
                            onClick  = { onUpdate { copy(pinnedApps = pinnedApps.filter { it != pkg }) } },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, "Rimuovi", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            SettingsSection("Avvio automatico", accent) {
                SettingsRow(
                    label    = "Avvio a freddo",
                    sublabel = "Lancia Google Maps nel riquadro sinistro",
                    icon     = Icons.Default.RocketLaunch,
                    accent   = accent
                ) {
                    BigSwitch(
                        checked         = settings.coldBootAutoLaunch,
                        onCheckedChange = { onUpdate { copy(coldBootAutoLaunch = it) } },
                        colors          = switchColors(accent)
                    )
                }
                SettingsDivider()
                SettingsRow(
                    label    = "Ritardo avvio",
                    sublabel = "Attesa GPS e rete prima del lancio",
                    icon     = Icons.Default.Timer,
                    accent   = accent
                ) {
                    Stepper(
                        valueText = "${settings.coldBootDelaySeconds}s",
                        accent    = accent,
                        onMinus   = { onUpdate { copy(coldBootDelaySeconds = (coldBootDelaySeconds - 1).coerceIn(1, 30)) } },
                        onPlus    = { onUpdate { copy(coldBootDelaySeconds = (coldBootDelaySeconds + 1).coerceIn(1, 30)) } }
                    )
                }
            }

            // ── Appearance ───────────────────────────────────────────────────────
            SettingsSection("Appearance", accent) {
                SettingsRow(
                    label    = "Display Mode",
                    sublabel = when (settings.dayNightMode) {
                        DayNightMode.DARK   -> "Always dark"
                        DayNightMode.LIGHT  -> "Always light"
                        DayNightMode.AUTO   -> "Sunrise / sunset"
                        DayNightMode.SYSTEM -> "Follows system theme"
                    },
                    icon = when (settings.dayNightMode) {
                        DayNightMode.DARK   -> Icons.Default.NightlightRound
                        DayNightMode.LIGHT  -> Icons.Default.LightMode
                        DayNightMode.AUTO   -> Icons.Default.Brightness4
                        DayNightMode.SYSTEM -> Icons.Default.PhoneAndroid
                    },
                    accent = accent
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayNightMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.dayNightMode == mode,
                                onClick  = { onUpdate { copy(dayNightMode = mode) } },
                                label    = {
                                    Text(
                                        text      = when (mode) {
                                            DayNightMode.DARK   -> "Dark"
                                            DayNightMode.LIGHT  -> "Light"
                                            DayNightMode.AUTO   -> "Sunset"
                                            DayNightMode.SYSTEM -> "System"
                                        },
                                        fontSize  = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent,
                                    selectedLabelColor     = onAccent
                                )
                            )
                        }
                    }
                }

                SettingsDivider()

                // Background color + gradient
                SettingsRow(
                    label    = "Background",
                    sublabel = if (settings.useGradient) "Gradient" else "Solid color",
                    icon     = Icons.Default.FormatColorFill,
                    accent   = accent
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(AaCardShape)
                                .background(Color(settings.backgroundColor))
                                .clickable { showBgPicker = true }
                        )
                        if (settings.useGradient) {
                            Icon(
                                Icons.Default.ArrowForward, null,
                                tint = if (isDayMode) Color(0xFF999999) else Color(0xFF555555), modifier = Modifier.size(14.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(AaCardShape)
                                    .background(Color(settings.gradientEndColor))
                                    .clickable { showGradientEndPicker = true }
                            )
                        }
                        if (settings.useCustomBackgroundColor) {
                            TextButton(
                                onClick = {
                                    onUpdate {
                                        copy(
                                            useCustomBackgroundColor = false,
                                            backgroundColor = Color.Black.toArgb(),
                                            useGradient = false
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("DEFAULT", color = accent, fontSize = 9.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }

                SettingsDivider()

                SettingsRow(
                    label    = "Font Color",
                    sublabel = "Custom text color in dark mode",
                    icon     = Icons.Default.FormatSize,
                    accent   = accent
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(settings.fontColor))
                            .clickable { showFontColorPicker = true }
                    )
                }

                SettingsDivider()

                SettingsRow(
                    label    = "Use Gradient",
                    sublabel = "Blend two colors as background",
                    icon     = Icons.Default.Gradient,
                    accent   = accent
                ) {
                    BigSwitch(
                        checked         = settings.useGradient,
                        onCheckedChange = { onUpdate { copy(useGradient = it) } },
                        colors          = switchColors(accent)
                    )
                }

                if (settings.useGradient) {
                    SettingsDivider()
                    SettingsRow(
                        label    = "Gradient Direction",
                        sublabel = when (settings.gradientDirection) {
                            GradientDirection.TOP_TO_BOTTOM -> "Top to Bottom"
                            GradientDirection.LEFT_TO_RIGHT -> "Left to Right"
                            GradientDirection.DIAGONAL      -> "Diagonal (Linear)"
                            GradientDirection.RADIAL        -> "Radial (Circular)"
                        },
                        icon     = Icons.Default.TrendingFlat,
                        accent   = accent
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            GradientDirection.entries.forEach { dir ->
                                FilterChip(
                                    selected = settings.gradientDirection == dir,
                                    onClick  = { onUpdate { copy(gradientDirection = dir) } },
                                    label    = {
                                        Text(
                                            text      = when (dir) {
                                                GradientDirection.TOP_TO_BOTTOM -> "Vertical"
                                                GradientDirection.LEFT_TO_RIGHT -> "Horizontal"
                                                GradientDirection.DIAGONAL      -> "Diagonal"
                                                GradientDirection.RADIAL        -> "Radial"
                                            },
                                            fontSize  = 9.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent,
                                        selectedLabelColor     = onAccent
                                    )
                                )
                            }
                        }
                    }
                }

                SettingsDivider()

                SettingsButton(
                    label    = "Set Wallpaper",
                    sublabel = if (settings.wallpaperUri.isNotEmpty()) "Custom wallpaper active" else "Choose image from gallery",
                    icon     = Icons.Default.Wallpaper,
                    accent   = accent,
                    onClick  = { wallpaperPicker.launch(arrayOf("image/*")) }
                )
                if (settings.wallpaperUri.isNotEmpty()) {
                    Column {
                        SettingsRow(
                            label    = "Wallpaper Dim",
                            sublabel = "${"%.0f".format(settings.wallpaperDim * 100)}%",
                            icon     = Icons.Default.BrightnessLow,
                            accent   = accent
                        ) {}
                        Slider(
                            value         = settings.wallpaperDim,
                            onValueChange = { onUpdate { copy(wallpaperDim = it) } },
                            valueRange    = 0f..0.95f,
                            steps         = 18,
                            colors        = sliderColors(accent),
                            modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick  = { onUpdate { copy(wallpaperUri = "") } },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("REMOVE WALLPAPER", color = Color(0xFF993333), fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            // ── Typography ───────────────────────────────────────────────────────
            SettingsSection("Typography", accent) {
                SettingsRow(
                    label    = "Font",
                    sublabel = fontDisplayName(settings.appFont),
                    icon     = Icons.Default.FontDownload,
                    accent   = accent
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppFont.entries.forEach { font ->
                            FilterChip(
                                selected = settings.appFont == font,
                                onClick  = { onUpdate { copy(appFont = font) } },
                                label    = { Text(fontDisplayName(font), fontSize = 9.sp, letterSpacing = 0.5.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent,
                                    selectedLabelColor     = onAccent
                                )
                            )
                        }
                    }
                }

                SettingsDivider()

                SettingsRow(
                    label    = "Bold Font",
                    sublabel = "Heavier weight across all text",
                    icon     = Icons.Default.FormatBold,
                    accent   = accent
                ) {
                    BigSwitch(
                        checked         = settings.fontBold,
                        onCheckedChange = { onUpdate { copy(fontBold = it) } },
                        colors          = switchColors(accent)
                    )
                }

                SettingsDivider()

                Column {
                    SettingsRow(
                        label    = "Text Scale",
                        sublabel = "${"%.0f".format(settings.textScale * 100)}%",
                        icon     = Icons.Default.TextFields,
                        accent   = accent
                    ) {}
                    Slider(
                        value         = settings.textScale,
                        onValueChange = { onUpdate { copy(textScale = it) } },
                        valueRange    = 0.8f..1.4f,
                        steps         = 5,
                        colors        = sliderColors(accent),
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }

                SettingsDivider()

                Column {
                    SettingsRow(
                        label    = "UI Scale",
                        sublabel = "${"%.0f".format(settings.uiScale * 100)}%  — scales all elements",
                        icon     = Icons.Default.ZoomIn,
                        accent   = accent
                    ) {}
                    Slider(
                        value         = settings.uiScale,
                        onValueChange = { onUpdate { copy(uiScale = it) } },
                        valueRange    = 0.7f..1.5f,
                        steps         = 7,
                        colors        = sliderColors(accent),
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }

            // ── GPS & Calibration ───────────────────────────────────────────────
            SettingsSection("GPS & Calibration", accent) {
                var calibrationStatus by remember { mutableStateOf<String?>(null) }
                val coroutineScope = rememberCoroutineScope()
                var isCalibratingCompass by remember { mutableStateOf(false) }
                var compassCountdown by remember { mutableIntStateOf(0) }

                SettingsButton(
                    label    = "Reset A-GPS Assistance Data",
                    sublabel = calibrationStatus ?: "Forces cold start to download fresh satellite orbits entirely offline",
                    icon     = Icons.Default.MyLocation,
                    accent   = accent,
                    onClick  = {
                        calibrationStatus = "Clearing A-GPS cache..."
                        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                        var success = false
                        try {
                            success = lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "delete_aiding_data", android.os.Bundle())
                            lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
                            lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "force_time_injection", null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        calibrationStatus = if (success) {
                            "Cold start forced — go outdoors for a fresh satellite lock (2–3 min)"
                        } else {
                            "Not supported by this device's GPS driver — no data was cleared"
                        }
                    }
                )

                SettingsDivider()

                SettingsButton(
                    label    = "Magnetometer Sweep (Parking Lot)",
                    sublabel = if (isCalibratingCompass) {
                        "Sweep active: Drive slowly in two 360° circles... (${compassCountdown}s remaining)"
                    } else {
                        "Guided sweep — Android self-calibrates the compass while you circle"
                    },
                    icon     = Icons.Default.Navigation,
                    accent   = if (isCalibratingCompass) Color.Green else accent,
                    onClick  = {
                        if (!isCalibratingCompass) {
                            isCalibratingCompass = true
                            compassCountdown = 30
                            coroutineScope.launch {
                                while (compassCountdown > 0) {
                                    delay(1000)
                                    compassCountdown--
                                }
                                isCalibratingCompass = false
                                calibrationStatus = "Sweep complete — check the compass widget; if heading is still off, use the manual offset below"
                            }
                        }
                    }
                )

                SettingsDivider()

                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SettingsRow(
                        label    = "Compass Heading Offset",
                        sublabel = "Manual Alignment: ${if (settings.compassOffset >= 0) "+" else ""}${settings.compassOffset.toInt()}°  — aligns compass with vehicle front",
                        icon     = Icons.Default.Explore,
                        accent   = accent
                    ) {}
                    Slider(
                        value         = settings.compassOffset,
                        onValueChange = { onUpdate { copy(compassOffset = it) } },
                        valueRange    = -180f..180f,
                        steps         = 71,
                        colors        = sliderColors(accent),
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }

            // ── Updates ──────────────────────────────────────────────────────────
            SettingsSection("Updates", accent) {
                SettingsButton(
                    label    = "Check for Updates",
                    sublabel = "View releases on GitHub",
                    icon     = Icons.Default.SystemUpdate,
                    accent   = accent,
                    onClick  = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dw2lam/openlauncher/releases"))
                        context.startActivity(intent)
                    }
                )
            }

            // ── Maintenance ──────────────────────────────────────────────────────
            SettingsSection("Maintenance", accent) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { showResetDialog = true },
                    shape    = AaCardShape,
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0000)),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to Defaults", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text          = "v0.0.5  ·  Made by David Lam  ·  2026",
                color         = if (isDayMode) Color(0xFFAAAAAA) else Color(0xFF2A2A2A),
                fontSize      = 10.sp,
                letterSpacing = 1.sp,
                modifier      = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
            )
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (showResetDialog) {
        ConfirmDialog(
            title        = "Reset Settings",
            message      = "Are you sure you want to reset all settings to default? This cannot be undone.",
            confirmLabel = "Reset",
            onConfirm    = { onReset(); showResetDialog = false },
            onDismiss    = { showResetDialog = false }
        )
    }

    if (showAccentPicker) {
        ColorPickerDialog(
            title           = "Accent Color",
            initialColor    = accent,
            onColorSelected = { c ->
                val hex = String.format("#%06X", 0xFFFFFF and c.toArgb())
                onUpdate { copy(accentColorHex = hex, accentColor = c.toArgb()) }
            },
            onDismiss       = { showAccentPicker = false }
        )
    }

    if (showBgPicker) {
        ColorPickerDialog(
            title           = "Background Color",
            initialColor    = Color(settings.backgroundColor),
            onColorSelected = { c ->
                onUpdate {
                    copy(
                        backgroundColor = c.toArgb(),
                        useCustomBackgroundColor = true
                    )
                }
            },
            onDismiss       = { showBgPicker = false }
        )
    }

    if (showGradientEndPicker) {
        ColorPickerDialog(
            title           = "Gradient End Color",
            initialColor    = Color(settings.gradientEndColor),
            onColorSelected = { c ->
                onUpdate {
                    copy(
                        gradientEndColor = c.toArgb(),
                        useCustomBackgroundColor = true
                    )
                }
            },
            onDismiss       = { showGradientEndPicker = false }
        )
    }

    if (showFontColorPicker) {
        ColorPickerDialog(
            title           = "Font Color",
            initialColor    = Color(settings.fontColor),
            onColorSelected = { c -> onUpdate { copy(fontColor = c.toArgb()) } },
            onDismiss       = { showFontColorPicker = false }
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text          = title.uppercase(),
            style         = MaterialTheme.typography.labelMedium,
            color         = accent,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier      = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AaModuleShape)
                .background(AaPanel)
                .border(1.dp, AaBorder, AaModuleShape)
                .padding(horizontal = 16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    sublabel: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (sublabel.isNotEmpty())
                Text(sublabel, color = AaTextSecondary, fontSize = 13.sp)
        }
        content()
    }
}

@Composable
private fun ColumnScope.SettingsButton(
    label: String,
    sublabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (sublabel.isNotEmpty())
                Text(sublabel, color = AaTextSecondary, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = AaTextSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ColumnScope.SettingsDivider() {
    HorizontalDivider(color = AaBorder)
}

@Composable
private fun BigSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: androidx.compose.material3.SwitchColors
) {
    Box(modifier = Modifier.padding(horizontal = 6.dp)) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = colors,
            modifier = Modifier.scale(1.3f)
        )
    }
}

@Composable
private fun Stepper(valueText: String, accent: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperButton("\u2212", accent, onMinus)
        Text(
            valueText, color = AaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center
        )
        StepperButton("+", accent, onPlus)
    }
}

@Composable
private fun StepperButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(AaCardShape)
            .background(AaPanelHigh)
            .border(1.dp, AaBorder, AaCardShape)
            .clickable(onClick = onClick)
    ) {
        Text(label, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun outlinedFieldColors(accent: Color): androidx.compose.material3.TextFieldColors {
    val isDayMode = LocalDayMode.current
    val textColor = if (isDayMode) Color(0xFF111111) else Color.White
    val borderU   = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF2A2A2A)
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = accent,
        unfocusedBorderColor = borderU,
        focusedTextColor     = textColor,
        unfocusedTextColor   = textColor,
        cursorColor          = accent,
        focusedLabelColor    = accent,
        unfocusedLabelColor  = if (isDayMode) Color(0xFF888888) else Color(0xFF666666)
    )
}

@Composable
private fun switchColors(accent: Color): androidx.compose.material3.SwitchColors {
    val isDayMode = LocalDayMode.current
    val thumbChecked = if (accent.luminance() > 0.5f) Color.Black else Color.White
    return SwitchDefaults.colors(
        checkedThumbColor    = thumbChecked,
        checkedTrackColor    = accent,
        uncheckedThumbColor  = if (isDayMode) Color(0xFFBBBBBB) else Color(0xFF888888),
        uncheckedTrackColor  = AaPanelHigh,
        uncheckedBorderColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF3A3A3A)
    )
}

@Composable
private fun sliderColors(accent: Color): androidx.compose.material3.SliderColors {
    val isDayMode = LocalDayMode.current
    return SliderDefaults.colors(
        thumbColor         = accent,
        activeTrackColor   = accent,
        inactiveTrackColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF2A2A2A)
    )
}

private fun fontDisplayName(font: AppFont): String = when (font) {
    AppFont.SYSTEM          -> "System"
    AppFont.JETBRAINS_MONO  -> "JetBrains Mono"
    AppFont.SOURCE_CODE_PRO -> "Source Code Pro"
}
