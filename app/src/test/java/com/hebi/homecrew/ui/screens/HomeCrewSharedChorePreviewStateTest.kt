package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.cloud.RemoteChore
import com.hebi.homecrew.data.cloud.RemoteHouseholdReadGateway
import com.hebi.homecrew.data.cloud.RemoteOccurrence
import com.hebi.homecrew.data.cloud.RemoteSnapshot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeCrewSharedChorePreviewStateTest {
    private val day = LocalDate.of(2026, 11, 24)
    private val chore = RemoteChore("chore", "설거지", "주방", 2, "owner", 1L)
    private fun snapshot(stale: Boolean = false) = RemoteSnapshot(
        chores = listOf(chore),
        occurrences = listOf(
            RemoteOccurrence("a", "chore", day.toEpochDay(), "owner", false, null, 1L),
            RemoteOccurrence("b", "chore", day.toEpochDay(), "other", true, "other", 2L),
            RemoteOccurrence("c", "chore", day.toEpochDay(), "owner", false, null, 3L, skipped = true),
            RemoteOccurrence("d", "missing", day.toEpochDay(), "owner", false, null, 1L),
            RemoteOccurrence("future", "chore", day.plusDays(1).toEpochDay(), "owner", false, null, 1L)
        ),
        mayBeStale = stale
    )

    private fun gateway(stream: Flow<RemoteSnapshot>) = object : RemoteHouseholdReadGateway {
        override fun observe(householdId: String): Flow<RemoteSnapshot> = stream
    }

    @Test fun onlyServerVerifiedSnapshotsExposeRecords() = runBlocking {
        val live = snapshot()
        val states = observeSharedChorePreview(
            gateway(flowOf(snapshot(true), live, snapshot(true))), "house"
        ).toList()
        assertEquals(listOf(
            SharedChorePreviewState.Loading,
            SharedChorePreviewState.Offline,
            SharedChorePreviewState.Ready(live),
            SharedChorePreviewState.Offline
        ), states)
    }

    @Test fun permissionLossDropsPreviouslyVisibleSnapshot() = runBlocking {
        val live = snapshot()
        val states = observeSharedChorePreview(gateway(flow {
            emit(live)
            error("Permission denied")
        }), "house").toList()
        assertEquals(listOf(
            SharedChorePreviewState.Loading,
            SharedChorePreviewState.Ready(live),
            SharedChorePreviewState.Unavailable
        ), states)
    }

    @Test fun dayRowsUseOccurrenceAssigneeAndDoNotInventMissingNames() {
        val result = sharedChoreDay(snapshot(), day, "owner")
        assertEquals(1, result.missingDefinitionCount)
        assertEquals(listOf("a", "b", "c"), result.rows.map { it.occurrenceId })
        assertEquals(listOf(true, false, true), result.rows.map { it.assignedToMe })
        assertEquals(
            listOf(SharedChoreStatus.PENDING, SharedChoreStatus.COMPLETED, SharedChoreStatus.SKIPPED),
            result.rows.map { it.status }
        )
        assertEquals(listOf("future"), sharedChoreDay(snapshot(), day.plusDays(1), "owner").rows.map { it.occurrenceId })
        assertThrows(IllegalArgumentException::class.java) { sharedChoreDay(snapshot(true), day, "owner") }
    }
}
