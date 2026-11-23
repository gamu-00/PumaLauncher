package com.openlauncher.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Google Maps style right-hand traffic (counter-clockwise) roundabout icon.
 * Entry is fixed at 6 o'clock; [exitAngle] uses clockwise degrees from straight ahead (0° = 12 o'clock).
 */
@Composable
internal fun RoundaboutIcon(
    exitAngle: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val minDim = size.minDimension
        val cx = size.width / 2f
        val cy = size.height * 0.50f
        val ringRadius = minDim * 0.22f
        val strokeWidth = minDim * 0.11f
        val stemLength = minDim * 0.20f

        val normExit = ((exitAngle % 360f) + 360f) % 360f
        val rawCcwSweep = ((180f - normExit) % 360f + 360f) % 360f
        val ccwSweep = if (rawCcwSweep == 0f) 325f else rawCcwSweep.coerceIn(35f, 325f)

        // 1. Full semi-transparent roundabout ring
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = ringRadius,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth)
        )

        // 2. Entry stem fixed at 6 o'clock
        drawLine(
            color = Color.White,
            start = Offset(cx, cy + ringRadius + stemLength),
            end = Offset(cx, cy + ringRadius),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // 3. Traversed counter-clockwise arc from 6 o'clock (90° in Canvas coordinates)
        drawArc(
            color = Color.White,
            startAngle = 90f,
            sweepAngle = -ccwSweep,
            useCenter = false,
            topLeft = Offset(cx - ringRadius, cy - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 4. Exit stem + arrowhead pointing radially outward along exitCanvasAngleDeg
        val exitCanvasAngleDeg = 90f - ccwSweep
        val rad = Math.toRadians(exitCanvasAngleDeg.toDouble())
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()
        val px = -uy
        val py = ux

        val exitStart = Offset(cx + ringRadius * ux, cy + ringRadius * uy)
        val stemEndDist = ringRadius + stemLength * 0.65f
        val exitStemEnd = Offset(cx + stemEndDist * ux, cy + stemEndDist * uy)
        drawLine(
            color = Color.White,
            start = exitStart,
            end = exitStemEnd,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )

        val tipDist = ringRadius + stemLength
        val baseDist = ringRadius + stemLength * 0.42f
        val wingHalf = strokeWidth * 1.15f

        val arrowPath = Path().apply {
            moveTo(cx + tipDist * ux, cy + tipDist * uy)
            lineTo(cx + baseDist * ux + wingHalf * px, cy + baseDist * uy + wingHalf * py)
            lineTo(cx + baseDist * ux - wingHalf * px, cy + baseDist * uy - wingHalf * py)
            close()
        }
        drawPath(path = arrowPath, color = Color.White)
    }
}
