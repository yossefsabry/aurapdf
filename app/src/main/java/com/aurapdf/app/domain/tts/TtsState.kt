package com.aurapdf.app.domain.tts

/** Observable playback state emitted by [TtsEngine]. */
sealed class TtsState {
    /** Engine is idle / not initialised. */
    data object Idle : TtsState()
    /** Loading voice / initialising. */
    data object Loading : TtsState()
    /** Actively speaking. [currentWord] is the index into the word list. */
    data class Speaking(val currentWordIndex: Int) : TtsState()
    /** Paused mid-utterance. */
    data class Paused(val currentWordIndex: Int) : TtsState()
    /** Finished the last segment. */
    data object Finished : TtsState()
    /** Unrecoverable error. */
    data class Error(val message: String) : TtsState()
}
