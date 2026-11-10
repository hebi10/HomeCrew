package com.hebi.homecrew.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCrewDatabaseTest {
    private lateinit var database: HomeCrewDatabase
    private lateinit var dao: HomeCrewDao

    private val today = LocalDate.of(2026, 11, 10).toEpochDay()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, HomeCrewDatabase::class.java).build()
        dao = database.chores()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun definition(name: String = "설거지") = ChoreDefinitionEntity(
        id = "dishes",
        name = name,
        category = "주방",
        defaultOwner = "나",
        repeatLabel = "매일",
        points = 2,
        scheduledTime = "20:00",
        description = "",
        startEpochDay = today,
        repeatMode = "DAILY",
        weekdayMask = 0,
        repeatIntervalDays = 2,
        assignmentMode = "ROUND_ROBIN",
        createdAtEpochMillis = 100,
        updatedAtEpochMillis = 100
    )

    private fun occurrence(day: Long, owner: String) = ChoreOccurrenceEntity(
        id = "dishes-$day",
        definitionId = "dishes",
        scheduledEpochDay = day,
        assignedMember = owner
    )

    @Test
    fun savesTasksAssignmentsAndCompletionAcrossQueries() = runBlocking {
        val members = listOf(
            ChoreAssignmentMemberEntity("dishes", 0, "나"),
            ChoreAssignmentMemberEntity("dishes", 1, "지민")
        )
        dao.saveSchedule(
            definition(),
            members,
            listOf(occurrence(today, "나"), occurrence(today + 1, "지민")),
            today
        )

        assertEquals("설거지", dao.findDefinition("dishes")?.name)
        assertEquals(listOf("나", "지민"), dao.observeAssignmentMembers().first().map { it.memberName })
        assertEquals(2, dao.observeOccurrences().first().size)

        assertEquals(1, dao.setCompleted("dishes-$today", true, 12345, "나"))
        val first = dao.observeOccurrences().first().first { it.scheduledEpochDay == today }
        assertTrue(first.completed)
        assertEquals("나", first.completedBy)
        assertEquals(12345L, first.completedAtEpochMillis)
    }

    @Test
    fun reschedulingPreservesCompletedHistoryAndDeleteCascades() = runBlocking {
        val members = listOf(ChoreAssignmentMemberEntity("dishes", 0, "나"))
        dao.saveSchedule(
            definition(), members,
            listOf(occurrence(today, "나"), occurrence(today + 1, "나")),
            today
        )
        dao.setCompleted("dishes-$today", true, 12345, "나")

        dao.saveSchedule(
            definition("설거지 변경"),
            members,
            listOf(occurrence(today, "지민"), occurrence(today + 2, "나")),
            today
        )

        val rows = dao.observeOccurrences().first()
        assertEquals(listOf(today, today + 2), rows.map { it.scheduledEpochDay })
        assertTrue(rows.single { it.scheduledEpochDay == today }.completed)
        assertEquals("나", rows.single { it.scheduledEpochDay == today }.assignedMember)
        assertFalse(rows.single { it.scheduledEpochDay == today + 2 }.completed)
        assertEquals("설거지 변경", dao.findDefinition("dishes")?.name)

        assertEquals(1, dao.deleteDefinition("dishes"))
        assertNull(dao.findDefinition("dishes"))
        assertTrue(dao.observeAssignmentMembers().first().isEmpty())
        assertTrue(dao.observeOccurrences().first().isEmpty())
    }
}
