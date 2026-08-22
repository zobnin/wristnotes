package org.execbit.rpker

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class NoteStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun load(): List<Note> {
        val serialized = preferences.getString(NOTES_KEY, null) ?: return emptyList()
        return try {
            val seenIds = mutableSetOf<String>()
            buildList {
                val notes = JSONArray(serialized)
                for (index in 0 until notes.length()) {
                    val item = notes.optJSONObject(index) ?: continue
                    val id = item.optString(ID_KEY)
                    val markdown = item.optString(MARKDOWN_KEY)
                    if (id.isNotBlank() && markdown.isNotBlank() && seenIds.add(id)) {
                        add(Note(id = id, markdown = markdown))
                    }
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    fun save(notes: List<Note>) {
        val serialized = JSONArray().apply {
            notes.forEach { note ->
                put(
                    JSONObject()
                        .put(ID_KEY, note.id)
                        .put(MARKDOWN_KEY, note.markdown)
                )
            }
        }.toString()
        preferences.edit { putString(NOTES_KEY, serialized) }
    }

    private companion object {
        const val PREFERENCES_NAME = "wrist_note_notes"
        const val NOTES_KEY = "notes"
        const val ID_KEY = "id"
        const val MARKDOWN_KEY = "markdown"
    }
}
