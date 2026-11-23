package com.openlauncher.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.R
import com.openlauncher.app.data.DrivingAvatar
import com.openlauncher.app.ui.theme.LocalCockpitColors

private val SheetDividerDay = Color(0xFFE8EAED)
private val SheetDividerNight = Color(0xFF3C4043)
private val SheetTextDay = Color(0xFF202124)
private val SheetTextNight = Color(0xFFE8EAED)
private val SheetMutedDay = Color(0xFF5F6368)
private val SheetMutedNight = Color(0xFF9AA0A6)
private val SheetBlueDay = Color(0xFF1A73E8)
private val SheetBlueNight = Color(0xFF8AB4F8)
private val SheetBlueTintDay = Color(0xFFE8F0FE)
private val SheetBlueTintNight = Color(0xFF1E3A5F)
private val SheetIdleCircleDay = Color(0xFFF1F3F4)
private val SheetIdleCircleNight = Color(0xFF282A2C)

@Composable
internal fun TripSheetExpandedOptions(
    nav3dViewEnabled: Boolean,
    drivingAvatar: DrivingAvatar,
    compact: Boolean,
    onAddWaypointClick: () -> Unit,
    onToggleNav3dView: (Boolean) -> Unit,
    onSelectDrivingAvatar: (DrivingAvatar) -> Unit
) {
    val isLight = LocalCockpitColors.current.isLight
    val dividerCol = if (isLight) SheetDividerDay else SheetDividerNight
    val blueCol = if (isLight) SheetBlueDay else SheetBlueNight
    val blueTint = if (isLight) SheetBlueTintDay else SheetBlueTintNight
    val idleCircle = if (isLight) SheetIdleCircleDay else SheetIdleCircleNight
    val textPrimary = if (isLight) SheetTextDay else SheetTextNight
    val textMuted = if (isLight) SheetMutedDay else SheetMutedNight
    val uncheckedBorder = if (isLight) Color(0xFFBDC1C6) else Color(0xFF5F6368)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (compact) 250.dp else 330.dp)
            .verticalScroll(rememberScrollState())
    ) {
        HorizontalDivider(color = dividerCol, thickness = 1.dp)

        TripSheetActionRow(
            icon = Icons.Default.AddLocationAlt,
            iconTint = blueCol,
            iconBg = blueTint,
            title = stringResource(R.string.nav_add_stop),
            subtitle = stringResource(R.string.nav_add_stop_subtitle),
            textPrimary = textPrimary,
            textMuted = textMuted,
            onClick = onAddWaypointClick,
            trailing = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        )

        HorizontalDivider(color = dividerCol, thickness = 0.75.dp)

        TripSheetActionRow(
            icon = Icons.Default.ViewInAr,
            iconTint = if (nav3dViewEnabled) blueCol else textMuted,
            iconBg = if (nav3dViewEnabled) blueTint else idleCircle,
            title = stringResource(R.string.nav_3d_view),
            subtitle = stringResource(R.string.nav_3d_view_subtitle),
            textPrimary = textPrimary,
            textMuted = textMuted,
            onClick = { onToggleNav3dView(!nav3dViewEnabled) },
            trailing = {
                Switch(
                    checked = nav3dViewEnabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SheetBlueDay,
                        uncheckedThumbColor = textMuted,
                        uncheckedTrackColor = idleCircle,
                        uncheckedBorderColor = uncheckedBorder
                    )
                )
            }
        )

        HorizontalDivider(color = dividerCol, thickness = 0.75.dp)

        DrivingAvatarPickerSection(
            selectedAvatar = drivingAvatar,
            onSelectAvatar = onSelectDrivingAvatar,
            compact = compact
        )
    }
}

@Composable
private fun TripSheetActionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textMuted: Color,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}
