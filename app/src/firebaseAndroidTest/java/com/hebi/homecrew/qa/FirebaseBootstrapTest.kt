package com.hebi.homecrew.qa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
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
