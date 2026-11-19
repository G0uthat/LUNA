package com.example.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HighlightNoteEntity
import com.example.data.model.BlockType
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.PageAnimationMode
import com.example.data.model.ParsedChapter
import com.example.data.model.ReaderPreferences
import com.example.data.model.SelectedWordContext
import com.example.data.model.TextAlignOption
import com.example.engine.FluidPaginator
import com.example.engine.FluidSheet
import com.example.engine.FluidSheetBlock
import com.example.ui.components.DrawingCanvasOverlay
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Sheet-paginated reflowable book reader for EPUB, DOCX, TXT, and FB2.
 * - Divides every chapter into crisp physical book sheets ("hojas") sized for the active screen & font.
 * - Stamps every drawing directly INSIDE each sheet's own coordinate container so strokes stay
 *   100% locked to the exact words you drew on, both in paged modes AND in continuous vertical scroll.
 * - Supports all 6 page animations (Libro 3D, Tinta electrónica, Diapositiva, Tarjeta, Desvanecer, Scroll)
 *   with BOTH swipe AND left/right edge tap navigation working reliably in every paged mode!
 * - Supports smooth Google Drive-style 2-finger pinch-to-zoom & pan on every sheet.
 */
@Composable
fun FluidBookReader(
    chapters: List<ParsedChapter>,
    currentChapterIndex: Int,
    jumpToChapterTrigger: Int,
    prefs: ReaderPreferences,
    highlights: List<HighlightNoteEntity>,
    drawingsByLocation: Map<String, List<DrawingStroke>>,
    isDrawingMode: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    onActiveSheetChanged: (sheetIndex: Int, totalSheets: Int, locationKey: String, chapterIndex: Int) -> Unit,
    onWordSelected: (SelectedWordContext) -> Unit,
    onStrokeCompleted: (String, DrawingStroke) -> Unit,
    onEraseStrokes: (String, Set<String>) -> Unit,
    onTapPageCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (chapters.isEmpty()) return
    val palette = LocalFolioPalette.current
    val coroutineScope = rememberCoroutineScope()

    // Fluid Google Drive-style sheet pinch-zoom state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val latestZoom by rememberUpdatedState(zoomScale)
    val latestPan by rememberUpdatedState(panOffset)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(isDrawingMode) {
                if (isDrawingMode) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val oldZoom = latestZoom
                                val newZoom = (oldZoom * zoomChange).coerceIn(1f, 3.2f)
                                if (newZoom <= 1.01f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val focal = centroid - center
                                    val ratio = newZoom / oldZoom
                                    val rawPan = (latestPan - focal) * ratio + focal + panChange
                                    val maxX = size.width * (newZoom - 1f) * 0.5f
                                    val maxY = size.height * (newZoom - 1f) * 0.5f
                                    zoomScale = newZoom
                                    panOffset = Offset(
                                        rawPan.x.coerceIn(-maxX, maxX),
                                        rawPan.y.coerceIn(-maxY, maxY)
                                    )
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (pressedCount == 1 && latestZoom > 1.02f) {
                            val panChange = event.calculatePan()
                            if (panChange != Offset.Zero) {
                                val maxX = size.width * (latestZoom - 1f) * 0.5f
                                val maxY = size.height * (latestZoom - 1f) * 0.5f
                                val nextX = (latestPan.x + panChange.x).coerceIn(-maxX, maxX)
                                val nextY = (latestPan.y + panChange.y).coerceIn(-maxY, maxY)
                                val hitEdgeX = abs(nextX - latestPan.x) < 0.5f && abs(panChange.x) > abs(panChange.y)
                                panOffset = Offset(nextX, nextY)
                                if (!hitEdgeX) {
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val viewportW = maxWidth.value.coerceAtLeast(320f)
        val viewportH = maxHeight.value.coerceAtLeast(480f)
        val sheetMinHeightDp = maxHeight

        val sheets = remember(
            chapters,
            prefs.fontSizeSp,
            prefs.lineHeightMultiplier,
            prefs.horizontalMarginDp,
            (viewportW / 24f).toInt(),
            (viewportH / 32f).toInt()
        ) {
            FluidPaginator.paginate(chapters, prefs, viewportW, viewportH)
        }

        if (sheets.isEmpty()) return@BoxWithConstraints

        val initialSheetIdx = remember(chapters) {
            sheets.indexOfFirst { it.chapterIndex == currentChapterIndex }.coerceAtLeast(0)
        }
        var activeSheetIndex by remember { mutableIntStateOf(initialSheetIdx.coerceIn(0, sheets.lastIndex)) }

        LaunchedEffect(jumpToChapterTrigger) {
            if (jumpToChapterTrigger > 0) {
                val target = sheets.indexOfFirst { it.chapterIndex == currentChapterIndex }
                if (target >= 0) {
                    activeSheetIndex = target
                }
            }
        }

        val clampedSheetIndex = activeSheetIndex.coerceIn(0, sheets.lastIndex)

        LaunchedEffect(clampedSheetIndex, sheets) {
            val s = sheets[clampedSheetIndex]
            onActiveSheetChanged(clampedSheetIndex, sheets.size, s.locationKey, s.chapterIndex)
        }

        val zoomLayerMod = Modifier.graphicsLayer {
            scaleX = zoomScale
            scaleY = zoomScale
            translationX = panOffset.x
            translationY = panOffset.y
        }

        when (prefs.pageAnimation) {
            PageAnimationMode.VERTICAL_SCROLL -> {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = clampedSheetIndex)

                LaunchedEffect(jumpToChapterTrigger) {
                    if (jumpToChapterTrigger > 0) {
                        val target = sheets.indexOfFirst { it.chapterIndex == currentChapterIndex }
                        if (target >= 0) {
                            listState.scrollToItem(target)
                        }
                    }
                }
                LaunchedEffect(listState.firstVisibleItemIndex) {
                    val idx = listState.firstVisibleItemIndex.coerceIn(0, sheets.lastIndex)
                    activeSheetIndex = idx
                }

                LazyColumn(
                    state = listState,
                    userScrollEnabled = !isDrawingMode && zoomScale <= 1.02f,
                    contentPadding = PaddingValues(top = 44.dp, bottom = 84.dp, start = 10.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.surfaceAlt.copy(alpha = 0.55f))
                        .then(zoomLayerMod)
                ) {
                    itemsIndexed(sheets, key = { _, s -> s.locationKey }) { index, sheet ->
                        val sheetStrokes = remember(drawingsByLocation, sheet) {
                            val primary = drawingsByLocation[sheet.locationKey].orEmpty()
                            if (sheet.sheetInChapter == 0) {
                                drawingsByLocation[sheet.cfiPrefix].orEmpty() + primary
                            } else {
                                primary
                            }
                        }

                        FluidSheetSurface(
                            sheet = sheet,
                            totalSheets = sheets.size,
                            prefs = prefs,
                            chapterHighlights = highlights.filter { it.chapterIndex == sheet.chapterIndex },
                            strokes = sheetStrokes,
                            isDrawingMode = isDrawingMode,
                            activeTool = activeTool,
                            activeColorArgb = activeColorArgb,
                            activeWidthNorm = activeWidthNorm,
                            isVerticalScrollCard = true,
                            minSheetHeightDp = (sheetMinHeightDp - 96.dp).coerceAtLeast(360.dp),
                            onWordSelected = onWordSelected,
                            onStrokeCompleted = { stroke ->
                                activeSheetIndex = index
                                onStrokeCompleted(sheet.locationKey, stroke)
                            },
                            onEraseStrokes = { ids ->
                                activeSheetIndex = index
                                onEraseStrokes(sheet.locationKey, ids)
                                if (sheet.sheetInChapter == 0) {
                                    onEraseStrokes(sheet.cfiPrefix, ids)
                                }
                            },
                            onTapAtXFraction = { onTapPageCenter() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                        )
                    }
                }
            }

            else -> {
                // Unified HorizontalPager for ALL paged modes (Libro 3D, Tinta electrónica, Diapositiva, Tarjeta, Desvanecer)
                // Ensures BOTH horizontal swiping AND left/right edge tapping advance sheets seamlessly!
                val pagerState = rememberPagerState(initialPage = clampedSheetIndex) { sheets.size }
                var eInkFlash by remember { mutableStateOf(false) }
                var lastRenderedPage by remember { mutableIntStateOf(clampedSheetIndex) }

                LaunchedEffect(jumpToChapterTrigger) {
                    if (jumpToChapterTrigger > 0) {
                        val target = sheets.indexOfFirst { it.chapterIndex == currentChapterIndex }
                        if (target >= 0 && pagerState.currentPage != target) {
                            if (prefs.pageAnimation == PageAnimationMode.E_INK) {
                                pagerState.scrollToPage(target)
                            } else {
                                pagerState.animateScrollToPage(target)
                            }
                        }
                    }
                }

                LaunchedEffect(pagerState.currentPage, prefs.pageAnimation) {
                    val idx = pagerState.currentPage.coerceIn(0, sheets.lastIndex)
                    activeSheetIndex = idx
                    zoomScale = 1f
                    panOffset = Offset.Zero
                    if (prefs.pageAnimation == PageAnimationMode.E_INK && idx != lastRenderedPage) {
                        eInkFlash = true
                        delay(85)
                        eInkFlash = false
                    }
                    lastRenderedPage = idx
                }

                val handlePagedTap: (Float) -> Unit = { xFraction ->
                    if (zoomScale > 1.05f) {
                        onTapPageCenter()
                    } else {
                        when {
                            xFraction < 0.22f && pagerState.currentPage > 0 -> {
                                val prev = pagerState.currentPage - 1
                                coroutineScope.launch {
                                    if (prefs.pageAnimation == PageAnimationMode.E_INK) {
                                        pagerState.scrollToPage(prev)
                                    } else {
                                        pagerState.animateScrollToPage(prev)
                                    }
                                }
                            }
                            xFraction > 0.78f && pagerState.currentPage < sheets.lastIndex -> {
                                val next = pagerState.currentPage + 1
                                coroutineScope.launch {
                                    if (prefs.pageAnimation == PageAnimationMode.E_INK) {
                                        pagerState.scrollToPage(next)
                                    } else {
                                        pagerState.animateScrollToPage(next)
                                    }
                                }
                            }
                            else -> onTapPageCenter()
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize().then(zoomLayerMod)) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isDrawingMode && zoomScale <= 1.02f,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx ->
                        val sheet = sheets[pageIdx]
                        val pageOffset = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                        val animModifier = when (prefs.pageAnimation) {
                            PageAnimationMode.BOOK_CURL -> Modifier
                                .graphicsLayer {
                                    val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                                    cameraDistance = 14f * density
                                    if (pageOffset >= 0f) {
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                        rotationY = -pageOffset * 52f
                                        translationX = pageOffset * size.width * 0.14f
                                        alpha = 1f - absOffset * 0.22f
                                    } else {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        scaleX = 0.96f + (1f - absOffset) * 0.04f
                                        scaleY = 0.96f + (1f - absOffset) * 0.04f
                                    }
                                }
                                .drawWithContent {
                                    drawContent()
                                    val curlStrength = abs(pageOffset).coerceIn(0f, 1f)
                                    if (curlStrength > 0.01f) {
                                        val shadowWidth = size.width * 0.18f * curlStrength
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.16f * curlStrength),
                                                    Color.White.copy(alpha = 0.12f * curlStrength),
                                                    Color.Black.copy(alpha = 0.22f * curlStrength)
                                                ),
                                                startX = size.width - shadowWidth,
                                                endX = size.width
                                            )
                                        )
                                    }
                                }

                            PageAnimationMode.DEPTH_CARD -> Modifier.graphicsLayer {
                                val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                                if (pageOffset < 0f) {
                                    val scale = 0.88f + (1f - absOffset) * 0.12f
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 0.45f + (1f - absOffset) * 0.55f
                                    translationX = -pageOffset * size.width * 0.72f
                                } else {
                                    alpha = 1f - absOffset * 0.15f
                                }
                            }

                            PageAnimationMode.FADE -> Modifier.graphicsLayer {
                                translationX = pageOffset * size.width
                                alpha = (1f - abs(pageOffset)).coerceIn(0f, 1f)
                            }

                            PageAnimationMode.E_INK -> Modifier.graphicsLayer {
                                translationX = pageOffset * size.width
                                alpha = if (abs(pageOffset) < 0.5f) 1f else 0f
                            }

                            else -> Modifier
                        }

                        val sheetStrokes = remember(drawingsByLocation, sheet) {
                            val primary = drawingsByLocation[sheet.locationKey].orEmpty()
                            if (sheet.sheetInChapter == 0) {
                                drawingsByLocation[sheet.cfiPrefix].orEmpty() + primary
                            } else {
                                primary
                            }
                        }

                        FluidSheetSurface(
                            sheet = sheet,
                            totalSheets = sheets.size,
                            prefs = prefs,
                            chapterHighlights = highlights.filter { it.chapterIndex == sheet.chapterIndex },
                            strokes = sheetStrokes,
                            isDrawingMode = isDrawingMode,
                            activeTool = activeTool,
                            activeColorArgb = activeColorArgb,
                            activeWidthNorm = activeWidthNorm,
                            isVerticalScrollCard = false,
                            minSheetHeightDp = sheetMinHeightDp,
                            onWordSelected = onWordSelected,
                            onStrokeCompleted = { onStrokeCompleted(sheet.locationKey, it) },
                            onEraseStrokes = { ids ->
                                onEraseStrokes(sheet.locationKey, ids)
                                if (sheet.sheetInChapter == 0) onEraseStrokes(sheet.cfiPrefix, ids)
                            },
                            onTapAtXFraction = handlePagedTap,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(animModifier)
                        )
                    }

                    // Authentic E-Ink quick contrast refresh flash
                    if (eInkFlash) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (palette.isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.65f))
                        )
                    }
                }
            }
        }

        // Discreet reset zoom pill at bottom-center (never touches Dynamic Island or top bar!)
        if (!isDrawingMode && zoomScale > 1.05f) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 42.dp)
                    .shadow(8.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(palette.surface.copy(alpha = 0.94f))
                    .border(1.dp, palette.border, RoundedCornerShape(50))
                    .clickable {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.FitScreen,
                    contentDescription = "Restablecer zoom",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Ajustar (${(zoomScale * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.ink
                )
            }
        }
    }
}

