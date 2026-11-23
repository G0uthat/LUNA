package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.DynamicIslandEventManager
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import com.example.ui.theme.LumaBrandFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Unified floating Luma dropdown menu container matching ProfileDropdownCapsuleOverlay:
 * - Generous breathing margins all around (never glued edge-to-edge like standard bottom sheets)
 * - Soft 32.dp rounded capsule geometry with subtle 1.dp border and layered ambient shadow
 * - Gentle 15% dim + subtle frosted veil scrim (disables Android's harsh 60% dialog dim)
 * - Smooth spring dropdown scaleIn + fadeIn on open and crisp scaleOut + fadeOut before closing
 */
@Composable
fun LumaFloatingDropdownDialog(
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.TopCenter,
    transformOrigin: TransformOrigin = TransformOrigin(0.5f, 0.08f),
    maxWidth: Dp = 520.dp,
    maxHeightFraction: Float = 0.84f,
    dismissOnScrimClick: Boolean = true,
    dismissOnBackPress: Boolean = true,
    scrimAlpha: Float = 0.15f,
    testTag: String = "luma_dropdown_panel",
    content: @Composable ColumnScope.(dismissWithAnimation: () -> Unit) -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val scope = rememberCoroutineScope()
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    var isVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val exitDurationMs = motion.duration(175)
    val triggerDismiss: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            isVisible = false
            scope.launch {
                delay(exitDurationMs.toLong() + 12L)
                onDismiss()
            }
        }
    }

    val modalHostId = remember { DynamicIslandEventManager.registerModalHost() }
    DisposableEffect(modalHostId) {
        onDispose {
            DynamicIslandEventManager.unregisterModalHost(modalHostId)
        }
    }

    Dialog(
        onDismissRequest = { if (dismissOnScrimClick && dismissOnBackPress) triggerDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnScrimClick
        )
    ) {
        // Remove Android's heavy default 60% black dialog dim so our gentle 15% scrim shines cleanly
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0f)
        }

        val capsuleShape = RoundedCornerShape(32.dp)
        val dialogContext = LocalContext.current
        val activeIslandEvent by DynamicIslandEventManager.activeEvent.collectAsStateWithLifecycle()
        val queuedIslandCount by DynamicIslandEventManager.queuedCount.collectAsStateWithLifecycle()
        val topModalHostId by DynamicIslandEventManager.topModalHostId.collectAsStateWithLifecycle()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactHeight = this.maxHeight < 500.dp
            val effectiveHeightFraction = if (isCompactHeight) maxOf(maxHeightFraction, 0.92f) else maxHeightFraction
            val maxCardHeight = (this.maxHeight * effectiveHeightFraction).coerceAtLeast(220.dp)
            val effectiveMaxWidth = if (isCompactHeight) maxOf(maxWidth, 560.dp) else maxWidth
            val outerVerticalPad = if (isCompactHeight) 8.dp else 18.dp
            val outerHorizontalPad = if (isCompactHeight) 14.dp else 16.dp

            // Gentle 15% dim + 8% theme veil matching ProfileDropdownCapsuleOverlay (stronger for mandatory blockers)
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = motion.tweenSpec(195)),
                exit = fadeOut(animationSpec = motion.exitTween(155))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                        .background(palette.background.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = scrimInteraction,
                            indication = null,
                            onClick = { if (dismissOnScrimClick) triggerDismiss() }
                        )
                )
            }

            // Floating dropdown card with comfortable breathing space all around (adapts to landscape cleanly)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = outerHorizontalPad, vertical = outerVerticalPad),
                contentAlignment = alignment
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = scaleIn(
                        initialScale = 0.89f,
                        transformOrigin = transformOrigin,
                        animationSpec = motion.modalSpring()
                    ) + slideInVertically(
                        initialOffsetY = { -16 },
                        animationSpec = motion.modalSpring()
                    ) + fadeIn(animationSpec = motion.emphasizedTween(210)),
                    exit = scaleOut(
                        targetScale = 0.92f,
                        transformOrigin = transformOrigin,
                        animationSpec = motion.exitTween(175)
                    ) + slideOutVertically(
                        targetOffsetY = { -10 },
                        animationSpec = motion.exitTween(175)
                    ) + fadeOut(animationSpec = motion.exitTween(160))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = effectiveMaxWidth)
                            .heightIn(max = maxCardHeight)
                            .shadow(
                                elevation = if (palette.isDark) 20.dp else 16.dp,
                                shape = capsuleShape,
                                ambientColor = Color.Black.copy(alpha = 0.28f),
                                spotColor = Color.Black.copy(alpha = 0.24f)
                            )
                            .clip(capsuleShape)
                            .background(palette.surface.copy(alpha = 0.98f))
                            .border(1.dp, palette.border, capsuleShape)
                            .clickable(
                                interactionSource = cardInteraction,
                                indication = null,
                                onClick = {}
                            )
                            .testTag(testTag)
                    ) {
                        content(triggerDismiss)
                    }
                }
            }

            // Only the topmost modal host renders DynamicIslandEventBanner (zero duplicate animations)
            if (topModalHostId == modalHostId) {
                DynamicIslandEventBanner(
                    event = activeIslandEvent,
                    queuedCount = queuedIslandCount,
                    onTapEvent = { event -> DynamicIslandEventManager.openActiveEvent(dialogContext, event) },
                    onDismiss = { DynamicIslandEventManager.dismissActiveEvent() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .displayCutoutPadding()
                        .statusBarsPadding()
                        .padding(top = 6.dp, start = 12.dp, end = 12.dp)
                )
            }
        }
    }
}

