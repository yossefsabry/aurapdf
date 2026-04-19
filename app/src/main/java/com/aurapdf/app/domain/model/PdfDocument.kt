package com.aurapdf.app.domain.model

/**
 * Clean domain model for a PDF document.
 * Has no Room or Android framework annotations — pure Kotlin data class.
 */
data class PdfDocument(
    val id: Long,
    val uri: String,
    val name: String,
    val lastPage: Int,
    val scrollOffset: Int,
    val dateAdded: Long,
    val totalPages: Int
)
