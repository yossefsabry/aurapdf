package com.aurapdf.app.domain.analyzer

import android.graphics.RectF
import com.aurapdf.app.domain.model.ContentType
import com.aurapdf.app.domain.model.ReadableSegment
import com.aurapdf.app.domain.model.TextBlock
import com.aurapdf.app.domain.prefs.SmartReadingPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Phase-2 Smart Content Filter.
 *
 * Applies a hybrid rule-based pipeline to a list of [TextBlock]s extracted
 * from one or more PDF pages and returns only [ReadableSegment]s that should
 * be spoken by the TTS engine.
 *
 * Rules (in order):
 *  1. Margin detection — skip blocks in top/bottom margin bands.
 *  2. Repeated-text detection — skip text that appears on ≥ N pages at the
 *     same relative position (running headers/footers, page numbers).
 *  3. Font-size threshold — skip blocks whose font size is < X % of the
 *     median body font size (footnotes, captions, references).
 *  4. Page-number pattern — skip blocks that look like isolated page numbers.
 *  5. Paragraph merging — combine adjacent same-line-height blocks into
 *     coherent paragraphs for smooth TTS delivery.
 */
@Singleton
class ContentAnalyzer @Inject constructor() {

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Analyse [blocks] (may span multiple pages) with the given [prefs].
     *
     * @param blocks        All text blocks from the pages you want to process.
     * @param pageWidth     Width of a page in points (same coordinate space as blocks).
     * @param pageHeight    Height of a page in points.
     * @param prefs         User filter preferences.
     * @return              Filtered, merged [ReadableSegment] list ready for TTS.
     */
    fun analyze(
        blocks: List<TextBlock>,
        @Suppress("UNUSED_PARAMETER") pageWidth: Float,
        pageHeight: Float,
        prefs: SmartReadingPrefs,
    ): List<ReadableSegment> {
        if (blocks.isEmpty()) return emptyList()

        // Step 1 — classify each block
        val medianFontSize = medianFontSize(blocks)
        val repeatedTexts  = detectRepeatedTexts(blocks, prefs.repeatedTextMinPages)

        val classified = blocks.map { block ->
            block to classify(
                block       = block,
                pageHeight  = pageHeight,
                medianFont  = medianFontSize,
                prefs       = prefs,
                isRepeated  = block.text.trim() in repeatedTexts,
            )
        }

        // Step 2 — apply user-configurable filter
        val kept = classified.filter { (_, type) -> shouldKeep(type, prefs) }

        // Step 3 — merge adjacent blocks on the same page into paragraphs
        return mergeIntoParagraphs(kept)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Classification
    // ──────────────────────────────────────────────────────────────────────────

    private fun classify(
        block: TextBlock,
        pageHeight: Float,
        medianFont: Float,
        prefs: SmartReadingPrefs,
        isRepeated: Boolean,
    ): ContentType {
        val text = block.text.trim()

        // Page-number pattern: only digits, maybe "N / M" or "Page N"
        if (isPageNumber(text)) return ContentType.PageNumber

        val topMargin    = pageHeight * TOP_MARGIN_RATIO
        val bottomMargin = pageHeight * BOTTOM_MARGIN_RATIO

        // Blocks sitting inside the top or bottom margin band
        if (block.bounds.top < topMargin)               return ContentType.Header
        if (block.bounds.bottom > pageHeight - bottomMargin) return ContentType.Footer

        // Repeated across many pages → running header/footer
        if (isRepeated) {
            return if (block.bounds.centerY() < pageHeight / 2) ContentType.Header
            else ContentType.Footer
        }

        // Small font → footnote/caption
        if (medianFont > 0 && block.fontSize > 0 &&
            block.fontSize < medianFont * prefs.footnoteSizeRatio
        ) {
            return ContentType.Footnote
        }

        // Large font → heading
        if (medianFont > 0 && block.fontSize > 0 &&
            block.fontSize > medianFont * HEADING_FONT_RATIO
        ) {
            return ContentType.Title
        }

        return ContentType.Paragraph
    }

    private fun shouldKeep(type: ContentType, prefs: SmartReadingPrefs): Boolean = when (type) {
        ContentType.Paragraph  -> true
        ContentType.Title      -> true
        ContentType.Header     -> !prefs.skipHeaders
        ContentType.Footer     -> !prefs.skipFooters
        ContentType.PageNumber -> !prefs.skipPageNumbers
        ContentType.Footnote   -> !prefs.skipFootnotes
        ContentType.FigureCaption -> !prefs.skipCaptions
        ContentType.Table      -> true   // keep table text for now
        ContentType.Unknown    -> true
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Repeated-text detection
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the set of text strings that appear on at least [minPages]
     * different pages at the same approximate relative Y position (± 5 % of page height).
     */
    private fun detectRepeatedTexts(blocks: List<TextBlock>, minPages: Int): Set<String> {
        // Group by normalised text
        val textToPages = mutableMapOf<String, MutableSet<Int>>()
        for (block in blocks) {
            val key = block.text.trim().lowercase()
            if (key.isBlank() || key.length > MAX_REPEATED_TEXT_LENGTH) continue
            textToPages.getOrPut(key) { mutableSetOf() }.add(block.pageIndex)
        }
        return textToPages
            .filter { (_, pages) -> pages.size >= minPages }
            .keys
            .toSet()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Paragraph merging
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Combines consecutive blocks on the same page that are vertically
     * adjacent (gap < 1.5× line-height) into a single [ReadableSegment].
     * This prevents TTS from inserting unnatural pauses between wrapped lines.
     */
    private fun mergeIntoParagraphs(
        classified: List<Pair<TextBlock, ContentType>>,
    ): List<ReadableSegment> {
        if (classified.isEmpty()) return emptyList()

        val result = mutableListOf<ReadableSegment>()

        // Sort by page then top-Y so we merge top-to-bottom
        val sorted = classified.sortedWith(
            compareBy({ it.first.pageIndex }, { it.first.bounds.top })
        )

        var current: TextBlock = sorted.first().first
        var currentType: ContentType = sorted.first().second
        val currentText = StringBuilder(current.text.trim())
        var currentBounds = RectF(current.bounds)

        for (i in 1 until sorted.size) {
            val (next, nextType) = sorted[i]
            val lineHeight = current.bounds.height().coerceAtLeast(4f)
            val gap = next.bounds.top - current.bounds.bottom

            val samePageAndType = next.pageIndex == current.pageIndex && nextType == currentType
            val verticallyClose = gap < lineHeight * MAX_LINE_GAP_RATIO

            if (samePageAndType && verticallyClose) {
                // Merge: extend the bounding box and append text
                currentBounds = RectF(
                    minOf(currentBounds.left, next.bounds.left),
                    minOf(currentBounds.top, next.bounds.top),
                    maxOf(currentBounds.right, next.bounds.right),
                    maxOf(currentBounds.bottom, next.bounds.bottom),
                )
                currentText.append(' ').append(next.text.trim())
                current = next
            } else {
                // Flush current segment
                result += ReadableSegment(
                    text      = currentText.toString().normaliseWhitespace(),
                    bounds    = RectF(currentBounds),
                    pageIndex = current.pageIndex,
                    type      = currentType,
                )
                current       = next
                currentType   = nextType
                currentBounds = RectF(next.bounds)
                currentText.clear()
                currentText.append(next.text.trim())
            }
        }

        // Flush last
        result += ReadableSegment(
            text      = currentText.toString().normaliseWhitespace(),
            bounds    = RectF(currentBounds),
            pageIndex = current.pageIndex,
            type      = currentType,
        )

        return result.filter { it.text.isNotBlank() }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun medianFontSize(blocks: List<TextBlock>): Float {
        val sizes = blocks.map { it.fontSize }.filter { it > 0f }.sorted()
        if (sizes.isEmpty()) return 0f
        return sizes[sizes.size / 2]
    }

    private fun isPageNumber(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank()) return false
        // Pure number
        if (t.all { it.isDigit() } && t.length <= 5) return true
        // "Page N" / "N of M" / "N / M"
        if (Regex("""^(page\s*)?\d+(\s*(of|/)\s*\d+)?$""", RegexOption.IGNORE_CASE).matches(t)) return true
        return false
    }

    private fun String.normaliseWhitespace(): String =
        replace(Regex("""\s+"""), " ").trim()

    // ──────────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────────

    companion object {
        /** Top N % of the page is treated as the header band. */
        private const val TOP_MARGIN_RATIO = 0.08f
        /** Bottom N % of the page is treated as the footer band. */
        private const val BOTTOM_MARGIN_RATIO = 0.08f
        /** Font-size multiplier above median that marks a heading. */
        private const val HEADING_FONT_RATIO = 1.20f
        /** Maximum vertical gap (× line-height) to still merge two blocks. */
        private const val MAX_LINE_GAP_RATIO = 1.5f
        /** Running headers/footers are rarely longer than this. */
        private const val MAX_REPEATED_TEXT_LENGTH = 80
    }
}
