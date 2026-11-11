package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.ui.model.ChoreStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewChoreListFiltersTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val members = listOf("나", "지민")

    private fun definition(
        id: String,
        name: String,
        category: String = "청소",
        owner: String = "나",
        repeat: ChoreRepeat = ChoreRepeat(),
        points: Int = 2,
        time: String = "20:00",
        assignment: ChoreAssignment? = null
    ) = ChoreDefinition(
        id = id,
        name = name,
        category = category,
        owner = owner,
        repeat = repeatDescription(repeat),
        points = points,
        time = time,
        recurrence = repeat,
        assignment = assignment,
        startDate = today
    )

    @Test
    fun searchCategoryStatusMemberAndRepeatCompose() {
        val dish = definition(
            id = "dish", name = "설거지", category = "주방",
            repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
            assignment = ChoreAssignment(ChoreAssignmentMode.ROUND_ROBIN, members)
        )
        val laundry = definition("laundry", "빨래", category = "세탁", owner = "지민")
        val all = listOf(dish, laundry)
        val actual = filterChoreList(
            definitions = all,
            instances = listOf(
                DemoChore("dish-today", dish.copy(owner = "지민"), today),
                DemoChore("laundry-today", laundry, today, completed = true)
            ),
            today = today,
            selection = ChoreListSelection(
                query = " 설 ",
                category = "주방",
                status = "예정",
                member = "나",
                repeat = ChoreListRepeatFilter.RECURRING
            )
        )

        assertEquals(listOf("dish"), actual.map { it.definition.id })
        assertEquals(listOf("나", "지민"), actual.single().participants)
        assertEquals(ChoreStatus.PLANNED, actual.single().todayStatus)
    }

    @Test
    fun overdueFilterFindsPastIncompleteOccurrencesEvenIfTodayCompleted() {
        val dish = definition("dish", "설거지", repeat = ChoreRepeat(ChoreRepeatMode.DAILY))
        val chores = listOf(
            DemoChore("old-1", dish, today.minusDays(3)),
            DemoChore("old-2", dish, today.minusDays(1)),
            DemoChore("done-today", dish, today, completed = true)
        )
        val overdue = filterChoreList(
            listOf(dish), chores, today,
            ChoreListSelection(status = "기한 초과")
        ).single()
        assertEquals(2, overdue.overdueCount)
        assertEquals("old-2", overdue.occurrenceForDetails("기한 초과"))
        assertEquals(ChoreStatus.OVERDUE, overdue.statusForDisplay("기한 초과"))
        assertEquals(ChoreStatus.COMPLETED, overdue.statusForDisplay("전체"))
        assertEquals(
            listOf("dish"),
            filterChoreList(listOf(dish), chores, today, ChoreListSelection(status = "완료"))
                .map { it.definition.id }
        )
    }

    @Test
    fun oneTimeAndRecurringFiltersHandleDefinitionsWithoutTodayOccurrence() {
        val oneTime = definition("future", "먼지 정리")
        val daily = definition("daily", "분리수거", repeat = ChoreRepeat(ChoreRepeatMode.DAILY))
        val chores = listOf(DemoChore("future-next", oneTime, today.plusDays(7)))
        val entry = filterChoreList(
            listOf(oneTime, daily), chores, today,
            ChoreListSelection(repeat = ChoreListRepeatFilter.ONE_TIME)
        ).single()

        assertEquals(oneTime.id, entry.definition.id)
        assertNull(entry.todayOccurrence)
        assertNull(entry.todayStatus)
        assertNull(entry.statusForDisplay("전체"))
        assertEquals("future-next", entry.occurrenceForDetails("전체"))
        assertEquals(today.plusDays(7), entry.nextOccurrence?.date)
        assertEquals(
            listOf("daily"),
            filterChoreList(
                listOf(oneTime, daily), chores, today,
                ChoreListSelection(repeat = ChoreListRepeatFilter.RECURRING)
            ).map { it.definition.id }
        )
    }

    @Test
    fun memberFilterUsesEligibleParticipantsRatherThanOnlyTodaysOwner() {
        val rotating = definition(
            id = "rotation", name = "순번 설거지", owner = "나",
            repeat = ChoreRepeat(ChoreRepeatMode.DAILY),
            assignment = ChoreAssignment(ChoreAssignmentMode.ROUND_ROBIN, members)
        )
        val todayChore = DemoChore("rotation-today", rotating.copy(owner = "지민"), today)
        val result = filterChoreList(
            listOf(rotating), listOf(todayChore), today,
            ChoreListSelection(member = "나")
        )
        assertEquals(1, result.size)
        assertEquals("지민", result.single().todayOccurrence?.definition?.owner)
    }

    @Test
    fun sortsByNameTimeAndWorkloadWithoutChangingDefinitions() {
        val alpha = definition("alpha", "가", time = "21:00", points = 1)
        val beta = definition("beta", "나", time = "09:00", points = 3)
        val gamma = definition("gamma", "다", time = "14:00", points = 2)
        val all = listOf(gamma, beta, alpha)
        val names = filterChoreList(all, emptyList(), today, ChoreListSelection())
        val times = filterChoreList(
            all, emptyList(), today, ChoreListSelection(sort = ChoreListSort.TIME)
        )
        val workloads = filterChoreList(
            all, emptyList(), today, ChoreListSelection(sort = ChoreListSort.WORKLOAD)
        )
        assertEquals(listOf("alpha", "beta", "gamma"), names.map { it.definition.id })
        assertEquals(listOf("beta", "gamma", "alpha"), times.map { it.definition.id })
        assertEquals(listOf("beta", "gamma", "alpha"), workloads.map { it.definition.id })
        assertEquals(listOf("gamma", "beta", "alpha"), all.map { it.id })
    }

    @Test
    fun distinguishesFirstUseFromFilteredOutResultsAndResetState() {
        val dish = definition("dish", "설거지")
        assertTrue(filterChoreList(emptyList(), emptyList(), today, ChoreListSelection()).isEmpty())
        assertTrue(
            filterChoreList(
                listOf(dish), emptyList(), today,
                ChoreListSelection(query = "존재하지 않는 항목")
            ).isEmpty()
        )
        assertFalse(ChoreListSelection().hasFilters)
        assertTrue(ChoreListSelection(member = "지민").hasFilters)
        assertTrue(ChoreListSelection(sort = ChoreListSort.TIME).hasFilters)
        assertFalse(isRecurringChore(dish, today))
    }
}
