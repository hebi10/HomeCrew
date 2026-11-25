package com.hebi.homecrew.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeCrewMemberManagementTest {
    @Test fun displayNameIsNormalizedAndDoesNotExposeUid() {
        assertEquals("가족", normalizeSharedMemberName(" 가족 "))
        assertEquals("é", normalizeSharedMemberName("e\u0301"))
        assertEquals("가족 (나)", sharedMemberLabel(RemoteHouseholdMember("uid", false, "가족"), 2, "uid"))
        assertEquals("구성원 2", sharedMemberLabel(RemoteHouseholdMember("uid", false), 2, "other"))
        assertThrows(IllegalArgumentException::class.java) { normalizeSharedMemberName("A\nB") }
        assertThrows(IllegalArgumentException::class.java) { normalizeSharedMemberName("a".repeat(13)) }
        assertThrows(IllegalArgumentException::class.java) { normalizeSharedMemberName(" ") }
    }

    @Test fun rosterRequiresUniqueMembersAndExactlyOneOwner() {
        val owner = RemoteHouseholdMember("owner", true)
        val member = RemoteHouseholdMember("member", false)
        val digest = "a".repeat(64)
        assertEquals(2, SharedHouseholdRoster(listOf(owner, member), digest, false).members.size)
        assertThrows(IllegalArgumentException::class.java) { SharedHouseholdRoster(listOf(owner, owner), digest, false) }
        assertThrows(IllegalArgumentException::class.java) { SharedHouseholdRoster(listOf(member), digest, false) }
    }
}
