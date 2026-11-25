package com.hebi.homecrew.data.cloud

import com.hebi.homecrew.data.local.ChoreAssignmentMemberEntity
import com.hebi.homecrew.data.local.ChoreDefinitionEntity
import com.hebi.homecrew.data.local.ChoreOccurrenceEntity
import com.hebi.homecrew.data.local.HomeCrewRows
import com.hebi.homecrew.ui.setup.HouseholdSetup
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewCloudMigrationManifestTest {
    private val day = LocalDate.of(2026, 11, 25).toEpochDay()
    private val definition = ChoreDefinitionEntity(
        "local-task", "설거지", "주방", "A", "매주", 3, "19:20", "원본 설명",
        day, "WEEKLY", 5, 2, "ROUND_ROBIN", 1000, 2000
    )
    private val assignments = listOf(
        ChoreAssignmentMemberEntity(definition.id, 0, "A"),
        ChoreAssignmentMemberEntity(definition.id, 1, "B")
    )
    private val completed = ChoreOccurrenceEntity(
        "local-completed", definition.id, day - 1, "B", 2, true, 1500, "A"
    )
    private val skipped = ChoreOccurrenceEntity(
        "local-skipped", definition.id, day, "A", 0,
        skippedAtEpochMillis = 1700, skipReason = "외출"
    )
    private val source = LocalMigrationSource(
        HouseholdSetup("우리 집", listOf("A", "B"), "A", emptyList()),
        HomeCrewRows(listOf(definition), assignments, listOf(completed, skipped))
    )
    private val target = RemoteSnapshot(emptyList(), emptyList(), false,
        listOf(RemoteHouseholdMember("uid-a", true), RemoteHouseholdMember("uid-b", false)))
    private val mapping = mapOf("A" to "uid-a", "B" to "uid-b")
    private fun plan(local: LocalMigrationSource = source, cloud: RemoteSnapshot = target,
        people: Map<String, String> = mapping, uid: String = "uid-a") =
        prepareLocalMigration(local, "house", uid, cloud, people)

    @Test fun manifestPreservesHistoryTimesRulesAndStableCloudIds() {
        val result = plan()
        assertTrue(result.blockers.isEmpty())
        val manifest = requireNotNull(result.manifest)
        assertEquals(1, result.completedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(1, manifest.legacyWorkloadCount)
        val task = manifest.definitions.single()
        assertEquals("19:20", task["scheduledTime"])
        assertEquals(1000L, task["sourceCreatedAtEpochMillis"])
        assertEquals(listOf("uid-a", "uid-b"), (task["schedule"] as Map<*, *>)["memberUids"])
        val first = manifest.occurrences.first()
        assertEquals("uid-a", first["completedByUid"])
        assertEquals(1500L, first["completedAtEpochMillis"])
        assertEquals(2, first["workloadPoints"])
        val second = manifest.occurrences.last()
        assertEquals("외출", second["skipReason"])
        assertEquals(1700L, second["skippedAtEpochMillis"])
        assertEquals(0, second["sourceWorkloadPoints"])
        assertEquals(3, second["workloadPoints"])
        assertEquals(migratedOccurrenceId(task["id"] as String, day), second["id"])
        assertEquals(64, manifest.digest.length)
        val reversed = source.copy(rows = source.rows.copy(members = assignments.reversed(),
            occurrences = source.rows.occurrences.reversed()))
        assertEquals(manifest.digest, plan(reversed).manifest?.digest)
        val modified = source.copy(rows = source.rows.copy(occurrences = listOf(completed.copy(completedAtEpochMillis = 1501), skipped)))
        assertNotEquals(manifest.digest, plan(modified).manifest?.digest)
    }

    @Test fun invalidMappingsPermissionsExistingDataAndBrokenRowsBlockEntireManifest() {
        assertNull(plan(people = mapOf("A" to "uid-a", "B" to "uid-a")).manifest)
        assertNull(plan(people = mapOf("A" to "uid-a", "B" to "outsider")).manifest)
        assertNull(plan(uid = "uid-b").manifest)
        assertNull(plan(cloud = target.copy(mayBeStale = true)).manifest)
        assertNull(plan(cloud = target.copy(chores = listOf(RemoteChore("exists", "기존", "주방", 1, "uid-a", 1)))).manifest)
        assertNull(plan(local = source.copy(rows = source.rows.copy(occurrences = listOf(completed, completed.copy(id = "duplicate"))))).manifest)
        assertNull(plan(local = source.copy(rows = source.rows.copy(definitions = listOf(definition.copy(startEpochDay = null))))).manifest)
        assertNotNull(plan().manifest)
        assertFalse(plan(people = emptyMap()).blockers.isEmpty())
    }
}
