# Phase 5: Translation via Double-Press Volume Key — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add offline translation of the current TTS segment via ML Kit On-Device Translation, triggered by double-pressing Volume Down, displayed in a bottom sheet overlay, with target language configurable in settings.

**Architecture:** Domain interface `TranslationEngine` backed by `MlKitTranslationEngine` (data layer), wired via Hilt. `ReadingController` exposes current segment text. `MainActivity` detects double-press Volume Down (300ms window) and delegates to `PdfViewerViewModel.translateCurrentSegment()`. Results shown in a Material3 modal bottom sheet. Translation preferences (target language) stored in existing DataStore. In-memory `LruCache` avoids redundant translations.

**Tech Stack:** ML Kit Translate (`com.google.mlkit:translate:17.0.3`), existing DataStore, Hilt, Compose Material3 ModalBottomSheet.

---

## File Structure

### New Files
| File | Purpose |
|------|---------|
| `domain/translation/TranslationEngine.kt` | Interface: `translate(text, from, to): Result<String>`, `ensureModelDownloaded(from, to)`, `availableLanguages()` |
| `domain/translation/TranslationResult.kt` | Data class: `originalText`, `translatedText`, `sourceLanguage`, `targetLanguage` |
| `domain/translation/TranslationPreferences.kt` | Interface + `TranslationPrefs` data class (targetLanguageCode: String) |
| `data/translation/MlKitTranslationEngine.kt` | ML Kit implementation with `LruCache<String, String>` |
| `data/prefs/TranslationPreferencesImpl.kt` | DataStore-backed implementation |
| `di/TranslationModule.kt` | Hilt bindings for TranslationEngine + TranslationPreferences |
| `presentation/viewer/TranslationBottomSheet.kt` | Modal bottom sheet composable |

### Modified Files
| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add ML Kit translate dependency |
| `domain/tts/ReadingController.kt` | Add `currentSegmentText(): String?` method |
| `presentation/viewer/PdfViewerViewModel.kt` | Add translation state flows + `translateCurrentSegment()` + `dismissTranslation()` + `setTargetLanguage()` |
| `presentation/viewer/PdfViewerScreen.kt` | Show `TranslationBottomSheet` when translation result is non-null |
| `presentation/viewer/settings/SmartReadingSettingsDialog.kt` | Add target language dropdown section |
| `MainActivity.kt` | Double-press Volume Down detection (300ms window) |

---

## Task 1: Add ML Kit Translate dependency

**Files:**
- Modify: `app/build.gradle.kts:114` (after PDFBox line)

- [ ] **Step 1: Add dependency**

Add after the PDFBox line (line 114):
```kotlin
// Offline translation (ML Kit On-Device)
implementation("com.google.mlkit:translate:17.0.3")
```

- [ ] **Step 2: Sync and verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 2: Domain layer — TranslationEngine interface + data classes

**Files:**
- Create: `app/src/main/java/com/aurapdf/app/domain/translation/TranslationEngine.kt`
- Create: `app/src/main/java/com/aurapdf/app/domain/translation/TranslationResult.kt`
- Create: `app/src/main/java/com/aurapdf/app/domain/translation/TranslationPreferences.kt`

- [ ] **Step 1: Create TranslationEngine interface**

```kotlin
package com.aurapdf.app.domain.translation

/**
 * Abstraction over an offline translation engine.
 * Implementations handle model lifecycle and caching.
 */
interface TranslationEngine {
    /** Translate [text] from [sourceLanguage] to [targetLanguage]. Language codes are BCP-47 (e.g. "en", "ar", "es"). */
    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): Result<String>

    /** Ensure the model for [sourceLanguage] → [targetLanguage] is downloaded. Suspends until ready. */
    suspend fun ensureModelReady(sourceLanguage: String, targetLanguage: String): Result<Unit>

    /** Return the list of supported BCP-47 language codes. */
    fun availableLanguages(): List<String>
}
```

- [ ] **Step 2: Create TranslationResult data class**

```kotlin
package com.aurapdf.app.domain.translation

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
)
```

- [ ] **Step 3: Create TranslationPreferences interface + data class**

