package com.aurapdf.app.domain.tts

import android.graphics.RectF

/** A single word with its position on the page, used for scroll/highlight sync. */
data class TtsWord(
    val text: String,
    val startChar: Int,   // character offset in the full utterance string
    val endChar: Int,
    val bounds: RectF,    // page-space bounding box for highlight overlay
    val pageIndex: Int,
)
