package com.openlauncher.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.openlauncher.app.data.AppFont
import com.openlauncher.app.data.DEFAULT_UI_SCALE

val LocalDayMode = staticCompositionLocalOf { false }

@Immutable
data class PhysicalScreenMetrics(
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    val baseDensity: Density,
    val uiScale: Float
) {
    val aspectRatio: Float
        get() = if (screenHeightDp > 0.dp) screenWidthDp / screenHeightDp else 1.77f
    val isCompactHeight: Boolean
        get() = screenHeightDp < 520.dp
    val isPortrait: Boolean
        get() = aspectRatio < 1.1f
    val isLargeDisplay: Boolean
        get() = screenWidthDp >= 1100.dp && !isPortrait
}

val LocalPhysicalScreenMetrics = staticCompositionLocalOf {
    PhysicalScreenMetrics(
        screenWidthDp = 1080.dp,
        screenHeightDp = 720.dp,
        baseDensity = Density(1f, 1f),
        uiScale = DEFAULT_UI_SCALE
    )
}

/** Mini AA corner radii: 16dp main modules, 12dp cards and buttons. */
val AaModuleShape = RoundedCornerShape(16.dp)
val AaCardShape   = RoundedCornerShape(12.dp)
val AaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun OpenLauncherTheme(
    accent: Color     = AccentBlue,
    background: Color = AaBackground,
    textColor: Color  = Color.White,
    fontBold: Boolean = false,
    textScale: Float  = 1.0f,
    appFont: AppFont  = AppFont.JETBRAINS_MONO,
    isDayMode: Boolean = false,
    useCustomBg: Boolean = false,
    cockpitColors: CockpitColors = if (isDayMode) LightCockpitPalette else DarkCockpitPalette,
    content: @Composable () -> Unit
) {
    // Contrast-aware: the accent is user-chosen and can be any brightness,
    // so a fixed onPrimary (white) goes invisible on light accents
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val colorScheme = if (cockpitColors.isLight) lightColorScheme(
        primary              = accent,
        onPrimary            = onAccent,
        secondary            = accent.copy(alpha = 0.7f),
        onSecondary          = onAccent,
        tertiary             = accent.copy(alpha = 0.5f),
        background           = cockpitColors.canvasBackground,
        surface              = cockpitColors.cardSurface,
        surfaceContainer     = cockpitColors.cardSurface,
        surfaceContainerHigh = cockpitColors.elevatedSurface,
        onBackground         = cockpitColors.textPrimary,
        onSurface            = cockpitColors.textPrimary,
        surfaceVariant       = cockpitColors.elevatedSurface,
        onSurfaceVariant     = cockpitColors.textSecondary,
        outline              = cockpitColors.hairlineBorder,
        outlineVariant       = cockpitColors.hairlineBorder,
        error                = cockpitColors.dangerContent
    ) else darkColorScheme(
        primary              = accent,
        onPrimary            = onAccent,
        secondary            = accent.copy(alpha = 0.7f),
        onSecondary          = onAccent,
        tertiary             = accent.copy(alpha = 0.5f),
        background           = cockpitColors.canvasBackground,
        surface              = cockpitColors.cardSurface,
        surfaceContainer     = cockpitColors.cardSurface,
        surfaceContainerHigh = cockpitColors.elevatedSurface,
        primaryContainer     = cockpitColors.elevatedSurface,
        onPrimaryContainer   = accent,
        onBackground         = cockpitColors.textPrimary,
        onSurface            = cockpitColors.textPrimary,
        surfaceVariant       = cockpitColors.elevatedSurface,
        onSurfaceVariant     = cockpitColors.textSecondary,
        outline              = cockpitColors.hairlineBorder,
        outlineVariant       = cockpitColors.hairlineBorder,
        error                = cockpitColors.dangerContent
    )
    CompositionLocalProvider(
        LocalDayMode provides cockpitColors.isLight,
        LocalCockpitColors provides cockpitColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = launcherTypography(fontBold, textScale, appFont.toFontFamily()),
            shapes      = AaShapes,
            content     = content
        )
    }
}
