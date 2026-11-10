package com.hebi.homecrew.ui.setup

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class LocalHouseholdStore(context: Context) {
    private val preferences = context.getSharedPreferences("homecrew_setup_v1", Context.MODE_PRIVATE)

    fun load(): HouseholdSetup? {
        val stored = preferences.getString("household", null) ?: return null
        return try {
            val json = JSONObject(stored)
            val memberArray = json.getJSONArray("members")
            val templateArray = json.getJSONArray("templates")
            val configured = HouseholdSetup(
                homeName = json.getString("homeName"),
                members = (0 until memberArray.length()).map { memberArray.getString(it) },
                defaultMember = json.getString("defaultMember"),
                selectedTemplateIds = (0 until templateArray.length()).map { templateArray.getString(it) }
            )
            configured.takeIf(::validateHouseholdSetup)
        } catch (_: JSONException) {
            null
        }
    }

    fun save(setup: HouseholdSetup): Boolean {
        if (!validateHouseholdSetup(setup)) return false
        val content = JSONObject()
            .put("homeName", setup.homeName.trim())
            .put("members", JSONArray(setup.members))
            .put("defaultMember", setup.defaultMember)
            .put("templates", JSONArray(setup.selectedTemplateIds))
        return preferences.edit().putString("household", content.toString()).commit()
    }
}
