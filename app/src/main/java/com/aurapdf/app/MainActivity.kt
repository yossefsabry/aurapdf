package com.aurapdf.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurapdf.app.domain.tts.ReadingController
import com.aurapdf.app.domain.tts.TtsState
import com.aurapdf.app.presentation.navigation.AuraPdfNavGraph
import com.aurapdf.app.presentation.theme.AppThemeViewModel
import com.aurapdf.app.presentation.theme.LocalThemeController
import com.aurapdf.app.presentation.theme.ThemeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeVm: AppThemeViewModel by viewModels()

    /**
     * Injected so we can intercept volume keys to skip TTS segments while
     * reading is active, without coupling to any specific ViewModel.
     */
    @Inject lateinit var readingController: ReadingController

    // Launcher for the POST_NOTIFICATIONS permission (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — no action needed; the OS handles silently */ }

    /** Timestamp of last Volume Down ACTION_DOWN — used for double-press translation. */
    private var lastVolumeDownTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            // null = follow system, true/false = user override
            val overrideDark by themeVm.overrideDark.collectAsStateWithLifecycle()
            val systemDark   = isSystemInDarkTheme()
            val isDark       = overrideDark ?: systemDark

            val themeController = ThemeController(
                isDark = isDark,
                toggle = { themeVm.toggle(isDark) },
            )

            CompositionLocalProvider(LocalThemeController provides themeController) {
                AuraPdfTheme(darkTheme = isDark) {
                    Surface {
                        AuraPdfNavGraph()
                    }
                }
            }
        }
    }

    /**
     * Intercept volume keys to skip TTS paragraphs while reading is active.
     *
     * Volume Up   -> skip forward one segment  (next paragraph)
     * Volume Down -> single press: skip back one segment
     *                double press (within 300ms): translate current segment
     *
     * When TTS is idle the keys pass through normally so the user can still
     * adjust media/ring volume.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val state    = readingController.ttsState.value
            val isActive = state is TtsState.Speaking || state is TtsState.Paused
            if (isActive) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        readingController.skipForward()
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        val now = System.currentTimeMillis()
                        if (now - lastVolumeDownTime < DOUBLE_PRESS_WINDOW_MS) {
                            // Double-press -> translate current segment
                            readingController.requestTranslation()
                            lastVolumeDownTime = 0L
                        } else {
                            // Single press -> skip back
                            lastVolumeDownTime = now
                            readingController.skipBack()
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private const val DOUBLE_PRESS_WINDOW_MS = 300L

@Composable
fun AuraPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Monochrome/neutral colour scheme — black & white brand identity
    val lightColors = lightColorScheme(
        primary              = Color(0xFF1A1A1A),
        onPrimary            = Color.White,
        primaryContainer     = Color(0xFFE8E8E8),
        onPrimaryContainer   = Color(0xFF1A1A1A),
        secondary            = Color(0xFF555555),
        onSecondary          = Color.White,
        secondaryContainer   = Color(0xFFDDDDDD),
        onSecondaryContainer = Color(0xFF1A1A1A),
        background           = Color(0xFFF5F5F5),
        onBackground         = Color(0xFF1A1A1A),
        surface              = Color(0xFFFFFFFF),
        onSurface            = Color(0xFF1A1A1A),
        surfaceVariant       = Color(0xFFEEEEEE),
        onSurfaceVariant     = Color(0xFF555555),
        outline              = Color(0xFFBBBBBB),
        outlineVariant       = Color(0xFFDDDDDD),
    )

    val darkColors = darkColorScheme(
        primary              = Color(0xFFE8E8E8),
        onPrimary            = Color(0xFF1A1A1A),
        primaryContainer     = Color(0xFF333333),
        onPrimaryContainer   = Color(0xFFE8E8E8),
        secondary            = Color(0xFFAAAAAA),
        onSecondary          = Color(0xFF1A1A1A),
        secondaryContainer   = Color(0xFF3A3A3A),
        onSecondaryContainer = Color(0xFFE8E8E8),
        background           = Color(0xFF111111),
        onBackground         = Color(0xFFE8E8E8),
        surface              = Color(0xFF1C1C1C),
        onSurface            = Color(0xFFE8E8E8),
        surfaceVariant       = Color(0xFF2A2A2A),
        onSurfaceVariant     = Color(0xFFAAAAAA),
        outline              = Color(0xFF555555),
        outlineVariant       = Color(0xFF333333),
    )

    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        content = content
    )
}
