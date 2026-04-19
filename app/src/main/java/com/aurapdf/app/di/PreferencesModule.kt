package com.aurapdf.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.aurapdf.app.data.prefs.SmartReadingPreferencesImpl
import com.aurapdf.app.data.prefs.TranslationPreferencesImpl
import com.aurapdf.app.data.theme.AppThemePreferencesImpl
import com.aurapdf.app.domain.prefs.SmartReadingPreferences
import com.aurapdf.app.domain.theme.AppThemePreferences
import com.aurapdf.app.domain.translation.TranslationPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "aurapdf_preferences")

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindSmartReadingPreferences(
        impl: SmartReadingPreferencesImpl
    ): SmartReadingPreferences

    @Binds
    @Singleton
    abstract fun bindAppThemePreferences(
        impl: AppThemePreferencesImpl
    ): AppThemePreferences

    @Binds
    @Singleton
    abstract fun bindTranslationPreferences(
        impl: TranslationPreferencesImpl
    ): TranslationPreferences

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.dataStore
    }
}
