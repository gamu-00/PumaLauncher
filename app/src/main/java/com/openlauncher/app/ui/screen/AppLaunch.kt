package com.openlauncher.app.ui.screen

import android.content.Context
import android.content.Intent
import android.util.Log

/** Standard full-screen launch of [packageName]. */
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
