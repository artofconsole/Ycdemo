package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val YOUTUBE_HOST_ORIGIN = "https://www.youtube-nocookie.com"

private class YouTubeBridge(
    private val onReady: () -> Unit,
    private val onProgress: (Long, Long) -> Unit,
    private val onStateChanged: (Int) -> Unit,
    private val onError: (Int) -> Unit,
) {
    @JavascriptInterface
    fun notifyReady() {
        onReady()
    }

    @JavascriptInterface
    fun notifyProgress(positionMs: Long, durationMs: Long) {
        onProgress(positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L))
    }

    @JavascriptInterface
    fun notifyState(state: Int) {
        onStateChanged(state)
    }

    @JavascriptInterface
    fun notifyError(code: Int) {
        onError(code)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    playWhenReady: Boolean,
    seekCommandId: Int,
    seekTargetMs: Long,
    onPlaybackChanged: (Boolean) -> Unit,
    onProgressChanged: (Long, Long) -> Unit,
    onEnded: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestPlayWhenReady by rememberUpdatedState(playWhenReady)
    val latestOnPlaybackChanged by rememberUpdatedState(onPlaybackChanged)
    val latestOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val latestOnEnded by rememberUpdatedState(onEnded)
    val latestOnError by rememberUpdatedState(onError)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isReady by remember(videoId) { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val host = request?.url?.host?.lowercase().orEmpty()
                    return !(host.contains("youtube.com") || host.contains("youtube-nocookie.com") || host.contains("googlevideo.com") || host.contains("ytimg.com"))
                }
            }
            addJavascriptInterface(
                YouTubeBridge(
                    onReady = {
                        post {
                            isReady = true
                            val cmd = if (latestPlayWhenReady) "window.ytPlay && window.ytPlay();" else "window.ytPause && window.ytPause();"
                            evaluateJavascript(cmd, null)
                        }
                    },
                    onProgress = { positionMs, durationMs ->
                        post {
                            if (!isReady && durationMs > 0L) isReady = true
                            latestOnProgressChanged(positionMs, durationMs)
                        }
                    },
                    onStateChanged = { state ->
                        post {
                            if (!isReady) isReady = true
                            when (state) {
                                1 -> latestOnPlaybackChanged(true)
                                2 -> latestOnPlaybackChanged(false)
                                0 -> {
                                    latestOnPlaybackChanged(false)
                                    latestOnEnded()
                                }
                            }
                        }
                    },
                    onError = { code ->
                        post {
                            val message = when (code) {
                                2 -> "Geçersiz YouTube video kimliği."
                                5 -> "Video HTML5 oynatıcıda açılamadı."
                                100 -> "Video bulunamadı veya özel yapılmış."
                                101, 150 -> "Video sahibi bu videonun uygulama içi gömülü oynatılmasını kısıtlamış."
                                else -> "YouTube oynatma hatası ($code)."
                            }
                            latestOnError(message)
                        }
                    },
                ),
                "AndroidBridge",
            )
        }
    }

    DisposableEffect(webView) {
        webViewRef = webView
        onDispose {
            webViewRef = null
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(videoId) {
        isReady = false
        val safeVideoId = Uri.encode(videoId)
        val html = buildYouTubeHtml(safeVideoId, playWhenReady)
        webView.loadDataWithBaseURL(
            YOUTUBE_HOST_ORIGIN,
            html,
            "text/html",
            "utf-8",
            null,
        )
    }

    LaunchedEffect(playWhenReady, isReady, videoId) {
        val current = webViewRef ?: return@LaunchedEffect
        val script = if (playWhenReady) {
            "window.ytPlay && window.ytPlay();"
        } else {
            "window.ytPause && window.ytPause();"
        }
        current.evaluateJavascript(script, null)
    }

    LaunchedEffect(seekCommandId, isReady, videoId) {
        if (seekCommandId <= 0) return@LaunchedEffect
        val current = webViewRef ?: return@LaunchedEffect
        val seconds = (seekTargetMs.coerceAtLeast(0L) / 1000.0)
        current.evaluateJavascript("window.ytSeek && window.ytSeek($seconds);", null)
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
    )
}

private fun buildYouTubeHtml(videoId: String, autoplay: Boolean): String {
    val autoFlag = if (autoplay) 1 else 0
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
          <style>
            html, body {
              margin: 0;
              padding: 0;
              width: 100%;
              height: 100%;
              background: #000;
              overflow: hidden;
            }
            #player, iframe {
              width: 100%;
              height: 100%;
              border: 0;
            }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script>
            var player = null;
            var playerReady = false;
            var pendingPlay = ${if (autoplay) "true" else "false"};
            var pendingSeek = null;

            window.ytPlay = function() {
              pendingPlay = true;
              if (player && playerReady && typeof player.playVideo === 'function') {
                player.playVideo();
              }
            };

            window.ytPause = function() {
              pendingPlay = false;
              if (player && playerReady && typeof player.pauseVideo === 'function') {
                player.pauseVideo();
              }
            };

            window.ytSeek = function(sec) {
              pendingSeek = sec;
              if (player && playerReady && typeof player.seekTo === 'function') {
                player.seekTo(sec, true);
                pendingSeek = null;
              }
            };

            var tag = document.createElement('script');
            tag.src = "https://www.youtube.com/iframe_api";
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                videoId: '$videoId',
                playerVars: {
                  autoplay: $autoFlag,
                  controls: 0,
                  rel: 0,
                  fs: 0,
                  modestbranding: 1,
                  playsinline: 1,
                  iv_load_policy: 3,
                  origin: '$YOUTUBE_HOST_ORIGIN'
                },
                events: {
                  onReady: function(event) {
                    playerReady = true;
                    AndroidBridge.notifyReady();
                    if (pendingSeek !== null) {
                      event.target.seekTo(pendingSeek, true);
                      pendingSeek = null;
                    }
                    if (pendingPlay) {
                      event.target.playVideo();
                    } else {
                      event.target.pauseVideo();
                    }
                  },
                  onStateChange: function(event) {
                    playerReady = true;
                    AndroidBridge.notifyState(event.data);
                  },
                  onError: function(event) {
                    AndroidBridge.notifyError(event.data);
                  }
                }
              });
            }

            setInterval(function() {
              if (player && playerReady && typeof player.getCurrentTime === 'function' && typeof player.getDuration === 'function') {
                var pos = Math.round((player.getCurrentTime() || 0) * 1000);
                var dur = Math.round((player.getDuration() || 0) * 1000);
                AndroidBridge.notifyProgress(pos, dur);
              }
            }, 350);
          </script>
        </body>
        </html>
    """.trimIndent()
}
