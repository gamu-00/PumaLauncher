package com.openlauncher.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.R
import com.openlauncher.app.data.WeatherData
import com.openlauncher.app.data.WmoConditionCategory
import com.openlauncher.app.ui.theme.LocalCockpitColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class WeatherAtmosphereColors(
    val gradientStart: Color,
    val gradientEnd: Color,
    val radialGlow: Color,
    val accentTint: Color
)

private data class RainParticle(
    val xFrac: Float,
    val phaseOffset: Float,
    val speedMul: Float,
    val lengthFrac: Float,
    val alpha: Float,
    val strokeDp: Float
)

private data class SnowParticle(
    val xFrac: Float,
    val phaseOffset: Float,
    val speedMul: Float,
    val radiusDp: Float,
    val swayDp: Float,
    val alpha: Float
)

private data class CloudDriftSpec(
    val yFrac: Float,
    val widthFrac: Float,
    val heightFrac: Float,
    val phaseOffset: Float,
    val speedMul: Float,
    val alpha: Float
)

private data class StarSpec(
    val xFrac: Float,
    val yFrac: Float,
    val radiusDp: Float,
    val phaseOffset: Float
)

private val RAIN_PARTICLES = listOf(
    RainParticle(0.06f, 0.08f, 1.00f, 0.14f, 0.28f, 1.3f),
    RainParticle(0.12f, 0.55f, 1.15f, 0.12f, 0.22f, 1.1f),
    RainParticle(0.19f, 0.24f, 0.95f, 0.16f, 0.30f, 1.4f),
    RainParticle(0.25f, 0.78f, 1.10f, 0.13f, 0.24f, 1.2f),
    RainParticle(0.32f, 0.40f, 1.05f, 0.15f, 0.28f, 1.3f),
    RainParticle(0.39f, 0.92f, 0.90f, 0.12f, 0.20f, 1.1f),
    RainParticle(0.46f, 0.15f, 1.12f, 0.17f, 0.32f, 1.4f),
    RainParticle(0.53f, 0.64f, 0.98f, 0.13f, 0.25f, 1.2f),
    RainParticle(0.60f, 0.33f, 1.08f, 0.15f, 0.28f, 1.3f),
    RainParticle(0.67f, 0.84f, 0.94f, 0.12f, 0.22f, 1.1f),
    RainParticle(0.74f, 0.48f, 1.14f, 0.16f, 0.30f, 1.4f),
    RainParticle(0.80f, 0.05f, 1.02f, 0.13f, 0.24f, 1.2f),
    RainParticle(0.86f, 0.71f, 0.96f, 0.14f, 0.26f, 1.2f),
    RainParticle(0.92f, 0.29f, 1.08f, 0.12f, 0.22f, 1.1f),
    RainParticle(0.97f, 0.60f, 1.00f, 0.15f, 0.26f, 1.3f),
    RainParticle(0.50f, 0.89f, 1.18f, 0.14f, 0.24f, 1.2f)
)

private val SNOW_PARTICLES = listOf(
    SnowParticle(0.07f, 0.10f, 1.00f, 2.2f, 8f, 0.55f),
    SnowParticle(0.14f, 0.62f, 0.85f, 1.6f, 6f, 0.42f),
    SnowParticle(0.21f, 0.35f, 1.12f, 2.5f, 10f, 0.60f),
    SnowParticle(0.28f, 0.84f, 0.92f, 1.8f, 7f, 0.48f),
    SnowParticle(0.35f, 0.22f, 1.05f, 2.1f, 9f, 0.52f),
    SnowParticle(0.42f, 0.71f, 0.88f, 1.5f, 6f, 0.40f),
    SnowParticle(0.49f, 0.46f, 1.10f, 2.4f, 8f, 0.58f),
    SnowParticle(0.56f, 0.95f, 0.96f, 1.9f, 7f, 0.46f),
    SnowParticle(0.63f, 0.17f, 1.04f, 2.3f, 9f, 0.55f),
    SnowParticle(0.70f, 0.54f, 0.90f, 1.7f, 6f, 0.44f),
    SnowParticle(0.77f, 0.79f, 1.08f, 2.6f, 10f, 0.60f),
    SnowParticle(0.84f, 0.30f, 0.94f, 1.8f, 7f, 0.46f),
    SnowParticle(0.90f, 0.66f, 1.02f, 2.0f, 8f, 0.50f),
    SnowParticle(0.95f, 0.04f, 0.86f, 1.5f, 5f, 0.40f),
    SnowParticle(0.39f, 0.58f, 1.14f, 2.2f, 9f, 0.52f),
    SnowParticle(0.68f, 0.41f, 0.98f, 1.9f, 7f, 0.48f)
)

private val CLOUD_SPECS = listOf(
    CloudDriftSpec(0.20f, 0.42f, 0.24f, 0.10f, 1.00f, 0.14f),
    CloudDriftSpec(0.44f, 0.52f, 0.28f, 0.56f, 0.75f, 0.11f),
    CloudDriftSpec(0.66f, 0.36f, 0.20f, 0.33f, 1.20f, 0.09f)
)

private val NIGHT_STARS = listOf(
    StarSpec(0.16f, 0.20f, 1.4f, 0.0f),
    StarSpec(0.32f, 0.14f, 1.1f, 1.3f),
    StarSpec(0.56f, 0.24f, 1.5f, 2.7f),
    StarSpec(0.68f, 0.12f, 1.2f, 4.1f),
    StarSpec(0.77f, 0.32f, 1.3f, 5.2f)
)

