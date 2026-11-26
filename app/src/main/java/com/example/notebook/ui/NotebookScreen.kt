package com.example.notebook.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.cloud.CloudAccountManager
import com.example.core.cloud.CloudMyAccountDialog
import com.example.notebook.data.NotebookBackgroundType
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.engine.NotebookExportFormat
import com.example.notebook.engine.NotebookExportOptions
import com.example.notebook.engine.NotebookExporter
import com.example.notebook.model.ImageObject
import com.example.notebook.model.NotebookTool
import com.example.notebook.model.ShapeObject
import com.example.notebook.model.TableObject
import com.example.notebook.model.TextAlignMode
import com.example.notebook.model.TextObject
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

/**
 * Full Study Notebook Studio Screen (Samsung Notes / GoodNotes class).
 * - Adapts purely via Material 3 `NotebookWindowWidthClass` (Compact / Medium / Expanded).
 * - Preserves page index, zoom, pan, and active tool across screen rotations inside `NotebookViewModel`.
 * - Supports tablet hardware keyboard shortcuts (Ctrl+Z, Ctrl+Y, Ctrl+Shift+Z, Ctrl+C, Ctrl+V, Delete).
 * - All interactive touch targets >= 48 dp. ZERO `Modifier.blur()`.
 */
@Composable
fun NotebookScreen(
    viewModel: NotebookViewModel,
    onBackToLibrary: () -> Unit,
    onImportPdfToLibrary: suspend (File, String) -> Long? = { _, _ -> null },
    autoHideChromeEnabled: Boolean = true,
    autoHideLandscapeOnly: Boolean = true,
    autoHideDelaySec: Int = 6,
    autoHideFadeSpeedMs: Int = 220,
    onToggleAutoHideChrome: ((Boolean) -> Unit)? = null
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val cloudAccountManager = remember(context) { CloudAccountManager.getInstance(context) }
    var showCloudAccountDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showPropertiesPanel by rememberSaveable { mutableStateOf(false) }
    var isDockCollapsed by rememberSaveable { mutableStateOf(false) }
    var showPageStrip by rememberSaveable { mutableStateOf(false) }

    var showColorPicker by remember { mutableStateOf(false) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTitleEditDialog by remember { mutableStateOf(false) }
    var showBgAndSizeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showStarterTemplatesDialog by remember { mutableStateOf(false) }

    var editingTextBox by remember { mutableStateOf<TextObject?>(null) }
    var editingCallout by remember { mutableStateOf<ShapeObject?>(null) }
    var editingImage by remember { mutableStateOf<ImageObject?>(null) }
    var editingTable by remember { mutableStateOf<TableObject?>(null) }

    val keyboardFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { keyboardFocusRequester.requestFocus() }
    }

    val handleExit = remember(viewModel, onBackToLibrary) {
        {
            viewModel.flushPendingSaveAsync()
            onBackToLibrary()
        }
    }

    BackHandler { handleExit() }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushPendingSaveAsync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPendingSaveAsync()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(2400L)
            viewModel.clearStatusMessage()
        }
    }

    val pasteImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importImageObjectFromUri(uri)
    }

    val myStickerPngPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addMyStickerFromUri(uri)
    }

    val pageBgImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.setCurrentPageBackgroundImage(uri)
    }

    val addPdfPagesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.addPagesFromPdfUri(uri)
    }

    var pendingSafExportOptions by remember { mutableStateOf(NotebookExportOptions()) }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri, pendingSafExportOptions)
    }

    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri, pendingSafExportOptions)
    }

    val exportJpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri, pendingSafExportOptions)
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri, pendingSafExportOptions)
    }

    val launchSafForOptions: (NotebookExportOptions, String) -> Unit = { opts, suggestedName ->
        pendingSafExportOptions = opts
        when (opts.format) {
            NotebookExportFormat.PDF -> exportPdfLauncher.launch(suggestedName)
            NotebookExportFormat.PNG -> exportPngLauncher.launch(suggestedName)
            NotebookExportFormat.JPG -> exportJpgLauncher.launch(suggestedName)
            NotebookExportFormat.ZIP_PNG,
            NotebookExportFormat.ZIP_JPG -> exportZipLauncher.launch(suggestedName)
        }
    }

    val nb = uiState.notebook
    val page = uiState.currentPage
    val pageAspectRatio = remember(nb?.pageWidthPx, nb?.pageHeightPx) {
        if (nb != null && nb.pageHeightPx > 0) {
            (nb.pageWidthPx.toFloat() / nb.pageHeightPx.toFloat()).coerceIn(0.45f, 2.2f)
        } else 0.707f
    }
    val activeTemplate = remember(nb?.pageTemplate) { NotebookPageTemplate.fromName(nb?.pageTemplate) }
    val bgType = remember(page?.backgroundType) { NotebookBackgroundType.fromName(page?.backgroundType) }
    val bgBitmap = page?.backgroundAssetPath?.takeIf { it.isNotBlank() }?.let { uiState.loadedBitmaps[it] }
    val zoomScale = uiState.zoomScale
    val panOffset = Offset(uiState.panOffsetX, uiState.panOffsetY)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (palette.isDark) Color(0xFF10131A) else Color(0xFFEAECEF))
            .focusRequester(keyboardFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    keyEvent.isCtrlPressed && keyEvent.isShiftPressed && keyEvent.key == Key.Z -> {
                        viewModel.redo(); true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Z -> {
                        viewModel.undo(); true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Y -> {
                        viewModel.redo(); true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.C -> {
                        viewModel.copySelectedObjects(); true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.V -> {
                        viewModel.pasteCopiedObjects(); true
                    }
                    (keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace) && uiState.selectedObjectIds.isNotEmpty() -> {
                        viewModel.deleteSelectedObject(); true
                    }
                    else -> false
                }
            }
            .testTag("notebook_screen_root")
    ) {
        val widthClass = NotebookWindowWidthClass.fromWidth(maxWidth)
        val isWide = widthClass.isWide
        val isCompactLandscape = maxHeight < 520.dp && maxWidth > maxHeight
        val effectiveAutoHide = autoHideChromeEnabled && (!autoHideLandscapeOnly || isCompactLandscape)
        val fadeMs = autoHideFadeSpeedMs.coerceIn(80, 600)

        var isChromeVisible by rememberSaveable { mutableStateOf(true) }
        var chromeInteractionNonce by remember { mutableIntStateOf(0) }
        val bumpChromeTimer: () -> Unit = {
            isChromeVisible = true
            chromeInteractionNonce++
        }

        val hasBlockingOverlay = showPropertiesPanel ||
            showPageStrip ||
            showColorPicker ||
            showLayersDialog ||
            showOverflowMenu ||
            showTitleEditDialog ||
            showBgAndSizeDialog ||
            showExportDialog ||
            showStarterTemplatesDialog ||
            editingTextBox != null ||
            editingCallout != null ||
            editingImage != null ||
            editingTable != null

        LaunchedEffect(effectiveAutoHide) {
            if (!effectiveAutoHide) {
                isChromeVisible = true
            }
        }

        LaunchedEffect(
            effectiveAutoHide,
            isChromeVisible,
            chromeInteractionNonce,
            hasBlockingOverlay,
            autoHideDelaySec
        ) {
            if (effectiveAutoHide && isChromeVisible && !hasBlockingOverlay) {
                delay(autoHideDelaySec.coerceIn(3, 30) * 1000L)
                isChromeVisible = false
            }
        }

        val canvasTopPad by animateDpAsState(
            targetValue = if (isChromeVisible) {
                if (isCompactLandscape) 50.dp else 66.dp
            } else {
                4.dp
            },
            animationSpec = tween(durationMillis = fadeMs),
            label = "nb_canvas_top_pad"
        )
        val canvasBottomPad by animateDpAsState(
            targetValue = if (isChromeVisible) {
                if (isWide) {
                    if (isCompactLandscape) 6.dp else 12.dp
                } else {
                    if (isCompactLandscape) 56.dp else 78.dp
                }
            } else {
                4.dp
            },
            animationSpec = tween(durationMillis = fadeMs),
            label = "nb_canvas_bottom_pad"
        )
        val canvasStartPad by animateDpAsState(
            targetValue = if (isChromeVisible && isWide && !isDockCollapsed) {
                if (isCompactLandscape) 58.dp else 76.dp
            } else {
                4.dp
            },
            animationSpec = tween(durationMillis = fadeMs),
            label = "nb_canvas_start_pad"
        )

        NotebookCanvasView(
            pageAspectRatio = pageAspectRatio,
            template = activeTemplate,
            backgroundType = bgType,
            backgroundColorArgb = page?.backgroundColor ?: 0xFFFFFFFFL,
            backgroundBitmap = bgBitmap,
            content = uiState.currentContent,
            loadedBitmaps = uiState.loadedBitmaps,
            activeTool = uiState.activeTool,
            activeLayerId = uiState.activeLayerId,
            activeShapeKind = uiState.activeShapeKind,
            activeStickerKind = uiState.activeStickerKind,
            activeColorArgb = uiState.activeColorArgb,
            activeHighlighterColorArgb = uiState.activeHighlighterColorArgb,
            activeStrokeWidthNorm = uiState.activeStrokeWidthNorm,
            activeAlpha = uiState.activeAlpha,
            stabilizerLevel = uiState.activeStabilizerLevel,
            eraserMode = uiState.eraserMode,
            stylusOnlyMode = uiState.stylusOnlyMode,
            lockAspectRatio = uiState.lockAspectRatio,
            isEyedropperActive = uiState.isEyedropperActive,
            activeShapeFillEnabled = uiState.activeShapeFillEnabled,
            activeShapeDashed = uiState.activeShapeDashed,
            activeShapeShadow = uiState.activeShapeShadow,
            selectedObjectIds = uiState.selectedObjectIds,
            pendingConnectorFromId = uiState.pendingConnectorFromId,
            zoomScale = zoomScale,
            panOffset = panOffset,
            onZoomPanChange = { z, p -> viewModel.setZoomPan(z, p.x, p.y) },
            onResetZoomPan = viewModel::resetZoomPan,
            onEyedropperColorPicked = viewModel::applyEyedroppedColor,
            onAddStroke = viewModel::addStroke,
            onEraseStrokes = viewModel::eraseStrokesByIds,
            onErasePixelsAt = viewModel::erasePixelsAt,
            onAddShape = { shape -> viewModel.addObject(shape, selectAfter = false) },
            onAddSticker = { sticker -> viewModel.addObject(sticker, selectAfter = true) },
            onConnectorObjectTap = viewModel::handleConnectorObjectTap,
            onRequestCreateTextAt = { nx, ny, isBanner ->
                val defaultW = if (isBanner) 0.78f else 0.48f
                val defaultH = if (isBanner) 0.10f else 0.16f
                editingTextBox = TextObject(
                    x = (nx - defaultW * 0.5f).coerceIn(0.04f, 1f - defaultW - 0.04f),
                    y = (ny - defaultH * 0.5f).coerceIn(0.04f, 1f - defaultH - 0.04f),
                    width = defaultW,
                    height = defaultH,
                    spans = emptyList(),
                    fontId = if (isBanner) NotebookFonts.DEFAULT_BANNER_ID else NotebookFonts.DEFAULT_BODY_ID,
                    fontSizeNorm = if (isBanner) 0.036f else 0.022f,
                    alignment = if (isBanner) TextAlignMode.CENTER else TextAlignMode.START,
                    isBanner = isBanner,
                    hasShadow = isBanner,
                    fillColorArgb = 0xFFFFFFFFL,
                    borderColorArgb = if (isBanner) 0xFF1D5BFFL else 0xFF151C2BL,
                    cornerRadiusDp = if (isBanner) 18f else 12f
                )
            },
            onRequestCreateTableAt = { nx, ny ->
                val defaultW = 0.72f
                val defaultH = 0.24f
                editingTable = TableObject(
                    x = (nx - defaultW * 0.5f).coerceIn(0.04f, 1f - defaultW - 0.04f),
                    y = (ny - defaultH * 0.5f).coerceIn(0.04f, 1f - defaultH - 0.04f),
                    width = defaultW,
                    height = defaultH,
                    rows = 3,
                    cols = 3,
                    cells = listOf("Concepto", "Detalle", "Nota", "", "", "", "", "", "")
                )
            },
            onRequestEditText = { editingTextBox = it },
            onRequestEditCallout = { editingCallout = it },
            onRequestEditImage = { editingImage = it },
            onRequestEditTable = { editingTable = it },
            onSelectObject = viewModel::selectObject,
            onSelectObjects = viewModel::selectObjects,
            onBeginObjectsTransform = viewModel::beginContinuousTransform,
            onUpdateObjectsLive = viewModel::updateObjectsLiveNoUndo,
            onCommitObjectsTransform = viewModel::commitContinuousTransform,
            onCopySelected = viewModel::copySelectedObjects,
            onFlipSelectedH = viewModel::flipSelectedHorizontal,
            onFlipSelectedV = viewModel::flipSelectedVertical,
            onGroupSelected = viewModel::groupSelected,
            onUngroupSelected = viewModel::ungroupSelected,
            onAlignSelected = viewModel::alignSelected,
            onSetSelectedOpacity = viewModel::setSelectedOpacity,
            onToggleLockSelected = viewModel::toggleLockSelected,
            onDuplicateSelected = viewModel::duplicateSelectedObject,
            onBringToFront = viewModel::bringSelectedToFront,
            onSendToBack = viewModel::sendSelectedToBack,
            onDeleteSelected = viewModel::deleteSelectedObject,
            onConvertStrokesToText = viewModel::convertSelectedStrokesToText,
            onRemoveImageBackground = viewModel::removeBackgroundFromImage,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    top = canvasTopPad,
                    bottom = canvasBottomPad,
                    start = canvasStartPad,
                    end = 4.dp
                )
        )

        // Top Studio Bar: sleeker on compact landscape phones, auto-hides smoothly in immersive mode
        AnimatedVisibility(
            visible = isChromeVisible,
            enter = fadeIn(tween(fadeMs)) + slideInVertically(tween(fadeMs)) { -it / 2 },
            exit = fadeOut(tween(fadeMs)) + slideOutVertically(tween(fadeMs)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            val topBarCorner = if (isCompactLandscape) 16.dp else 20.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(
                        horizontal = if (isCompactLandscape) 8.dp else 10.dp,
                        vertical = if (isCompactLandscape) 3.dp else 6.dp
                    )
                    .clip(RoundedCornerShape(topBarCorner))
                    .background(palette.surface.copy(alpha = 0.92f))
                    .border(1.dp, palette.border, RoundedCornerShape(topBarCorner))
                    .padding(
                        horizontal = 4.dp,
                        vertical = if (isCompactLandscape) 1.dp else 2.dp
                    )
                    .testTag("notebook_top_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 1.dp else 2.dp)
            ) {
                TopBarIcon(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    desc = "Volver a biblioteca",
                    onClick = handleExit,
                    compact = isCompactLandscape,
                    testTag = "nb_back_button"
                )

                // Editable Notebook Title + Page Indicator pill
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (isCompactLandscape) 38.dp else 48.dp)
                        .clip(RoundedCornerShape(if (isCompactLandscape) 10.dp else 12.dp))
                        .clickable {
                            bumpChromeTimer()
                            showTitleEditDialog = true
                        }
                        .padding(horizontal = if (isCompactLandscape) 4.dp else 6.dp)
                        .testTag("nb_editable_title_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = nb?.title ?: "Cuaderno",
                        style = if (isCompactLandscape) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Editar título",
                        tint = palette.inkMuted,
                        modifier = Modifier.size(if (isCompactLandscape) 12.dp else 14.dp)
                    )
                }

                // Quick Page Strip toggle pill (e.g. "1/3")
                Box(
                    modifier = Modifier
                        .heightIn(min = if (isCompactLandscape) 32.dp else 44.dp)
                        .clip(RoundedCornerShape(if (isCompactLandscape) 10.dp else 12.dp))
                        .background(if (showPageStrip) palette.accent else palette.surfaceAlt)
                        .border(
                            1.dp,
                            if (showPageStrip) palette.accent else palette.border,
                            RoundedCornerShape(if (isCompactLandscape) 10.dp else 12.dp)
                        )
                        .clickable {
                            bumpChromeTimer()
                            showPageStrip = !showPageStrip
                        }
                        .padding(
                            horizontal = if (isCompactLandscape) 8.dp else 10.dp,
                            vertical = if (isCompactLandscape) 4.dp else 6.dp
                        )
                        .testTag("nb_page_strip_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${uiState.currentPageIndex + 1}/${uiState.pages.size.coerceAtLeast(1)}",
                        fontSize = if (isCompactLandscape) 10.5.sp else 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (showPageStrip) palette.onAccent else palette.ink
                    )
                }

                TopBarIcon(
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    desc = "Deshacer",
                    enabled = uiState.canUndo,
                    onClick = {
                        bumpChromeTimer()
                        viewModel.undo()
                    },
                    compact = isCompactLandscape,
                    testTag = "nb_undo_button"
                )
                TopBarIcon(
                    icon = Icons.AutoMirrored.Rounded.Redo,
                    desc = "Rehacer",
                    enabled = uiState.canRedo,
                    onClick = {
                        bumpChromeTimer()
                        viewModel.redo()
                    },
                    compact = isCompactLandscape,
                    testTag = "nb_redo_button"
                )
                TopBarIcon(
                    icon = Icons.Rounded.Layers,
                    desc = "Capas",
                    onClick = {
                        bumpChromeTimer()
                        showLayersDialog = true
                    },
                    compact = isCompactLandscape,
                    testTag = "nb_layers_button"
                )
                TopBarIcon(
                    icon = Icons.Rounded.IosShare,
                    desc = "Exportar PDF o imagen",
                    tint = palette.accent,
                    onClick = {
                        bumpChromeTimer()
                        showExportDialog = true
                    },
                    compact = isCompactLandscape,
                    testTag = "nb_export_button"
                )
                TopBarIcon(
                    icon = Icons.Rounded.MoreHoriz,
                    desc = "Más opciones",
                    onClick = {
                        bumpChromeTimer()
                        showOverflowMenu = true
                    },
                    compact = isCompactLandscape,
                    testTag = "nb_overflow_menu_button"
                )
            }
        }

        // Subtle floating pill to restore hidden menus in 1 tap during immersive notebook mode
        AnimatedVisibility(
            visible = !isChromeVisible,
            enter = fadeIn(tween(fadeMs)),
            exit = fadeOut(tween(fadeMs)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 4.dp, end = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = bumpChromeTimer)
                    .testTag("nb_restore_chrome_pill"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.surface.copy(alpha = 0.88f))
                        .border(1.dp, palette.border, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Mostrar menús del cuaderno",
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Menú",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                }
            }
        }

        // Collapsible Page Thumbnail Strip (with Add, Duplicate, Reorder, Delete & Cross-page Copy/Paste)
        AnimatedVisibility(
            visible = showPageStrip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(
                    top = if (isCompactLandscape) 52.dp else 66.dp,
                    start = if (isWide) (if (isCompactLandscape) 62.dp else 82.dp) else 10.dp,
                    end = 10.dp
                )
        ) {
            NotebookPageStrip(
                pages = uiState.pages,
                currentPageIndex = uiState.currentPageIndex,
                pageAspectRatio = pageAspectRatio,
                hasSelectedObjects = uiState.selectedObjectIds.isNotEmpty(),
                copiedObjectCount = uiState.copiedObjects.size,
                onSelectPage = { idx ->
                    bumpChromeTimer()
                    viewModel.selectPage(idx, resetZoom = true)
                },
                onAddPage = {
                    bumpChromeTimer()
                    viewModel.addBlankPage()
                },
                onDuplicatePage = {
                    bumpChromeTimer()
                    viewModel.duplicateCurrentPage()
                },
                onMovePage = { from, to ->
                    bumpChromeTimer()
                    viewModel.movePage(from, to)
                },
                onDeletePage = { idx ->
                    bumpChromeTimer()
                    viewModel.deletePage(idx)
                },
                onCopySelected = {
                    bumpChromeTimer()
                    viewModel.copySelectedObjects()
                },
                onPasteCopied = {
                    bumpChromeTimer()
                    viewModel.pasteCopiedObjects()
                },
                onCloseStrip = { showPageStrip = false }
            )
        }

        // Adaptive Studio Dock (strictly below top bar with statusBarsPadding so menus never overlap in landscape)
        AnimatedVisibility(
            visible = isChromeVisible,
            enter = fadeIn(tween(fadeMs)) + if (isWide) {
                slideInHorizontally(tween(fadeMs)) { -it / 2 }
            } else {
                slideInVertically(tween(fadeMs)) { it / 2 }
            },
            exit = fadeOut(tween(fadeMs)) + if (isWide) {
                slideOutHorizontally(tween(fadeMs)) { -it / 2 }
            } else {
                slideOutVertically(tween(fadeMs)) { it / 2 }
            },
            modifier = if (isWide) {
                Modifier
                    .align(Alignment.CenterStart)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(
                        start = if (isCompactLandscape) 8.dp else 10.dp,
                        top = if (isCompactLandscape) 52.dp else 70.dp,
                        bottom = if (isCompactLandscape) 8.dp else 14.dp
                    )
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            }
        ) {
            NotebookStudioToolbar(
                widthClass = widthClass,
                isCompactLandscape = isCompactLandscape,
                isDockCollapsed = isDockCollapsed,
                onToggleDockCollapsed = {
                    bumpChromeTimer()
                    isDockCollapsed = !isDockCollapsed
                },
                activeTool = uiState.activeTool,
                activeShapeKind = uiState.activeShapeKind,
                activeStickerKind = uiState.activeStickerKind,
                activeColorArgb = uiState.activeColorArgb,
                activeHighlighterColorArgb = uiState.activeHighlighterColorArgb,
                recentColors = uiState.recentColors,
                savedColors = uiState.savedColors,
                strokeWidthNorm = uiState.activeStrokeWidthNorm,
                alpha = uiState.activeAlpha,
                stabilizerLevel = uiState.activeStabilizerLevel,
                eraserMode = uiState.eraserMode,
                isEyedropperActive = uiState.isEyedropperActive,
                shapeFillEnabled = uiState.activeShapeFillEnabled,
                shapeDashedEnabled = uiState.activeShapeDashed,
                shapeShadowEnabled = uiState.activeShapeShadow,
                showPropertiesPanel = showPropertiesPanel,
                myStickerPaths = uiState.myStickerPaths,
                onSelectTool = { tool ->
                    bumpChromeTimer()
                    viewModel.selectTool(tool)
                    if (tool == NotebookTool.TEXT_BOX || tool == NotebookTool.TITLE_BANNER || tool == NotebookTool.TABLE) {
                        showPropertiesPanel = false
                    }
                },
                onSelectShapeKind = {
                    bumpChromeTimer()
                    viewModel.selectShapeKind(it)
                },
                onSelectStickerKind = {
                    bumpChromeTimer()
                    viewModel.selectStickerKind(it)
                },
                onSelectColor = {
                    bumpChromeTimer()
                    viewModel.selectColor(it)
                },
                onOpenColorPicker = {
                    bumpChromeTimer()
                    showColorPicker = true
                },
                onToggleEyedropper = {
                    bumpChromeTimer()
                    viewModel.toggleEyedropper()
                },
                onStrokeWidthChange = {
                    bumpChromeTimer()
                    viewModel.setStrokeWidthNorm(it)
                },
                onAlphaChange = {
                    bumpChromeTimer()
                    viewModel.setAlpha(it)
                },
                onStabilizerChange = {
                    bumpChromeTimer()
                    viewModel.setStabilizerLevel(it)
                },
                onSelectEraserMode = {
                    bumpChromeTimer()
                    viewModel.setEraserMode(it)
                },
                onToggleShapeFill = {
                    bumpChromeTimer()
                    viewModel.toggleShapeFill()
                },
                onToggleShapeDashed = {
                    bumpChromeTimer()
                    viewModel.toggleShapeDashed()
                },
                onToggleShapeShadow = {
                    bumpChromeTimer()
                    viewModel.toggleShapeShadow()
                },
                onTogglePropertiesPanel = {
                    bumpChromeTimer()
                    showPropertiesPanel = !showPropertiesPanel
                },
                onPickImage = {
                    bumpChromeTimer()
                    pasteImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onAddQuickTitleBanner = {
                    bumpChromeTimer()
                    showPropertiesPanel = false
                    editingTextBox = TextObject(
                        x = 0.08f,
                        y = 0.05f,
                        width = 0.84f,
                        height = 0.10f,
                        spans = emptyList(),
                        fontId = NotebookFonts.DEFAULT_BANNER_ID,
                        fontSizeNorm = 0.036f,
                        alignment = TextAlignMode.CENTER,
                        isBanner = true,
                        hasShadow = true,
                        fillColorArgb = 0xFFEFF6FFL,
                        borderColorArgb = 0xFF1D5BFFL,
                        cornerRadiusDp = 18f
                    )
                },
                onPasteClipboardImage = {
                    bumpChromeTimer()
                    showPropertiesPanel = false
                    viewModel.pasteImageFromClipboard()
                },
                onPickMyStickerPng = {
                    bumpChromeTimer()
                    myStickerPngPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPlaceMySticker = { absPath ->
                    bumpChromeTimer()
                    showPropertiesPanel = false
                    viewModel.placeCustomSticker(absPath)
                },
                onOpenStarterTemplates = {
                    bumpChromeTimer()
                    showPropertiesPanel = false
                    showStarterTemplatesDialog = true
                }
            )
        }

        // Floating Zoom Pill (− / 100% / +) — bottom-right, auto-hides with chrome
        AnimatedVisibility(
            visible = isChromeVisible,
            enter = fadeIn(tween(fadeMs)),
            exit = fadeOut(tween(fadeMs)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(
                    end = if (isCompactLandscape) 8.dp else 12.dp,
                    bottom = if (isWide) (if (isCompactLandscape) 8.dp else 14.dp) else 78.dp
                )
        ) {
            val zoomBtnMin = if (isCompactLandscape) 36.dp else 44.dp
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.surface.copy(alpha = 0.90f))
                    .border(1.dp, palette.border, RoundedCornerShape(50))
                    .padding(horizontal = if (isCompactLandscape) 3.dp else 4.dp, vertical = if (isCompactLandscape) 1.dp else 2.dp)
                    .testTag("nb_zoom_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = zoomBtnMin, minHeight = zoomBtnMin)
                        .clip(CircleShape)
                        .clickable {
                            bumpChromeTimer()
                            val next = (zoomScale - 0.25f).coerceIn(0.5f, 5.0f)
                            if (next <= 1f) viewModel.setZoomPan(next, 0f, 0f)
                            else viewModel.setZoomPan(next, panOffset.x, panOffset.y)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Remove,
                        contentDescription = "Alejar",
                        tint = palette.ink,
                        modifier = Modifier.size(if (isCompactLandscape) 16.dp else 18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = zoomBtnMin)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            bumpChromeTimer()
                            viewModel.resetZoomPan()
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(zoomScale * 100f).roundToInt()}%",
                        fontSize = if (isCompactLandscape) 10.5.sp else 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                }
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = zoomBtnMin, minHeight = zoomBtnMin)
                        .clip(CircleShape)
                        .clickable {
                            bumpChromeTimer()
                            val next = (zoomScale + 0.25f).coerceIn(0.5f, 5.0f)
                            viewModel.setZoomPan(next, panOffset.x, panOffset.y)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Acercar",
                        tint = palette.ink,
                        modifier = Modifier.size(if (isCompactLandscape) 16.dp else 18.dp)
                    )
                }
            }
        }

        // Transient status / cancellable background export progress pill
        val exportProg = uiState.exportProgress
        AnimatedVisibility(
            visible = uiState.statusMessage != null || uiState.isBusy || exportProg != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = if (isCompactLandscape) 52.dp else 68.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.ink.copy(alpha = 0.92f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isBusy || exportProg != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = palette.background
                    )
                }
                Text(
                    text = if (exportProg != null) {
                        "${exportProg.message} (${(exportProg.fraction * 100f).roundToInt().coerceIn(0, 100)}%)"
                    } else {
                        uiState.statusMessage ?: "Procesando…"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.background,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (exportProg != null) {
                    Text(
                        text = "Cancelar",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.cancelExport() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    // ---- Modal Studio Panels & Editors ----

    if (showCloudAccountDialog) {
        CloudMyAccountDialog(
            accountManager = cloudAccountManager,
            onDismiss = { showCloudAccountDialog = false }
        )
    }

    if (showTitleEditDialog) {
        NotebookTitleEditDialog(
            initialTitle = nb?.title ?: "Cuaderno",
            onConfirm = viewModel::renameNotebook,
            onDismiss = { showTitleEditDialog = false }
        )
    }

    if (showColorPicker) {
        NotebookColorPickerDialog(
            initialColorArgb = if (uiState.activeTool == NotebookTool.HIGHLIGHTER) {
                uiState.activeHighlighterColorArgb
            } else uiState.activeColorArgb,
            recentColors = uiState.recentColors,
            savedColors = uiState.savedColors,
            onSelectColor = viewModel::selectColor,
            onSaveColor = viewModel::saveColorFavorite,
            onRemoveSavedColor = viewModel::removeSavedColor,
            onStartEyedropper = { viewModel.toggleEyedropper(true) },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showLayersDialog) {
        val resolvedLayers = uiState.currentContent.resolvedLayers()
        val counts = remember(uiState.currentContent.objects) {
            uiState.currentContent.objects.groupingBy { it.layerId }.eachCount()
        }
        NotebookLayersDialog(
            layers = resolvedLayers,
            activeLayerId = uiState.activeLayerId,
            objectCountsByLayer = counts,
            onSelectActiveLayer = viewModel::selectActiveLayer,
            onAddLayer = viewModel::addLayer,
            onToggleVisible = viewModel::toggleLayerVisible,
            onToggleLocked = viewModel::toggleLayerLocked,
            onOpacityChange = viewModel::setLayerOpacity,
            onMoveLayer = viewModel::moveLayer,
            onDeleteLayer = viewModel::deleteLayer,
            onDismiss = { showLayersDialog = false }
        )
    }

    if (showOverflowMenu) {
        NotebookStudioOverflowMenuDialog(
            stylusOnlyMode = uiState.stylusOnlyMode,
            lockAspectRatio = uiState.lockAspectRatio,
            showPageStrip = showPageStrip,
            copiedObjectCount = uiState.copiedObjects.size,
            autoHideChromeEnabled = autoHideChromeEnabled,
            onToggleAutoHideChrome = onToggleAutoHideChrome,
            onToggleStylusOnly = viewModel::setStylusOnlyMode,
            onToggleLockAspect = viewModel::setLockAspectRatio,
            onTogglePageStrip = { showPageStrip = it },
            onOpenBackgroundAndSize = { showBgAndSizeDialog = true },
            onOpenStarterTemplates = { showStarterTemplatesDialog = true },
            onOpenCloudAccount = { showCloudAccountDialog = true },
            onPasteCopied = viewModel::pasteCopiedObjects,
            onClearCurrentPage = viewModel::clearCurrentPage,
            onDismiss = { showOverflowMenu = false }
        )
    }

    if (showStarterTemplatesDialog) {
        NotebookStarterTemplatesDialog(
            onApplyTemplate = viewModel::applyStarterTemplate,
            onDismiss = { showStarterTemplatesDialog = false }
        )
    }

    if (showBgAndSizeDialog && nb != null) {
        NotebookBackgroundAndSizeDialog(
            activeTemplate = activeTemplate,
            currentPaperColorArgb = page?.backgroundColor ?: 0xFFFFFFFFL,
            currentWidthPx = nb.pageWidthPx,
            currentHeightPx = nb.pageHeightPx,
            onChangeTemplate = viewModel::updateTemplate,
            onChangePaperColor = viewModel::updateCurrentPageBackgroundColor,
            onSetBackgroundImage = {
                pageBgImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onAddPdfPages = { addPdfPagesPicker.launch(arrayOf("application/pdf")) },
            onChangePageSize = viewModel::updateNotebookPageSize,
            onDismiss = { showBgAndSizeDialog = false }
        )
    }

    editingTextBox?.let { draft ->
        RichTextBoxEditorDialog(
            initialItem = draft,
            favoriteFontIds = uiState.favoriteFontIds,
            recentFontIds = uiState.recentFontIds,
            customQuickStyles = uiState.customQuickStyles,
            onToggleFavoriteFont = viewModel::toggleFavoriteFont,
            onFontSelected = viewModel::recordRecentFont,
            onSaveQuickStyle = viewModel::saveQuickStyle,
            onDeleteQuickStyle = viewModel::deleteQuickStyle,
            onSave = { saved ->
                viewModel.upsertObject(saved, selectAfter = true)
                viewModel.selectTool(NotebookTool.SELECT)
                editingTextBox = null
            },
            onDismiss = { editingTextBox = null }
        )
    }

    editingCallout?.let { callout ->
        CalloutLabelEditorDialog(
            shape = callout,
            onSave = { saved ->
                viewModel.upsertObject(saved, selectAfter = true)
                editingCallout = null
            },
            onDismiss = { editingCallout = null }
        )
    }

    editingImage?.let { img ->
        ImageCropAndStyleDialog(
            imageItem = img,
            bitmap = uiState.loadedBitmaps[img.relativeAssetPath],
            onRemoveBackground = {
                editingImage = null
                viewModel.removeBackgroundFromImage(img)
            },
            onSave = { saved ->
                viewModel.upsertObject(saved, selectAfter = true)
                editingImage = null
            },
            onDismiss = { editingImage = null }
        )
    }

    editingTable?.let { tbl ->
        TableEditorDialog(
            table = tbl,
            onSave = { saved ->
                viewModel.upsertObject(saved, selectAfter = true)
                viewModel.selectTool(NotebookTool.SELECT)
                editingTable = null
            },
            onDismiss = { editingTable = null }
        )
    }

    if (showExportDialog && nb != null) {
        NotebookExportSheet(
            notebookTitle = nb.title,
            pageCount = uiState.pages.size.coerceAtLeast(1),
            currentPageNumber = uiState.currentPageIndex + 1,
            exportProgress = uiState.exportProgress,
            onShare = { opts ->
                viewModel.shareExport(opts) { intent ->
                    showExportDialog = false
                    context.startActivity(intent)
                }
            },
            onSaveToDevice = { opts ->
                viewModel.saveToDeviceDirect(opts) { suggestedName, _ ->
                    showExportDialog = false
                    launchSafForOptions(opts, suggestedName)
                }
            },
            onSaveToCustomFolder = { opts ->
                val suggestedName = NotebookExporter.suggestedFileName(
                    nb.title,
                    uiState.currentPageIndex + 1,
                    opts
                )
                launchSafForOptions(opts, suggestedName)
            },
            onAddPdfToLumaLibrary = { pdfOpts ->
                viewModel.exportPdfToLumaLibrary(pdfOpts, onImportPdfToLibrary)
            },
            onCancelExport = viewModel::cancelExport,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null,
    compact: Boolean = false,
    testTag: String? = null
) {
    val palette = LocalFolioPalette.current
    val resolvedTint = (tint ?: palette.ink).copy(alpha = if (enabled) 1f else 0.32f)
    val minTouch = if (compact) 40.dp else 48.dp
    Box(
        modifier = Modifier
            .sizeIn(minWidth = minTouch, minHeight = minTouch)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = resolvedTint,
            modifier = Modifier.size(if (compact) 18.dp else 21.dp)
        )
    }
}
