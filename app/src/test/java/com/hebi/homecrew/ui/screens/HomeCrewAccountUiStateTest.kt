package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.auth.GoogleSignInResult
import com.hebi.homecrew.data.auth.HomeCrewAccountActionResult
import com.hebi.homecrew.data.auth.HomeCrewAuthSession
import com.hebi.homecrew.data.auth.HomeCrewAuthUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewAccountUiStateTest {
    @Test
    fun restoringDoesNotExposeLoginOrStaleAccountData() {
        val state = homeCrewAccountUiState(
            HomeCrewAuthSession.Restoring, signingIn = false, result = GoogleSignInResult.SIGNED_IN
        )

        assertEquals("계정 상태 확인 중", state.status)
        assertFalse(state.showSignIn)
        assertNull(state.accountName)
        assertNull(state.accountEmail)
        assertNull(state.feedback)
    }

    @Test
    fun localOnlyModeDoesNotRequireGoogleAccount() {
        val state = homeCrewAccountUiState(HomeCrewAuthSession.LocalOnly, false, null)

        assertEquals("로그인 없이 사용 중", state.status)
        assertTrue(state.showSignIn)
        assertTrue(state.signInEnabled)
        assertEquals("Google로 로그인", state.signInLabel)
        assertNull(state.accountName)
    }

    @Test
    fun signingInPreventsDuplicateButtonRequests() {
        val state = homeCrewAccountUiState(HomeCrewAuthSession.LocalOnly, true, null)

        assertEquals("Google 로그인 진행 중", state.status)
        assertFalse(state.signInEnabled)
        assertEquals("로그인 중…", state.signInLabel)
    }

    @Test
    fun signedInAccountShowsNameAndEmailWithoutExposingUid() {
        val session = HomeCrewAuthSession.SignedIn(
            HomeCrewAuthUser(
                uid = "private-firebase-uid",
                displayName = "  예시 사용자  ",
                email = "  user@example.invalid  "
            )
        )
        val state = homeCrewAccountUiState(session, false, GoogleSignInResult.FAILED)

        assertEquals("Google 계정 연결됨", state.status)
        assertEquals("예시 사용자", state.accountName)
        assertEquals("user@example.invalid", state.accountEmail)
        assertFalse(state.showSignIn)
        assertNull(state.feedback)
        assertFalse(state.toString().contains("private-firebase-uid"))
    }

    @Test
    fun signedInSessionHandlesMissingOptionalProfileFields() {
        val user = HomeCrewAuthUser(uid = "uid", displayName = " ", email = null)
        val state = homeCrewAccountUiState(HomeCrewAuthSession.SignedIn(user), false, null)

        assertEquals("이름 정보 없음", state.accountName)
        assertEquals("이메일 정보 없음", state.accountEmail)
    }

    @Test
    fun cancellationAndNoAccountAreNonErrorMessages() {
        for (result in listOf(GoogleSignInResult.CANCELLED, GoogleSignInResult.NO_ACCOUNT)) {
            val state = homeCrewAccountUiState(HomeCrewAuthSession.LocalOnly, false, result)
            assertTrue(state.feedback?.isNotEmpty() == true)
            assertFalse(state.feedbackIsError)
            assertTrue(state.signInEnabled)
        }
    }

    @Test
    fun actualAuthErrorsHaveRetryableFeedback() {
        for (result in listOf(
            GoogleSignInResult.NETWORK_ERROR,
            GoogleSignInResult.CONFIGURATION_ERROR,
            GoogleSignInResult.INVALID_CREDENTIAL,
            GoogleSignInResult.FAILED
        )) {
            val state = homeCrewAccountUiState(HomeCrewAuthSession.LocalOnly, false, result)
            assertTrue("Missing feedback for $result", state.feedback?.isNotBlank() == true)
            assertTrue(state.feedbackIsError)
            assertTrue(state.signInEnabled)
        }
    }

    @Test
    fun signInResultDoesNotOverrideUnconfirmedFirebaseSession() {
        val state = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly, false, GoogleSignInResult.SIGNED_IN
        )

        assertEquals("로그인 없이 사용 중", state.status)
        assertEquals("Google 계정 상태를 확인하고 있습니다.", state.feedback)
        assertNull(state.accountEmail)
    }
    @Test
    fun signedInUsersSeeAccountControlsAndProgressDisablesBoth() {
        val session = HomeCrewAuthSession.SignedIn(
            HomeCrewAuthUser(uid = "private-uid", displayName = "테스트", email = null)
        )
        val ready = homeCrewAccountUiState(session, false, null)
        assertTrue(ready.showSignOut)
        assertTrue(ready.showDeleteAccount)
        assertTrue(ready.accountActionsEnabled)

        val deleting = homeCrewAccountUiState(
            session, false, null, accountAction = HomeCrewAccountAction.DELETE_ACCOUNT
        )
        assertEquals("계정 삭제 처리 중", deleting.status)
        assertFalse(deleting.accountActionsEnabled)
        assertFalse(deleting.toString().contains("private-uid"))

        val signingOut = homeCrewAccountUiState(
            session, false, null, accountAction = HomeCrewAccountAction.SIGN_OUT
        )
        assertEquals("로그아웃 처리 중", signingOut.status)
        assertFalse(signingOut.accountActionsEnabled)
    }

    @Test
    fun accountDeletionMessagesDistinguishRemoteResultFromLocalData() {
        val local = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly,
            signingIn = false,
            result = null,
            accountResult = HomeCrewAccountActionResult.DELETED
        )
        assertEquals("로그인 없이 사용 중", local.status)
        assertTrue(local.feedback?.contains("기기") == true)
        assertFalse(local.feedbackIsError)
        assertTrue(local.showSignIn)

        val warn = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly,
            signingIn = false,
            result = null,
            accountResult = HomeCrewAccountActionResult.DELETED_CREDENTIAL_WARNING
        )
        assertTrue(warn.feedback?.contains("삭제됐지만") == true)
        assertTrue(warn.feedbackIsError)
    }

    @Test
    fun failedReauthenticationDoesNotClaimAccountDeletion() {
        val signedIn = HomeCrewAuthSession.SignedIn(
            HomeCrewAuthUser(uid = "not-visible", displayName = null, email = null)
        )
        val failed = homeCrewAccountUiState(
            signedIn, signingIn = false, result = null,
            accountResult = HomeCrewAccountActionResult.RECENT_LOGIN_REQUIRED
        )
        assertTrue(failed.feedback?.contains("재인증") == true)
        assertTrue(failed.feedbackIsError)
        assertTrue(failed.accountActionsEnabled)

        val cancelled = homeCrewAccountUiState(
            signedIn, false, null, accountResult = HomeCrewAccountActionResult.CANCELLED
        )
        assertFalse(cancelled.feedbackIsError)
        assertTrue(cancelled.feedback?.contains("삭제되지 않았습니다") == true)
        assertFalse(cancelled.toString().contains("not-visible"))
    }

    @Test
    fun signOutDoesNotAlterLocalOnlyEligibility() {
        val state = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly, false, null,
            accountResult = HomeCrewAccountActionResult.SIGNED_OUT
        )
        assertTrue(state.showSignIn)
        assertTrue(state.signInEnabled)
        assertTrue(state.feedback?.contains("집안일") == true)
        assertFalse(state.feedbackIsError)
    }

    @Test
    fun confirmedSignOutOrDeletionWaitsForActualAuthListenerBeforeShowingLocalState() {
        val user = HomeCrewAuthSession.SignedIn(
            HomeCrewAuthUser(
                uid = "internal-uid-not-for-ui", displayName = "테스트", email = "qa@example.invalid"
            )
        )
        for (result in listOf(
            HomeCrewAccountActionResult.SIGNED_OUT,
            HomeCrewAccountActionResult.SIGNED_OUT_CREDENTIAL_WARNING,
            HomeCrewAccountActionResult.DELETED,
            HomeCrewAccountActionResult.DELETED_CREDENTIAL_WARNING
        )) {
            val pending = homeCrewAccountUiState(
                user, signingIn = false, result = null, accountResult = result
            )
            assertEquals("Firebase 계정 상태 확인 중", pending.status)
            assertFalse(pending.accountActionsEnabled)
            assertFalse(pending.showSignOut)
            assertFalse(pending.showDeleteAccount)
            assertNull(pending.accountName)
            assertNull(pending.accountEmail)
            assertFalse(pending.toString().contains("internal-uid-not-for-ui"))
        }
        val local = homeCrewAccountUiState(
            HomeCrewAuthSession.LocalOnly, false, null,
            accountResult = HomeCrewAccountActionResult.DELETED
        )
        assertEquals("로그인 없이 사용 중", local.status)
        assertTrue(local.signInEnabled)
    }

}
