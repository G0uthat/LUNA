package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.IncomingAppNotification
import com.example.ui.theme.LocalFolioPalette

/**
 * Ultra-minimal, distraction-free battery-only indicator shown in the reader while
 * Android's system status bar & notification icons are hidden.
 * Reads real live battery telemetry via ACTION_BATTERY_CHANGED (zero permissions needed).
 */
@Composable
fun ReaderBatteryOnlyIndicator(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalFolioPalette.current

    var batteryPct by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        fun updateFromIntent(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (level >= 0) {
                batteryPct = ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                updateFromIntent(intent)
            }
        }
        val sticky = runCatching {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        updateFromIntent(sticky)

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val fillColor = when {
        isCharging -> Color(0xFF22C55E)
        batteryPct <= 18 -> Color(0xFFEF4444)
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
            color = palette.ink.copy(alpha = 0.68f)
        )
        Box(
            modifier = Modifier.size(width = 22.dp, height = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val bodyW = size.width - 2.6.dp.toPx()
                val bodyH = size.height
                val strokePx = 1.2.dp.toPx()

                // Battery shell
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(bodyW - strokePx, bodyH - strokePx),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = strokePx)
                )

                // Battery positive cap
                val capW = 1.8.dp.toPx()
                val capH = bodyH * 0.44f
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(bodyW + 0.8.dp.toPx(), (bodyH - capH) / 2f),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )

                // Proportional inner fill
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
 * Apple / Infinix-style Dynamic Island notification banner that zooms in/out cleanly
 * from the top-center whenever another app posts a notification during distraction-free reading.
 */
@Composable
fun DynamicIslandNotificationBanner(
    notification: IncomingAppNotification?,
    onTapNotification: (IncomingAppNotification) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topOrigin = TransformOrigin(0.5f, 0f)
    val islandBg = Color(0xFF0C111C)
    val islandFg = Color(0xFFF3F6FC)

    // Keep last non-null reference briefly so exit zoom-out renders smoothly
    var lastNotif by remember { mutableStateOf<IncomingAppNotification?>(null) }
    if (notification != null) {
        lastNotif = notification
    }

    AnimatedVisibility(
        visible = notification != null,
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
        val current = notification ?: lastNotif ?: return@AnimatedVisibility
        val pillShape = RoundedCornerShape(26.dp)
        Row(
            modifier = Modifier
                .widthIn(min = 210.dp, max = 328.dp)
                .shadow(16.dp, pillShape, ambientColor = Color.Black.copy(alpha = 0.45f))
                .clip(pillShape)
                .background(islandBg.copy(alpha = 0.97f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), pillShape)
                .clickable { onTapNotification(current) }
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("dynamic_island_notification_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (current.appIcon != null) {
                Image(
                    bitmap = current.appIcon.asImageBitmap(),
                    contentDescription = current.appName,
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
                        contentDescription = current.appName,
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
                    Text(
                        text = current.appName.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = Color(0xFF7BA4FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                if (current.text.isNotBlank()) {
                    Text(
                        text = current.text,
                        fontSize = 11.sp,
                        lineHeight = 13.5.sp,
                        color = islandFg.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onDismiss)
                    .testTag("dynamic_island_notification_dismiss"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar notificación",
                    tint = islandFg.copy(alpha = 0.72f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
