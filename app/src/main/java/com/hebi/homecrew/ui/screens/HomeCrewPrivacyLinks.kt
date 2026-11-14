package com.hebi.homecrew.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal const val HOMECREW_PRIVACY_POLICY_URL =
    "https://hebi10.github.io/HomeCrew/privacy-policy.html"

internal const val HOMECREW_DELETE_ACCOUNT_URL =
    "https://hebi10.github.io/HomeCrew/delete-account.html"

internal fun openHomeCrewWebPage(context: Context, url: String): Boolean = try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
