package com.openlauncher.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors
import com.openlauncher.app.ui.theme.accentPresetLabelRes
import com.openlauncher.app.ui.theme.accentPresets

@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalCockpitColors.current
    val thumbColor = if (colors.isLight) colors.textPrimary else Color.White

    // Seed HSV state from the initial color synchronously — initializing in a
    // LaunchedEffect made the sliders flash wrong positions on the first frame
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var hue   by remember { mutableStateOf(initialHsv[0]) }
    var sat   by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }

    fun rebuildColor() {
        selectedColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    }

    fun syncFrom(color: Color) {
        selectedColor = color
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor    = colors.cardSurface,
        titleContentColor = colors.textPrimary,
        textContentColor  = colors.textSecondary,
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Preset swatches
                Text(stringResource(R.string.color_picker_presets), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accentPresets.forEachIndexed { i, color ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedColor == color) 2.5.dp else 1.dp,
                                        color = if (selectedColor == color) colors.textPrimary else colors.hairlineBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { syncFrom(color) }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(accentPresetLabelRes[i]),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.hairlineBorder)

                // Custom HSV sliders
                Text(stringResource(R.string.color_picker_custom), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)

                // Hue slider
                Text(stringResource(R.string.color_picker_hue), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Slider(
                    value = hue / 360f,
                    onValueChange = { hue = it * 360f; rebuildColor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(
                            colors = (0..6).map { i ->
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)))
                            }
                        )),
                    colors = SliderDefaults.colors(
                        thumbColor = thumbColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )

                // Saturation slider
                Text(stringResource(R.string.color_picker_saturation), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Slider(
                    value = sat,
                    onValueChange = { sat = it; rebuildColor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))
                        ))),
                    colors = SliderDefaults.colors(
                        thumbColor = thumbColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )

                // Brightness slider
                Text(stringResource(R.string.color_picker_brightness), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Slider(
                    value = value,
                    onValueChange = { value = it; rebuildColor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(
                            Color.Black,
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))
                        ))),
                    colors = SliderDefaults.colors(
                        thumbColor = thumbColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )

                // Preview swatch
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(selectedColor)
                        .border(1.dp, colors.hairlineBorder, RoundedCornerShape(8.dp))
                )
            }
        },
        confirmButton = {
            // Filled with the chosen color + auto-contrast label, so Apply stays
            // visible no matter how dark or light the selection is
            Button(
                onClick = { onColorSelected(selectedColor); onDismiss() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedColor,
                    contentColor   = if (selectedColor.luminance() > 0.5f) Color.Black else Color.White
                )
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.textSecondary) }
        }
    )
}
