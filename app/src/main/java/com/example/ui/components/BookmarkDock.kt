package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.BookmarkDockSide
import com.example.data.model.ReaderMenuStyle
import com.example.ui.theme.LocalFolioPalette
import kotlin.math.roundToInt

/**
 * Unified distraction-free Reader Menu supporting 3 configurable styles:
 * 1. BOOKMARK_DOCK: Soft organic bookmark pill draggable freely to ANY side & vertical height.
 * 2. DYNAMIC_ISLAND: Sleek Apple-style floating top capsule expanding softly on tap.
 * 3. CLASSIC_BAR: Traditional Kindle-style top & bottom overlay bars toggled by tapping center.
 *
 * Keeps ONLY objective reading actions + direct brightness control + a tiny discreet Settings gear (Kindle style).
 * Respects [dockScale] (0.85x..1.35x) for phones and large tablets.
 */
@Composable
fun BookmarkDockOverlay(
    menuStyle: ReaderMenuStyle,
    side: BookmarkDockSide,
    dockYFraction: Float,
    dockScale: Float,
    brightnessValue: Float, // -1f = auto, 0.05f..1f = manual
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isDrawingMode: Boolean,
    isPdfDocument: Boolean,
    isPageTranslated: Boolean,
    isProcessingStatus: Boolean = false,
    processingStatusText: String = "",
    bookTitle: String,
    progressLabel: String,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    onDockRepositioned: (BookmarkDockSide, Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onToggleBookmarkHere: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleDrawing: () -> Unit,
    onTriggerOcrTranslate: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isDrawingMode) return

    when (menuStyle) {
        ReaderMenuStyle.BOOKMARK_DOCK -> DraggableSoftBookmarkDock(
            side = side,
            dockYFraction = dockYFraction,
            dockScale = dockScale,
            brightnessValue = brightnessValue,
            isExpanded = isExpanded,
            isBookmarkedHere = isBookmarkedHere,
            isPdfDocument = isPdfDocument,
            isPageTranslated = isPageTranslated,
            bookTitle = bookTitle,
            progressLabel = progressLabel,
            onToggleExpanded = onToggleExpanded,
            onDismiss = onDismiss,
            onDockRepositioned = onDockRepositioned,
            onBrightnessChanged = onBrightnessChanged,
            onToggleBookmarkHere = onToggleBookmarkHere,
            onOpenAppearance = onOpenAppearance,
            onOpenToc = onOpenToc,
            onToggleDrawing = onToggleDrawing,
            onTriggerOcrTranslate = onTriggerOcrTranslate,
            onOpenAnnotations = onOpenAnnotations,
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onOpenExport = onOpenExport,
            onBackToLibrary = onBackToLibrary,
            modifier = modifier
        )

        ReaderMenuStyle.DYNAMIC_ISLAND -> DynamicIslandReaderDock(
            dockScale = dockScale,
            brightnessValue = brightnessValue,
            isExpanded = isExpanded,
            isBookmarkedHere = isBookmarkedHere,
            isPdfDocument = isPdfDocument,
            isPageTranslated = isPageTranslated,
            isProcessingStatus = isProcessingStatus,
            processingStatusText = processingStatusText,
            bookTitle = bookTitle,
            progressLabel = progressLabel,
            onToggleExpanded = onToggleExpanded,
            onDismiss = onDismiss,
            onBrightnessChanged = onBrightnessChanged,
            onToggleBookmarkHere = onToggleBookmarkHere,
            onOpenAppearance = onOpenAppearance,
            onOpenToc = onOpenToc,
            onToggleDrawing = onToggleDrawing,
            onTriggerOcrTranslate = onTriggerOcrTranslate,
            onOpenAnnotations = onOpenAnnotations,
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onOpenExport = onOpenExport,
            onBackToLibrary = onBackToLibrary,
            modifier = modifier
        )

        ReaderMenuStyle.CLASSIC_BAR -> ClassicKindleOverlayBars(
            dockScale = dockScale,
            brightnessValue = brightnessValue,
            isExpanded = isExpanded,
            isBookmarkedHere = isBookmarkedHere,
            isPdfDocument = isPdfDocument,
            isPageTranslated = isPageTranslated,
            bookTitle = bookTitle,
            progressLabel = progressLabel,
            onDismiss = onDismiss,
            onBrightnessChanged = onBrightnessChanged,
            onToggleBookmarkHere = onToggleBookmarkHere,
            onOpenAppearance = onOpenAppearance,
            onOpenToc = onOpenToc,
            onToggleDrawing = onToggleDrawing,
            onTriggerOcrTranslate = onTriggerOcrTranslate,
            onOpenAnnotations = onOpenAnnotations,
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onOpenExport = onOpenExport,
            onBackToLibrary = onBackToLibrary,
            modifier = modifier
        )
    }
}

