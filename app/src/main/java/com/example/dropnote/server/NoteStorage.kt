package com.example.dropnote.server

import com.example.dropnote.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object NoteStorage {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes = _notes.asStateFlow()

    fun addNote(text: String): Note {
        val note = Note(
            id = UUID.randomUUID().toString(),
            text = text,
            timestamp = System.currentTimeMillis()
        )

        _notes.value = _notes.value + note
        return note
    }

    fun getNotes(): List<Note> = _notes.value
}