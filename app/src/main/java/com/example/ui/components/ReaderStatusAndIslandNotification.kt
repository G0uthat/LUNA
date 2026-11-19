package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.CriticalEventKind
import com.example.engine.DynamicIslandEvent
import com.example.engine.DynamicIslandEventManager
import com.example.ui.theme.LocalFolioPalette

/**
 * Ultra-minimal, distraction-free battery-only indicator shown in the reader while
 * Android's system status bar & notification icons are hidden.
 * Observes centralized live battery telemetry from [DynamicIslandEventManager].
 */
@Composable
fun ReaderBatteryOnlyIndicator(
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val battery by DynamicIslandEventManager.batteryState.collectAsStateWithLifecycle()
    val batteryPct = battery.levelPct
    val isCharging = battery.isCharging

    val fillColor = when {
        isCharging -> Color(0xFF22C55E)
        batteryPct <= 15 -> Color(0xFFEF4444)
        else -> palette.ink.copy(alpha = 0.74f)
    }
    val outlineColor = palette.ink.copy(alpha = 0.52f)

    Row(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("reader_battery_only_indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "$batteryPct%",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (!isCharging && batteryPct <= 15) Color(0xFFEF4444) else palette.ink.copy(alpha = 0.68f)
        )
        Box(
            modifier = Modifier.size(width = 22.dp, height = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val bodyW = size.width - 2.6.dp.toPx()
                val bodyH = size.height
                val strokePx = 1.2.dp.toPx()

                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(bodyW - strokePx, bodyH - strokePx),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = strokePx)
                )

                val capW = 1.8.dp.toPx()
                val capH = bodyH * 0.44f
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(bodyW + 0.8.dp.toPx(), (bodyH - capH) / 2f),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )

                val pad = 2.dp.toPx()
                val innerMaxW = (bodyW - pad * 2f).coerceAtLeast(1f)
                val fillW = (innerMaxW * (batteryPct / 100f)).coerceAtLeast(if (batteryPct > 0) 1.5.dp.toPx() else 0f)
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(pad, pad),
                    size = Size(fillW, (bodyH - pad * 2f).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(1.6.dp.toPx(), 1.6.dp.toPx())
                )
            }
        }
        if (isCharging) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "Cargando",
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Centralized Dynamic Island event banner that zooms in/out cleanly from top-center,
 * rendering ONLY:
 * - [DynamicIslandEvent.CriticalState]: critical battery, connectivity drops/restores,
 *   urgent calls/alarms, or critical app errors (with subtle pulsing critical dot & tinted ring).
 * - [DynamicIslandEvent.AllowedAppAlert]: notifications from explicitly permitted external apps.
 */
@Composable
fun DynamicIslandEventBanner(
    event: DynamicIslandEvent?,
    queuedCount: Int = 0,
    onTapEvent: (DynamicIslandEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topOrigin = TransformOrigin(0.5f, 0f)
    val islandBg = Color(0xFF0C111C)
    val islandFg = Color(0xFFF3F6FC)

    // Keep last non-null reference briefly so exit zoom-out renders smoothly
    var lastEvent by remember { mutableStateOf<DynamicIslandEvent?>(null) }
    if (event != null) {
        lastEvent = event
    }

    AnimatedVisibility(
        visible = event != null,
        modifier = modifier,
        enter = scaleIn(
            initialScale = 0.56f,
            transformOrigin = topOrigin,
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(170)),
        exit = scaleOut(
            targetScale = 0.56f,
            transformOrigin = topOrigin,
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(135))
    ) {
        val current = event ?: lastEvent ?: return@AnimatedVisibility
        val pillShape = RoundedCornerShape(26.dp)

        when (current) {
            is DynamicIslandEvent.CriticalState -> {
                val accent = Color(current.accentColorArgb)
                val pulseTransition = rememberInfiniteTransition(label = "island_critical_pulse")
                val dotAlpha by pulseTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(680, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "island_critical_dot_alpha"
                )
                val isInteractive = current.contentIntent != null || current.packageName.isNotBlank()

                Row(
                    modifier = Modifier
                        .widthIn(min = 224.dp, max = 336.dp)
                        .shadow(18.dp, pillShape, ambientColor = accent.copy(alpha = 0.42f))
                        .clip(pillShape)
                        .background(islandBg.copy(alpha = 0.98f))
                        .border(1.2.dp, accent.copy(alpha = 0.58f), pillShape)
                        .clickable {
                            if (isInteractive) onTapEvent(current) else onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("dynamic_island_critical_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.20f))
                            .border(1.dp, accent.copy(alpha = 0.52f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = criticalEventIcon(current.kind),
                            contentDescription = current.badgeLabel,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .alpha(dotAlpha)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = current.badgeLabel.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.65.sp,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (queuedCount > 0) {
                                IslandQueueCountChip(count = queuedCount)
                            }
                        }
                        Text(
                            text = current.title,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = islandFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (current.subtitle.isNotBlank()) {
                            Text(
                                text = current.subtitle,
                                fontSize = 11.sp,
                                lineHeight = 13.5.sp,
                                color = islandFg.copy(alpha = 0.78f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IslandDismissCircleButton(
                        contentDescription = "Cerrar alerta crítica",
                        onClick = onDismiss
                    )
                }
            }

            is DynamicIslandEvent.AllowedAppAlert -> {
                val notif = current.notification
                Row(
                    modifier = Modifier
                        .widthIn(min = 216.dp, max = 332.dp)
                        .shadow(16.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.45f))
                        .clip(pillShape)
                        .background(islandBg.copy(alpha = 0.97f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), pillShape)
                        .clickable { onTapEvent(current) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("dynamic_island_notification_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (notif.appIcon != null) {
                        Image(
                            bitmap = notif.appIcon.asImageBitmap(),
                            contentDescription = notif.appName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3E6AE1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = notif.appName,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Text(
                                text = notif.appName.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                color = Color(0xFF7BA4FF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (queuedCount > 0) {
                                IslandQueueCountChip(count = queuedCount)
                            }
                        }
                        Text(
                            text = notif.title,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = islandFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (notif.text.isNotBlank()) {
                            Text(
                                text = notif.text,
                                fontSize = 11.sp,
                                lineHeight = 13.5.sp,
                                color = islandFg.copy(alpha = 0.78f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IslandDismissCircleButton(
                        contentDescription = "Cerrar notificación",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun IslandQueueCountChip(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFE2E8F0)
        )
    }
}

@Composable
private fun IslandDismissCircleButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .testTag("dynamic_island_notification_dismiss"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = contentDescription,
            tint = Color(0xFFF3F6FC).copy(alpha = 0.78f),
            modifier = Modifier.size(13.dp)
        )
    }
}

private fun criticalEventIcon(kind: CriticalEventKind): ImageVector = when (kind) {
    CriticalEventKind.CRITICAL_BATTERY,
    CriticalEventKind.LOW_BATTERY -> Icons.Rounded.BatteryAlert
    CriticalEventKind.CHARGING_RESUMED -> Icons.Rounded.Bolt
    CriticalEventKind.OFFLINE -> Icons.Rounded.WifiOff
    CriticalEventKind.ONLINE_RESTORED -> Icons.Rounded.Wifi
    CriticalEventKind.INCOMING_CALL -> Icons.Rounded.PhoneInTalk
    CriticalEventKind.ALARM -> Icons.Rounded.Alarm
    CriticalEventKind.CRITICAL_ERROR -> Icons.Rounded.ErrorOutline
}
