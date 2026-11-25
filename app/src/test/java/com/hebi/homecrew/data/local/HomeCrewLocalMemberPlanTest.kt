package com.hebi.homecrew.data.local

import com.hebi.homecrew.ui.setup.HouseholdSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeCrewLocalMemberPlanTest {
    private val setup = HouseholdSetup("우리 집", listOf("A", "B", "C"), "A", emptyList())
    private val rows = HomeCrewRows(
        emptyList(), listOf(ChoreAssignmentMemberEntity("task", 0, "A")),
        listOf(ChoreOccurrenceEntity("row", "task", 20780, "B", 2, true, 1000, "과거 구성원"))
    )
    @Test fun renameChangesOnlyExactMemberAndDefault() {
        val plan = planLocalMemberChange(setup, rows, "A", "가족")
        assertEquals(listOf("가족", "B", "C"), plan.after.members)
        assertEquals("가족", plan.after.defaultMember)
        assertThrows(LocalMemberChangeRejected::class.java) { planLocalMemberChange(setup, rows, "A", "b") }
        assertThrows(LocalMemberChangeRejected::class.java) { planLocalMemberChange(setup, rows, "A", "과거 구성원") }
    }
    @Test fun onlyUnreferencedMembersCanBeRemoved() {
        assertThrows(LocalMemberChangeRejected::class.java) { planLocalMemberChange(setup, rows, "A", null) }
        assertThrows(LocalMemberChangeRejected::class.java) { planLocalMemberChange(setup, rows, "B", null) }
        assertEquals(listOf("A", "B"), planLocalMemberChange(setup, rows, "C", null).after.members)
        val empty = HomeCrewRows(emptyList(), emptyList(), emptyList())
        assertEquals("B", planLocalMemberChange(setup, empty, "A", null).after.defaultMember)
        assertThrows(LocalMemberChangeRejected::class.java) {
            planLocalMemberChange(setup.copy(members = listOf("A")), empty, "A", null)
        }
    }
}
