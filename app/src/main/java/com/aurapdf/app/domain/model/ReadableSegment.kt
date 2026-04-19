package com.aurapdf.app.domain.model

import android.graphics.RectF

/**
 * A content segment that has passed through [ContentAnalyzer] and is
 * ready to be spoken by the TTS engine (Phase 3) or displayed in the
 * reading-preview panel.
 *
 * @param text  Clean, merged body text ready for TTS.
 * @param bounds Bounding box on the page — used for scroll/highlight sync (Phase 4).
 * @param pageIndex 0-based page number.
 * @param type  Classification used for display/debug.
 */
data class ReadableSegment(
    val text: String,
    val bounds: RectF,
    val pageIndex: Int,
    val type: ContentType = ContentType.Paragraph,
)
