package com.aurapdf.app.domain.model

import android.graphics.RectF

/**
 * A single text run extracted from a PDF page via PDFBox.
 * Bounds are in page-space coordinates (points, origin top-left).
 */
data class TextBlock(
    val text: String,
    val bounds: RectF,
    /** Approximate font size in points (may be 0 if PDFBox cannot determine it). */
    val fontSize: Float,
    val fontName: String = "",
    /** Page index (0-based) this block belongs to. */
    val pageIndex: Int,
)
