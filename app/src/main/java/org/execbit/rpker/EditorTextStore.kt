package org.execbit.rpker

import android.content.Context

internal class EditorTextStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): String = preferences.getString(TEXT_KEY, null).orEmpty()

    fun save(text: String) {
        if (load() == text) return
        preferences.edit().putString(TEXT_KEY, text).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "rpk_editor"
        const val TEXT_KEY = "markdown_text"
    }
}
