package com.aurapdf.app.di

import com.aurapdf.app.data.translation.MlKitTranslationEngine
import com.aurapdf.app.domain.translation.TranslationEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModule {

    @Binds
    @Singleton
    abstract fun bindTranslationEngine(impl: MlKitTranslationEngine): TranslationEngine
}
