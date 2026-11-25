package com.hebi.homecrew.data.cloud

import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface CompletionJournalStorage {
    fun read(key: String): String?
    fun write(key: String, value: String?): Boolean
}

internal fun completionJournalKey(projectId: String, uid: String, householdId: String): String {
    require(projectId.isNotBlank() && uid.isNotBlank())
    requireValidCloudId(householdId)
    val scope = listOf(projectId, uid, householdId).joinToString("") { "${it.length}:$it" }
    val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
    return "completion-v1-" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal object CompletionJournalCodec {
    private val documentId = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")

    fun encode(command: RemoteCompletionCommand): String {
        require(documentId.matches(command.occurrenceId))
        return listOf(
            "1", command.occurrenceId, if (command.completed) "1" else "0",
            command.expectedRevision.toString(), command.requestId
        ).joinToString("|")
    }

    fun decode(value: String): RemoteCompletionCommand {
        val parts = value.split('|')
        require(parts.size == 5 && parts[0] == "1" && documentId.matches(parts[1])) {
            "Invalid completion journal"
        }
        require(parts[2] == "0" || parts[2] == "1")
        return RemoteCompletionCommand(parts[1], parts[2] == "1", parts[3].toLong(), parts[4])
    }
}

/**
 * One outstanding completion per project/account/household. A durable commit
 * precedes every network attempt; neither opening a dialog nor reading a record
 * initiates a write. Only the exact staged command can be retried or removed.
 */
internal class HomeCrewCompletionJournal(
    private val storage: CompletionJournalStorage,
    projectId: String,
    accountUid: String,
    householdId: String
) {
    private val key = completionJournalKey(projectId, accountUid, householdId)

    suspend fun load(): RemoteCompletionCommand? = withContext(Dispatchers.IO) {
        synchronized(lock) { storage.read(key)?.let(CompletionJournalCodec::decode) }
    }

    suspend fun stage(command: RemoteCompletionCommand) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = storage.read(key)?.let(CompletionJournalCodec::decode)
            check(current == null || current == command) { "Another request is pending" }
            if (current == null) {
                check(storage.write(key, CompletionJournalCodec.encode(command))) {
                    "Unable to persist completion request"
                }
            }
        }
    }

    suspend fun remove(command: RemoteCompletionCommand) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = storage.read(key)?.let(CompletionJournalCodec::decode)
            check(current == null || current == command) { "Pending request changed" }
            if (current != null) {
                check(storage.write(key, null)) { "Unable to clear completion request" }
            }
        }
    }

    companion object {
        private val lock = Any()
    }
}
