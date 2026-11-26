package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Bulut eşitleme için Google hesabı ile giriş yapılmalıdır.")
    }

    fun observeCloudVideos(expectedUid: String): Flow<Result<List<CloudVideo>>> = flow {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank() || currentUid != expectedUid) {
            emit(Result.success(emptyList()))
            return@flow
        }

        val path = "users/$expectedUid/videos"
        emitAll(
            db.collection("users")
                .document(expectedUid)
                .collection("videos")
                .snapshots()
                .map { snapshot ->
                    val items = snapshot.documents.mapNotNull { doc ->
                        runCatching {
                            doc.toObject(
                                CloudVideo::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }.getOrNull()
                    }
                    Result.success(items)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    emit(Result.failure(firestoreError ?: error))
                }
        )
    }

    suspend fun upsertCloudVideo(
        title: String,
        sourceType: VideoSourceType,
        videoUrl: String,
        thumbnailUrl: String,
        category: VideoCategory,
        favorite: Boolean,
        durationMs: Long = 0L,
        existingDocId: String? = null
    ): Result<String> {
        val currentUid = auth.currentUser?.uid
        var docPath = "users/${currentUid ?: "unauthenticated"}/videos"
        return try {
            val uid = requireUserId()
            val normalizedSource = if (sourceType == VideoSourceType.YOUTUBE) {
                VideoSourceType.YOUTUBE.id
            } else {
                VideoSourceType.CLOUD_URL.id
            }
            val safeCategory = if (category in VideoCategory.assignableCategories) {
                category.id
            } else {
                VideoCategory.CARTOON.id
            }
            val cleanTitle = title.trim().ifEmpty { "yCloud Video" }.take(150)
            val cleanUrl = videoUrl.trim().take(1000)
            require(cleanUrl.isNotEmpty()) { "Video bağlantısı boş olamaz." }
            val cleanThumb = thumbnailUrl.trim().take(1000)
            val docId = existingDocId?.takeIf { it.isNotBlank() && it.length <= 128 && !it.contains("/") }
                ?: stableDocIdForUrl(normalizedSource, cleanUrl)

            val docRef = db.collection("users")
                .document(uid)
                .collection("videos")
                .document(docId)
            docPath = docRef.path

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mapOf(
                    "title" to cleanTitle,
                    "sourceType" to normalizedSource,
                    "videoUrl" to cleanUrl,
                    "thumbnailUrl" to cleanThumb,
                    "categoryId" to safeCategory,
                    "favorite" to favorite,
                    "durationMs" to durationMs.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "sourceType" to normalizedSource,
                    "videoUrl" to cleanUrl,
                    "thumbnailUrl" to cleanThumb,
                    "categoryId" to safeCategory,
                    "favorite" to favorite,
                    "durationMs" to durationMs.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCloudVideoDetails(
        docId: String,
        title: String,
        category: VideoCategory,
        favorite: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/videos/$docId"
        return try {
            val uid = requireUserId()
            require(docId.isNotBlank() && !docId.contains("/")) { "Geçersiz bulut video kimliği." }
            val safeCategory = if (category in VideoCategory.assignableCategories) {
                category.id
            } else {
                VideoCategory.CARTOON.id
            }
            val cleanTitle = title.trim().ifEmpty { "yCloud Video" }.take(150)
            val docRef = db.collection("users")
                .document(uid)
                .collection("videos")
                .document(docId)
            docPath = docRef.path

            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "categoryId" to safeCategory,
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudVideo(docId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/videos/$docId"
        return try {
            val uid = requireUserId()
            require(docId.isNotBlank() && !docId.contains("/")) { "Geçersiz bulut video kimliği." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("videos")
                .document(docId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        fun stableDocIdForUrl(sourceType: String, url: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$sourceType|$url".toByteArray())
            val hex = digest.joinToString("") { "%02x".format(it) }.take(24)
            return "${sourceType.lowercase()}_$hex"
        }
    }
}
