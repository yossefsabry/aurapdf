package com.aurapdf.app.di

import android.content.ContentResolver
import android.content.Context
import com.aurapdf.app.data.repository.PdfRepositoryImpl
import com.aurapdf.app.domain.repository.PdfRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /** Bind the concrete implementation to the domain interface. */
    @Binds
    @Singleton
    abstract fun bindPdfRepository(impl: PdfRepositoryImpl): PdfRepository

    companion object {
        @Provides
        @Singleton
        fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
            context.contentResolver
    }
}
