package com.hebi.homecrew.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeCrewSharedHouseholdGatewayTest {
    private val request = "6dfdfbc0-3cc0-4db9-8cb3-1eb24cfc9c10"

    @Test fun creationRequestPreservesRetryIdAndValidatesName() {
        val first = sharedHouseholdCreateRequest(" 우리 집 ", request)
        assertEquals("우리 집", first.name)
        assertEquals(request, first.requestId)
        assertEquals(first, sharedHouseholdCreateRequest("우리 집", request))
        assertThrows(IllegalArgumentException::class.java) {
            sharedHouseholdCreateRequest("", request)
        }
        assertThrows(IllegalArgumentException::class.java) {
            sharedHouseholdCreateRequest("우리 집", "../invalid")
        }
    }

    @Test fun previewRequiresExplicitNonProductionProjectAndOptIn() {
        val testProject = "demo-homecrew-shared-ci"
        assertTrue(canUseSharedHouseholdPreview(true, testProject, testProject))
        assertFalse(canUseSharedHouseholdPreview(false, testProject, testProject))
        assertFalse(canUseSharedHouseholdPreview(true, testProject, ""))
        assertFalse(canUseSharedHouseholdPreview(true, "homecrew-1b14b", "homecrew-1b14b"))
        assertFalse(canUseSharedHouseholdPreview(true, "homecrew-1b14b", testProject))
        assertFalse(canUseSharedHouseholdPreview(true, null, testProject))
    }

    @Test fun householdSummaryRejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException::class.java) {
            SharedHouseholdSummary("house-1", "우리 집", 9, true)
        }
    }
}
