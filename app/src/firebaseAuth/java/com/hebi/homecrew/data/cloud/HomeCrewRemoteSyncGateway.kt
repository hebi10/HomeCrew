package com.hebi.homecrew.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * Explicit Cloud-only gateway. It must not overwrite Room or auto-migrate data.
 * The user must confirm a separate member mapping and migration plan first.
 */
internal class HomeCrewRemoteSyncGateway(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val boundUid: String? = null
) : RemoteHouseholdChoreGateway {
    override fun observe(householdId: String): Flow<RemoteSnapshot> = flow {
        val expectedUid = requireUid()
        requireValidCloudId(householdId)

        // Firestore uses a shared on-device cache. A different signed-in account
        // must not receive cached entries from the previous account before the
        // server has verified its membership in this specific household.
        val authorizedHousehold = firestore.collection("households")
            .document(householdId).get(Source.SERVER).await()
        check(authorizedHousehold.exists() && auth.currentUser?.uid == expectedUid) {
            "Current Firebase account is not authorized to read this household"
        }

        val homeFlow = observeHousehold(householdId, expectedUid)
        val choreFlow = observeCollection(householdId, "chores", expectedUid)
        val occurrenceFlow = observeCollection(householdId, "occurrences", expectedUid)
        val memberFlow = observeCollection(householdId, "members", expectedUid)
        emitAll(combine(homeFlow, choreFlow, occurrenceFlow, memberFlow) { home, chores, occurrences, members ->
            check(auth.currentUser?.uid == expectedUid) {
                "Firebase account changed while observing shared data"
            }
            RemoteSnapshot(
                chores = chores.documents.map(::decodeChore),
                occurrences = occurrences.documents.map(::decodeOccurrence),
                mayBeStale = home.metadata.isFromCache || chores.metadata.isFromCache ||
                    occurrences.metadata.isFromCache || home.metadata.hasPendingWrites() ||
                    chores.metadata.hasPendingWrites() || occurrences.metadata.hasPendingWrites() ||
                    members.metadata.isFromCache || members.metadata.hasPendingWrites(),
                members = decodeMembers(home, members)
            )
        })
    }

    override suspend fun createChore(
        householdId: String,
        choreId: String,
        name: String,
        category: String,
        description: String,
        points: Int,
        assigneeUid: String
    ): Long {
        requireUid()
        requireValidCloudId(householdId)
        requireValidCloudId(choreId)
        val chore = mapOf(
            "id" to choreId, "name" to name.trim(), "category" to category.trim(),
            "description" to description.trim(), "points" to points, "assigneeUid" to assigneeUid
        )
        val response = call("createRemoteChore", mapOf(
            "householdId" to householdId, "chore" to chore
        ))
        return (response["revision"] as? Number)?.toLong() ?: error("Missing cloud revision")
    }

    override suspend fun configureSchedule(
        householdId: String,
        choreId: String,
        expectedRevision: Long,
        schedule: RemoteSchedule
    ): Long {
        requireUid()
        requireValidCloudId(householdId)
        requireValidCloudId(choreId)
        require(expectedRevision > 0)
        val result = call("configureRemoteSchedule", mapOf(
            "householdId" to householdId, "choreId" to choreId,
            "expectedRevision" to expectedRevision, "schedule" to schedule.payload()
        ))
        check(result["choreId"] == choreId)
        return (result["revision"] as? Number)?.toLong() ?: error("Missing schedule revision")
    }

    override suspend fun generateSchedule(
        householdId: String,
        choreId: String,
        expectedRevision: Long,
        fromEpochDay: Long,
        toEpochDay: Long
    ): RemoteGenerationResult {
        requireUid()
        requireValidCloudId(householdId)
        requireValidCloudId(choreId)
        require(expectedRevision > 0 && toEpochDay >= fromEpochDay && toEpochDay - fromEpochDay <= 62)
        val result = call("generateRemoteSchedule", mapOf(
            "householdId" to householdId, "choreId" to choreId,
            "expectedRevision" to expectedRevision, "fromEpochDay" to fromEpochDay,
            "toEpochDay" to toEpochDay
        ))
        val created = (result["created"] as? Number)?.toInt() ?: error("Missing created count")
        val existing = (result["existing"] as? Number)?.toInt() ?: error("Missing existing count")
        check(created in 0..63 && existing in 0..63 && created + existing <= 63)
        return RemoteGenerationResult(created, existing)
    }

    override suspend fun previewFutureSchedule(
        householdId: String, choreId: String, expectedRevision: Long
    ): RemoteFutureSchedulePreview = futureScheduleResult(
        "previewRemoteFutureSchedule", householdId, choreId, expectedRevision, null
    )

    override suspend fun applyFutureSchedule(
        householdId: String, choreId: String, expectedRevision: Long, previewDigest: String
    ): RemoteFutureSchedulePreview = futureScheduleResult(
        "applyRemoteFutureSchedule", householdId, choreId, expectedRevision, previewDigest
    )

    private suspend fun futureScheduleResult(
        function: String, householdId: String, choreId: String, revision: Long, digest: String?
    ): RemoteFutureSchedulePreview {
        requireUid()
        requireValidCloudId(householdId)
        requireValidCloudId(choreId)
        require(revision > 0)
        require(digest == null || digest.matches(Regex("[a-f0-9]{64}")))
        val payload = mutableMapOf<String, Any>(
            "householdId" to householdId, "choreId" to choreId, "expectedRevision" to revision
        )
        if (digest != null) payload["previewDigest"] = digest
        val result = call(function, payload)
        fun count(key: String) = (result[key] as Number).toInt()
        return RemoteFutureSchedulePreview(
            result["previewDigest"] as String,
            count("cancelled"), count("reassigned"), count("restored"),
            count("protectedCount"), count("balancedDeferred"),
            (result["changes"] as List<*>).map { value ->
                val change = value as Map<*, *>
                RemoteFutureScheduleChange((change["date"] as Number).toLong(), change["type"] as String)
            }
        )
    }

    suspend fun updateChore(
        householdId: String,
        choreId: String,
        name: String,
        category: String,
        description: String,
        points: Int,
        assigneeUid: String,
        expectedRevision: Long
    ): Long {
        requireUid()
        requireValidCloudId(householdId)
        requireValidCloudId(choreId)
        require(expectedRevision > 0)
        val payload = mapOf(
            "id" to choreId, "name" to name.trim(), "category" to category.trim(),
            "description" to description.trim(), "points" to points, "assigneeUid" to assigneeUid
        )
        val response = call("updateRemoteChore", mapOf(
            "householdId" to householdId, "chore" to payload,
            "expectedRevision" to expectedRevision
        ))
        return (response["revision"] as? Number)?.toLong()
            ?: error("Missing cloud revision")
    }

    suspend fun ensureOccurrence(
        householdId: String,
        choreId: String,
        scheduledEpochDay: Long
    ): Pair<String, Long> {
        requireUid()
        requireValidCloudId(householdId)
        requireValidCloudId(choreId)
        val response = call("ensureRemoteOccurrence", mapOf(
            "householdId" to householdId,
            "choreId" to choreId,
            "scheduledEpochDay" to scheduledEpochDay
        ))
        val id = response["occurrenceId"] as? String ?: error("Missing occurrence ID")
        requireValidCloudId(id)
        val revision = (response["revision"] as? Number)?.toLong()
            ?: error("Missing occurrence revision")
        return id to revision
    }

    override suspend fun recordCompletion(
        householdId: String,
        command: RemoteCompletionCommand
    ): RemoteCompletionResult {
        return try {
            val revision = completeOccurrence(
                householdId, command.occurrenceId, command.completed,
                command.expectedRevision, command.requestId
            )
            if (revision == command.expectedRevision + 1) {
                RemoteCompletionResult.Accepted(revision)
            } else RemoteCompletionResult.Unknown
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseFunctionsException) {
            when (error.code) {
                FirebaseFunctionsException.Code.ABORTED,
                FirebaseFunctionsException.Code.ALREADY_EXISTS -> RemoteCompletionResult.Conflict
                FirebaseFunctionsException.Code.UNAUTHENTICATED,
                FirebaseFunctionsException.Code.PERMISSION_DENIED,
                FirebaseFunctionsException.Code.NOT_FOUND,
                FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> RemoteCompletionResult.Rejected
                else -> RemoteCompletionResult.Unknown
            }
        } catch (_: Exception) {
            if (boundUid != null && auth.currentUser?.uid != boundUid) {
                RemoteCompletionResult.Rejected
            } else RemoteCompletionResult.Unknown
        }
    }

    suspend fun completeOccurrence(
        householdId: String,
        occurrenceId: String,
        completed: Boolean,
        expectedRevision: Long,
        stableRequestId: String
    ): Long {
        requireUid()
        requireValidCloudId(householdId)
        requireValidCloudId(occurrenceId)
        require(stableRequestId.length in 20..72)
        val response = call("recordChoreCompletion", mapOf(
            "householdId" to householdId, "occurrenceId" to occurrenceId,
            "completed" to completed, "expectedRevision" to expectedRevision,
            "requestId" to stableRequestId
        ))
        return (response["revision"] as? Number)?.toLong() ?: error("Missing cloud revision")
    }

    fun newRequestId(): String = UUID.randomUUID().toString()

    private fun observeHousehold(
        householdId: String,
        expectedUid: String
    ): Flow<DocumentSnapshot> = callbackFlow {
        val sessionListener = FirebaseAuth.AuthStateListener { current ->
            if (current.currentUser?.uid != expectedUid) {
                close(IllegalStateException("Cloud session changed"))
            }
        }
        auth.addAuthStateListener(sessionListener)
        if (auth.currentUser?.uid != expectedUid) {
            close(IllegalStateException("Cloud session changed before subscription"))
            awaitClose { auth.removeAuthStateListener(sessionListener) }
            return@callbackFlow
        }
        val registration = firestore.collection("households").document(householdId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                    auth.currentUser?.uid != expectedUid ->
                        close(IllegalStateException("Cloud session changed"))
                    error != null -> close(error)
                    snapshot != null && !snapshot.exists() ->
                        close(IllegalStateException("Household no longer available"))
                    snapshot != null -> trySend(snapshot)
                    else -> Unit
                }
            }
        awaitClose {
            registration.remove()
            auth.removeAuthStateListener(sessionListener)
        }
    }

    private fun observeCollection(
        householdId: String,
        collectionName: String,
        expectedUid: String
    ): Flow<QuerySnapshot> = callbackFlow {
        val sessionListener = FirebaseAuth.AuthStateListener { current ->
            if (current.currentUser?.uid != expectedUid) {
                close(IllegalStateException("Cloud session changed; discard cached household data"))
            }
        }
        auth.addAuthStateListener(sessionListener)
        if (auth.currentUser?.uid != expectedUid) {
            close(IllegalStateException("Cloud session changed before subscription"))
            awaitClose { auth.removeAuthStateListener(sessionListener) }
            return@callbackFlow
        }

        val reference = firestore.collection("households").document(householdId)
            .collection(collectionName)
        val registration = reference.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (auth.currentUser?.uid != expectedUid) {
                close(IllegalStateException("Cloud session changed during subscription"))
            } else if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot)
            }
        }
        awaitClose {
            registration.remove()
            auth.removeAuthStateListener(sessionListener)
        }
    }

    private suspend fun call(name: String, data: Map<String, Any>): Map<*, *> {
        val expectedUid = requireUid()
        val result = functions.getHttpsCallable(name).call(data).await().data
        check(auth.currentUser?.uid == expectedUid) {
            "Firebase account changed during cloud operation"
        }
        return result as? Map<*, *> ?: error("Malformed callable response")
    }

    private fun requireUid(): String {
        val uid = auth.currentUser?.uid?.takeIf(String::isNotBlank) ?: error("Sign in required")
        check(boundUid == null || boundUid == uid) { "Cloud session changed" }
        return uid
    }

    private fun decodeMembers(home: DocumentSnapshot, snapshot: QuerySnapshot): List<RemoteHouseholdMember> {
        val ownerUid = requireNotNull(home.getString("ownerUid"))
        return listOf(RemoteHouseholdMember(ownerUid, true)) +
            snapshot.documents.filter { it.id != ownerUid }.map {
                check(it.getString("uid") == it.id && it.getString("role") == "member")
                RemoteHouseholdMember(it.id, false)
            }.sortedBy { it.uid }
    }

    private fun decodeSchedule(doc: DocumentSnapshot): RemoteSchedule? {
        val raw = doc.get("schedule") ?: return null
        val map = raw as? Map<*, *> ?: error("Invalid shared schedule")
        return RemoteSchedule(
            startEpochDay = (map["startEpochDay"] as Number).toLong(),
            repeatMode = map["repeatMode"] as String,
            weekdays = (map["weekdays"] as List<*>).map { (it as Number).toInt() },
            intervalDays = (map["intervalDays"] as Number).toInt(),
            assignmentMode = map["assignmentMode"] as String,
            memberUids = (map["memberUids"] as List<*>).map { it as String }
        )
    }

    private fun decodeChore(doc: DocumentSnapshot) = RemoteChore(
        id = doc.id,
        name = requireNotNull(doc.getString("name")),
        category = doc.getString("category").orEmpty(),
        points = requireNotNull(doc.getLong("points")).toInt(),
        assigneeUid = requireNotNull(doc.getString("assigneeUid")),
        revision = requireNotNull(doc.getLong("revision")),
        schedule = decodeSchedule(doc)
    )

    private fun decodeOccurrence(doc: DocumentSnapshot) = RemoteOccurrence(
        id = doc.id,
        definitionId = requireNotNull(doc.getString("definitionId")),
        scheduledEpochDay = requireNotNull(doc.getLong("scheduledEpochDay")),
        assignedUid = requireNotNull(doc.getString("assignedUid")),
        completed = requireNotNull(doc.getBoolean("completed")),
        completedByUid = doc.getString("completedByUid"),
        revision = requireNotNull(doc.getLong("revision")),
        skipped = doc.getTimestamp("skippedAt") != null,
        workloadPoints = doc.getLong("workloadPoints")?.toInt(),
        scheduleCancelled = doc.getTimestamp("scheduleCancelledAt") != null
    )
}
