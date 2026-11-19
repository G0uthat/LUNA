package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import com.example.data.model.UserProfile
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LumaBrandFontFamily
import com.example.ui.theme.LumaLetteringFontFamily

/**
 * Wide, extended medium-bold brush-lettering logotype for "Luma" using Kaushan Script,
 * horizontally elongated across the top header with a sleek tapered ink flourish
 * and high-contrast luminous tuning for dark themes.
 */
@Composable
fun LumaWideWordmark(
    bookCount: Int,
    seriesCount: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)

    BoxWithConstraints(modifier = modifier) {
        val availDp = maxWidth.value
        val targetFontSp = ((availDp * 0.165f) / fontScale.coerceAtMost(1.25f)).coerceIn(36f, 46f)
        // Extended horizontal brush-lettering stretch proportional to available bar width
        val horizontalStretch = (availDp / 215f).coerceIn(1.22f, 1.42f)
        val letterTrackingSp = ((availDp - 200f) * 0.025f).coerceIn(1.6f, 4.2f)

        val wordmarkBrush = remember(palette.ink, palette.accent, palette.isDark) {
            if (palette.isDark) {
                // High-contrast luminous ink for Dark / Midnight / Navy / AMOLED
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.White,
                        lerp(Color.White, palette.ink, 0.20f),
                        lerp(Color.White, palette.accent, 0.34f)
                    )
                )
            } else {
                Brush.horizontalGradient(
                    colors = listOf(
                        palette.ink,
                        palette.ink,
                        lerp(palette.ink, palette.accent, 0.44f)
                    )
                )
            }
        }

        val wordmarkShadow = remember(palette.accent, palette.isDark) {
            if (palette.isDark) {
                Shadow(
                    color = palette.accent.copy(alpha = 0.34f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 11f
                )
            } else {
                Shadow(
                    color = Color.Black.copy(alpha = 0.08f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 3f
                )
            }
        }

        val swashStart = if (palette.isDark) {
            lerp(palette.accent, Color.White, 0.32f).copy(alpha = 0.90f)
        } else {
            palette.accent.copy(alpha = 0.85f)
        }
        val swashMid = if (palette.isDark) {
            Color.White.copy(alpha = 0.48f)
        } else {
            palette.ink.copy(alpha = 0.36f)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "Luma",
                style = TextStyle(
                    brush = wordmarkBrush,
                    shadow = wordmarkShadow,
                    fontFamily = LumaLetteringFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = targetFontSp.sp,
                    lineHeight = (targetFontSp * 1.18f).sp,
                    letterSpacing = letterTrackingSp.sp,
                    textGeometricTransform = TextGeometricTransform(scaleX = horizontalStretch)
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .padding(start = 2.dp, end = 10.dp)
                    .testTag("luma_brand_wordmark")
            )

            // Sleek extended calligraphic flourish spanning across the header
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(7.dp)
                    .padding(start = 2.dp)
            ) {
                val w = size.width
                val h = size.height
                val swashPath = Path().apply {
                    moveTo(0f, h * 0.62f)
                    cubicTo(
                        w * 0.28f, h * 0.08f,
                        w * 0.66f, h * 0.95f,
                        w, h * 0.26f
                    )
                }
                drawPath(
                    path = swashPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            swashStart,
                            swashMid,
                            Color.Transparent
                        )
                    ),
                    style = Stroke(
                        width = 2.1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = "$bookCount libros · $seriesCount series",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = palette.inkMuted.copy(alpha = if (palette.isDark) 0.88f else 0.80f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 3.dp)
            )
        }
    }
}

/**
 * Floating vertical profile capsule menu anchored seamlessly to the top-right profile sphere.
 * - Top sphere row IS "CUENTA" (no "Lector" text, no duplicate Cuenta circle): tapping it a second time
 *   immediately opens ONLY Account configuration.
 * - Below: cleanly separated AMIGOS, AJUSTES, and IMPORTAR entries.
 */
