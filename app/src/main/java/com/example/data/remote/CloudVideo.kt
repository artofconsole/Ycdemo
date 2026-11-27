package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudVideo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sourceType: String = VideoSourceType.CLOUD_URL.id,
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val categoryId: String = VideoCategory.CARTOON.id,
    val favorite: Boolean = false,
    val durationMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun safeSourceType(): VideoSourceType = when (sourceType) {
        VideoSourceType.YOUTUBE.id -> VideoSourceType.YOUTUBE
        else -> VideoSourceType.CLOUD_URL
    }

    fun safeCategory(): VideoCategory {
        val candidate = VideoCategory.fromId(categoryId)
        return if (candidate in VideoCategory.assignableCategories) {
            candidate
        } else {
            VideoCategory.CARTOON
        }
    }
}
