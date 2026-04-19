package com.aurapdf.app.presentation.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurapdf.app.domain.tts.TtsState
import com.aurapdf.app.domain.tts.TtsWord
import com.aurapdf.app.presentation.theme.LocalThemeController
import com.aurapdf.app.presentation.viewer.settings.SmartReadingSettingsDialog
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel(),
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsState     by viewModel.ttsState.collectAsStateWithLifecycle()
    val ttsSpeed     by viewModel.ttsSpeed.collectAsStateWithLifecycle()
    val currentWord  by viewModel.currentTtsWord.collectAsStateWithLifecycle()
    val translationResult  by viewModel.translationResult.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") // Will be used for loading indicator in future polish
    val translationLoading by viewModel.translationLoading.collectAsStateWithLifecycle()
    val translationPrefs   by viewModel.translationPrefs.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    val readyState = uiState as? ViewerUiState.Ready

    Scaffold(
        topBar = {
            val theme = LocalThemeController.current
            TopAppBar(
                title = {
                    val title = readyState?.document?.name ?: "AuraPDF"
                    Text(
                        text     = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = theme.toggle) {
                        Icon(
                            imageVector        = if (theme.isDark) Icons.Default.LightMode
                                                 else Icons.Default.DarkMode,
                            contentDescription = if (theme.isDark) "Switch to light mode"
                                                 else "Switch to dark mode",
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector        = Icons.Default.Tune,
                            contentDescription = "Smart reading settings",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.surface,
                    titleContentColor          = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor     = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            if (readyState != null && readyState.readableSegments.isNotEmpty()) {
                TtsControlBar(
                    ttsState   = ttsState,
                    speed      = ttsSpeed,
                    wordCount  = readyState.readableWordCount,
                    onPlay     = viewModel::startReading,
                    onPause    = viewModel::pauseReading,
                    onResume   = viewModel::resumeReading,
                    onStop     = viewModel::stopReading,
                    onSetSpeed = viewModel::setTtsSpeed,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (val state = uiState) {
                ViewerUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = MaterialTheme.colorScheme.primary,
                    )
                }

                is ViewerUiState.Error -> {
                    ErrorState(
                        message  = state.message,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is ViewerUiState.Ready -> {
                    PdfPageList(
                        state         = state,
                        ttsState      = ttsState,
                        currentWord   = currentWord,
                        onPageVisible = viewModel::onPageVisible,
                    )
                }
            }
        }
    }

    if (showSettings) {
        SmartReadingSettingsDialog(
            prefs                     = readyState?.smartPrefs
                ?: com.aurapdf.app.domain.prefs.SmartReadingPrefs(),
            translationPrefs          = translationPrefs,
            availableLanguages        = viewModel.availableLanguages,
            onPrefsChanged            = viewModel::onSmartPrefsChanged,
            onTranslationPrefsChanged = viewModel::setTranslationPrefs,
            onDismiss                 = { showSettings = false },
        )
    }

    // Translation bottom sheet
    translationResult?.let { result ->
        TranslationBottomSheet(
            result    = result,
            onDismiss = viewModel::dismissTranslation,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// TTS control bar
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TtsControlBar(
    ttsState:   TtsState,
    speed:      Float,
    wordCount:  Int,
    onPlay:     () -> Unit,
    onPause:    () -> Unit,
    onResume:   () -> Unit,
    onStop:     () -> Unit,
    onSetSpeed: (Float) -> Unit,
) {
    val isActive = ttsState is TtsState.Speaking ||
                   ttsState is TtsState.Paused   ||
                   ttsState is TtsState.Loading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play / Pause / Resume
            when {
                ttsState is TtsState.Speaking || ttsState is TtsState.Loading -> {
                    IconButton(onClick = onPause) {
                        Icon(
                            imageVector        = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                ttsState is TtsState.Paused -> {
                    IconButton(onClick = onResume) {
                        Icon(
                            imageVector        = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                else -> {
                    IconButton(onClick = onPlay) {
                        Icon(
                            imageVector        = Icons.Default.PlayArrow,
                            contentDescription = "Start reading",
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Stop — only when active
            if (isActive) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector        = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text  = "${String.format("%.1f", speed)}×",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(4.dp))

            Slider(
                value         = speed,
                onValueChange = onSetSpeed,
                valueRange    = 0.5f..2.5f,
                steps         = 7,
                modifier      = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text  = "$wordCount words",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Loading indicator when TTS engine is initialising
        if (ttsState is TtsState.Loading) {
            LinearProgressRow()
        }
    }
}

@Composable
private fun LinearProgressRow() {
    androidx.compose.material3.LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
        color     = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Page list with auto-scroll + highlight + pinch-to-zoom
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun PdfPageList(
    state:         ViewerUiState.Ready,
    ttsState:      TtsState,
    currentWord:   TtsWord?,
    onPageVisible: (Int) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.currentPage,
    )

    // Zoom state — shared across all pages for unified zoom experience
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }

    // Report visible page changes to the ViewModel
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> onPageVisible(index) }
    }

    // Auto-scroll to the page of the word currently being spoken
    LaunchedEffect(currentWord?.pageIndex) {
        val targetPage = currentWord?.pageIndex ?: return@LaunchedEffect
        val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (targetPage !in visibleIndices) {
            listState.animateScrollToItem(targetPage)
        }
    }

    val isReading = ttsState is TtsState.Speaking || ttsState is TtsState.Paused

    // Color inversion matrix for dark-mode PDF rendering
    val isDarkMode = LocalThemeController.current.isDark
    val invertColorFilter = remember(isDarkMode) {
        if (isDarkMode) {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                         0f, -1f,  0f, 0f, 255f,
                         0f,  0f, -1f, 0f, 255f,
                         0f,  0f,  0f, 1f,   0f,
                    )
                )
            )
        } else null
    }

    LazyColumn(
        state               = listState,
        modifier            = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled   = zoomScale <= 1.01f,
    ) {
        itemsIndexed(
            items = List(state.totalPages) { it },
            key   = { _, page -> page },
        ) { _, page ->
            val bitmap = state.renderedPages[page]
            val isActivePage = isReading && currentWord?.pageIndex == page

            if (bitmap != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .then(
                            // Colored border on the page currently being read
                            if (isActivePage) Modifier.border(
                                BorderStroke(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                )
                            ) else Modifier
                        )
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (canceled) break

                                    val pressedCount = event.changes.count { it.pressed }

                                    if (pressedCount >= 2) {
                                        // Pinch-to-zoom + two-finger pan
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
                                        if (zoomScale > 1f) {
                                            zoomOffsetX += panChange.x
                                            zoomOffsetY += panChange.y
                                            val maxOX = size.width * (zoomScale - 1f) / 2f
                                            val maxOY = size.height * (zoomScale - 1f) / 2f
                                            zoomOffsetX = zoomOffsetX.coerceIn(-maxOX, maxOX)
                                            zoomOffsetY = zoomOffsetY.coerceIn(-maxOY, maxOY)
                                        } else {
                                            zoomOffsetX = 0f
                                            zoomOffsetY = 0f
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (pressedCount == 1 && zoomScale > 1.01f) {
                                        // Single-finger pan when zoomed in
                                        val panChange = event.calculatePan()
                                        zoomOffsetX += panChange.x
                                        zoomOffsetY += panChange.y
                                        val maxOX = size.width * (zoomScale - 1f) / 2f
                                        val maxOY = size.height * (zoomScale - 1f) / 2f
                                        zoomOffsetX = zoomOffsetX.coerceIn(-maxOX, maxOX)
                                        zoomOffsetY = zoomOffsetY.coerceIn(-maxOY, maxOY)
                                        event.changes.forEach { it.consume() }
                                    }
                                    // Single finger + not zoomed → events NOT consumed → LazyColumn scrolls
                                } while (event.changes.any { it.pressed })

                                // Snap back to 1x if barely zoomed
                                if (zoomScale < 1.05f) {
                                    zoomScale = 1f
                                    zoomOffsetX = 0f
                                    zoomOffsetY = 0f
                                }
                            }
                        },
                ) {
                    val imgHeightDp = maxWidth * (bitmap.height.toFloat() / bitmap.width)

                    // Zoomable container: scale + translate via graphics layer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(imgHeightDp)
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = zoomOffsetX
                                translationY = zoomOffsetY
                            },
                    ) {
                        Image(
                            bitmap             = bitmap.asImageBitmap(),
                            contentDescription = "Page ${page + 1}",
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.FillWidth,
                            colorFilter        = invertColorFilter,
                        )

                        // Animated highlight strip for the current TTS word
                        if (isActivePage && currentWord != null) {
                            val topNorm = (currentWord.bounds.top / state.pdfPageHeight)
                                .coerceIn(0f, 1f)
                            val bottomNorm = (currentWord.bounds.bottom / state.pdfPageHeight)
                                .coerceIn(topNorm, 1f)
                            val targetTop = imgHeightDp * topNorm
                            val targetHeight = imgHeightDp * (bottomNorm - topNorm)

                            val animatedTop by animateDpAsState(
                                targetValue   = targetTop,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label         = "highlightTop",
                            )
                            val animatedHeight by animateDpAsState(
                                targetValue   = targetHeight,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label         = "highlightHeight",
                            )

                            if (animatedHeight > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(animatedHeight)
                                        .offset(y = animatedTop)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        ),
                                ) {
                                    // Left accent bar for visual emphasis
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            )
                                            .align(Alignment.CenterStart),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Placeholder while the page renders
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Error state
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text  = "Cannot open PDF",
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
