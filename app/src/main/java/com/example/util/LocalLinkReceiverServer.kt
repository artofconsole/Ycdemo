package com.example.util

import com.example.data.model.VideoCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

data class RemoteIncomingLink(
    val mode: String, // "youtube", "cloud", "json"
    val url: String,
    val title: String?,
    val category: VideoCategory,
    val downloadOffline: Boolean
)

@Serializable
private data class IncomingLinkPayload(
    val mode: String = "youtube",
    val url: String = "",
    val title: String = "",
    val category: String = "cartoon",
    val downloadOffline: Boolean = false
)

@Serializable
private data class ApiResponsePayload(
    val ok: Boolean,
    val message: String
)

class LocalLinkReceiverServer(
    private val scope: CoroutineScope,
    private val onReceiveLink: suspend (RemoteIncomingLink) -> Result<String>
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun start(preferredPort: Int = 8765): String? {
        stop()
        val socket = runCatching { ServerSocket(preferredPort) }
            .recoverCatching { ServerSocket(0) }
            .getOrNull() ?: return null

        serverSocket = socket
        val ip = findLocalIpv4Address() ?: "127.0.0.1"
        val boundPort = socket.localPort

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch(Dispatchers.IO) {
                    handleClient(client)
                }
            }
        }

        return "http://$ip:$boundPort"
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)?.uppercase() ?: return
            val path = parts.getOrNull(1) ?: "/"

            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }

            val output = client.getOutputStream()
            if (method == "GET" && (path == "/" || path.startsWith("/?"))) {
                writeResponse(output, 200, "text/html; charset=utf-8", webPageHtml)
            } else if (method == "POST" && path == "/add") {
                val bodyChars = CharArray(contentLength.coerceIn(0, 64 * 1024))
                var readTotal = 0
                while (readTotal < bodyChars.size) {
                    val count = reader.read(bodyChars, readTotal, bodyChars.size - readTotal)
                    if (count == -1) break
                    readTotal += count
                }
                val bodyText = String(bodyChars, 0, readTotal)
                val parsed = runCatching {
                    json.decodeFromString<IncomingLinkPayload>(bodyText)
                }.getOrNull()

                if (parsed == null || parsed.url.isBlank()) {
                    val errJson = json.encodeToString(
                        ApiResponsePayload(false, "Lütfen geçerli bir video bağlantısı girin.")
                    )
                    writeResponse(output, 400, "application/json; charset=utf-8", errJson)
                    return
                }

                val category = VideoCategory.assignableCategories.firstOrNull {
                    it.id.equals(parsed.category, ignoreCase = true)
                } ?: VideoCategory.CARTOON

                val result = onReceiveLink(
                    RemoteIncomingLink(
                        mode = parsed.mode.lowercase(),
                        url = parsed.url.trim(),
                        title = parsed.title.trim().takeIf { it.isNotEmpty() },
                        category = category,
                        downloadOffline = parsed.downloadOffline
                    )
                )

                val respPayload = result.fold(
                    onSuccess = { msg -> ApiResponsePayload(true, msg) },
                    onFailure = { err ->
                        ApiResponsePayload(false, err.localizedMessage ?: "Bağlantı eklenemedi.")
                    }
                )
                writeResponse(
                    output,
                    if (respPayload.ok) 200 else 400,
                    "application/json; charset=utf-8",
                    json.encodeToString(respPayload)
                )
            } else {
                writeResponse(output, 404, "text/plain; charset=utf-8", "Not Found")
            }
        }
    }

    private fun writeResponse(
        output: OutputStream,
        statusCode: Int,
        contentType: String,
        body: String
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            else -> "Not Found"
        }
        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun findLocalIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallback: String? = null
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.")) {
                            return host
                        }
                        if (fallback == null) fallback = host
                    }
                }
            }
            fallback
        }.getOrNull()
    }

    private val webPageHtml: String = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>yCloud • TV'ye Video Gönder</title>
          <style>
            * { box-sizing: border-box; font-family: system-ui, -apple-system, sans-serif; }
            body { margin: 0; padding: 20px; background: #FFFBF4; color: #1E1B2E; }
            .card { max-width: 480px; margin: 0 auto; background: #FFFFFF; border-radius: 24px; padding: 22px; box-shadow: 0 8px 28px rgba(0,0,0,0.08); }
            .brand { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
            .badge { background: #F91B42; color: #fff; font-weight: 800; padding: 8px 14px; border-radius: 14px; font-size: 18px; }
            h1 { margin: 0; font-size: 20px; }
            p.sub { margin: 4px 0 18px; color: #67637A; font-size: 14px; }
            label { display: block; font-size: 13px; font-weight: 700; margin: 12px 0 6px; }
            input, select { width: 100%; padding: 13px 14px; border-radius: 14px; border: 1.5px solid #E2DCEB; font-size: 15px; background: #FCFAFF; }
            .tabs { display: flex; gap: 8px; margin-bottom: 8px; }
            .tab { flex: 1; padding: 10px 8px; border-radius: 14px; border: 1.5px solid #E2DCEB; background: #fff; font-weight: 700; font-size: 13px; cursor: pointer; text-align: center; }
            .tab.active { background: #F91B42; color: #fff; border-color: #F91B42; }
            .check-row { display: none; align-items: center; gap: 10px; margin-top: 12px; font-size: 14px; }
            .check-row input { width: 18px; height: 18px; }
            button.submit { width: 100%; margin-top: 18px; padding: 15px; border: none; border-radius: 16px; background: #F91B42; color: #fff; font-size: 16px; font-weight: 800; cursor: pointer; }
            button.submit:disabled { opacity: 0.6; }
            .msg { margin-top: 14px; padding: 12px 14px; border-radius: 14px; font-size: 14px; font-weight: 600; display: none; }
            .msg.ok { display: block; background: #E4F8EE; color: #127C47; }
            .msg.err { display: block; background: #FFE6EA; color: #C21031; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="brand">
              <div class="badge">yCloud</div>
              <h1>TV'ye Video Gönder</h1>
            </div>
            <p class="sub">Telefonunuzdan YouTube, Bulut MP4/Drive veya JSON liste linkini yapıştırıp doğrudan TV'ye aktarın.</p>
            <div class="tabs">
              <button type="button" class="tab active" onclick="setMode('youtube', this)">YouTube</button>
              <button type="button" class="tab" onclick="setMode('youtube_playlist', this)">YT Liste</button>
              <button type="button" class="tab" onclick="setMode('cloud', this)">Bulut</button>
              <button type="button" class="tab" onclick="setMode('json', this)">JSON</button>
            </div>
            <label for="url">Video veya Liste Linki</label>
            <input id="url" type="url" placeholder="https://youtu.be/... veya https://.../video.mp4" />
            <div id="titleRow">
              <label for="title">Başlık (İsteğe bağlı)</label>
              <input id="title" type="text" placeholder="Boş bırakırsanız otomatik alınır" />
            </div>
            <label for="category">Kategori</label>
            <select id="category">
              <option value="cartoon">Çizgi Film</option>
              <option value="music">Müzik & Şarkı</option>
              <option value="educational">Eğitici</option>
              <option value="games">Oyun</option>
            </select>
            <label class="check-row" id="offlineRow">
              <input id="downloadOffline" type="checkbox" />
              <span>TV cihazına indir (İnternetsiz izle)</span>
            </label>
            <button type="button" class="submit" id="sendBtn" onclick="sendLink()">TV'ye Gönder</button>
            <div id="msg" class="msg"></div>
          </div>
          <script>
            var currentMode = 'youtube';
            function setMode(mode, el) {
              currentMode = mode;
              document.querySelectorAll('.tab').forEach(function(t){ t.classList.remove('active'); });
              el.classList.add('active');
              document.getElementById('offlineRow').style.display = mode === 'cloud' ? 'flex' : 'none';
              document.getElementById('titleRow').style.display = (mode === 'youtube' || mode === 'cloud') ? 'block' : 'none';
              var urlInput = document.getElementById('url');
              if (mode === 'youtube') urlInput.placeholder = 'https://youtu.be/... veya watch?v=...';
              else if (mode === 'youtube_playlist') urlInput.placeholder = 'https://www.youtube.com/playlist?list=PL...';
              else if (mode === 'cloud') urlInput.placeholder = 'https://.../video.mp4 veya Google Drive linki';
              else urlInput.placeholder = 'https://.../playlist.json';
            }
            async function sendLink() {
              var btn = document.getElementById('sendBtn');
              var msgEl = document.getElementById('msg');
              var url = document.getElementById('url').value.trim();
              if (!url) {
                msgEl.className = 'msg err';
                msgEl.textContent = 'Lütfen bir link yapıştırın.';
                return;
              }
              btn.disabled = true;
              btn.textContent = 'Gönderiliyor...';
              msgEl.className = 'msg';
              try {
                var res = await fetch('/add', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({
                    mode: currentMode,
                    url: url,
                    title: document.getElementById('title').value.trim(),
                    category: document.getElementById('category').value,
                    downloadOffline: document.getElementById('downloadOffline').checked
                  })
                });
                var data = await res.json();
                if (data.ok) {
                  msgEl.className = 'msg ok';
                  msgEl.textContent = data.message;
                  document.getElementById('url').value = '';
                  document.getElementById('title').value = '';
                } else {
                  msgEl.className = 'msg err';
                  msgEl.textContent = data.message || 'Eklenemedi.';
                }
              } catch (e) {
                msgEl.className = 'msg err';
                msgEl.textContent = 'TV ile bağlantı kurulamadı. Aynı Wi-Fi ağında olduğunuzdan emin olun.';
              }
              btn.disabled = false;
              btn.textContent = "TV'ye Gönder";
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
