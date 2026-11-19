package com.openlauncher.app.mediastation

import android.annotation.SuppressLint
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.openlauncher.app.R
import android.graphics.Color as AColor

private const val YOUTUBE_URL = "https://m.youtube.com"

private const val ANTI_PAUSE_JS = """
(function() {
    try {
        var props = {
            hidden: false,
            webkitHidden: false,
            visibilityState: 'visible',
            webkitVisibilityState: 'visible'
        };
        for (var key in props) {
            if (Object.prototype.hasOwnProperty.call(props, key)) {
                (function(propName, propVal) {
                    try {
                        Object.defineProperty(document, propName, {
                            configurable: true,
                            get: function() { return propVal; }
                        });
                    } catch (e) {}
                })(key, props[key]);
            }
        }
        var blockEvent = function(e) {
            e.stopImmediatePropagation();
            e.stopPropagation();
        };
        if (!document.__pumaVisibilityPatched) {
            document.__pumaVisibilityPatched = true;
            document.addEventListener('visibilitychange', blockEvent, true);
            document.addEventListener('webkitvisibilitychange', blockEvent, true);
        }
        if (!window.__pumaVisibilityPatched) {
            window.__pumaVisibilityPatched = true;
            window.addEventListener('visibilitychange', blockEvent, true);
            window.addEventListener('webkitvisibilitychange', blockEvent, true);
        }
    } catch (e) {}
})();
"""

/**
 * Vanilla YouTube WebView with a minimal "✕" bar. Fullscreen videos are mounted in an overlay
 * FrameLayout that lives inside the card's bounds while keeping the underlying WebView visible
 * so Chromium never suspends media playback. The WebView is destroyed on dispose.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun YouTubeView(
    isCompactHeight: Boolean = false,
    isHomeVisible: Boolean = true,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var hideCustomView: (() -> Unit)? by remember { mutableStateOf(null) }

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
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(ANTI_PAUSE_JS, null)
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    view?.evaluateJavascript(ANTI_PAUSE_JS, null)
                }
            }
        }
    }

    BackHandler(enabled = isHomeVisible && isFullscreen) { hideCustomView?.invoke() }

    DisposableEffect(Unit) {
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

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
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
            CookieManager.getInstance().flush()
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
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
            MediaTopBar(
                title = stringResource(R.string.youtube_topbar_title),
                onClose = onClose,
                isCompactHeight = isCompactHeight
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { rootContainer },
            update = { }
        )
    }
}