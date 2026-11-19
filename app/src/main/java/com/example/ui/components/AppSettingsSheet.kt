package com.example.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DictionarySourceEntity
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import com.example.data.model.TargetFpsOption
import com.example.engine.DisplayRefreshRateController
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
@OptIn(ExperimentalMaterial3Api::class)
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
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("app_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetHeader(title = "Configuración", onDismiss = onDismiss)
            AppSettingsContent(
                prefs = prefs,
                sources = sources,
                activeTab = activeTab,
                onTabChange = { activeTab = it },
                onUpdatePrefs = onUpdatePrefs,
                onAddSource = onAddSource,
                onToggleSource = onToggleSource,
                onDeleteSource = onDeleteSource,
                onRegenerateCovers = onRegenerateCovers
            )
            Spacer(Modifier.height(16.dp))
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
    onRegenerateCovers: (() -> Unit)? = null
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val mediaState by SystemMediaController.mediaState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Segmented Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

            if (activeTab == 0) {
                // 1. Prominent 7-Theme Visual Grid (ALL 7 themes immediately visible without scrolling!)
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
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Cambiar tema",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.ink
                            )
                        }
                        Text(
                            text = prefs.theme.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                    }

                    val themeRows = ReadingTheme.entries.chunked(4)
                    for (rowThemes in themeRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (theme in rowThemes) {
                                val tp = paletteFor(theme)
                                val selected = prefs.theme == theme
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(tp.background)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) palette.accent else tp.border,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onUpdatePrefs { it.copy(theme = theme) } }
                                        .padding(vertical = 10.dp, horizontal = 4.dp)
                                        .testTag("settings_theme_${theme.name}"),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Aa",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = tp.ink
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) tp.accent else tp.inkMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                            repeat(4 - rowThemes.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

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
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .clickable { SystemMediaController.openNotificationAccessSettings(context) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("media_listener_settings_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = if (mediaState.isNotificationAccessGranted) Color(0xFF1DB954) else palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reproductor en Isla Dinámica",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                            Text(
                                text = if (mediaState.isNotificationAccessGranted) {
                                    "Conectado con Spotify, YouTube y reproductores"
                                } else {
                                    "Activa acceso a notificaciones para ver carátula y canción"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (mediaState.isNotificationAccessGranted) "Activo" else "Activar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (mediaState.isNotificationAccessGranted) Color(0xFF1DB954) else palette.accent
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
            } else {
                // Embedded Dictionary Sources & BYOK AI Engine content
                DictionaryAndAiSettingsContent(
                    prefs = prefs,
                    sources = sources,
                    onUpdatePrefs = onUpdatePrefs,
                    onAddSource = onAddSource,
                    onToggleSource = onToggleSource,
                    onDeleteSource = onDeleteSource
                )
            }
    }
}
