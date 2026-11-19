package com.example.ui.reader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.OcrPageResult
import com.example.data.model.PageAnimationMode
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.SelectedWordContext
import com.example.ui.components.DrawingCanvasOverlay
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Native PDF & Manhwa Reader:
 * - High-resolution crisp rendering via android.graphics.pdf.PdfRenderer
 * - Google Drive-style continuous focal pinch-to-zoom (1x–4.5x), 1-finger pan when zoomed,
 *   and instant double-tap zoom around the tapped point (zero gesture restarts!)
 * - E-Ink & Fade support BOTH swipe AND left/right edge taps
 * - Zero-overlap status & quick-toggle pills (Dynamic Island shows live status inside the capsule;
 *   non-Island modes place pills safely at the bottom above footer/classic bar)
 */
@Composable
fun PdfManhwaReader(
    filePath: String,
    pageCount: Int,
    currentPageIndex: Int,
    prefs: ReaderPreferences,
    drawingsByLocation: Map<String, List<DrawingStroke>>,
    ocrByPage: Map<Int, OcrPageResult>,
    showTranslatedOverlay: Boolean,
    isProcessingOcrOrTranslation: Boolean,
    processingMessage: String,
    isDockExpanded: Boolean = false,
    isDrawingMode: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    onPageChanged: (Int) -> Unit,
    onPageBitmapReady: (Int, Bitmap) -> Unit,
    onWordSelected: (SelectedWordContext) -> Unit,
    onStrokeCompleted: (String, DrawingStroke) -> Unit,
    onEraseStrokes: (String, Set<String>) -> Unit,
    onToggleTranslateOverlay: () -> Unit,
    onTapPageCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safeIndex = currentPageIndex.coerceIn(0, safePageCount - 1)
    val palette = LocalFolioPalette.current
    val coroutineScope = rememberCoroutineScope()

    val renderMutex = remember(filePath) { Mutex() }
    val pfdAndRenderer = remember(filePath) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pfd to renderer
        }.getOrNull()
    }

    DisposableEffect(filePath) {
        onDispose {
            runCatching { pfdAndRenderer?.second?.close() }
            runCatching { pfdAndRenderer?.first?.close() }
        }
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        if (prefs.pageAnimation == PageAnimationMode.VERTICAL_SCROLL) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
            LaunchedEffect(safeIndex) {
                if (abs(listState.firstVisibleItemIndex - safeIndex) > 1) {
                    listState.scrollToItem(safeIndex)
                }
            }
            LaunchedEffect(listState.firstVisibleItemIndex) {
                onPageChanged(listState.firstVisibleItemIndex)
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = !isDrawingMode && zoomScale <= 1.02f,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(safePageCount) { pageIdx ->
                    PdfInteractivePage(
                        pageIndex = pageIdx,
                        renderer = pfdAndRenderer?.second,
                        renderMutex = renderMutex,
                        ocrResult = ocrByPage[pageIdx],
                        showTranslatedOverlay = showTranslatedOverlay,
                        strokes = drawingsByLocation["pdf-page-$pageIdx"].orEmpty(),
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        isDrawingMode = isDrawingMode,
                        activeTool = activeTool,
                        activeColorArgb = activeColorArgb,
                        activeWidthNorm = activeWidthNorm,
                        onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                        onBitmapReady = { bmp -> onPageBitmapReady(pageIdx, bmp) },
                        onWordSelected = onWordSelected,
                        onStrokeCompleted = { onStrokeCompleted("pdf-page-$pageIdx", it) },
                        onEraseStrokes = { onEraseStrokes("pdf-page-$pageIdx", it) },
                        onTapAtXFraction = { onTapPageCenter() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(initialPage = safeIndex) { safePageCount }
            var eInkFlash by remember { mutableStateOf(false) }
            var lastPdfPage by remember { mutableIntStateOf(safeIndex) }

            LaunchedEffect(safeIndex) {
                if (pagerState.currentPage != safeIndex) {
                    if (prefs.pageAnimation == PageAnimationMode.E_INK) {
                        pagerState.scrollToPage(safeIndex)
                    } else {
                        pagerState.animateScrollToPage(safeIndex)
                    }
                }
            }
            LaunchedEffect(pagerState.currentPage, prefs.pageAnimation) {
                zoomScale = 1f
                panOffset = Offset.Zero
                if (prefs.pageAnimation == PageAnimationMode.E_INK && pagerState.currentPage != lastPdfPage) {
                    eInkFlash = true
                    delay(85)
                    eInkFlash = false
                }
                lastPdfPage = pagerState.currentPage
                onPageChanged(pagerState.currentPage)
            }

            val handlePdfTap: (Float) -> Unit = { xFraction ->
                if (zoomScale > 1.05f) {
                    onTapPageCenter()
                } else {
                    when {
                        xFraction < 0.20f && pagerState.currentPage > 0 -> {
                            val prev = pagerState.currentPage - 1
                            coroutineScope.launch {
                                if (prefs.pageAnimation == PageAnimationMode.E_INK) {
                                    pagerState.scrollToPage(prev)
                                } else {
                                    pagerState.animateScrollToPage(prev)
                                }
                            }
                        }
                        xFraction > 0.80f && pagerState.currentPage < safePageCount - 1 -> {
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

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !isDrawingMode && zoomScale <= 1.02f,
                    modifier = Modifier.fillMaxSize()
                ) { pageIdx ->
                    val pageOffset = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                    val animMod = when (prefs.pageAnimation) {
                        PageAnimationMode.BOOK_CURL -> Modifier
                            .graphicsLayer {
                                val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                                cameraDistance = 14f * density
                                if (pageOffset >= 0f) {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    rotationY = -pageOffset * 48f
                                    alpha = 1f - absOffset * 0.18f
                                }
                            }
                            .drawWithContent {
                                drawContent()
                                val curlStrength = abs(pageOffset).coerceIn(0f, 1f)
                                if (curlStrength > 0.01f) {
                                    val shadowWidth = size.width * 0.16f * curlStrength
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.18f * curlStrength)
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
                                alpha = 0.5f + (1f - absOffset) * 0.5f
                                translationX = -pageOffset * size.width * 0.7f
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

                    Box(
                        modifier = Modifier.fillMaxSize().then(animMod),
                        contentAlignment = Alignment.Center
                    ) {
                        PdfInteractivePage(
                            pageIndex = pageIdx,
                            renderer = pfdAndRenderer?.second,
                            renderMutex = renderMutex,
                            ocrResult = ocrByPage[pageIdx],
                            showTranslatedOverlay = showTranslatedOverlay,
                            strokes = drawingsByLocation["pdf-page-$pageIdx"].orEmpty(),
                            zoomScale = zoomScale,
                            panOffset = panOffset,
                            isDrawingMode = isDrawingMode,
                            activeTool = activeTool,
                            activeColorArgb = activeColorArgb,
                            activeWidthNorm = activeWidthNorm,
                            onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                            onBitmapReady = { bmp -> onPageBitmapReady(pageIdx, bmp) },
                            onWordSelected = onWordSelected,
                            onStrokeCompleted = { onStrokeCompleted("pdf-page-$pageIdx", it) },
                            onEraseStrokes = { onEraseStrokes("pdf-page-$pageIdx", it) },
                            onTapAtXFraction = handlePdfTap,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (eInkFlash) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (palette.isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.65f))
                    )
                }
            }
        }

        // Bottom-anchored conflict-free status & quick controls strip:
        // - Never collides with Dynamic Island at TopCenter or Classic Top Bar!
        // - Automatically slides cleanly above Classic Bottom Bar when expanded!
        val bottomClearance by animateDpAsState(
            targetValue = when {
                prefs.menuStyle == ReaderMenuStyle.CLASSIC_BAR && isDockExpanded -> 144.dp
                else -> 40.dp
            },
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "pdf_pill_bottom_clearance"
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = bottomClearance, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show floating OCR/translation pill ONLY when not already displayed inside Dynamic Island
            AnimatedVisibility(
                visible = isProcessingOcrOrTranslation && prefs.menuStyle != ReaderMenuStyle.DYNAMIC_ISLAND,
                enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 }
            ) {
                Row(
                    modifier = Modifier
                        .shadow(10.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(palette.surface.copy(alpha = 0.96f))
                        .border(1.5.dp, palette.accent, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag("ocr_translation_progress_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.2.dp,
                        color = palette.accent
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = processingMessage.ifBlank { "Procesando OCR y traducción…" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick floating toggles for Zoom Reset & Translated Manhwa toggle (cleanly at bottom!)
            val showQuickControls = !isDrawingMode && (zoomScale > 1.05f || ocrByPage[safeIndex]?.isTranslated == true)
            AnimatedVisibility(
                visible = showQuickControls,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (zoomScale > 1.05f) {
                        Row(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(50))
                                .clip(RoundedCornerShape(50))
                                .background(palette.surface.copy(alpha = 0.95f))
                                .border(1.dp, palette.border, RoundedCornerShape(50))
                                .clickable {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.FitScreen,
                                contentDescription = "Ajustar al ancho",
                                tint = palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Ajustar (${(zoomScale * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                        }
                    }
                    if (ocrByPage[safeIndex]?.isTranslated == true) {
                        Row(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(50))
                                .clip(RoundedCornerShape(50))
                                .background(if (showTranslatedOverlay) palette.accent else palette.surface.copy(alpha = 0.95f))
                                .border(1.dp, palette.accent, RoundedCornerShape(50))
                                .clickable(onClick = onToggleTranslateOverlay)
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                                .testTag("toggle_manhwa_translation_pill"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showTranslatedOverlay) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = "Alternar traducción",
                                tint = if (showTranslatedOverlay) Color.White else palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (showTranslatedOverlay) "Español" else "Original",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (showTranslatedOverlay) Color.White else palette.ink
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfInteractivePage(
    pageIndex: Int,
    renderer: PdfRenderer?,
    renderMutex: Mutex,
    ocrResult: OcrPageResult?,
    showTranslatedOverlay: Boolean,
    strokes: List<DrawingStroke>,
    zoomScale: Float,
    panOffset: Offset,
    isDrawingMode: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    onZoomPanChange: (Float, Offset) -> Unit,
    onBitmapReady: (Bitmap) -> Unit,
    onWordSelected: (SelectedWordContext) -> Unit,
    onStrokeCompleted: (DrawingStroke) -> Unit,
    onEraseStrokes: (Set<String>) -> Unit,
    onTapAtXFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    var bitmap by remember(pageIndex, renderer) { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember(pageIndex) { mutableFloatStateOf(0.707f) }
    var selectedOcrWordBox by remember(pageIndex) { mutableStateOf<com.example.data.model.OcrWordBox?>(null) }

    // Always read latest zoom/pan inside gesture coroutines WITHOUT restarting pointerInput!
    val latestZoom by rememberUpdatedState(zoomScale)
    val latestPan by rememberUpdatedState(panOffset)
    val latestOcr by rememberUpdatedState(ocrResult)
    val latestOnZoomPan by rememberUpdatedState(onZoomPanChange)
    val latestOnTapX by rememberUpdatedState(onTapAtXFraction)

    LaunchedEffect(pageIndex, renderer) {
        if (renderer == null) return@LaunchedEffect
        val rendered = withContext(Dispatchers.IO) {
            renderMutex.withLock {
                runCatching {
                    renderer.openPage(pageIndex).use { page ->
                        // High-resolution Google Drive-quality bitmap so pinch-zoom stays razor-sharp
                        val targetWidth = 1720
                        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                        val rawHeight = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val maxHeightCap = 3600
                        val (finalW, finalH) = if (rawHeight > maxHeightCap) {
                            val ratio = maxHeightCap.toFloat() / rawHeight
                            (targetWidth * ratio).roundToInt().coerceAtLeast(1) to maxHeightCap
                        } else {
                            targetWidth to rawHeight
                        }
                        val bmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }.getOrNull()
            }
        }
        if (rendered != null) {
            aspectRatio = rendered.width.toFloat() / rendered.height.toFloat().coerceAtLeast(1f)
            bitmap = rendered
            onBitmapReady(rendered)
        }
    }

    val bmp = bitmap
    if (bmp == null) {
        Box(
            modifier = modifier.aspectRatio(aspectRatio).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = palette.accent)
        }
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
                translationX = panOffset.x
                translationY = panOffset.y
            }
            .then(
                if (!isDrawingMode) {
                    Modifier
                        // Continuous Google Drive-style multi-touch focal zoom & 1-finger pan (NEVER restarts per frame!)
                        .pointerInput(pageIndex) {
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
                                            val newZoom = (oldZoom * zoomChange).coerceIn(1f, 4.5f)
                                            if (newZoom <= 1.01f) {
                                                latestOnZoomPan(1f, Offset.Zero)
                                            } else {
                                                val center = Offset(size.width / 2f, size.height / 2f)
                                                val focal = centroid - center
                                                val ratio = newZoom / oldZoom
                                                val rawPan = (latestPan - focal) * ratio + focal + panChange
                                                val maxX = size.width * (newZoom - 1f) * 0.5f
                                                val maxY = size.height * (newZoom - 1f) * 0.5f
                                                latestOnZoomPan(
                                                    newZoom,
                                                    Offset(
                                                        rawPan.x.coerceIn(-maxX, maxX),
                                                        rawPan.y.coerceIn(-maxY, maxY)
                                                    )
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
                                            val atHorizontalEdge = abs(nextX - latestPan.x) < 0.5f &&
                                                abs(panChange.x) > abs(panChange.y)
                                            latestOnZoomPan(latestZoom, Offset(nextX, nextY))
                                            if (!atHorizontalEdge) {
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(pageIndex) {
                            detectTapGestures(
                                onDoubleTap = { tapPos ->
                                    if (latestZoom > 1.15f) {
                                        latestOnZoomPan(1f, Offset.Zero)
                                    } else {
                                        val targetZoom = 2.35f
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val focal = tapPos - center
                                        val maxX = size.width * (targetZoom - 1f) * 0.5f
                                        val maxY = size.height * (targetZoom - 1f) * 0.5f
                                        val targetPan = Offset(
                                            (-focal.x * (targetZoom - 1f)).coerceIn(-maxX, maxX),
                                            (-focal.y * (targetZoom - 1f)).coerceIn(-maxY, maxY)
                                        )
                                        latestOnZoomPan(targetZoom, targetPan)
                                    }
                                },
                                onTap = { tapPos ->
                                    selectedOcrWordBox = null
                                    val nx = (tapPos.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    latestOnTapX(nx)
                                },
                                onLongPress = { pos ->
                                    val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val hit = findOcrWordAt(latestOcr, nx, ny)
                                    if (hit != null) {
                                        val (block, word) = hit
                                        selectedOcrWordBox = word
                                        onWordSelected(
                                            SelectedWordContext(
                                                word = word.text,
                                                snippet = block.originalText,
                                                locationKey = "pdf-page-$pageIndex",
                                                blockIndex = block.id,
                                                startChar = 0,
                                                endChar = word.text.length,
                                                anchorXFraction = nx,
                                                anchorYFraction = ny
                                            )
                                        )
                                    }
                                }
                            )
                        }
                } else {
                    Modifier
                }
            )
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val pageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current

        // 1. Crisp rendered PDF / Manhwa page image
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Página PDF ${pageIndex + 1}",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Subtle PC-style OCR word highlight box when user long-presses a word on a Manhwa/PDF
        if (selectedOcrWordBox != null) {
            val box = selectedOcrWordBox!!
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color(0xFF1D5BFF).copy(alpha = 0.28f),
                    topLeft = Offset(box.left * size.width, box.top * size.height),
                    size = Size((box.right - box.left) * size.width, (box.bottom - box.top) * size.height),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }

        // 3. Seamless Borderless Manhwa Speech-Bubble Text Replacement
        if (showTranslatedOverlay && ocrResult != null && ocrResult.isTranslated) {
            for (block in ocrResult.blocks) {
                val translated = block.translatedText?.takeIf { it.isNotBlank() } ?: continue
                val padX = 0.012f
                val padY = 0.008f
                val normLeft = (block.left - padX).coerceAtLeast(0f)
                val normTop = (block.top - padY).coerceAtLeast(0f)
                val normW = ((block.right - block.left) + padX * 2f).coerceIn(0.08f, 1f - normLeft)
                val normH = ((block.bottom - block.top) + padY * 2f).coerceIn(0.032f, 1f - normTop)

                val leftPx = (normLeft * pageWidthPx).roundToInt()
                val topPx = (normTop * pageHeightPx).roundToInt()
                val widthPx = (normW * pageWidthPx).roundToInt()
                val heightPx = (normH * pageHeightPx).roundToInt()

                val boxWidthDp = with(density) { widthPx.toDp() }
                val boxHeightDp = with(density) { heightPx.toDp() }

                val areaDp2 = (boxWidthDp.value * boxHeightDp.value).coerceAtLeast(360f)
                val charCount = translated.length.coerceAtLeast(4)
                val fittedSp = kotlin.math.sqrt(areaDp2 / (charCount * 1.15f))
                    .coerceIn(8.5f, 15.5f)

                val bgColor = Color(block.bgColorArgb.toInt())
                val inkColor = Color(block.inkColorArgb.toInt())

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx, topPx) }
                        .size(width = boxWidthDp, height = boxHeightDp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = translated,
                        color = inkColor,
                        fontSize = fittedSp.sp,
                        lineHeight = (fittedSp * 1.14f).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4. Zoom-synchronized persistent Drawing Canvas Overlay
        DrawingCanvasOverlay(
            strokes = strokes,
            isInteractive = isDrawingMode,
            activeTool = activeTool,
            activeColorArgb = activeColorArgb,
            activeWidthNorm = activeWidthNorm,
            onStrokeCompleted = onStrokeCompleted,
            onEraseStrokeIds = onEraseStrokes,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun findOcrWordAt(
    ocrPage: OcrPageResult?,
    nx: Float,
    ny: Float
): Pair<com.example.data.model.OcrBlockBox, com.example.data.model.OcrWordBox>? {
    if (ocrPage == null) return null
    val pad = 0.018f
    for (block in ocrPage.blocks) {
        for (word in block.words) {
            if (nx in (word.left - pad)..(word.right + pad) &&
                ny in (word.top - pad)..(word.bottom + pad)
            ) {
                return block to word
            }
        }
    }
    var best: Pair<com.example.data.model.OcrBlockBox, com.example.data.model.OcrWordBox>? = null
    var bestDist = 0.085f
    for (block in ocrPage.blocks) {
        for (word in block.words) {
            val cx = (word.left + word.right) * 0.5f
            val cy = (word.top + word.bottom) * 0.5f
            val dist = kotlin.math.hypot(cx - nx, cy - ny)
            if (dist < bestDist) {
                bestDist = dist
                best = block to word
            }
        }
    }
    return best
}
