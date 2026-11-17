package com.hebi.homecrew.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeCrewCloudContractsTest {
    @Test fun namesAreNormalizedButLocalNamesAreNeverAutomaticallyUploaded() {
        assertEquals("우리 집", normalizeCloudHouseholdName("  우리 집  "))
        assertEquals(
            "우리 집",
            CloudHousehold("house-1", "uid-1", "우리 집").name
        )
    }

    @Test fun emptyOrTooLongHouseholdNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCloudHouseholdName("  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCloudHouseholdName("a".repeat(81))
        }
    }

    @Test fun firestoreDocumentIdsCannotEscapeTheirCollection() {
        listOf("", "../victim", ".", "..", "a/b").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) {
                requireValidCloudId(id)
            }
        }
    }
}
