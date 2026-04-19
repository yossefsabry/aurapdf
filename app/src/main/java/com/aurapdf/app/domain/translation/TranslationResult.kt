package com.aurapdf.app.domain.translation

/**
 * Holds a completed translation pair — shown in the UI bottom sheet.
 */
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
)