@Composable
private fun DraggableSoftBookmarkDock(
    side: BookmarkDockSide,
    dockYFraction: Float,
    dockScale: Float,
    brightnessValue: Float,
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isPdfDocument: Boolean,
    isPageTranslated: Boolean,
    bookTitle: String,
    progressLabel: String,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    onDockRepositioned: (BookmarkDockSide, Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onToggleBookmarkHere: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleDrawing: () -> Unit,
    onTriggerOcrTranslate: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val density = LocalDensity.current
    val isRight = side == BookmarkDockSide.RIGHT

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val tabTouchW = with(density) { (48.dp * dockScale.coerceIn(0.9f, 1.25f)).toPx() }
        val tabTouchH = with(density) { (68.dp * dockScale.coerceIn(0.9f, 1.25f)).toPx() }

        var isDragging by remember { mutableStateOf(false) }
        var dragX by remember(side, maxW) {
            mutableFloatStateOf(if (isRight) maxW - tabTouchW else 0f)
        }
        var dragY by remember(dockYFraction, maxH) {
            mutableFloatStateOf((dockYFraction * maxH - tabTouchH / 2f).coerceIn(maxH * 0.12f, maxH * 0.84f))
        }

        // Click-outside scrim when expanded
        AnimatedVisibility(visible = isExpanded, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Soft Organic Draggable Bookmark Pill Tab
        if (!isExpanded) {
            val currentX = if (isDragging) dragX else if (isRight) maxW - tabTouchW else 0f
            val currentY = if (isDragging) dragY else (dockYFraction * maxH - tabTouchH / 2f).coerceIn(maxH * 0.12f, maxH * 0.84f)
            val pillShape = if (isRight && !isDragging) {
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp)
            } else if (!isRight && !isDragging) {
                RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 4.dp, bottomStart = 4.dp)
            } else {
                RoundedCornerShape(20.dp)
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                    .size(
                        width = with(density) { tabTouchW.toDp() },
                        height = with(density) { tabTouchH.toDp() }
                    )
                    .pointerInput(maxW, maxH) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragX = if (isRight) maxW - tabTouchW else 0f
                                dragY = (dockYFraction * maxH - tabTouchH / 2f).coerceIn(maxH * 0.12f, maxH * 0.84f)
                            },
                            onDragEnd = {
                                isDragging = false
                                val newSide = if (dragX + tabTouchW / 2f > maxW / 2f) {
                                    BookmarkDockSide.RIGHT
                                } else {
                                    BookmarkDockSide.LEFT
                                }
                                val newYFrac = ((dragY + tabTouchH / 2f) / maxH).coerceIn(0.14f, 0.86f)
                                onDockRepositioned(newSide, newYFrac)
                            },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragX = (dragX + dragAmount.x).coerceIn(0f, maxW - tabTouchW)
                                dragY = (dragY + dragAmount.y).coerceIn(maxH * 0.10f, maxH * 0.86f)
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleExpanded
                    )
                    .testTag("bookmark_dock_tab"),
                contentAlignment = if (isRight && !isDragging) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                // Soft translucent capsule bookmark handle
                Box(
                    modifier = Modifier
                        .size(
                            width = (26.dp * dockScale.coerceIn(0.9f, 1.25f)),
                            height = (54.dp * dockScale.coerceIn(0.9f, 1.25f))
                        )
                        .shadow(6.dp, pillShape, ambientColor = palette.accent.copy(alpha = 0.25f))
                        .clip(pillShape)
                        .background(palette.accent.copy(alpha = 0.86f))
                        .border(1.dp, Color.White.copy(alpha = 0.26f), pillShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBookmarkedHere) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd,
                        contentDescription = "Abrir menú marcapáginas (arrastrable)",
                        tint = Color.White,
                        modifier = Modifier.size((15.dp * dockScale.coerceIn(0.9f, 1.25f)))
                    )
                }
            }
        }

        // Expanded Soft Floating Drawer (Objective reading tools + Direct Brightness + Tiny discreet Gear)
        val dockWidth = (260.dp * dockScale).coerceIn(226.dp, 360.dp)
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInHorizontally(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetX = { w -> if (isRight) w else -w }
            ) + fadeIn(),
            exit = slideOutHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                targetOffsetX = { w -> if (isRight) w else -w }
            ) + fadeOut(),
            modifier = Modifier
                .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 20.dp)
        ) {
            val dockShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .width(dockWidth)
                    .shadow(16.dp, dockShape, ambientColor = Color.Black.copy(alpha = 0.22f))
                    .clip(dockShape)
                    .background(palette.surface.copy(alpha = 0.97f))
                    .border(1.dp, palette.border, dockShape)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
                    .testTag("bookmark_dock_menu"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Soft header with title + tiny discreet Settings gear (Kindle style) + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkMuted,
                            maxLines = 1
                        )
                    }
                    // Tiny discreet settings gear (non-intrusive Kindle style)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                onDismiss()
                                onOpenSettings()
                            }
                            .testTag("dock_discreet_settings"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Configuración",
                            tint = palette.inkMuted.copy(alpha = 0.72f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar menú",
                            tint = palette.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Direct Brightness Control directly inside the floating menu!
                DirectBrightnessRow(
                    brightnessValue = brightnessValue,
                    onBrightnessChanged = onBrightnessChanged
                )

                // Objective Reading Tools ONLY
                DockToolRow(
                    icon = if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    label = if (isBookmarkedHere) "Quitar marcapáginas" else "Marcar esta página",
                    highlighted = isBookmarkedHere,
                    scale = dockScale,
                    onClick = { onToggleBookmarkHere(); onDismiss() },
                    testTag = "dock_toggle_bookmark"
                )
                DockToolRow(
                    icon = Icons.Rounded.TextFields,
                    label = "Texto, Tema y Animación",
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenAppearance() },
                    testTag = "dock_appearance"
                )
                DockToolRow(
                    icon = Icons.Rounded.FormatListBulleted,
                    label = "Índice",
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenToc() },
                    testTag = "dock_toc"
                )
                DockToolRow(
                    icon = Icons.Rounded.Draw,
                    label = "Dibujar en la página",
                    scale = dockScale,
                    onClick = { onDismiss(); onToggleDrawing() },
                    testTag = "dock_draw"
                )
                if (isPdfDocument) {
                    DockToolRow(
                        icon = Icons.Rounded.Translate,
                        label = if (isPageTranslated) "Ver original" else "Traducir página (OCR)",
                        highlighted = isPageTranslated,
                        scale = dockScale,
                        onClick = { onDismiss(); onTriggerOcrTranslate() },
                        testTag = "dock_ocr_translate"
                    )
                }
                DockToolRow(
                    icon = Icons.Rounded.Bookmarks,
                    label = "Notas y Marcadores",
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenAnnotations() },
                    testTag = "dock_annotations"
                )
                DockToolRow(
                    icon = Icons.Rounded.Search,
                    label = "Buscar texto",
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenSearch() },
                    testTag = "dock_search"
                )
                DockToolRow(
                    icon = Icons.Rounded.IosShare,
                    label = "Exportar fusionado",
                    accentTint = true,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenExport() },
                    testTag = "dock_export"
                )

                Spacer(Modifier.height(2.dp))
                TactileButton(
                    onClick = { onDismiss(); onBackToLibrary() },
                    containerColor = palette.surfaceAlt,
                    shadowColor = palette.borderShadow,
                    contentColor = palette.ink,
                    borderColor = palette.border,
                    cornerRadius = 16.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier.testTag("dock_back_library")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Biblioteca", style = MaterialTheme.typography.labelLarge, color = palette.ink)
                }
            }
        }
    }
}

