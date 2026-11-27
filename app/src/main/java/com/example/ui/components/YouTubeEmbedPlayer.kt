package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.CherryRed
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.TheaterBlack
import kotlinx.coroutines.delay

@Stable
class YouTubePlayerController {
    internal var webView: WebView? = null

    fun play() {
        webView?.evaluateJavascript(
            """
            (function() {
              if (window.yCloudPlay) { window.yCloudPlay(); return; }
              var mp = document.getElementById('movie_player');
              if (mp && mp.playVideo) { mp.playVideo(); return; }
              var v = document.querySelector('video');
              if (v) { v.play(); }
            })();
            """.trimIndent(),
            null
        )
    }

    fun pause() {
        webView?.evaluateJavascript(
            """
            (function() {
              if (window.yCloudPause) { window.yCloudPause(); return; }
              var mp = document.getElementById('movie_player');
              if (mp && mp.pauseVideo) { mp.pauseVideo(); return; }
              var v = document.querySelector('video');
              if (v) { v.pause(); }
            })();
            """.trimIndent(),
            null
        )
    }

    fun seekToMs(ms: Long) {
        val sec = (ms.coerceAtLeast(0L) / 1000.0)
        webView?.evaluateJavascript(
            """
            (function() {
              if (window.yCloudSeek) { window.yCloudSeek($sec); return; }
              var mp = document.getElementById('movie_player');
              if (mp && mp.seekTo) { mp.seekTo($sec, true); return; }
              var v = document.querySelector('video');
              if (v) { v.currentTime = $sec; }
            })();
            """.trimIndent(),
            null
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    startPositionMs: Long,
    controller: YouTubePlayerController,
    onPlayingChanged: (Boolean) -> Unit,
    onProgressChanged: (positionMs: Long, durationMs: Long) -> Unit,
    onEnded: () -> Unit,
    onPlayNextRequested: () -> Unit,
    onBackRequested: () -> Unit,
    onHasErrorChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val appOrigin = remember(context) { "https://${context.packageName}" }
    val appReferer = remember(appOrigin) { "$appOrigin/" }

    var activeVideoId by remember { mutableStateOf(videoId) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var useDirectEmbedMode by remember { mutableStateOf(false) }
    var embedErrorCode by remember { mutableStateOf<Int?>(null) }

    if (activeVideoId != videoId) {
        activeVideoId = videoId
        isPlayerReady = false
        useDirectEmbedMode = false
        embedErrorCode = null
    }

    val latestOnHasErrorChanged by rememberUpdatedState(onHasErrorChanged)
    LaunchedEffect(embedErrorCode) {
        latestOnHasErrorChanged(embedErrorCode != null)
    }

    val latestOnPlaying by rememberUpdatedState(onPlayingChanged)
    val latestOnProgress by rememberUpdatedState(onProgressChanged)
    val latestOnEnded by rememberUpdatedState(onEnded)

    val webView = remember(context) {
        val blackPoster = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            // Keep Android TV D-Pad focus on Compose player controls
            isFocusable = false
            isFocusableInTouchMode = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap = blackPoster
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    if (request?.isForMainFrame == false) return false
                    val targetUrl = request?.url?.toString().orEmpty()
                    // Allow embedded YouTube frames inside player, block external navigation away from yCloud
                    if (targetUrl.startsWith("https://www.youtube.com/embed/") ||
                        targetUrl.startsWith("https://www.youtube-nocookie.com/embed/") ||
                        targetUrl.startsWith(appOrigin)
                    ) {
                        return false
                    }
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.contains("/embed/") == true) {
                        isPlayerReady = true
                        view?.evaluateJavascript(
                            """
                            (function() {
                              if (window.__yCloudEmbedHooked) return;
                              window.__yCloudEmbedHooked = true;
                              setInterval(function() {
                                if (!window.YCloudBridge) return;
                                var mp = document.getElementById('movie_player');
                                if (mp && mp.getCurrentTime && mp.getDuration) {
                                  YCloudBridge.onTimeUpdate(mp.getCurrentTime() || 0, mp.getDuration() || 0);
                                  if (mp.getPlayerState) {
                                    var st = mp.getPlayerState();
                                    if (st === 1 || st === 2 || st === 0) YCloudBridge.onPlayerState(st);
                                  }
                                } else {
                                  var v = document.querySelector('video');
                                  if (v) {
                                    YCloudBridge.onTimeUpdate(v.currentTime || 0, v.duration || 0);
                                    if (v.ended) YCloudBridge.onPlayerState(0);
                                    else YCloudBridge.onPlayerState(v.paused ? 2 : 1);
                                  }
                                }
                              }, 500);
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(webView) {
        controller.webView = webView
        val bridge = object {
            @JavascriptInterface
            fun onPlayerReady() {
                mainHandler.post {
                    isPlayerReady = true
                    embedErrorCode = null
                }
            }

            @JavascriptInterface
            fun onPlayerState(state: Int) {
                mainHandler.post {
                    isPlayerReady = true
                    when (state) {
                        1 -> latestOnPlaying(true)
                        2, 5 -> latestOnPlaying(false)
                        0 -> {
                            latestOnPlaying(false)
                            latestOnEnded()
                        }
                    }
                }
            }

            @JavascriptInterface
            fun onTimeUpdate(currentSec: Double, durationSec: Double) {
                mainHandler.post {
                    val posMs = (currentSec * 1000.0).toLong().coerceAtLeast(0L)
                    val durMs = (durationSec * 1000.0).toLong().coerceAtLeast(0L)
                    latestOnProgress(posMs, durMs)
                }
            }

            @JavascriptInterface
            fun onPlayerError(code: Int) {
                mainHandler.post {
                    if (code == 153 && !useDirectEmbedMode) {
                        // Retry with direct embed URL + Referer header
                        useDirectEmbedMode = true
                    } else {
                        embedErrorCode = code
                        latestOnPlaying(false)
                    }
                }
            }
        }
        webView.addJavascriptInterface(bridge, "YCloudBridge")

        onDispose {
            controller.webView = null
            webView.removeJavascriptInterface("YCloudBridge")
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    // Automatically fall back to direct embed URL if IFrame JS API doesn't become ready within 7s
    LaunchedEffect(videoId, useDirectEmbedMode, isPlayerReady, embedErrorCode) {
        if (!useDirectEmbedMode && !isPlayerReady && embedErrorCode == null) {
            delay(7000L)
            if (!isPlayerReady && embedErrorCode == null) {
                useDirectEmbedMode = true
            }
        }
    }

    LaunchedEffect(videoId, useDirectEmbedMode) {
        embedErrorCode = null
        val startSeconds = (startPositionMs / 1000L).coerceAtLeast(0L)
        val safeId = videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
        if (useDirectEmbedMode) {
            val embedUrl = "https://www.youtube.com/embed/$safeId?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=0&controls=1&enablejsapi=1&origin=$appOrigin&start=$startSeconds"
            webView.loadUrl(embedUrl, mapOf("Referer" to appReferer))
        } else {
            val html = buildYouTubeIframeHtml(safeId, startSeconds, appOrigin)
            // Use app package origin as baseUrl instead of youtube.com to avoid YouTube Error 153
            webView.loadDataWithBaseURL(
                appReferer,
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TheaterBlack)
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPlayerReady && embedErrorCode == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CherryRed)
            }
        }

        if (embedErrorCode != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TheaterBlack.copy(alpha = 0.92f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = CherryRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Bu YouTube videosu uygulama içi oynatılamıyor",
                        style = MaterialTheme.typography.titleLarge,
                        color = CrispWhite,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = when (embedErrorCode) {
                            101, 150 -> "Video sahibi başka uygulamalarda oynatmayı kısıtlamış olabilir."
                            153 -> "YouTube oynatıcı doğrulaması yapılamadı."
                            else -> "İnternet bağlantısı veya video izni nedeniyle oynatılamadı (Kod: $embedErrorCode)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrispWhite.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onBackRequested,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.tvFocusHighlight(RoundedCornerShape(18.dp), focusBorderColor = CrispWhite)
                        ) {
                            Text("Geri Dön", color = CrispWhite)
                        }
                        if (!useDirectEmbedMode) {
                            OutlinedButton(
                                onClick = {
                                    embedErrorCode = null
                                    useDirectEmbedMode = true
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.tvFocusHighlight(RoundedCornerShape(18.dp), focusBorderColor = SkyBlue)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = SkyBlue)
                                Text(" Tekrar Dene", color = SkyBlue)
                            }
                        }
                        Button(
                            onClick = onPlayNextRequested,
                            colors = ButtonDefaults.buttonColors(containerColor = CherryRed),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.tvFocusHighlight(RoundedCornerShape(18.dp), focusBorderColor = CrispWhite)
                        ) {
                            Text("Sonraki Video", color = CrispWhite)
                        }
                    }
                }
            }
        }
    }
}

private fun buildYouTubeIframeHtml(
    safeId: String,
    startSeconds: Long,
    appOrigin: String
): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="referrer" content="strict-origin-when-cross-origin">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            html, body {
              margin: 0;
              padding: 0;
              width: 100%;
              height: 100%;
              background: #000000;
              overflow: hidden;
            }
            #player, iframe {
              position: fixed;
              top: 0;
              left: 0;
              width: 100vw;
              height: 100vh;
              border: 0;
            }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script>
            var ytPlayer = null;
            var timeTimer = null;
            var pendingAction = null;

            function onYouTubeIframeAPIReady() {
              ytPlayer = new YT.Player('player', {
                width: '100%',
                height: '100%',
                videoId: '$safeId',
                playerVars: {
                  autoplay: 1,
                  playsinline: 1,
                  rel: 0,
                  modestbranding: 1,
                  fs: 0,
                  controls: 1,
                  iv_load_policy: 3,
                  origin: '$appOrigin',
                  start: $startSeconds
                },
                events: {
                  onReady: function(event) {
                    YCloudBridge.onPlayerReady();
                    if (pendingAction === 'pause') {
                      event.target.pauseVideo();
                    } else {
                      event.target.playVideo();
                    }
                    if (typeof pendingAction === 'number') {
                      event.target.seekTo(pendingAction, true);
                    }
                    pendingAction = null;
                    if (timeTimer) clearInterval(timeTimer);
                    timeTimer = setInterval(function() {
                      if (ytPlayer && ytPlayer.getCurrentTime && ytPlayer.getDuration) {
                        YCloudBridge.onTimeUpdate(ytPlayer.getCurrentTime() || 0, ytPlayer.getDuration() || 0);
                      }
                    }, 500);
                  },
                  onStateChange: function(event) {
                    YCloudBridge.onPlayerState(event.data);
                  },
                  onError: function(event) {
                    YCloudBridge.onPlayerError(event.data);
                  }
                }
              });
            }

            var tag = document.createElement('script');
            tag.src = "https://www.youtube.com/iframe_api";
            tag.onerror = function() {
              YCloudBridge.onPlayerError(153);
            };
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

            window.yCloudPlay = function() {
              if (ytPlayer && ytPlayer.playVideo) ytPlayer.playVideo();
              else pendingAction = 'play';
            };
            window.yCloudPause = function() {
              if (ytPlayer && ytPlayer.pauseVideo) ytPlayer.pauseVideo();
              else pendingAction = 'pause';
            };
            window.yCloudSeek = function(sec) {
              if (ytPlayer && ytPlayer.seekTo) ytPlayer.seekTo(sec, true);
              else pendingAction = sec;
            };
          </script>
        </body>
        </html>
    """.trimIndent()
}
