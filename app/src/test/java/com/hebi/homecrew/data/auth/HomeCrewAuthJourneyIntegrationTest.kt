package com.hebi.homecrew.data.auth

import com.hebi.homecrew.ui.screens.HomeCrewAccountAction
import com.hebi.homecrew.ui.screens.homeCrewAccountUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewAuthJourneyIntegrationTest {
    private val user = HomeCrewAuthUser(
        uid = "private-firebase-uid-should-not-render",
        displayName = "가족 사용자",
        email = "member@example.invalid"
    )
    private val signedIn = HomeCrewAuthSession.SignedIn(user)

    @Test
    fun optionalLoginJourneyUsesFirebaseSessionInsteadOfLoginButtonResult() {
        val restoring = homeCrewAccountUiState(
            HomeCrewAuthSession.Restoring, false, null
        )
        assertFalse(restoring.showSignIn)
        assertFalse(restoring.showDeleteAccount)

        val offline = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly, false, GoogleSignInResult.NETWORK_ERROR
        )
        assertTrue(offline.signInEnabled)
        assertTrue(offline.feedbackIsError)
        assertTrue(offline.description.contains("로그인하지 않아도"))

        val canceled = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly, false, GoogleSignInResult.CANCELLED
        )
        assertFalse(canceled.feedbackIsError)
        assertTrue(canceled.showSignIn)

        val premature = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly, false, GoogleSignInResult.SIGNED_IN
        )
        assertEquals("로그인 없이 사용 중", premature.status)
        assertNull(premature.accountEmail)

        val confirmed = homeCrewAccountUiState(
            signedIn, false, GoogleSignInResult.SIGNED_IN
        )
        assertEquals("Google 계정 연결됨", confirmed.status)
        assertEquals(user.email, confirmed.accountEmail)
        assertFalse(confirmed.showSignIn)
        assertTrue(confirmed.showSignOut)
        assertTrue(confirmed.showDeleteAccount)
        assertFalse(confirmed.toString().contains(user.uid))
    }

    @Test
    fun sessionSignOutAndReloginHaveNoFalseCompletionOrUidDisclosure() {
        val pending = homeCrewAccountUiState(
            signedIn, false, null, accountAction = HomeCrewAccountAction.SIGN_OUT
        )
        assertEquals("로그아웃 처리 중", pending.status)
        assertFalse(pending.accountActionsEnabled)

        val signedOut = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly, false, null,
            accountResult = HomeCrewAccountActionResult.SIGNED_OUT
        )
        assertEquals("로그인 없이 사용 중", signedOut.status)
        assertTrue(signedOut.feedback?.contains("기기") == true)
        assertTrue(signedOut.signInEnabled)
        assertFalse(signedOut.showDeleteAccount)

        val relogged = homeCrewAccountUiState(signedIn, false, null)
        assertEquals(user.email, relogged.accountEmail)
        assertTrue(relogged.accountActionsEnabled)
        assertFalse(relogged.toString().contains(user.uid))
    }

    @Test
    fun failedDeletionNeverClaimsSuccessAndOnlyConfirmedDeletionAllowsLocalMode() {
        val pending = homeCrewAccountUiState(
            signedIn, false, null, accountAction = HomeCrewAccountAction.DELETE_ACCOUNT
        )
        assertFalse(pending.accountActionsEnabled)

        for (failure in listOf(
            HomeCrewAccountActionResult.CANCELLED,
            HomeCrewAccountActionResult.INVALID_CREDENTIAL,
            HomeCrewAccountActionResult.NETWORK_ERROR,
            HomeCrewAccountActionResult.RECENT_LOGIN_REQUIRED,
            HomeCrewAccountActionResult.FAILED
        )) {
            val failed = homeCrewAccountUiState(
                signedIn, false, null, accountResult = failure
            )
            assertEquals("Google 계정 연결됨", failed.status)
            assertTrue(failed.accountActionsEnabled)
            assertFalse(failed.feedback?.contains("계정이 삭제되었습니다") == true)
            assertFalse(failed.toString().contains(user.uid))
        }

        val deleted = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly, false, null,
            accountResult = HomeCrewAccountActionResult.DELETED
        )
        assertEquals("로그인 없이 사용 중", deleted.status)
        assertTrue(deleted.feedback?.contains("기기") == true)
        assertFalse(deleted.showSignOut)
        assertTrue(deleted.signInEnabled)
    }
}
