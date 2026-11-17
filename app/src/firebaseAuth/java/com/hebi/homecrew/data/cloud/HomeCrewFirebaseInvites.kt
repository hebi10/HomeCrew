package com.hebi.homecrew.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Invitations are server-authoritative. They do not migrate Room or change local
 * members; FIREBASE-05 must resolve mapping and synchronization explicitly.
 */
internal class HomeCrewFirebaseInvites(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions
) {
    suspend fun create(householdId: String): HomeCrewCloudInvite {
        signedIn()
        requireValidCloudId(householdId)
        val data = invoke("createHouseholdInvite", mapOf("householdId" to householdId))
        return HomeCrewCloudInvite(
            inviteCode = data["inviteCode"] as? String ?: error("Invalid invite response"),
            householdId = data["householdId"] as? String ?: error("Missing household"),
            expiresAtMs = (data["expiresAtMs"] as? Number)?.toLong()
                ?: error("Invalid invite expiration")
        )
    }

    suspend fun accept(inviteCode: String): String {
        signedIn()
        val data = invoke(
            "acceptHouseholdInvite",
            mapOf("inviteCode" to normalizeHomeCrewInviteCode(inviteCode))
        )
        return (data["householdId"] as? String)?.also(::requireValidCloudId)
            ?: error("Missing shared household")
    }

    suspend fun leave(householdId: String) {
        signedIn()
        requireValidCloudId(householdId)
        invoke("leaveHousehold", mapOf("householdId" to householdId))
    }

    suspend fun ensureOwnerIndex(householdId: String) {
        signedIn()
        requireValidCloudId(householdId)
        invoke("ensureHouseholdOwnerIndex", mapOf("householdId" to householdId))
    }

    private suspend fun invoke(
        name: String,
        arguments: Map<String, String>
    ): Map<*, *> {
        val value = functions.getHttpsCallable(name).call(arguments).await().data
        return value as? Map<*, *> ?: error("Malformed cloud response")
    }

    private fun signedIn() {
        check(!auth.currentUser?.uid.isNullOrBlank()) { "Firebase sign-in required" }
    }
}
