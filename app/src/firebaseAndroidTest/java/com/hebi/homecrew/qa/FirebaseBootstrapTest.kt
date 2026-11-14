package com.hebi.homecrew.qa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.hebi.homecrew.data.auth.HomeCrewAuthSession
import com.hebi.homecrew.data.auth.homeCrewGoogleSignIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseBootstrapTest {
    @Test
    fun defaultAppIsInitializedForHomeCrew() {
        val firebase = FirebaseApp.getInstance()

        assertEquals("com.hebi.homecrew", firebase.applicationContext.packageName)
        assertEquals("homecrew-1b14b", firebase.options.projectId)
    }

    @Test
    fun authSessionListenerRestoresCurrentFirebaseSession() = runBlocking {
        val context = FirebaseApp.getInstance().applicationContext
        val signIn = requireNotNull(homeCrewGoogleSignIn)
        val actual = withTimeout(8_000) { signIn.observeSession(context).first() }
        val firebaseUser = FirebaseAuth.getInstance().currentUser

        if (firebaseUser == null) {
            assertTrue("Firebase session should be local only", actual is HomeCrewAuthSession.LocalOnly)
        } else {
            assertTrue("Firebase user should map to signed-in session", actual is HomeCrewAuthSession.SignedIn)
            assertTrue(
                "Firebase Auth UID does not match observed session",
                firebaseUser.uid == (actual as HomeCrewAuthSession.SignedIn).user.uid
            )
        }
    }

    @Test
    fun googleSignInHasAWebClientResource() {
        val context = FirebaseApp.getInstance().applicationContext
        val resource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)

        assertTrue("Google sign-in web client resource is missing", resource != 0)
        assertTrue(
            "Google sign-in web client resource is invalid",
            context.getString(resource).endsWith(".apps.googleusercontent.com")
        )
    }
}
