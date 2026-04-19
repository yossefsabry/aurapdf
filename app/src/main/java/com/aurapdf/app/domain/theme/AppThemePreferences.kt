package com.aurapdf.app.domain.theme

import kotlinx.coroutines.flow.Flow

/**
 * Persisted app-level theme preference.
 * null means "follow system" (default before the user first taps the toggle).
 */
interface AppThemePreferences {
    /** Emits the current value whenever it changes. */
    fun isDarkMode(): Flow<Boolean?>
    /** Persist the chosen value. */
    suspend fun setDarkMode(dark: Boolean)
}
