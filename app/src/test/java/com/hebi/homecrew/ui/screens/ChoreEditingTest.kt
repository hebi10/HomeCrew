package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.ui.setup.HouseholdSetup
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoreEditingTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val members = listOf("나", "지민", "민수")

    private fun draft() = ChoreEditorDraft(
        name = "설거지",
        category = "주방",
        startDate = today,
        assignment = ChoreAssignment(members = listOf("나"))
    )

    @Test
    fun monthlyRecurrenceAdjustsEndOfMonthAndLeapYear() {
        val start = LocalDate.of(2028, 1, 31)
        val dates = nextChoreDates(
            start, ChoreRepeat(ChoreRepeatMode.MONTHLY),
            start, LocalDate.of(2028, 4, 30)
        )
        assertEquals(
            listOf(
                LocalDate.of(2028, 1, 31),
                LocalDate.of(2028, 2, 29),
                LocalDate.of(2028, 3, 31),
                LocalDate.of(2028, 4, 30)
            ), dates
        )
    }

    @Test
    fun weeklyAndBiweeklyHonorWeekdayAndAnchor() {
        val weekly = ChoreRepeat(ChoreRepeatMode.WEEKLY, setOf(DayOfWeek.TUESDAY))
        val biweekly = weekly.copy(mode = ChoreRepeatMode.BIWEEKLY)
        assertEquals(
            listOf(today, today.plusDays(7), today.plusDays(14)),
            nextChoreDates(today, weekly, today, today.plusDays(14))
        )
        assertEquals(
            listOf(today, today.plusDays(14)),
            nextChoreDates(today, biweekly, today, today.plusDays(14))
        )
    }

    @Test
    fun nonRepeatingAndCustomIntervalsAreDeterministic() {
        assertEquals(
            listOf(today),
            nextChoreDates(today, ChoreRepeat(), today, today.plusDays(30))
        )
        assertEquals(
            listOf(today, today.plusDays(3), today.plusDays(6)),
            nextChoreDates(
                today, ChoreRepeat(ChoreRepeatMode.CUSTOM, intervalDays = 3),
                today, today.plusDays(8)
            )
        )
    }

    @Test
    fun ownerRotationAndWeightedBalanceBehaveDeterministically() {
        val roundRobin = ChoreAssignment(ChoreAssignmentMode.ROUND_ROBIN, members)
        assertEquals(
            listOf("나", "지민", "민수", "나"),
            assignedOwners(roundRobin, 4, 3)
        )
        val balanced = ChoreAssignment(ChoreAssignmentMode.BALANCED, listOf("나", "지민"))
        assertEquals(
            listOf("지민", "지민", "나"),
            assignedOwners(balanced, 3, 2, mapOf("나" to 4, "지민" to 0))
        )
    }

    @Test
    fun validatesRequiredFieldsRepeatAndKnownMembers() {
        assertNull(validationError(draft(), members))
        assertNotNull(validationError(draft().copy(name = "  "), members))
        assertNotNull(validationError(draft().copy(points = 7), members))
        assertNotNull(validationError(draft().copy(time = "31:90"), members))
        assertNotNull(validationError(draft().copy(description = "a".repeat(301)), members))
        assertNotNull(validationError(draft().copy(
            repeat = ChoreRepeat(ChoreRepeatMode.WEEKLY)
        ), members))
        assertNotNull(validationError(draft().copy(
            assignment = ChoreAssignment(members = listOf("없는 사람"))
        ), members))
        assertNotNull(validationError(draft().copy(
            assignment = ChoreAssignment(members = emptyList())
        ), members))
    }

    @Test
    fun roundRobinUsesDisplayedMemberOrderNotCheckboxClickOrder() {
        val selectedByClick = listOf("민수", "지민")
        val ordered = orderedParticipants(selectedByClick, members)
        assertEquals(listOf("지민", "민수"), ordered)
        assertEquals(
            listOf("지민", "민수", "지민"),
            assignedOwners(ChoreAssignment(ChoreAssignmentMode.ROUND_ROBIN, ordered), 3, 2)
        )

        val model = DemoChoreViewModel()
        model.configureHousehold(HouseholdSetup("우리 집", members, "나", emptyList()))
        model.openEditor(null, members)
        model.editDraft {
            it.copy(
                name = "순번 배정",
                repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
                assignment = ChoreAssignment(ChoreAssignmentMode.ROUND_ROBIN, selectedByClick)
            )
        }
        assertNotNull(model.saveEditor(members))
        assertEquals(
            listOf("지민", "민수", "지민"),
            (0L..2L).map { days ->
                tasksForDay(model.chores.value, model.today.plusDays(days))
                    .single().definition.owner
            }
        )
    }

    @Test
    fun viewModelCreatesEditsAndDeletesSessionChore() {
        val model = DemoChoreViewModel()
        model.configureHousehold(HouseholdSetup("우리 집", members, "나", emptyList()))
        assertTrue(model.definitions.value.isEmpty())

        model.openEditor(null, members)
        model.editDraft {
            it.copy(name = "테스트 집안일", category = "청소",
                repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
                assignment = ChoreAssignment(
                    ChoreAssignmentMode.ROUND_ROBIN, listOf("나", "지민")
                ))
        }
        val id = model.saveEditor(members)
        assertNotNull(id)
        val actualId = requireNotNull(id)
        assertEquals(listOf("테스트 집안일"), model.definitions.value.map { it.name })
        assertEquals("나", tasksForDay(model.chores.value, model.today).single().definition.owner)
        assertEquals(
            "지민", tasksForDay(model.chores.value, model.today.plusDays(1)).single().definition.owner
        )

        val completedId = tasksForDay(model.chores.value, model.today).single().id
        model.toggleCompleted(completedId)
        model.openEditor(model.definitions.value.single(), members)
        model.editDraft { it.copy(name = "이름 수정") }
        assertEquals(actualId, model.saveEditor(members))
        assertEquals(1, model.chores.value.count { it.id == completedId })
        assertTrue(model.chores.value.single { it.id == completedId }.completed)
        assertEquals(
            "이름 수정",
            tasksForDay(model.chores.value, model.today.plusDays(1)).single().definition.name
        )

        model.deleteDefinition(actualId)
        assertTrue(model.definitions.value.isEmpty())
        assertFalse(model.chores.value.any { it.definition.id == actualId })
    }
}
