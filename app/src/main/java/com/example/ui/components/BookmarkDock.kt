package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.displayCutoutPadding
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
import androidx.compose.material.icons.rounded.AutoStories
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookmarkDockSide
import com.example.data.model.MediaPlaybackInfo
import com.example.data.model.ReaderMenuStyle
import com.example.engine.DynamicIslandEvent
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
    mediaInfo: MediaPlaybackInfo = MediaPlaybackInfo(),
    activeIslandEvent: DynamicIslandEvent? = null,
    queuedIslandCount: Int = 0,
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
    onMediaPlayPause: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    onMediaPrevious: () -> Unit = {},
    onMediaSeek: (Long) -> Unit = {},
    onOpenNotificationAccess: () -> Unit = {},
    onTapIslandEvent: (DynamicIslandEvent) -> Unit = {},
    onDismissIslandEvent: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isDrawingMode) return

    when (menuStyle) {
        ReaderMenuStyle.BOOKMARK_DOCK -> Box(modifier = modifier.fillMaxSize()) {
            DraggableSoftBookmarkDock(
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
                modifier = Modifier.fillMaxSize()
            )
            // Route centralized critical states & allowed app notifications cleanly via Dynamic Island
            DynamicIslandEventBanner(
                event = if (!isExpanded) activeIslandEvent else null,
                queuedCount = queuedIslandCount,
                onTapEvent = onTapIslandEvent,
                onDismiss = onDismissIslandEvent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(top = 6.dp, start = 12.dp, end = 12.dp)
            )
        }

        ReaderMenuStyle.DYNAMIC_ISLAND -> DynamicIslandReaderDock(
            dockScale = dockScale,
            brightnessValue = brightnessValue,
            isExpanded = isExpanded,
            isBookmarkedHere = isBookmarkedHere,
            isPdfDocument = isPdfDocument,
            isPageTranslated = isPageTranslated,
            isProcessingStatus = isProcessingStatus,
            processingStatusText = processingStatusText,
            mediaInfo = mediaInfo,
            activeIslandEvent = activeIslandEvent,
            queuedIslandCount = queuedIslandCount,
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
            onMediaPlayPause = onMediaPlayPause,
            onMediaNext = onMediaNext,
            onMediaPrevious = onMediaPrevious,
            onMediaSeek = onMediaSeek,
            onOpenNotificationAccess = onOpenNotificationAccess,
            onTapIslandEvent = onTapIslandEvent,
            onDismissIslandEvent = onDismissIslandEvent,
            modifier = modifier
        )

        ReaderMenuStyle.CLASSIC_BAR -> Box(modifier = modifier.fillMaxSize()) {
            ClassicKindleOverlayBars(
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
                modifier = Modifier.fillMaxSize()
            )
            DynamicIslandEventBanner(
                event = if (!isExpanded) activeIslandEvent else null,
                queuedCount = queuedIslandCount,
                onTapEvent = onTapIslandEvent,
                onDismiss = onDismissIslandEvent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(top = 6.dp, start = 12.dp, end = 12.dp)
            )
        }
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

        val coroutineScope = rememberCoroutineScope()
        var isDragging by remember { mutableStateOf(false) }

        // Persistent Animatables — NEVER re-keyed on side/dockYFraction so pointerInput NEVER holds stale refs!
        val animX = remember { Animatable(if (isRight) maxW - tabTouchW else 0f) }
        val animY = remember {
            Animatable((dockYFraction * maxH - tabTouchH / 2f).coerceIn(maxH * 0.10f, maxH * 0.86f))
        }
        var hasInitializedPosition by remember { mutableStateOf(false) }

        val latestMaxW by rememberUpdatedState(maxW)
        val latestMaxH by rememberUpdatedState(maxH)
        val latestTabW by rememberUpdatedState(tabTouchW)
        val latestTabH by rememberUpdatedState(tabTouchH)
        val latestOnRepositioned by rememberUpdatedState(onDockRepositioned)

        // Sync smoothly when layout size or saved preferences change while not actively dragging
        LaunchedEffect(side, dockYFraction, maxW, maxH, tabTouchW, tabTouchH) {
            if (!isDragging) {
                val targetX = if (side == BookmarkDockSide.RIGHT) {
                    (maxW - tabTouchW).coerceAtLeast(0f)
                } else {
                    0f
                }
                val targetY = (dockYFraction * maxH - tabTouchH / 2f)
                    .coerceIn(maxH * 0.10f, (maxH - tabTouchH - maxH * 0.08f).coerceAtLeast(maxH * 0.10f))
                if (!hasInitializedPosition) {
                    animX.snapTo(targetX)
                    animY.snapTo(targetY)
                    hasInitializedPosition = true
                } else {
                    launch {
                        animX.animateTo(
                            targetX,
                            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                    launch {
                        animY.animateTo(
                            targetY,
                            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                }
            }
        }

        // Tactile lift scale while dragging + subtle spring pop when page bookmark state changes
        val dragLiftScale by animateFloatAsState(
            targetValue = if (isDragging) 1.12f else 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
            label = "bookmark_drag_lift"
        )
        val bookmarkIconPop by animateFloatAsState(
            targetValue = if (isBookmarkedHere) 1.12f else 1f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
            label = "bookmark_icon_pop"
        )

        val currentYFrac = ((animY.value + tabTouchH / 2f) / maxH).coerceIn(0.15f, 0.85f)
        val pillOrigin = TransformOrigin(if (isRight) 1f else 0f, 0.5f)
        val drawerOrigin = TransformOrigin(if (isRight) 1f else 0f, currentYFrac)

        // Gentle 15% dim + subtle frosted veil matching ProfileDropdownCapsuleOverlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .background(palette.background.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Soft Organic Draggable Bookmark Pill Tab (1:1 instant finger tracking + Dynamic Island Zoom-In/Zoom-Out)
        AnimatedVisibility(
            visible = !isExpanded,
            enter = scaleIn(
                initialScale = 0.74f,
                transformOrigin = pillOrigin,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(160)),
            exit = scaleOut(
                targetScale = 0.74f,
                transformOrigin = pillOrigin,
                animationSpec = tween(145, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(120))
        ) {
            val pillShape = if (isDragging) {
                RoundedCornerShape(20.dp)
            } else if (isRight) {
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 4.dp, bottomStart = 4.dp)
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                    .size(
                        width = with(density) { tabTouchW.toDp() },
                        height = with(density) { tabTouchH.toDp() }
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                coroutineScope.launch {
                                    animX.stop()
                                    animY.stop()
                                }
                            },
                            onDragEnd = {
                                val w = latestMaxW
                                val h = latestMaxH
                                val tw = latestTabW
                                val th = latestTabH
                                val newSide = if (animX.value + tw / 2f > w / 2f) {
                                    BookmarkDockSide.RIGHT
                                } else {
                                    BookmarkDockSide.LEFT
                                }
                                val snapX = if (newSide == BookmarkDockSide.RIGHT) {
                                    (w - tw).coerceAtLeast(0f)
                                } else {
                                    0f
                                }
                                val newYFrac = ((animY.value + th / 2f) / h).coerceIn(0.12f, 0.88f)
                                isDragging = false
                                latestOnRepositioned(newSide, newYFrac)
                                coroutineScope.launch {
                                    animX.animateTo(
                                        targetValue = snapX,
                                        animationSpec = spring(
                                            dampingRatio = 0.76f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                val w = latestMaxW
                                val tw = latestTabW
                                val snapX = if (animX.value + tw / 2f > w / 2f) {
                                    (w - tw).coerceAtLeast(0f)
                                } else {
                                    0f
                                }
                                isDragging = false
                                coroutineScope.launch {
                                    animX.animateTo(
                                        targetValue = snapX,
                                        animationSpec = spring(
                                            dampingRatio = 0.76f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val maxAllowedX = (latestMaxW - latestTabW).coerceAtLeast(0f)
                                val minAllowedY = latestMaxH * 0.08f
                                val maxAllowedY = (latestMaxH - latestTabH - latestMaxH * 0.06f)
                                    .coerceAtLeast(minAllowedY)
                                val nextX = (animX.value + dragAmount.x).coerceIn(0f, maxAllowedX)
                                val nextY = (animY.value + dragAmount.y).coerceIn(minAllowedY, maxAllowedY)
                                coroutineScope.launch {
                                    animX.snapTo(nextX)
                                    animY.snapTo(nextY)
                                }
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleExpanded
                    )
                    .testTag("bookmark_dock_tab"),
                contentAlignment = if (isDragging) {
                    Alignment.Center
                } else if (isRight) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                }
            ) {
                // Soft translucent capsule bookmark handle
                Box(
                    modifier = Modifier
                        .scale(dragLiftScale)
                        .size(
                            width = (26.dp * dockScale.coerceIn(0.9f, 1.25f)),
                            height = (54.dp * dockScale.coerceIn(0.9f, 1.25f))
                        )
                        .shadow(
                            elevation = if (isDragging) 14.dp else 6.dp,
                            shape = pillShape,
                            ambientColor = palette.accent.copy(alpha = if (isDragging) 0.42f else 0.25f)
                        )
                        .clip(pillShape)
                        .background(palette.accent.copy(alpha = if (isDragging) 0.96f else 0.86f))
                        .border(1.dp, Color.White.copy(alpha = if (isDragging) 0.45f else 0.26f), pillShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBookmarkedHere) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd,
                        contentDescription = "Abrir menú marcapáginas (arrastrable)",
                        tint = Color.White,
                        modifier = Modifier
                            .scale(bookmarkIconPop)
                            .size((15.dp * dockScale.coerceIn(0.9f, 1.25f)))
                    )
                }
            }
        }

        // Expanded Floating Capsule Drawer — matches ProfileDropdownCapsuleOverlay (32.dp capsule, breathing margins, Zoom-In/Zoom-Out)
        val dockWidth = (264.dp * dockScale).coerceIn(232.dp, 360.dp)
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(
                initialScale = 0.56f,
                transformOrigin = drawerOrigin,
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(170)),
            exit = scaleOut(
                targetScale = 0.56f,
                transformOrigin = drawerOrigin,
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(135)),
            modifier = Modifier
                .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                .displayCutoutPadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 22.dp)
        ) {
            val dockShape = RoundedCornerShape(32.dp)
            Column(
                modifier = Modifier
                    .width(dockWidth)
                    .shadow(
                        elevation = if (palette.isDark) 20.dp else 16.dp,
                        shape = dockShape,
                        ambientColor = Color.Black.copy(alpha = 0.28f),
                        spotColor = Color.Black.copy(alpha = 0.24f)
                    )
                    .clip(dockShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, dockShape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("bookmark_dock_menu"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Soft header with title + circular Settings gear + circular close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
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
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceAlt)
                            .border(1.dp, palette.border, CircleShape)
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
                            tint = palette.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceAlt)
                            .border(1.dp, palette.border, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar menú",
                            tint = palette.ink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    thickness = 1.dp,
                    color = palette.border.copy(alpha = 0.6f)
                )

                // Direct Brightness Control directly inside the floating capsule!
                DirectBrightnessRow(
                    brightnessValue = brightnessValue,
                    onBrightnessChanged = onBrightnessChanged
                )

                // Objective Reading Tools in Luma floating capsule style (circular icon badges aligned to thumb edge)
                DockToolRow(
                    icon = if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    label = if (isBookmarkedHere) "QUITAR MARCADOR" else "MARCAR PÁGINA",
                    highlighted = isBookmarkedHere,
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onToggleBookmarkHere(); onDismiss() },
                    testTag = "dock_toggle_bookmark"
                )
                DockToolRow(
                    icon = Icons.Rounded.TextFields,
                    label = "TEXTO Y TEMA",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenAppearance() },
                    testTag = "dock_appearance"
                )
                DockToolRow(
                    icon = Icons.Rounded.FormatListBulleted,
                    label = "ÍNDICE",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenToc() },
                    testTag = "dock_toc"
                )
                DockToolRow(
                    icon = Icons.Rounded.Draw,
                    label = "DIBUJAR",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onToggleDrawing() },
                    testTag = "dock_draw"
                )
                if (isPdfDocument) {
                    DockToolRow(
                        icon = Icons.Rounded.Translate,
                        label = if (isPageTranslated) "VER ORIGINAL" else "TRADUCIR (OCR)",
                        highlighted = isPageTranslated,
                        iconOnRight = isRight,
                        scale = dockScale,
                        onClick = { onDismiss(); onTriggerOcrTranslate() },
                        testTag = "dock_ocr_translate"
                    )
                }
                DockToolRow(
                    icon = Icons.Rounded.Bookmarks,
                    label = "NOTAS",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenAnnotations() },
                    testTag = "dock_annotations"
                )
                DockToolRow(
                    icon = Icons.Rounded.Search,
                    label = "BUSCAR",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenSearch() },
                    testTag = "dock_search"
                )
                DockToolRow(
                    icon = Icons.Rounded.IosShare,
                    label = "EXPORTAR",
                    accentTint = true,
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onOpenExport() },
                    testTag = "dock_export"
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    thickness = 1.dp,
                    color = palette.border.copy(alpha = 0.6f)
                )

                DockToolRow(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    label = "BIBLIOTECA",
                    iconOnRight = isRight,
                    scale = dockScale,
                    onClick = { onDismiss(); onBackToLibrary() },
                    testTag = "dock_back_library"
                )
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
    mediaInfo: MediaPlaybackInfo,
    activeIslandEvent: DynamicIslandEvent?,
    queuedIslandCount: Int,
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
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onMediaSeek: (Long) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onTapIslandEvent: (DynamicIslandEvent) -> Unit,
    onDismissIslandEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val islandBg = Color(0xFF0C111C)
    val islandFg = Color(0xFFF3F6FC)
    val topOrigin = TransformOrigin(0.5f, 0f)

    // Only allow switching between Lectura and Música when media is actively playing or recently paused/detected
    val hasMediaAvailable = mediaInfo.hasActiveSession && mediaInfo.title.isNotBlank()
    // 0 = Lectura (app/reader tools), 1 = Música / Video (Spotify, YouTube, etc.)
    var activeIslandTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasMediaAvailable) {
        if (!hasMediaAvailable && activeIslandTab != 0) {
            activeIslandTab = 0
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Gentle 15% dim + subtle frosted veil matching ProfileDropdownCapsuleOverlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .background(palette.background.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Top-center anchor container sitting cleanly in the reserved top safe band (NEVER invades sheets/PDFs!)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(top = 6.dp, start = 12.dp, end = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 0. Centralized critical states & permitted external app notifications via Dynamic Island
            DynamicIslandEventBanner(
                event = if (!isExpanded) activeIslandEvent else null,
                queuedCount = queuedIslandCount,
                onTapEvent = onTapIslandEvent,
                onDismiss = onDismissIslandEvent
            )

            // 1. Compact Apple/Infinix Dynamic Island Pill (pure Zoom-In / Zoom-Out, zero bounce/square clip)
            AnimatedVisibility(
                visible = !isExpanded && activeIslandEvent == null,
                enter = scaleIn(
                    initialScale = 0.84f,
                    transformOrigin = topOrigin,
                    animationSpec = tween(210, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(170)),
                exit = scaleOut(
                    targetScale = 0.84f,
                    transformOrigin = topOrigin,
                    animationSpec = tween(150, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(120))
            ) {
                val pillShape = RoundedCornerShape(50)
                val showCompactMedia = !isProcessingStatus && hasMediaAvailable
                Row(
                    modifier = Modifier
                        .widthIn(max = 264.dp)
                        .shadow(8.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.32f))
                        .clip(pillShape)
                        .background(islandBg.copy(alpha = 0.96f))
                        .border(
                            width = 1.dp,
                            color = when {
                                isProcessingStatus -> palette.accent.copy(alpha = 0.7f)
                                showCompactMedia && mediaInfo.isPlaying -> Color(0xFF1DB954).copy(alpha = 0.45f)
                                else -> Color.White.copy(alpha = 0.14f)
                            },
                            shape = pillShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleExpanded
                        )
                        .padding(
                            horizontal = (11.dp * dockScale.coerceIn(0.88f, 1.18f)),
                            vertical = (5.dp * dockScale.coerceIn(0.88f, 1.18f))
                        )
                        .testTag("dynamic_island_dock"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (isProcessingStatus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF639BFF)
                        )
                        Text(
                            text = processingStatusText.ifBlank { "Traduciendo…" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = islandFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isBookmarkedHere) Color(0xFF639BFF) else Color(0xFF39C689))
                        )
                        Text(
                            text = progressLabel.substringBefore("·").trim(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = islandFg
                        )
                        if (showCompactMedia) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(12.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                            if (mediaInfo.albumArt != null) {
                                Image(
                                    bitmap = mediaInfo.albumArt.asImageBitmap(),
                                    contentDescription = "Miniatura multimedia",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (mediaInfo.isPlaying) Color(0xFF1DB954) else Color(0xFF5C8DFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            Text(
                                text = mediaInfo.title,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = islandFg.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = "Expandir isla dinámica",
                                tint = islandFg.copy(alpha = 0.72f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // 2. Compact Expanded Dynamic Island — pure Zoom-In / Zoom-Out, ALL actions in ONE single row
            AnimatedVisibility(
                visible = isExpanded,
                enter = scaleIn(
                    initialScale = 0.58f,
                    transformOrigin = topOrigin,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(170)),
                exit = scaleOut(
                    targetScale = 0.58f,
                    transformOrigin = topOrigin,
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(135))
            ) {
                val expandedW = (312.dp * dockScale).coerceIn(280.dp, 400.dp)
                val expandedShape = RoundedCornerShape(24.dp)
                Column(
                    modifier = Modifier
                        .width(expandedW)
                        .shadow(16.dp, expandedShape, ambientColor = Color.Black.copy(alpha = 0.42f))
                        .clip(expandedShape)
                        .background(islandBg.copy(alpha = 0.97f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), expandedShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("dynamic_island_expanded"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Show Lectura <-> Música switcher ONLY when music/video is playing or recently paused
                    if (hasMediaAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                IslandModePill(
                                    label = "Lectura",
                                    icon = Icons.Rounded.AutoStories,
                                    selected = activeIslandTab == 0,
                                    accentColor = palette.accent,
                                    onClick = { activeIslandTab = 0 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("island_tab_reading")
                                )
                                IslandModePill(
                                    label = "Música",
                                    icon = Icons.Rounded.MusicNote,
                                    selected = activeIslandTab == 1,
                                    accentColor = Color(0xFF1DB954),
                                    showLiveDot = mediaInfo.isPlaying,
                                    onClick = { activeIslandTab = 1 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("island_tab_media")
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Contraer",
                                    tint = islandFg.copy(alpha = 0.78f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (!hasMediaAvailable || activeIslandTab == 0) {
                        // Compact Header: Back + Title/Progress + Settings (+ Close when switcher is hidden)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable { onDismiss(); onBackToLibrary() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Biblioteca", tint = islandFg, modifier = Modifier.size(15.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookTitle,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
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
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    color = if (isProcessingStatus) Color(0xFF639BFF) else islandFg.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { onDismiss(); onOpenSettings() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Configuración", tint = islandFg.copy(alpha = 0.78f), modifier = Modifier.size(14.dp))
                            }
                            if (!hasMediaAvailable) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable(onClick = onDismiss),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Contraer", tint = islandFg.copy(alpha = 0.78f), modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // Compact Brightness Slider inside Dynamic Island
                        DirectBrightnessRow(
                            brightnessValue = brightnessValue,
                            onBrightnessChanged = onBrightnessChanged,
                            darkSurfaceOverride = true,
                            compact = true
                        )

                        // ALL quick reading tools in ONE SINGLE compact row!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IslandActionTile(
                                icon = Icons.Rounded.TextFields,
                                label = "Texto",
                                active = false,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenAppearance() }
                            IslandActionTile(
                                icon = Icons.Rounded.FormatListBulleted,
                                label = "Índice",
                                active = false,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenToc() }
                            IslandActionTile(
                                icon = Icons.Rounded.Draw,
                                label = "Dibujo",
                                active = false,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onToggleDrawing() }
                            IslandActionTile(
                                icon = if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                label = "Marcar",
                                active = isBookmarkedHere,
                                modifier = Modifier.weight(1f)
                            ) { onToggleBookmarkHere(); onDismiss() }
                            if (isPdfDocument) {
                                IslandActionTile(
                                    icon = Icons.Rounded.Translate,
                                    label = if (isPageTranslated) "Orig." else "Trad.",
                                    active = isPageTranslated,
                                    modifier = Modifier.weight(1f)
                                ) { onDismiss(); onTriggerOcrTranslate() }
                            }
                            IslandActionTile(
                                icon = Icons.Rounded.Bookmarks,
                                label = "Notas",
                                active = false,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenAnnotations() }
                            IslandActionTile(
                                icon = Icons.Rounded.Search,
                                label = "Buscar",
                                active = false,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenSearch() }
                            IslandActionTile(
                                icon = Icons.Rounded.IosShare,
                                label = "Export",
                                active = false,
                                modifier = Modifier.weight(1f)
                            ) { onDismiss(); onOpenExport() }
                        }
                    } else {
                        // TAB 1: Compact Live Music & Video Controller (only reachable when media is active/recent)
                        DynamicIslandMediaPanel(
                            mediaInfo = mediaInfo,
                            islandFg = islandFg,
                            accentColor = palette.accent,
                            onPlayPause = onMediaPlayPause,
                            onNext = onMediaNext,
                            onPrevious = onMediaPrevious,
                            onSeek = onMediaSeek,
                            onOpenNotificationAccess = onOpenNotificationAccess
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandModePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLiveDot: Boolean = false
) {
    Row(
        modifier = modifier
            .height(25.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) accentColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            maxLines = 1
        )
        if (showLiveDot && !selected) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954))
            )
        }
    }
}

@Composable
private fun DynamicIslandMediaPanel(
    mediaInfo: MediaPlaybackInfo,
    islandFg: Color,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenNotificationAccess: () -> Unit
) {
    val duration = mediaInfo.durationMs.coerceAtLeast(0L)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val progressFraction = if (isScrubbing) {
        scrubFraction
    } else if (duration > 0L) {
        (mediaInfo.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dynamic_island_media_panel"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Compact Artwork thumbnail + Title + Artist + App pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1F3B73), Color(0xFF121C30))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (mediaInfo.albumArt != null) {
                    Image(
                        bitmap = mediaInfo.albumArt.asImageBitmap(),
                        contentDescription = "Miniatura de reproducción",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF8AB4FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (mediaInfo.appName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (mediaInfo.isPlaying) Color(0xFF1DB954) else Color(0xFF8B99B3))
                        )
                        Text(
                            text = mediaInfo.appName,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mediaInfo.isPlaying) Color(0xFF4ADE80) else islandFg.copy(alpha = 0.62f),
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = mediaInfo.title,
                    fontSize = 12.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = islandFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (mediaInfo.artist.isNotBlank()) {
                    Text(
                        text = mediaInfo.artist,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = islandFg.copy(alpha = 0.64f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Compact Track / Video progress slider + timestamps
        if (duration > 0L) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progressFraction,
                    onValueChange = { v ->
                        isScrubbing = true
                        scrubFraction = v.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        if (isScrubbing) {
                            onSeek((scrubFraction * duration).toLong())
                        }
                        isScrubbing = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = if (mediaInfo.isPlaying) Color(0xFF1DB954) else accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val shownPos = if (isScrubbing) (scrubFraction * duration).toLong() else mediaInfo.positionMs
                    Text(
                        text = formatMediaTime(shownPos),
                        fontSize = 9.5.sp,
                        color = islandFg.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatMediaTime(duration),
                        fontSize = 9.5.sp,
                        color = islandFg.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Compact Media transport buttons: Previous / Play-Pause / Next
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
                    .clickable(onClick = onPrevious)
                    .testTag("island_media_prev"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = islandFg,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (mediaInfo.isPlaying) Color(0xFF1DB954) else accentColor)
                    .clickable(onClick = onPlayPause)
                    .testTag("island_media_play_pause"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mediaInfo.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (mediaInfo.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
                    .clickable(onClick = onNext)
                    .testTag("island_media_next"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = islandFg,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Compact link to unlock artwork/title/progress when using AudioManager fallback
        if (!mediaInfo.isNotificationAccessGranted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClick = onOpenNotificationAccess)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("island_media_enable_access"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = Color(0xFF7FA8FF),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Activar permiso para ver carátula y avance",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = islandFg.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatMediaTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
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
    val capsuleShape = RoundedCornerShape(28.dp)
    Box(modifier = modifier.fillMaxSize()) {
        // Gentle 15% dim scrim matching ProfileDropdownCapsuleOverlay
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .background(palette.background.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Floating Top Capsule with breathing space around edges + spring Zoom-In/Zoom-Out dropdown
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(
                initialScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(tween(150)),
            exit = scaleOut(
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = tween(140)
            ) + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .shadow(16.dp, capsuleShape, ambientColor = Color.Black.copy(alpha = 0.25f))
                    .clip(capsuleShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, capsuleShape)
                    .padding(horizontal = 12.dp, vertical = (10.dp * dockScale.coerceIn(0.9f, 1.25f))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable { onDismiss(); onBackToLibrary() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Biblioteca", tint = palette.ink, modifier = Modifier.size(19.dp))
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
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
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isBookmarkedHere) palette.accentSoft else palette.surfaceAlt)
                        .border(1.dp, if (isBookmarkedHere) palette.accent else palette.border, CircleShape)
                        .clickable { onToggleBookmarkHere() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isBookmarkedHere) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = "Marcar página",
                        tint = if (isBookmarkedHere) palette.accent else palette.ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, CircleShape)
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

        // Floating Bottom Capsule with breathing space around edges + brightness + tools
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(
                initialScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 1f),
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(tween(150)),
            exit = scaleOut(
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 1f),
                animationSpec = tween(140)
            ) + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .shadow(16.dp, capsuleShape, ambientColor = Color.Black.copy(alpha = 0.25f))
                    .clip(capsuleShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, capsuleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
    darkSurfaceOverride: Boolean = false,
    compact: Boolean = false
) {
    val palette = LocalFolioPalette.current
    val bg = if (darkSurfaceOverride) Color.White.copy(alpha = 0.08f) else palette.surfaceAlt.copy(alpha = 0.72f)
    val fg = if (darkSurfaceOverride) Color(0xFFF3F6FC) else palette.ink
    val sliderPos = if (brightnessValue < 0f) 0.5f else brightnessValue.coerceIn(0.05f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
            .background(bg)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 1.dp else 2.dp)
            .testTag("dock_brightness_control"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.BrightnessLow,
            contentDescription = "Bajar brillo",
            tint = fg.copy(alpha = 0.7f),
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Slider(
            value = sliderPos,
            onValueChange = { v -> onBrightnessChanged(v.coerceIn(0.05f, 1f)) },
            valueRange = 0.05f..1f,
            modifier = Modifier
                .weight(1f)
                .height(if (compact) 26.dp else 34.dp)
                .padding(horizontal = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent
            )
        )
        Icon(
            imageVector = Icons.Rounded.Brightness6,
            contentDescription = "Subir brillo",
            tint = fg.copy(alpha = 0.85f),
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
    }
}

@Composable
private fun IslandActionTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) palette.accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            fontSize = 8.5.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
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
    accentTint: Boolean = false,
    iconOnRight: Boolean = true
) {
    val circleSize = (36.dp * scale.coerceIn(0.9f, 1.18f)).coerceIn(34.dp, 42.dp)
    LumaCapsuleOptionRow(
        title = label,
        icon = icon,
        selected = highlighted,
        isAccentPrimary = accentTint,
        iconOnRight = iconOnRight,
        iconCircleSize = circleSize,
        onClick = onClick,
        testTag = testTag
    )
}
