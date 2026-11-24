package com.example.dropnote.server

import android.util.Base64
import com.example.dropnote.model.CreateNoteRequest
import com.example.dropnote.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object NoteStorage {

    private const val MAX_NOTES = 50
    private const val MAX_ATTACHMENT_BYTES = 1 * 1024 * 1024

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes = _notes.asStateFlow()

    sealed class AddNoteResult {
        data class Success(val note: Note) : AddNoteResult()
        data class Error(val message: String) : AddNoteResult()
    }

    fun addNote(request: CreateNoteRequest): AddNoteResult {
        val cleanText = request.text.trim()
        val cleanAuthor = request.author.trim().ifEmpty { "Guest" }
        val cleanFileUrl = request.fileUrl?.takeIf { it.isNotBlank() }


        if (cleanText.isEmpty() && cleanFileUrl == null) {
            return AddNoteResult.Error("Note cannot be empty")
        }

        if (cleanFileUrl != null) {
            val base64Part = cleanFileUrl.substringAfter("base64,", cleanFileUrl)
            val decodedSize = try {
                Base64.decode(base64Part, Base64.DEFAULT).size
            } catch (e: IllegalArgumentException) {
                return AddNoteResult.Error("Invalid attachment encoding")
            }

            if (decodedSize > MAX_ATTACHMENT_BYTES) {
                return AddNoteResult.Error("Attachment exceeds the 1 MB server limit")
            }
        }

        val note = Note(
            id = UUID.randomUUID().toString(),
            author = cleanAuthor.take(30),
            text = cleanText.take(500),
            color = request.color.ifBlank { "blue" },
            likes = 0,
            fileUrl = cleanFileUrl,
            fileName = request.fileName,
            fileSize = request.fileSize,
            isImage = request.isImage,
            timestamp = System.currentTimeMillis()
        )

        _notes.update { current ->
            (listOf(note) + current).take(MAX_NOTES)
        }

        return AddNoteResult.Success(note)
    }

    fun toggleLike(noteId: String, increment: Boolean): Note? {
        var updatedNote: Note? = null
        _notes.update { current ->
            current.map { note ->
                if (note.id == noteId) {
                    val newLikes = if (increment) note.likes + 1 else maxOf(0, note.likes - 1)
                    note.copy(likes = newLikes).also { updatedNote = it }
                } else {
                    note
                }
            }
        }
        return updatedNote
    }

    fun getNotes(): List<Note> = _notes.value
}