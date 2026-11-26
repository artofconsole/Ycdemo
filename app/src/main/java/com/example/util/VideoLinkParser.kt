package com.example.util

import android.net.Uri
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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

object VideoLinkParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val youtubeIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
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
