package com.openlauncher.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import com.openlauncher.app.MainActivity
import com.openlauncher.app.data.SettingsRepository
import com.openlauncher.app.util.bringLauncherToFront
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object ColdBootManager {
    private val coldBootChecked = AtomicBoolean(false)

    /**
     * Returns true only once per process cold-boot session.
     */
    fun markColdBootChecked(): Boolean = coldBootChecked.compareAndSet(false, true)

    @Volatile
    var navigatorBounds: Rect? = null

    /** Brings the single-activity native navigator launcher to the front on boot. */
    fun launchNavigator(context: Context) {
        bringLauncherToFront(context)
    }
}

class ColdBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            try {
                val settings = SettingsRepository(appContext).settingsFlow.first()
                if (settings.coldBootAutoLaunch && ColdBootManager.markColdBootChecked()) {
                    runCatching {
                        appContext.startActivity(
                            Intent(appContext, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
