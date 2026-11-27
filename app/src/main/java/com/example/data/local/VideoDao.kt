package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    suspend fun getAllVideosOnce(): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE uriString = :uriString LIMIT 1")
    suspend fun getVideoByUri(uriString: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE cloudDocId = :cloudDocId LIMIT 1")
    suspend fun getVideoByCloudDocId(cloudDocId: String): VideoEntity?

    @Query("SELECT uriString FROM videos")
    suspend fun getAllUriStrings(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>): List<Long>

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET durationMs = :durationMs, thumbnailPath = :thumbnailPath WHERE id = :id")
    suspend fun updateMetadata(id: Long, durationMs: Long, thumbnailPath: String?)

    @Query("UPDATE videos SET uriString = :uriString WHERE id = :id")
    suspend fun updateUriString(id: Long, uriString: String)

    @Query("UPDATE videos SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE videos SET title = :title, categoryId = :categoryId WHERE id = :id")
    suspend fun updateDetails(id: Long, title: String, categoryId: String)

    @Query("UPDATE videos SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePlaybackPosition(id: Long, positionMs: Long)

    @Query("UPDATE videos SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)

    @Query(
        "UPDATE videos SET localCachePath = :localCachePath, durationMs = :durationMs, thumbnailPath = :thumbnailPath WHERE id = :id"
    )
    suspend fun updateOfflineCache(
        id: Long,
        localCachePath: String?,
        durationMs: Long,
        thumbnailPath: String?
    )

    @Query("UPDATE videos SET syncedFromCloud = :synced WHERE id = :id")
    suspend fun markSyncedFromCloud(id: Long, synced: Boolean)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)

    @Query("DELETE FROM videos WHERE syncedFromCloud = 1")
    suspend fun deleteAllCloudSyncedVideos()
}
