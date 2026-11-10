package com.hebi.homecrew.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hebi.homecrew.ui.screens.ChoreAssignment
import com.hebi.homecrew.ui.screens.ChoreAssignmentMode
import com.hebi.homecrew.ui.screens.ChoreEditorDraft
import com.hebi.homecrew.ui.screens.ChoreRepeat
import com.hebi.homecrew.ui.screens.ChoreRepeatMode
import com.hebi.homecrew.ui.setup.HouseholdSetup
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewLocalBackupTest {
    private lateinit var database: HomeCrewDatabase
    private lateinit var repository: HomeCrewRoomRepository
    private val today = LocalDate.of(2026, 11, 10)
    private val household = HouseholdSetup(
        homeName = "테스트 집",
        members = listOf("나", "지민"),
        defaultMember = "나",
        selectedTemplateIds = emptyList()
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, HomeCrewDatabase::class.java).build()
        repository = HomeCrewRoomRepository(database.chores()) { 1234567L }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createTask(): String {
        repository.initializeIfNeeded(household, today)
        return repository.save(
            ChoreEditorDraft(
                name = "백업 대상",
                category = "청소",
                startDate = today,
                repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
                assignment = ChoreAssignment(
                    ChoreAssignmentMode.ROUND_ROBIN,
                    listOf("나", "지민")
                )
            ),
            household.members,
            emptyList(),
            today
        )
    }

    @Test
    fun backupRestoreKeepsCompletedSkippedAndMemberRotation() = runBlocking {
        val id = createTask()
        val initial = repository.snapshot().chores.filter { it.definition.id == id }
        repository.toggleCompleted(initial[0].id, "나")
        repository.toggleSkipped(initial[1].id, "일정 변경")
        val expected = repository.snapshot()
        val backup = repository.exportBackup(household)
        assertTrue(backup.contains("homecrew-local"))
        assertTrue(backup.contains("일정 변경"))

        repository.delete(id)
        assertTrue(repository.snapshot().chores.isEmpty())
        assertTrue(repository.snapshot().definitions.isEmpty())

        repository.restoreBackup(backup, household)
        assertEquals(expected, repository.snapshot())
        val restored = repository.snapshot().chores.filter { it.definition.id == id }
        assertTrue(restored[0].completed)
        assertTrue(restored[1].skipped)
        assertEquals("일정 변경", restored[1].skipReason)
        assertEquals("지민", restored[1].definition.owner)

        repository.delete(id)
        assertTrue(database.chores().listOccurrences().isEmpty())
        assertTrue(database.chores().listAssignmentMembers().isEmpty())
        repository.initializeIfNeeded(household, today)
        assertTrue(repository.snapshot().definitions.isEmpty())
    }

    @Test
    fun invalidBackupDoesNotChangeExistingRows() = runBlocking {
        createTask()
        val expected = repository.snapshot()
        val valid = repository.exportBackup(household)
        val wrongHousehold = JSONObject(valid).put("homeName", "다른 집").toString()
        assertTrue(runCatching {
            repository.restoreBackup(wrongHousehold, household)
        }.isFailure)
        assertEquals(expected, repository.snapshot())

        val duplicate = JSONObject(valid)
        val occurrences = duplicate.getJSONArray("occurrences")
        occurrences.put(occurrences.getJSONObject(0))
        assertTrue(runCatching {
            repository.restoreBackup(duplicate.toString(), household)
        }.isFailure)
        assertEquals(expected, repository.snapshot())

        assertTrue(runCatching {
            repository.restoreBackup("malformed", household)
        }.isFailure)
        assertEquals(expected, repository.snapshot())
    }

    @Test
    fun restoredSkipCanBeCancelledAndNeverCountsAsCompletion() = runBlocking {
        val id = createTask()
        val task = repository.snapshot().chores.first { it.definition.id == id }
        repository.toggleSkipped(task.id, "휴식")
        val backup = repository.exportBackup(household)
        repository.restoreBackup(backup, household)

        val skipped = repository.snapshot().chores.single { it.id == task.id }
        assertTrue(skipped.skipped)
        assertFalse(skipped.completed)
        assertEquals(1234567L, skipped.skippedAtEpochMillis)
        repository.toggleSkipped(task.id, null)
        val cleared = repository.snapshot().chores.single { it.id == task.id }
        assertFalse(cleared.skipped)
        assertNull(cleared.skipReason)
    }
}
