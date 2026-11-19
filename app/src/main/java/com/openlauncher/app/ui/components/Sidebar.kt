package com.openlauncher.app.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.location.LocationManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.R
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SIDEBAR_WIDTH = 80.dp
private val SLOT_SIZE = 52.dp

/**
 * Mini AA Nav Rail (80dp): digital clock (long-press = cold-boot settings), Wi-Fi/GPS
 * indicators, up to 4 dynamic slots (pinned apps first, then recents), App Drawer and
 * Settings buttons. Long-press on a slot: pin/unpin or replace app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(
    currentDest: NavDestination,
    railPackages: List<String>,
    pinnedPackages: Set<String>,
    isWifi: Boolean,
    isData: Boolean,
    installedIconFor: (String) -> Drawable?,
    onNavigate: (NavDestination) -> Unit,
    onRailClick: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onReplace: (Int) -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
    coldBootAutoLaunch: Boolean = false,
    coldBootDelaySeconds: Int = 4,
    onUpdateColdBoot: (Boolean, Int) -> Unit = { _, _ -> },
    isCompactHeight: Boolean = false,
    homePressTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var gpsOn by remember { mutableStateOf(true) }
    var showClockSettings by remember { mutableStateOf(false) }

    LaunchedEffect(homePressTrigger) {
        if (homePressTrigger > 0) {
            showClockSettings = false
        }
    }
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            now = Date()
            if (tick % 5 == 0) {
                gpsOn = runCatching {
                    (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                        .isProviderEnabled(LocationManager.GPS_PROVIDER)
                }.getOrDefault(false)
            }
            tick++
            delay(1000)
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    BoxWithConstraints(
        modifier = modifier.fillMaxHeight()
    ) {
        val compact = isCompactHeight || maxHeight < 520.dp
        val heightScale = (maxHeight.value / 520f).coerceIn(0.65f, 1.25f)
        val outerPad = if (compact) (4f * heightScale).dp.coerceAtLeast(3.dp) else (6f * heightScale).dp
        val verticalPad = if (compact) (5f * heightScale).dp.coerceAtLeast(4.dp) else (10f * heightScale).dp
        val itemGap = if (compact) (4f * heightScale).dp.coerceAtLeast(3.dp) else (8f * heightScale).dp
        val moduleShape = CockpitCardShape
        val cardShape = RoundedCornerShape((10f * heightScale).dp.coerceIn(8.dp, 14.dp))

        val hourSp = if (compact) (20f * heightScale).coerceIn(15f, 22f).sp else (26f * heightScale).coerceIn(22f, 30f).sp
        val minSp = if (compact) (17f * heightScale).coerceIn(13f, 19f).sp else (22f * heightScale).coerceIn(18f, 26f).sp
        val statusIconSize = if (compact) (14f * heightScale).dp.coerceIn(12.dp, 16.dp) else (18f * heightScale).dp.coerceIn(16.dp, 22.dp)

        val innerHeight = (maxHeight - outerPad * 2 - verticalPad * 2).coerceAtLeast(180.dp)
        val headerReserve = if (compact) 64.dp else 96.dp
        val totalSlots = (railPackages.size + 2).coerceAtLeast(2)
        val slotBudget = ((innerHeight - headerReserve - itemGap * (totalSlots + 1)) / totalSlots)
        val maxWidthSlot = (maxWidth - outerPad * 2 - 10.dp).coerceAtLeast(30.dp)
        val slotSize = minOf(slotBudget, maxWidthSlot).coerceIn(
            if (compact) 30.dp else 44.dp,
            if (compact) 46.dp else 56.dp
        )

        val colors = LocalCockpitColors.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPad)
                .clip(moduleShape)
                .background(colors.dockSurface)
                .border(1.dp, colors.cardBorderBrush, moduleShape)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = verticalPad),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val time = timeFmt.format(now)
                val tabularStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp),
                    modifier = Modifier
                        .clip(cardShape)
                        .combinedClickable(onClick = {}, onLongClick = { showClockSettings = true })
                        .padding(horizontal = (6f * heightScale).dp, vertical = (4f * heightScale).dp)
                ) {
                    Text(
                        text = time.substringBefore(':'),
                        style = tabularStyle,
                        color = colors.textPrimary,
                        fontSize = hourSp,
                        lineHeight = hourSp * 1.0f,
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Text(
                        text = time.substringAfter(':'),
                        style = tabularStyle,
                        color = accent,
                        fontSize = minSp,
                        lineHeight = minSp * 1.0f,
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(itemGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val netIcon = when {
                        isWifi -> Icons.Default.Wifi
                        isData -> Icons.Default.SignalCellularAlt
                        else   -> Icons.Default.WifiOff
                    }
                    Icon(
                        netIcon, stringResource(R.string.sidebar_network),
                        tint = if (isWifi || isData) accent else colors.textSecondary.copy(alpha = 0.55f),
                        modifier = Modifier.size(statusIconSize)
                    )
                    Icon(
                        if (gpsOn) Icons.Default.GpsFixed else Icons.Default.GpsOff, stringResource(R.string.sidebar_gps),
                        tint = if (gpsOn) accent else colors.textSecondary.copy(alpha = 0.55f),
                        modifier = Modifier.size(statusIconSize)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Dynamic slots: pinned first, then recents
                railPackages.forEachIndexed { index, pkg ->
                    RailSlot(
                        pkg = pkg,
                        drawable = installedIconFor(pkg),
                        pinned = pkg in pinnedPackages,
                        accent = accent,
                        slotSize = slotSize,
                        cardShape = cardShape,
                        homePressTrigger = homePressTrigger,
                        onClick = { onRailClick(pkg) },
                        onTogglePin = {
                            onTogglePin(pkg)
                        },
                        onReplace = { onReplace(index) }
                    )
                    Spacer(Modifier.height(itemGap))
                }

                Spacer(Modifier.weight(1f))

                val drawerActive = currentDest == NavDestination.APP_LIBRARY
                RailButton(Icons.Default.Apps, stringResource(R.string.sidebar_app_drawer), drawerActive, accent, slotSize, cardShape) {
                    val nextDest = if (drawerActive) NavDestination.HOME else NavDestination.APP_LIBRARY
                    onNavigate(nextDest)
                }
                Spacer(Modifier.height(itemGap))
                val settingsActive = currentDest == NavDestination.SETTINGS
                RailButton(Icons.Default.Settings, stringResource(R.string.nav_settings), settingsActive, accent, slotSize, cardShape) {
                    val nextDest = if (settingsActive) NavDestination.HOME else NavDestination.SETTINGS
                    onNavigate(nextDest)
                }
            }
        }
    }

    if (showClockSettings) {
        ColdBootSettingsDialog(
            enabled = coldBootAutoLaunch,
            delaySeconds = coldBootDelaySeconds,
            accent = accent,
            onUpdate = onUpdateColdBoot,
            onOpenFullSettings = {
                showClockSettings = false
                onNavigate(NavDestination.SETTINGS)
            },
            onDismiss = {
                showClockSettings = false
            }
        )
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    slotSize: androidx.compose.ui.unit.Dp = SLOT_SIZE,
    cardShape: RoundedCornerShape = AaCardShape,
    onClick: () -> Unit
) {
    val colors = LocalCockpitColors.current
    val activeContentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(slotSize)
            .clip(cardShape)
            .background(if (active) accent else colors.elevatedSurface)
            .then(
                when {
                    active -> Modifier.border(1.dp, accent, cardShape)
                    colors.isLight -> Modifier.border(1.dp, colors.hairlineBorder, cardShape)
                    else -> Modifier.border(1.dp, colors.cardBorderBrush, cardShape)
                }
            )
            .clickable(onClick = onClick)
    ) {
        Icon(icon, label, tint = if (active) activeContentColor else colors.textPrimary, modifier = Modifier.size(slotSize * 0.5f))
    }
}

@Composable
private fun ColdBootSettingsDialog(
    enabled: Boolean,
    delaySeconds: Int,
    accent: Color,
    onUpdate: (Boolean, Int) -> Unit,
    onOpenFullSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalCockpitColors.current
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(AaModuleShape)
                .background(colors.cardSurface)
                .border(1.dp, colors.hairlineBorder, AaModuleShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, null, tint = accent, modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.cold_boot_dialog_title),
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.cold_boot_auto_launch),
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.cold_boot_auto_launch_desc),
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onUpdate(it, delaySeconds) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = onAccent,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.elevatedSurface,
                        uncheckedBorderColor = colors.hairlineBorder
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.cold_boot_delay_dialog),
                        color = colors.textPrimary,
                        fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (delaySeconds > 1) onUpdate(enabled, delaySeconds - 1) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Remove, stringResource(R.string.sidebar_decrease), tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${delaySeconds}s",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { if (delaySeconds < 15) onUpdate(enabled, delaySeconds + 1) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, stringResource(R.string.sidebar_increase), tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Slider(
                    value = delaySeconds.toFloat(),
                    onValueChange = { onUpdate(enabled, it.toInt().coerceIn(1, 15)) },
                    valueRange = 1f..15f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = colors.hairlineBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenFullSettings) {
                    Icon(Icons.Default.Settings, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cold_boot_all_settings), color = colors.textSecondary, fontSize = 13.sp)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(stringResource(R.string.action_close), color = onAccent, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailSlot(
    pkg: String,
    drawable: Drawable?,
    pinned: Boolean,
    accent: Color,
    slotSize: androidx.compose.ui.unit.Dp = SLOT_SIZE,
    cardShape: RoundedCornerShape = AaCardShape,
    homePressTrigger: Int = 0,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onReplace: () -> Unit
) {
    val colors = LocalCockpitColors.current
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(homePressTrigger) {
        if (homePressTrigger > 0) {
            menuOpen = false
        }
    }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(slotSize)
                .clip(cardShape)
                .background(colors.elevatedSurface)
                .then(
                    when {
                        pinned -> Modifier.border(1.dp, accent, cardShape)
                        colors.isLight -> Modifier.border(1.dp, colors.hairlineBorder, cardShape)
                        else -> Modifier.border(1.dp, colors.cardBorderBrush, cardShape)
                    }
                )
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
        ) {
            if (drawable != null) {
                val bmp = remember(pkg, drawable) { drawable.toBitmap(96, 96).asImageBitmap() }
                Image(bitmap = bmp, contentDescription = pkg, modifier = Modifier.size(slotSize * 0.65f))
            } else {
                Icon(Icons.Default.Apps, pkg, tint = colors.textPrimary, modifier = Modifier.size(slotSize * 0.54f))
            }
            if (pinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .width(18.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = colors.cardSurface
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(if (pinned) R.string.sidebar_unpin else R.string.sidebar_pin), color = colors.textPrimary) },
                leadingIcon = { Icon(Icons.Default.PushPin, null, tint = accent) },
                onClick = { menuOpen = false; onTogglePin() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sidebar_replace_app), color = colors.textPrimary) },
                leadingIcon = { Icon(Icons.Default.SwapHoriz, null, tint = accent) },
                onClick = { menuOpen = false; onReplace() }
            )
        }
    }
}
