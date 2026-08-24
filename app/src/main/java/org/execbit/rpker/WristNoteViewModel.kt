package org.execbit.rpker

import android.app.Application
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.AndroidViewModel
import java.util.UUID

internal data class NoteEditorState(
    val noteId: String?,
    val input: TextFieldState,
)

internal class WristNoteViewModel(application: Application) : AndroidViewModel(application) {
    private val store = NoteStore(application)

    var notes by mutableStateOf(store.load())
        private set

    var editor by mutableStateOf<NoteEditorState?>(null)
        private set

    fun addNote(initialMarkdown: String = "") {
        editor = NoteEditorState(
            noteId = null,
            input = TextFieldState(
                initialText = initialMarkdown,
                initialSelection = TextRange(initialMarkdown.length),
            ),
        )
    }

    fun editNote(noteId: String) {
        val note = notes.firstOrNull { it.id == noteId } ?: return
        editor = NoteEditorState(
            noteId = note.id,
            input = TextFieldState(
                initialText = note.markdown,
                initialSelection = TextRange(note.markdown.length),
            ),
        )
    }

    fun saveEditor() {
        val current = editor ?: return
        val markdown = current.input.text.toString()
        if (markdown.isBlank()) return

        val existingIndex = current.noteId?.let { id -> notes.indexOfFirst { it.id == id } } ?: -1
        notes = if (existingIndex >= 0) {
            notes.toMutableList().apply {
                this[existingIndex] = this[existingIndex].copy(markdown = markdown)
            }
        } else {
            notes + Note(id = UUID.randomUUID().toString(), markdown = markdown)
        }
        store.save(notes)
        editor = null
    }

    fun closeEditor() {
        editor = null
    }

    fun deleteNote(noteId: String) {
        val updated = notes.filterNot { it.id == noteId }
        if (updated.size == notes.size) return
        notes = updated
        store.save(notes)
    }

    fun moveNote(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in notes.indices || toIndex !in notes.indices || fromIndex == toIndex) return
        notes = notes.moved(fromIndex, toIndex)
        store.save(notes)
    }
}
