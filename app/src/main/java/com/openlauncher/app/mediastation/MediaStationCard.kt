package com.openlauncher.app.mediastation

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.R
import com.openlauncher.app.mediastation.ui.MediaStationViewModel
import com.openlauncher.app.mediastation.ui.MoviesHubView
import com.openlauncher.app.ui.components.CockpitCardShape
import com.openlauncher.app.ui.theme.LocalCockpitColors

/** Top-level states of the Media Station card. */
enum class MediaStationState { IDLE, MOVIES, YOUTUBE }

private val CardShape = CockpitCardShape

/**
 * Top-right "Media Station" card. IDLE shows a split Movies | YouTube chooser; each half
 * cross-fades into its sub-view within the same rounded perimeter.
 */
@Composable
fun MediaStationCard(
    state: MediaStationState,
    onStateChange: (MediaStationState) -> Unit,
    mediaStationViewModel: MediaStationViewModel,
    isCompactHeight: Boolean = false,
    isHomeVisible: Boolean = true,
    cardShape: RoundedCornerShape = CardShape,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    Box(
        modifier = modifier
            .clip(cardShape)
            .background(colors.cardSurface)
            .border(1.dp, colors.cardBorderBrush, cardShape)
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "mediaStation",
            modifier = Modifier.fillMaxSize()
        ) { target ->
            when (target) {
                MediaStationState.IDLE -> IdleSplit(
                    isCompactHeight = isCompactHeight,
                    onMovies = { onStateChange(MediaStationState.MOVIES) },
                    onYouTube = { onStateChange(MediaStationState.YOUTUBE) }
                )
                MediaStationState.MOVIES -> MoviesHubView(
                    viewModel = mediaStationViewModel,
                    isCompactHeight = isCompactHeight,
                    isHomeVisible = isHomeVisible,
                    onClose = {
                        mediaStationViewModel.resetToCatalog()
                        onStateChange(MediaStationState.IDLE)
                    }
                )
                MediaStationState.YOUTUBE -> YouTubeView(
                    viewModel = mediaStationViewModel,
                    isCompactHeight = isCompactHeight,
                    isHomeVisible = isHomeVisible,
                    onClose = {
                        mediaStationViewModel.resetYouTubeState()
                        onStateChange(MediaStationState.IDLE)
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleSplit(
    isCompactHeight: Boolean,
    onMovies: () -> Unit,
    onYouTube: () -> Unit
) {
    val colors = LocalCockpitColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = isCompactHeight || maxHeight < 180.dp
        val iconSize = if (maxHeight < 130.dp) 44.dp else 52.dp
        val labelFontSize = if (compact) 13.sp else 15.sp
        val spacing = if (compact) 8.dp else 10.dp

        Row(Modifier.fillMaxSize()) {
            HalfButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                iconRes = R.drawable.ic_dashboard_movies,
                label = stringResource(R.string.card_movies_tv),
                iconSize = iconSize,
                labelFontSize = labelFontSize,
                spacing = spacing,
                onClick = onMovies
            )
            HalfButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                iconRes = R.drawable.ic_dashboard_youtube,
                label = stringResource(R.string.card_youtube),
                iconSize = iconSize,
                labelFontSize = labelFontSize,
                spacing = spacing,
                onClick = onYouTube
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight(0.72f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.hairlineBorder.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun HalfButton(
    modifier: Modifier,
    @DrawableRes iconRes: Int,
    label: String,
    iconSize: Dp,
    labelFontSize: TextUnit,
    spacing: Dp,
    onClick: () -> Unit
) {
    val colors = LocalCockpitColors.current
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = labelFontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Thin top bar with title and "✕" close button, shared by sub-views. */
@Composable
internal fun MediaTopBar(
    title: String,
    onClose: () -> Unit,
    isCompactHeight: Boolean = false
) {
    val colors = LocalCockpitColors.current
    val barHeight = if (isCompactHeight) 28.dp else 32.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(colors.elevatedSurface)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = barHeight)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text("\u2715", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}