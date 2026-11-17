package com.hebi.homecrew.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Explicit cloud-only entry point. Do not call it from local app startup or Room observers.
 * Membership and chore changes require a trusted backend in FIREBASE-04/05.
 */
internal class HomeCrewFirestoreHouseholds(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    suspend fun create(name: String): CloudHousehold {
        val uid = currentUid()
        val normalized = normalizeCloudHouseholdName(name)
        val doc = firestore.collection("households").document()
        doc.set(
            mapOf(
                "name" to normalized,
                "ownerUid" to uid,
                "schemaVersion" to 1,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return CloudHousehold(doc.id, uid, normalized)
    }

    suspend fun read(householdId: String): CloudHousehold? {
        currentUid()
        requireValidCloudId(householdId)
        val snapshot = firestore.collection("households").document(householdId)
            .get(Source.SERVER).await()
        if (!snapshot.exists()) return null
        return CloudHousehold(
            id = snapshot.id,
            ownerUid = requireNotNull(snapshot.getString("ownerUid")),
            name = requireNotNull(snapshot.getString("name")),
            schemaVersion = requireNotNull(snapshot.getLong("schemaVersion")).toInt()
        )
    }

    suspend fun rename(householdId: String, name: String) {
        val uid = currentUid()
        requireValidCloudId(householdId)
        val normalized = normalizeCloudHouseholdName(name)
        val ref = firestore.collection("households").document(householdId)
        val current = ref.get(Source.SERVER).await()
        check(current.exists() && current.getString("ownerUid") == uid) {
            "Only the verified household owner can rename this household"
        }
        ref.update("name", normalized, "updatedAt", FieldValue.serverTimestamp()).await()
    }

    private fun currentUid(): String =
        auth.currentUser?.uid?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Sign in with Firebase before using cloud data")
}
