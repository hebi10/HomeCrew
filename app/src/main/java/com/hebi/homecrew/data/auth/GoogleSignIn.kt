package com.hebi.homecrew.data.auth

import android.app.Activity
import android.content.Context

internal interface GoogleSignIn {
    fun isSignedIn(context: Context): Boolean

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
