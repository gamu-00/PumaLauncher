package com.openlauncher.app.ui.screen

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Standard full-screen launch of [packageName] (no windowing-mode tricks). */
fun launchInFullscreen(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: run {
        Log.d("AppLaunch", "LaunchIntent non trovato per $packageName")
        return false
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return try {
        context.startActivity(intent)
        true
    } catch (e: Throwable) {
        Log.d("AppLaunch", "Errore avvio $packageName: ${e.message}", e)
        false
    }
}

/**
 * Compatibility shims for the untouched navigator panel / now-playing widget, which still
 * reference the removed Freeform video system. Both are inert; remove together with those call sites.
 */
object HomeFreeformState {
    var isNavigatorSearchFocused by mutableStateOf(false)
}

@Suppress("UNUSED_PARAMETER")
fun restoreActiveVideoWindow(context: Context) { /* no-op: Freeform video removed */ }