```kotlin
package com.aurapdf.app.domain.translation

/**
 * User's translation settings — persisted in DataStore.
 */
data class TranslationPrefs(
    /** BCP-47 code. Empty string means translation is not configured. */
    val targetLanguage: String = "",
    /** BCP-47 code for source language. "en" by default. */
    val sourceLanguage: String = "en",
)

interface TranslationPreferences {
    suspend fun getPrefs(): TranslationPrefs
    suspend fun savePrefs(prefs: TranslationPrefs)
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 3: Data layer — MlKitTranslationEngine

**Files:**
- Create: `app/src/main/java/com/aurapdf/app/data/translation/MlKitTranslationEngine.kt`

- [ ] **Step 1: Implement MlKitTranslationEngine**

```kotlin
package com.aurapdf.app.data.translation

import android.util.LruCache
import com.aurapdf.app.domain.translation.TranslationEngine
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitTranslationEngine @Inject constructor() : TranslationEngine {

    /** Cache keyed by "$sourceLanguage|$targetLanguage|$text" */
    private val cache = LruCache<String, String>(200)

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): Result<String> = runCatching {
        val cacheKey = "$sourceLanguage|$targetLanguage|$text"
        cache.get(cacheKey)?.let { return Result.success(it) }

        val sourceLang = TranslateLanguage.fromLanguageTag(sourceLanguage)
            ?: error("Unsupported source language: $sourceLanguage")
        val targetLang = TranslateLanguage.fromLanguageTag(targetLanguage)
            ?: error("Unsupported target language: $targetLanguage")

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)

        try {
            // Ensure model is downloaded
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()

            val result = translator.translate(text).await()
            cache.put(cacheKey, result)
            result
        } finally {
            translator.close()
        }
    }

    override suspend fun ensureModelReady(
        sourceLanguage: String,
        targetLanguage: String,
    ): Result<Unit> = runCatching {
        val sourceLang = TranslateLanguage.fromLanguageTag(sourceLanguage)
            ?: error("Unsupported source language: $sourceLanguage")
        val targetLang = TranslateLanguage.fromLanguageTag(targetLanguage)
            ?: error("Unsupported target language: $targetLanguage")

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
        } finally {
            translator.close()
        }
    }

    override fun availableLanguages(): List<String> =
        TranslateLanguage.getAllLanguages()
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 4: Data layer — TranslationPreferencesImpl

**Files:**
- Create: `app/src/main/java/com/aurapdf/app/data/prefs/TranslationPreferencesImpl.kt`

- [ ] **Step 1: Implement TranslationPreferencesImpl**

Follow the exact same pattern as `SmartReadingPreferencesImpl.kt` — uses the same shared DataStore instance.

```kotlin
package com.aurapdf.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aurapdf.app.domain.translation.TranslationPrefs
import com.aurapdf.app.domain.translation.TranslationPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class TranslationPreferencesImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TranslationPreferences {

    private object Keys {
        val TARGET_LANGUAGE = stringPreferencesKey("translation_target_language")
        val SOURCE_LANGUAGE = stringPreferencesKey("translation_source_language")
    }

    override suspend fun getPrefs(): TranslationPrefs {
        val p = dataStore.data.first()
        return TranslationPrefs(
            targetLanguage = p[Keys.TARGET_LANGUAGE] ?: "",
            sourceLanguage = p[Keys.SOURCE_LANGUAGE] ?: "en",
        )
    }

    override suspend fun savePrefs(prefs: TranslationPrefs) {
        dataStore.edit { p ->
            p[Keys.TARGET_LANGUAGE] = prefs.targetLanguage
            p[Keys.SOURCE_LANGUAGE] = prefs.sourceLanguage
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 5: DI — TranslationModule

**Files:**
- Create: `app/src/main/java/com/aurapdf/app/di/TranslationModule.kt`
- Modify: `app/src/main/java/com/aurapdf/app/di/PreferencesModule.kt:26-30` (add TranslationPreferences binding)

- [ ] **Step 1: Create TranslationModule**

```kotlin
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
```

- [ ] **Step 2: Add TranslationPreferences binding to PreferencesModule**

In `PreferencesModule.kt`, add import for `TranslationPreferencesImpl` and `TranslationPreferences`, then add:
```kotlin
@Binds
@Singleton
abstract fun bindTranslationPreferences(
    impl: TranslationPreferencesImpl
): TranslationPreferences
```

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 6: Expose current segment text from ReadingController

**Files:**
- Modify: `app/src/main/java/com/aurapdf/app/domain/tts/ReadingController.kt`

- [ ] **Step 1: Add `currentSegmentText()` method**

Add to the "Public API" section (after `setSpeed`):

```kotlin
/**
 * Return the full text of the segment that is currently being spoken,
 * or null if TTS is idle / no segments loaded.
 */
