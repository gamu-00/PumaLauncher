package com.openlauncher.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.openlauncher.app.MainActivity

/** Brings the launcher activity back to the foreground. */
fun bringLauncherToFront(context: Context) {
    try {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    } catch (e: Exception) {
        Log.d("LauncherUtils", "Errore bringLauncherToFront: ${e.message}", e)
    }
}