/**
 * Reusable Material You + lightweight Live Canvas weather card that adapts smoothly to both
 * compact (`NowPlayingWidget`) and tall (`MediaStationCard`) dashboard panels.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CockpitWeatherCard(
    weather: WeatherData?,
    isLoading: Boolean,
    accent: Color,
    returnIcon: ImageVector,
    returnLabel: String,
    returnContentDescription: String,
    onReturnClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isCompactHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    val isLightUi = colors.isLight
    val isDay = weather?.isDay ?: isLightUi
    val category = weather?.let { WmoConditionCategory.fromWeatherCode(it.weatherCode) }
    val atmosphere = remember(category, isDay, isLightUi, accent) {
        resolveAtmosphereColors(category, isDay, isLightUi, accent)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(colors.cardSurface)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(atmosphere.gradientStart, atmosphere.gradientEnd),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(atmosphere.radialGlow, Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.14f),
                        radius = maxOf(size.width, size.height) * 0.92f
                    )
                )
            }
            .combinedClickable(
                onClick = onRefreshClick,
                onLongClick = onLongClick
            )
    ) {
        if (category != null) {
            WeatherLiveBackdropCanvas(
                category = category,
                isDay = isDay,
                isLightUi = isLightUi,
                accent = atmosphere.accentTint,
                modifier = Modifier.fillMaxSize()
            )
        }

        val isVeryTight = maxHeight < 125.dp || maxWidth < 250.dp
        val isExpanded = !isCompactHeight &&
            maxHeight >= 185.dp &&
            (maxWidth / maxHeight.coerceAtLeast(1.dp)) < 1.85f
        val showPillLabel = (isExpanded || maxWidth >= 340.dp) && !isVeryTight
        val pad = when {
            isVeryTight -> 8.dp
            isExpanded -> 16.dp
            else -> 12.dp
        }
        val returnClearance = if (showPillLabel) 92.dp else 40.dp

        if (weather != null && category != null) {
            if (isExpanded) {
                ExpandedWeatherContent(
                    weather = weather,
                    category = category,
                    isLoading = isLoading,
                    isDay = weather.isDay,
                    accent = atmosphere.accentTint,
                    pad = pad,
                    returnClearance = returnClearance,
                    maxHeight = maxHeight,
                    maxWidth = maxWidth
                )
            } else {
                CompactWeatherContent(
                    weather = weather,
                    category = category,
                    isLoading = isLoading,
                    isDay = weather.isDay,
                    accent = atmosphere.accentTint,
                    pad = pad,
                    isVeryTight = isVeryTight,
                    returnClearance = returnClearance,
                    maxHeight = maxHeight,
                    maxWidth = maxWidth
                )
            }
        } else {
            WeatherFallbackContent(
                isLoading = isLoading,
                isDay = isDay,
                accent = atmosphere.accentTint,
                pad = pad,
                isVeryTight = isVeryTight,
                isExpanded = isExpanded,
                returnClearance = returnClearance
            )
        }

        ReturnTriggerPill(
            icon = returnIcon,
            label = returnLabel,
            showLabel = showPillLabel,
            isVeryTight = isVeryTight,
            contentDescription = returnContentDescription,
            accent = atmosphere.accentTint,
            onClick = onReturnClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(if (isVeryTight) 6.dp else 10.dp)
        )
    }
}

@Composable
private fun ExpandedWeatherContent(
    weather: WeatherData,
    category: WmoConditionCategory,
    isLoading: Boolean,
    isDay: Boolean,
    accent: Color,
    pad: Dp,
    returnClearance: Dp,
    maxHeight: Dp,
    maxWidth: Dp
) {
    val colors = LocalCockpitColors.current
    val iconSize = minOf(maxHeight * 0.34f, maxWidth * 0.24f).coerceIn(64.dp, 86.dp)
    val tempSize = if (maxHeight >= 250.dp) 50.sp else 44.sp
    val condSize = if (maxHeight >= 250.dp) 18.sp else 16.sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = returnClearance),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.weather_title).uppercase(),
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isLoading) {
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MaterialWeatherIcon(
                category = category,
                isDay = isDay,
                isLightUi = colors.isLight,
                accent = accent,
                modifier = Modifier.size(iconSize)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${weather.temperatureC}°",
                    color = colors.textPrimary,
                    fontSize = tempSize,
                    lineHeight = tempSize,
                    letterSpacing = (-1.2).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = stringResource(category.labelResId),
                    color = colors.textSecondary,
                    fontSize = condSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherMetricPill(
                icon = Icons.Default.Thermostat,
                text = stringResource(R.string.weather_feels_like, weather.apparentTemperatureC),
                accent = accent
            )
            WeatherMetricPill(
                icon = Icons.Default.Air,
                text = stringResource(R.string.weather_wind_kmh, weather.windSpeedKmh),
                accent = accent
            )
        }
    }
}

@Composable
private fun CompactWeatherContent(
    weather: WeatherData,
    category: WmoConditionCategory,
    isLoading: Boolean,
    isDay: Boolean,
    accent: Color,
    pad: Dp,
    isVeryTight: Boolean,
    returnClearance: Dp,
    maxHeight: Dp,
    maxWidth: Dp
) {
    val colors = LocalCockpitColors.current
    val innerH = (maxHeight - pad * 2).coerceAtLeast(56.dp)
    val innerW = (maxWidth - pad * 2).coerceAtLeast(120.dp)
    val iconSize = minOf(innerH * 0.78f, innerW * 0.24f).coerceIn(44.dp, 74.dp)
    val gap = if (isVeryTight) 10.dp else 14.dp
    val tempSize = when {
        isVeryTight -> 28.sp
        maxHeight < 160.dp -> 32.sp
        else -> 36.sp
    }
    val condSize = when {
        isVeryTight -> 13.sp
        maxHeight < 160.dp -> 14.sp
        else -> 16.sp
    }
    val metaSize = if (isVeryTight) 11.sp else 12.sp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        MaterialWeatherIcon(
            category = category,
            isDay = isDay,
            isLightUi = colors.isLight,
            accent = accent,
            modifier = Modifier.size(iconSize)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = returnClearance),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${weather.temperatureC}°",
                    color = colors.textPrimary,
                    fontSize = tempSize,
                    lineHeight = tempSize,
                    letterSpacing = (-0.8).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = stringResource(category.labelResId),
                    color = colors.textPrimary,
                    fontSize = condSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (isVeryTight) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(if (isVeryTight) 4.dp else 7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isVeryTight) 8.dp else 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(if (isVeryTight) 13.dp else 14.dp)
                    )
                    Text(
                        text = stringResource(R.string.weather_feels_like, weather.apparentTemperatureC),
                        color = colors.textSecondary,
                        fontSize = metaSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(if (isVeryTight) 13.dp else 14.dp)
                    )
                    Text(
                        text = stringResource(R.string.weather_wind_kmh, weather.windSpeedKmh),
                        color = colors.textSecondary,
                        fontSize = metaSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 1.8.dp,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherFallbackContent(
    isLoading: Boolean,
    isDay: Boolean,
    accent: Color,
    pad: Dp,
    isVeryTight: Boolean,
    isExpanded: Boolean,
    returnClearance: Dp
) {
    val colors = LocalCockpitColors.current
    val iconSize = when {
        isVeryTight -> 42.dp
        isExpanded -> 68.dp
        else -> 54.dp
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad)
            .padding(end = returnClearance),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isVeryTight) 10.dp else 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(iconSize)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 2.6.dp,
                    modifier = Modifier.size((iconSize * 0.56f).coerceIn(22.dp, 38.dp))
                )
            } else {
                MaterialWeatherIcon(
                    category = WmoConditionCategory.PARTLY_CLOUDY,
                    isDay = isDay,
                    isLightUi = colors.isLight,
                    accent = accent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.weather_title),
                color = colors.textPrimary,
                fontSize = if (isVeryTight) 14.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (!isLoading) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = stringResource(
                        if (isLoading) R.string.weather_loading else R.string.weather_tap_refresh
                    ),
                    color = colors.textSecondary,
                    fontSize = if (isVeryTight) 11.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WeatherMetricPill(
    icon: ImageVector,
    text: String,
    accent: Color
) {
    val colors = LocalCockpitColors.current
    val pillBg = colors.elevatedSurface.copy(alpha = if (colors.isLight) 0.78f else 0.56f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(pillBg)
            .border(1.dp, colors.hairlineBorder.copy(alpha = 0.65f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReturnTriggerPill(
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    isVeryTight: Boolean,
    contentDescription: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    val pillSurface = colors.elevatedSurface.copy(alpha = if (colors.isLight) 0.88f else 0.76f)
    val pillHeight = if (isVeryTight) 26.dp else 30.dp

    Row(
        modifier = modifier
            .height(pillHeight)
            .clip(RoundedCornerShape(50))
            .background(pillSurface)
            .border(1.dp, colors.hairlineBorder.copy(alpha = 0.85f), RoundedCornerShape(50))
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(if (isVeryTight) 13.dp else 14.dp)
        )
        if (showLabel) {
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun resolveAtmosphereColors(
    category: WmoConditionCategory?,
    isDay: Boolean,
    isLightUi: Boolean,
    accent: Color
): WeatherAtmosphereColors = when (category) {
    WmoConditionCategory.CLEAR -> if (isDay) {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFFFFB300).copy(alpha = 0.14f),
                gradientEnd = Color(0xFF38BDF8).copy(alpha = 0.12f),
                radialGlow = Color(0xFFFFCA28).copy(alpha = 0.22f),
                accentTint = Color(0xFFB45309)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFFF59E0B).copy(alpha = 0.22f),
                gradientEnd = Color(0xFF0284C7).copy(alpha = 0.24f),
                radialGlow = Color(0xFFFFCA28).copy(alpha = 0.20f),
                accentTint = Color(0xFFFBBF24)
            )
        }
    } else {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF312E81).copy(alpha = 0.16f),
                gradientEnd = Color(0xFF1E3A8A).copy(alpha = 0.12f),
                radialGlow = Color(0xFF4F46E5).copy(alpha = 0.16f),
                accentTint = Color(0xFF4338CA)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF312E81).copy(alpha = 0.42f),
                gradientEnd = Color(0xFF1E3A8A).copy(alpha = 0.28f),
                radialGlow = Color(0xFFFBBF24).copy(alpha = 0.14f),
                accentTint = Color(0xFFFBBF24)
            )
        }
    }

    WmoConditionCategory.PARTLY_CLOUDY -> if (isDay) {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF38BDF8).copy(alpha = 0.14f),
                gradientEnd = Color(0xFFFBBF24).copy(alpha = 0.10f),
                radialGlow = Color(0xFFFDE047).copy(alpha = 0.16f),
                accentTint = Color(0xFF0284C7)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF0284C7).copy(alpha = 0.26f),
                gradientEnd = Color(0xFFF59E0B).copy(alpha = 0.14f),
                radialGlow = Color(0xFFFFCA28).copy(alpha = 0.16f),
                accentTint = Color(0xFF38BDF8)
            )
        }
    } else {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF334155).copy(alpha = 0.16f),
                gradientEnd = Color(0xFF312E81).copy(alpha = 0.14f),
                radialGlow = Color(0xFF4F46E5).copy(alpha = 0.14f),
                accentTint = Color(0xFF3730A3)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF1E293B).copy(alpha = 0.44f),
                gradientEnd = Color(0xFF312E81).copy(alpha = 0.30f),
                radialGlow = Color(0xFF60A5FA).copy(alpha = 0.14f),
                accentTint = Color(0xFF60A5FA)
            )
        }
    }

    WmoConditionCategory.CLOUDY -> if (isDay) {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF94A3B8).copy(alpha = 0.16f),
                gradientEnd = Color(0xFFCBD5E1).copy(alpha = 0.20f),
                radialGlow = Color(0xFF94A3B8).copy(alpha = 0.14f),
                accentTint = Color(0xFF475569)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF64748B).copy(alpha = 0.24f),
                gradientEnd = Color(0xFF334155).copy(alpha = 0.28f),
                radialGlow = Color(0xFFCBD5E1).copy(alpha = 0.12f),
                accentTint = Color(0xFFCBD5E1)
            )
        }
    } else {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF475569).copy(alpha = 0.16f),
                gradientEnd = Color(0xFF1E293B).copy(alpha = 0.14f),
                radialGlow = Color(0xFF64748B).copy(alpha = 0.12f),
                accentTint = Color(0xFF334155)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF334155).copy(alpha = 0.42f),
                gradientEnd = Color(0xFF1E293B).copy(alpha = 0.32f),
                radialGlow = Color(0xFF94A3B8).copy(alpha = 0.12f),
                accentTint = Color(0xFF94A3B8)
            )
        }
    }

    WmoConditionCategory.FOG -> if (isDay) {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF94A3B8).copy(alpha = 0.22f),
                gradientEnd = Color(0xFFCBD5E1).copy(alpha = 0.16f),
                radialGlow = Color(0xFFCBD5E1).copy(alpha = 0.24f),
                accentTint = Color(0xFF475569)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF94A3B8).copy(alpha = 0.24f),
                gradientEnd = Color(0xFF475569).copy(alpha = 0.24f),
                radialGlow = Color(0xFFE2E8F0).copy(alpha = 0.16f),
                accentTint = Color(0xFFE2E8F0)
            )
        }
    } else {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF64748B).copy(alpha = 0.18f),
                gradientEnd = Color(0xFF334155).copy(alpha = 0.14f),
                radialGlow = Color(0xFF94A3B8).copy(alpha = 0.14f),
                accentTint = Color(0xFF475569)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF475569).copy(alpha = 0.38f),
                gradientEnd = Color(0xFF334155).copy(alpha = 0.26f),
                radialGlow = Color(0xFFCBD5E1).copy(alpha = 0.14f),
                accentTint = Color(0xFFCBD5E1)
            )
        }
    }

    WmoConditionCategory.RAIN -> if (isDay) {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF0284C7).copy(alpha = 0.15f),
                gradientEnd = Color(0xFF06B6D4).copy(alpha = 0.12f),
                radialGlow = Color(0xFF38BDF8).copy(alpha = 0.18f),
                accentTint = Color(0xFF0284C7)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF0284C7).copy(alpha = 0.26f),
                gradientEnd = Color(0xFF0E7490).copy(alpha = 0.30f),
                radialGlow = Color(0xFF38BDF8).copy(alpha = 0.18f),
                accentTint = Color(0xFF38BDF8)
            )
        }
    } else {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF1E3A8A).copy(alpha = 0.16f),
                gradientEnd = Color(0xFF0E7490).copy(alpha = 0.14f),
                radialGlow = Color(0xFF0284C7).copy(alpha = 0.14f),
                accentTint = Color(0xFF0369A1)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF0E7490).copy(alpha = 0.34f),
                gradientEnd = Color(0xFF1E293B).copy(alpha = 0.44f),
                radialGlow = Color(0xFF06B6D4).copy(alpha = 0.18f),
                accentTint = Color(0xFF38BDF8)
            )
        }
    }

    WmoConditionCategory.THUNDERSTORM -> if (isDay) {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF475569).copy(alpha = 0.18f),
                gradientEnd = Color(0xFF0284C7).copy(alpha = 0.14f),
                radialGlow = accent.copy(alpha = 0.16f),
                accentTint = Color(0xFFD97706)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF475569).copy(alpha = 0.32f),
                gradientEnd = Color(0xFF0369A1).copy(alpha = 0.30f),
                radialGlow = Color(0xFFF59E0B).copy(alpha = 0.16f),
                accentTint = Color(0xFFFBBF24)
            )
        }
    } else {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF312E81).copy(alpha = 0.18f),
                gradientEnd = Color(0xFF1E293B).copy(alpha = 0.16f),
                radialGlow = Color(0xFF0284C7).copy(alpha = 0.14f),
                accentTint = Color(0xFFB45309)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF312E81).copy(alpha = 0.46f),
                gradientEnd = Color(0xFF0F172A).copy(alpha = 0.50f),
                radialGlow = Color(0xFF0891B2).copy(alpha = 0.20f),
                accentTint = Color(0xFFFBBF24)
            )
        }
    }

    WmoConditionCategory.SNOW -> if (isDay) {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF7DD3FC).copy(alpha = 0.20f),
                gradientEnd = Color(0xFFCBD5E1).copy(alpha = 0.18f),
                radialGlow = Color(0xFFBAE6FD).copy(alpha = 0.28f),
                accentTint = Color(0xFF0284C7)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF38BDF8).copy(alpha = 0.24f),
                gradientEnd = Color(0xFF64748B).copy(alpha = 0.24f),
                radialGlow = Color(0xFFBAE6FD).copy(alpha = 0.18f),
                accentTint = Color(0xFF7DD3FC)
            )
        }
    } else {
        if (isLightUi) {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF1E3A8A).copy(alpha = 0.15f),
                gradientEnd = Color(0xFF475569).copy(alpha = 0.14f),
                radialGlow = Color(0xFF38BDF8).copy(alpha = 0.14f),
                accentTint = Color(0xFF0369A1)
            )
        } else {
            WeatherAtmosphereColors(
                gradientStart = Color(0xFF0C4A6E).copy(alpha = 0.38f),
                gradientEnd = Color(0xFF334155).copy(alpha = 0.32f),
                radialGlow = Color(0xFF7DD3FC).copy(alpha = 0.16f),
                accentTint = Color(0xFF7DD3FC)
            )
        }
    }

    null -> WeatherAtmosphereColors(
        gradientStart = accent.copy(alpha = if (isLightUi) 0.08f else 0.14f),
        gradientEnd = Color.Transparent,
        radialGlow = accent.copy(alpha = if (isLightUi) 0.10f else 0.16f),
        accentTint = accent
    )
}

@Composable
private fun WeatherLiveBackdropCanvas(
    category: WmoConditionCategory,
    isDay: Boolean,
    isLightUi: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    when (category) {
        WmoConditionCategory.CLEAR -> {
            val transition = rememberInfiniteTransition(label = "liveWeatherClear")
            val pulse by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "clearPulse"
            )
            val rayAngle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 52000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "clearRayAngle"
            )
            Canvas(modifier = modifier.clipToBounds()) {
                val glowCenter = Offset(size.width * 0.88f, size.height * 0.16f)
                val baseRadius = maxOf(size.width, size.height) * (0.48f + 0.08f * pulse)
                if (isDay) {
                    val centerGlowAlpha = if (isLightUi) 0.18f + 0.06f * pulse else 0.22f + 0.06f * pulse
                    val midGlowAlpha = if (isLightUi) 0.06f else 0.09f
                    val rayAlpha = if (isLightUi) 0.08f + 0.03f * pulse else 0.10f + 0.04f * pulse
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFB300).copy(alpha = centerGlowAlpha),
                                Color(0xFFFFD54F).copy(alpha = midGlowAlpha),
                                Color.Transparent
                            ),
                            center = glowCenter,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = glowCenter
                    )
                    rotate(degrees = rayAngle, pivot = glowCenter) {
                        val rayLen = baseRadius * 0.92f
                        val rayColor = Color(0xFFFFB300).copy(alpha = rayAlpha)
                        for (i in 0 until 8) {
                            val angleRad = (i * 45.0) * (PI / 180.0)
                            val end = Offset(
                                x = glowCenter.x + (cos(angleRad) * rayLen).toFloat(),
                                y = glowCenter.y + (sin(angleRad) * rayLen).toFloat()
                            )
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(rayColor, Color.Transparent),
                                    start = glowCenter,
                                    end = end
                                ),
                                start = glowCenter,
                                end = end,
                                strokeWidth = 10.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                } else {
                    val glowColors = if (isLightUi) {
                        listOf(
                            Color(0xFF4F46E5).copy(alpha = 0.12f + 0.04f * pulse),
                            Color(0xFF6366F1).copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color(0xFFFDE68A).copy(alpha = 0.10f + 0.04f * pulse),
                            Color(0xFF4338CA).copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = glowColors,
                            center = glowCenter,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = glowCenter
                    )
                    val starColor = if (isLightUi) Color(0xFF4338CA) else Color(0xFFE2E8F0)
                    val starBaseAlpha = if (isLightUi) 0.24f else 0.28f
                    NIGHT_STARS.forEach { star ->
                        val twinkle = (0.35f + 0.65f * ((sin((pulse * PI + star.phaseOffset).toDouble()) + 1.0) * 0.5).toFloat())
                        drawCircle(
                            color = starColor.copy(alpha = starBaseAlpha * twinkle),
                            radius = star.radiusDp.dp.toPx(),
                            center = Offset(size.width * star.xFrac, size.height * star.yFrac)
                        )
                    }
                }
            }
        }

        WmoConditionCategory.PARTLY_CLOUDY -> {
            val transition = rememberInfiniteTransition(label = "liveWeatherPartly")
            val pulse by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "partlyPulse"
            )
            val drift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 26000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "partlyDrift"
            )
            Canvas(modifier = modifier.clipToBounds()) {
                val glowCenter = Offset(size.width * 0.86f, size.height * 0.18f)
                val glowR = maxOf(size.width, size.height) * (0.40f + 0.05f * pulse)
                val glowColor = when {
                    isDay -> Color(0xFFFFCA28)
                    isLightUi -> Color(0xFF4F46E5)
                    else -> Color(0xFF60A5FA)
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.14f + 0.04f * pulse), Color.Transparent),
                        center = glowCenter,
                        radius = glowR
                    ),
                    radius = glowR,
                    center = glowCenter
                )
                val cloudBaseColor = if (isLightUi) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
                for (i in 0 until 2) {
                    val spec = CLOUD_SPECS[i]
                    val cw = size.width * spec.widthFrac
                    val ch = size.height * spec.heightFrac
                    val travel = size.width + cw * 1.4f
                    val progress = (drift * spec.speedMul + spec.phaseOffset) % 1f
                    val cx = -cw * 0.7f + progress * travel
                    drawDriftingCloud(
                        center = Offset(cx, size.height * spec.yFrac),
                        width = cw,
                        height = ch,
                        color = cloudBaseColor.copy(alpha = spec.alpha * if (isLightUi) 0.85f else 0.75f)
                    )
                }
            }
        }

        WmoConditionCategory.CLOUDY,
        WmoConditionCategory.FOG -> {
            val transition = rememberInfiniteTransition(label = "liveWeatherCloudFog")
            val driftSlow by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 29000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "driftSlow"
            )
            val driftFast by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 20000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "driftFast"
            )
            val isFog = category == WmoConditionCategory.FOG
            Canvas(modifier = modifier.clipToBounds()) {
                val baseColor = if (isLightUi) Color(0xFF64748B) else Color(0xFFCBD5E1)
                CLOUD_SPECS.forEachIndexed { idx, spec ->
                    val cw = size.width * spec.widthFrac
                    val ch = size.height * spec.heightFrac
                    val travel = size.width + cw * 1.4f
                    val baseProgress = if (idx % 2 == 0) driftSlow else driftFast
                    val progress = (baseProgress * spec.speedMul + spec.phaseOffset) % 1f
                    val cx = -cw * 0.7f + progress * travel
                    drawDriftingCloud(
                        center = Offset(cx, size.height * spec.yFrac),
                        width = cw,
                        height = ch,
                        color = baseColor.copy(alpha = spec.alpha * if (isLightUi) 0.90f else 0.78f)
                    )
                }
                if (isFog) {
                    val bandColor = if (isLightUi) Color(0xFF94A3B8) else Color(0xFFE2E8F0)
                    for (band in 0 until 3) {
                        val bProg = (driftFast + band * 0.31f) % 1f
                        val bandW = size.width * (0.55f + band * 0.08f)
                        val bandH = (4.dp.toPx()).coerceAtLeast(size.height * 0.035f)
                        val bx = -bandW + bProg * (size.width + bandW * 2f)
                        val by = size.height * (0.28f + band * 0.24f)
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    bandColor.copy(alpha = if (isLightUi) 0.16f else 0.12f),
                                    Color.Transparent
                                ),
                                startX = bx,
                                endX = bx + bandW
                            ),
                            topLeft = Offset(bx, by),
                            size = Size(bandW, bandH),
                            cornerRadius = CornerRadius(bandH / 2f, bandH / 2f)
                        )
                    }
                }
            }
        }

        WmoConditionCategory.RAIN,
        WmoConditionCategory.THUNDERSTORM -> {
            val isStorm = category == WmoConditionCategory.THUNDERSTORM
            val transition = rememberInfiniteTransition(label = "liveWeatherRain")
            val rainProgress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1050, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rainProgress"
            )
            val stormCycle = if (isStorm) {
                val cycle by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 7200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "stormCycle"
                )
                cycle
            } else {
                0f
            }
            Canvas(modifier = modifier.clipToBounds()) {
                val streakColor = if (isLightUi) Color(0xFF0284C7) else Color(0xFF7DD3FC)
                val dxFactor = -0.32f // ~18 degree slant
                RAIN_PARTICLES.forEach { drop ->
                    val len = size.height * drop.lengthFrac
                    val progress = (rainProgress * drop.speedMul + drop.phaseOffset) % 1f
                    val startY = -len + progress * (size.height + len * 2f)
                    val endY = startY + len
                    val startX = size.width * drop.xFrac + (progress - 0.5f) * size.width * 0.08f
                    val endX = startX + len * dxFactor
                    drawLine(
                        color = streakColor.copy(alpha = drop.alpha * if (isLightUi) 0.85f else 0.95f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = drop.strokeDp.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                if (isStorm) {
                    val flashAlpha = when {
                        stormCycle in 0.84f..0.865f -> 0.14f
                        stormCycle in 0.885f..0.915f -> 0.16f
                        else -> 0f
                    }
                    if (flashAlpha > 0f) {
                        val flashColor = if (isLightUi) accent else Color(0xFFE0F2FE)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(flashColor.copy(alpha = flashAlpha), Color.Transparent),
                                center = Offset(size.width * 0.72f, size.height * 0.18f),
                                radius = maxOf(size.width, size.height) * 0.85f
                            )
                        )
                    }
                }
            }
        }

        WmoConditionCategory.SNOW -> {
            val transition = rememberInfiniteTransition(label = "liveWeatherSnow")
            val snowProgress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 6800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "snowProgress"
            )
            Canvas(modifier = modifier.clipToBounds()) {
                val flakeColor = if (isLightUi) Color(0xFF38BDF8) else Color(0xFFE0F2FE)
                val haloColor = if (isLightUi) Color(0xFF0284C7) else Color(0xFFBAE6FD)
                SNOW_PARTICLES.forEach { flake ->
                    val progress = (snowProgress * flake.speedMul + flake.phaseOffset) % 1f
                    val rPx = flake.radiusDp.dp.toPx()
                    val cy = -rPx * 3f + progress * (size.height + rPx * 6f)
                    val sway = (sin((progress + flake.phaseOffset) * 2.0 * PI) * flake.swayDp.dp.toPx()).toFloat()
                    val cx = size.width * flake.xFrac + sway
                    val center = Offset(cx, cy)
                    drawCircle(
                        color = haloColor.copy(alpha = flake.alpha * 0.28f),
                        radius = rPx * 2.1f,
                        center = center
                    )
                    drawCircle(
                        color = flakeColor.copy(alpha = flake.alpha),
                        radius = rPx,
                        center = center
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawDriftingCloud(
    center: Offset,
    width: Float,
    height: Float,
    color: Color
) {
    val baseH = height * 0.50f
    val baseW = width * 0.82f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - baseW / 2f, center.y),
        size = Size(baseW, baseH),
        cornerRadius = CornerRadius(baseH / 2f, baseH / 2f)
    )
    drawCircle(
        color = color,
        radius = height * 0.34f,
        center = Offset(center.x - width * 0.16f, center.y + height * 0.04f)
    )
    drawCircle(
        color = color,
        radius = height * 0.44f,
        center = Offset(center.x + width * 0.06f, center.y - height * 0.04f)
    )
}

@Composable
private fun MaterialWeatherIcon(
    category: WmoConditionCategory,
    isDay: Boolean,
    isLightUi: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val minDim = minOf(w, h)

            val frontCloudTop = if (isLightUi) Color(0xFFE2E8F0) else Color(0xFFFFFFFF)
            val frontCloudBottom = if (isLightUi) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
            val rearCloudTop = Color(0xFF94A3B8)
            val rearCloudBottom = Color(0xFF64748B)
            val stormCloudTop = if (isLightUi) Color(0xFF64748B) else Color(0xFF94A3B8)
            val stormCloudBottom = Color(0xFF475569)

            val mainCloudPath = buildSculptedCloudPath(
                left = w * 0.08f,
                top = h * 0.24f,
                width = w * 0.84f,
                height = h * 0.48f
            )
            val lowerCloudPath = buildSculptedCloudPath(
                left = w * 0.06f,
                top = h * 0.30f,
                width = w * 0.86f,
                height = h * 0.46f
            )
            val rearCloudPath = buildSculptedCloudPath(
                left = w * 0.22f,
                top = h * 0.16f,
                width = w * 0.72f,
                height = h * 0.44f
            )
            val frontStackedCloudPath = buildSculptedCloudPath(
                left = w * 0.06f,
                top = h * 0.34f,
                width = w * 0.78f,
                height = h * 0.46f
            )
            val boltPath = Path().apply {
                moveTo(w * 0.53f, h * 0.56f)
                lineTo(w * 0.41f, h * 0.76f)
                lineTo(w * 0.50f, h * 0.76f)
                lineTo(w * 0.45f, h * 0.95f)
                lineTo(w * 0.62f, h * 0.71f)
                lineTo(w * 0.52f, h * 0.71f)
                close()
            }
            val moonOuter = Path().apply {
                addOval(
                    Rect(
                        center = Offset(w * 0.48f, h * 0.50f),
                        radius = minDim * 0.32f
                    )
                )
            }
            val moonCutout = Path().apply {
                addOval(
                    Rect(
                        center = Offset(w * 0.62f, h * 0.40f),
                        radius = minDim * 0.26f
                    )
                )
            }
            val fullMoonCrescent = Path.combine(PathOperation.Difference, moonOuter, moonCutout)

            val partlyMoonOuter = Path().apply {
                addOval(
                    Rect(
                        center = Offset(w * 0.66f, h * 0.30f),
                        radius = minDim * 0.18f
                    )
                )
            }
            val partlyMoonCut = Path().apply {
                addOval(
                    Rect(
                        center = Offset(w * 0.74f, h * 0.24f),
                        radius = minDim * 0.15f
                    )
                )
            }
            val partlyMoonCrescent = Path.combine(PathOperation.Difference, partlyMoonOuter, partlyMoonCut)

            onDrawBehind {
                when (category) {
                    WmoConditionCategory.CLEAR -> {
                        if (isDay) {
                            val sunCenter = Offset(w * 0.50f, h * 0.50f)
                            val sunR = minDim * 0.24f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFD54F).copy(alpha = 0.40f), Color.Transparent),
                                    center = sunCenter,
                                    radius = minDim * 0.48f
                                ),
                                radius = minDim * 0.48f,
                                center = sunCenter
                            )
                            for (i in 0 until 8) {
                                val rad = (i * 45.0) * (PI / 180.0)
                                val r0 = sunR + minDim * 0.06f
                                val r1 = sunR + minDim * 0.15f
                                drawLine(
                                    color = Color(0xFFFFA000),
                                    start = Offset(
                                        sunCenter.x + (cos(rad) * r0).toFloat(),
                                        sunCenter.y + (sin(rad) * r0).toFloat()
                                    ),
                                    end = Offset(
                                        sunCenter.x + (cos(rad) * r1).toFloat(),
                                        sunCenter.y + (sin(rad) * r1).toFloat()
                                    ),
                                    strokeWidth = minDim * 0.055f,
                                    cap = StrokeCap.Round
                                )
                            }
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFB8C00)),
                                    start = Offset(sunCenter.x - sunR, sunCenter.y - sunR),
                                    end = Offset(sunCenter.x + sunR, sunCenter.y + sunR)
                                ),
                                radius = sunR,
                                center = sunCenter
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.35f),
                                radius = sunR * 0.45f,
                                center = Offset(sunCenter.x - sunR * 0.26f, sunCenter.y - sunR * 0.26f)
                            )
                        } else {
                            val haloColor = if (isLightUi) {
                                Color(0xFF4F46E5).copy(alpha = 0.16f)
                            } else {
                                Color(0xFFFDE68A).copy(alpha = 0.24f)
                            }
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(haloColor, Color.Transparent),
                                    center = Offset(w * 0.48f, h * 0.50f),
                                    radius = minDim * 0.48f
                                ),
                                radius = minDim * 0.48f,
                                center = Offset(w * 0.48f, h * 0.50f)
                            )
                            val moonColors = if (isLightUi) {
                                listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFD97706))
                            } else {
                                listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A), Color(0xFFCBD5E1))
                            }
                            drawPath(
                                path = fullMoonCrescent,
                                brush = Brush.linearGradient(
                                    colors = moonColors,
                                    start = Offset(w * 0.22f, h * 0.20f),
                                    end = Offset(w * 0.74f, h * 0.80f)
                                )
                            )
                            if (isLightUi) {
                                drawPath(
                                    path = fullMoonCrescent,
                                    color = Color(0xFF4338CA).copy(alpha = 0.45f),
                                    style = Stroke(width = minDim * 0.022f)
                                )
                            }
                            val starPrimary = if (isLightUi) Color(0xFF4338CA) else Color(0xFFFEF3C7)
                            val starSecondary = if (isLightUi) Color(0xFFD97706) else Color(0xFFCBD5E1)
                            drawSparkleStar(Offset(w * 0.74f, h * 0.28f), minDim * 0.075f, starPrimary)
                            drawSparkleStar(Offset(w * 0.82f, h * 0.54f), minDim * 0.052f, starSecondary)
                        }
                    }

                    WmoConditionCategory.PARTLY_CLOUDY -> {
                        if (isDay) {
                            val sunCenter = Offset(w * 0.66f, h * 0.32f)
                            val sunR = minDim * 0.17f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFD54F).copy(alpha = 0.34f), Color.Transparent),
                                    center = sunCenter,
                                    radius = minDim * 0.32f
                                ),
                                radius = minDim * 0.32f,
                                center = sunCenter
                            )
                            for (i in 0 until 7) {
                                val rad = (-135.0 + i * 38.0) * (PI / 180.0)
                                val r0 = sunR + minDim * 0.035f
                                val r1 = sunR + minDim * 0.09f
                                drawLine(
                                    color = Color(0xFFFFA000),
                                    start = Offset(
                                        sunCenter.x + (cos(rad) * r0).toFloat(),
                                        sunCenter.y + (sin(rad) * r0).toFloat()
                                    ),
                                    end = Offset(
                                        sunCenter.x + (cos(rad) * r1).toFloat(),
                                        sunCenter.y + (sin(rad) * r1).toFloat()
                                    ),
                                    strokeWidth = minDim * 0.04f,
                                    cap = StrokeCap.Round
                                )
                            }
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300)),
                                    start = Offset(sunCenter.x - sunR, sunCenter.y - sunR),
                                    end = Offset(sunCenter.x + sunR, sunCenter.y + sunR)
                                ),
                                radius = sunR,
                                center = sunCenter
                            )
                        } else {
                            val partlyMoonColors = if (isLightUi) {
                                listOf(Color(0xFFFBBF24), Color(0xFFD97706))
                            } else {
                                listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A))
                            }
                            drawPath(
                                path = partlyMoonCrescent,
                                brush = Brush.linearGradient(
                                    colors = partlyMoonColors,
                                    start = Offset(w * 0.50f, h * 0.14f),
                                    end = Offset(w * 0.80f, h * 0.46f)
                                )
                            )
                            if (isLightUi) {
                                drawPath(
                                    path = partlyMoonCrescent,
                                    color = Color(0xFF4338CA).copy(alpha = 0.45f),
                                    style = Stroke(width = minDim * 0.018f)
                                )
                            }
                            val partlyStarColor = if (isLightUi) Color(0xFF4338CA) else Color(0xFFFEF3C7)
                            drawSparkleStar(Offset(w * 0.84f, h * 0.22f), minDim * 0.05f, partlyStarColor)
                        }

                        drawPath(
                            path = lowerCloudPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(frontCloudTop, frontCloudBottom),
                                startY = h * 0.28f,
                                endY = h * 0.78f
                            )
                        )
                        if (isLightUi) {
                            drawPath(
                                path = lowerCloudPath,
                                color = Color(0xFF94A3B8).copy(alpha = 0.45f),
                                style = Stroke(width = minDim * 0.022f)
                            )
                        }
                    }

                    WmoConditionCategory.CLOUDY -> {
                        drawPath(
                            path = rearCloudPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(rearCloudTop, rearCloudBottom),
                                startY = h * 0.16f,
                                endY = h * 0.62f
                            )
                        )
                        drawPath(
                            path = frontStackedCloudPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(frontCloudTop, frontCloudBottom),
                                startY = h * 0.34f,
                                endY = h * 0.82f
                            )
                        )
                        if (isLightUi) {
                            drawPath(
                                path = frontStackedCloudPath,
                                color = Color(0xFF64748B).copy(alpha = 0.35f),
                                style = Stroke(width = minDim * 0.02f)
                            )
                        }
                    }

                    WmoConditionCategory.FOG -> {
                        drawPath(
                            path = mainCloudPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(frontCloudTop, frontCloudBottom),
                                startY = h * 0.18f,
                                endY = h * 0.64f
                            )
                        )
                        val mistColor = if (isLightUi) Color(0xFF64748B) else Color(0xFFCBD5E1)
                        val barH = minDim * 0.058f
                        val bars = listOf(
                            Triple(0.16f, 0.74f, 0.64f),
                            Triple(0.24f, 0.83f, 0.56f),
                            Triple(0.18f, 0.92f, 0.44f)
                        )
                        bars.forEachIndexed { idx, (xFrac, yFrac, wFrac) ->
                            drawRoundRect(
                                color = mistColor.copy(alpha = 0.78f - idx * 0.14f),
                                topLeft = Offset(w * xFrac, h * yFrac),
                                size = Size(w * wFrac, barH),
                                cornerRadius = CornerRadius(barH / 2f, barH / 2f)
                            )
                        }
                    }

                    WmoConditionCategory.RAIN -> {
                        drawPath(
                            path = mainCloudPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(frontCloudTop, frontCloudBottom),
                                startY = h * 0.22f,
                                endY = h * 0.72f
                            )
                        )
                        val dropColor = if (isLightUi) Color(0xFF0284C7) else Color(0xFF38BDF8)
                        val dropStroke = minDim * 0.065f
                        listOf(0.30f, 0.50f, 0.70f).forEachIndexed { idx, xFrac ->
                            val topY = h * (0.74f + (idx % 2) * 0.04f)
                            val botY = topY + h * 0.15f
                            drawLine(
                                color = dropColor,
                                start = Offset(w * xFrac, topY),
                                end = Offset(w * (xFrac - 0.06f), botY),
                                strokeWidth = dropStroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    WmoConditionCategory.THUNDERSTORM -> {
                        drawPath(
                            path = mainCloudPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(stormCloudTop, stormCloudBottom),
                                startY = h * 0.22f,
                                endY = h * 0.72f
                            )
                        )
                        val rainColor = if (isLightUi) Color(0xFF0284C7) else Color(0xFF38BDF8)
                        val rainStroke = minDim * 0.055f
                        listOf(0.24f, 0.76f).forEach { xFrac ->
                            drawLine(
                                color = rainColor,
                                start = Offset(w * xFrac, h * 0.74f),
                                end = Offset(w * (xFrac - 0.05f), h * 0.89f),
                                strokeWidth = rainStroke,
                                cap = StrokeCap.Round
                            )
                        }
                        drawPath(
                            path = boltPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B)),
                                startY = h * 0.56f,
                                endY = h * 0.95f
                            )
                        )
                    }

                    WmoConditionCategory.SNOW -> {
                        drawPath(
                            path = mainCloudPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(frontCloudTop, frontCloudBottom),
                                startY = h * 0.22f,
                                endY = h * 0.72f
                            )
                        )
                        if (isLightUi) {
                            drawPath(
                                path = mainCloudPath,
                                color = Color(0xFF94A3B8).copy(alpha = 0.45f),
                                style = Stroke(width = minDim * 0.02f)
                            )
                        }
                        val snowColor = if (isLightUi) Color(0xFF0284C7) else Color(0xFFBAE6FD)
                        val flakeR = minDim * 0.055f
                        listOf(
                            Offset(w * 0.28f, h * 0.80f),
                            Offset(w * 0.50f, h * 0.86f),
                            Offset(w * 0.72f, h * 0.80f)
                        ).forEach { pt ->
                            drawSnowCrystal(pt, flakeR, snowColor, accent)
                        }
                    }
                }
            }
        }
    )
}

private fun buildSculptedCloudPath(
    left: Float,
    top: Float,
    width: Float,
    height: Float
): Path {
    val right = left + width
    val bottom = top + height
    val baseHeight = height * 0.52f
    val baseTop = bottom - baseHeight
    val leftCircleR = height * 0.34f
    val centerCircleR = height * 0.44f
    val rightCircleR = height * 0.30f

    val basePath = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = left,
                top = baseTop,
                right = right,
                bottom = bottom,
                cornerRadius = CornerRadius(baseHeight / 2f, baseHeight / 2f)
            )
        )
    }
    val lobes = Path().apply {
        addOval(
            Rect(
                center = Offset(left + width * 0.28f, baseTop + baseHeight * 0.08f),
                radius = leftCircleR
            )
        )
        addOval(
            Rect(
                center = Offset(left + width * 0.53f, top + centerCircleR),
                radius = centerCircleR
            )
        )
        addOval(
            Rect(
                center = Offset(left + width * 0.76f, baseTop + baseHeight * 0.14f),
                radius = rightCircleR
            )
        )
    }
    return Path.combine(PathOperation.Union, basePath, lobes)
}

private fun DrawScope.drawSparkleStar(center: Offset, radius: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = radius * 0.34f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - radius),
        end = Offset(center.x, center.y + radius),
        strokeWidth = radius * 0.34f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawSnowCrystal(
    center: Offset,
    radius: Float,
    primaryColor: Color,
    accent: Color
) {
    drawCircle(
        color = accent.copy(alpha = 0.18f),
        radius = radius * 1.45f,
        center = center
    )
    val strokeW = (radius * 0.42f).coerceAtLeast(1.5f)
    for (i in 0 until 3) {
        val angle = (i * 60.0) * (PI / 180.0)
        val dx = (cos(angle) * radius).toFloat()
        val dy = (sin(angle) * radius).toFloat()
        drawLine(
            color = primaryColor,
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}
