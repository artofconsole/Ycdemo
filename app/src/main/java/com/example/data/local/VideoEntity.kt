package com.example.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import java.io.File

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uriString: String,
    val title: String,
    val categoryId: String = VideoCategory.CARTOON.id,
    val durationMs: Long = 0L,
    val thumbnailPath: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPositionMs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    val sourceType: String = VideoSourceType.LOCAL.id,
    val remoteThumbnailUrl: String? = null,
    val cloudDocId: String? = null,
    val localCachePath: String? = null,
    val syncedFromCloud: Boolean = false
) {
    val category: VideoCategory
        get() = VideoCategory.fromId(categoryId)

    val source: VideoSourceType
        get() = VideoSourceType.fromId(sourceType)

    val isDownloadedOffline: Boolean
        get() = !localCachePath.isNullOrBlank() && File(localCachePath).exists()

    fun effectivePlaybackUri(): String {
        val cached = localCachePath?.let { File(it) }?.takeIf { it.exists() }
        return if (cached != null) Uri.fromFile(cached).toString() else uriString
    }

    fun formattedDuration(): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
