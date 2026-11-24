package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.cloud.RemoteChore
import com.hebi.homecrew.data.cloud.RemoteCompletionResult
import com.hebi.homecrew.data.cloud.RemoteOccurrence
import com.hebi.homecrew.data.cloud.RemoteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCrewSharedCompletionStateTest {
    private val id = "00000000-0000-4000-8000-000000000001"

    private fun ready(
        completed: Boolean = false,
        revision: Long = 1,
        skipped: Boolean = false,
        stale: Boolean = false,
        hasDefinition: Boolean = true
    ) = SharedChorePreviewState.Ready(RemoteSnapshot(
        chores = if (hasDefinition) listOf(RemoteChore("definition", "설거지", "주방", 2, "uid", 1)) else emptyList(),
        occurrences = listOf(RemoteOccurrence("occurrence", "definition", 20000, "uid", completed,
            if (completed) "uid" else null, revision, skipped)),
        mayBeStale = stale
    ))

    private fun command(preview: SharedChorePreviewState = ready()) = requireNotNull(
        newSharedCompletionCommand(preview, SharedCompletionState.Idle, "occurrence", id)
    )

    @Test fun requestsUseServerRevisionAndExplicitTargetState() {
        val start = command()
        assertTrue(start.completed)
        assertEquals(1L, start.expectedRevision)
        assertEquals(id, start.requestId)
        val undo = command(ready(completed = true, revision = 5))
        assertFalse(undo.completed)
        assertEquals(5L, undo.expectedRevision)
        assertNull(newSharedCompletionCommand(ready(skipped = true), SharedCompletionState.Idle, "occurrence", id))
        assertNull(newSharedCompletionCommand(ready(hasDefinition = false), SharedCompletionState.Idle, "occurrence", id))
        assertNull(newSharedCompletionCommand(ready(stale = true), SharedCompletionState.Idle, "occurrence", id))
        assertNull(newSharedCompletionCommand(SharedChorePreviewState.Offline, SharedCompletionState.Idle, "occurrence", id))
    }

    @Test fun uncertainResponseRetainsIdAndBlocksDuplicateOrOfflineWrites() {
        val request = command()
        val sending = SharedCompletionState.Sending(request)
        assertFalse(canStartSharedCompletion(ready(), sending))
        val uncertain = sharedCompletionResponse(request, RemoteCompletionResult.Unknown)
        assertEquals(SharedCompletionState.Uncertain(request), uncertain)
        assertNull(retrySharedCompletionCommand(SharedChorePreviewState.Offline, uncertain))
        assertNull(newSharedCompletionCommand(ready(), uncertain, "occurrence", id))
        assertEquals(request, retrySharedCompletionCommand(ready(revision = 7), uncertain))
    }

    @Test fun acknowledgedWriteWaitsForVerifiedSnapshotWithoutOptimisticUpdate() {
        val request = command()
        val awaiting = sharedCompletionResponse(request, RemoteCompletionResult.Accepted(2))
        assertEquals(SharedCompletionState.AwaitingSnapshot(request, 2), awaiting)
        assertEquals(awaiting, reconcileSharedCompletion(awaiting, ready()))
        assertEquals(awaiting, reconcileSharedCompletion(awaiting, ready(completed = true, revision = 2, stale = true)))
        assertEquals(SharedCompletionState.Notice(SharedCompletionNotice.CONFIRMED),
            reconcileSharedCompletion(awaiting, ready(completed = true, revision = 2)))
        assertEquals(SharedCompletionState.Notice(SharedCompletionNotice.SUPERSEDED),
            reconcileSharedCompletion(awaiting, ready(revision = 3)))
    }

    @Test fun conflictsAndRejectedCommandsDoNotCreateNewRequests() {
        val request = command()
        val conflict = sharedCompletionResponse(request, RemoteCompletionResult.Conflict)
        assertEquals(SharedCompletionState.Notice(SharedCompletionNotice.CONFLICT), conflict)
        assertNull(retrySharedCompletionCommand(ready(), conflict))
        assertFalse(canStartSharedCompletion(SharedChorePreviewState.Loading, conflict))
        assertEquals(SharedCompletionState.Notice(SharedCompletionNotice.REJECTED),
            sharedCompletionResponse(request, RemoteCompletionResult.Rejected))
    }
}
