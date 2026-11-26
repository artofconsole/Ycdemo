package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET
}

@Serializable
data class FirestoreErrorInfo(
    val error: String,
    val operationType: String,
    val path: String?,
    val uid: String?,
    val email: String?
)

fun handleFirestoreError(
    error: Exception,
    operationType: OperationType,
    path: String?
) {
    val user = runCatching { Firebase.auth.currentUser }.getOrNull()
    val info = FirestoreErrorInfo(
        error = error.localizedMessage ?: error.javaClass.simpleName,
        operationType = operationType.name,
        path = path,
        uid = user?.uid,
        email = user?.email
    )
    Log.e("FirestoreError", Json.encodeToString(info), error)
}
