package com.openlauncher.app.mediastation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.mediastation.ui.MoviesHubView
import com.openlauncher.app.ui.theme.AaBorder
import com.openlauncher.app.ui.theme.AaPanel
import com.openlauncher.app.ui.theme.AaPanelHigh
import com.openlauncher.app.ui.theme.AaTextPrimary

/** Top-level states of the Media Station card. */
enum class MediaStationState { IDLE, MOVIES, YOUTUBE }

private val CardShape = RoundedCornerShape(20.dp)

/**
 * Top-right "Media Station" card. IDLE shows a split Movies | YouTube chooser; each half
 * cross-fades into its sub-view within the same rounded perimeter.
 */
@Composable
fun MediaStationCard(modifier: Modifier = Modifier) {
    var state by rememberSaveable { mutableStateOf(MediaStationState.IDLE) }

    Box(
        modifier = modifier
            .clip(CardShape)
            .background(AaPanel)
            .border(1.dp, AaBorder, CardShape)
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "mediaStation",
            modifier = Modifier.fillMaxSize()
        ) { target ->
            when (target) {
                MediaStationState.IDLE -> IdleSplit(
                    onMovies = { state = MediaStationState.MOVIES },
                    onYouTube = { state = MediaStationState.YOUTUBE }
                )
                MediaStationState.MOVIES -> MoviesHubView(onClose = { state = MediaStationState.IDLE })
                MediaStationState.YOUTUBE -> YouTubeView(onClose = { state = MediaStationState.IDLE })
            }
        }
    }
}

@Composable
private fun IdleSplit(onMovies: () -> Unit, onYouTube: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            HalfButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Default.Movie,
                tint = Color(0xFFFFB300),
                label = "Film & Serie TV",
                onClick = onMovies
            )
            HalfButton(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = Icons.Default.SmartDisplay,
                tint = Color(0xFFFF0000),
                label = "YouTube",
                onClick = onYouTube
            )
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.15f))
        )
    }
}

@Composable
private fun HalfButton(
    modifier: Modifier,
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(52.dp))
            Text(label, color = AaTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Thin top bar with title and "✕" close button, shared by sub-views. */
@Composable
internal fun MediaTopBar(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(AaPanelHigh)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = AaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(width = 48.dp, height = 32.dp).clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text("\u2715", color = AaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}