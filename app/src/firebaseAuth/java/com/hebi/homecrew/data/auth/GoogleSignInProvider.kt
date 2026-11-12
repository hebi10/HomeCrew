package com.hebi.homecrew.data.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal val homeCrewGoogleSignIn: GoogleSignIn? = FirebaseGoogleSignIn

private object FirebaseGoogleSignIn : GoogleSignIn {
    private val signInLock = Mutex()

    override fun isSignedIn(context: Context): Boolean = firebaseAuth(context)?.currentUser != null

    override suspend fun signIn(activity: Activity): GoogleSignInResult {
        if (!signInLock.tryLock()) return GoogleSignInResult.IN_PROGRESS

        try {
            val auth = firebaseAuth(activity) ?: return GoogleSignInResult.CONFIGURATION_ERROR
            if (auth.currentUser != null) return GoogleSignInResult.SIGNED_IN

            val webClientId = findWebClientId(activity) ?: return GoogleSignInResult.CONFIGURATION_ERROR
            val manager = CredentialManager.create(activity)
            val credential = try {
                manager.getCredential(activity, googleRequest(webClientId, authorizedOnly = true)).credential
            } catch (_: NoCredentialException) {
                manager.getCredential(activity, googleRequest(webClientId, authorizedOnly = false)).credential
            }
            val token = googleIdToken(credential) ?: return GoogleSignInResult.INVALID_CREDENTIAL

            // The Firebase task can finish after the settings screen is closed.
            val user = withContext(NonCancellable) {
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
            }
            return if (user == null) GoogleSignInResult.FAILED else GoogleSignInResult.SIGNED_IN
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return when (error) {
                is GetCredentialCancellationException -> GoogleSignInResult.CANCELLED
                is NoCredentialException -> GoogleSignInResult.NO_ACCOUNT
                is FirebaseNetworkException -> GoogleSignInResult.NETWORK_ERROR
                is GetCredentialProviderConfigurationException,
                is GetCredentialUnsupportedException -> GoogleSignInResult.CONFIGURATION_ERROR
                is FirebaseAuthInvalidCredentialsException,
                is GoogleIdTokenParsingException -> GoogleSignInResult.INVALID_CREDENTIAL
                is FirebaseAuthException -> when (error.errorCode) {
                    "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_API_KEY", "ERROR_OPERATION_NOT_ALLOWED" ->
                        GoogleSignInResult.CONFIGURATION_ERROR
                    else -> GoogleSignInResult.FAILED
                }
                else -> GoogleSignInResult.FAILED
            }
        } finally {
            signInLock.unlock()
        }
    }

    private fun firebaseAuth(context: Context): FirebaseAuth? =
        FirebaseApp.getApps(context)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?.let { FirebaseAuth.getInstance(it) }

    // The Google Services plugin omits this resource if the local JSON lacks a web OAuth client.
    @SuppressLint("DiscouragedApi")
    private fun findWebClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.endsWith(".apps.googleusercontent.com") }
    }

    private fun googleRequest(webClientId: String, authorizedOnly: Boolean): GetCredentialRequest {
        val googleId = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .build()
        return GetCredentialRequest.Builder().addCredentialOption(googleId).build()
    }

    private fun googleIdToken(credential: Credential): String? {
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return null
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken.takeIf(String::isNotBlank)
    }
}
