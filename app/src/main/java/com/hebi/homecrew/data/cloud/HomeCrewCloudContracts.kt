package com.hebi.homecrew.data.cloud

internal data class CloudHousehold(
    val id: String,
    val ownerUid: String,
    val name: String,
    val schemaVersion: Int = 1
) {
    init {
        requireValidCloudId(id)
        require(ownerUid.isNotBlank()) { "Firebase UID is required" }
        require(normalizeCloudHouseholdName(name) == name)
        require(schemaVersion == 1)
    }
}

internal fun normalizeCloudHouseholdName(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty() && normalized.length <= 80) {
        "Household name must contain 1 to 80 characters"
    }
    return normalized
}

internal fun requireValidCloudId(id: String) {
    require(id.isNotBlank() && id.length <= 128 && '/' !in id && id != "." && id != "..") {
        "Invalid Firestore document ID"
    }
}
