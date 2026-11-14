package com.hebi.homecrew.data.auth

/**
 * Only retries Firebase deletion after an explicit fresh authentication.
 * A user switch, failed authentication, or unrelated deletion error never triggers another delete.
 */
internal suspend fun deleteWithRecentLoginFallback(
    sameAccount: () -> Boolean,
    delete: suspend () -> Unit,
    requiresRecentLogin: (Exception) -> Boolean,
    reauthenticate: suspend () -> Unit
) {
    check(sameAccount()) { "The active account changed before deletion" }
    try {
        delete()
    } catch (error: Exception) {
        if (!requiresRecentLogin(error)) throw error
        check(sameAccount()) { "The active account changed before reauthentication" }
        reauthenticate()
        check(sameAccount()) { "The active account changed after reauthentication" }
        delete()
    }
}
