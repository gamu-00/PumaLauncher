package com.openlauncher.app.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.openlauncher.app.R
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.util.AppLanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal NotificationListenerService that mirrors active media sessions via
 * [MediaSessionManager] for any music or audio app without vendor-specific bias.
 */
class MediaNotificationListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackedControllers = mutableMapOf<MediaSession.Token, MediaController>()
    private var sessionListenerRegistered = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshActiveSessions()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshActiveSessions()
        override fun onSessionDestroyed() = refreshActiveSessions()
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { _ ->
        refreshActiveSessions()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isConnected.value = true
        registerSessionsListener()
        refreshActiveSessions()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        teardown()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshActiveSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActiveSessions()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        unregisterSessionsListener()
        clearTrackedControllers()
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
        _nowPlaying.value = null
    }

    private fun registerSessionsListener() {
        if (sessionListenerRegistered) return
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        try {
            msm.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                ComponentName(this, MediaNotificationListenerService::class.java),
                mainHandler
            )
            sessionListenerRegistered = true
        } catch (_: SecurityException) {
        }
    }

    private fun unregisterSessionsListener() {
        if (!sessionListenerRegistered) return
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        sessionListenerRegistered = false
    }

    private fun clearTrackedControllers() {
        trackedControllers.values.forEach { ctrl ->
            runCatching { ctrl.unregisterCallback(controllerCallback) }
        }
        trackedControllers.clear()
    }

    private fun refreshActiveSessions() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshActiveSessions() }
            return
        }
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val component = ComponentName(this, MediaNotificationListenerService::class.java)
        val sessions = try {
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            emptyList()
        }

        val eligibleSessions = sessions.filter { it.packageName != packageName }
        if (eligibleSessions.isEmpty()) {
            clearTrackedControllers()
            _nowPlaying.value = null
            return
        }

        val currentTokens = eligibleSessions.map { it.sessionToken }.toSet()
        val removedTokens = trackedControllers.keys.filter { it !in currentTokens }
        removedTokens.forEach { token ->
            trackedControllers.remove(token)?.let { ctrl ->
                runCatching { ctrl.unregisterCallback(controllerCallback) }
            }
        }
        eligibleSessions.forEach { ctrl ->
            if (ctrl.sessionToken !in trackedControllers) {
                trackedControllers[ctrl.sessionToken] = ctrl
                runCatching { ctrl.registerCallback(controllerCallback, mainHandler) }
            }
        }

        val active = selectActiveController(this, eligibleSessions)
        _nowPlaying.value = active?.let { buildNowPlayingState(this, it) }
    }

    companion object {
        private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
        val nowPlaying: StateFlow<NowPlayingState?> = _nowPlaying.asStateFlow()

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        @Volatile
        private var instance: MediaNotificationListenerService? = null

        fun isNotificationListenerEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun requestRefresh(context: Context) {
            val svc = instance
            if (svc != null) {
                svc.refreshActiveSessions()
                return
            }

            val appContext = context.applicationContext
            if (!isNotificationListenerEnabled(appContext)) {
                _nowPlaying.value = null
                return
            }

            val component = ComponentName(appContext, MediaNotificationListenerService::class.java)
            runCatching { requestRebind(component) }

            val msm = appContext.getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val sessions = try {
                msm.getActiveSessions(component)
            } catch (_: SecurityException) {
                emptyList()
            }
            val active = selectActiveController(appContext, sessions)
            _nowPlaying.value = active?.let { buildNowPlayingState(appContext, it) }
        }

        private fun hasTitleMetadata(ctrl: MediaController): Boolean {
            val meta = ctrl.metadata ?: return false
            val title = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            return !title.isNullOrBlank()
        }

        private fun isTransitionPlaybackState(state: Int?): Boolean = when (state) {
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> true
            else -> false
        }

        private fun selectActiveController(
            ctx: Context,
            sessions: List<MediaController>
        ): MediaController? {
            val external = sessions.filter { it.packageName != ctx.packageName }
            if (external.isEmpty()) return null

            val playing = external.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            if (playing.isNotEmpty()) {
                return playing.firstOrNull { hasTitleMetadata(it) } ?: playing.first()
            }

            val transitioning = external.filter { isTransitionPlaybackState(it.playbackState?.state) }
            if (transitioning.isNotEmpty()) {
                return transitioning.firstOrNull { hasTitleMetadata(it) } ?: transitioning.first()
            }

            val pausedWithMetadata = external.filter {
                it.playbackState?.state == PlaybackState.STATE_PAUSED && hasTitleMetadata(it)
            }
            return pausedWithMetadata.firstOrNull()
        }

        private fun buildNowPlayingState(
            ctx: Context,
            controller: MediaController
        ): NowPlayingState {
            val meta = controller.metadata
            val playbackState = controller.playbackState?.state
            val isPlaying = playbackState == PlaybackState.STATE_PLAYING
            val isBuffering = isTransitionPlaybackState(playbackState)

            val title = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?: if (isPlaying || isBuffering) {
                    runCatching {
                        val pm = ctx.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(controller.packageName, 0)).toString()
                    }.getOrDefault(AppLanguageManager.localizedString(ctx, R.string.media_playing))
                } else {
                    ""
                }

            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_AUTHOR)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() }
                ?: ""

            val albumArt = runCatching {
                meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            }.getOrNull()

            val artUri = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)?.takeIf { it.isNotBlank() }

            return NowPlayingState(
                title = title,
                artist = artist,
                albumArt = albumArt,
                artUri = artUri,
                isPlaying = isPlaying,
                controller = controller
            )
        }
    }
}
