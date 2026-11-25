package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.cloud.RemoteCompletionCommand
import com.hebi.homecrew.data.cloud.RemoteCompletionResult
import java.util.UUID

internal enum class SharedCompletionNotice {
    CONFIRMED, SUPERSEDED, CONFLICT, REJECTED, RECHECK
}

internal sealed interface SharedCompletionState {
    data object Idle : SharedCompletionState
    data class Sending(val command: RemoteCompletionCommand) : SharedCompletionState
    data class Uncertain(val command: RemoteCompletionCommand) : SharedCompletionState
    data class AwaitingSnapshot(
        val command: RemoteCompletionCommand,
        val revision: Long
    ) : SharedCompletionState
    data class Notice(val type: SharedCompletionNotice) : SharedCompletionState
}

internal fun canStartSharedCompletion(
    preview: SharedChorePreviewState,
    completion: SharedCompletionState
): Boolean = preview is SharedChorePreviewState.Ready &&
    !preview.snapshot.mayBeStale &&
    (completion is SharedCompletionState.Idle || completion is SharedCompletionState.Notice)

internal fun newSharedCompletionCommand(
    preview: SharedChorePreviewState,
    completion: SharedCompletionState,
    occurrenceId: String,
    requestId: String = UUID.randomUUID().toString()
): RemoteCompletionCommand? {
    if (!canStartSharedCompletion(preview, completion)) return null
    val snapshot = (preview as SharedChorePreviewState.Ready).snapshot
    val row = snapshot.occurrences.singleOrNull { it.id == occurrenceId } ?: return null
    if (row.skipped || row.scheduleCancelled || row.revision !in 1 until Long.MAX_VALUE ||
        snapshot.chores.none { it.id == row.definitionId }) return null
    return RemoteCompletionCommand(row.id, !row.completed, row.revision, requestId)
}

internal fun retrySharedCompletionCommand(
    preview: SharedChorePreviewState,
    completion: SharedCompletionState
): RemoteCompletionCommand? =
    if (preview is SharedChorePreviewState.Ready && !preview.snapshot.mayBeStale &&
        completion is SharedCompletionState.Uncertain
    ) completion.command else null

internal fun sharedCompletionResponse(
    command: RemoteCompletionCommand,
    result: RemoteCompletionResult
): SharedCompletionState = when (result) {
    is RemoteCompletionResult.Accepted ->
        if (result.revision == command.expectedRevision + 1) {
            SharedCompletionState.AwaitingSnapshot(command, result.revision)
        } else SharedCompletionState.Uncertain(command)
    RemoteCompletionResult.Conflict -> SharedCompletionState.Notice(SharedCompletionNotice.CONFLICT)
    RemoteCompletionResult.Rejected -> SharedCompletionState.Notice(SharedCompletionNotice.REJECTED)
    RemoteCompletionResult.Unknown -> SharedCompletionState.Uncertain(command)
}

internal fun reconcileSharedCompletion(
    completion: SharedCompletionState,
    preview: SharedChorePreviewState
): SharedCompletionState {
    if (completion !is SharedCompletionState.AwaitingSnapshot ||
        preview !is SharedChorePreviewState.Ready || preview.snapshot.mayBeStale
    ) return completion
    val occurrence = preview.snapshot.occurrences.singleOrNull {
        it.id == completion.command.occurrenceId
    } ?: return completion
    if (occurrence.revision < completion.revision) return completion
    val notice = if (occurrence.revision == completion.revision &&
        occurrence.completed == completion.command.completed && !occurrence.skipped && !occurrence.scheduleCancelled
    ) SharedCompletionNotice.CONFIRMED else SharedCompletionNotice.SUPERSEDED
    return SharedCompletionState.Notice(notice)
}
