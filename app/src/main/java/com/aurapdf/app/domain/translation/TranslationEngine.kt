package com.aurapdf.app.domain.translation

/**
 * Abstraction over an offline translation engine.
 * Implementations handle model lifecycle and caching.
 */
interface TranslationEngine {

    /**
     * Translate [text] from [sourceLanguage] to [targetLanguage].
     * Language codes are BCP-47 (e.g. "en", "ar", "es").
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): Result<String>

    /**
     * Ensure the model for [sourceLanguage] -> [targetLanguage] is
     * downloaded and ready.  Suspends until the download completes.
     */
    suspend fun ensureModelReady(
        sourceLanguage: String,
        targetLanguage: String,
    ): Result<Unit>

    /** Return the list of supported BCP-47 language codes. */
    fun availableLanguages(): List<String>
}
