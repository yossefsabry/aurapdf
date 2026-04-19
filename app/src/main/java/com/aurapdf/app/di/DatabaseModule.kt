package com.aurapdf.app.di

import android.content.Context
import androidx.room.Room
import com.aurapdf.app.data.local.dao.PdfDocumentDao
import com.aurapdf.app.data.local.db.AuraPdfDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AuraPdfDatabase =
        Room.databaseBuilder(
            context,
            AuraPdfDatabase::class.java,
            AuraPdfDatabase.DATABASE_NAME
        ).build()

    @Provides
    @Singleton
    fun providePdfDocumentDao(database: AuraPdfDatabase): PdfDocumentDao =
        database.pdfDocumentDao()
}
