package com.aurapdf.app.di

import com.aurapdf.app.data.tts.AndroidTtsEngine
import com.aurapdf.app.domain.tts.TtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the [TtsEngine] interface to [AndroidTtsEngine].
 *
 * To upgrade to Piper ONNX in the future, replace [AndroidTtsEngine] with
 * the new implementation here — zero changes elsewhere.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {

    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: AndroidTtsEngine): TtsEngine
}
