package com.openlauncher.app.ui.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
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

private val SearchBlueDay = Color(0xFF1A73E8)
private val SearchBlueNight = Color(0xFF8AB4F8)
private val SearchBlueTintDay = Color(0xFFE8F0FE)
private val SearchBlueTintNight = Color(0xFF1E3A5F)
private val SearchSurfaceDay = Color(0xFFFFFFFF)
private val SearchSurfaceNight = Color(0xFF1E1F20)
private val SearchTextDay = Color(0xFF202124)
private val SearchTextNight = Color(0xFFE8EAED)
private val SearchMutedDay = Color(0xFF5F6368)
private val SearchMutedNight = Color(0xFF9AA0A6)
private val SearchBorderDay = Color(0xFFE8EAED)
private val SearchBorderNight = Color(0xFF3C4043)

@Composable
internal fun SearchResultsDropdown(
    isSearching: Boolean,
    searchResults: List<PhotonPlace>,
    searchStatusMessage: String?,
    tightOverlay: Boolean,
    isSettingHome: Boolean,
    hasActiveOrPreviewDestination: Boolean,
    onSelectPlace: (PhotonPlace) -> Unit,
    onAddWaypoint: (PhotonPlace) -> Unit
) {
    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) SearchSurfaceDay else SearchSurfaceNight
    val borderCol = if (isLight) SearchBorderDay else SearchBorderNight
    val textPrimary = if (isLight) SearchTextDay else SearchTextNight
    val textMuted = if (isLight) SearchMutedDay else SearchMutedNight
    val blueCol = if (isLight) SearchBlueDay else SearchBlueNight
    val blueTintBg = if (isLight) SearchBlueTintDay else SearchBlueTintNight

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceBg,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, borderCol),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (tightOverlay) 180.dp else 260.dp)
            .zIndex(10f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when {
                isSearching && searchResults.isEmpty() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = blueCol,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.nav_searching),
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                searchResults.isNotEmpty() -> {
                    searchResults.forEachIndexed { idx, place ->
                        if (idx > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(borderCol)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Log.d(
                                        NAV_LOG_TAG,
                                        "Destinazione selezionata per anteprima: \"${place.displayName}\" (${place.lat}, ${place.lon})"
                                    )
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onSelectPlace(place)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = blueCol,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = place.name,
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (place.subtitle.isNotBlank()) {
                                    Text(
                                        text = place.subtitle,
                                        color = textMuted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (!isSettingHome && hasActiveOrPreviewDestination) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(blueTintBg)
                                        .border(1.dp, blueCol.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            onAddWaypoint(place)
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.nav_add_stop_chip),
                                        color = blueCol,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isSettingHome) Icons.Default.Home else Icons.Default.Directions,
                                contentDescription = stringResource(R.string.nav_start_navigation),
                                tint = blueCol,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                searchStatusMessage != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = blueCol,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = searchStatusMessage,
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
