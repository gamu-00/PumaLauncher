package com.openlauncher.app.mediastation

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Color as AColor

private const val YOUTUBE_URL = "https://m.youtube.com"

/**
 * Vanilla YouTube WebView with a minimal "✕" bar. Fullscreen videos are mounted in an overlay
 * FrameLayout that lives inside the card's bounds while keeping the underlying WebView visible
 * so Chromium never suspends media playback. The WebView is destroyed on dispose.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun YouTubeView(onClose: () -> Unit) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var hideCustomView: (() -> Unit)? by remember { mutableStateOf(null) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    val audioFocusRequest = remember {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { /* Keep WebView session active while YouTubeView is open */ }
            .build()
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

    val fullscreenContainer = remember(context) {
        FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(AColor.BLACK)
            visibility = View.GONE
        }
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
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = userAgentString.replace("; wv", "")
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    BackHandler(enabled = isFullscreen) { hideCustomView?.invoke() }

    DisposableEffect(Unit) {
        audioManager?.requestAudioFocus(audioFocusRequest)

        var customView: View? = null
        var customViewCallback: WebChromeClient.CustomViewCallback? = null

        fun exitFullscreen() {
            val activeView = customView
            val activeCallback = customViewCallback
            if (activeView != null) {
                fullscreenContainer.removeView(activeView)
            }
            fullscreenContainer.removeAllViews()
            fullscreenContainer.visibility = View.GONE
            customView = null
            customViewCallback = null
            activeCallback?.onCustomViewHidden()
            webView.visibility = View.VISIBLE
            isFullscreen = false
        }

        hideCustomView = ::exitFullscreen

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                audioManager?.requestAudioFocus(audioFocusRequest)
                customView = view
                customViewCallback = callback

                (view.parent as? ViewGroup)?.removeView(view)

                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        val lp = child.layoutParams
                        if (lp != null) {
                            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                            child.layoutParams = lp
                        } else {
                            child.layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    }
                }

                fullscreenContainer.removeAllViews()
                fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.bringToFront()
                webView.visibility = View.VISIBLE
                isFullscreen = true
            }

            override fun onHideCustomView() {
                exitFullscreen()
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
        if (fullscreenContainer.parent == null) {
            rootContainer.addView(
                fullscreenContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        if (webView.url == null) {
            webView.loadUrl(YOUTUBE_URL)
        }

        onDispose {
            exitFullscreen()
            hideCustomView = null
            audioManager?.abandonAudioFocusRequest(audioFocusRequest)
            CookieManager.getInstance().flush()
            webView.stopLoading()
            webView.webChromeClient = null
            webView.loadUrl("about:blank")
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
            fullscreenContainer.removeAllViews()
            rootContainer.removeAllViews()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isFullscreen) {
            MediaTopBar("YOUTUBE", onClose)
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { rootContainer },
            update = { container ->
                container.layoutParams = container.layoutParams?.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                } ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        )
    }
}