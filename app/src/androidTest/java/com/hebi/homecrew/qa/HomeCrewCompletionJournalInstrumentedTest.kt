package com.hebi.homecrew.qa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hebi.homecrew.data.cloud.AndroidCompletionJournalStorage
import com.hebi.homecrew.data.cloud.HomeCrewCompletionJournal
import com.hebi.homecrew.data.cloud.RemoteCompletionCommand
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewCompletionJournalInstrumentedTest {
    @Test
    fun storedRequestSurvivesJournalRecreationAndIsAccountScoped() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val project = "demo-journal-qa"
        val uid = UUID.randomUUID().toString()
        val home = UUID.randomUUID().toString()
        val command = RemoteCompletionCommand("qa-occurrence", true, 12, UUID.randomUUID().toString())
        fun journal(account: String = uid, household: String = home) =
            HomeCrewCompletionJournal(AndroidCompletionJournalStorage(context), project, account, household)

        val first = journal()
        try {
            first.stage(command)
            assertEquals(command, journal().load())
            assertNull(journal("different-account").load())
            assertNull(journal(household = "different-house").load())
            journal().remove(command)
            assertNull(first.load())
        } finally {
            first.remove(command)
        }
    }
}
