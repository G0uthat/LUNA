package com.example.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DictionarySourceEntity
import com.example.data.model.IslandNotificationFilterMode
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import com.example.data.model.TargetFpsOption
import com.example.engine.DisplayRefreshRateController
import com.example.engine.DynamicIslandEventManager
import com.example.engine.SystemMediaController
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.paletteFor
import kotlin.math.roundToInt

/**
 * Centralized App Configuration sheet (reachable from Library settings gear & discreet Reader gear):
 * - Tab 0: General & Visual (7 Themes incl. Azul Marino, Device System Font toggle, Menu Style
 *          [Marcapáginas arrastrable / Isla dinámica / Clásico], Dock Size scale for Tablets, Auto-Covers)
 * - Tab 1: Diccionario e IA (Google Traductor vs User BYOK AI API Key + custom {palabra} sources)
 */
@Composable
fun AppSettingsSheet(
    prefs: ReaderPreferences,
    sources: List<DictionarySourceEntity>,
    initialTab: Int = 0,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onAddSource: (name: String, urlTemplate: String) -> Unit,
    onToggleSource: (DictionarySourceEntity) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onRegenerateCovers: (() -> Unit)? = null,
    onReplayOnboarding: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.TopCenter,
        transformOrigin = TransformOrigin(0.84f, 0.06f),
        maxWidth = 520.dp,
        maxHeightFraction = 0.85f,
        testTag = "app_settings_sheet"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = "Ajustes",
            subtitle = "Tema, lectura, diccionario y créditos",
            leadingIcon = Icons.Rounded.Settings,
            onDismiss = dismissAnimated,
            closeTestTag = "app_settings_close"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppSettingsContent(
                prefs = prefs,
                sources = sources,
                activeTab = activeTab,
                onTabChange = { activeTab = it },
                onUpdatePrefs = onUpdatePrefs,
                onAddSource = onAddSource,
                onToggleSource = onToggleSource,
                onDeleteSource = onDeleteSource,
                onRegenerateCovers = onRegenerateCovers,
                onReplayOnboarding = onReplayOnboarding
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun AppSettingsContent(
    prefs: ReaderPreferences,
    sources: List<DictionarySourceEntity>,
    activeTab: Int,
    onTabChange: (Int) -> Unit,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onAddSource: (name: String, urlTemplate: String) -> Unit,
    onToggleSource: (DictionarySourceEntity) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onRegenerateCovers: (() -> Unit)? = null,
    onReplayOnboarding: (() -> Unit)? = null
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val mediaState by SystemMediaController.mediaState.collectAsStateWithLifecycle()
    val suppressedCount by DynamicIslandEventManager.suppressedCount.collectAsStateWithLifecycle()
    var showIslandPolicyDialog by remember { mutableStateOf(false) }

    if (showIslandPolicyDialog) {
        IslandNotificationPolicyDialog(
            prefs = prefs,
            isListenerGranted = mediaState.isNotificationAccessGranted,
            suppressedCount = suppressedCount,
            onUpdatePrefs = onUpdatePrefs,
            onOpenSystemAccess = { SystemMediaController.openNotificationAccessSettings(context) },
            onDismiss = { showIslandPolicyDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Segmented Tabs (horizontally scrollable so all 3 fit comfortably on compact screens)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TactileChip(
                    label = "Diseño y Menú",
                    icon = Icons.Rounded.Palette,
                    selected = activeTab == 0,
                    onClick = { onTabChange(0) },
                    modifier = Modifier.testTag("settings_tab_general")
                )
                TactileChip(
                    label = "Diccionario e IA",
                    icon = Icons.Rounded.MenuBook,
                    selected = activeTab == 1,
                    onClick = { onTabChange(1) },
                    modifier = Modifier.testTag("settings_tab_dict_ai")
                )
                TactileChip(
                    label = "Créditos",
                    icon = Icons.Rounded.Favorite,
                    selected = activeTab == 2,
                    onClick = { onTabChange(2) },
                    modifier = Modifier.testTag("settings_tab_credits")
                )
            }

            if (activeTab == 0) {
                // 1. Soft Visual Themes, Warm Blue-Light Shield & Configurable Night Reading Mode
                SoftThemeAndNightModePanel(
                    prefs = prefs,
                    onUpdatePrefs = onUpdatePrefs,
                    compactMode = false
                )

                // 2. FPS / High Refresh Rate Unlock (60, 90, 120, 144, 240 FPS + Máx)
                val displayInfo = remember(context, prefs.targetFps) {
                    (context as? Activity)?.let { DisplayRefreshRateController.getDisplayInfo(it) }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.55f))
                        .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Tasa de refresco (FPS)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.ink
                            )
                        }
                        val maxHzLabel = displayInfo?.maxSupportedHz?.roundToInt() ?: 60
                        Text(
                            text = "Pantalla: ${maxHzLabel} Hz",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (fpsOption in TargetFpsOption.entries) {
                            val selected = prefs.targetFps == fpsOption
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) palette.accent else palette.surface)
                                    .border(
                                        width = if (selected) 0.dp else 1.dp,
                                        color = if (selected) palette.accent else palette.border,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        onUpdatePrefs { it.copy(targetFps = fpsOption) }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 2.dp)
                                    .testTag("settings_fps_${fpsOption.name}"),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = fpsOption.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) palette.onAccent else palette.ink,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (fpsOption == TargetFpsOption.AUTO) "Auto" else "FPS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) palette.onAccent.copy(alpha = 0.85f) else palette.inkMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    val maxHz = displayInfo?.maxSupportedHz?.roundToInt() ?: 60
                    val statusCaption = when {
                        prefs.targetFps == TargetFpsOption.AUTO ->
                            "Desbloqueado automáticamente al máximo nativo de tu pantalla (${maxHz} Hz)."
                        prefs.targetFps.fps.roundToInt() > maxHz ->
                            "Solicitando ${prefs.targetFps.label} FPS. Tu panel actual soporta hasta ${maxHz} Hz (se aplicará el máximo disponible)."
                        else ->
                            "Frecuencia objetivo desbloqueada a ${prefs.targetFps.label} FPS con impulso táctil."
                    }
                    Text(
                        text = statusCaption,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mostrar contador de FPS en vivo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.ink
                        )
                        Switch(
                            checked = prefs.showFpsCounter,
                            onCheckedChange = { show -> onUpdatePrefs { it.copy(showFpsCounter = show) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                            modifier = Modifier.testTag("show_fps_counter_switch")
                        )
                    }
                }

                // 3. Device System Typography Toggle (changes ALL app fonts to device font)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.7f))
                        .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Usar tipografía del dispositivo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "Adapta toda la interfaz de Luma a la fuente configurada en tu teléfono o tablet",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = prefs.useSystemFont,
                        onCheckedChange = { enabled -> onUpdatePrefs { it.copy(useSystemFont = enabled) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                        modifier = Modifier.testTag("use_system_font_switch")
                    )
                }

                // 3. Reader Menu Style (Draggable Bookmark / Apple Dynamic Island / Classic Bar)
                SectionTitle("Estilo del menú durante la lectura")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (style in ReaderMenuStyle.entries) {
                        val selected = prefs.menuStyle == style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) palette.accentSoft else palette.surfaceAlt.copy(alpha = 0.55f))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) palette.accent else palette.border,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { onUpdatePrefs { it.copy(menuStyle = style) } }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("menu_style_${style.name}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = if (selected) palette.accent else palette.inkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = style.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.ink
                                )
                                Text(
                                    text = style.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkMuted
                                )
                            }
                        }
                    }
                }

                // 4. Configurable Menu Size for Phones & Large Tablets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tamaño del menú desplegable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "${(prefs.dockScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.accent
                        )
                    }
                    Text(
                        text = "Ajusta la escala del marcapáginas e isla para teléfonos o tablets grandes",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                    Slider(
                        value = prefs.dockScale,
                        onValueChange = { s -> onUpdatePrefs { it.copy(dockScale = s) } },
                        valueRange = 0.85f..1.35f,
                        colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
                        modifier = Modifier.testTag("dock_scale_slider")
                    )
                }

                // 5. Effortless Tap-to-Turn-Page + Night PDF Filter + Media Player Access
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.65f))
                        .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cambio rápido con toque lateral",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                            Text(
                                text = "Un clic en el borde izquierdo o derecho pasa o retrocede página en libros y PDFs",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = prefs.tapZonesEnabled,
                            onCheckedChange = { enabled -> onUpdatePrefs { it.copy(tapZonesEnabled = enabled) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                            modifier = Modifier.testTag("tap_zones_enabled_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Filtro nocturno suave en PDF",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                            Text(
                                text = "Atenúa el blanco intenso de los PDFs cuando lees con temas oscuros o Medianoche",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = prefs.pdfNightFilter,
                            onCheckedChange = { enabled -> onUpdatePrefs { it.copy(pdfNightFilter = enabled) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                            modifier = Modifier.testTag("pdf_night_filter_switch")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.accent.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
                            .clickable { showIslandPolicyDialog = true }
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                            .testTag("island_event_manager_settings_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (mediaState.isNotificationAccessGranted) {
                                        Color(0xFF22C55E).copy(alpha = 0.14f)
                                    } else {
                                        palette.accentSoft
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = if (mediaState.isNotificationAccessGranted) Color(0xFF22C55E) else palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gestor de Isla Dinámica",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                            val modeSummary = when (prefs.islandFilterMode) {
                                IslandNotificationFilterMode.ALLOWED_ONLY ->
                                    "Críticos + ${prefs.islandAllowedPackages.size} apps permitidas"
                                IslandNotificationFilterMode.CRITICAL_ONLY ->
                                    "Solo estados críticos (apps silenciadas)"
                                IslandNotificationFilterMode.ALL_APPS ->
                                    "Estados críticos y todas las apps"
                            }
                            Text(
                                text = if (mediaState.isNotificationAccessGranted) {
                                    "$modeSummary · $suppressedCount bloqueadas"
                                } else {
                                    "$modeSummary · Activa permiso del sistema"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Configurar Isla Dinámica",
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 6. Auto-covers helper
                if (onRegenerateCovers != null) {
                    TactileButton(
                        onClick = onRegenerateCovers,
                        containerColor = palette.surfaceAlt,
                        shadowColor = palette.borderShadow,
                        contentColor = palette.ink,
                        borderColor = palette.border,
                        cornerRadius = 18.dp,
                        lipDepth = 2.dp,
                        modifier = Modifier.testTag("regenerate_covers_button")
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Autocompletar carátulas de la biblioteca", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (activeTab == 1) {
                // Embedded Dictionary Sources & BYOK AI Engine content
                DictionaryAndAiSettingsContent(
                    prefs = prefs,
                    sources = sources,
                    onUpdatePrefs = onUpdatePrefs,
                    onAddSource = onAddSource,
                    onToggleSource = onToggleSource,
                    onDeleteSource = onDeleteSource
                )
            } else {
                // Tab 2: Credits, Community Discord & Dedication
                LumaCreditsSettingsContent(
                    onReplayOnboarding = onReplayOnboarding
                )
            }
    }
}

private const val LUMA_DISCORD_URL = "https://discord.gg/f2Ey5NP9dN"

@Composable
private fun LumaCreditsSettingsContent(
    onReplayOnboarding: (() -> Unit)? = null
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    var copiedDiscord by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_credits_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Brand emblem card with the vectorized calligraphic Luma logo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LumaCalligraphicLogo(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(66.dp)
            )
            Text(
                text = "Lector universal EPUB · PDF · DOCX · PPTX · TXT · FB2",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                textAlign = TextAlign.Center
            )
        }

        // 2. Community Discord Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surfaceAlt.copy(alpha = 0.68f))
                .border(1.dp, palette.accent.copy(alpha = 0.34f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5865F2).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forum,
                        contentDescription = null,
                        tint = Color(0xFF5865F2),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Comunidad oficial en Discord",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink
                    )
                    Text(
                        text = "Comparte lecturas, reporta mejoras y únete a la comunidad de Luma.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
            }

            // Direct clickable URL box + Copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .clickable {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LUMA_DISCORD_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("credits_discord_link_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LUMA_DISCORD_URL,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .clickable {
                            runCatching {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Discord Luma", LUMA_DISCORD_URL))
                                copiedDiscord = true
                            }
                        }
                        .testTag("credits_copy_discord_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (copiedDiscord) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copiar enlace de Discord",
                        tint = if (copiedDiscord) Color(0xFF22C55E) else palette.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            TactileButton(
                onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LUMA_DISCORD_URL)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                containerColor = Color(0xFF5865F2),
                shadowColor = Color(0xFF3C45A5),
                contentColor = Color.White,
                cornerRadius = 16.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("credits_open_discord_btn")
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unirse al Discord de la comunidad", fontWeight = FontWeight.ExtraBold)
            }
        }

        // 3. Dedicated separate Dedication Card
        val roseAccent = if (palette.isDark) Color(0xFFF48FB1) else Color(0xFFD84B78)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(roseAccent.copy(alpha = if (palette.isDark) 0.12f else 0.08f))
                .border(1.dp, roseAccent.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .testTag("credits_dedication_card"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(roseAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = roseAccent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Text(
                text = "Dedicada a mi amada Anghela.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                color = palette.ink,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp
            )
        }

        // 4. Optional Permissions & Interactive Tour Replay
        if (onReplayOnboarding != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surfaceAlt.copy(alpha = 0.6f))
                    .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                    .clickable(onClick = onReplayOnboarding)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("credits_replay_tour_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Permisos y tour interactivo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = "Vuelve a revisar permisos del sistema y el recorrido guiado",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Floating dropdown configuration dialog for the centralized Dynamic Island Event Manager:
 * controls critical state alerts, notification filter mode, per-app allowlist (curated + auto-discovered),
 * Android Notification Access shortcut, and instant live previews.
 */
@Composable
private fun IslandNotificationPolicyDialog(
    prefs: ReaderPreferences,
    isListenerGranted: Boolean,
    suppressedCount: Int,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onOpenSystemAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val discoveredApps by DynamicIslandEventManager.discoveredApps.collectAsStateWithLifecycle()

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.TopCenter,
        transformOrigin = TransformOrigin(0.5f, 0.08f),
        maxWidth = 520.dp,
        maxHeightFraction = 0.86f,
        testTag = "island_policy_dialog"
    ) { dismissAnimated ->
        LumaDropdownHeader(
            title = "Gestor de Isla Dinámica",
            subtitle = "Estados críticos y apps permitidas",
            leadingIcon = Icons.Rounded.Shield,
            onDismiss = dismissAnimated,
            closeTestTag = "island_policy_close"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. System Notification Listener Permission Banner + Suppressed counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isListenerGranted) Color(0xFF22C55E).copy(alpha = 0.10f)
                        else palette.accentSoft.copy(alpha = 0.7f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isListenerGranted) Color(0xFF22C55E).copy(alpha = 0.35f) else palette.accent.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(onClick = onOpenSystemAccess)
                    .padding(horizontal = 13.dp, vertical = 11.dp)
                    .testTag("island_system_access_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isListenerGranted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = if (isListenerGranted) Color(0xFF22C55E) else palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isListenerGranted) "Puente del sistema activo" else "Activar acceso a notificaciones",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = if (isListenerGranted) {
                            "Interceptando avisos con filtro estricto ($suppressedCount silenciadas)"
                        } else {
                            "Permite a Luma mostrar solo las apps autorizadas en la Isla Dinámica"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isListenerGranted) "Ajustar" else "Activar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isListenerGranted) Color(0xFF22C55E) else palette.accent
                )
            }

            // 2. Live Preview Triggers (Critical State vs Allowed App)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.32f), RoundedCornerShape(14.dp))
                        .clickable { DynamicIslandEventManager.triggerPreviewCritical() }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("island_preview_critical_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Probar crítico",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accentSoft.copy(alpha = 0.72f))
                        .border(1.dp, palette.accent.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
                        .clickable { DynamicIslandEventManager.triggerPreviewAllowedApp(context) }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("island_preview_app_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Probar app",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        maxLines = 1
                    )
                }
            }

            // 3. Filter Policy Mode Selector
            SectionTitle("Política de filtrado")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in IslandNotificationFilterMode.entries) {
                    val selected = prefs.islandFilterMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) palette.accentSoft else palette.surfaceAlt.copy(alpha = 0.55f))
                            .border(
                                width = if (selected) 1.8.dp else 1.dp,
                                color = if (selected) palette.accent else palette.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onUpdatePrefs { it.copy(islandFilterMode = mode) } }
                            .padding(horizontal = 13.dp, vertical = 10.dp)
                            .testTag("island_filter_mode_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) palette.accent else palette.ink
                            )
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted
                            )
                        }
                    }
                }
            }

            // 4. Critical Device & System State Monitors
            SectionTitle("Estados críticos integrados")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surfaceAlt.copy(alpha = 0.6f))
                    .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IslandCriticalToggleRow(
                    icon = Icons.Rounded.BatteryAlert,
                    iconTint = Color(0xFFEF4444),
                    title = "Batería baja y crítica",
                    subtitle = "Avisa en 15%, 5% y al reconectar el cargador",
                    checked = prefs.islandShowCriticalBattery,
                    onCheckedChange = { enabled -> onUpdatePrefs { it.copy(islandShowCriticalBattery = enabled) } },
                    testTag = "island_toggle_battery"
                )
                HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                IslandCriticalToggleRow(
                    icon = Icons.Rounded.WifiOff,
                    iconTint = Color(0xFFF59E0B),
                    title = "Pérdida y recuperación de red",
                    subtitle = "Informa cuando pierdes o recuperas conexión a internet",
                    checked = prefs.islandShowCriticalConnectivity,
                    onCheckedChange = { enabled -> onUpdatePrefs { it.copy(islandShowCriticalConnectivity = enabled) } },
                    testTag = "island_toggle_connectivity"
                )
                HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                IslandCriticalToggleRow(
                    icon = Icons.Rounded.PhoneInTalk,
                    iconTint = Color(0xFF22C55E),
                    title = "Llamadas y alarmas urgentes",
                    subtitle = "Prioridad máxima sobre cualquier otra notificación",
                    checked = prefs.islandAllowIncomingCalls,
                    onCheckedChange = { enabled -> onUpdatePrefs { it.copy(islandAllowIncomingCalls = enabled) } },
                    testTag = "island_toggle_calls"
                )
            }

            // 5. Explicitly Permitted External Apps Allowlist (Discovered + Curated)
            if (prefs.islandFilterMode == IslandNotificationFilterMode.ALLOWED_ONLY) {
                SectionTitle("Apps permitidas (${prefs.islandAllowedPackages.size})")

                val curatedPackages = remember { DynamicIslandEventManager.curatedApps.map { it.packageName }.toSet() }
                val extraDiscovered = discoveredApps.filter { it.packageName !in curatedPackages }

                if (extraDiscovered.isNotEmpty()) {
                    Text(
                        text = "Detectadas recientemente",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.surfaceAlt.copy(alpha = 0.6f))
                            .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        extraDiscovered.forEachIndexed { index, app ->
                            val isAllowed = app.packageName in prefs.islandAllowedPackages
                            IslandAppAllowToggleRow(
                                name = app.appName,
                                subtitle = if (app.wasBlocked && !isAllowed) "Bloqueada recientemente" else "Detectada en el dispositivo",
                                allowed = isAllowed,
                                onToggle = { enable ->
                                    onUpdatePrefs { current ->
                                        val next = if (enable) {
                                            current.islandAllowedPackages + app.packageName
                                        } else {
                                            current.islandAllowedPackages - app.packageName
                                        }
                                        current.copy(islandAllowedPackages = next)
                                    }
                                },
                                testTag = "island_discovered_${app.packageName}"
                            )
                            if (index < extraDiscovered.lastIndex) {
                                HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.6f))
                        .border(1.dp, palette.border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    DynamicIslandEventManager.curatedApps.forEachIndexed { index, app ->
                        val isAllowed = app.packageName in prefs.islandAllowedPackages
                        IslandAppAllowToggleRow(
                            name = app.displayName,
                            subtitle = app.category,
                            allowed = isAllowed,
                            onToggle = { enable ->
                                onUpdatePrefs { current ->
                                    val next = if (enable) {
                                        current.islandAllowedPackages + app.packageName
                                    } else {
                                        current.islandAllowedPackages - app.packageName
                                    }
                                    current.copy(islandAllowedPackages = next)
                                }
                            },
                            testTag = "island_curated_${app.packageName}"
                        )
                        if (index < DynamicIslandEventManager.curatedApps.lastIndex) {
                            HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun IslandCriticalToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun IslandAppAllowToggleRow(
    name: String,
    subtitle: String,
    allowed: Boolean,
    onToggle: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!allowed) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = allowed,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
            modifier = Modifier.testTag(testTag)
        )
    }
}

