package com.hebi.homecrew.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewChoreTest {
    @Test
    fun `four out of seven is rounded to fifty seven percent`() {
        assertEquals(57, completionPercent(4, 7))
    }

    @Test
    fun `zero or negative total is safe`() {
        assertEquals(0, completionPercent(0, 0))
        assertEquals(0, completionPercent(10, -1))
    }

    @Test
    fun `progress never exceeds zero to one hundred percent`() {
        assertEquals(0, completionPercent(-3, 7))
        assertEquals(100, completionPercent(10, 7))
    }
}
