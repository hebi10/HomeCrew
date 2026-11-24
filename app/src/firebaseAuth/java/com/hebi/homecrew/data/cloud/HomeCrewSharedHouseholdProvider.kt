package com.hebi.homecrew.data.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.hebi.homecrew.R
import kotlinx.coroutines.tasks.await

internal fun homeCrewSharedHouseholdGateway(
    context: Context,
    accountUid: String
): HomeCrewSharedHouseholdGateway? {
    if (!context.resources.getBoolean(R.bool.homecrew_shared_households_preview)) return null
    val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        ?: return null
    val auth = FirebaseAuth.getInstance(app)
    if (auth.currentUser?.uid != accountUid) return null
    return FirebaseSharedHouseholdGateway(
        accountUid, auth, FirebaseFirestore.getInstance(app),
        FirebaseFunctions.getInstance(app, "asia-northeast3")
    )
}

private class FirebaseSharedHouseholdGateway(
    private val uid: String,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : HomeCrewSharedHouseholdGateway {
    private val households = HomeCrewFirestoreHouseholds(auth, firestore)
    private val invites = HomeCrewFirebaseInvites(auth, functions)

    private fun verifySession() {
        check(auth.currentUser?.uid == uid) { "Account changed" }
    }

    override suspend fun list(): List<SharedHouseholdSummary> {
        verifySession()
        val index = firestore.collection("users").document(uid).collection("households")
            .limit(51).get(Source.SERVER).await()
        verifySession()
        check(index.size() <= 50) { "Household limit exceeded" }
        val result = index.documents.map { record ->
            verifySession()
            val home = checkNotNull(households.read(record.id)) { "Missing household" }
            verifySession()
            SharedHouseholdSummary(home.id, home.name, home.memberCount, home.ownerUid == uid)
        }
        verifySession()
        return result.sortedWith(compareBy({ it.name }, { it.id }))
    }

    override suspend fun create(request: SharedHouseholdCreateRequest): String {
        verifySession()
        val response = functions.getHttpsCallable("createSharedHousehold").call(
            mapOf("name" to request.name, "requestId" to request.requestId)
        ).await().data as? Map<*, *> ?: error("Invalid creation response")
        verifySession()
        return (response["householdId"] as? String)?.also(::requireValidCloudId)
            ?: error("Missing household")
    }

    override suspend fun invite(householdId: String): HomeCrewCloudInvite {
        verifySession()
        val result = invites.create(householdId)
        verifySession()
        check(result.householdId == householdId)
        return result
    }

    override suspend fun join(code: String): String {
        verifySession()
        val result = invites.accept(normalizeHomeCrewInviteCode(code))
        verifySession()
        return result
    }
}
