package com.hebi.homecrew.data.auth

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.Flow

internal data class HomeCrewAuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?
) {
    init {
        require(uid.isNotBlank()) { "Authenticated user must have a Firebase UID" }
    }
}

internal sealed interface HomeCrewAuthSession {
    data object Restoring : HomeCrewAuthSession
    data object LocalOnly : HomeCrewAuthSession
    data class SignedIn(val user: HomeCrewAuthUser) : HomeCrewAuthSession
}

internal interface GoogleSignIn {
    fun observeSession(context: Context): Flow<HomeCrewAuthSession>
    suspend fun signIn(activity: Activity): GoogleSignInResult
}

internal enum class GoogleSignInResult {
    SIGNED_IN,
    CANCELLED,
    IN_PROGRESS,
    NO_ACCOUNT,
    NETWORK_ERROR,
    CONFIGURATION_ERROR,
    INVALID_CREDENTIAL,
    FAILED
}
