package com.aurapdf.app.domain.usecase

import android.net.Uri
import com.aurapdf.app.data.pdf.PdfTextExtractor
import com.aurapdf.app.domain.analyzer.ContentAnalyzer
import com.aurapdf.app.domain.model.ReadableSegment
import com.aurapdf.app.domain.prefs.SmartReadingPreferences
import javax.inject.Inject

/**
 * Orchestrates text extraction + smart filtering for a range of PDF pages.
 *
 * Usage:
 * ```kotlin
 * val segments = analyzeUseCase(
 *     uri        = documentUri,
 *     fromPage   = 0,
 *     toPage     = 2,
 *     pageWidth  = 595f,   // pts (A4)
 *     pageHeight = 842f,
 * )
 * ```
 */
class AnalyzePageUseCase @Inject constructor(
    private val extractor : PdfTextExtractor,
    private val analyzer  : ContentAnalyzer,
    private val prefs     : SmartReadingPreferences,
) {
    suspend operator fun invoke(
        uri       : Uri,
        fromPage  : Int,
        toPage    : Int,
        pageWidth : Float,
        pageHeight: Float,
    ): List<ReadableSegment> {
        val blocks = extractor.extractBlocks(uri, fromPage, toPage)
        if (blocks.isEmpty()) return emptyList()

        val currentPrefs = prefs.getPrefs()
        return analyzer.analyze(
            blocks     = blocks,
            pageWidth  = pageWidth,
            pageHeight = pageHeight,
            prefs      = currentPrefs,
        )
    }
}
