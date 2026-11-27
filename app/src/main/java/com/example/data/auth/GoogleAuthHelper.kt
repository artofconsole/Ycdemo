package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthHelper {

    private const val TAG = "yCloudAuth"

    suspend fun attemptAutoSignIn(context: Context): Boolean {
        if (Firebase.auth.currentUser != null) return true
        val clientId = runCatching {
            context.getString(R.string.default_web_client_id)
        }.getOrNull() ?: return false

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    sealed interface InteractiveSignInResult {
        data class Success(val email: String?) : InteractiveSignInResult
        data object Cancelled : InteractiveSignInResult
        data class Error(val message: String) : InteractiveSignInResult
    }

    suspend fun signInWithGoogle(activity: Activity): InteractiveSignInResult {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return InteractiveSignInResult.Error("Google oturum yapılandırması bulunamadı.")
        }

        val credentialManager = CredentialManager.create(activity)

        // 1. Primary flow: GetSignInWithGoogleOption (standard Google Sign-In bottom sheet)
        val primaryOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(primaryOption)
            .build()

        try {
            val cred = credentialManager.getCredential(activity, primaryRequest).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                return InteractiveSignInResult.Success(authResult.user?.email)
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            return InteractiveSignInResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Primary CredentialManager sign-in failed (${e.javaClass.simpleName}): ${e.message}", e)
            // Continue to fallbacks below (important for Android TV & older Play Services)
        } catch (e: Exception) {
            Log.w(TAG, "Primary CredentialManager unexpected error: ${e.message}", e)
        }

        // 2. Fallback A: GetGoogleIdOption with setFilterByAuthorizedAccounts(false)
        val secondaryOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val secondaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(secondaryOption)
            .build()

        try {
            val cred = credentialManager.getCredential(activity, secondaryRequest).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                return InteractiveSignInResult.Success(authResult.user?.email)
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Secondary Google Sign-In cancelled: ${e.message}", e)
            return InteractiveSignInResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.w(TAG, "NoCredentialException on device/TV: ${e.message}", e)
        } catch (e: Exception) {
            Log.w(TAG, "Secondary CredentialManager error: ${e.message}", e)
        }

        // 3. Fallback B: Firebase Browser/CustomTab OAuth flow (helpful when Credential Manager sheet is unavailable)
        return try {
            val provider = OAuthProvider.newBuilder("google.com")
                .addCustomParameter("prompt", "select_account")
                .build()
            val authResult = Firebase.auth
                .startActivityForSignInWithProvider(activity, provider)
                .await()
            InteractiveSignInResult.Success(authResult.user?.email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "All Google Sign-In methods failed", e)
            val msg = e.localizedMessage.orEmpty()
            val friendlyMessage = if (msg.contains("cancel", ignoreCase = true)) {
                return InteractiveSignInResult.Cancelled
            } else {
                "Bu cihazda Google girişi açılamadı (Cihazda Google hesabı/tarayıcı olmayabilir). TV için aşağıdaki 'Telefondan Link Gönder (Wi-Fi)' seçeneğini kullanabilirsiniz."
            }
            InteractiveSignInResult.Error(friendlyMessage)
        }
    }

    suspend fun signOut(context: Context, onBeforeAuthSignOut: suspend () -> Unit = {}) {
        withContext(NonCancellable) {
            runCatching { onBeforeAuthSignOut() }
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }.onFailure { e ->
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            }
            Firebase.auth.signOut()
        }
    }
}
