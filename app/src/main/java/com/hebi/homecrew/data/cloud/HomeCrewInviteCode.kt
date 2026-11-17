package com.hebi.homecrew.data.cloud

import java.util.Locale

internal fun normalizeHomeCrewInviteCode(value: String): String {
    val code = value.trim().uppercase(Locale.ROOT).replace("-", "")
    require(code.matches(Regex("[0-9A-F]{16}"))) { "Invalid invite code format" }
    return code
}

internal data class HomeCrewCloudInvite(
    val inviteCode: String,
    val householdId: String,
    val expiresAtMs: Long
)
