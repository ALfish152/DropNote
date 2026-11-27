package com.example.dropnote.model

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val author: String = "Guest",
    val text: String,
    val color: String = "blue",
    val likes: Int = 0,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: String? = null,
    val isImage: Boolean = false,
    val timestamp: Long
)