/**
 * Sleek capsule header matching ProfileDropdownCapsuleOverlay's clean typography & circular close button.
 */
@Composable
fun LumaDropdownHeader(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onDismiss: () -> Unit,
    showCloseButton: Boolean = true,
    closeTestTag: String = "luma_dropdown_close"
) {
    val palette = LocalFolioPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    fontFamily = LumaBrandFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.4.sp,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showCloseButton) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable(onClick = onDismiss)
                        .testTag(closeTestTag),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar",
                        tint = palette.ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = palette.border.copy(alpha = 0.6f)
        )
    }
}

/**
 * Floating capsule option row matching ProfileDropdownCapsuleOverlay's circular icon badge + uppercase/clean typography.
 */
@Composable
fun LumaCapsuleOptionRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    selected: Boolean = false,
    isAccentPrimary: Boolean = false,
    isDestructive: Boolean = false,
    iconOnRight: Boolean = true,
    iconCircleSize: Dp = 40.dp,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    val rowShape = RoundedCornerShape(24.dp)
    val errorColor = MaterialTheme.colorScheme.error

    val rowBg = when {
        isDestructive -> errorColor.copy(alpha = 0.08f)
        isAccentPrimary || selected -> palette.accentSoft.copy(alpha = 0.58f)
        else -> Color.Transparent
    }
    val titleColor = when {
        isDestructive -> errorColor
        isAccentPrimary || selected -> palette.accent
        else -> palette.ink
    }
    val subtitleColor = when {
        isDestructive -> errorColor.copy(alpha = 0.78f)
        isAccentPrimary || selected -> palette.accent.copy(alpha = 0.9f)
        else -> palette.inkMuted
    }

    val iconBadge: @Composable () -> Unit = {
        val badgeMod = when {
            isAccentPrimary || selected -> Modifier
                .size(iconCircleSize)
                .shadow(4.dp, CircleShape, ambientColor = palette.accent.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(palette.accent, palette.accentShadow)))
            isDestructive -> Modifier
                .size(iconCircleSize)
                .clip(CircleShape)
                .background(errorColor.copy(alpha = 0.14f))
                .border(1.dp, errorColor.copy(alpha = 0.28f), CircleShape)
            else -> Modifier
                .size(iconCircleSize)
                .clip(CircleShape)
                .background(palette.surfaceAlt)
                .border(1.dp, palette.border, CircleShape)
        }
        Box(modifier = badgeMod, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (selected && !isDestructive) Icons.Rounded.Check else icon,
                contentDescription = null,
                tint = when {
                    isAccentPrimary || selected -> palette.onAccent
                    isDestructive -> errorColor
                    else -> palette.ink
                },
                modifier = Modifier.size((iconCircleSize.value * 0.48f).dp)
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(
                start = if (iconOnRight) 12.dp else 4.dp,
                end = if (iconOnRight) 4.dp else 12.dp,
                top = 5.dp,
                bottom = 5.dp
            )
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (iconOnRight) Arrangement.End else Arrangement.Start
    ) {
        if (!iconOnRight) {
            iconBadge()
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (iconOnRight) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                fontFamily = LumaBrandFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.6.sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (iconOnRight) TextAlign.End else TextAlign.Start
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.5.sp,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (iconOnRight) TextAlign.End else TextAlign.Start
                )
            }
        }
        if (iconOnRight) {
            Spacer(Modifier.width(12.dp))
            iconBadge()
        }
    }
}
