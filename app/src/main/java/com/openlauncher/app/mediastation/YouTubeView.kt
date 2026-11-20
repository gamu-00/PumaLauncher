package com.openlauncher.app.mediastation

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openlauncher.app.R
import com.openlauncher.app.mediastation.ui.MediaPlayerView
import com.openlauncher.app.mediastation.ui.MediaStationViewModel
import com.openlauncher.app.ui.theme.AaAmberAlt
import com.openlauncher.app.ui.theme.AaBorder
import com.openlauncher.app.ui.theme.AaPanel
import com.openlauncher.app.ui.theme.AaTextPrimary
import kotlinx.coroutines.delay
import android.graphics.Color as AColor

private const val YOUTUBE_URL = "https://m.youtube.com"

/**
 * Immediate JS to silence, pause, and strip sources from any `<video>` or `<audio>` in the DOM
 * so playback never occurs inside the WebView.
 */
private const val SILENCE_AND_CLEAR_VIDEOS_JS = """
(function() {
    try {
        var mediaList = document.querySelectorAll('video, audio');
        for (var i = 0; i < mediaList.length; i++) {
            var m = mediaList[i];
            try {
                m.muted = true;
                m.volume = 0;
                m.pause();
                m.removeAttribute('src');
                m.srcObject = null;
                m.load();
            } catch (e) {}
        }
    } catch (e) {}
})();
"""

/**
 * CSS cleanup for promotional/open-in-app banners + capture-phase media blocker ensuring
 * the WebView functions strictly as a browsing and Google login surface.
 */
private const val CLEAN_PROMO_CSS_JS = """
(function() {
    try {
        if (!document.getElementById('puma-yt-clean-style')) {
            var style = document.createElement('style');
            style.id = 'puma-yt-clean-style';
            style.textContent = [
                'ytm-app-promo-banner-renderer,',
                'ytm-mealbar-promo-renderer,',
                'ytm-statement-banner-renderer,',
                'ytm-upsell-dialog-renderer,',
                'ytm-promoted-sparkles-web-renderer,',
                '.mobile-topbar-header-open-in-app,',
                '.open-in-app-banner,',
                '.smartbanner,',
                'a[href^="intent://"],',
                'a[href*="play.google.com/store/apps/details?id=com.google.android.youtube"] {',
                '  display: none !important;',
                '  height: 0 !important;',
                '  max-height: 0 !important;',
                '  overflow: hidden !important;',
                '  pointer-events: none !important;',
                '}'
            ].join('\n');
            (document.head || document.documentElement).appendChild(style);
        }

        if (!document.__pumaMediaSilencerHooked) {
            document.__pumaMediaSilencerHooked = true;
            var stopMedia = function(e) {
                var target = e && e.target;
                if (target && (target.tagName === 'VIDEO' || target.tagName === 'AUDIO')) {
                    try {
                        target.muted = true;
                        target.volume = 0;
                        target.pause();
                        target.removeAttribute('src');
                        target.srcObject = null;
                        target.load();
                    } catch (err) {}
                }
            };
            document.addEventListener('play', stopMedia, true);
            document.addEventListener('playing', stopMedia, true);
            document.addEventListener('loadedmetadata', stopMedia, true);
        }
    } catch (e) {}
})();
"""

