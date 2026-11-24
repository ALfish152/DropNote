package com.example.dropnote.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateNoteRequest(
    val author: String = "Guest",
    val text: String = "",
    val color: String = "blue",
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: String? = null,
    val isImage: Boolean = false
)