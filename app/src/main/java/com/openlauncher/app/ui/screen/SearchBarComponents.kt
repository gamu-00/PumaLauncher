package com.openlauncher.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors

private val PillSurfaceDay = Color(0xFFFFFFFF)
private val PillSurfaceNight = Color(0xFF1E1F20)
private val PillBorderDay = Color(0xFFDADCE0)
private val PillBorderNight = Color(0xFF3C4043)
private val PillPlaceholderDay = Color(0xFF5F6368)
private val PillPlaceholderNight = Color(0xFF9AA0A6)
private val PillTextDay = Color(0xFF202124)
private val PillTextNight = Color(0xFFE8EAED)
private val PillPinRed = Color(0xFFEA4335)
private val PillAccentBlueDay = Color(0xFF1A73E8)
private val PillAccentBlueNight = Color(0xFF8AB4F8)
private val PillHomeBgIdleDay = Color(0xFFF1F3F4)
private val PillHomeBgIdleNight = Color(0xFF282A2C)
private val PillHomeBgActiveDay = Color(0xFFE8F0FE)
private val PillHomeBgActiveNight = Color(0xFF1E3A5F)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GoogleSearchBarPill(
    searchQuery: String,
    @Suppress("UNUSED_PARAMETER") onSearchQueryChange: (String) -> Unit,
    searchFocused: Boolean,
    @Suppress("UNUSED_PARAMETER") onSearchFocusedChange: (Boolean) -> Unit,
    isSearching: Boolean,
    isRouting: Boolean,
    tightOverlay: Boolean,
    hasHomeAddress: Boolean,
    isSettingHome: Boolean,
    @Suppress("UNUSED_PARAMETER") focusRequester: FocusRequester,
    onOpenSearchSheet: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onImmediateSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onHomeClick: () -> Unit,
    onHomeLongClick: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) PillSurfaceDay else PillSurfaceNight
    val borderCol = if (isLight) PillBorderDay else PillBorderNight
    val textPrimary = if (isLight) PillTextDay else PillTextNight
    val textPlaceholder = if (isLight) PillPlaceholderDay else PillPlaceholderNight
    val accentBlue = if (isLight) PillAccentBlueDay else PillAccentBlueNight
    val homeBg = if (isSettingHome || hasHomeAddress) {
        if (isLight) PillHomeBgActiveDay else PillHomeBgActiveNight
    } else {
        if (isLight) PillHomeBgIdleDay else PillHomeBgIdleNight
    }
    val barHeight = if (tightOverlay) 44.dp else 48.dp

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = surfaceBg,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier.height(barHeight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    onInteraction()
                    onOpenSearchSheet()
                }
                .padding(start = 14.dp, end = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = stringResource(R.string.nav_search_content_desc),
                tint = PillPinRed,
                modifier = Modifier.size(22.dp)
            )
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f)
            ) {
                val labelText = when {
                    searchQuery.isNotEmpty() -> searchQuery
                    isSettingHome -> stringResource(R.string.nav_search_home_placeholder)
                    else -> stringResource(R.string.nav_search_here)
                }
                val labelColor = if (searchQuery.isNotEmpty()) textPrimary else textPlaceholder
                Text(
                    text = labelText,
                    color = labelColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSearching || isRouting) {
                CircularProgressIndicator(
                    color = accentBlue,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else if (searchQuery.isNotEmpty() || searchFocused) {
                IconButton(
                    onClick = { onClearSearch() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.nav_search_close),
                        tint = textPlaceholder,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val homeSize = if (tightOverlay) 34.dp else 36.dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(homeSize)
                    .clip(CircleShape)
                    .background(homeBg)
                    .combinedClickable(
                        onClick = onHomeClick,
                        onLongClick = onHomeLongClick
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = stringResource(R.string.nav_home),
                    tint = if (isSettingHome || hasHomeAddress) accentBlue else textPlaceholder,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
internal fun ValhallaMenuCircleButton(
    hasActiveOptions: Boolean,
    size: Dp = 48.dp,
    onClick: () -> Unit
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) PillSurfaceDay else PillSurfaceNight
    val borderCol = if (isLight) PillBorderDay else PillBorderNight
    val iconTint = if (isLight) PillTextDay else PillTextNight
    val accentBlue = if (isLight) PillAccentBlueDay else PillAccentBlueNight

    Box(modifier = Modifier.size(size)) {
        Surface(
            shape = CircleShape,
            color = surfaceBg,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, borderCol),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.nav_route_options),
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (hasActiveOptions) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentBlue)
                    .border(1.5.dp, surfaceBg, CircleShape)
            )
        }
    }
}
