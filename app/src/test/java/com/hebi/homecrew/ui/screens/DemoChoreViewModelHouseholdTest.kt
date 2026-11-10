package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.ui.setup.HouseholdSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoChoreViewModelHouseholdTest {
    private val setup = HouseholdSetup(
        homeName = "우리 집",
        members = listOf("나", "지민"),
        defaultMember = "나",
        selectedTemplateIds = listOf("dishes", "recycle")
    )

    @Test
    fun completionSurvivesRecomposingWithSameHousehold() {
        val model = DemoChoreViewModel()
        model.configureHousehold(setup)
        assertEquals(2, model.chores.value.size)
        val first = model.chores.value.first()
        model.toggleCompleted(first.id)
        assertTrue(model.chores.value.first().completed)

        model.configureHousehold(setup)
        assertTrue(model.chores.value.first().completed)
    }

    @Test
    fun newSetupReinitializesDemoTasksWithoutPersistingCompletions() {
        val model = DemoChoreViewModel()
        model.configureHousehold(setup)
        val first = model.chores.value.first()
        model.toggleCompleted(first.id)
        val different = setup.copy(selectedTemplateIds = listOf("plants"))
        model.configureHousehold(different)
        assertEquals(listOf("plants"), model.chores.value.map { it.definition.id })
        assertFalse(model.chores.value.single().completed)
    }
}
