package com.aurapdf.app.presentation.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurapdf.app.domain.model.PdfDocument
import com.aurapdf.app.domain.model.ReadableSegment
import com.aurapdf.app.domain.prefs.SmartReadingPrefs
import com.aurapdf.app.domain.prefs.SmartReadingPreferences
import com.aurapdf.app.domain.repository.PdfRepository
import com.aurapdf.app.domain.tts.ReadingController
import com.aurapdf.app.domain.tts.TtsState
import com.aurapdf.app.domain.tts.TtsWord
import com.aurapdf.app.domain.translation.TranslationEngine
import com.aurapdf.app.domain.translation.TranslationPreferences
import com.aurapdf.app.domain.translation.TranslationPrefs
import com.aurapdf.app.domain.translation.TranslationResult
import com.aurapdf.app.domain.usecase.AnalyzePageUseCase
import com.aurapdf.app.domain.usecase.SaveReadingPositionUseCase
import com.aurapdf.app.service.ReadingForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Error(val message: String) : ViewerUiState
    data class Ready(
        val document: PdfDocument,
        val totalPages: Int,
        val currentPage: Int,
        val renderedPages: Map<Int, Bitmap>,
        /** Filtered segments ready for TTS. */
        val readableSegments: List<ReadableSegment> = emptyList(),
        /** Word count of readable content after filtering. */
        val readableWordCount: Int = 0,
        /** Total word count before filtering. */
        val totalWordCount: Int = 0,
        /** Current smart-reading preferences (shown in settings dialog). */
        val smartPrefs: SmartReadingPrefs = SmartReadingPrefs(),
        /** Page dimensions in PDF points — used for coordinate mapping in the UI. */
        val pdfPageWidth: Float = 595f,
        val pdfPageHeight: Float = 842f,
    ) : ViewerUiState
}

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PdfRepository,
    private val savePosition: SaveReadingPositionUseCase,
    private val analyzePage: AnalyzePageUseCase,
    private val smartPreferences: SmartReadingPreferences,
    private val readingController: ReadingController,
    private val translationEngine: TranslationEngine,
    private val translationPreferences: TranslationPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val documentId: Long = checkNotNull(savedStateHandle["documentId"])

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    // ── TTS state surfaces ──────────────────────────────────────────────────
    /** Live TTS playback state — collected independently to avoid full-state rebuilds. */
    val ttsState: StateFlow<TtsState> = readingController.ttsState

    /** Absolute index of the word currently being spoken (-1 = not speaking). */
    val currentWordIndex: StateFlow<Int> = readingController.currentWordIndex

    /**
     * The word currently being spoken (null when idle).
     * Used by the UI for auto-scroll to page and page-highlight border.
     */
    val currentTtsWord: StateFlow<TtsWord?> = readingController.currentWord

    private val _ttsSpeed = MutableStateFlow(1.0f)
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    // ── Translation state ───────────────────────────────────────────────────
    private val _translationResult = MutableStateFlow<TranslationResult?>(null)
    val translationResult: StateFlow<TranslationResult?> = _translationResult.asStateFlow()

    private val _translationLoading = MutableStateFlow(false)
    val translationLoading: StateFlow<Boolean> = _translationLoading.asStateFlow()

    private val _translationPrefs = MutableStateFlow(TranslationPrefs())
    val translationPrefs: StateFlow<TranslationPrefs> = _translationPrefs.asStateFlow()

    /** List of BCP-47 language codes supported by the translation engine. */
    val availableLanguages: List<String> = translationEngine.availableLanguages()

    // Cached page dimensions in PDF-space points (needed by the analyzer)
    private var pdfPageWidth  = 595f  // A4 default
    private var pdfPageHeight = 842f

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var documentUri: Uri? = null

    init {
        loadDocument()

        // Listen for translation requests from MainActivity double-press
        viewModelScope.launch {
            readingController.translationRequested.collect {
                translateCurrentSegment()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Document loading
    // ──────────────────────────────────────────────────────────────────────────

    private fun loadDocument() {
        viewModelScope.launch {
            val document = repository.getDocumentById(documentId)
            if (document == null) {
                _uiState.value = ViewerUiState.Error("Document not found")
                return@launch
            }

            val uri = Uri.parse(document.uri)
            documentUri = uri

            withContext(Dispatchers.IO) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: throw IllegalStateException("Cannot open: ${document.uri}")
                    parcelFileDescriptor = pfd
                    pdfRenderer = PdfRenderer(pfd)

                    val totalPages = pdfRenderer!!.pageCount
                    if (document.totalPages == 0) repository.updateTotalPages(documentId, totalPages)

                    // Cache page dimensions from the first page
                    pdfRenderer!!.openPage(0).use { page ->
                        pdfPageWidth  = page.width.toFloat()
                        pdfPageHeight = page.height.toFloat()
                    }

                    val startPage = document.lastPage.coerceIn(0, maxOf(0, totalPages - 1))
                    val initialBitmaps = renderPageRange(startPage, minOf(startPage + 2, totalPages - 1))
                    val prefs = smartPreferences.getPrefs()

                    _uiState.value = ViewerUiState.Ready(
                        document      = document.copy(totalPages = totalPages),
                        totalPages    = totalPages,
                        currentPage   = startPage,
                        renderedPages = initialBitmaps,
                        smartPrefs    = prefs,
                        pdfPageWidth  = pdfPageWidth,
                        pdfPageHeight = pdfPageHeight,
                    )

                    _translationPrefs.value = translationPreferences.getPrefs()
                } catch (e: Exception) {
                    _uiState.value = ViewerUiState.Error("Failed to open PDF: ${e.message}")
                }
            }

            analyzeCurrentPage()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Page navigation
    // ──────────────────────────────────────────────────────────────────────────

    fun onPageVisible(page: Int) {
        val current = _uiState.value as? ViewerUiState.Ready ?: return
        if (current.currentPage == page && current.renderedPages.containsKey(page)) return

        viewModelScope.launch {
            savePosition(documentId, page, 0)

            val missing = (maxOf(0, page - 1)..minOf(current.totalPages - 1, page + 1))
                .filter { !current.renderedPages.containsKey(it) }

            if (missing.isEmpty()) {
                _uiState.value = current.copy(currentPage = page)
            } else {
                val newBitmaps = withContext(Dispatchers.IO) {
                    renderPageRange(missing.first(), missing.last())
                }
                val s = _uiState.value as? ViewerUiState.Ready ?: return@launch
                _uiState.value = s.copy(
                    currentPage   = page,
                    renderedPages = s.renderedPages + newBitmaps,
                )
            }

            analyzeCurrentPage()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Smart-reading settings
    // ──────────────────────────────────────────────────────────────────────────

    fun onSmartPrefsChanged(newPrefs: SmartReadingPrefs) {
        viewModelScope.launch {
            smartPreferences.savePrefs(newPrefs)
            val s = _uiState.value as? ViewerUiState.Ready ?: return@launch
            _uiState.value = s.copy(smartPrefs = newPrefs)
            analyzeCurrentPage()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TTS controls
    // ──────────────────────────────────────────────────────────────────────────

    /** Start reading from the beginning of the current page's segments. */
    fun startReading() {
        val segments = (uiState.value as? ViewerUiState.Ready)?.readableSegments ?: return
        if (segments.isEmpty()) return

        readingController.loadSegments(segments)
        readingController.startFrom()
        ReadingForegroundService.start(context)
    }

    fun pauseReading() {
        readingController.pause()
    }

    fun resumeReading() {
        readingController.resume()
    }

    fun stopReading() {
        readingController.stop()
        ReadingForegroundService.stop(context)
    }

    fun setTtsSpeed(rate: Float) {
        _ttsSpeed.value = rate
        readingController.setSpeed(rate)
    }

    /** Skip TTS forward by one paragraph/segment (Volume Up handler). */
    fun skipForward() = readingController.skipForward()

    /** Skip TTS back by one paragraph/segment (Volume Down handler). */
    fun skipBack() = readingController.skipBack()

    // ──────────────────────────────────────────────────────────────────────────
    // Translation controls
    // ──────────────────────────────────────────────────────────────────────────

    /** Translate the segment currently being spoken by TTS. */
    fun translateCurrentSegment() {
        val text = readingController.currentSegmentText() ?: return
        val prefs = _translationPrefs.value
        if (prefs.targetLanguage.isBlank()) return

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

    // ──────────────────────────────────────────────────────────────────────────
    // Content analysis
    // ──────────────────────────────────────────────────────────────────────────

    private fun analyzeCurrentPage() {
        val state = _uiState.value as? ViewerUiState.Ready ?: return
        val uri   = documentUri ?: return
        val page  = state.currentPage

        viewModelScope.launch {
            val segments = analyzePage(
                uri        = uri,
                fromPage   = maxOf(0, page - 1),
                toPage     = minOf(state.totalPages - 1, page + 1),
                pageWidth  = pdfPageWidth,
                pageHeight = pdfPageHeight,
            )

            val readableWords = segments.sumOf { it.text.split(" ").size }

            val current = _uiState.value as? ViewerUiState.Ready ?: return@launch
            _uiState.value = current.copy(
                readableSegments  = segments,
                readableWordCount = readableWords,
                totalWordCount    = readableWords,
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PDF rendering
    // ──────────────────────────────────────────────────────────────────────────

    private fun renderPageRange(from: Int, to: Int): Map<Int, Bitmap> {
        val renderer = pdfRenderer ?: return emptyMap()
        val bitmaps  = mutableMapOf<Int, Bitmap>()
        // Use device screen width for crisp rendering on high-DPI displays
        val screenWidth = context.resources.displayMetrics.widthPixels
        val targetWidth = maxOf(screenWidth, 1440) // at least 1440 for readability
        for (i in from..to) {
            renderer.openPage(i).use { page ->
                val scale        = targetWidth.toFloat() / page.width
                val targetHeight = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps[i] = bitmap
            }
        }
        return bitmaps
    }

    override fun onCleared() {
        super.onCleared()
        // Stop TTS when the viewer is closed
        readingController.stop()
        ReadingForegroundService.stop(context)
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
    }
}
