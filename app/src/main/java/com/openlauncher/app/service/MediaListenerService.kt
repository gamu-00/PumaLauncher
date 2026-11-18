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
import com.openlauncher.app.model.NowPlayingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MediaListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackedControllers = mutableMapOf<MediaSession.Token, MediaController>()
    private var activeController: MediaController? = null
    private var sessionListenerRegistered = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshNowPlaying()
        override fun onMetadataChanged(metadata: MediaMetadata?)   = refreshNowPlaying()
        override fun onSessionDestroyed()                          = refreshNowPlaying()
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { _ ->
        refreshNowPlaying()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isConnected.value = true
        registerSessionsListener()
        refreshNowPlaying()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        unregisterSessionsListener()
        instance = null
        isConnected.value = false
        clearAllControllers()
        _nowPlaying.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?)  { refreshNowPlaying() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refreshNowPlaying() }

    override fun onDestroy() {
        unregisterSessionsListener()
        instance = null
        clearAllControllers()
        _nowPlaying.value = null
        super.onDestroy()
    }

    private fun registerSessionsListener() {
        if (sessionListenerRegistered) return
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        try {
            msm.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                ComponentName(this, MediaListenerService::class.java),
                mainHandler
            )
            sessionListenerRegistered = true
        } catch (_: SecurityException) {
        }
    }

    private fun unregisterSessionsListener() {
        if (!sessionListenerRegistered) return
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
        try {
            msm?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {
        }
        sessionListenerRegistered = false
    }

    private fun clearAllControllers() {
        trackedControllers.values.forEach { ctrl ->
            runCatching { ctrl.unregisterCallback(controllerCallback) }
        }
        trackedControllers.clear()
        activeController = null
    }

    private fun refreshNowPlaying() {
        refreshSessionsFromContext(this)
    }

    private fun refreshSessionsFromContext(ctx: Context) {
        val msm = ctx.getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val component = ComponentName(ctx, MediaListenerService::class.java)
        val sessions: List<MediaController> = try {
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            emptyList()
        }

        if (sessions.isEmpty()) {
            clearAllControllers()
            _nowPlaying.value = null
            return
        }

        // Sync callbacks across ALL active sessions so background players (YouTube, ReVanced,
        // YouTube Music, podcast apps, etc.) immediately notify us when they start playing.
        val currentTokens = sessions.map { it.sessionToken }.toSet()
        val removedTokens = trackedControllers.keys.filter { it !in currentTokens }
        removedTokens.forEach { token ->
            trackedControllers.remove(token)?.let { ctrl ->
                runCatching { ctrl.unregisterCallback(controllerCallback) }
            }
        }
        sessions.forEach { ctrl ->
            if (ctrl.sessionToken !in trackedControllers) {
                trackedControllers[ctrl.sessionToken] = ctrl
                runCatching { ctrl.registerCallback(controllerCallback, mainHandler) }
            }
        }

        val active = selectBestController(sessions)
        if (active == null) {
            activeController = null
            _nowPlaying.value = null
            return
        }

        activeController = active
        updateFromController(ctx, active)
    }

    companion object {
        private val PRIORITY_PACKAGES = setOf(
            "com.google.android.apps.youtube.music",
            "com.spotify.music",
            "app.revanced.android.youtube",
            "com.google.android.youtube"
        )

        private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
        val nowPlaying: StateFlow<NowPlayingState?> = _nowPlaying
        val isConnected = MutableStateFlow(false)

        @Volatile private var instance: MediaListenerService? = null

        fun requestRefresh(context: Context? = null) {
            val svc = instance
            if (svc != null) {
                svc.refreshNowPlaying()
            } else if (context != null) {
                refreshStatic(context)
            }
        }

        private fun hasTitleMetadata(ctrl: MediaController): Boolean {
            val meta = ctrl.metadata ?: return false
            val title = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            return !title.isNullOrBlank()
        }

        private fun selectBestController(sessions: List<MediaController>): MediaController? {
            // 1. Strict priority to any controller in PlaybackState.STATE_PLAYING
            val playing = sessions.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            if (playing.isNotEmpty()) {
                return playing.firstOrNull { hasTitleMetadata(it) }
                    ?: playing.firstOrNull { it.packageName in PRIORITY_PACKAGES }
                    ?: playing.first()
            }

            // 2. Buffering / active transition state
            val buffering = sessions.filter {
                val st = it.playbackState?.state
                st == PlaybackState.STATE_BUFFERING ||
                    st == PlaybackState.STATE_FAST_FORWARDING ||
                    st == PlaybackState.STATE_REWINDING
            }
            if (buffering.isNotEmpty()) {
                return buffering.firstOrNull { hasTitleMetadata(it) } ?: buffering.first()
            }

            // 3. Paused or other sessions that have valid track/video metadata
            return sessions.firstOrNull { hasTitleMetadata(it) && it.packageName in PRIORITY_PACKAGES }
                ?: sessions.firstOrNull { hasTitleMetadata(it) }
                ?: sessions.firstOrNull()
        }

        private fun refreshStatic(ctx: Context) {
            val msm = ctx.getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val sessions = try {
                msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
            } catch (_: SecurityException) {
                emptyList()
            }
            val active = selectBestController(sessions)
            if (active == null) {
                _nowPlaying.value = null
                return
            }
            updateFromControllerStatic(ctx, active)
        }

        private fun updateFromControllerStatic(ctx: Context, controller: MediaController?) {
            if (controller == null) {
                _nowPlaying.value = null
                return
            }
            val meta = controller.metadata
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

            // Title fallback chain: METADATA_KEY_DISPLAY_TITLE -> METADATA_KEY_TITLE
            val title = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?: if (isPlaying) {
                    runCatching {
                        val pm = ctx.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(controller.packageName, 0)).toString()
                    }.getOrDefault("In riproduzione")
                } else {
                    ""
                }

            // Artist/channel fallback chain: METADATA_KEY_ARTIST -> METADATA_KEY_AUTHOR -> METADATA_KEY_DISPLAY_SUBTITLE
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_AUTHOR)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_WRITER)?.takeIf { it.isNotBlank() }
                ?: ""

            // Cover art fallback chain: METADATA_KEY_ALBUM_ART -> METADATA_KEY_ART -> METADATA_KEY_DISPLAY_ICON
            val art = try {
                meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            } catch (_: Exception) {
                null
            }

            val artUri = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)?.takeIf { it.isNotBlank() }
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)?.takeIf { it.isNotBlank() }

            _nowPlaying.value = NowPlayingState(
                title      = title,
                artist     = artist,
                albumArt   = art,
                artUri     = artUri,
                isPlaying  = isPlaying,
                controller = controller
            )
        }
    }

    private fun updateFromController(ctx: Context, controller: MediaController?) {
        updateFromControllerStatic(ctx, controller)
    }
}
