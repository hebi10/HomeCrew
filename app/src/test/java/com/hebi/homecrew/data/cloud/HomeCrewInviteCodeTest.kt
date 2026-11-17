package com.hebi.homecrew.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeCrewInviteCodeTest {
    @Test fun inputSupportsLowercaseAndDashGroups() {
        assertEquals("1234ABCDEF012345", normalizeHomeCrewInviteCode(" 1234-abcd-ef01-2345 "))
    }
    @Test fun malformedOrShortCodeIsRejected() {
        listOf("", "AAA", "../../secret", "ABCDEFGHIJKLMNOP").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                normalizeHomeCrewInviteCode(value)
            }
        }
    }
}
