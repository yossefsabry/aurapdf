package com.aurapdf.app.domain.tts

import android.graphics.RectF
import com.aurapdf.app.domain.model.ReadableSegment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Master clock for TTS + scroll/highlight sync.
 *
 * Converts [ReadableSegment] lists into a flat [TtsWord] list via
 * [loadSegments], then drives [TtsEngine] and exposes [currentWordIndex]
 * so the UI can highlight and auto-scroll to the word being spoken.
 *
 * Android [android.speech.tts.TextToSpeech] has no native resume, so
 * [resume] re-speaks from the last word boundary callback received before
 * [pause] was called.
 *
 * [skipForward] / [skipBack] jump by one paragraph/segment — intended as
 * the handler for volume-key presses while TTS is active.
 *
 * This class is a singleton so both [PdfViewerViewModel] and
 * [com.aurapdf.app.service.ReadingForegroundService] share the same state.
 */
@Singleton
class ReadingController @Inject constructor(
    private val engine: TtsEngine,
) {

    // ──────────────────────────────────────────────────────────────────────────
    // Public state
    // ──────────────────────────────────────────────────────────────────────────

    /** Mirrors the engine's playback state — collected by ViewModel and Service. */
    val ttsState: StateFlow<TtsState> get() = engine.state

    /**
     * Absolute index into the loaded word list of the word currently being
     * spoken.  -1 means not currently speaking.
     */
    private val _currentWordIndex = MutableStateFlow(-1)
    val currentWordIndex: StateFlow<Int> = _currentWordIndex.asStateFlow()

    /**
     * The [TtsWord] currently being spoken, or null when idle.
     * Consumed by the UI for auto-scroll and page-highlight.
     */
    private val _currentWord = MutableStateFlow<TtsWord?>(null)
    val currentWord: StateFlow<TtsWord?> = _currentWord.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // Internal state
    // ──────────────────────────────────────────────────────────────────────────

    private var words: List<TtsWord> = emptyList()
    private var fullText: String = ""

    /** Last absolute word index confirmed by a word-boundary callback. */
    private var pausedWordIndex = 0

    /**
     * Absolute word index of the first word of each segment.
     * Used by [skipForward] and [skipBack] for paragraph-level navigation.
     */
    private var segmentBoundaries: List<Int> = emptyList()

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Convert [segments] into the flat TTS utterance + word list.
     * Must be called before [startFrom].  Safe to call multiple times
     * (e.g. when the user navigates to a new page).
     */
    fun loadSegments(segments: List<ReadableSegment>) {
        val (text, wordList, boundaries) = segmentsToTtsContent(segments)
        fullText = text
        words = wordList
        segmentBoundaries = boundaries
        pausedWordIndex = 0
        _currentWordIndex.value = -1
        _currentWord.value = null
    }

    /** Start (or restart) speaking from [wordIndex] (default: the beginning). */
    fun startFrom(wordIndex: Int = 0) {
        val clamped = wordIndex.coerceIn(0, maxOf(0, words.size - 1))
        speakFrom(clamped)
    }

    /** Pause mid-utterance.  The engine stops TTS; [resume] restarts from [pausedWordIndex]. */
    fun pause() {
        engine.pause()
        // pausedWordIndex is kept current by the per-word boundary callback.
    }

    /**
     * Resume from where we paused.
     * Re-speaks from [pausedWordIndex] because Android TTS has no native resume.
     */
    fun resume() {
        speakFrom(pausedWordIndex.coerceIn(0, maxOf(0, words.size - 1)))
    }

    /** Stop speaking and reset position to the beginning. */
    fun stop() {
        engine.stop()
        _currentWordIndex.value = -1
        _currentWord.value = null
        pausedWordIndex = 0
    }

    /**
     * Skip forward to the first word of the next segment.
     * If already in the last segment, does nothing.
     * Intended as the Volume Up handler.
     */
    fun skipForward() {
        val nextBoundary = segmentBoundaries.firstOrNull { it > pausedWordIndex } ?: return
        speakFrom(nextBoundary)
    }

    /**
     * Skip back to the start of the current segment.  If we are already within
     * [SKIP_BACK_THRESHOLD] words of the current segment's beginning, skips to
     * the previous segment instead.
     * Intended as the Volume Down handler.
     */
    fun skipBack() {
        val currentBoundary = segmentBoundaries.lastOrNull { it <= pausedWordIndex } ?: 0
        val target = if (pausedWordIndex - currentBoundary > SKIP_BACK_THRESHOLD) {
            // We're well inside the current segment — go to its start
            currentBoundary
        } else {
            // We're near the top — jump to the previous segment (or word 0)
            segmentBoundaries.lastOrNull { it < currentBoundary } ?: 0
        }
        speakFrom(target)
    }

    /** 0.5 = half speed, 1.0 = normal, 2.5 = max. */
    fun setSpeed(rate: Float) = engine.setSpeed(rate)

    /**
     * Return the full text of the segment that is currently being spoken,
     * or null if TTS is idle / no segments loaded.
     */
    fun currentSegmentText(): String? {
        val idx = pausedWordIndex
        if (idx < 0 || words.isEmpty()) return null
        val segIdx = segmentBoundaries.indexOfLast { it <= idx }
        if (segIdx < 0) return null
        val segStart = segmentBoundaries[segIdx]
        val segEnd = segmentBoundaries.getOrNull(segIdx + 1) ?: words.size
        return words.subList(segStart, segEnd).joinToString(" ") { it.text }
    }

    // ── Translation request event (fired by MainActivity double-press) ──────

    private val _translationRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val translationRequested: SharedFlow<Unit> = _translationRequested.asSharedFlow()

    /** Fire a translation-request event (called from [MainActivity] on double-press Volume Down). */
    fun requestTranslation() {
        _translationRequested.tryEmit(Unit)
    }

    /** Release native TTS resources.  Call from ViewModel.onCleared(). */
    fun shutdown() = engine.shutdown()

    // ──────────────────────────────────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────────────────────────────────

    private fun speakFrom(startIdx: Int) {
        if (words.isEmpty() || fullText.isBlank()) return
        val clamped = startIdx.coerceIn(0, words.size - 1)
        val startChar = words[clamped].startChar

        // Slice the utterance string and re-index char offsets relative to the slice
        val textSlice = fullText.substring(startChar)
        val sliceWords = words.drop(clamped).map { w ->
            w.copy(
                startChar = w.startChar - startChar,
                endChar   = w.endChar   - startChar,
            )
        }

        engine.speak(textSlice, sliceWords) { relativeIndex ->
            val absoluteIndex = clamped + relativeIndex
            _currentWordIndex.value = absoluteIndex
            _currentWord.value = words.getOrNull(absoluteIndex)
            pausedWordIndex = absoluteIndex
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Segment → TtsWord mapping
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Flatten [segments] into a Triple of:
     *  1. A single utterance [String] (all segment texts joined with spaces)
     *  2. A [List] of [TtsWord] with char offsets and bounding boxes
     *  3. A [List] of absolute word indices marking the start of each segment
     *     (used for paragraph-level skip navigation)
     */
    private fun segmentsToTtsContent(
        segments: List<ReadableSegment>,
    ): Triple<String, List<TtsWord>, List<Int>> {
        val sb         = StringBuilder()
        val words      = mutableListOf<TtsWord>()
        val boundaries = mutableListOf<Int>()
        val wordRegex  = Regex("""\S+""")

        for (segment in segments) {
            val segStart = sb.length
            boundaries.add(words.size)   // word index of this segment's first word
            for (match in wordRegex.findAll(segment.text)) {
                words.add(
                    TtsWord(
                        text      = match.value,
                        startChar = segStart + match.range.first,
                        endChar   = segStart + match.range.last + 1,
                        bounds    = RectF(segment.bounds),
                        pageIndex = segment.pageIndex,
                    )
                )
            }
            sb.append(segment.text)
            if (!segment.text.endsWith(' ')) sb.append(' ')
        }

        return Triple(sb.toString(), words, boundaries)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────────

    private companion object {
        /**
         * If [pausedWordIndex] is more than this many words past the current
         * segment's start, [skipBack] returns to that start rather than jumping
         * to the previous segment.
         */
        const val SKIP_BACK_THRESHOLD = 3
    }
}
