package com.hebi.homecrew.data.cloud

import com.hebi.homecrew.data.local.ChoreAssignmentMemberEntity
import com.hebi.homecrew.data.local.ChoreOccurrenceEntity
import com.hebi.homecrew.data.local.HomeCrewRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewCloudMigrationPreviewTest {
    private val rows = HomeCrewRows(
        definitions = emptyList(),
        members = listOf(
            ChoreAssignmentMemberEntity("chore-1", 0, "해비"),
            ChoreAssignmentMemberEntity("chore-1", 1, "가족")
        ),
        occurrences = listOf(
            ChoreOccurrenceEntity(
                id = "chore-1-1", definitionId = "chore-1", scheduledEpochDay = 1,
                assignedMember = "가족", completed = true, completedBy = "해비"
            )
        )
    )

    @Test fun unmappedNamesBlockMigrationWithoutChangingRoomRows() {
        val preview = previewCloudMigration(rows, mapOf("해비" to "firebase-uid-1"))
        assertFalse(preview.canProceed)
        assertEquals(listOf("가족"), preview.unmappedNames)
        assertEquals(1, preview.occurrences)
    }

    @Test fun sharedFirebaseAccountIsNotAnImplicitMergeOfTwoLocalMembers() {
        val preview = previewCloudMigration(
            rows, mapOf("해비" to "same-uid", "가족" to "same-uid")
        )
        assertFalse(preview.canProceed)
        assertFalse(preview.mappingOneToOne)
    }

    @Test fun explicitDistinctMemberMappingProducesAReadOnlyMigrationPreview() {
        val preview = previewCloudMigration(
            rows, mapOf("해비" to "uid-1", "가족" to "uid-2")
        )
        assertTrue(preview.canProceed)
        assertEquals(0, preview.definitions)
        assertEquals(1, preview.occurrences)
    }
}