/**
 * Apple / Infinix-style Dynamic Island floating capsule at top center:
 * - Uses crisp Zoom-In & Zoom-Out anchored at top-center (TransformOrigin(0.5f, 0f)) with
 *   FastOutSlowInEasing (ZERO overshoot bounce and ZERO square clipping artifacts!).
 * - Integrates live OCR / translation status directly inside the capsule (Live Activity style)
 *   so floating pills never overlap or collide with the Dynamic Island!
 */
@Composable
private fun DynamicIslandReaderDock(
    dockScale: Float,
    brightnessValue: Float,
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isPdfDocument: Boolean,
    isPageTranslated: Boolean,
    isProcessingStatus: Boolean,
    processingStatusText: String,
    bookTitle: String,
    progressLabel: String,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onToggleBookmarkHere: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleDrawing: () -> Unit,
    onTriggerOcrTranslate: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val islandBg = Color(0xFF0E131F)
    val islandFg = Color(0xFFF3F6FC)
    val topOrigin = TransformOrigin(0.5f, 0f)

    Box(modifier = modifier.fillMaxSize()) {
        // Click-outside scrim when expanded
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Top-center anchor container (NO animateContentSize rectangular clip!)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 1. Compact Apple/Infinix Dynamic Island Pill (zooms in smoothly when collapsed)
            AnimatedVisibility(
                visible = !isExpanded,
                enter = scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = topOrigin,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(180)),
                exit = scaleOut(
                    targetScale = 0.82f,
                    transformOrigin = topOrigin,
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(130))
            ) {
                val pillShape = RoundedCornerShape(50)
                Row(
                    modifier = Modifier
                        .shadow(12.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.35f))
                        .clip(pillShape)
                        .background(islandBg.copy(alpha = 0.95f))
                        .border(
                            width = 1.dp,
                            color = if (isProcessingStatus) palette.accent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f),
                            shape = pillShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleExpanded
                        )
                        .padding(
                            horizontal = (16.dp * dockScale.coerceIn(0.9f, 1.25f)),
                            vertical = (8.dp * dockScale.coerceIn(0.9f, 1.25f))
                        )
                        .testTag("dynamic_island_dock"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isProcessingStatus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF639BFF)
                        )
                        Text(
                            text = processingStatusText.ifBlank { "Traduciendo…" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = islandFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isBookmarkedHere) Color(0xFF639BFF) else Color(0xFF39C689))
                        )
                        Text(
                            text = progressLabel.substringBefore("·").trim(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = islandFg
                        )
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = "Expandir isla dinámica",
                            tint = islandFg.copy(alpha = 0.72f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 2. Expanded Dynamic Island Card — pure Zoom-In / Zoom-Out from top-center origin
            AnimatedVisibility(
                visible = isExpanded,
                enter = scaleIn(
                    initialScale = 0.52f,
                    transformOrigin = topOrigin,
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(190)),
                exit = scaleOut(
                    targetScale = 0.52f,
                    transformOrigin = topOrigin,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(150))
            ) {
                val expandedW = (324.dp * dockScale).coerceIn(280.dp, 440.dp)
                val expandedShape = RoundedCornerShape(30.dp)
                Column(
                    modifier = Modifier
                        .width(expandedW)
                        .shadow(18.dp, expandedShape, ambientColor = Color.Black.copy(alpha = 0.42f))
                        .clip(expandedShape)
                        .background(islandBg.copy(alpha = 0.96f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), expandedShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(16.dp)
                        .testTag("dynamic_island_expanded"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable { onDismiss(); onBackToLibrary() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Biblioteca", tint = islandFg, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = islandFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isProcessingStatus && processingStatusText.isNotBlank()) {
                                    processingStatusText
                                } else {
                                    progressLabel
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isProcessingStatus) Color(0xFF639BFF) else islandFg.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss(); onOpenSettings() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Configuración", tint = islandFg.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Contraer", tint = islandFg.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
                        }
                    }

                    // Brightness slider inside Dynamic Island
                    DirectBrightnessRow(
                        brightnessValue = brightnessValue,
                        onBrightnessChanged = onBrightnessChanged,
                        darkSurfaceOverride = true
                    )

                    // Row 1 of quick objective tools
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IslandActionTile(Icons.Rounded.TextFields, "Texto", false) { onDismiss(); onOpenAppearance() }
                        IslandActionTile(Icons.Rounded.FormatListBulleted, "Índice", false) { onDismiss(); onOpenToc() }
                        IslandActionTile(Icons.Rounded.Draw, "Dibujar", false) { onDismiss(); onToggleDrawing() }
                        IslandActionTile(
                            if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                            "Marcar",
                            isBookmarkedHere
                        ) { onToggleBookmarkHere(); onDismiss() }
                    }

                    // Row 2 of quick objective tools
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isPdfDocument) {
                            IslandActionTile(Icons.Rounded.Translate, if (isPageTranslated) "Original" else "Traducir", isPageTranslated) {
                                onDismiss(); onTriggerOcrTranslate()
                            }
                        }
                        IslandActionTile(Icons.Rounded.Bookmarks, "Notas", false) { onDismiss(); onOpenAnnotations() }
                        IslandActionTile(Icons.Rounded.Search, "Buscar", false) { onDismiss(); onOpenSearch() }
                        IslandActionTile(Icons.Rounded.IosShare, "Exportar", false) { onDismiss(); onOpenExport() }
                    }
                }
            }
        }
    }
}

