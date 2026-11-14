package com.hebi.homecrew.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewDeletionFailureIntegrationTest {
    private class RecentLogin : IllegalStateException()

    @Test
    fun cancelingGoogleReauthenticationNeverRetriesDeletion() = runBlocking {
        var deleteAttempts = 0
        var reauthAttempts = 0
        val result = runCatching {
            deleteWithRecentLoginFallback(
                sameAccount = { true },
                delete = {
                    deleteAttempts++
                    throw RecentLogin()
                },
                requiresRecentLogin = { it is RecentLogin },
                reauthenticate = {
                    reauthAttempts++
                    throw CancellationException("Google account picker cancelled")
                }
            )
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, deleteAttempts)
        assertEquals(1, reauthAttempts)
    }

    @Test
    fun secondFirebaseDeleteFailureIsPropagatedWithoutThirdAttempt() = runBlocking {
        var attempts = 0
        var reauthAttempts = 0
        val result = runCatching {
            deleteWithRecentLoginFallback(
                sameAccount = { true },
                delete = {
                    attempts++
                    if (attempts == 1) throw RecentLogin()
                    throw IllegalStateException("server delete failed")
                },
                requiresRecentLogin = { it is RecentLogin },
                reauthenticate = { reauthAttempts++ }
            )
        }
        assertTrue(result.isFailure)
        assertEquals(2, attempts)
        assertEquals(1, reauthAttempts)
    }

    @Test
    fun anotherAccountBeforePromptPreventsReauthentication() = runBlocking {
        var correctAccount = true
        var attempts = 0
        var prompted = false
        val result = runCatching {
            deleteWithRecentLoginFallback(
                sameAccount = { correctAccount },
                delete = {
                    attempts++
                    correctAccount = false
                    throw RecentLogin()
                },
                requiresRecentLogin = { it is RecentLogin },
                reauthenticate = { prompted = true }
            )
        }
        assertTrue(result.isFailure)
        assertEquals(1, attempts)
        assertFalse(prompted)
    }
}
