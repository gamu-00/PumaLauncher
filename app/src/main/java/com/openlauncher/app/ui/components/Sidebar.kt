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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.data.DefaultShortcutIcon
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SIDEBAR_WIDTH = 80.dp
private val SLOT_SIZE = 52.dp

const val PKG_MAPS = "com.google.android.apps.maps"
const val PKG_NETFLIX = "com.netflix.mediaclient"
const val PKG_CAR_STOCK = "com.carletter.car"
val DEFAULT_SLOT_PACKAGES = listOf(PKG_MAPS, PKG_NETFLIX, PKG_CAR_STOCK)

/** Effective package of a quick slot: the user's choice, else the preset. */
fun slotPackage(userPackage: String?, slot: Int): String =
    if (!userPackage.isNullOrEmpty()) userPackage else DEFAULT_SLOT_PACKAGES.getOrElse(slot) { "" }

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var gpsOn by remember { mutableStateOf(true) }
    var showClockSettings by remember { mutableStateOf(false) }
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
        modifier = modifier
            .widthIn(min = 68.dp, max = 104.dp)
            .fillMaxHeight()
    ) {
        val uiScale = (maxHeight.value / 480f).coerceIn(0.85f, 1.35f)
        val outerPad = (6f * uiScale).dp
        val verticalPad = (10f * uiScale).dp
        val itemGap = (8f * uiScale).dp
        val moduleShape = RoundedCornerShape((18f * uiScale).dp)
        val cardShape = RoundedCornerShape((12f * uiScale).dp)
        val slotSize = (maxWidth * 0.68f).coerceIn(44.dp, (56f * uiScale).dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPad)
                .clip(moduleShape)
                .background(AaPanel)
                .border(1.dp, AaBorder, moduleShape)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = verticalPad),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val time = timeFmt.format(now)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(cardShape)
                        .combinedClickable(onClick = {}, onLongClick = { showClockSettings = true })
                        .padding(horizontal = (4f * uiScale).dp, vertical = (2f * uiScale).dp)
                ) {
                    Text(
                        text = time.substringBefore(':'),
                        color = AaTextPrimary,
                        fontSize = (28f * uiScale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Text(
                        text = time.substringAfter(':'),
                        color = accent,
                        fontSize = (24f * uiScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(itemGap))
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
                        netIcon, "Network",
                        tint = if (isWifi || isData) accent else AaTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size((18f * uiScale).dp)
                    )
                    Icon(
                        if (gpsOn) Icons.Default.GpsFixed else Icons.Default.GpsOff, "GPS",
                        tint = if (gpsOn) accent else AaTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size((18f * uiScale).dp)
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
                RailButton(Icons.Default.Apps, "App Drawer", drawerActive, accent, slotSize, cardShape) {
                    val nextDest = if (drawerActive) NavDestination.HOME else NavDestination.APP_LIBRARY
                    onNavigate(nextDest)
                }
                Spacer(Modifier.height(itemGap))
                val settingsActive = currentDest == NavDestination.SETTINGS
                RailButton(Icons.Default.Settings, "Impostazioni", settingsActive, accent, slotSize, cardShape) {
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
    val activeContentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(slotSize)
            .heightIn(min = 44.dp)
            .clip(cardShape)
            .background(if (active) accent else AaPanelHigh)
            .border(1.dp, if (active) accent else AaBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, label, tint = if (active) activeContentColor else AaTextPrimary, modifier = Modifier.size(slotSize * 0.5f))
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
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(AaModuleShape)
                .background(AaPanelHigh)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, null, tint = accent, modifier = Modifier.size(24.dp))
                Text(
                    text = "Impostazioni Cold-Boot",
                    color = Color.White,
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
                        text = "Avvio automatico a freddo",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Lancia Google Maps nel riquadro sinistro all'avvio",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onUpdate(it, delaySeconds) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = onAccent,
                        checkedTrackColor = accent
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
                        text = "Ritardo avvio (attesa GPS e rete)",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (delaySeconds > 1) onUpdate(enabled, delaySeconds - 1) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Remove, "Meno", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${delaySeconds}s",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { if (delaySeconds < 15) onUpdate(enabled, delaySeconds + 1) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, "Piu", tint = Color.White, modifier = Modifier.size(16.dp))
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
                        activeTrackColor = accent
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
                    Icon(Icons.Default.Settings, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tutte le impostazioni", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Chiudi", color = onAccent, fontSize = 13.sp)
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
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onReplace: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(slotSize)
                .heightIn(min = 44.dp)
                .clip(cardShape)
                .background(AaPanelHigh)
                .border(1.dp, if (pinned) accent else AaBorder, cardShape)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
        ) {
            if (drawable != null) {
                val bmp = remember(pkg, drawable) { drawable.toBitmap(96, 96).asImageBitmap() }
                Image(bitmap = bmp, contentDescription = pkg, modifier = Modifier.size(slotSize * 0.65f))
            } else {
                Icon(Icons.Default.Apps, pkg, tint = AaTextPrimary, modifier = Modifier.size(slotSize * 0.54f))
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
            containerColor = AaPanelHigh
        ) {
            DropdownMenuItem(
                text = { Text(if (pinned) "Rimuovi dai fissati" else "Fissa", color = AaTextPrimary) },
                leadingIcon = { Icon(Icons.Default.PushPin, null, tint = accent) },
                onClick = { menuOpen = false; onTogglePin() }
            )
            DropdownMenuItem(
                text = { Text("Sostituisci app", color = AaTextPrimary) },
                leadingIcon = { Icon(Icons.Default.SwapHoriz, null, tint = accent) },
                onClick = { menuOpen = false; onReplace() }
            )
        }
    }
}

fun DefaultShortcutIcon.toIcon(): ImageVector = when (this) {
    // Navigation & vehicle
    DefaultShortcutIcon.RADIO       -> Icons.Default.Radio
    DefaultShortcutIcon.CAMERA      -> Icons.Default.CameraAlt
    DefaultShortcutIcon.PHONE       -> Icons.Default.Phone
    DefaultShortcutIcon.MAP         -> Icons.Default.Map
    DefaultShortcutIcon.NAVIGATION  -> Icons.Default.Navigation
    DefaultShortcutIcon.CAR         -> Icons.Default.DirectionsCar
    DefaultShortcutIcon.GAS_STATION -> Icons.Default.LocalGasStation
    DefaultShortcutIcon.DASHBOARD   -> Icons.Default.Speed
    // Audio & media
    DefaultShortcutIcon.MUSIC       -> Icons.Default.MusicNote
    DefaultShortcutIcon.SPEAKER     -> Icons.Default.Speaker
    DefaultShortcutIcon.HEADSET     -> Icons.Default.Headset
    DefaultShortcutIcon.EQUALIZER   -> Icons.Default.Equalizer
    DefaultShortcutIcon.VOLUME_UP   -> Icons.Default.VolumeUp
    // Connectivity
    DefaultShortcutIcon.BLUETOOTH   -> Icons.Default.Bluetooth
    DefaultShortcutIcon.WIFI        -> Icons.Default.Wifi
    // Lighting & climate
    DefaultShortcutIcon.LIGHTBULB   -> Icons.Default.Lightbulb
    DefaultShortcutIcon.BRIGHTNESS  -> Icons.Default.BrightnessHigh
    DefaultShortcutIcon.AC          -> Icons.Default.AcUnit
    DefaultShortcutIcon.THERMOSTAT  -> Icons.Default.Thermostat
    // General utility
    DefaultShortcutIcon.TV          -> Icons.Default.Tv
    DefaultShortcutIcon.VIDEOCAM    -> Icons.Default.Videocam
    DefaultShortcutIcon.STAR        -> Icons.Default.Star
    DefaultShortcutIcon.MESSAGE     -> Icons.Default.Message
    DefaultShortcutIcon.TIMER       -> Icons.Default.Timer
    DefaultShortcutIcon.LOCK        -> Icons.Default.Lock
    DefaultShortcutIcon.SETTINGS    -> Icons.Default.Settings
    DefaultShortcutIcon.FAVORITE    -> Icons.Default.Favorite
    // Web / location
    DefaultShortcutIcon.GLOBE       -> Icons.Default.Language
    DefaultShortcutIcon.NONE        -> Icons.Default.Apps
}