/**
 * Classic Kindle-style top & bottom overlay bars revealed by tapping the center of the page.
 */
@Composable
private fun ClassicKindleOverlayBars(
    dockScale: Float,
    brightnessValue: Float,
    isExpanded: Boolean,
    isBookmarkedHere: Boolean,
    isPdfDocument: Boolean,
    isPageTranslated: Boolean,
    bookTitle: String,
    progressLabel: String,
    onDismiss: () -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onToggleBookmarkHere: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleDrawing: () -> Unit,
    onTriggerOcrTranslate: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    Box(modifier = modifier.fillMaxSize()) {
        // Top classic bar
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface.copy(alpha = 0.96f))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = (10.dp * dockScale.coerceIn(0.9f, 1.25f))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss(); onBackToLibrary() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Biblioteca", tint = palette.ink)
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onToggleBookmarkHere() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = "Marcar página",
                        tint = if (isBookmarkedHere) palette.accent else palette.ink
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss(); onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Configuración",
                        tint = palette.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom classic bar with brightness + objective tools
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .background(palette.surface.copy(alpha = 0.97f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectBrightnessRow(
                    brightnessValue = brightnessValue,
                    onBrightnessChanged = onBrightnessChanged
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ClassicBarAction(Icons.Rounded.FormatListBulleted, "Índice") { onDismiss(); onOpenToc() }
                    ClassicBarAction(Icons.Rounded.TextFields, "Texto") { onDismiss(); onOpenAppearance() }
                    ClassicBarAction(Icons.Rounded.Draw, "Dibujar") { onDismiss(); onToggleDrawing() }
                    if (isPdfDocument) {
                        ClassicBarAction(Icons.Rounded.Translate, "Traducir") { onDismiss(); onTriggerOcrTranslate() }
                    }
                    ClassicBarAction(Icons.Rounded.Bookmarks, "Notas") { onDismiss(); onOpenAnnotations() }
                    ClassicBarAction(Icons.Rounded.Search, "Buscar") { onDismiss(); onOpenSearch() }
                    ClassicBarAction(Icons.Rounded.IosShare, "Exportar") { onDismiss(); onOpenExport() }
                }
            }
        }
    }
}

