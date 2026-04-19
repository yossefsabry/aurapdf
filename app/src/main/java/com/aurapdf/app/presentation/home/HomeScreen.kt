package com.aurapdf.app.presentation.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurapdf.app.domain.model.PdfDocument
import com.aurapdf.app.presentation.theme.LocalThemeController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDocument: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val thumbnails        by viewModel.thumbnails.collectAsStateWithLifecycle()
    val sortOrder         by viewModel.sortOrder.collectAsStateWithLifecycle()
    val context           = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu      by remember { mutableStateOf(false) }

    val scrollBehavior    = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Collect one-shot events (snackbar messages)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // SAF file picker launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = context.contentResolver
                .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)
                        )
                    } else null
                } ?: uri.lastPathSegment ?: "document.pdf"

            viewModel.onDocumentPicked(uri.toString(), displayName)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val theme = LocalThemeController.current
            LargeTopAppBar(
                title = {
                    Text(
                        text  = "Your Library",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    // Sort order selector
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort library",
                            )
                        }
                        DropdownMenu(
                            expanded         = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text        = { Text(order.label) },
                                    leadingIcon = if (sortOrder == order) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null,
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }

                    // Dark/light mode toggle
                    IconButton(onClick = theme.toggle) {
                        Icon(
                            imageVector        = if (theme.isDark) Icons.Default.LightMode
                                                 else Icons.Default.DarkMode,
                            contentDescription = if (theme.isDark) "Switch to light mode"
                                                 else "Switch to dark mode",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                icon    = { Icon(Icons.Default.Add, contentDescription = "Add PDF") },
                text    = { Text("Add PDF") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
        ) {
            when (val state = uiState) {
                HomeUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                HomeUiState.Empty -> {
                    EmptyLibraryPlaceholder(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is HomeUiState.Success -> {
                    PdfDocumentGrid(
                        documents  = state.documents,
                        thumbnails = thumbnails,
                        onOpen     = { doc -> onOpenDocument(doc.id) },
                        onDelete   = viewModel::onDeleteDocument,
                    )
                }

                is HomeUiState.Error -> {
                    ErrorPlaceholder(
                        message  = state.message,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Empty state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyLibraryPlaceholder(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Description,
                contentDescription = null,
                modifier           = Modifier.size(48.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text  = "Your library is empty",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = "Tap 'Add PDF' to start reading with near-human TTS",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Error state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorPlaceholder(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.error,
        )
        Text(
            text  = "Could not load library",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Document grid
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun PdfDocumentGrid(
    documents:  List<PdfDocument>,
    thumbnails: Map<Long, Bitmap>,
    onOpen:     (PdfDocument) -> Unit,
    onDelete:   (PdfDocument) -> Unit,
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 140.dp),
        contentPadding        = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 88.dp // Space for the FAB
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
    ) {
        items(documents, key = { it.id }) { document ->
            PdfDocumentCard(
                document  = document,
                thumbnail = thumbnails[document.id],
                onOpen    = { onOpen(document) },
                onDelete  = { onDelete(document) },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Document card
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfDocumentCard(
    document:  PdfDocument,
    thumbnail: Bitmap?,
    onOpen:    () -> Unit,
    onDelete:  () -> Unit,
) {
    var showMenu         by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val readingProgress = if (document.totalPages > 0)
        (document.lastPage + 1).toFloat() / document.totalPages
    else 0f

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = onOpen,
                onLongClick = { showMenu = true },
            ),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // First-page thumbnail (or icon placeholder while loading)
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f) // 3:4 aspect ratio for typical documents
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap             = thumbnail.asImageBitmap(),
                        contentDescription = "Preview of ${document.name}",
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector        = Icons.Default.Description,
                        contentDescription = null,
                        modifier           = Modifier.size(48.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Document name
            Text(
                text     = document.name,
                style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Reading progress bar
            if (document.totalPages > 0) {
                LinearProgressIndicator(
                    progress   = { readingProgress },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Progress text and date
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (document.totalPages > 0) {
                    Text(
                        text  = "p. ${document.lastPage + 1} / ${document.totalPages}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text  = SimpleDateFormat("MMM d", Locale.getDefault())
                        .format(Date(document.dateAdded)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Context menu on long press
        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text        = { Text("Remove from library") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick     = {
                    showMenu = false
                    showDeleteDialog = true
                },
            )
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text("Remove PDF?") },
            text             = { Text("\"${document.name}\" will be removed from your library.") },
            confirmButton    = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("Remove") }
            },
            dismissButton    = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}
