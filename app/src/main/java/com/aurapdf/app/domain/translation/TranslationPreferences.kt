package com.aurapdf.app.domain.translation

/**
 * User's translation settings — persisted in DataStore.
 */
data class TranslationPrefs(
    /** BCP-47 code for the target language.  Empty string = not configured. */
    val targetLanguage: String = "",
    /** BCP-47 code for the source language.  Defaults to English. */
    val sourceLanguage: String = "en",
)

/** Repository interface for reading/writing [TranslationPrefs]. */
interface TranslationPreferences {
    suspend fun getPrefs(): TranslationPrefs
    suspend fun savePrefs(prefs: TranslationPrefs)
}