@Composable
private fun DirectBrightnessRow(
    brightnessValue: Float,
    onBrightnessChanged: (Float) -> Unit,
    darkSurfaceOverride: Boolean = false
) {
    val palette = LocalFolioPalette.current
    val bg = if (darkSurfaceOverride) Color.White.copy(alpha = 0.08f) else palette.surfaceAlt.copy(alpha = 0.72f)
    val fg = if (darkSurfaceOverride) Color(0xFFF3F6FC) else palette.ink
    val sliderPos = if (brightnessValue < 0f) 0.5f else brightnessValue.coerceIn(0.05f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .testTag("dock_brightness_control"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.BrightnessLow,
            contentDescription = "Bajar brillo",
            tint = fg.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Slider(
            value = sliderPos,
            onValueChange = { v -> onBrightnessChanged(v.coerceIn(0.05f, 1f)) },
            valueRange = 0.05f..1f,
            modifier = Modifier.weight(1f).height(34.dp).padding(horizontal = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent
            )
        )
        Icon(
            imageVector = Icons.Rounded.Brightness6,
            contentDescription = "Subir brillo",
            tint = fg.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun IslandActionTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) palette.accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
    }
}

@Composable
private fun ClassicBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = palette.ink, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.inkMuted, maxLines = 1)
    }
}

@Composable
private fun DockToolRow(
    icon: ImageVector,
    label: String,
    scale: Float,
    onClick: () -> Unit,
    testTag: String,
    highlighted: Boolean = false,
    accentTint: Boolean = false
) {
    val palette = LocalFolioPalette.current
    val bg = when {
        highlighted -> palette.accent
        accentTint -> palette.accentSoft
        else -> palette.surfaceAlt.copy(alpha = 0.62f)
    }
    val fg = when {
        highlighted -> palette.onAccent
        accentTint -> palette.accent
        else -> palette.ink
    }
    val shape = RoundedCornerShape(16.dp)
    val rowH = (44.dp * scale.coerceIn(0.88f, 1.25f)).coerceAtLeast(42.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowH)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
