package com.hebi.homecrew.ui.components

import com.hebi.homecrew.ui.model.ChoreStatus
import com.hebi.homecrew.ui.model.completionPercent
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCrewStatePresentationTest {
    @Test
    fun noScheduledChoresDoesNotClaimZeroPercentCompleted() {
        assertEquals("등록된 집안일 없음", homeProgressLabel(0, 0))
        assertEquals(0, completionPercent(0, 0))
    }

    @Test
    fun progressSummaryDistinguishesEmptyPendingPartialAndComplete() {
        assertEquals("0% 완료", homeProgressLabel(0, 4))
        assertEquals("25% 완료", homeProgressLabel(1, 4))
        assertEquals("모두 완료", homeProgressLabel(4, 4))
        assertEquals("모두 완료", homeProgressLabel(7, 4))
    }

    @Test
    fun taskStatusLabelsRemainExplicitAndReadable() {
        assertEquals("예정", ChoreStatus.PLANNED.label)
        assertEquals("완료", ChoreStatus.COMPLETED.label)
        assertEquals("기한 초과", ChoreStatus.OVERDUE.label)
    }
}
