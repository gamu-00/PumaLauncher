package com.openlauncher.app.ui.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.R
import com.openlauncher.app.data.DrivingAvatar
import com.openlauncher.app.ui.theme.LocalCockpitColors
import kotlin.math.sin

private val AvatarBlueDay = Color(0xFF1A73E8)
private val AvatarBlueNight = Color(0xFF8AB4F8)
private val AvatarBlueTintDay = Color(0xFFE8F0FE)
private val AvatarBlueTintNight = Color(0xFF1E3A5F)
private val AvatarIdleBgDay = Color(0xFFF8F9FA)
private val AvatarIdleBgNight = Color(0xFF282A2C)
private val AvatarBorderDay = Color(0xFFDADCE0)
private val AvatarBorderNight = Color(0xFF3C4043)
private val AvatarTextDay = Color(0xFF202124)
private val AvatarTextNight = Color(0xFFE8EAED)
private val AvatarMutedDay = Color(0xFF5F6368)
private val AvatarMutedNight = Color(0xFF9AA0A6)

@DrawableRes
internal fun DrivingAvatar.drawableResId(): Int = when (this) {
    DrivingAvatar.CLASSIC_ARROW -> R.drawable.ic_nav_arrow
    DrivingAvatar.PUMA_ST_BLUE -> R.drawable.ic_avatar_puma_st_blue
    DrivingAvatar.SPORT_RED -> R.drawable.ic_avatar_sport_red
}

@Composable
internal fun DrivingAvatar.displayName(): String = when (this) {
    DrivingAvatar.CLASSIC_ARROW -> stringResource(R.string.nav_avatar_classic_arrow)
    DrivingAvatar.PUMA_ST_BLUE -> stringResource(R.string.nav_avatar_puma_st_blue)
    DrivingAvatar.SPORT_RED -> stringResource(R.string.nav_avatar_sport_white)
}

@Composable
internal fun DrivingAvatarPickerSection(
    selectedAvatar: DrivingAvatar,
    onSelectAvatar: (DrivingAvatar) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val blueCol = if (isLight) AvatarBlueDay else AvatarBlueNight
    val blueTint = if (isLight) AvatarBlueTintDay else AvatarBlueTintNight
    val idleBg = if (isLight) AvatarIdleBgDay else AvatarIdleBgNight
    val borderCol = if (isLight) AvatarBorderDay else AvatarBorderNight
    val textPrimary = if (isLight) AvatarTextDay else AvatarTextNight
    val textMuted = if (isLight) AvatarMutedDay else AvatarMutedNight

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 8.dp else 10.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_driving_avatar),
            color = textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DrivingAvatar.entries.forEach { avatar ->
                val isSelected = avatar == selectedAvatar
                val cardShape = RoundedCornerShape(14.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(cardShape)
                        .background(if (isSelected) blueTint else idleBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) blueCol else borderCol,
                            shape = cardShape
                        )
                        .clickable { onSelectAvatar(avatar) }
                        .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 8.dp else 10.dp)
                ) {
                    val previewPath = remember { Path() }
                    val previewTiltFactor = remember { sin(Math.toRadians(45.0)).toFloat() }
                    val previewHeading = if (avatar == DrivingAvatar.CLASSIC_ARROW) 0f else -24f
                    Canvas(modifier = Modifier.size(if (compact) 46.dp else 56.dp)) {
                        val unit = size.minDimension / 68f
                        drawVehicleAvatar25D(
                            avatar = avatar,
                            centerX = size.width * 0.5f,
                            centerY = size.height * 0.56f,
                            unit = unit,
                            densityPx = density,
                            relativeHeading = previewHeading,
                            tiltFactor = previewTiltFactor,
                            path = previewPath
                        )
                    }
                    Text(
                        text = avatar.displayName(),
                        color = if (isSelected) blueCol else textPrimary,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
