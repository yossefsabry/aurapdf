package com.aurapdf.app.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurapdf.app.domain.theme.AppThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single source of truth for the app-wide dark/light mode toggle.
 * Hosted in [MainActivity] so it outlives individual screens.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    private val prefs: AppThemePreferences,
) : ViewModel() {

    /**
     * null = follow system; true = dark; false = light.
     * The UI reads [isDark] after resolving the system default.
     */
    val overrideDark: StateFlow<Boolean?> = prefs.isDarkMode()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun toggle(currentlyDark: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(!currentlyDark) }
    }
}
