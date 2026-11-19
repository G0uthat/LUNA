package com.example.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.PageAnimationMode
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedDocument
import com.example.data.model.ParsedSlide
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.StandardPageFormat
import com.example.ui.components.DrawingCanvasOverlay
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

private object SlideBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(18) {}

    suspend fun load(path: String): Bitmap? {
        cache.get(path)?.takeIf { !it.isRecycled }?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                if (!file.exists()) return@runCatching null
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                val maxDim = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
                var sample = 1
                while (maxDim / sample > 1600) sample *= 2
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, decodeOpts)?.also { bmp ->
                    cache.put(path, bmp)
                }
            }.getOrNull()
        }
    }
}

private data class StandardDocumentSheet(
    val sheetIndex: Int,
    val chapterIndex: Int,
    val locationKey: String,
    val headerLabel: String,
    val blocks: List<ParsedBlock> = emptyList(),
    val slide: ParsedSlide? = null
)

/**
 * Universal Standard Physical Page & PowerPoint Slide Reader:
 * - Renders ANY document (PPTX, DOCX, EPUB, TXT, FB2) at true physical sheet dimensions
 *   (Auto file size, A4, Carta, Oficio, A3, A5, Slide 16:9, Slide 4:3) instead of reflowing to phone width.
 * - Supports silky GPU focal pinch-to-zoom (0.85x..4.5x), double-tap zoom, and 2D pan.
 * - Anchors freehand drawings & signatures directly to the exact physical sheet coordinates so
 *   users can zoom straight onto a signature line, lock pan, sign cleanly, and export to PDF.
 */
