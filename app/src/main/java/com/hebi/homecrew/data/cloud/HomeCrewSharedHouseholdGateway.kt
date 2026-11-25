package com.hebi.homecrew.data.cloud

import java.util.UUID

internal data class SharedHouseholdSummary(
    val id: String,
    val name: String,
    val memberCount: Int,
    val isOwner: Boolean
) {
    init {
        requireValidCloudId(id)
        require(normalizeCloudHouseholdName(name) == name)
        require(memberCount in 1..8)
    }
}

internal data class SharedHouseholdCreateRequest(val name: String, val requestId: String) {
    init {
        require(normalizeCloudHouseholdName(name) == name)
        require(UUID.fromString(requestId).toString() == requestId)
    }
}

internal fun sharedHouseholdCreateRequest(name: String, requestId: String) =
    SharedHouseholdCreateRequest(normalizeCloudHouseholdName(name), requestId)

internal interface HomeCrewSharedHouseholdGateway {
    fun choreGateway(): RemoteHouseholdChoreGateway
    fun completionJournal(householdId: String): HomeCrewCompletionJournal
    suspend fun list(): List<SharedHouseholdSummary>
    suspend fun create(request: SharedHouseholdCreateRequest): String
    suspend fun invite(householdId: String): HomeCrewCloudInvite
    suspend fun join(code: String): String
}

// A development preview must be explicitly pinned to a non-production project.
// This gate never enables the release variant and does not configure Firebase.
internal fun canUseSharedHouseholdPreview(
    enabled: Boolean,
    actualProjectId: String?,
    allowedProjectId: String
): Boolean = enabled &&
    allowedProjectId.matches(Regex("[a-z][a-z0-9-]{4,29}")) &&
    allowedProjectId != "homecrew-1b14b" &&
    actualProjectId == allowedProjectId
