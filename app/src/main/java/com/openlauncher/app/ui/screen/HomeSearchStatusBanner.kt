package com.openlauncher.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors

private val BannerBlueDay = Color(0xFF1A73E8)
private val BannerBlueNight = Color(0xFF8AB4F8)
private val BannerSurfaceDay = Color(0xFFFFFFFF)
private val BannerSurfaceNight = Color(0xFF1E1F20)
private val BannerTextDay = Color(0xFF202124)
private val BannerTextNight = Color(0xFFE8EAED)
private val BannerMutedDay = Color(0xFF5F6368)
private val BannerMutedNight = Color(0xFF9AA0A6)
private val BannerDangerDay = Color(0xFFD93025)
private val BannerDangerNight = Color(0xFFF28B82)

@Composable
internal fun HomeSearchStatusBanner(
    isSettingHome: Boolean,
    showClearHomeConfirm: Boolean,
    hasHomeAddress: Boolean,
    onCancelSetHome: () -> Unit,
    onDismissClearHome: () -> Unit,
    onConfirmClearHome: () -> Unit
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) BannerSurfaceDay else BannerSurfaceNight
    val textPrimary = if (isLight) BannerTextDay else BannerTextNight
    val textMuted = if (isLight) BannerMutedDay else BannerMutedNight
    val blueCol = if (isLight) BannerBlueDay else BannerBlueNight
    val dangerCol = if (isLight) BannerDangerDay else BannerDangerNight

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isSettingHome) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, blueCol.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = blueCol,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = stringResource(R.string.nav_search_select_home),
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.nav_preview_cancel),
                    color = blueCol,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onCancelSetHome()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    } else if (showClearHomeConfirm && hasHomeAddress) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, dangerCol.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = dangerCol,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = stringResource(R.string.nav_remove_home_confirm),
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.nav_preview_cancel),
                    color = textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismissClearHome)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(R.string.nav_remove),
                    color = dangerCol,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(dangerCol.copy(alpha = 0.14f))
                        .clickable(onClick = onConfirmClearHome)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