@Composable
fun ProfileDropdownCapsuleOverlay(
    visible: Boolean,
    anchorTopPx: Float,
    userProfile: UserProfile,
    unreadMessages: Int,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportDocument: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val density = LocalDensity.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val innerPadPx = with(density) { 10.dp.toPx() }
    val capsuleTopOffsetPx = (anchorTopPx - innerPadPx).coerceAtLeast(0f).roundToInt()

    Box(modifier = Modifier.fillMaxSize()) {
        // Gentle 15% dim + subtle frosted veil (pairs with the 18% background blur in LibraryScreen)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .background(palette.background.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
                    .testTag("profile_menu_scrim")
            )
        }

        // Tall vertical pill/capsule aligned pixel-exact over the top-right profile sphere
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, capsuleTopOffsetPx) }
                .padding(end = 8.dp),
            enter = scaleIn(
                initialScale = 0.86f,
                transformOrigin = TransformOrigin(0.88f, 0.08f),
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(150)),
            exit = scaleOut(
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(0.88f, 0.08f),
                animationSpec = tween(140)
            ) + fadeOut(animationSpec = tween(120))
        ) {
            val capsuleShape = RoundedCornerShape(32.dp)
            val accountSubtitle = remember(userProfile.username) {
                val clean = userProfile.username.trim()
                if (clean.isNotBlank() && !clean.equals("Lector", ignoreCase = true)) {
                    clean
                } else {
                    "Perfil y privacidad"
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(min = 206.dp, max = 238.dp)
                    .shadow(
                        elevation = if (palette.isDark) 20.dp else 16.dp,
                        shape = capsuleShape,
                        ambientColor = Color.Black.copy(alpha = 0.28f),
                        spotColor = Color.Black.copy(alpha = 0.24f)
                    )
                    .clip(capsuleShape)
                    .background(palette.surface.copy(alpha = 0.98f))
                    .border(1.dp, palette.border, capsuleShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("profile_dropdown_capsule"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top sphere row IS CUENTA: tapping the sphere once opens this menu; tapping right here again
                // ("doble click") opens ONLY Account configuration!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .clickable {
                            onDismiss()
                            onOpenAccount()
                        }
                        .padding(start = 12.dp, end = 0.dp, top = 0.dp, bottom = 0.dp)
                        .testTag("profile_menu_account_item"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "CUENTA",
                            fontFamily = LumaBrandFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            letterSpacing = 1.1.sp,
                            color = palette.ink,
                            maxLines = 1,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = accountSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatarCircle(
                            displayName = userProfile.username.ifBlank { "C" },
                            avatarPath = userProfile.avatarPath,
                            avatarBase64 = "",
                            size = 44.dp,
                            showOnlineDot = userProfile.privacy.showOnlineStatus,
                            unreadBadge = 0
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 1.dp,
                    color = palette.border.copy(alpha = 0.6f)
                )

                // 1. AMIGOS (Solo amigos P2P y chat)
                ProfileCapsuleActionRow(
                    title = "AMIGOS",
                    subtitle = if (unreadMessages > 0) {
                        "$unreadMessages sin leer"
                    } else {
                        "Chat y lectura"
                    },
                    subtitleHighlight = unreadMessages > 0,
                    icon = Icons.Rounded.Group,
                    iconContentDescription = "Abrir amigos y chat",
                    badgeCount = unreadMessages,
                    isAccentPrimary = false,
                    isLoading = false,
                    onClick = {
                        onDismiss()
                        onOpenFriends()
                    },
                    testTag = "profile_menu_friends_item"
                )

                // 2. AJUSTES (Solo temas, tipografía, lector y fuentes)
                ProfileCapsuleActionRow(
                    title = "AJUSTES",
                    subtitle = "Tema y lectura",
                    subtitleHighlight = false,
                    icon = Icons.Rounded.Settings,
                    iconContentDescription = "Abrir ajustes",
                    badgeCount = 0,
                    isAccentPrimary = false,
                    isLoading = false,
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    },
                    testTag = "profile_menu_settings_item"
                )

                // 3. IMPORTAR (EPUB, PDF, DOCX, TXT)
                ProfileCapsuleActionRow(
                    title = "IMPORTAR",
                    subtitle = if (isImporting) "Importando…" else "EPUB · PDF · DOCX",
                    subtitleHighlight = true,
                    icon = Icons.Rounded.Add,
                    iconContentDescription = "Importar documento",
                    badgeCount = 0,
                    isAccentPrimary = true,
                    isLoading = isImporting,
                    enabled = !isImporting,
                    onClick = {
                        onDismiss()
                        onImportDocument()
                    },
                    testTag = "import_document_button"
                )
            }
        }
    }
}

@Composable
private fun ProfileCapsuleActionRow(
    title: String,
    subtitle: String,
    subtitleHighlight: Boolean,
    icon: ImageVector,
    iconContentDescription: String,
    badgeCount: Int,
    isAccentPrimary: Boolean,
    isLoading: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    val rowShape = RoundedCornerShape(26.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                if (isAccentPrimary) palette.accentSoft.copy(alpha = 0.55f)
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                fontFamily = LumaBrandFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                letterSpacing = 1.1.sp,
                color = if (isAccentPrimary) palette.accent else palette.ink,
                maxLines = 1,
                textAlign = TextAlign.End
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.5.sp,
                color = if (subtitleHighlight) palette.accent else palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            val circleModifier = if (isAccentPrimary) {
                Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape, ambientColor = palette.accent.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(palette.accent, palette.accentShadow)
                        )
                    )
            } else {
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, CircleShape)
            }

            Box(
                modifier = circleModifier,
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (isAccentPrimary) palette.onAccent else palette.accent
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        tint = if (isAccentPrimary) palette.onAccent else palette.ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(palette.accent)
                        .border(1.5.dp, palette.surface, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.coerceAtMost(99).toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
