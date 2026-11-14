package com.hebi.homecrew.qa

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.hebi.homecrew.data.auth.HomeCrewAuthSession
import com.hebi.homecrew.data.auth.homeCrewGoogleSignIn
import com.hebi.homecrew.data.local.HomeCrewDatabase
import com.hebi.homecrew.data.local.HomeCrewRoomRepository
import com.hebi.homecrew.ui.screens.homeCrewAccountUiState
import com.hebi.homecrew.ui.setup.HouseholdSetup
import com.hebi.homecrew.ui.setup.LocalHouseholdStore
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseAuthIsolationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun repeatedFirebaseSessionObserversLeaveRoomAndLocalSettingsUnchanged() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, HomeCrewDatabase::class.java).build()
        val household = HouseholdSetup(
            homeName = "인증 QA 집",
            members = listOf("나", "지민"),
            defaultMember = "나",
            selectedTemplateIds = listOf("dishes", "recycle")
        )
        val today = LocalDate.of(2026, 11, 10)
        try {
            val room = HomeCrewRoomRepository(database.chores()) { 123456L }
            room.initializeIfNeeded(household, today)
            val initial = room.snapshot()
            val completed = initial.chores.first {
                it.definition.id == "dishes" && it.date == today
            }
            val skipped = initial.chores.first {
                it.definition.id == "recycle" && it.date == today
            }
            room.toggleCompleted(completed.id, "나")
            room.toggleSkipped(skipped.id, "QA 건너뛰기")
            val before = room.snapshot()
            val backupBefore = room.exportBackup(household)
            val savedHouseholdBefore = LocalHouseholdStore(context).load()

            assertTrue(before.chores.any { it.id == completed.id && it.completed })
            assertTrue(before.chores.any { it.id == skipped.id && it.skipped })
            val auth = requireNotNull(homeCrewGoogleSignIn)
            val firstSession = withTimeout(10_000) { auth.observeSession(context).first() }
            val restoredSession = withTimeout(10_000) { auth.observeSession(context).first() }
            val sameSession = when {
                firstSession is HomeCrewAuthSession.SignedIn &&
                    restoredSession is HomeCrewAuthSession.SignedIn ->
                    firstSession.user.uid == restoredSession.user.uid
                else -> firstSession is HomeCrewAuthSession.LocalOnly &&
                    restoredSession is HomeCrewAuthSession.LocalOnly
            }
            assertTrue("Firebase session changed without an auth action", sameSession)

            val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
            if (currentFirebaseUser == null) {
                assertTrue(restoredSession is HomeCrewAuthSession.LocalOnly)
            } else {
                assertTrue(restoredSession is HomeCrewAuthSession.SignedIn)
                assertTrue(
                    "Current Firebase UID does not match observed session",
                    currentFirebaseUser.uid ==
                        (restoredSession as HomeCrewAuthSession.SignedIn).user.uid
                )
            }

            val accountUi = homeCrewAccountUiState(restoredSession, false, null)
            assertFalse("Firebase UID must stay out of account UI", accountUi.toString()
                .contains(currentFirebaseUser?.uid ?: "never-an-actual-uid"))
            assertNotNull(accountUi.status)
            assertEquals(before, room.snapshot())
            assertEquals(backupBefore, room.exportBackup(household))
            assertTrue("Local household changed after auth observation",
                savedHouseholdBefore == LocalHouseholdStore(context).load())
        } finally {
            database.close()
        }
    }
}
