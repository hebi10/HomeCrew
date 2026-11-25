package com.hebi.homecrew.qa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hebi.homecrew.data.cloud.HomeCrewLocalMigrationJournal
import com.hebi.homecrew.data.cloud.PendingLocalMigration
import com.hebi.homecrew.data.cloud.RemoteMigrationReceipt
import com.hebi.homecrew.data.local.ChoreAssignmentMemberEntity
import com.hebi.homecrew.data.local.ChoreDefinitionEntity
import com.hebi.homecrew.data.local.ChoreOccurrenceEntity
import com.hebi.homecrew.data.local.HomeCrewLocalBackup
import com.hebi.homecrew.data.local.HomeCrewRows
import com.hebi.homecrew.ui.setup.HouseholdSetup
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewLocalMigrationJournalInstrumentedTest {
    @Test fun backupAndReceiptSurviveJournalRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uid = UUID.randomUUID().toString()
        val house = UUID.randomUUID().toString()
        val setup = HouseholdSetup("우리 집", listOf("A"), "A", emptyList())
        val task = ChoreDefinitionEntity("task", "설거지", "주방", "A", "매일", 2, "19:00", "",
            20782, "DAILY", 0, 2, "FIXED", 1000, 2000)
        val rows = HomeCrewRows(listOf(task), listOf(ChoreAssignmentMemberEntity("task", 0, "A")),
            listOf(ChoreOccurrenceEntity("past", "task", 20782, "A", 2, true, 1500, "A")))
        val backup = HomeCrewLocalBackup.encode(setup, rows)
        val pending = PendingLocalMigration("a".repeat(64), setup, mapOf("A" to uid), backup, 1, 1, 1, 0)
        fun journal(account: String = uid, project: String = "demo-migration-qa") =
            HomeCrewLocalMigrationJournal(context, project, account, house)
        journal().stage(pending)
        assertEquals(pending, journal().load())
        assertNull(journal("other-account").load())
        assertNull(journal(project = "other-project").load())
        assertTrue(runCatching { journal().stage(pending.copy(digest = "b".repeat(64))) }.isFailure)
        assertTrue(runCatching { journal().confirm(RemoteMigrationReceipt("b".repeat(64), 1, 1, 1, 0)) }.isFailure)
        val result = journal().confirm(RemoteMigrationReceipt(pending.digest, 1, 1, 1, 0))
        assertTrue(result.confirmed)
        assertEquals(result, journal().load())
        assertEquals(rows, HomeCrewLocalBackup.decode(result.backupJson, setup))
    }
}
