package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.example.data.local.VideoDao
import com.example.data.local.VideoEntity
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.CloudVideo
import com.example.util.VideoLinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class VideoRepository(
    private val context: Context,
    private val videoDao: VideoDao
) {
    private val supportedVideoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()

    fun getAppMoviesFolderPath(): String {
        val extMovies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "Movies")
        if (!extMovies.exists()) extMovies.mkdirs()
        return extMovies.absolutePath
    }

    suspend fun getVideoById(id: Long): VideoEntity? = withContext(Dispatchers.IO) {
        videoDao.getVideoById(id)
    }

    suspend fun getAllVideosOnce(): List<VideoEntity> = withContext(Dispatchers.IO) {
        videoDao.getAllVideosOnce()
    }

    suspend fun toggleFavorite(id: Long, currentFavorite: Boolean) = withContext(Dispatchers.IO) {
        videoDao.updateFavorite(id, !currentFavorite)
    }

    suspend fun updateVideoDetails(id: Long, title: String, category: VideoCategory) =
        withContext(Dispatchers.IO) {
            val cleanTitle = title.trim().ifEmpty { "İsimsiz Video" }.take(150)
            videoDao.updateDetails(id, cleanTitle, category.id)
        }

    suspend fun updatePlaybackPosition(id: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        videoDao.updatePlaybackPosition(id, positionMs.coerceAtLeast(0L))
    }

    suspend fun incrementPlayCount(id: Long) = withContext(Dispatchers.IO) {
        videoDao.incrementPlayCount(id)
    }

    suspend fun markSyncedFromCloud(id: Long, synced: Boolean) = withContext(Dispatchers.IO) {
        videoDao.markSyncedFromCloud(id, synced)
    }

    suspend fun clearCloudSyncedVideos() = withContext(Dispatchers.IO) {
        val current = videoDao.getAllVideosOnce()
        current.filter { it.syncedFromCloud }.forEach { video ->
            video.thumbnailPath?.let { runCatching { File(it).delete() } }
            video.localCachePath?.let { runCatching { File(it).delete() } }
        }
        videoDao.deleteAllCloudSyncedVideos()
    }

    suspend fun deleteVideo(video: VideoEntity) = withContext(Dispatchers.IO) {
        videoDao.deleteVideoById(video.id)
        video.thumbnailPath?.let { path ->
            runCatching { File(path).delete() }
        }
        video.localCachePath?.let { path ->
            runCatching { File(path).delete() }
        }
        if (video.uriString.startsWith("file://")) {
            val filePath = video.uriString.toUri().path
            if (filePath != null && filePath.contains("imported_videos")) {
                runCatching { File(filePath).delete() }
            }
        }
    }

    suspend fun addYouTubeVideo(
        rawUrl: String,
        customTitle: String?,
        category: VideoCategory
    ): Result<VideoEntity> = withContext(Dispatchers.IO) {
        try {
            val ytId = VideoLinkParser.extractYouTubeVideoId(rawUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Geçerli bir YouTube bağlantısı girin (youtube.com veya youtu.be).")
                )

            val canonicalUrl = VideoLinkParser.canonicalYouTubeUrl(ytId)
            val existing = videoDao.getVideoByUri(canonicalUrl)
            if (existing != null) {
                return@withContext Result.failure(
                    IllegalStateException("Bu YouTube videosu zaten kütüphanenizde mevcut.")
                )
            }

            val resolvedTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() }
                ?: fetchYouTubeOEmbedTitle(canonicalUrl)
                ?: "YouTube Video ($ytId)"
            val cleanTitle = resolvedTitle.take(150)
            val thumbUrl = VideoLinkParser.youtubeThumbnailUrl(ytId)
            val docId = CloudSyncRepository.stableDocIdForUrl(VideoSourceType.YOUTUBE.id, canonicalUrl)

            val entity = VideoEntity(
                uriString = canonicalUrl,
                title = cleanTitle,
                categoryId = category.id,
                sourceType = VideoSourceType.YOUTUBE.id,
                remoteThumbnailUrl = thumbUrl,
                cloudDocId = docId,
                addedAt = System.currentTimeMillis()
            )
            val insertedId = videoDao.insertVideo(entity)
            Result.success(entity.copy(id = insertedId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importYouTubePlaylist(
        rawUrlOrId: String,
        category: VideoCategory
    ): Result<List<VideoEntity>> = withContext(Dispatchers.IO) {
        try {
            val playlistId = VideoLinkParser.extractYouTubePlaylistId(rawUrlOrId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Geçerli bir YouTube oynatma listesi linki girin (youtube.com/playlist?list=...).")
                )

            val browserHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
                "Cookie" to "CONSENT=YES+cb.20210328-17-p0.en+FX+678; SOCS=CAI"
            )

            fun fetchUrlText(targetUrl: String): String? {
                val reqBuilder = Request.Builder().url(targetUrl).get()
                browserHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                return runCatching {
                    httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }
                }.getOrNull()
            }

            var parsedEntries = fetchUrlText("https://www.youtube.com/playlist?list=$playlistId")
                ?.let { VideoLinkParser.extractYouTubePlaylistItemsFromHtml(it) }
                .orEmpty()

            // Fallback A: For watch?v=...&list=RD... (Mix) URLs, fetch the watch page panel
            if (parsedEntries.isEmpty()) {
                val singleVidId = VideoLinkParser.extractYouTubeVideoId(rawUrlOrId)
                if (singleVidId != null) {
                    val watchUrl = "https://www.youtube.com/watch?v=$singleVidId&list=$playlistId"
                    parsedEntries = fetchUrlText(watchUrl)
                        ?.let { VideoLinkParser.extractYouTubePlaylistItemsFromHtml(it) }
                        .orEmpty()
                }
            }

            // Fallback B: Official YouTube RSS Atom feed for public playlists
            if (parsedEntries.isEmpty()) {
                parsedEntries = fetchUrlText("https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId")
                    ?.let { VideoLinkParser.extractYouTubePlaylistItemsFromRss(it) }
                    .orEmpty()
            }

            if (parsedEntries.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("YouTube listesinden video alınamadı. Listenin 'Herkese Açık' veya 'Liste Dışı' olduğundan emin olun.")
                )
            }

            val addedEntities = mutableListOf<VideoEntity>()
            val baseTime = System.currentTimeMillis()
            val cappedEntries = parsedEntries.take(100)
            cappedEntries.forEachIndexed { idx, entry ->
                val canonicalUrl = VideoLinkParser.canonicalYouTubeUrl(entry.videoId)
                val existing = videoDao.getVideoByUri(canonicalUrl)
                if (existing == null) {
                    val docId = CloudSyncRepository.stableDocIdForUrl(
                        VideoSourceType.YOUTUBE.id,
                        canonicalUrl
                    )
                    val entity = VideoEntity(
                        uriString = canonicalUrl,
                        title = entry.title,
                        categoryId = category.id,
                        sourceType = VideoSourceType.YOUTUBE.id,
                        remoteThumbnailUrl = entry.thumbnailUrl,
                        cloudDocId = docId,
                        addedAt = baseTime + (cappedEntries.size - idx)
                    )
                    val rowId = videoDao.insertVideo(entity)
                    addedEntities.add(entity.copy(id = rowId))
                }
            }
            Result.success(addedEntities)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addCloudVideoUrl(
        rawUrl: String,
        customTitle: String?,
        category: VideoCategory,
        downloadOffline: Boolean
    ): Result<VideoEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = VideoLinkParser.normalizeCloudVideoUrl(rawUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Geçerli bir https:// video bağlantısı girin.")
                )

            val existing = videoDao.getVideoByUri(normalizedUrl)
            if (existing != null) {
                return@withContext Result.failure(
                    IllegalStateException("Bu bulut video bağlantısı zaten kütüphanenizde mevcut.")
                )
            }

            val cleanTitle = (customTitle?.trim()?.takeIf { it.isNotEmpty() }
                ?: VideoLinkParser.inferTitleFromUrl(normalizedUrl)).take(150)
            val docId = CloudSyncRepository.stableDocIdForUrl(VideoSourceType.CLOUD_URL.id, normalizedUrl)

            var localCachePath: String? = null
            var durationMs = 0L
            var thumbnailPath: String? = null

            if (downloadOffline) {
                val downloadedFile = downloadRemoteVideoFile(normalizedUrl)
                if (downloadedFile != null) {
                    localCachePath = downloadedFile.absolutePath
                    val (dur, thumb) = extractMetadataAndThumbnail(Uri.fromFile(downloadedFile))
                    durationMs = dur
                    thumbnailPath = thumb
                } else {
                    return@withContext Result.failure(
                        IllegalStateException("Video indirilemedi. Bağlantının doğrudan MP4/video dosyası olduğundan emin olun.")
                    )
                }
            }

            val entity = VideoEntity(
                uriString = normalizedUrl,
                title = cleanTitle,
                categoryId = category.id,
                durationMs = durationMs,
                thumbnailPath = thumbnailPath,
                sourceType = VideoSourceType.CLOUD_URL.id,
                cloudDocId = docId,
                localCachePath = localCachePath,
                addedAt = System.currentTimeMillis()
            )
            val insertedId = videoDao.insertVideo(entity)
            Result.success(entity.copy(id = insertedId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadCloudVideoOffline(video: VideoEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (video.source != VideoSourceType.CLOUD_URL) {
                return@withContext Result.failure(
                    IllegalArgumentException("Yalnızca doğrudan bulut video linkleri çevrimdışı indirilebilir.")
                )
            }
            if (video.isDownloadedOffline) {
                return@withContext Result.success(Unit)
            }
            val downloadedFile = downloadRemoteVideoFile(video.uriString)
                ?: return@withContext Result.failure(
                    IllegalStateException("Video indirilemedi. Bağlantı doğrudan indirilebilir bir dosya olmayabilir.")
                )
            val (dur, thumb) = extractMetadataAndThumbnail(Uri.fromFile(downloadedFile))
            videoDao.updateOfflineCache(
                id = video.id,
                localCachePath = downloadedFile.absolutePath,
                durationMs = if (dur > 0L) dur else video.durationMs,
                thumbnailPath = thumb ?: video.thumbnailPath
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importJsonPlaylist(
        rawJsonUrl: String,
        fallbackCategory: VideoCategory
    ): Result<List<VideoEntity>> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = VideoLinkParser.normalizeCloudVideoUrl(rawJsonUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Geçerli bir https:// JSON liste adresi girin.")
                )

            val request = Request.Builder().url(normalizedUrl).get().build()
            val bodyText = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Liste indirilemedi (HTTP ${response.code}).")
                    )
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.size > 2 * 1024 * 1024) {
                    return@withContext Result.failure(
                        IllegalStateException("JSON liste dosyası çok büyük.")
                    )
                }
                bytes.toString(Charsets.UTF_8)
            }

            val parsedItems = VideoLinkParser.parsePlaylistJson(bodyText, fallbackCategory)
            if (parsedItems.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("JSON içinde geçerli YouTube veya bulut video linki bulunamadı.")
                )
            }

            val addedEntities = mutableListOf<VideoEntity>()
            val baseTime = System.currentTimeMillis()
            parsedItems.forEachIndexed { idx, item ->
                val existing = videoDao.getVideoByUri(item.canonicalUrl)
                if (existing == null) {
                    val docId = CloudSyncRepository.stableDocIdForUrl(
                        item.sourceType.id,
                        item.canonicalUrl
                    )
                    val entity = VideoEntity(
                        uriString = item.canonicalUrl,
                        title = item.title,
                        categoryId = item.category.id,
                        sourceType = item.sourceType.id,
                        remoteThumbnailUrl = item.thumbnailUrl.ifBlank { null },
                        cloudDocId = docId,
                        addedAt = baseTime + idx
                    )
                    val rowId = videoDao.insertVideo(entity)
                    addedEntities.add(entity.copy(id = rowId))
                }
            }
            Result.success(addedEntities)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reconcileCloudVideos(cloudVideos: List<CloudVideo>) = withContext(Dispatchers.IO) {
        val cloudById = cloudVideos.filter { it.id.isNotBlank() }.associateBy { it.id }

        // Upsert remote items into local Room library
        cloudVideos.forEach { remote ->
            if (remote.videoUrl.isBlank()) return@forEach
            val existing = (if (remote.id.isNotBlank()) videoDao.getVideoByCloudDocId(remote.id) else null)
                ?: videoDao.getVideoByUri(remote.videoUrl)

            val safeCategory = remote.safeCategory().id
            val safeTitle = remote.title.trim().ifEmpty { "Bulut Videosu" }.take(150)
            val safeThumb = remote.thumbnailUrl.trim().ifBlank { null }
            val safeSource = remote.safeSourceType().id

            if (existing == null) {
                videoDao.insertVideo(
                    VideoEntity(
                        uriString = remote.videoUrl,
                        title = safeTitle,
                        categoryId = safeCategory,
                        durationMs = remote.durationMs.coerceAtLeast(0L),
                        isFavorite = remote.favorite,
                        sourceType = safeSource,
                        remoteThumbnailUrl = safeThumb,
                        cloudDocId = remote.id,
                        syncedFromCloud = true,
                        addedAt = remote.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                )
            } else {
                val updated = existing.copy(
                    title = safeTitle,
                    categoryId = safeCategory,
                    isFavorite = remote.favorite,
                    sourceType = safeSource,
                    remoteThumbnailUrl = safeThumb ?: existing.remoteThumbnailUrl,
                    cloudDocId = remote.id,
                    syncedFromCloud = true,
                    durationMs = if (existing.durationMs > 0L) existing.durationMs else remote.durationMs
                )
                if (updated != existing) {
                    videoDao.updateVideo(updated)
                }
            }
        }

        // Remove items that were previously synced from cloud and deleted on another device
        val localSnapshot = videoDao.getAllVideosOnce()
        localSnapshot.forEach { local ->
            val docId = local.cloudDocId
            if (local.syncedFromCloud && docId != null && !cloudById.containsKey(docId)) {
                deleteVideo(local)
            }
        }
    }

    private fun fetchYouTubeOEmbedTitle(canonicalUrl: String): String? {
        return runCatching {
            val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val obj = jsonParser.parseToJsonElement(body).jsonObject
                obj["title"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun downloadRemoteVideoFile(url: String): File? {
        val dir = File(context.filesDir, "downloaded_videos").apply { mkdirs() }
        val destFile = File(dir, "cloud_${UUID.randomUUID()}.mp4")
        return try {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    destFile.delete()
                    return null
                }
                val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                if (contentType.startsWith("text/html")) {
                    destFile.delete()
                    return null
                }
                val body = resp.body ?: run {
                    destFile.delete()
                    return null
                }
                val maxBytes = 350L * 1024L * 1024L
                var totalBytes = 0L
                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            totalBytes += read
                            if (totalBytes > maxBytes) {
                                destFile.delete()
                                return null
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (destFile.length() < 1024L) {
                    destFile.delete()
                    return null
                }
                destFile
            }
        } catch (_: Exception) {
            destFile.delete()
            null
        }
    }

    suspend fun importVideos(
        uris: List<Uri>,
        defaultCategory: VideoCategory? = null
    ): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        for (uri in uris) {
            val resolvedUri = persistOrCopyVideoUri(uri) ?: continue
            val resolvedUriStr = resolvedUri.toString()

            val existing = videoDao.getVideoByUri(resolvedUriStr)
            if (existing != null) continue

            val rawName = queryDisplayName(uri) ?: "Video ${System.currentTimeMillis() % 1000}"
            val cleanTitle = cleanTitleFromFileName(rawName)
            val category = defaultCategory ?: inferCategoryFromTitle(cleanTitle)
            val (durationMs, thumbnailPath) = extractMetadataAndThumbnail(resolvedUri)

            videoDao.insertVideo(
                VideoEntity(
                    uriString = resolvedUriStr,
                    title = cleanTitle,
                    categoryId = category.id,
                    durationMs = durationMs,
                    thumbnailPath = thumbnailPath,
                    sourceType = VideoSourceType.LOCAL.id,
                    addedAt = System.currentTimeMillis() + importedCount
                )
            )
            importedCount++
        }
        importedCount
    }

    suspend fun importFromFolderTree(
        treeUri: Uri,
        defaultCategory: VideoCategory? = null
    ): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Best-effort persistable tree permission
        }

        val collectedUris = mutableListOf<Uri>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        collectVideosFromTree(treeUri, rootDocId, collectedUris, depth = 0)
        importVideos(collectedUris, defaultCategory)
    }

    suspend fun scanAppMoviesFolders(
        defaultCategory: VideoCategory? = null
    ): Int = withContext(Dispatchers.IO) {
        val candidateDirs = buildList {
            context.getExternalFilesDirs(Environment.DIRECTORY_MOVIES)?.filterNotNull()?.forEach {
                if (!it.exists()) it.mkdirs()
                add(it)
            }
            add(File(context.filesDir, "Movies").apply { mkdirs() })
        }

        val discoveredUris = mutableListOf<Uri>()
        candidateDirs.forEach { dir ->
            dir.walkTopDown()
                .maxDepth(3)
                .filter { it.isFile && it.extension.lowercase() in supportedVideoExtensions }
                .forEach { file ->
                    discoveredUris.add(Uri.fromFile(file))
                }
        }
        importVideos(discoveredUris, defaultCategory)
    }

    private fun collectVideosFromTree(
        treeUri: Uri,
        parentDocId: String,
        output: MutableList<Uri>,
        depth: Int
    ) {
        if (depth > 3 || output.size >= 150) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        collectVideosFromTree(treeUri, docId, output, depth + 1)
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (mime.startsWith("video/") || ext in supportedVideoExtensions) {
                            output.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                        }
                    }
                }
            }
        }
    }

    private fun persistOrCopyVideoUri(uri: Uri): Uri? {
        if (uri.scheme == "file") return uri

        val persisted = try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        if (persisted) return uri

        return try {
            val dir = File(context.filesDir, "imported_videos").apply { mkdirs() }
            val fileName = "vid_${UUID.randomUUID()}.mp4"
            val destFile = File(dir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return null
            Uri.fromFile(destFile)
        } catch (_: Exception) {
            uri
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun cleanTitleFromFileName(rawName: String): String {
        val withoutExt = rawName.substringBeforeLast('.')
        return withoutExt
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { "Çocuk Videosu" }
    }

    private fun inferCategoryFromTitle(title: String): VideoCategory {
        val lower = title.lowercase()
        return when {
            lower.contains("şarkı") || lower.contains("sarki") ||
                lower.contains("müzik") || lower.contains("muzik") ||
                lower.contains("ninni") || lower.contains("song") ||
                lower.contains("music") || lower.contains("dans") -> VideoCategory.MUSIC

            lower.contains("sayı") || lower.contains("sayi") ||
                lower.contains("harf") || lower.contains("renk") ||
                lower.contains("alfabe") || lower.contains("eğitim") ||
                lower.contains("egitim") || lower.contains("hayvan") ||
                lower.contains("abc") || lower.contains("learn") -> VideoCategory.EDUCATIONAL

            lower.contains("oyun") || lower.contains("game") ||
                lower.contains("lego") || lower.contains("puzzle") -> VideoCategory.GAMES

            else -> VideoCategory.CARTOON
        }
    }

    private fun extractMetadataAndThumbnail(uri: Uri): Pair<Long, String?> {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val targetTimeUs = if (durationMs > 2000L) {
                (durationMs * 1000L) / 6L
            } else {
                500_000L
            }

            val rawBitmap = retriever.getFrameAtTime(
                targetTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(0L)

            val savedPath = rawBitmap?.let { bmp ->
                saveScaledThumbnail(bmp).also {
                    bmp.recycle()
                }
            }
            durationMs to savedPath
        } catch (_: Exception) {
            0L to null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun saveScaledThumbnail(bitmap: Bitmap): String? {
        return try {
            val maxWidth = 640
            val scaled = if (bitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / bitmap.width.toFloat()
                val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)
            } else {
                bitmap
            }
            val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val thumbFile = File(thumbDir, "thumb_${UUID.randomUUID()}.jpg")
            FileOutputStream(thumbFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (scaled !== bitmap) {
                scaled.recycle()
            }
            thumbFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