private fun sanitizeUserAgent(rawUa: String): String {
    return rawUa
        .replace("; wv", "")
        .replace("wv; ", "")
        .replace(Regex("""Version/\d+(\.\d+)*\s*"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun isYouTubeHost(host: String?): Boolean {
    val h = host?.lowercase() ?: return false
    return h == "youtube.com" ||
        h == "m.youtube.com" ||
        h == "www.youtube.com" ||
        h == "music.youtube.com" ||
        h == "youtu.be" ||
        h.endsWith(".youtube.com")
}

/**
 * Extracts the YouTube video ID if [uri] points to `/watch?v=<ID>`, `/shorts/<ID>`, or `youtu.be/<ID>`.
 */
private fun extractYouTubeVideoId(uri: Uri?): String? {
    if (uri == null) return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase() ?: return null
    if (!isYouTubeHost(host)) return null

    if (host == "youtu.be") {
        return uri.pathSegments.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    val path = uri.path.orEmpty()
    return when {
        path == "/watch" || path.startsWith("/watch/") -> {
            uri.getQueryParameter("v")?.trim()?.takeIf { it.isNotEmpty() }
        }
        path.startsWith("/shorts/") -> {
            uri.pathSegments.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
        else -> null
    }
}

private fun extractYouTubeVideoId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    return extractYouTubeVideoId(parsed)
}

/**
 * Pure YouTube browsing WebView with persistent Google login (including OAuth/2FA popup overlay)
 * and instant interception of `/watch?v=` and `/shorts/` links into full-card 720p ExoPlayer.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun YouTubeView(
    viewModel: MediaStationViewModel,
    isCompactHeight: Boolean = false,
    isHomeVisible: Boolean = true,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val youTubeState by viewModel.youTubeState.collectAsStateWithLifecycle()
    val latestViewModel by rememberUpdatedState(viewModel)

    var isPopupOpen by remember { mutableStateOf(false) }
    var closeTopPopup: (() -> Unit)? by remember { mutableStateOf(null) }

    // Auto-dismiss discrete temporary notice ("Video non riproducibile") after 3 seconds.
    LaunchedEffect(youTubeState.noticeId, youTubeState.noticeMessage) {
        if (youTubeState.noticeMessage != null) {
            delay(3_000L)
            viewModel.dismissYouTubeNotice()
        }
    }

    val rootContainer = remember(context) {
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(AColor.BLACK)
        }
    }

    val popupContainer = remember(context) {
        FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(AColor.BLACK)
            visibility = View.GONE
        }
    }

    val interceptionTracker = remember {
        object {
            var lastVideoId: String? = null
            var lastTimestampMs: Long = 0L

            fun shouldTriggerExtraction(videoId: String): Boolean {
                val now = SystemClock.elapsedRealtime()
                val isDuplicate = videoId == lastVideoId && (now - lastTimestampMs) < 1_200L
                if (!isDuplicate) {
                    lastVideoId = videoId
                    lastTimestampMs = now
                }
                return !isDuplicate
            }
        }
    }

    fun interceptVideoNavigation(
        targetWebView: WebView?,
        videoId: String,
        revertSpaHistory: Boolean
    ) {
        targetWebView?.stopLoading()
        targetWebView?.evaluateJavascript(SILENCE_AND_CLEAR_VIDEOS_JS, null)
        if (revertSpaHistory) {
            if (targetWebView?.canGoBack() == true) {
                targetWebView.goBack()
            } else {
                targetWebView?.loadUrl(YOUTUBE_URL)
            }
        }
        targetWebView?.postDelayed(
            { targetWebView.evaluateJavascript(SILENCE_AND_CLEAR_VIDEOS_JS, null) },
            120L
        )
        if (interceptionTracker.shouldTriggerExtraction(videoId)) {
            val cleanWatchUrl = "https://www.youtube.com/watch?v=$videoId"
            latestViewModel.playYouTubeVideo(cleanWatchUrl)
        }
    }

    fun handleUrlOverride(targetWebView: WebView?, uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false

        if (scheme == "http" || scheme == "https") {
            val videoId = extractYouTubeVideoId(uri)
            if (videoId != null) {
                val currentVideoId = extractYouTubeVideoId(targetWebView?.url)
                interceptVideoNavigation(
                    targetWebView = targetWebView,
                    videoId = videoId,
                    revertSpaHistory = currentVideoId != null
                )
                return true
            }
            return false
        }

        if (scheme == "intent") {
            val rawUrl = uri.toString()
            val fallbackUrl = runCatching {
                Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME)
                    ?.getStringExtra("browser_fallback_url")
            }.getOrNull()
            if (!fallbackUrl.isNullOrBlank() &&
                (fallbackUrl.startsWith("https://") || fallbackUrl.startsWith("http://"))
            ) {
                val fallbackVideoId = extractYouTubeVideoId(fallbackUrl)
                if (fallbackVideoId != null) {
                    interceptVideoNavigation(
                        targetWebView = targetWebView,
                        videoId = fallbackVideoId,
                        revertSpaHistory = false
                    )
                } else {
                    targetWebView?.loadUrl(fallbackUrl)
                }
            }
            return true
        }

        // Block external schemes (vnd.youtube://, market://, etc.) from leaving the dashboard.
        return true
    }

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                userAgentString = sanitizeUserAgent(userAgentString)
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return handleUrlOverride(view, request?.url)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val parsed = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
                    return handleUrlOverride(view, parsed)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val videoId = extractYouTubeVideoId(url)
                    if (videoId != null) {
                        interceptVideoNavigation(
                            targetWebView = view,
                            videoId = videoId,
                            revertSpaHistory = true
                        )
                    } else {
                        view?.evaluateJavascript(CLEAN_PROMO_CSS_JS, null)
                        view?.evaluateJavascript(SILENCE_AND_CLEAR_VIDEOS_JS, null)
                    }
                    CookieManager.getInstance().flush()
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    val videoId = extractYouTubeVideoId(url)
                    if (videoId != null) {
                        interceptVideoNavigation(
                            targetWebView = view,
                            videoId = videoId,
                            revertSpaHistory = true
                        )
                    } else {
                        view?.evaluateJavascript(CLEAN_PROMO_CSS_JS, null)
                    }
                }
            }
        }
    }

    // Back handling when OAuth popup is open or while stream extraction is in progress.
    BackHandler(enabled = isHomeVisible && youTubeState.activeStream == null && (isPopupOpen || youTubeState.isExtracting)) {
        if (youTubeState.isExtracting) {
            viewModel.closeYouTubePlayer()
        } else if (isPopupOpen) {
            closeTopPopup?.invoke()
        }
    }

    DisposableEffect(webView) {
        val popupWebViews = mutableListOf<WebView>()

        fun destroyPopup(popup: WebView?) {
            if (popup == null) return
            popupWebViews.remove(popup)
            CookieManager.getInstance().flush()
            runCatching {
                popup.stopLoading()
                popup.webViewClient = WebViewClient()
                popup.webChromeClient = null
                popup.loadUrl("about:blank")
                (popup.parent as? ViewGroup)?.removeView(popup)
                popup.removeAllViews()
                popup.destroy()
            }
            if (popupWebViews.isEmpty()) {
                popupContainer.removeAllViews()
                popupContainer.visibility = View.GONE
                isPopupOpen = false
            }
        }

        fun closeAllPopups() {
            val copy = popupWebViews.toList()
            popupWebViews.clear()
            copy.forEach { popup ->
                runCatching {
                    popup.stopLoading()
                    popup.webViewClient = WebViewClient()
                    popup.webChromeClient = null
                    (popup.parent as? ViewGroup)?.removeView(popup)
                    popup.removeAllViews()
                    popup.destroy()
                }
            }
            popupContainer.removeAllViews()
            popupContainer.visibility = View.GONE
            isPopupOpen = false
            CookieManager.getInstance().flush()
        }

        closeTopPopup = {
            val top = popupWebViews.lastOrNull()
            if (top != null) {
                if (top.canGoBack()) {
                    top.goBack()
                } else {
                    destroyPopup(top)
                }
            } else {
                closeAllPopups()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popupWebView = WebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = true
                        userAgentString = sanitizeUserAgent(userAgentString)
                    }
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val reqUri = request?.url
                            val videoId = extractYouTubeVideoId(reqUri)
                            if (videoId != null) {
                                destroyPopup(this@apply)
                                interceptVideoNavigation(
                                    targetWebView = webView,
                                    videoId = videoId,
                                    revertSpaHistory = false
                                )
                                return true
                            }
                            return handleUrlOverride(v, reqUri)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                            val parsed = url?.let { runCatching { Uri.parse(it) }.getOrNull() }
                            val videoId = extractYouTubeVideoId(parsed)
                            if (videoId != null) {
                                destroyPopup(this@apply)
                                interceptVideoNavigation(
                                    targetWebView = webView,
                                    videoId = videoId,
                                    revertSpaHistory = false
                                )
                                return true
                            }
                            return handleUrlOverride(v, parsed)
                        }

                        override fun onPageFinished(v: WebView?, url: String?) {
                            super.onPageFinished(v, url)
                            CookieManager.getInstance().flush()
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView?) {
                            destroyPopup(window ?: this@apply)
                        }
                    }
                }

                popupWebViews.add(popupWebView)
                popupContainer.addView(
                    popupWebView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                popupContainer.visibility = View.VISIBLE
                popupContainer.bringToFront()
                isPopupOpen = true

                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                destroyPopup(window)
            }
        }

        if (webView.parent == null) {
            rootContainer.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        if (popupContainer.parent == null) {
            rootContainer.addView(
                popupContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        webView.onResume()
        webView.resumeTimers()
        if (webView.url == null) {
            webView.loadUrl(YOUTUBE_URL)
        }

        onDispose {
            closeAllPopups()
            closeTopPopup = null
            CookieManager.getInstance().flush()
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = null
            webView.loadUrl("about:blank")
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
            popupContainer.removeAllViews()
            rootContainer.removeAllViews()
        }
    }

    val activeStream = youTubeState.activeStream

    // Ensure WebView never plays audio while ExoPlayer is active.
    LaunchedEffect(activeStream) {
        if (activeStream != null) {
            webView.evaluateJavascript(SILENCE_AND_CLEAR_VIDEOS_JS, null)
        } else {
            interceptionTracker.lastVideoId = null
            webView.onResume()
            webView.resumeTimers()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Keep the browsing WebView mounted so its state/scroll position/login session are preserved.
        Column(modifier = Modifier.fillMaxSize()) {
            if (activeStream == null) {
                MediaTopBar(
                    title = stringResource(R.string.youtube_topbar_title),
                    onClose = {
                        if (isPopupOpen) {
                            closeTopPopup?.invoke()
                        } else {
                            onClose()
                        }
                    },
                    isCompactHeight = isCompactHeight
                )
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { rootContainer },
                update = {
                    if (activeStream == null) {
                        webView.onResume()
                        webView.resumeTimers()
                    }
                }
            )
        }

        // Rapid loading indicator overlay while extracting 720p stream via NewPipeExtractor.
        AnimatedVisibility(
            visible = youTubeState.isExtracting && activeStream == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AaPanel.copy(alpha = 0.95f))
                        .border(1.dp, AaAmberAlt, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AaAmberAlt,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = stringResource(R.string.youtube_loading_stream),
                        color = AaTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Discrete temporary notice ("Video non riproducibile") when extraction fails (no WebView fallback).
        AnimatedVisibility(
            visible = youTubeState.noticeMessage != null && activeStream == null && !youTubeState.isExtracting,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp, start = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AaPanel.copy(alpha = 0.94f))
                    .border(1.dp, AaBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = youTubeState.noticeMessage.orEmpty(),
                    color = AaTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Full-frame dedicated card ExoPlayer (100% of card space, edge-to-edge without margins or fixed bars).
        if (activeStream != null) {
            MediaPlayerView(
                title = activeStream.title,
                streams = listOf(activeStream),
                currentStreamIndex = 0,
                resumeOffsetMs = 0L,
                isResolvingStreams = false,
                streamError = null,
                isCompactHeight = isCompactHeight,
                isHomeVisible = isHomeVisible,
                isYouTubeMode = true,
                onCycleStream = {},
                onStreamError = { viewModel.onYouTubePlaybackError() },
                onRetryResolve = {},
                onSyncProgress = { _, _ -> },
                onPlaybackActiveChanged = { viewModel.setVideoPlaying(it) },
                onBack = { viewModel.closeYouTubePlayer() },
                onClose = {
                    viewModel.closeYouTubePlayer()
                    onClose()
                }
            )
        }
    }
}
