package com.aurapdf.app.presentation.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Carries the resolved dark-mode state and a toggle callback down the
 * composition tree without prop-drilling through every screen.
 *
 * Provided by [MainActivity] via [CompositionLocalProvider].
 */
data class ThemeController(
    val isDark: Boolean,
    val toggle: () -> Unit,
)

/**
 * Throws if accessed outside a [CompositionLocalProvider] that supplies a
 * [ThemeController] — intentional, to catch wiring mistakes early.
 */
val LocalThemeController = compositionLocalOf<ThemeController> {
    error("ThemeController not provided — wrap with CompositionLocalProvider")
}
