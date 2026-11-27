package com.example.util

import android.net.Uri
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

@Serializable
data class RemotePlaylistEntry(
    val title: String = "",
    val url: String = "",
    val category: String = "cartoon",
    val thumbnail: String = ""
)

data class ParsedPlaylistItem(
    val title: String,
    val sourceType: VideoSourceType,
    val canonicalUrl: String,
    val thumbnailUrl: String,
    val category: VideoCategory
)

data class ParsedYouTubePlaylistEntry(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String
)

object VideoLinkParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val youtubeIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
    private val youtubePlaylistIdRegex = Regex("^(PL|UU|OLAK5uy_|LL|FL|RD|EL)[A-Za-z0-9_-]{8,}$")
    private val driveFileRegex = Regex("/file/d/([A-Za-z0-9_-]+)")

    fun extractYouTubeVideoId(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        if (youtubeIdRegex.matches(trimmed)) return trimmed

        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null

        val candidate = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()
            host == "youtube.com" || host == "youtube-nocookie.com" || host == "music.youtube.com" -> {
                val segments = uri.pathSegments
                when {
                    segments.isEmpty() -> uri.getQueryParameter("v")
                    segments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
                    segments.firstOrNull() in setOf("embed", "shorts", "live", "v") ->
                        segments.getOrNull(1)
                    else -> uri.getQueryParameter("v")
                }
            }
            else -> null
        }

        return candidate?.takeIf { youtubeIdRegex.matches(it) }
    }

    fun extractYouTubePlaylistId(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        if (youtubePlaylistIdRegex.matches(trimmed)) return trimmed

        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        if (host != "youtube.com" && host != "youtube-nocookie.com" &&
            host != "music.youtube.com" && host != "youtu.be"
        ) {
            return null
        }

        val listParam = uri.getQueryParameter("list")?.trim() ?: return null
        return listParam.takeIf { it.length >= 10 && Regex("^[A-Za-z0-9_-]+$").matches(it) }
    }

    fun extractYouTubePlaylistItemsFromHtml(html: String): List<ParsedYouTubePlaylistEntry> {
        val initialDataJson = extractYtInitialDataJson(html) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(initialDataJson) }.getOrNull() ?: return emptyList()

        val collected = mutableListOf<ParsedYouTubePlaylistEntry>()
        val seenIds = mutableSetOf<String>()

        fun extractTitle(renderer: JsonObject): String? {
            val titleObj = renderer["title"]?.jsonObject ?: return null
            titleObj["simpleText"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return it
            }
            val runs = titleObj["runs"] as? JsonArray ?: return null
            val joined = runs.mapNotNull {
                (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.joinToString("").trim()
            return joined.takeIf { it.isNotEmpty() }
        }

        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val renderer = (element["playlistVideoRenderer"] as? JsonObject)
                        ?: (element["playlistPanelVideoRenderer"] as? JsonObject)
                    if (renderer != null) {
                        val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull?.trim()
                        val rawTitle = extractTitle(renderer)
                        if (videoId != null && youtubeIdRegex.matches(videoId) && seenIds.add(videoId)) {
                            val isUnavailable = rawTitle == "[Deleted video]" ||
                                rawTitle == "[Private video]" ||
                                rawTitle == "[Silinmiş video]" ||
                                rawTitle == "[Gizli video]"
                            if (!isUnavailable) {
                                collected.add(
                                    ParsedYouTubePlaylistEntry(
                                        videoId = videoId,
                                        title = (rawTitle ?: "YouTube Video ($videoId)").take(150),
                                        thumbnailUrl = youtubeThumbnailUrl(videoId)
                                    )
                                )
                            }
                        }
                    }
                    element.values.forEach { walk(it) }
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }

        walk(root)
        return collected
    }

    fun extractYouTubePlaylistItemsFromRss(xml: String): List<ParsedYouTubePlaylistEntry> {
        val collected = mutableListOf<ParsedYouTubePlaylistEntry>()
        val seenIds = mutableSetOf<String>()
        runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var inEntry = false
            var currentVideoId: String? = null
            var currentTitle: String? = null
            var currentTag: String? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "entry") {
                            inEntry = true
                            currentVideoId = null
                            currentTitle = null
                        } else if (inEntry) {
                            currentTag = name
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inEntry) {
                            val text = parser.text?.trim().orEmpty()
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "videoId" -> currentVideoId = (currentVideoId ?: "") + text
                                    "title" -> currentTitle = (currentTitle ?: "") + text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name == "entry" && inEntry) {
                            val vid = currentVideoId?.trim()
                            if (vid != null && youtubeIdRegex.matches(vid) && seenIds.add(vid)) {
                                val title = currentTitle?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: "YouTube Video ($vid)"
                                collected.add(
                                    ParsedYouTubePlaylistEntry(
                                        videoId = vid,
                                        title = title.take(150),
                                        thumbnailUrl = youtubeThumbnailUrl(vid)
                                    )
                                )
                            }
                            inEntry = false
                        }
                        if (currentTag == name) {
                            currentTag = null
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return collected
    }

    private fun extractYtInitialDataJson(html: String): String? {
        val markers = listOf(
            "var ytInitialData =",
            "window[\"ytInitialData\"] =",
            "ytInitialData ="
        )
        var startBrace = -1
        for (marker in markers) {
            val idx = html.indexOf(marker)
            if (idx >= 0) {
                val braceIdx = html.indexOf('{', idx + marker.length)
                if (braceIdx >= 0) {
                    startBrace = braceIdx
                    break
                }
            }
        }
        if (startBrace < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in startBrace until html.length) {
            val ch = html[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return html.substring(startBrace, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    fun canonicalYouTubeUrl(videoId: String): String =
        "https://www.youtube.com/watch?v=$videoId"

    fun youtubeThumbnailUrl(videoId: String): String =
        "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    fun normalizeCloudVideoUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            !trimmed.contains("://") -> "https://$trimmed"
            else -> return null
        }

        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host.isBlank()) return null

        // Google Drive share link conversion -> direct download/stream endpoint
        if (host.contains("drive.google.com")) {
            val pathMatch = driveFileRegex.find(uri.path ?: "")
            val fileId = pathMatch?.groupValues?.getOrNull(1) ?: uri.getQueryParameter("id")
            if (!fileId.isNullOrBlank()) {
                return "https://drive.google.com/uc?export=download&id=$fileId"
            }
        }

        // Dropbox share link conversion -> dl=1
        if (host.contains("dropbox.com")) {
            return withScheme
                .replace("?dl=0", "?dl=1")
                .replace("&dl=0", "&dl=1")
                .let { updated ->
                    if (updated.contains("dl=1")) updated
                    else if (updated.contains("?")) "$updated&dl=1"
                    else "$updated?dl=1"
                }
        }

        return withScheme
    }

    fun inferTitleFromUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val last = uri?.lastPathSegment
            ?.substringBefore('?')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.trim()
        return if (!last.isNullOrBlank() && last != "uc" && last != "watch") {
            last.take(120)
        } else {
            "Bulut Videosu"
        }
    }

    fun parsePlaylistJson(rawJson: String, fallbackCategory: VideoCategory): List<ParsedPlaylistItem> {
        val root: JsonElement = json.parseToJsonElement(rawJson.trim())
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                (root["videos"] as? JsonArray)
                    ?: (root["items"] as? JsonArray)
                    ?: (root["playlist"] as? JsonArray)
                    ?: JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }

        val parsed = mutableListOf<ParsedPlaylistItem>()
        for (element in array) {
            val entry = runCatching {
                json.decodeFromJsonElement<RemotePlaylistEntry>(element)
            }.getOrNull() ?: continue

            val rawUrl = entry.url.trim()
            if (rawUrl.isEmpty()) continue

            val category = VideoCategory.assignableCategories.firstOrNull {
                it.id.equals(entry.category.trim(), ignoreCase = true) ||
                    it.labelTr.equals(entry.category.trim(), ignoreCase = true)
            } ?: fallbackCategory

            val ytId = extractYouTubeVideoId(rawUrl)
            if (ytId != null) {
                val title = entry.title.trim().ifEmpty { "YouTube Video ($ytId)" }.take(150)
                val thumb = entry.thumbnail.trim().ifEmpty { youtubeThumbnailUrl(ytId) }.take(1000)
                parsed.add(
                    ParsedPlaylistItem(
                        title = title,
                        sourceType = VideoSourceType.YOUTUBE,
                        canonicalUrl = canonicalYouTubeUrl(ytId),
                        thumbnailUrl = thumb,
                        category = category
                    )
                )
            } else {
                val cloudUrl = normalizeCloudVideoUrl(rawUrl) ?: continue
                val title = entry.title.trim().ifEmpty { inferTitleFromUrl(cloudUrl) }.take(150)
                val thumb = entry.thumbnail.trim().take(1000)
                parsed.add(
                    ParsedPlaylistItem(
                        title = title,
                        sourceType = VideoSourceType.CLOUD_URL,
                        canonicalUrl = cloudUrl.take(1000),
                        thumbnailUrl = thumb,
                        category = category
                    )
                )
            }
        }
        return parsed
    }
}
