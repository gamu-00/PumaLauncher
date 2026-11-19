package com.openlauncher.app.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_confirm),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalCockpitColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = colors.dangerContent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = colors.textSecondary)
            }
        },
        containerColor    = colors.cardSurface,
        titleContentColor = colors.textPrimary,
        textContentColor  = colors.textSecondary
    )
}
