package com.hebi.homecrew.data.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal val homeCrewGoogleSignIn: GoogleSignIn? = FirebaseGoogleSignIn

private object FirebaseGoogleSignIn : GoogleSignIn {
    private val operationLock = Mutex()

    override fun observeSession(context: Context): Flow<HomeCrewAuthSession> = callbackFlow {
        val auth = firebaseAuth(context)
        if (auth == null) {
            trySend(HomeCrewAuthSession.LocalOnly)
            close()
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { current ->
            val session = current.currentUser?.let { user ->
                HomeCrewAuthSession.SignedIn(
                    HomeCrewAuthUser(
                        uid = user.uid,
                        displayName = user.displayName,
                        email = user.email
                    )
                )
            } ?: HomeCrewAuthSession.LocalOnly
            trySend(session)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(activity: Activity): GoogleSignInResult {
        if (!operationLock.tryLock()) return GoogleSignInResult.IN_PROGRESS

        try {
            val auth = firebaseAuth(activity) ?: return GoogleSignInResult.CONFIGURATION_ERROR
            if (auth.currentUser != null) return GoogleSignInResult.SIGNED_IN

            val webClientId = findWebClientId(activity) ?: return GoogleSignInResult.CONFIGURATION_ERROR
            val manager = CredentialManager.create(activity)
            val credential = manager.getCredential(activity, googleRequest(webClientId)).credential
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
            operationLock.unlock()
        }
    }


    override suspend fun signOut(context: Context): HomeCrewAccountActionResult {
        if (!operationLock.tryLock()) return HomeCrewAccountActionResult.IN_PROGRESS
        try {
            val auth = firebaseAuth(context) ?: return HomeCrewAccountActionResult.CONFIGURATION_ERROR
            if (auth.currentUser == null) return HomeCrewAccountActionResult.NOT_SIGNED_IN
            auth.signOut()
            return if (clearCredentialState(context)) {
                HomeCrewAccountActionResult.SIGNED_OUT
            } else {
                HomeCrewAccountActionResult.SIGNED_OUT_CREDENTIAL_WARNING
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return accountError(error)
        } finally {
            operationLock.unlock()
        }
    }

    override suspend fun deleteAccount(activity: Activity): HomeCrewAccountActionResult {
        if (!operationLock.tryLock()) return HomeCrewAccountActionResult.IN_PROGRESS
        try {
            val auth = firebaseAuth(activity) ?: return HomeCrewAccountActionResult.CONFIGURATION_ERROR
            val user = auth.currentUser ?: return HomeCrewAccountActionResult.NOT_SIGNED_IN
            val originalUid = user.uid
            deleteWithRecentLoginFallback(
                sameAccount = { auth.currentUser?.uid == originalUid },
                delete = {
                    // Completion of Firebase deletion must not be reported while a remote task is in flight.
                    withContext(NonCancellable) { user.delete().await() }
                },
                requiresRecentLogin = { error -> error is FirebaseAuthRecentLoginRequiredException },
                reauthenticate = {
                    val webClientId = findWebClientId(activity)
                        ?: throw IllegalStateException("Google authentication is not configured")
                    val credential = CredentialManager.create(activity)
                        .getCredential(activity, googleRequest(webClientId)).credential
                    val token = googleIdToken(credential)
                        ?: throw IllegalArgumentException("Invalid Google credential")
                    if (auth.currentUser?.uid != originalUid) {
                        throw IllegalStateException("Firebase user changed")
                    }
                    withContext(NonCancellable) {
                        user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()
                    }
                }
            )
            return if (clearCredentialState(activity)) {
                HomeCrewAccountActionResult.DELETED
            } else {
                HomeCrewAccountActionResult.DELETED_CREDENTIAL_WARNING
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return accountError(error)
        } finally {
            operationLock.unlock()
        }
    }

    private suspend fun clearCredentialState(context: Context): Boolean = try {
        withContext(NonCancellable) {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun accountError(error: Exception): HomeCrewAccountActionResult = when (error) {
        is GetCredentialCancellationException -> HomeCrewAccountActionResult.CANCELLED
        is NoCredentialException -> HomeCrewAccountActionResult.INVALID_CREDENTIAL
        is FirebaseNetworkException -> HomeCrewAccountActionResult.NETWORK_ERROR
        is GetCredentialProviderConfigurationException,
        is GetCredentialUnsupportedException -> HomeCrewAccountActionResult.CONFIGURATION_ERROR
        is FirebaseAuthInvalidCredentialsException,
        is GoogleIdTokenParsingException,
        is IllegalArgumentException -> HomeCrewAccountActionResult.INVALID_CREDENTIAL
        is FirebaseAuthRecentLoginRequiredException -> HomeCrewAccountActionResult.RECENT_LOGIN_REQUIRED
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_API_KEY", "ERROR_OPERATION_NOT_ALLOWED" ->
                HomeCrewAccountActionResult.CONFIGURATION_ERROR
            else -> HomeCrewAccountActionResult.FAILED
        }
        else -> HomeCrewAccountActionResult.FAILED
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

    private fun googleRequest(webClientId: String): GetCredentialRequest {
        val googleId = GetSignInWithGoogleOption.Builder(webClientId).build()
        return GetCredentialRequest.Builder().addCredentialOption(googleId).build()
    }

    private fun googleIdToken(credential: Credential): String? {
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return null
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken.takeIf(String::isNotBlank)
    }
}
