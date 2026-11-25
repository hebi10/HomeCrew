package com.hebi.homecrew.qa

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hebi.homecrew.data.local.ChoreAssignmentMemberEntity
import com.hebi.homecrew.data.local.ChoreDefinitionEntity
import com.hebi.homecrew.data.local.ChoreOccurrenceEntity
import com.hebi.homecrew.data.local.HomeCrewDatabase
import com.hebi.homecrew.data.local.HomeCrewLocalMemberCoordinator
import com.hebi.homecrew.data.local.HomeCrewRoomRepository
import com.hebi.homecrew.data.local.LOCAL_MEMBER_CHANGE_KEY
import com.hebi.homecrew.data.local.LocalMemberChangeRejected
import com.hebi.homecrew.ui.setup.HouseholdSetup
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewLocalMemberRecoveryInstrumentedTest {
    @Test fun renameReferencesAndRecoverSettingsAfterInterruptedCommit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "hc05-member-" + UUID.randomUUID() + ".db"
        var db = Room.databaseBuilder(context, HomeCrewDatabase::class.java, file).build()
        var setup = HouseholdSetup("우리 집", listOf("A", "B", "C"), "A", emptyList())
        var failSave = true
        fun coordinator() = HomeCrewLocalMemberCoordinator(db.chores(), { setup }, { next ->
            if (failSave) false else { setup = next; true }
        })
        try {
            val dao = db.chores()
            val task = ChoreDefinitionEntity("task", "설거지", "주방", "A", "매일", 3, "20:00", "",
                20780, "DAILY", 0, 2, "ROUND_ROBIN", 1000, 2000)
            val history = ChoreOccurrenceEntity("done", "task", 20781, "A", 2, true, 1500, "A")
            val skipped = ChoreOccurrenceEntity("skip", "task", 20782, "B", 3,
                skippedAtEpochMillis = 1700, skipReason = "외출")
            dao.upsertDefinition(task)
            dao.upsertAssignmentMembers(listOf(ChoreAssignmentMemberEntity("task", 0, "A"),
                ChoreAssignmentMemberEntity("task", 1, "B")))
            dao.upsertOccurrences(listOf(history, skipped))
            assertTrue(runCatching { coordinator().change(setup, "A", "가족") }.isFailure)
            assertEquals("A", setup.defaultMember)
            assertNotNull(dao.readState(LOCAL_MEMBER_CHANGE_KEY))
            assertEquals(history.copy(assignedMember = "가족", completedBy = "가족"), dao.findOccurrence("done"))
            assertEquals(skipped, dao.findOccurrence("skip"))
            assertTrue(runCatching { HomeCrewRoomRepository(dao).extendUpcoming(LocalDate.ofEpochDay(20782)) }.isFailure)
            db.close()
            db = Room.databaseBuilder(context, HomeCrewDatabase::class.java, file).build()
            failSave = false
            assertEquals(listOf("가족", "B", "C"), coordinator().recoverAndLoad()?.members)
            assertEquals("가족", setup.defaultMember)
            assertNull(db.chores().readState(LOCAL_MEMBER_CHANGE_KEY))
            assertEquals("가족", db.chores().findDefinition("task")?.defaultOwner)
            assertEquals(listOf("가족", "B"), db.chores().listAssignmentMembers().map { it.memberName })
            assertEquals(setup, coordinator().recoverAndLoad())
            assertTrue(runCatching { coordinator().change(setup, "B", null) }.exceptionOrNull() is LocalMemberChangeRejected)
            assertEquals(listOf("가족", "B"), coordinator().change(setup, "C", null).members)
            assertEquals(history.copy(assignedMember = "가족", completedBy = "가족"), db.chores().findOccurrence("done"))
            assertEquals(skipped, db.chores().findOccurrence("skip"))
        } finally { db.close(); context.deleteDatabase(file) }
    }
}
