package com.aurapdf.app.domain.prefs

/**
 * User-configurable filters for the Smart Content Filter.
 * Persisted via DataStore; defaults match the most common reading expectation.
 */
data class SmartReadingPrefs(
    /** Skip blocks detected as headers / running titles. */
    val skipHeaders: Boolean = true,
    /** Skip blocks detected as footers / running footers. */
    val skipFooters: Boolean = true,
    /** Skip standalone page numbers. */
    val skipPageNumbers: Boolean = true,
    /** Skip footnotes (small-font blocks in the bottom margin). */
    val skipFootnotes: Boolean = true,
    /** Skip figure/table captions. */
    val skipCaptions: Boolean = false,
    /**
     * Font-size ratio below which a block is treated as a footnote.
     * Default 0.85 means blocks whose font size is < 85 % of the median
     * body size are considered footnotes.
     */
    val footnoteSizeRatio: Float = 0.85f,
    /**
     * Minimum number of pages a text+position pattern must repeat on
     * before it is flagged as a running header/footer.
     */
    val repeatedTextMinPages: Int = 3,
)

/** Repository interface for reading/writing [SmartReadingPrefs]. */
interface SmartReadingPreferences {
    suspend fun getPrefs(): SmartReadingPrefs
    suspend fun savePrefs(prefs: SmartReadingPrefs)
}
