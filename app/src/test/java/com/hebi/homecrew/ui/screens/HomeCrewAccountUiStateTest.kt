package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.auth.GoogleSignInResult
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
}
