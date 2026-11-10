package com.hebi.homecrew.ui.setup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdSetupTest {
    @Test
    fun validatesHouseNameAndDuplicateMembers() {
        assertEquals("집 이름을 입력해 주세요.", houseNameError("  "))
        assertNull(houseNameError("우리 집"))
        assertEquals("같은 이름의 구성원이 이미 있습니다.", memberNameError("민수", listOf("민수")))
        assertNull(memberNameError("지민", listOf("나", "지민"), editingIndex = 1))
        assertEquals("구성원 이름은 12자 이내로 입력해 주세요.", memberNameError("1234567890123", emptyList()))
    }

    @Test
    fun rejectsMissingMemberAndInvalidTemplate() {
        val valid = HouseholdSetup(
            homeName = "우리 집",
            members = listOf("나", "지민"),
            defaultMember = "나",
            selectedTemplateIds = listOf("dishes", "recycle")
        )
        assertTrue(validateHouseholdSetup(valid))
        assertFalse(validateHouseholdSetup(valid.copy(members = emptyList())))
        assertFalse(validateHouseholdSetup(valid.copy(members = listOf("나", "나"))))
        assertFalse(validateHouseholdSetup(valid.copy(defaultMember = "없음")))
        assertFalse(validateHouseholdSetup(valid.copy(selectedTemplateIds = listOf("unlisted"))))
        assertTrue(validateHouseholdSetup(valid.copy(selectedTemplateIds = emptyList())))
    }

    @Test
    fun createsOnlySelectedDailyDemoInstances() {
        val setup = HouseholdSetup(
            homeName = "우리 집",
            members = listOf("나", "지민"),
            defaultMember = "지민",
            selectedTemplateIds = listOf("dishes", "petwalk")
        )
        val date = LocalDate.of(2026, 11, 10)
        val chores = previewChoresForSetup(setup, date)
        assertEquals(listOf("dishes", "petwalk"), chores.map { it.definition.id })
        assertEquals(listOf("나", "지민"), chores.map { it.definition.owner })
        assertTrue(chores.all { !it.completed && it.date == date })
        assertTrue(previewChoresForSetup(setup.copy(selectedTemplateIds = emptyList()), date).isEmpty())
    }
}
