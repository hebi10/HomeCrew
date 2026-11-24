package com.hebi.homecrew.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import java.util.UUID
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
) : RemoteHouseholdReadGateway {
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
        emitAll(combine(homeFlow, choreFlow, occurrenceFlow) { home, chores, occurrences ->
            check(auth.currentUser?.uid == expectedUid) {
                "Firebase account changed while observing shared data"
            }
            RemoteSnapshot(
                chores = chores.documents.map(::decodeChore),
                occurrences = occurrences.documents.map(::decodeOccurrence),
                mayBeStale = home.metadata.isFromCache || chores.metadata.isFromCache ||
                    occurrences.metadata.isFromCache || home.metadata.hasPendingWrites() ||
                    chores.metadata.hasPendingWrites() || occurrences.metadata.hasPendingWrites()
            )
        })
    }

    suspend fun createChore(
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

    private fun decodeChore(doc: DocumentSnapshot) = RemoteChore(
        id = doc.id,
        name = requireNotNull(doc.getString("name")),
        category = doc.getString("category").orEmpty(),
        points = requireNotNull(doc.getLong("points")).toInt(),
        assigneeUid = requireNotNull(doc.getString("assigneeUid")),
        revision = requireNotNull(doc.getLong("revision"))
    )

    private fun decodeOccurrence(doc: DocumentSnapshot) = RemoteOccurrence(
        id = doc.id,
        definitionId = requireNotNull(doc.getString("definitionId")),
        scheduledEpochDay = requireNotNull(doc.getLong("scheduledEpochDay")),
        assignedUid = requireNotNull(doc.getString("assignedUid")),
        completed = requireNotNull(doc.getBoolean("completed")),
        completedByUid = doc.getString("completedByUid"),
        revision = requireNotNull(doc.getLong("revision")),
        skipped = doc.getTimestamp("skippedAt") != null
    )
}