/**
 * Single physical book sheet ("hoja").
 * - Outer container handles scrolling (when in paged mode and content slightly exceeds height).
 * - Inner Box wraps BOTH the text Column AND DrawingCanvasOverlay(matchParentSize()) together.
 * Result: every stroke is physically stamped onto the sheet coordinates and moves 1:1 with the text!
 */
@Composable
private fun FluidSheetSurface(
    sheet: FluidSheet,
    totalSheets: Int,
    prefs: ReaderPreferences,
    chapterHighlights: List<HighlightNoteEntity>,
    strokes: List<DrawingStroke>,
    isDrawingMode: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    isVerticalScrollCard: Boolean,
    minSheetHeightDp: Dp,
    onWordSelected: (SelectedWordContext) -> Unit,
    onStrokeCompleted: (DrawingStroke) -> Unit,
    onEraseStrokes: (Set<String>) -> Unit,
    onTapAtXFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    var sheetRootSizePx by remember { mutableStateOf(Pair(1f, 1f)) }
    val scrollState = rememberScrollState()

    val outerMod = if (isVerticalScrollCard) {
        val shape = RoundedCornerShape(20.dp)
        modifier
            .shadow(elevation = if (palette.isDark) 2.dp else 6.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.14f))
            .clip(shape)
            .background(palette.background)
            .border(1.dp, palette.border.copy(alpha = 0.65f), shape)
    } else {
        modifier
            .background(palette.background)
            .then(if (!isDrawingMode) Modifier.verticalScroll(scrollState) else Modifier)
    }

    Box(
        modifier = outerMod.then(
            if (!isDrawingMode) {
                Modifier.pointerInput(sheet.locationKey) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onTapAtXFraction(frac)
                    }
                }
            } else {
                Modifier
            }
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        // Unified sheet box: BOTH text and drawings live in this exact same coordinate frame!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .defaultMinSize(minHeight = minSheetHeightDp)
                .onGloballyPositioned { coords ->
                    sheetRootSizePx = coords.size.width.toFloat().coerceAtLeast(1f) to
                        coords.size.height.toFloat().coerceAtLeast(1f)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = prefs.horizontalMarginDp.dp,
                        vertical = if (isVerticalScrollCard) 28.dp else 54.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (sheetBlock in sheet.blocks) {
                    val rawBlock = sheetBlock.block
                    val sliceStart = sheetBlock.charOffsetInBlock
                    val sliceEnd = sliceStart + rawBlock.text.length
                    val blockHighlights = chapterHighlights
                        .filter { it.blockIndex == rawBlock.index && it.endChar > sliceStart && it.startChar < sliceEnd }
                        .map { hl ->
                            hl.copy(
                                startChar = (hl.startChar - sliceStart).coerceIn(0, rawBlock.text.length),
                                endChar = (hl.endChar - sliceStart).coerceIn(0, rawBlock.text.length)
                            )
                        }

                    SelectableTextBlock(
                        sheetBlock = sheetBlock,
                        sheet = sheet,
                        prefs = prefs,
                        highlights = blockHighlights,
                        isDrawingMode = isDrawingMode,
                        containerSizePx = sheetRootSizePx,
                        onWordSelected = onWordSelected,
                        onTapAtXFraction = onTapAtXFraction
                    )
                }
                Spacer(Modifier.height(26.dp))
            }

            // Subtle sheet page number stamp in continuous scroll mode so sheets feel like real pages
            if (isVerticalScrollCard) {
                Text(
                    text = "Hoja ${sheet.globalIndex + 1} / $totalSheets",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }

            // Sheet-anchored persistent freehand drawing canvas (moves 1:1 with the sheet!)
            DrawingCanvasOverlay(
                strokes = strokes,
                isInteractive = isDrawingMode,
                activeTool = activeTool,
                activeColorArgb = activeColorArgb,
                activeWidthNorm = activeWidthNorm,
                onStrokeCompleted = onStrokeCompleted,
                onEraseStrokeIds = onEraseStrokes,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun SelectableTextBlock(
    sheetBlock: FluidSheetBlock,
    sheet: FluidSheet,
    prefs: ReaderPreferences,
    highlights: List<HighlightNoteEntity>,
    isDrawingMode: Boolean,
    containerSizePx: Pair<Float, Float>,
    onWordSelected: (SelectedWordContext) -> Unit,
    onTapAtXFraction: (Float) -> Unit
) {
    val block = sheetBlock.block
    val palette = LocalFolioPalette.current
    val fontFamily = if (prefs.useSystemFont) FontFamily.Default else prefs.font.toFontFamily()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var blockCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val baseFontSize = when (block.type) {
        BlockType.HEADING_1 -> (prefs.fontSizeSp * 1.42f).sp
        BlockType.HEADING_2 -> (prefs.fontSizeSp * 1.18f).sp
        BlockType.QUOTE -> (prefs.fontSizeSp * 0.96f).sp
        else -> prefs.fontSizeSp.sp
    }

    val textStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = baseFontSize,
        lineHeight = (baseFontSize.value * prefs.lineHeightMultiplier).sp,
        fontWeight = when (block.type) {
            BlockType.HEADING_1 -> FontWeight.ExtraBold
            BlockType.HEADING_2 -> FontWeight.Bold
            else -> FontWeight.Normal
        },
        fontStyle = if (block.type == BlockType.QUOTE) FontStyle.Italic else FontStyle.Normal,
        color = if (block.type == BlockType.QUOTE) palette.inkMuted else palette.ink,
        textAlign = if (block.type == BlockType.PARAGRAPH && prefs.textAlign == TextAlignOption.JUSTIFY) {
            TextAlign.Justify
        } else {
            TextAlign.Start
        }
    )

    val annotated = remember(block.text, highlights) {
        buildAnnotatedString {
            append(block.text)
            for (hl in highlights) {
                val start = hl.startChar.coerceIn(0, block.text.length)
                val end = hl.endChar.coerceIn(start, block.text.length)
                if (end > start) {
                    addStyle(
                        style = SpanStyle(
                            background = Color(hl.colorArgb.toInt()).copy(alpha = 0.36f)
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }

    val gestureMod = if (!isDrawingMode) {
        Modifier.pointerInput(block.text, sheet.locationKey, containerSizePx) {
            detectTapGestures(
                onTap = { offset: Offset ->
                    val rootPos = blockCoords?.positionInRoot() ?: Offset.Zero
                    val xFrac = ((rootPos.x + offset.x) / containerSizePx.first).coerceIn(0f, 1f)
                    onTapAtXFraction(xFrac)
                },
                onLongPress = { offset: Offset ->
                    val layout = textLayoutResult ?: return@detectTapGestures
                    val charIndex = layout.getOffsetForPosition(offset)
                    val (word, start, end) = extractWordAt(block.text, charIndex)
                    if (word.isNotBlank()) {
                        val rootPos = blockCoords?.positionInRoot() ?: Offset.Zero
                        val screenX = ((rootPos.x + offset.x) / containerSizePx.first).coerceIn(0.08f, 0.92f)
                        val screenY = ((rootPos.y + offset.y) / containerSizePx.second).coerceIn(0.10f, 0.90f)
                        onWordSelected(
                            SelectedWordContext(
                                word = word,
                                snippet = block.text.take(200),
                                locationKey = sheet.locationKey,
                                blockIndex = block.index,
                                startChar = start + sheetBlock.charOffsetInBlock,
                                endChar = end + sheetBlock.charOffsetInBlock,
                                anchorXFraction = screenX,
                                anchorYFraction = screenY
                            )
                        )
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (block.type == BlockType.QUOTE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceAlt.copy(alpha = 0.55f))
                    .padding(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.accent)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = annotated,
                    style = textStyle,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { blockCoords = it }
                        .then(gestureMod)
                )
            }
        } else {
            val prefix = if (block.type == BlockType.LIST_ITEM) "•  " else ""
            Text(
                text = if (prefix.isEmpty()) annotated else buildAnnotatedString {
                    append(prefix)
                    append(annotated)
                },
                style = textStyle,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { blockCoords = it }
                    .then(gestureMod)
            )
        }

        val notesHere = highlights.filter { it.noteText.isNotBlank() }
        if (notesHere.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            for (noteItem in notesHere) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(noteItem.colorArgb.toInt()).copy(alpha = 0.16f))
                        .border(
                            1.dp,
                            Color(noteItem.colorArgb.toInt()).copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nota: ${noteItem.noteText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.ink
                    )
                }
            }
        }
    }
}

private fun extractWordAt(text: String, index: Int): Triple<String, Int, Int> {
    if (text.isEmpty()) return Triple("", 0, 0)
    val safe = index.coerceIn(0, text.lastIndex)
    var start = safe
    var end = safe
    while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '-' || text[start - 1] == '\'')) {
        start--
    }
    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '-' || text[end] == '\'')) {
        end++
    }
    return Triple(text.substring(start, end), start, end)
}