@Composable
fun StandardPageDocumentReader(
    parsed: ParsedDocument,
    currentUnitIndex: Int,
    jumpToUnitTrigger: Int,
    prefs: ReaderPreferences,
    drawingsByLocation: Map<String, List<DrawingStroke>>,
    isDrawingMode: Boolean,
    isPanZoomWhileDrawing: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    onActiveSheetChanged: (sheetIndex: Int, totalSheets: Int, locationKey: String, chapterIndex: Int) -> Unit,
    onSelectStandardFormat: (StandardPageFormat) -> Unit,
    onToggleStandardView: () -> Unit,
    onStrokeCompleted: (String, DrawingStroke) -> Unit,
    onEraseStrokes: (String, Set<String>) -> Unit,
    onTapPageCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val coroutineScope = rememberCoroutineScope()
    val isPptx = parsed.format == DocumentFormat.PPTX

    val (resolvedWidthPt, resolvedHeightPt) = remember(
        prefs.standardPageFormat,
        parsed.nativePageWidthPt,
        parsed.nativePageHeightPt
    ) {
        prefs.standardPageFormat.resolveSizePt(parsed.nativePageWidthPt, parsed.nativePageHeightPt)
    }
    val sheetAspectRatio = (resolvedWidthPt / resolvedHeightPt.coerceAtLeast(1f)).coerceIn(0.45f, 2.25f)
    val activeFormatLabel = remember(prefs.standardPageFormat, parsed.detectedFormatLabel) {
        if (prefs.standardPageFormat == StandardPageFormat.AUTO) {
            parsed.detectedFormatLabel
        } else {
            prefs.standardPageFormat.shortLabel
        }
    }

    val sheets = remember(parsed, resolvedWidthPt, resolvedHeightPt, isPptx) {
        if (isPptx && parsed.slides.isNotEmpty()) {
            parsed.slides.mapIndexed { idx, slide ->
                StandardDocumentSheet(
                    sheetIndex = idx,
                    chapterIndex = idx,
                    locationKey = "pptx-slide-$idx",
                    headerLabel = "Diapositiva ${idx + 1} / ${parsed.slides.size}",
                    slide = slide
                )
            }
        } else {
            paginateDocumentForPhysicalSheet(
                parsed = parsed,
                pageWidthPt = resolvedWidthPt,
                pageHeightPt = resolvedHeightPt
            )
        }
    }
    val safeSheetCount = sheets.size.coerceAtLeast(1)

    val initialSheetIdx = remember(sheets, currentUnitIndex) {
        sheets.indexOfFirst { it.chapterIndex == currentUnitIndex }
            .takeIf { it >= 0 }
            ?: currentUnitIndex.coerceIn(0, safeSheetCount - 1)
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showSpeakerNotes by remember { mutableStateOf(false) }

    val canInteractWithCanvas = isDrawingMode && !isPanZoomWhileDrawing
    val canPanOrZoom = !canInteractWithCanvas

    val hasDynamicIsland = prefs.menuStyle == ReaderMenuStyle.DYNAMIC_ISLAND
    val topBarPaddingDp = if (hasDynamicIsland) (48.dp * prefs.dockScale.coerceIn(0.88f, 1.25f)) else 12.dp
    val deskBackground = remember(palette.background, palette.surfaceAlt, palette.isDark) {
        if (palette.isDark) {
            lerp(palette.background, Color.Black, 0.35f)
        } else {
            lerp(palette.surfaceAlt, Color(0xFFDCE2EC), 0.42f)
        }
    }

    var activeSheetIndex by remember { mutableStateOf(initialSheetIdx) }
    val activeSheet = sheets.getOrNull(activeSheetIndex.coerceIn(0, safeSheetCount - 1))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(deskBackground)
            .testTag("standard_page_reader_root")
    ) {
        if (prefs.pageAnimation == PageAnimationMode.VERTICAL_SCROLL) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialSheetIdx)
            LaunchedEffect(jumpToUnitTrigger) {
                if (jumpToUnitTrigger > 0) {
                    val targetSheet = sheets.indexOfFirst { it.chapterIndex == currentUnitIndex }
                        .takeIf { it >= 0 } ?: currentUnitIndex.coerceIn(0, safeSheetCount - 1)
                    listState.scrollToItem(targetSheet)
                }
            }
            LaunchedEffect(listState.firstVisibleItemIndex, sheets) {
                val idx = listState.firstVisibleItemIndex.coerceIn(0, safeSheetCount - 1)
                activeSheetIndex = idx
                val sheet = sheets.getOrNull(idx) ?: return@LaunchedEffect
                onActiveSheetChanged(idx, safeSheetCount, sheet.locationKey, sheet.chapterIndex)
            }

            LazyColumn(
                state = listState,
                userScrollEnabled = !canInteractWithCanvas && zoomScale <= 1.02f,
                contentPadding = PaddingValues(
                    top = topBarPaddingDp + 54.dp,
                    bottom = if (isDrawingMode) 156.dp else 72.dp,
                    start = 14.dp,
                    end = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                items(
                    count = safeSheetCount,
                    key = { idx -> sheets[idx].locationKey },
                    contentType = { if (isPptx) "pptx_slide" else "std_sheet" }
                ) { idx ->
                    val sheet = sheets[idx]
                    StandardInteractiveSheetFrame(
                        sheet = sheet,
                        isPptx = isPptx,
                        aspectRatio = sheetAspectRatio,
                        pageWidthPt = resolvedWidthPt,
                        pageHeightPt = resolvedHeightPt,
                        formatBadge = activeFormatLabel,
                        prefs = prefs,
                        strokes = drawingsByLocation[sheet.locationKey].orEmpty(),
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        canDraw = canInteractWithCanvas,
                        canPanZoom = canPanOrZoom,
                        activeTool = activeTool,
                        activeColorArgb = activeColorArgb,
                        activeWidthNorm = activeWidthNorm,
                        onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                        onStrokeCompleted = { onStrokeCompleted(sheet.locationKey, it) },
                        onEraseStrokes = { onEraseStrokes(sheet.locationKey, it) },
                        onTapAtXFraction = { onTapPageCenter() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(initialPage = initialSheetIdx) { safeSheetCount }

            LaunchedEffect(jumpToUnitTrigger) {
                if (jumpToUnitTrigger > 0) {
                    val targetSheet = sheets.indexOfFirst { it.chapterIndex == currentUnitIndex }
                        .takeIf { it >= 0 } ?: currentUnitIndex.coerceIn(0, safeSheetCount - 1)
                    pagerState.animateScrollToPage(targetSheet)
                }
            }

            LaunchedEffect(pagerState.currentPage, sheets) {
                zoomScale = 1f
                panOffset = Offset.Zero
                val idx = pagerState.currentPage.coerceIn(0, safeSheetCount - 1)
                activeSheetIndex = idx
                val sheet = sheets.getOrNull(idx) ?: return@LaunchedEffect
                onActiveSheetChanged(idx, safeSheetCount, sheet.locationKey, sheet.chapterIndex)
            }

            val handleTapX: (Float) -> Unit = { xFrac ->
                if (zoomScale > 1.05f || !prefs.tapZonesEnabled || isDrawingMode) {
                    onTapPageCenter()
                } else {
                    when {
                        xFrac < 0.24f && pagerState.currentPage > 0 -> {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        xFrac > 0.76f && pagerState.currentPage < safeSheetCount - 1 -> {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        else -> onTapPageCenter()
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !canInteractWithCanvas && zoomScale <= 1.02f,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(
                        top = topBarPaddingDp + 44.dp,
                        bottom = if (isDrawingMode) 146.dp else 44.dp
                    )
            ) { idx ->
                val sheet = sheets[idx]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StandardInteractiveSheetFrame(
                        sheet = sheet,
                        isPptx = isPptx,
                        aspectRatio = sheetAspectRatio,
                        pageWidthPt = resolvedWidthPt,
                        pageHeightPt = resolvedHeightPt,
                        formatBadge = activeFormatLabel,
                        prefs = prefs,
                        strokes = drawingsByLocation[sheet.locationKey].orEmpty(),
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        canDraw = canInteractWithCanvas,
                        canPanZoom = canPanOrZoom,
                        activeTool = activeTool,
                        activeColorArgb = activeColorArgb,
                        activeWidthNorm = activeWidthNorm,
                        onZoomPanChange = { z, p -> zoomScale = z; panOffset = p },
                        onStrokeCompleted = { onStrokeCompleted(sheet.locationKey, it) },
                        onEraseStrokes = { onEraseStrokes(sheet.locationKey, it) },
                        onTapAtXFraction = handleTapX,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Top Floating Format Selector Strip (Auto, A4, Carta, Oficio, A3, A5, 16:9, 4:3 + Adapted switch)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = topBarPaddingDp, start = 12.dp, end = 12.dp)
                .shadow(8.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.22f))
                .clip(RoundedCornerShape(50))
                .background(palette.surface.copy(alpha = 0.96f))
                .border(1.dp, palette.border, RoundedCornerShape(50))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag("standard_page_format_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accentSoft)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isPptx) Icons.Rounded.Slideshow else Icons.Rounded.Description,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isPptx) "PPTX · $activeFormatLabel" else "Hoja · $activeFormatLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accent
                )
            }

            val selectableFormats = remember(isPptx) {
                if (isPptx) {
                    listOf(
                        StandardPageFormat.AUTO,
                        StandardPageFormat.SLIDE_16_9,
                        StandardPageFormat.SLIDE_4_3,
                        StandardPageFormat.A4,
                        StandardPageFormat.LETTER
                    )
                } else {
                    listOf(
                        StandardPageFormat.AUTO,
                        StandardPageFormat.A4,
                        StandardPageFormat.LETTER,
                        StandardPageFormat.LEGAL,
                        StandardPageFormat.A3,
                        StandardPageFormat.A5
                    )
                }
            }

            for (fmt in selectableFormats) {
                val isSelected = prefs.standardPageFormat == fmt
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) palette.accent else Color.Transparent)
                        .clickable {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            onSelectStandardFormat(fmt)
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("std_format_chip_${fmt.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fmt.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else palette.inkMuted
                    )
                }
            }

            // Quick 1-tap toggle back to Adapted Text mode
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, RoundedCornerShape(50))
                    .clickable(onClick = onToggleStandardView)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("std_bar_switch_adapted"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ViewAgenda,
                    contentDescription = "Cambiar a vista adaptada",
                    tint = palette.ink,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Adaptar",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink
                )
            }
        }

        // Speaker Notes Pill (PPTX only when slide has notes) & Zoom Reset Pill
        val currentSlideNotes = activeSheet?.slide?.notes?.takeIf { it.isNotBlank() }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    bottom = if (isDrawingMode) 152.dp else 40.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = showSpeakerNotes && !currentSlideNotes.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.surface.copy(alpha = 0.97f))
                        .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "NOTAS DEL ORADOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accent,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = currentSlideNotes.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.ink,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (zoomScale > 1.05f) {
                    Row(
                        modifier = Modifier
                            .shadow(6.dp, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(palette.surface.copy(alpha = 0.96f))
                            .border(1.dp, palette.border, RoundedCornerShape(50))
                            .clickable {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("std_zoom_reset_pill"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FitScreen,
                            contentDescription = "Restablecer zoom",
                            tint = palette.accent,
                            modifier = Modifier.size(15.dp)
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

                if (!currentSlideNotes.isNullOrBlank() && !isDrawingMode) {
                    Row(
                        modifier = Modifier
                            .shadow(6.dp, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(if (showSpeakerNotes) palette.accent else palette.surface.copy(alpha = 0.96f))
                            .border(1.dp, if (showSpeakerNotes) palette.accent else palette.border, RoundedCornerShape(50))
                            .clickable { showSpeakerNotes = !showSpeakerNotes }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("pptx_speaker_notes_pill"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notes,
                            contentDescription = "Notas del orador",
                            tint = if (showSpeakerNotes) Color.White else palette.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (showSpeakerNotes) "Ocultar notas" else "Notas",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (showSpeakerNotes) Color.White else palette.ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardInteractiveSheetFrame(
    sheet: StandardDocumentSheet,
    isPptx: Boolean,
    aspectRatio: Float,
    pageWidthPt: Float,
    pageHeightPt: Float,
    formatBadge: String,
    prefs: ReaderPreferences,
    strokes: List<DrawingStroke>,
    zoomScale: Float,
    panOffset: Offset,
    canDraw: Boolean,
    canPanZoom: Boolean,
    activeTool: DrawingTool,
    activeColorArgb: Long,
    activeWidthNorm: Float,
    onZoomPanChange: (Float, Offset) -> Unit,
    onStrokeCompleted: (DrawingStroke) -> Unit,
    onEraseStrokes: (Set<String>) -> Unit,
    onTapAtXFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val latestZoom by rememberUpdatedState(zoomScale)
    val latestPan by rememberUpdatedState(panOffset)
    val latestOnZoomPan by rememberUpdatedState(onZoomPanChange)
    val latestOnTapX by rememberUpdatedState(onTapAtXFraction)

    // Physical paper surface: crisp white paper for light/pastel themes, low-glare studio paper for dark themes
    val paperBg = if (palette.isDark) Color(0xFF1A202D) else Color(0xFFFFFFFF)
    val paperInk = if (palette.isDark) Color(0xFFEDF2FA) else Color(0xFF141B29)
    val paperMuted = if (palette.isDark) Color(0xFF9AA7BD) else Color(0xFF5B677C)
    val paperBorder = if (palette.isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFCFD8E6)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val availW = maxWidth
        val availH = maxHeight
        // Compute exact fitted physical sheet dimensions preserving true aspectRatio without clipping
        val useHeightBound = availH.value.isFinite() && availH.value > 0f && (availW.value / availH.value) > aspectRatio
        val sheetSizeMod = if (useHeightBound) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
        }

        BoxWithConstraints(
            modifier = sheetSizeMod
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
                .shadow(
                    elevation = if (palette.isDark) 14.dp else 10.dp,
                    shape = RoundedCornerShape(4.dp),
                    ambientColor = Color.Black.copy(alpha = 0.24f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
                .clip(RoundedCornerShape(4.dp))
                .background(paperBg)
                .border(1.dp, paperBorder, RoundedCornerShape(4.dp))
                .then(
                    if (canPanZoom) {
                        Modifier
                            .pointerInput(sheet.locationKey, canPanZoom) {
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
                                                    val maxX = size.width * (newZoom - 1f) * 0.55f
                                                    val maxY = size.height * (newZoom - 1f) * 0.55f
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
                                                val maxX = size.width * (latestZoom - 1f) * 0.55f
                                                val maxY = size.height * (latestZoom - 1f) * 0.55f
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
                            .pointerInput(sheet.locationKey, canPanZoom) {
                                detectTapGestures(
                                    onDoubleTap = { tapPos ->
                                        if (latestZoom > 1.15f) {
                                            latestOnZoomPan(1f, Offset.Zero)
                                        } else {
                                            val targetZoom = 2.4f
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            val focal = tapPos - center
                                            val maxX = size.width * (targetZoom - 1f) * 0.55f
                                            val maxY = size.height * (targetZoom - 1f) * 0.55f
                                            latestOnZoomPan(
                                                targetZoom,
                                                Offset(
                                                    (-focal.x * (targetZoom - 1f)).coerceIn(-maxX, maxX),
                                                    (-focal.y * (targetZoom - 1f)).coerceIn(-maxY, maxY)
                                                )
                                            )
                                        }
                                    },
                                    onTap = { tapPos ->
                                        val nx = (tapPos.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        latestOnTapX(nx)
                                    }
                                )
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            val sheetW = maxWidth.value.coerceAtLeast(120f)
            val sheetH = maxHeight.value.coerceAtLeast(120f)
            // Scale factor translating physical PDF points (e.g. 595pt A4 or 842pt A3) to rendered dp
            val ptScale = (sheetW / pageWidthPt.coerceAtLeast(240f)).coerceIn(0.28f, 1.35f)
            val fontFamily = prefs.font.toFontFamily()

            if (isPptx && sheet.slide != null) {
                PptxSlideSurface(
                    slide = sheet.slide,
                    headerLabel = sheet.headerLabel,
                    formatBadge = formatBadge,
                    ptScale = ptScale,
                    isLandscape = sheetW >= sheetH,
                    paperInk = paperInk,
                    paperMuted = paperMuted,
                    accent = palette.accent,
                    fontFamily = fontFamily
                )
            } else {
                PhysicalDocumentSheetSurface(
                    sheet = sheet,
                    formatBadge = formatBadge,
                    ptScale = ptScale,
                    paperInk = paperInk,
                    paperMuted = paperMuted,
                    paperBorder = paperBorder,
                    accent = palette.accent,
                    fontFamily = fontFamily,
                    textAlign = if (prefs.textAlign == com.example.data.model.TextAlignOption.JUSTIFY) {
                        TextAlign.Justify
                    } else {
                        TextAlign.Start
                    }
                )
            }

            // Pixel-locked persistent Drawing / Signature Canvas Layer directly over the physical sheet!
            DrawingCanvasOverlay(
                strokes = strokes,
                isInteractive = canDraw,
                activeTool = activeTool,
                activeColorArgb = activeColorArgb,
                activeWidthNorm = activeWidthNorm,
                onStrokeCompleted = onStrokeCompleted,
                onEraseStrokeIds = onEraseStrokes,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PptxSlideSurface(
    slide: ParsedSlide,
    headerLabel: String,
    formatBadge: String,
    ptScale: Float,
    isLandscape: Boolean,
    paperInk: Color,
    paperMuted: Color,
    accent: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily
) {
    val padX = (44f * ptScale).coerceIn(12f, 32f).dp
    val padY = (32f * ptScale).coerceIn(10f, 24f).dp
    val titleSp = (26f * ptScale).coerceIn(11f, 22f).sp
    val subtitleSp = (16f * ptScale).coerceIn(8.5f, 14f).sp
    val bodySp = (14f * ptScale).coerceIn(7.5f, 13f).sp
    val metaSp = (10f * ptScale).coerceIn(6.5f, 10f).sp

    val firstImgPath = slide.imagePaths.firstOrNull()
    val slideBitmap by produceState<Bitmap?>(initialValue = null, key1 = firstImgPath) {
        value = firstImgPath?.let { SlideBitmapCache.load(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Subtle PowerPoint top accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((5f * ptScale).coerceIn(2.5f, 5f).dp)
                .background(accent)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = padX, vertical = padY),
            verticalArrangement = Arrangement.spacedBy((8f * ptScale).coerceIn(3f, 8f).dp)
        ) {
            Text(
                text = slide.title,
                fontFamily = fontFamily,
                fontSize = titleSp,
                lineHeight = (titleSp.value * 1.18f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = paperInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (slide.subtitle.isNotBlank()) {
                Text(
                    text = slide.subtitle,
                    fontFamily = fontFamily,
                    fontSize = subtitleSp,
                    lineHeight = (subtitleSp.value * 1.24f).sp,
                    fontWeight = FontWeight.Medium,
                    color = paperMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (slideBitmap != null && isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy((16f * ptScale).coerceIn(6f, 14f).dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (slide.bodyBlocks.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy((6f * ptScale).coerceIn(2.5f, 6f).dp)
                        ) {
                            SlideBodyBlocks(
                                blocks = slide.bodyBlocks,
                                bodySp = bodySp,
                                ink = paperInk,
                                accent = accent,
                                fontFamily = fontFamily
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = slideBitmap!!.asImageBitmap(),
                            contentDescription = "Imagen de diapositiva",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy((6f * ptScale).coerceIn(2.5f, 6f).dp)
                ) {
                    SlideBodyBlocks(
                        blocks = slide.bodyBlocks,
                        bodySp = bodySp,
                        ink = paperInk,
                        accent = accent,
                        fontFamily = fontFamily
                    )
                    if (slideBitmap != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = slideBitmap!!.asImageBitmap(),
                                contentDescription = "Imagen de diapositiva",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Crisp PowerPoint slide footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatBadge,
                    fontSize = metaSp,
                    fontWeight = FontWeight.SemiBold,
                    color = paperMuted.copy(alpha = 0.72f)
                )
                Text(
                    text = headerLabel,
                    fontSize = metaSp,
                    fontWeight = FontWeight.Bold,
                    color = paperMuted.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SlideBodyBlocks(
    blocks: List<ParsedBlock>,
    bodySp: androidx.compose.ui.unit.TextUnit,
    ink: Color,
    accent: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily
) {
    for (block in blocks.take(10)) {
        if (block.type == BlockType.LIST_ITEM) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = (bodySp.value * 0.42f).dp)
                        .size((bodySp.value * 0.34f).coerceIn(3f, 5f).dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = block.text,
                    fontFamily = fontFamily,
                    fontSize = bodySp,
                    lineHeight = (bodySp.value * 1.28f).sp,
                    color = ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = block.text,
                fontFamily = fontFamily,
                fontSize = bodySp,
                lineHeight = (bodySp.value * 1.28f).sp,
                color = ink,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PhysicalDocumentSheetSurface(
    sheet: StandardDocumentSheet,
    formatBadge: String,
    ptScale: Float,
    paperInk: Color,
    paperMuted: Color,
    paperBorder: Color,
    accent: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    textAlign: TextAlign
) {
    val marginX = (52f * ptScale).coerceIn(14f, 40f).dp
    val marginY = (46f * ptScale).coerceIn(12f, 36f).dp
    val h1Sp = (19f * ptScale).coerceIn(9.5f, 18f).sp
    val h2Sp = (15f * ptScale).coerceIn(8.5f, 15f).sp
    val bodySp = (11.5f * ptScale).coerceIn(6.5f, 12.5f).sp
    val metaSp = (8.5f * ptScale).coerceIn(6f, 9.5f).sp
    val blockGap = (9f * ptScale).coerceIn(3.5f, 9f).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = marginX, vertical = marginY)
    ) {
        // Subtle top physical paper header (A4 / Carta / A3 indicator)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatBadge,
                fontSize = metaSp,
                fontWeight = FontWeight.Bold,
                color = paperMuted.copy(alpha = 0.65f)
            )
            Text(
                text = sheet.headerLabel,
                fontSize = metaSp,
                fontWeight = FontWeight.SemiBold,
                color = paperMuted.copy(alpha = 0.65f)
            )
        }
        Spacer(Modifier.height((6f * ptScale).coerceIn(3f, 7f).dp))
        HorizontalDivider(thickness = 0.6.dp, color = paperBorder.copy(alpha = 0.7f))
        Spacer(Modifier.height((10f * ptScale).coerceIn(5f, 12f).dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(blockGap)
        ) {
            for (block in sheet.blocks) {
                when (block.type) {
                    BlockType.HEADING_1 -> {
                        Text(
                            text = block.text,
                            fontFamily = fontFamily,
                            fontSize = h1Sp,
                            lineHeight = (h1Sp.value * 1.24f).sp,
                            fontWeight = FontWeight.Bold,
                            color = paperInk
                        )
                    }
                    BlockType.HEADING_2 -> {
                        Text(
                            text = block.text,
                            fontFamily = fontFamily,
                            fontSize = h2Sp,
                            lineHeight = (h2Sp.value * 1.26f).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = paperInk
                        )
                    }
                    BlockType.QUOTE -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .heightIn(min = 16.dp)
                                    .background(accent.copy(alpha = 0.75f))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = block.text,
                                fontFamily = fontFamily,
                                fontSize = bodySp,
                                lineHeight = (bodySp.value * 1.38f).sp,
                                fontStyle = FontStyle.Italic,
                                color = paperMuted
                            )
                        }
                    }
                    BlockType.LIST_ITEM -> {
                        Text(
                            text = "•  ${block.text}",
                            fontFamily = fontFamily,
                            fontSize = bodySp,
                            lineHeight = (bodySp.value * 1.38f).sp,
                            color = paperInk,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    BlockType.PARAGRAPH -> {
                        Text(
                            text = block.text,
                            fontFamily = fontFamily,
                            fontSize = bodySp,
                            lineHeight = (bodySp.value * 1.40f).sp,
                            textAlign = textAlign,
                            color = paperInk
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deterministically paginates any text document (DOCX, EPUB, TXT, FB2) into physical sheets
 * matching real paper dimensions (A4, Carta, Oficio, A3, A5, or native file size).
 */
private fun paginateDocumentForPhysicalSheet(
    parsed: ParsedDocument,
    pageWidthPt: Float,
    pageHeightPt: Float
): List<StandardDocumentSheet> {
    val usableW = (pageWidthPt - 104f).coerceAtLeast(220f)
    val usableH = (pageHeightPt - 118f).coerceAtLeast(260f)
    val charsPerLine = (usableW / 5.75f).toInt().coerceIn(38, 128)
    val maxLinesPerSheet = (usableH / 16.2f).toInt().coerceIn(18, 72)

    val result = mutableListOf<StandardDocumentSheet>()
    var currentBlocks = mutableListOf<ParsedBlock>()
    var currentChapterIdx = 0
    var usedLines = 0

    fun flushSheet() {
        if (currentBlocks.isEmpty()) return
        val idx = result.size
        result.add(
            StandardDocumentSheet(
                sheetIndex = idx,
                chapterIndex = currentChapterIdx,
                locationKey = "std-page-$idx",
                headerLabel = "Hoja ${idx + 1}",
                blocks = currentBlocks.toList()
            )
        )
        currentBlocks = mutableListOf()
        usedLines = 0
    }

    for (chapter in parsed.chapters) {
        if (currentBlocks.isNotEmpty() && usedLines > maxLinesPerSheet * 0.55f) {
            flushSheet()
        }
        currentChapterIdx = chapter.index
        for (block in chapter.blocks) {
            val weightMult = when (block.type) {
                BlockType.HEADING_1 -> 1.9f
                BlockType.HEADING_2 -> 1.45f
                else -> 1f
            }
            val blockLines = ceil(block.text.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
            val weighted = (blockLines * weightMult).toInt().coerceAtLeast(1) + 1

            if (usedLines + weighted > maxLinesPerSheet && currentBlocks.isNotEmpty()) {
                flushSheet()
                currentChapterIdx = chapter.index
            }

            if (weighted > maxLinesPerSheet) {
                // Split very long paragraphs neatly across consecutive physical sheets
                val maxChars = (charsPerLine * (maxLinesPerSheet - 3)).coerceAtLeast(240)
                var offset = 0
                while (offset < block.text.length) {
                    val remaining = block.text.length - offset
                    val end = if (remaining <= maxChars) {
                        block.text.length
                    } else {
                        val candidate = offset + maxChars
                        val spaceBack = block.text.lastIndexOf(' ', candidate)
                        if (spaceBack > offset + maxChars / 2) spaceBack else candidate
                    }
                    val slice = block.text.substring(offset, end).trim()
                    if (slice.isNotEmpty()) {
                        currentBlocks.add(block.copy(text = slice))
                        flushSheet()
                        currentChapterIdx = chapter.index
                    }
                    offset = end
                }
            } else {
                currentBlocks.add(block)
                usedLines += weighted
            }
        }
    }
    flushSheet()

    if (result.isEmpty()) {
        result.add(
            StandardDocumentSheet(
                sheetIndex = 0,
                chapterIndex = 0,
                locationKey = "std-page-0",
                headerLabel = "Hoja 1",
                blocks = listOf(ParsedBlock(0, BlockType.PARAGRAPH, parsed.title))
            )
        )
    }

    val total = result.size
    return result.map { it.copy(headerLabel = "Hoja ${it.sheetIndex + 1} / $total") }
}
