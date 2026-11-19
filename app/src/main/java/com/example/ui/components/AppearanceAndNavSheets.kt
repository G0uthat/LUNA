package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BookmarkEntity
import com.example.data.local.HighlightNoteEntity
import com.example.data.model.BookmarkDockSide
import com.example.data.model.PageAnimationMode
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import com.example.data.model.SearchMatch
import com.example.data.model.TextAlignOption
import com.example.data.model.TocEntry
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.paletteFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsSheet(
    prefs: ReaderPreferences,
    isPdf: Boolean,
    onUpdatePrefs: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .testTag("appearance_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetHeader(title = "Texto, Tema y Animación", onDismiss = onDismiss)

            // 1. Reading Themes (7 soft themes including Azul Marino)
            SectionTitle("Tema de lectura (${ReadingTheme.entries.size} estilos)")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (theme in ReadingTheme.entries) {
                    val tp = paletteFor(theme)
                    val selected = prefs.theme == theme
                    Column(
                        modifier = Modifier
                            .width(82.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(tp.background)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) palette.accent else tp.border,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onUpdatePrefs { it.copy(theme = theme) } }
                            .padding(vertical = 12.dp, horizontal = 6.dp)
                            .testTag("theme_option_${theme.name}"),
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
                            color = tp.inkMuted,
                            maxLines = 1
                        )
                    }
                }
            }

            // 2. Page Turn Animations (Libro, Tinta electrónica, Diapositiva, Tarjeta, Desvanecer, Scroll)
            SectionTitle("Animación de paso de página")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (mode in PageAnimationMode.entries) {
                    val selected = prefs.pageAnimation == mode
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) palette.accentSoft else palette.surfaceAlt.copy(alpha = 0.7f))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) palette.accent else palette.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onUpdatePrefs { it.copy(pageAnimation = mode) } }
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                            .testTag("anim_option_${mode.name}")
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) palette.accent else palette.ink
                        )
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkMuted
                        )
                    }
                }
            }

            // 3. Typography (for fluid formats EPUB/DOCX/TXT/FB2)
            if (!isPdf) {
                SectionTitle("Tipografía de lectura")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (font in ReaderFont.entries) {
                        val selected = prefs.font == font
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) palette.accentSoft else palette.surfaceAlt.copy(alpha = 0.7f))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) palette.accent else palette.border,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onUpdatePrefs { it.copy(font = font) } }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("font_option_${font.name}")
                        ) {
                            Text(
                                text = font.displayName,
                                fontFamily = font.toFontFamily(),
                                fontWeight = FontWeight.Bold,
                                color = if (selected) palette.accent else palette.ink,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = font.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.inkMuted
                            )
                        }
                    }
                }

                // Font size
                SliderSettingRow(
                    label = "Tamaño de letra",
                    valueText = "${prefs.fontSizeSp.toInt()} pt",
                    value = prefs.fontSizeSp,
                    range = 14f..28f,
                    onValueChange = { v -> onUpdatePrefs { it.copy(fontSizeSp = v) } }
                )

                // Line height
                SliderSettingRow(
                    label = "Interlineado",
                    valueText = String.format("%.1fx", prefs.lineHeightMultiplier),
                    value = prefs.lineHeightMultiplier,
                    range = 1.2f..2.1f,
                    onValueChange = { v -> onUpdatePrefs { it.copy(lineHeightMultiplier = v) } }
                )

                // Margins
                SliderSettingRow(
                    label = "Márgenes laterales",
                    valueText = "${prefs.horizontalMarginDp.toInt()} dp",
                    value = prefs.horizontalMarginDp,
                    range = 12f..40f,
                    onValueChange = { v -> onUpdatePrefs { it.copy(horizontalMarginDp = v) } }
                )

                // Alignment
                SectionTitle("Alineación de texto")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (align in TextAlignOption.entries) {
                        TactileChip(
                            label = align.displayName,
                            selected = prefs.textAlign == align,
                            onClick = { onUpdatePrefs { it.copy(textAlign = align) } }
                        )
                    }
                }
            } else {
                // PDF OCR Auto Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OCR interactivo automático en PDF",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.ink
                        )
                        Text(
                            text = "Permite seleccionar y traducir palabras sobre imágenes y manhwas",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                    }
                    Switch(
                        checked = prefs.autoOcrPdf,
                        onCheckedChange = { checked -> onUpdatePrefs { it.copy(autoOcrPdf = checked) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent)
                    )
                }
            }

            // Minimal progress footer toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceAlt)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pie de progreso discreto",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.ink
                    )
                    Text(
                        text = "Porcentaje leído y tiempo restante estimado",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                }
                Switch(
                    checked = prefs.showMinimalFooter,
                    onCheckedChange = { checked -> onUpdatePrefs { it.copy(showMinimalFooter = checked) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent)
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocBottomSheet(
    toc: List<TocEntry>,
    currentIndex: Int,
    onSelectEntry: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        contentColor = palette.ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("toc_sheet")
        ) {
            SheetHeader(title = "Tabla de contenidos", onDismiss = onDismiss)
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(toc) { entry ->
                    val isCurrent = entry.targetIndex == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isCurrent) palette.accentSoft else palette.surfaceAlt.copy(alpha = 0.6f))
                            .border(
                                1.5.dp,
                                if (isCurrent) palette.accent else palette.border,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSelectEntry(entry.targetIndex)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) palette.accent else palette.inkMuted.copy(alpha = 0.4f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) palette.accent else palette.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InBookSearchSheet(
    query: String,
    matches: List<SearchMatch>,
    onQueryChange: (String) -> Unit,
    onSelectMatch: (SearchMatch) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        contentColor = palette.ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("search_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(title = "Buscar en el libro", onDismiss = onDismiss)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Escribe una palabra o frase…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("inbook_search_input")
            )
            if (query.isNotBlank() && matches.isEmpty()) {
                Text(
                    text = "No se encontraron coincidencias para \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(matches) { match ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelectMatch(match)
                                    onDismiss()
                                }
                                .padding(14.dp)
                        ) {
                            Text(
                                text = match.chapterTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.accent
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = match.snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.ink,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsAndBookmarksSheet(
    highlights: List<HighlightNoteEntity>,
    bookmarks: List<BookmarkEntity>,
    onNavigateToUnit: (Int) -> Unit,
    onDeleteHighlight: (Long) -> Unit,
    onEditHighlightNote: (HighlightNoteEntity) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var tab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        contentColor = palette.ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("annotations_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(title = "Notas y Marcadores", onDismiss = onDismiss)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TactileChip(
                    label = "Subrayados (${highlights.size})",
                    selected = tab == 0,
                    onClick = { tab = 0 }
                )
                TactileChip(
                    label = "Marcadores (${bookmarks.size})",
                    selected = tab == 1,
                    onClick = { tab = 1 }
                )
            }

            if (tab == 0) {
                if (highlights.isEmpty()) {
                    Text(
                        text = "Mantén pulsada una palabra mientras lees para subrayar fragmentos y guardar notas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(highlights, key = { it.id }) { hl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.surfaceAlt.copy(alpha = 0.7f))
                                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                                    .clickable {
                                        onNavigateToUnit(hl.chapterIndex)
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(hl.colorArgb.toInt()))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "«${hl.quotedText}»",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.ink,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (hl.noteText.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = hl.noteText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.accent
                                        )
                                    }
                                }
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onEditHighlightNote(hl) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.EditNote,
                                            contentDescription = "Editar nota",
                                            tint = palette.ink,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onDeleteHighlight(hl.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Eliminar subrayado",
                                            tint = palette.inkMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (bookmarks.isEmpty()) {
                    Text(
                        text = "Aún no tienes marcapáginas en este libro. Usa el marcapáginas lateral para guardar tu ubicación.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.surfaceAlt.copy(alpha = 0.7f))
                                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                                    .clickable {
                                        onNavigateToUnit(bm.unitIndex)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = bm.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.ink,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onDeleteBookmark(bm.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Eliminar marcador",
                                        tint = palette.inkMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun NoteEditorDialog(
    quotedText: String,
    initialNote: String,
    initialColorArgb: Long,
    onSave: (noteText: String, colorArgb: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var note by remember { mutableStateOf(initialNote) }
    var colorArgb by remember { mutableLongStateOf(initialColorArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Nota de lectura", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "«${quotedText.take(120)}»",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((color, argb) in HighlightSwatches) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (colorArgb == argb) 3.dp else 1.dp,
                                    color = if (colorArgb == argb) palette.ink else palette.border,
                                    shape = CircleShape
                                )
                                .clickable { colorArgb = argb }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Escribe tu reflexión o resumen…") },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("note_editor_input")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note.trim(), colorArgb) }) {
                Text("Guardar", fontWeight = FontWeight.Bold, color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = palette.inkMuted)
            }
        }
    )
}

@Composable
internal fun SheetHeader(title: String, onDismiss: () -> Unit) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = palette.ink,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceAlt)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Cerrar",
                tint = palette.ink
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    val palette = LocalFolioPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = palette.inkMuted
    )
}

@Composable
private fun SliderSettingRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val palette = LocalFolioPalette.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = palette.ink)
            Text(valueText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = palette.accent)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent
            )
        )
    }
}