fun currentSegmentText(): String? {
    val idx = pausedWordIndex
    if (idx < 0 || words.isEmpty()) return null
    // Find which segment boundary this word belongs to
    val segIdx = segmentBoundaries.indexOfLast { it <= idx }
    if (segIdx < 0) return null
    val segStart = segmentBoundaries[segIdx]
    val segEnd = segmentBoundaries.getOrNull(segIdx + 1) ?: words.size
    return words.subList(segStart, segEnd).joinToString(" ") { it.text }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 7: Update PdfViewerViewModel with translation logic

**Files:**
- Modify: `app/src/main/java/com/aurapdf/app/presentation/viewer/PdfViewerViewModel.kt`

- [ ] **Step 1: Add translation dependencies to constructor**

Add to the constructor parameters:
```kotlin
private val translationEngine: TranslationEngine,
private val translationPreferences: TranslationPreferences,
```

Add imports:
```kotlin
import com.aurapdf.app.domain.translation.TranslationEngine
import com.aurapdf.app.domain.translation.TranslationPreferences
import com.aurapdf.app.domain.translation.TranslationPrefs
import com.aurapdf.app.domain.translation.TranslationResult
```

- [ ] **Step 2: Add translation state flows**

Add after the `_ttsSpeed` block:
```kotlin
// ── Translation state ───────────────────────────────────────────────
private val _translationResult = MutableStateFlow<TranslationResult?>(null)
val translationResult: StateFlow<TranslationResult?> = _translationResult.asStateFlow()

private val _translationLoading = MutableStateFlow(false)
val translationLoading: StateFlow<Boolean> = _translationLoading.asStateFlow()

private val _translationPrefs = MutableStateFlow(TranslationPrefs())
val translationPrefs: StateFlow<TranslationPrefs> = _translationPrefs.asStateFlow()
```

- [ ] **Step 3: Load translation prefs in loadDocument()**

At the end of the `try` block inside `withContext(Dispatchers.IO)`, after `_uiState.value = ViewerUiState.Ready(...)`:
```kotlin
_translationPrefs.value = translationPreferences.getPrefs()
```

- [ ] **Step 4: Add translateCurrentSegment() method**

Add in a new "Translation controls" section:
```kotlin
// ──────────────────────────────────────────────────────────────────────────
// Translation controls
// ──────────────────────────────────────────────────────────────────────────

/** Translate the segment currently being spoken by TTS. */
fun translateCurrentSegment() {
    val text = readingController.currentSegmentText() ?: return
    val prefs = _translationPrefs.value
    if (prefs.targetLanguage.isBlank()) return // No target language configured

    viewModelScope.launch {
        _translationLoading.value = true
        val result = translationEngine.translate(
            text           = text,
            sourceLanguage = prefs.sourceLanguage,
            targetLanguage = prefs.targetLanguage,
        )
        result.onSuccess { translated ->
            _translationResult.value = TranslationResult(
                originalText   = text,
                translatedText = translated,
                sourceLanguage = prefs.sourceLanguage,
                targetLanguage = prefs.targetLanguage,
            )
        }
        _translationLoading.value = false
    }
}

fun dismissTranslation() {
    _translationResult.value = null
}

fun setTranslationPrefs(prefs: TranslationPrefs) {
    viewModelScope.launch {
        translationPreferences.savePrefs(prefs)
        _translationPrefs.value = prefs
    }
}
```

- [ ] **Step 5: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 8: Double-press Volume Down detection in MainActivity

**Files:**
- Modify: `app/src/main/java/com/aurapdf/app/MainActivity.kt:82-100`

- [ ] **Step 1: Add double-press tracking state**

Add fields to `MainActivity`:
```kotlin
/** Timestamp of last Volume Down ACTION_DOWN — used for double-press translation. */
private var lastVolumeDownTime = 0L
private companion object {
    const val DOUBLE_PRESS_WINDOW_MS = 300L
}
```

- [ ] **Step 2: Rewrite dispatchKeyEvent for double-press detection**

Replace the existing `dispatchKeyEvent` with logic that:
- On Volume Down: if second press is within 300ms, call `translateCurrentSegment()` on the current viewer ViewModel (via ReadingController event or a shared callback). Otherwise, post a delayed single-press action (skip back).
- On Volume Up: keep as single-press skip forward.

Since `MainActivity` doesn't hold a reference to `PdfViewerViewModel`, we need a lightweight event channel. The simplest approach: add a `translationRequested` `MutableSharedFlow<Unit>` to `ReadingController` (the singleton both share), and have the ViewModel collect it.

In `ReadingController`, add:
```kotlin
private val _translationRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val translationRequested: SharedFlow<Unit> = _translationRequested.asSharedFlow()

fun requestTranslation() {
    _translationRequested.tryEmit(Unit)
}
```

In `MainActivity.dispatchKeyEvent`:
```kotlin
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
                        // Double-press → translate
                        readingController.requestTranslation()
                        lastVolumeDownTime = 0L
                    } else {
                        // Single press → skip back
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
```

In `PdfViewerViewModel.init {}` (or `loadDocument`), collect translation requests:
```kotlin
viewModelScope.launch {
    readingController.translationRequested.collect {
        translateCurrentSegment()
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 9: TranslationBottomSheet composable

**Files:**
- Create: `app/src/main/java/com/aurapdf/app/presentation/viewer/TranslationBottomSheet.kt`

- [ ] **Step 1: Create TranslationBottomSheet**

```kotlin
package com.aurapdf.app.presentation.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.aurapdf.app.domain.translation.TranslationResult
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationBottomSheet(
    result: TranslationResult,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = MaterialTheme.colorScheme.surface,
        contentColor      = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Header
            Row(
                modifier          = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text  = "Translation",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text  = "${languageDisplayName(result.sourceLanguage)} -> ${languageDisplayName(result.targetLanguage)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Original text
            Text(
                text  = "Original",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = result.originalText,
                style     = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Translated text
            Text(
                text  = "Translated",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = result.translatedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))

            // Dismiss button
            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Dismiss")
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun languageDisplayName(code: String): String =
    Locale(code).displayLanguage.replaceFirstChar { it.uppercase() }
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 10: Wire translation into PdfViewerScreen

**Files:**
- Modify: `app/src/main/java/com/aurapdf/app/presentation/viewer/PdfViewerScreen.kt`

- [ ] **Step 1: Collect translation state and show bottom sheet**

In `PdfViewerScreen`, add after existing `collectAsStateWithLifecycle` calls:
```kotlin
val translationResult  by viewModel.translationResult.collectAsStateWithLifecycle()
val translationLoading by viewModel.translationLoading.collectAsStateWithLifecycle()
```

After the `SmartReadingSettingsDialog` block (at the end of the function), add:
```kotlin
// Translation bottom sheet
translationResult?.let { result ->
    TranslationBottomSheet(
        result    = result,
        onDismiss = viewModel::dismissTranslation,
    )
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 11: Add target language picker to settings dialog

**Files:**
- Modify: `app/src/main/java/com/aurapdf/app/presentation/viewer/settings/SmartReadingSettingsDialog.kt`
- Modify: `app/src/main/java/com/aurapdf/app/presentation/viewer/PdfViewerScreen.kt` (pass translation prefs to dialog)
- Modify: `app/src/main/java/com/aurapdf/app/presentation/viewer/PdfViewerViewModel.kt` (wire through)

- [ ] **Step 1: Add translation section to SmartReadingSettingsDialog**

Update the dialog's function signature to accept translation props:
```kotlin
fun SmartReadingSettingsDialog(
    prefs: SmartReadingPrefs,
    translationPrefs: TranslationPrefs,
    availableLanguages: List<String>,
    onPrefsChanged: (SmartReadingPrefs) -> Unit,
    onTranslationPrefsChanged: (TranslationPrefs) -> Unit,
    onDismiss: () -> Unit,
)
```

After the existing toggle rows, add a new "Translation" section with:
- A divider + "Translation" header
- A dropdown (`ExposedDropdownMenuBox`) for target language selection from `availableLanguages`
- Display language names using `Locale(code).displayLanguage`

- [ ] **Step 2: Update PdfViewerScreen to pass translation props to dialog**

Collect `translationPrefs` and pass to the dialog along with `viewModel.availableLanguages`.
Add `val availableLanguages: List<String>` property to `PdfViewerViewModel` that calls `translationEngine.availableLanguages()`.

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 12: Final integration build + verify

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify no lint errors on new files**

Run: `./gradlew lintDebug` (informational — may have pre-existing warnings)
