package com.example.dropnote.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateNoteRequest(
    val text: String
)