package com.hebi.homecrew.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewAuthSessionTest {
    @Test
    fun localModeAndRestoringNeverPretendToBeSignedIn() {
        assertFalse(HomeCrewAuthSession.Restoring is HomeCrewAuthSession.SignedIn)
        assertFalse(HomeCrewAuthSession.LocalOnly is HomeCrewAuthSession.SignedIn)
    }

    @Test
    fun signedInSessionUsesStableFirebaseUidInsteadOfHouseholdMemberName() {
        val user = HomeCrewAuthUser(
            uid = "firebase-test-uid",
            displayName = "테스트 구성원",
            email = "qa@example.invalid"
        )
        val session = HomeCrewAuthSession.SignedIn(user)

        assertEquals("firebase-test-uid", session.user.uid)
        assertEquals("테스트 구성원", session.user.displayName)
        assertEquals("qa@example.invalid", session.user.email)
        assertTrue(session != HomeCrewAuthSession.LocalOnly)
    }

    @Test
    fun emptyFirebaseUidCannotCreateSignedInSession() {
        assertThrows(IllegalArgumentException::class.java) {
            HomeCrewAuthUser(uid = "  ", displayName = "나", email = null)
        }
    }

    @Test
    fun repeatedAuthCallbacksForSameUserCompareEqual() {
        val session = HomeCrewAuthSession.SignedIn(
            HomeCrewAuthUser(uid = "firebase-test-uid", displayName = "나", email = null)
        )
        val same = HomeCrewAuthSession.SignedIn(
            HomeCrewAuthUser(uid = "firebase-test-uid", displayName = "나", email = null)
        )
        assertEquals(session, same)
    }
}
