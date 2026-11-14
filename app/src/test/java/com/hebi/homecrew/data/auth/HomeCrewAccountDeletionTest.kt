package com.hebi.homecrew.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewAccountDeletionTest {
    private class RecentLoginRequired : IllegalStateException()

    @Test
    fun recentSessionDeletesWithoutPromptingAgain() = runBlocking {
        var deleted = 0
        var requestedReauth = false
        deleteWithRecentLoginFallback(
            sameAccount = { true },
            delete = { deleted++ },
            requiresRecentLogin = { it is RecentLoginRequired },
            reauthenticate = { requestedReauth = true }
        )
        assertEquals(1, deleted)
        assertFalse(requestedReauth)
    }

    @Test
    fun expiredSessionReauthenticatesBeforeSingleRetry() = runBlocking {
        var attempts = 0
        var authenticated = false
        deleteWithRecentLoginFallback(
            sameAccount = { true },
            delete = {
                attempts++
                if (attempts == 1) throw RecentLoginRequired()
                assertTrue(authenticated)
            },
            requiresRecentLogin = { it is RecentLoginRequired },
            reauthenticate = { authenticated = true }
        )
        assertEquals(2, attempts)
        assertTrue(authenticated)
    }

    @Test
    fun switchingFirebaseAccountCancelsDeletionRetry() = runBlocking {
        var activeOriginalUser = true
        var attempts = 0
        val result = runCatching {
            deleteWithRecentLoginFallback(
                sameAccount = { activeOriginalUser },
                delete = {
                    attempts++
                    throw RecentLoginRequired()
                },
                requiresRecentLogin = { it is RecentLoginRequired },
                reauthenticate = { activeOriginalUser = false }
            )
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(1, attempts)
    }

    @Test
    fun unrelatedErrorsNeverRetryOrPrompt() = runBlocking {
        var attempts = 0
        var prompted = false
        val failure = runCatching {
            deleteWithRecentLoginFallback(
                sameAccount = { true },
                delete = { attempts++; throw IllegalArgumentException("invalid") },
                requiresRecentLogin = { it is RecentLoginRequired },
                reauthenticate = { prompted = true }
            )
        }
        assertTrue(failure.isFailure)
        assertEquals(1, attempts)
        assertFalse(prompted)
    }

    @Test
    fun missingAuthenticatedUserCannotStartDeletion() = runBlocking {
        var attempts = 0
        val failure = runCatching {
            deleteWithRecentLoginFallback(
                sameAccount = { false },
                delete = { attempts++ },
                requiresRecentLogin = { it is RecentLoginRequired },
                reauthenticate = {}
            )
        }
        assertTrue(failure.isFailure)
        assertEquals(0, attempts)
    }
}
