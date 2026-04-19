package com.aurapdf.app.presentation.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurapdf.app.domain.model.PdfDocument
import com.aurapdf.app.domain.usecase.AddPdfDocumentUseCase
import com.aurapdf.app.domain.usecase.DeletePdfDocumentUseCase
import com.aurapdf.app.domain.usecase.GetPdfLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Library sort options shown in the top-bar dropdown. */
enum class SortOrder(val label: String) {
    BY_DATE_ADDED("Recently Added"),
    BY_NAME("Name (A–Z)"),
    BY_PROGRESS("Most Read"),
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Success(val documents: List<PdfDocument>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

/** One-shot UI events (e.g. snackbar messages). */
sealed interface HomeEvent {
    data class ShowSnackbar(val message: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getPdfLibrary: GetPdfLibraryUseCase,
    private val addDocument: AddPdfDocumentUseCase,
    private val deleteDocument: DeletePdfDocumentUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ── Sort order ───────────────────────────────────────────────────────────

    private val _sortOrder = MutableStateFlow(SortOrder.BY_DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }

    // ── Library state ────────────────────────────────────────────────────────

    val uiState: StateFlow<HomeUiState> = combine(
        getPdfLibrary(),
        _sortOrder,
    ) { docs, order ->
        val sorted = when (order) {
            SortOrder.BY_DATE_ADDED -> docs.sortedByDescending { it.dateAdded }
            SortOrder.BY_NAME       -> docs.sortedBy { it.name.lowercase() }
            SortOrder.BY_PROGRESS   -> docs.sortedByDescending { progressFraction(it) }
        }
        if (sorted.isEmpty()) HomeUiState.Empty
        else HomeUiState.Success(sorted)
    }
        .catch { e -> emit(HomeUiState.Error(e.message ?: "Unknown error")) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    // ── Thumbnails ───────────────────────────────────────────────────────────

    /**
     * In-memory cache of first-page thumbnails keyed by document ID.
     * Generated lazily on the IO dispatcher when new documents appear.
     * The ViewModel survives config changes, so this cache persists through
     * normal rotation.
     */
    private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()

    init {
        // Whenever the document list updates, generate thumbnails for any
        // documents that are not yet cached.
        viewModelScope.launch {
            uiState.collect { state ->
                if (state is HomeUiState.Success) {
                    val cached = _thumbnails.value.keys
                    state.documents
                        .filter { it.id !in cached }
                        .forEach { doc ->
                            launch(Dispatchers.IO) {
                                val bmp = generateThumbnail(doc)
                                if (bmp != null) {
                                    _thumbnails.update { it + (doc.id to bmp) }
                                }
                            }
                        }
                }
            }
        }
    }

    // ── Events ───────────────────────────────────────────────────────────────

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    // ── User actions ─────────────────────────────────────────────────────────

    /**
     * Called after the user picks a PDF via SAF.
     * [uri] is the content URI string; [displayName] is the file's display name.
     */
    fun onDocumentPicked(uri: String, displayName: String) {
        viewModelScope.launch {
            try {
                addDocument(uri, displayName)
                _events.emit(HomeEvent.ShowSnackbar("Added \"$displayName\""))
            } catch (e: Exception) {
                _events.emit(HomeEvent.ShowSnackbar("Failed to add document"))
            }
        }
    }

    fun onDeleteDocument(document: PdfDocument) {
        viewModelScope.launch {
            try {
                deleteDocument(document)
                // Evict thumbnail from cache to free memory
                _thumbnails.update { it - document.id }
                _events.emit(HomeEvent.ShowSnackbar("Removed \"${document.name}\""))
            } catch (e: Exception) {
                _events.emit(HomeEvent.ShowSnackbar("Failed to remove document"))
            }
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun progressFraction(doc: PdfDocument): Float =
        if (doc.totalPages > 0) (doc.lastPage + 1).toFloat() / doc.totalPages else 0f

    /**
     * Render the first page of a PDF at low resolution for the library card.
     * Must be called on a background thread (IO dispatcher).
     * Returns null if the file cannot be opened or rendered.
     */
    private fun generateThumbnail(document: PdfDocument): Bitmap? = runCatching {
        val uri = Uri.parse(document.uri)
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            // Manage PdfRenderer manually to avoid nested `.use` label ambiguity
            val renderer = PdfRenderer(pfd)
            try {
                if (renderer.pageCount == 0) return@use null
                renderer.openPage(0).use { page ->
                    val targetWidth  = 240
                    val scale        = targetWidth.toFloat() / page.width
                    val targetHeight = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } finally {
                renderer.close()
            }
        }
    }.getOrNull()
}
