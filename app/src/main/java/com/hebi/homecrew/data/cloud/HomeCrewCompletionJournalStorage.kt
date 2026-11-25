package com.hebi.homecrew.data.cloud

import android.content.Context

internal class AndroidCompletionJournalStorage(context: Context) : CompletionJournalStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        "homecrew_shared_completion_journal_v1", Context.MODE_PRIVATE
    )

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String?): Boolean {
        val edit = preferences.edit()
        if (value == null) edit.remove(key) else edit.putString(key, value)
        return edit.commit()
    }
}
