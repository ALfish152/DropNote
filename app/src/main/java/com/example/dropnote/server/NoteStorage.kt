package com.example.dropnote.server

import com.example.dropnote.model.CreateNoteRequest
import com.example.dropnote.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object NoteStorage {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes = _notes.asStateFlow()

    fun addNote(request: CreateNoteRequest): Note {
        val cleanAuthor = request.author.trim().ifEmpty { "Host" }
        val note = Note(
            id = UUID.randomUUID().toString(),
            author = cleanAuthor,
            text = request.text.trim(),
            color = request.color.ifBlank { "blue" },
            likes = 0,
            fileUrl = request.fileUrl,
            fileName = request.fileName,
            fileSize = request.fileSize,
            isImage = request.isImage,
            timestamp = System.currentTimeMillis()
        )
        _notes.value = listOf(note) + _notes.value
        return note
    }

    fun toggleLike(id: String, increment: Boolean): Note? {
        var updatedNote: Note? = null
        _notes.value = _notes.value.map { note ->
            if (note.id == id) {
                val nextLikes = if (increment) note.likes + 1 else maxOf(0, note.likes - 1)
                updatedNote = note.copy(likes = nextLikes)
                updatedNote!!
            } else {
                note
            }
        }
        return updatedNote
    }

    fun deleteNote(id: String) {
        _notes.value = _notes.value.filterNot { it.id == id }
    }

    fun clearAll() {
        _notes.value = emptyList()
    }

    fun getNotes(): List<Note> = _notes.value
}