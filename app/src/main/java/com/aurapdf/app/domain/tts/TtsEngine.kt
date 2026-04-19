package com.aurapdf.app.domain.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * Engine-agnostic TTS contract.
 *
 * Implementations:
 *  - [com.aurapdf.app.data.tts.AndroidTtsEngine]  — Android TextToSpeech (Phase 3)
 *  - PiperTtsEngine (Phase 3+ / future ONNX upgrade)
 */
interface TtsEngine {

    /** Current playback state. */
    val state: StateFlow<TtsState>

    /**
     * Start speaking [text].
     * [words] provides the positional mapping used by [ReadingController] for
     * scroll/highlight sync; the engine fires [onWordBoundary] with the index
     * into [words] as each word begins.
     */
    fun speak(
        text: String,
        words: List<TtsWord>,
        onWordBoundary: (wordIndex: Int) -> Unit,
    )

    fun pause()
    fun resume()
    fun stop()

    /** 0.5 = half speed, 1.0 = normal, 2.0 = double speed. */
    fun setSpeed(rate: Float)

    /** Release native resources. Call from ViewModel.onCleared(). */
    fun shutdown()
}
