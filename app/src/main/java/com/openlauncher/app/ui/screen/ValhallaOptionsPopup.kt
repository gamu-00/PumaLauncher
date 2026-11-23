package com.openlauncher.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.RemoveRoad
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors

private val PopupBlueDay = Color(0xFF1A73E8)
private val PopupBlueNight = Color(0xFF8AB4F8)
private val PopupSurfaceDay = Color(0xFFFFFFFF)
private val PopupSurfaceNight = Color(0xFF1E1F20)
private val PopupTextDay = Color(0xFF202124)
private val PopupTextNight = Color(0xFFE8EAED)
private val PopupMutedDay = Color(0xFF5F6368)
private val PopupMutedNight = Color(0xFF9AA0A6)
private val PopupBorderDay = Color(0xFFE8EAED)
private val PopupBorderNight = Color(0xFF3C4043)

@Composable
internal fun ValhallaOptionsPopupCard(
    avoidTolls: Boolean,
    avoidHighways: Boolean,
    preferScenicRoute: Boolean,
    avoidFerries: Boolean,
    onToggleAvoidTolls: () -> Unit,
    onToggleAvoidHighways: () -> Unit,
    onTogglePreferScenic: () -> Unit,
    onToggleAvoidFerries: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) PopupSurfaceDay else PopupSurfaceNight
    val borderCol = if (isLight) PopupBorderDay else PopupBorderNight
    val textMuted = if (isLight) PopupMutedDay else PopupMutedNight

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surfaceBg,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier.widthIn(min = 260.dp, max = 330.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_route_options),
                color = textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            ValhallaOptionSwitchRow(
                icon = Icons.Default.MoneyOff,
                label = stringResource(R.string.nav_avoid_tolls),
                checked = avoidTolls,
                onClick = onToggleAvoidTolls
            )
            HorizontalDivider(color = borderCol, thickness = 0.5.dp)
            ValhallaOptionSwitchRow(
                icon = Icons.Default.RemoveRoad,
                label = stringResource(R.string.nav_avoid_highways),
                checked = avoidHighways,
                onClick = onToggleAvoidHighways
            )
            HorizontalDivider(color = borderCol, thickness = 0.5.dp)
            ValhallaOptionSwitchRow(
                icon = Icons.Default.Terrain,
                label = stringResource(R.string.nav_scenic_route),
                checked = preferScenicRoute,
                onClick = onTogglePreferScenic
            )
            HorizontalDivider(color = borderCol, thickness = 0.5.dp)
            ValhallaOptionSwitchRow(
                icon = Icons.Default.DirectionsBoat,
                label = stringResource(R.string.nav_avoid_ferries),
                checked = avoidFerries,
                onClick = onToggleAvoidFerries
            )
        }
    }
}

@Composable
private fun ValhallaOptionSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    val isLight = LocalCockpitColors.current.isLight
    val blueCol = if (isLight) PopupBlueDay else PopupBlueNight
    val textPrimary = if (isLight) PopupTextDay else PopupTextNight
    val textMuted = if (isLight) PopupMutedDay else PopupMutedNight
    val uncheckedTrack = if (isLight) Color(0xFFF1F3F4) else Color(0xFF282A2C)
    val uncheckedBorder = if (isLight) Color(0xFFBDC1C6) else Color(0xFF5F6368)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) blueCol else textMuted,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PopupBlueDay,
                uncheckedThumbColor = textMuted,
                uncheckedTrackColor = uncheckedTrack,
                uncheckedBorderColor = uncheckedBorder
            )
        )
    }
}
