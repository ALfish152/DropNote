package com.example.dropnote.model

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val text: String,
    val imageBase64: String? = null,
    val timestamp: Long
)
