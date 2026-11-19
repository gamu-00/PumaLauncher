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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.BuildConfig
import com.openlauncher.app.R
import com.openlauncher.app.data.AppFont
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.GradientDirection
import com.openlauncher.app.data.THEME_COLOR_PRESETS
import com.openlauncher.app.service.MediaNotificationListenerService
import com.openlauncher.app.ui.components.ColorPickerDialog
import com.openlauncher.app.ui.components.ConfirmDialog
import com.openlauncher.app.ui.theme.*
import com.openlauncher.app.util.AppLanguageManager

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

    val colors = LocalCockpitColors.current
    val isLightTheme = colors.isLight
    val screenBg = colors.canvasBackground
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White

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
                text          = stringResource(R.string.settings_title_upper),
                style         = MaterialTheme.typography.titleLarge,
                color         = colors.textPrimary,
                letterSpacing = 3.sp,
                fontSize      = 18.sp
            )

            Spacer(Modifier.height(4.dp))

            // ── 0. Language Selector (Launcher + Media Station Stream Priority) ───
            SettingsSection(stringResource(R.string.settings_section_language), accent) {
                val currentLang = remember(context) { AppLanguageManager.getSavedLanguage(context) }
                val activity = remember(context) { AppLanguageManager.findActivity(context) }
                SettingsRow(
                    label    = stringResource(R.string.settings_language_label),
                    sublabel = stringResource(R.string.settings_language_desc),
                    icon     = Icons.Default.Language,
                    accent   = accent
                ) {}
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguageManager.SUPPORTED_LANGUAGES.chunked(3).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowOptions.forEach { option ->
                                val selected = option.code == currentLang
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(AaCardShape)
                                        .background(if (selected) accent else colors.elevatedSurface)
                                        .border(1.dp, if (selected) accent else colors.hairlineBorder, AaCardShape)
                                        .clickable {
                                            if (!selected) {
                                                AppLanguageManager.applyLanguage(activity, context, option.code)
                                            }
                                        }
                                        .padding(horizontal = 10.dp)
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = onAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = option.nativeName,
                                        color = if (selected) onAccent else colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 1. Selettore Colore Tema (in cima) ───────────────────────────────
            SettingsSection(stringResource(R.string.settings_section_theme_color), accent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    THEME_COLOR_PRESETS.forEach { preset ->
                        val isSelected = settings.accentColorHex.equals(preset.hex, ignoreCase = true)
                        val presetName = stringResource(preset.nameRes)
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
                                        color = if (isSelected) colors.textPrimary else colors.hairlineBorder,
                                        shape = CircleShape
                                    )
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = presetName,
                                        tint = if (preset.color.luminance() > 0.5f) Color.Black else Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Text(
                                text = presetName,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── App Drawer & App Nascoste ────────────────────────────────────────
            SettingsSection(stringResource(R.string.settings_section_app_drawer), accent) {
                SettingsRow(
                    label    = stringResource(R.string.settings_show_hidden_apps),
                    sublabel = if (settings.hiddenPackages.isEmpty())
                        stringResource(R.string.settings_hidden_none)
                    else
                        stringResource(R.string.settings_hidden_count, settings.hiddenPackages.size),
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
                        label    = stringResource(R.string.settings_restore_hidden, settings.hiddenPackages.size),
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
            SettingsSection(stringResource(R.string.settings_section_permissions), accent) {
                val isMediaConnected by MediaNotificationListenerService.isConnected.collectAsState()

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
                    MediaNotificationListenerService.isNotificationListenerEnabled(context)
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
                    label    = stringResource(R.string.settings_default_launcher),
                    sublabel = stringResource(
                        if (isDefaultLauncher) R.string.settings_default_launcher_active
                        else R.string.settings_default_launcher_inactive
                    ),
                    icon     = Icons.Default.Home,
                    accent   = if (isDefaultLauncher) accent else colors.dangerContent,
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
                    label    = stringResource(R.string.settings_notification_access),
                    sublabel = stringResource(
                        if (isNotificationListenerEnabled || isMediaConnected) R.string.settings_notification_granted
                        else R.string.settings_notification_required
                    ),
                    icon     = if (isNotificationListenerEnabled || isMediaConnected) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    accent   = if (isNotificationListenerEnabled || isMediaConnected) accent else colors.dangerContent,
                    onClick  = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                SettingsDivider()
                SettingsButton(
                    label    = stringResource(R.string.settings_location_access),
                    sublabel = stringResource(
                        if (hasLocation) R.string.settings_location_granted
                        else R.string.settings_location_required
                    ),
                    icon     = if (hasLocation) Icons.Default.LocationOn else Icons.Default.LocationOff,
                    accent   = if (hasLocation) accent else colors.dangerContent,
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
            SettingsSection(stringResource(R.string.settings_section_vehicle), accent) {
                var nameInput by remember(settings.vehicleName) { mutableStateOf(settings.vehicleName) }
                SettingsRow(
                    label    = stringResource(R.string.settings_vehicle_name),
                    sublabel = "",
                    icon     = Icons.Default.DirectionsCar,
                    accent   = accent
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value         = nameInput,
                            onValueChange = { nameInput = it },
                            placeholder   = {
                                Text(
                                    text = stringResource(R.string.settings_vehicle_placeholder),
                                    color = colors.textSecondary.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            },
                            singleLine    = true,
                            textStyle     = LocalTextStyle.current.copy(
                                fontSize = 14.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            colors        = outlinedFieldColors(accent),
                            modifier      = Modifier.width(180.dp)
                        )
                        if (nameInput != settings.vehicleName) {
                            IconButton(onClick = { onUpdate { copy(vehicleName = nameInput) } }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, stringResource(R.string.action_save), tint = accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ── Sidebar Shortcuts ─────────────────────────────────────────────────
            SettingsSection(stringResource(R.string.settings_section_nav_rail), accent) {
                if (settings.pinnedApps.isEmpty()) {
                    SettingsRow(
                        label    = stringResource(R.string.settings_no_pinned),
                        sublabel = stringResource(R.string.settings_no_pinned_desc),
                        icon     = Icons.Default.PushPin,
                        accent   = accent
                    ) {}
                }
                settings.pinnedApps.forEachIndexed { index, pkg ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(
                        label    = stringResource(R.string.settings_pinned_slot, index + 1),
                        sublabel = pkg,
                        icon     = Icons.Default.PushPin,
                        accent   = accent
                    ) {
                        IconButton(
                            onClick  = { onUpdate { copy(pinnedApps = pinnedApps.filter { it != pkg }) } },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, stringResource(R.string.action_remove), tint = colors.dangerContent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_section_autostart), accent) {
                SettingsRow(
                    label    = stringResource(R.string.cold_boot_auto_launch),
                    sublabel = stringResource(R.string.cold_boot_auto_launch_desc),
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
                    label    = stringResource(R.string.settings_startup_delay),
                    sublabel = stringResource(R.string.settings_startup_delay_desc),
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
            SettingsSection(stringResource(R.string.settings_section_appearance), accent) {
                SettingsRow(
                    label    = stringResource(R.string.settings_display_mode),
                    sublabel = stringResource(
                        when (settings.dayNightMode) {
                            DayNightMode.DARK   -> R.string.settings_mode_dark_desc
                            DayNightMode.LIGHT  -> R.string.settings_mode_light_desc
                            DayNightMode.AUTO   -> R.string.settings_mode_auto_desc
                            DayNightMode.SYSTEM -> R.string.settings_mode_system_desc
                        }
                    ),
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
                            val selected = settings.dayNightMode == mode
                            FilterChip(
                                selected = selected,
                                onClick  = { onUpdate { copy(dayNightMode = mode) } },
                                label    = {
                                    Text(
                                        text      = stringResource(
                                            when (mode) {
                                                DayNightMode.DARK   -> R.string.settings_mode_dark
                                                DayNightMode.LIGHT  -> R.string.settings_mode_light
                                                DayNightMode.AUTO   -> R.string.settings_mode_sunset
                                                DayNightMode.SYSTEM -> R.string.settings_mode_system
                                            }
                                        ),
                                        fontSize  = 9.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor         = colors.elevatedSurface,
                                    labelColor             = colors.textPrimary,
                                    selectedContainerColor = accent,
                                    selectedLabelColor     = onAccent
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled             = true,
                                    selected            = selected,
                                    borderColor         = colors.hairlineBorder,
                                    selectedBorderColor = accent
                                )
                            )
                        }
                    }
                }

                SettingsDivider()

                // Background color + gradient
                SettingsRow(
                    label    = stringResource(R.string.settings_background),
                    sublabel = stringResource(if (settings.useGradient) R.string.settings_bg_gradient else R.string.settings_bg_solid),
                    icon     = Icons.Default.FormatColorFill,
                    accent   = accent
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        val bgPreviewColor = if (settings.useCustomBackgroundColor) {
                            Color(settings.backgroundColor)
                        } else {
                            colors.canvasBackground
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(AaCardShape)
                                .background(bgPreviewColor)
                                .border(1.dp, colors.hairlineBorder, AaCardShape)
                                .clickable { showBgPicker = true }
                        )
                        if (settings.useGradient) {
                            Icon(
                                Icons.Default.ArrowForward, null,
                                tint = colors.textSecondary, modifier = Modifier.size(14.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(AaCardShape)
                                    .background(Color(settings.gradientEndColor))
                                    .border(1.dp, colors.hairlineBorder, AaCardShape)
                                    .clickable { showGradientEndPicker = true }
                            )
                        }
                        if (settings.useCustomBackgroundColor || settings.useGradient) {
                            TextButton(
                                onClick = {
                                    onUpdate {
                                        copy(
                                            useCustomBackgroundColor = false,
                                            useGradient = false
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(stringResource(R.string.settings_default_upper), color = accent, fontSize = 9.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }

                SettingsDivider()

                val hasCustomFontColor = (if (isLightTheme) settings.customLightFontColor else settings.customDarkFontColor) != null
                SettingsRow(
                    label    = stringResource(R.string.settings_font_color),
                    sublabel = stringResource(R.string.settings_font_color_desc),
                    icon     = Icons.Default.FormatSize,
                    accent   = accent
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.textPrimary)
                                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(8.dp))
                                .clickable { showFontColorPicker = true }
                        )
                        if (hasCustomFontColor) {
                            TextButton(
                                onClick = {
                                    onUpdate {
                                        if (isLightTheme) copy(customLightFontColor = null)
                                        else copy(customDarkFontColor = null)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(stringResource(R.string.settings_default_upper), color = accent, fontSize = 9.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }

                SettingsDivider()

                SettingsRow(
                    label    = stringResource(R.string.settings_use_gradient),
                    sublabel = stringResource(R.string.settings_use_gradient_desc),
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
                        label    = stringResource(R.string.settings_gradient_direction),
                        sublabel = stringResource(
                            when (settings.gradientDirection) {
                                GradientDirection.TOP_TO_BOTTOM -> R.string.settings_grad_top_bottom_desc
                                GradientDirection.LEFT_TO_RIGHT -> R.string.settings_grad_left_right_desc
                                GradientDirection.DIAGONAL      -> R.string.settings_grad_diagonal_desc
                                GradientDirection.RADIAL        -> R.string.settings_grad_radial_desc
                            }
                        ),
                        icon     = Icons.Default.TrendingFlat,
                        accent   = accent
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            GradientDirection.entries.forEach { dir ->
                                val selected = settings.gradientDirection == dir
                                FilterChip(
                                    selected = selected,
                                    onClick  = { onUpdate { copy(gradientDirection = dir) } },
                                    label    = {
                                        Text(
                                            text      = stringResource(
                                                when (dir) {
                                                    GradientDirection.TOP_TO_BOTTOM -> R.string.settings_grad_vertical
                                                    GradientDirection.LEFT_TO_RIGHT -> R.string.settings_grad_horizontal
                                                    GradientDirection.DIAGONAL      -> R.string.settings_grad_diagonal
                                                    GradientDirection.RADIAL        -> R.string.settings_grad_radial
                                                }
                                            ),
                                            fontSize  = 9.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor         = colors.elevatedSurface,
                                        labelColor             = colors.textPrimary,
                                        selectedContainerColor = accent,
                                        selectedLabelColor     = onAccent
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled             = true,
                                        selected            = selected,
                                        borderColor         = colors.hairlineBorder,
                                        selectedBorderColor = accent
                                    )
                                )
                            }
                        }
                    }
                }

                SettingsDivider()

                SettingsButton(
                    label    = stringResource(R.string.settings_set_wallpaper),
                    sublabel = stringResource(
                        if (settings.wallpaperUri.isNotEmpty()) R.string.settings_wallpaper_active
                        else R.string.settings_wallpaper_choose
                    ),
                    icon     = Icons.Default.Wallpaper,
                    accent   = accent,
                    onClick  = { wallpaperPicker.launch(arrayOf("image/*")) }
                )
                if (settings.wallpaperUri.isNotEmpty()) {
                    Column {
                        SettingsRow(
                            label    = stringResource(R.string.settings_wallpaper_dim),
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
                            Text(stringResource(R.string.settings_remove_wallpaper), color = colors.dangerContent, fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            // ── Typography ───────────────────────────────────────────────────────
            SettingsSection(stringResource(R.string.settings_section_typography), accent) {
                val systemFontLabel = stringResource(R.string.settings_font_system)
                SettingsRow(
                    label    = stringResource(R.string.settings_font),
                    sublabel = fontDisplayName(settings.appFont, systemFontLabel),
                    icon     = Icons.Default.FontDownload,
                    accent   = accent
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppFont.entries.forEach { font ->
                            val selected = settings.appFont == font
                            FilterChip(
                                selected = selected,
                                onClick  = { onUpdate { copy(appFont = font) } },
                                label    = { Text(fontDisplayName(font, systemFontLabel), fontSize = 9.sp, letterSpacing = 0.5.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    containerColor         = colors.elevatedSurface,
                                    labelColor             = colors.textPrimary,
                                    selectedContainerColor = accent,
                                    selectedLabelColor     = onAccent
                                ),
                                border   = FilterChipDefaults.filterChipBorder(
                                    enabled             = true,
                                    selected            = selected,
                                    borderColor         = colors.hairlineBorder,
                                    selectedBorderColor = accent
                                )
                            )
                        }
                    }
                }

                SettingsDivider()

                SettingsRow(
                    label    = stringResource(R.string.settings_bold_font),
                    sublabel = stringResource(R.string.settings_bold_font_desc),
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
                        label    = stringResource(R.string.settings_text_scale),
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
                        label    = stringResource(R.string.settings_ui_scale),
                        sublabel = stringResource(R.string.settings_ui_scale_desc, (settings.uiScale * 100).toInt()),
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
            SettingsSection(stringResource(R.string.settings_section_gps), accent) {
                var calibrationStatus by remember { mutableStateOf<String?>(null) }

                SettingsButton(
                    label    = stringResource(R.string.settings_reset_agps),
                    sublabel = calibrationStatus ?: stringResource(R.string.settings_reset_agps_desc),
                    icon     = Icons.Default.MyLocation,
                    accent   = accent,
                    onClick  = {
                        calibrationStatus = context.getString(R.string.settings_agps_clearing)
                        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                        var success = false
                        try {
                            success = lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "delete_aiding_data", android.os.Bundle())
                            lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
                            lm.sendExtraCommand(android.location.LocationManager.GPS_PROVIDER, "force_time_injection", null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        calibrationStatus = context.getString(
                            if (success) R.string.settings_agps_success
                            else R.string.settings_agps_unsupported
                        )
                    }
                )
            }

            // ── Maintenance ──────────────────────────────────────────────────────
            SettingsSection(stringResource(R.string.settings_section_maintenance), accent) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick  = { showResetDialog = true },
                    shape    = AaCardShape,
                    colors   = ButtonDefaults.buttonColors(containerColor = colors.dangerContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, colors.dangerContent.copy(alpha = 0.35f), AaCardShape)
                ) {
                    Icon(Icons.Default.RestartAlt, null, tint = colors.dangerContent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_reset_defaults), color = colors.dangerContent, fontSize = 13.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text          = "${stringResource(R.string.app_name)}  ·  v${BuildConfig.VERSION_NAME}",
                color         = colors.textSecondary.copy(alpha = 0.7f),
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
            title        = stringResource(R.string.settings_reset_dialog_title),
            message      = stringResource(R.string.settings_reset_dialog_message),
            confirmLabel = stringResource(R.string.action_reset),
            onConfirm    = { onReset(); showResetDialog = false },
            onDismiss    = { showResetDialog = false }
        )
    }

    if (showAccentPicker) {
        ColorPickerDialog(
            title           = stringResource(R.string.settings_dialog_accent_color),
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
            title           = stringResource(R.string.settings_dialog_bg_color),
            initialColor    = if (settings.useCustomBackgroundColor) Color(settings.backgroundColor) else colors.canvasBackground,
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
            title           = stringResource(R.string.settings_dialog_gradient_end),
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
            title           = stringResource(R.string.settings_dialog_font_color),
            initialColor    = colors.textPrimary,
            onColorSelected = { c ->
                onUpdate {
                    if (isLightTheme) copy(customLightFontColor = c.toArgb())
                    else copy(customDarkFontColor = c.toArgb())
                }
            },
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
    val colors = LocalCockpitColors.current
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
                .background(colors.cardSurface)
                .border(1.dp, colors.hairlineBorder, AaModuleShape)
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
    val colors = LocalCockpitColors.current
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
            Text(label, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (sublabel.isNotEmpty())
                Text(sublabel, color = colors.textSecondary, fontSize = 13.sp)
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
    val colors = LocalCockpitColors.current
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
            Text(label, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (sublabel.isNotEmpty())
                Text(sublabel, color = colors.textSecondary, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ColumnScope.SettingsDivider() {
    HorizontalDivider(color = LocalCockpitColors.current.hairlineBorder)
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
    val colors = LocalCockpitColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperButton("\u2212", accent, onMinus)
        Text(
            valueText, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center
        )
        StepperButton("+", accent, onPlus)
    }
}

@Composable
private fun StepperButton(label: String, accent: Color, onClick: () -> Unit) {
    val colors = LocalCockpitColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(AaCardShape)
            .background(colors.elevatedSurface)
            .border(1.dp, colors.hairlineBorder, AaCardShape)
            .clickable(onClick = onClick)
    ) {
        Text(label, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun outlinedFieldColors(accent: Color): androidx.compose.material3.TextFieldColors {
    val colors = LocalCockpitColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor        = accent,
        unfocusedBorderColor      = colors.hairlineBorder,
        focusedContainerColor     = colors.elevatedSurface,
        unfocusedContainerColor   = colors.elevatedSurface,
        focusedTextColor          = colors.textPrimary,
        unfocusedTextColor        = colors.textPrimary,
        cursorColor               = accent,
        focusedLabelColor         = accent,
        unfocusedLabelColor       = colors.textSecondary,
        focusedPlaceholderColor   = colors.textSecondary.copy(alpha = 0.7f),
        unfocusedPlaceholderColor = colors.textSecondary.copy(alpha = 0.7f)
    )
}

@Composable
private fun switchColors(accent: Color): androidx.compose.material3.SwitchColors {
    val colors = LocalCockpitColors.current
    val thumbChecked = if (accent.luminance() > 0.5f) Color.Black else Color.White
    return SwitchDefaults.colors(
        checkedThumbColor    = thumbChecked,
        checkedTrackColor    = accent,
        uncheckedThumbColor  = colors.textSecondary,
        uncheckedTrackColor  = colors.elevatedSurface,
        uncheckedBorderColor = colors.hairlineBorder
    )
}

@Composable
private fun sliderColors(accent: Color): androidx.compose.material3.SliderColors {
    val colors = LocalCockpitColors.current
    return SliderDefaults.colors(
        thumbColor         = accent,
        activeTrackColor   = accent,
        inactiveTrackColor = colors.hairlineBorder
    )
}

private fun fontDisplayName(font: AppFont, systemLabel: String): String = when (font) {
    AppFont.SYSTEM          -> systemLabel
    AppFont.JETBRAINS_MONO  -> "JetBrains Mono"
    AppFont.SOURCE_CODE_PRO -> "Source Code Pro"
}
