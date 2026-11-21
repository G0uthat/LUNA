package com.example.ui.reader

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.HighlightNoteEntity
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.ExportFormatOption
import com.example.data.model.ParsedBlock
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.StandardPageFormat
import com.example.engine.DynamicIslandEventManager
import com.example.engine.MergedDocumentExporter
import com.example.engine.SocialRepository
import com.example.engine.SystemMediaController
import com.example.ui.components.AnnotationsAndBookmarksSheet
import com.example.ui.components.AppSettingsSheet
import com.example.ui.components.AppearanceSettingsSheet
import com.example.ui.components.BookmarkDockOverlay
import com.example.ui.components.ContextualDictionaryPopover
import com.example.ui.components.DrawingFloatingToolbar
import com.example.ui.components.InBookSearchSheet
import com.example.ui.components.LumaActionFeedbackOverlay
import com.example.ui.components.MergedExportDialog
import com.example.ui.components.NoteEditorDialog
import com.example.ui.components.ReaderBatteryOnlyIndicator
import com.example.ui.components.TactileButton
import com.example.ui.components.TocBottomSheet
import com.example.ui.theme.LocalFolioPalette
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBackToLibrary: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val view = LocalView.current

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val drawingsByLocation by viewModel.drawingsByLocation.collectAsStateWithLifecycle()
    val ocrByPage by viewModel.ocrByPage.collectAsStateWithLifecycle()
    val dictSources by viewModel.dictionarySources.collectAsStateWithLifecycle()
    val mediaInfo by SystemMediaController.mediaState.collectAsStateWithLifecycle()
    val activeIslandEvent by DynamicIslandEventManager.activeEvent.collectAsStateWithLifecycle()
    val queuedIslandCount by DynamicIslandEventManager.queuedCount.collectAsStateWithLifecycle()
    val topModalHostId by DynamicIslandEventManager.topModalHostId.collectAsStateWithLifecycle()
    val socialRepo = remember(context) { SocialRepository.getInstance(context) }

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showAnnotationsSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var editingHighlight by remember { mutableStateOf<HighlightNoteEntity?>(null) }
    var pendingExportOption by remember { mutableStateOf(ExportFormatOption.NATIVE_MERGED) }
    var showTempProgressOverlay by remember { mutableStateOf(false) }

    // Live absolute phone brightness synchronized 1:1 with Android's system brightness slider
    val activity = context as? Activity
    var liveBrightness by remember {
        mutableFloatStateOf(com.example.engine.SystemBrightnessController.readSystemBrightnessFraction(context))
    }
    LaunchedEffect(context) {
        com.example.engine.SystemBrightnessController.observeSystemBrightness(context).collect { systemFrac ->
            liveBrightness = systemFrac
        }
    }

    // Hide Android system status bar icons during reading (shows ONLY battery indicator + Dynamic Island alerts)
    DisposableEffect(view) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.statusBars())
            socialRepo.clearReadingPresence()
            com.example.engine.SystemBrightnessController.clearWindowOverride(activity)
        }
    }

    // SAF CreateDocument launcher for merged export (Zero storage permissions)
    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.exportMergedToUri(uri, pendingExportOption) {
                showExportDialog = false
            }
        }
    }

    // Hardware/gesture back handling
    BackHandler {
        when {
            state.selectedWord != null -> viewModel.dismissWordPopover()
            state.isDockExpanded -> viewModel.toggleDock(false)
            state.isDrawingMode -> viewModel.toggleDrawingMode(false)
            else -> onBackToLibrary()
        }
    }

    val handleTapCenter: () -> Unit = {
        if (prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR) {
            viewModel.toggleDock()
        } else {
            showTempProgressOverlay = !showTempProgressOverlay
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("reader_screen_root")
    ) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = palette.accent)
                    Text(
                        text = "Preparando documento…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted
                    )
                }
            }

            state.error != null || state.parsed == null || state.document == null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = state.error ?: "Error al cargar el documento",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink
                    )
                    TactileButton(onClick = onBackToLibrary) {
                        Text("Volver a Biblioteca")
                    }
                }
            }

            else -> {
                val doc = state.document!!
                val parsed = state.parsed!!
                val isEpub = parsed.format == DocumentFormat.EPUB
                val isPdf = parsed.format == DocumentFormat.PDF
                val isPptx = parsed.format == DocumentFormat.PPTX
                val showOriginalView = !isEpub && state.viewAsOriginalFormat
                val isShowingPdfNative = isPdf && showOriginalView
                val isShowingStandardSheet = !isPdf && showOriginalView

                val resolvedFormatLabel = if (prefs.standardPageFormat == StandardPageFormat.AUTO) {
                    parsed.detectedFormatLabel
                } else {
                    prefs.standardPageFormat.shortLabel
                }
                val totalUnits = (if (isShowingPdfNative) parsed.pdfPageCount else state.totalSheetCount).coerceAtLeast(1)
                val currentDisplayIndex = if (isShowingPdfNative) state.currentUnitIndex else state.currentSheetIndex
                val progress = ((currentDisplayIndex + 1).toFloat() / totalUnits.toFloat()).coerceIn(0f, 1f)
                val remainingWords = (parsed.totalWords * (1f - progress)).roundToInt().coerceAtLeast(0)
                val minsLeft = max(1, remainingWords / 210)
                val progressSummary = "${(progress * 100).roundToInt()}% · ~$minsLeft min restantes"
                val currentLocKey = viewModel.currentLocationKey()
                val isBookmarkedHere = bookmarks.any { it.locationKey == currentLocKey }

                // Enrich PDF adapted chapters with ML Kit OCR text on scanned/manhwa pages when available
                val effectiveChapters = remember(parsed, ocrByPage, isPdf, state.showTranslatedManhwaOverlay) {
                    if (!isPdf) {
                        parsed.chapters
                    } else {
                        parsed.chapters.map { ch ->
                            val nativeText = parsed.pdfPageTexts.getOrNull(ch.index).orEmpty()
                            val ocrPage = ocrByPage[ch.index]
                            if (nativeText.isBlank() && ocrPage != null && ocrPage.blocks.isNotEmpty()) {
                                val ocrBlocks = buildList {
                                    add(ParsedBlock(0, BlockType.HEADING_2, "Página ${ch.index + 1} (OCR)"))
                                    ocrPage.blocks.forEach { ob ->
                                        val txt = if (state.showTranslatedManhwaOverlay && !ob.translatedText.isNullOrBlank()) {
                                            ob.translatedText
                                        } else {
                                            ob.originalText
                                        }.trim()
                                        if (txt.isNotEmpty()) {
                                            add(ParsedBlock(size, BlockType.PARAGRAPH, txt))
                                        }
                                    }
                                }
                                ch.copy(blocks = ocrBlocks)
                            } else {
                                ch
                            }
                        }
                    }
                }

                // Share live reading status with connected P2P friends (respects privacy toggles)
                val progressPctInt = (progress * 100).roundToInt()
                LaunchedEffect(parsed.title, progressPctInt) {
                    socialRepo.updateReadingPresence(parsed.title, progressPctInt)
                }

                // Subtle 18% frosted blur on the reading surface while any floating dropdown menu is open
                val anyReaderMenuOpen = state.isDockExpanded || showAppearanceSheet || showTocSheet ||
                    showAnnotationsSheet || showSearchSheet || showSettingsSheet ||
                    showExportDialog || showNewNoteDialog || editingHighlight != null
                val readerBlurRadius by animateDpAsState(
                    targetValue = if (anyReaderMenuOpen) 6.dp else 0.dp,
                    animationSpec = tween(210),
                    label = "reader_menu_blur"
                )
                val readerSurfaceMod = if (readerBlurRadius > 0.dp) {
                    Modifier.fillMaxSize().blur(radius = readerBlurRadius)
                } else {
                    Modifier.fillMaxSize()
                }

                // 1. Distraction-Free Reading Surface (Native PDF, Standard Physical Sheet/PPTX, or Adapted EPUB Book)
                when {
                    isShowingPdfNative -> {
                        PdfManhwaReader(
                            filePath = doc.filePath,
                            pageCount = parsed.pdfPageCount,
                            currentPageIndex = state.currentUnitIndex,
                            prefs = prefs,
                            drawingsByLocation = drawingsByLocation,
                            ocrByPage = ocrByPage,
                            showTranslatedOverlay = state.showTranslatedManhwaOverlay,
                            isProcessingOcrOrTranslation = state.isProcessingOcrOrTranslation,
                            processingMessage = state.ocrTranslationStatus,
                            isDockExpanded = state.isDockExpanded,
                            isDrawingMode = state.isDrawingMode,
                            isPanZoomWhileDrawing = state.isPanZoomWhileDrawing,
                            detectedFormatLabel = parsed.detectedFormatLabel,
                            activeTool = state.activeTool,
                            activeColorArgb = state.activeColorArgb,
                            activeWidthNorm = state.activeWidthNorm,
                            onPageChanged = { idx -> viewModel.onUnitChanged(idx) },
                            onPageBitmapReady = viewModel::onPdfPageBitmapReady,
                            onWordSelected = viewModel::onWordSelected,
                            onStrokeCompleted = viewModel::addStroke,
                            onEraseStrokes = viewModel::eraseStrokes,
                            onToggleTranslateOverlay = viewModel::toggleOrRunManhwaPageTranslation,
                            onTapPageCenter = handleTapCenter,
                            modifier = readerSurfaceMod
                        )
                    }
                    isShowingStandardSheet -> {
                        StandardPageDocumentReader(
                            parsed = parsed,
                            currentUnitIndex = state.currentUnitIndex,
                            jumpToUnitTrigger = state.jumpToChapterTrigger,
                            prefs = prefs,
                            drawingsByLocation = drawingsByLocation,
                            isDrawingMode = state.isDrawingMode,
                            isPanZoomWhileDrawing = state.isPanZoomWhileDrawing,
                            activeTool = state.activeTool,
                            activeColorArgb = state.activeColorArgb,
                            activeWidthNorm = state.activeWidthNorm,
                            onActiveSheetChanged = viewModel::onFluidSheetChanged,
                            onSelectStandardFormat = { fmt ->
                                viewModel.updatePreferences { it.copy(standardPageFormat = fmt) }
                            },
                            onToggleStandardView = viewModel::toggleStandardPageViewForCurrentDoc,
                            onStrokeCompleted = viewModel::addStroke,
                            onEraseStrokes = viewModel::eraseStrokes,
                            onTapPageCenter = handleTapCenter,
                            modifier = readerSurfaceMod
                        )
                    }
                    else -> {
                        FluidBookReader(
                            chapters = effectiveChapters,
                            currentChapterIndex = state.currentUnitIndex,
                            jumpToChapterTrigger = state.jumpToChapterTrigger,
                            prefs = prefs,
                            highlights = highlights,
                            drawingsByLocation = drawingsByLocation,
                            isDrawingMode = state.isDrawingMode,
                            activeTool = state.activeTool,
                            activeColorArgb = state.activeColorArgb,
                            activeWidthNorm = state.activeWidthNorm,
                            activeSelection = state.selectedWord,
                            onActiveSheetChanged = viewModel::onFluidSheetChanged,
                            onWordSelected = viewModel::onWordSelected,
                            onStrokeCompleted = viewModel::addStroke,
                            onEraseStrokes = viewModel::eraseStrokes,
                            onTapPageCenter = handleTapCenter,
                            modifier = readerSurfaceMod
                        )
                    }
                }

                // 2. Ultra-minimalist discrete reading progress footer (only if enabled or tapped)
                AnimatedVisibility(
                    visible = (prefs.showMinimalFooter || showTempProgressOverlay) && !state.isDrawingMode &&
                        !(prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR && state.isDockExpanded),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isShowingPdfNative -> "Pág. ${currentDisplayIndex + 1} / $totalUnits · PDF"
                                isPptx && showOriginalView -> "Diapositiva ${currentDisplayIndex + 1} / $totalUnits"
                                isShowingStandardSheet -> "Hoja ${currentDisplayIndex + 1} / $totalUnits · $resolvedFormatLabel"
                                !isEpub -> "Hoja ${currentDisplayIndex + 1} / $totalUnits · Modo EPUB"
                                else -> "Hoja ${currentDisplayIndex + 1} / $totalUnits"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkMuted.copy(alpha = 0.72f)
                        )
                        Text(
                            text = progressSummary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkMuted.copy(alpha = 0.72f)
                        )
                    }
                }

                // 2.5 Distraction-free Battery-ONLY status indicator (system status bar & notification icons hidden)
                AnimatedVisibility(
                    visible = !state.isDrawingMode &&
                        !(prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR && state.isDockExpanded) &&
                        activeIslandEvent == null,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .displayCutoutPadding()
                        .padding(top = 2.dp)
                ) {
                    ReaderBatteryOnlyIndicator()
                }

                // 3. Configurable Reader Menu (Draggable Bookmark / Apple Dynamic Island / Classic Kindle Bar)
                BookmarkDockOverlay(
                    menuStyle = prefs.menuStyle,
                    side = prefs.dockSide,
                    dockYFraction = prefs.dockYFraction,
                    dockScale = prefs.dockScale,
                    brightnessValue = liveBrightness,
                    isExpanded = state.isDockExpanded,
                    isBookmarkedHere = isBookmarkedHere,
                    isDrawingMode = state.isDrawingMode,
                    isPdfDocument = isShowingPdfNative,
                    documentFormat = parsed.format,
                    showFormatModeToggle = !isEpub,
                    isOriginalFormatView = showOriginalView,
                    isPageTranslated = ocrByPage[state.currentUnitIndex]?.isTranslated == true && state.showTranslatedManhwaOverlay,
                    isProcessingStatus = state.isProcessingOcrOrTranslation,
                    processingStatusText = state.ocrTranslationStatus,
                    mediaInfo = mediaInfo,
                    activeIslandEvent = if (topModalHostId == null) activeIslandEvent else null,
                    queuedIslandCount = queuedIslandCount,
                    bookTitle = parsed.title,
                    progressLabel = progressSummary,
                    onToggleExpanded = { viewModel.toggleDock() },
                    onDismiss = { viewModel.toggleDock(false) },
                    onDockRepositioned = { newSide, newY ->
                        viewModel.updatePreferences { it.copy(dockSide = newSide, dockYFraction = newY) }
                    },
                    onBrightnessChanged = { b ->
                        val clamped = b.coerceIn(0.01f, 1f)
                        liveBrightness = clamped
                        com.example.engine.SystemBrightnessController.applyAbsoluteBrightness(context, activity, clamped)
                    },
                    onToggleBookmarkHere = viewModel::toggleBookmarkCurrentPage,
                    onOpenAppearance = { showAppearanceSheet = true },
                    onOpenToc = { showTocSheet = true },
                    onToggleDrawing = { viewModel.toggleDrawingMode() },
                    onSetOriginalFormatView = viewModel::setViewAsOriginalFormat,
                    onTriggerOcrTranslate = viewModel::toggleOrRunManhwaPageTranslation,
                    onOpenAnnotations = { showAnnotationsSheet = true },
                    onOpenSearch = { showSearchSheet = true },
                    onOpenSettings = {
                        settingsInitialTab = 0
                        showSettingsSheet = true
                    },
                    onOpenExport = { showExportDialog = true },
                    onBackToLibrary = onBackToLibrary,
                    onMediaPlayPause = { SystemMediaController.togglePlayPause(context) },
                    onMediaNext = { SystemMediaController.skipToNext(context) },
                    onMediaPrevious = { SystemMediaController.skipToPrevious(context) },
                    onMediaSeek = { posMs -> SystemMediaController.seekTo(posMs) },
                    onOpenNotificationAccess = { SystemMediaController.openNotificationAccessSettings(context) },
                    onTapIslandEvent = { event -> DynamicIslandEventManager.openActiveEvent(context, event) },
                    onDismissIslandEvent = { DynamicIslandEventManager.dismissActiveEvent() }
                )

                // 4. Floating Drawing & Signature Toolbar (only when drawing mode is active)
                if (state.isDrawingMode) {
                    DrawingFloatingToolbar(
                        activeTool = state.activeTool,
                        activeColorArgb = state.activeColorArgb,
                        activeWidthNorm = state.activeWidthNorm,
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        isStandardPageView = showOriginalView,
                        standardFormatLabel = if (isPdf) "PDF" else resolvedFormatLabel,
                        showStandardViewToggle = !isEpub,
                        canTogglePanZoom = showOriginalView,
                        isPanZoomMode = state.isPanZoomWhileDrawing,
                        onSelectTool = viewModel::setDrawingTool,
                        onSelectColor = viewModel::setDrawingColor,
                        onWidthChange = viewModel::setDrawingWidth,
                        onToggleStandardPageView = viewModel::toggleStandardPageViewForCurrentDoc,
                        onTogglePanZoomMode = viewModel::togglePanZoomWhileDrawing,
                        onApplySignaturePreset = viewModel::applySignaturePreset,
                        onUndo = viewModel::undoDrawing,
                        onRedo = viewModel::redoDrawing,
                        onClearPage = viewModel::clearCurrentPageDrawings,
                        onDone = { viewModel.toggleDrawingMode(false) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // 5. Dynamic Contextual Dictionary & EN->ES Translation Popover (kept mounted for silky enter/exit)
                ContextualDictionaryPopover(
                    selection = state.selectedWord,
                    sources = dictSources,
                    instantTranslation = state.instantTranslation,
                    isTranslatingWord = state.isTranslatingWord,
                    translationStatusText = state.wordTranslationStatus,
                    wikiSummary = state.wikiSummary,
                    wikiError = state.wikiError,
                    isWikiLoading = state.isWikiLoading,
                    onHighlight = { colorArgb -> viewModel.addHighlightFromSelection(colorArgb) },
                    onAddNote = { showNewNoteDialog = true },
                    onUpdateSelection = viewModel::updateSelection,
                    onOpenDictionarySettings = {
                        settingsInitialTab = 1
                        showSettingsSheet = true
                    },
                    onDismiss = viewModel::dismissWordPopover
                )

                // 6. Modal Bottom Sheets & Dialogs
                if (showAppearanceSheet) {
                    AppearanceSettingsSheet(
                        prefs = prefs,
                        documentFormat = parsed.format,
                        isOriginalFormatView = showOriginalView,
                        onSetOriginalFormatView = viewModel::setViewAsOriginalFormat,
                        onUpdatePrefs = viewModel::updatePreferences,
                        onDismiss = { showAppearanceSheet = false }
                    )
                }

                if (showTocSheet) {
                    TocBottomSheet(
                        toc = parsed.toc,
                        currentIndex = state.currentUnitIndex,
                        onSelectEntry = { idx -> viewModel.onUnitChanged(idx, isExplicitJump = true) },
                        onDismiss = { showTocSheet = false }
                    )
                }

                if (showSearchSheet) {
                    InBookSearchSheet(
                        query = state.searchQuery,
                        matches = state.searchMatches,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSelectMatch = { match -> viewModel.onUnitChanged(match.targetIndex, isExplicitJump = true) },
                        onDismiss = { showSearchSheet = false }
                    )
                }

                if (showAnnotationsSheet) {
                    AnnotationsAndBookmarksSheet(
                        highlights = highlights,
                        bookmarks = bookmarks,
                        onNavigateToUnit = { idx -> viewModel.onUnitChanged(idx, isExplicitJump = true) },
                        onDeleteHighlight = viewModel::deleteHighlight,
                        onEditHighlightNote = { hl -> editingHighlight = hl },
                        onDeleteBookmark = viewModel::deleteBookmark,
                        onDismiss = { showAnnotationsSheet = false }
                    )
                }

                if (showSettingsSheet) {
                    AppSettingsSheet(
                        prefs = prefs,
                        sources = dictSources,
                        initialTab = settingsInitialTab,
                        onUpdatePrefs = viewModel::updatePreferences,
                        onAddSource = viewModel::addDictionarySource,
                        onToggleSource = viewModel::toggleDictionarySource,
                        onDeleteSource = viewModel::deleteDictionarySource,
                        onDismiss = { showSettingsSheet = false }
                    )
                }

                if (showNewNoteDialog && state.selectedWord != null) {
                    NoteEditorDialog(
                        quotedText = state.selectedWord!!.word,
                        initialNote = "",
                        initialColorArgb = 0xFFF6C445L,
                        onSave = { noteText, colorArgb ->
                            viewModel.addHighlightFromSelection(colorArgb, noteText)
                            showNewNoteDialog = false
                        },
                        onDismiss = { showNewNoteDialog = false }
                    )
                }

                editingHighlight?.let { hl ->
                    NoteEditorDialog(
                        quotedText = hl.quotedText,
                        initialNote = hl.noteText,
                        initialColorArgb = hl.colorArgb,
                        onSave = { noteText, colorArgb ->
                            viewModel.updateHighlightNote(hl.id, noteText, colorArgb)
                            editingHighlight = null
                        },
                        onDismiss = { editingHighlight = null }
                    )
                }

                if (showExportDialog) {
                    MergedExportDialog(
                        document = doc,
                        drawingStrokeCount = drawingsByLocation.values.sumOf { it.size },
                        highlightCount = highlights.size,
                        ocrPageCount = ocrByPage.size,
                        isExporting = state.isExporting,
                        onExportSaveAs = { option ->
                            pendingExportOption = option
                            val ext = MergedDocumentExporter.resolveOutputExtension(doc, option)
                            val safe = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36)
                            exportSaveLauncher.launch("${safe}_anotado.$ext")
                        },
                        onExportShare = { option ->
                            viewModel.exportMergedForShare(option) { file, mime ->
                                showExportDialog = false
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "Compartir libro fusionado"))
                                }
                            }
                        },
                        onDismiss = { showExportDialog = false }
                    )
                }
            }
        }

        // Silk bookmark ribbon drop & tactile micro-icon animations (replaces Material Snackbars)
        LumaActionFeedbackOverlay(
            event = state.feedbackEvent,
            onConsumed = viewModel::clearFeedbackEvent
        )
    }
}
