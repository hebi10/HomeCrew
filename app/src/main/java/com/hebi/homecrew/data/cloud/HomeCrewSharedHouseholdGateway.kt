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
    fun migrationJournal(householdId: String): HomeCrewLocalMigrationJournal
    suspend fun roster(householdId: String): SharedHouseholdRoster
    suspend fun renameMember(householdId: String, memberUid: String, displayName: String, expectedRosterDigest: String)
    suspend fun changeMembership(householdId: String, memberUid: String, action: SharedMembershipAction, expectedRosterDigest: String)
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

internal enum class SharedMembershipAction { LEAVE, REMOVE, TRANSFER }

internal data class SharedHouseholdRoster(
    val members: List<RemoteHouseholdMember>,
    val digest: String,
    val transferBlocked: Boolean
) {
    init {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        require(members.size in 1..8 && members.count { it.isOwner } == 1)
        require(members.map { it.uid }.distinct().size == members.size)
    }
}

internal fun normalizeSharedMemberName(value: String): String {
    val name = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC).trim()
    require(name.length in 1..12 && !name.any { ch ->
        Character.getType(ch) in setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.SURROGATE.toInt())
    })
    return name
}
