package com.example.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.data.local.PlaylistEntity
import com.example.ui.components.AppSettingsSheet
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TactileIconButton
import com.example.ui.components.TactileProgressBar
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private val PlaylistSwatches = listOf(
    0xFF3E6AE1L,
    0xFF6366F1L,
    0xFF0D9488L,
    0xFFD97706L,
    0xFFE11D48L,
    0xFF7C3AEDL
)

private enum class LibraryViewTab(val label: String) {
    BOOKS("Libros"),
    SERIES("Series")
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenDocument: (Long) -> Unit
) {
    val palette = LocalFolioPalette.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistRefs by viewModel.playlistRefs.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val dictSources by viewModel.dictionarySources.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(LibraryViewTab.BOOKS) }
    var selectedFilter by remember { mutableStateOf("Todos") }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var openedSeriesId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    // Long-press floating book menu & sub-modals
    var optionsBook by remember { mutableStateOf<DocumentEntity?>(null) }
    var bookForPlaylistModal by remember { mutableStateOf<DocumentEntity?>(null) }
    var bookForMetadataModal by remember { mutableStateOf<DocumentEntity?>(null) }
    var pendingCoverBook by remember { mutableStateOf<DocumentEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Back handler when inside a specific Series detail view
    BackHandler(enabled = activeTab == LibraryViewTab.SERIES && openedSeriesId != null) {
        openedSeriesId = null
    }

    // Zero-permission SAF document picker (Play Policy compliant)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importDocument(uri, onImportedOpen = onOpenDocument)
        }
    }

    // Zero-permission Android Photo Picker for custom book cover selection (Play Policy compliant)
    val coverPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = pendingCoverBook
        if (uri != null && target != null) {
            viewModel.updateBookCoverFromUri(target, uri)
        }
        pendingCoverBook = null
    }

    val launchImport = {
        importLauncher.launch(
            arrayOf(
                "application/epub+zip",
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "application/x-fictionbook+xml",
                "*/*"
            )
        )
    }

    val activeSeries = remember(playlists, openedSeriesId) {
        playlists.firstOrNull { it.id == openedSeriesId }
    }

    val docIdsInActiveSeries = remember(playlistRefs, openedSeriesId) {
        if (openedSeriesId == null) null
        else playlistRefs.filter { it.playlistId == openedSeriesId }.map { it.documentId }.toSet()
    }

    val filteredDocs = remember(documents, selectedFilter, searchQuery, activeTab, docIdsInActiveSeries) {
        documents.filter { doc ->
            val inSeries = if (activeTab == LibraryViewTab.SERIES && docIdsInActiveSeries != null) {
                doc.id in docIdsInActiveSeries
            } else {
                true
            }
            val matchesFilter = when (selectedFilter) {
                "Todos" -> true
                "Otros" -> doc.format == "TXT" || doc.format == "FB2"
                else -> doc.format.equals(selectedFilter, ignoreCase = true)
            }
            val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.author.contains(searchQuery, ignoreCase = true) ||
                doc.genre.contains(searchQuery, ignoreCase = true)
            inSeries && matchesFilter && matchesQuery
        }
    }

    val filteredSeries = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists
        else playlists.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val continueDoc = remember(documents) { documents.firstOrNull() }

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Minimalist Luma Header + Subtle "Importar" pill + Settings Gear
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(palette.accent, palette.accentShadow)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoStories,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Luma",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.ink
                        )
                        Text(
                            text = "${documents.size} libros · ${playlists.size} series",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.inkMuted
                        )
                    }

                    // Subtle "Importar" pill with tiny format caption underneath
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(palette.accent)
                                .clickable(enabled = !isImporting, onClick = launchImport)
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                .testTag("import_document_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.onAccent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = palette.onAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Importar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.onAccent
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "EPUB · PDF · DOCX · TXT",
                            fontSize = 8.5.sp,
                            letterSpacing = 0.3.sp,
                            color = palette.inkMuted.copy(alpha = 0.72f)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    TactileIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Configuración y temas",
                        onClick = { showSettingsSheet = true },
                        size = 42.dp,
                        modifier = Modifier.testTag("library_settings_button")
                    )
                }
            }

            // 2. Soft Continue Reading Banner — stays fixed across Libros/Series switches so the layout never jumps!
            if (continueDoc != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueReadingSoftBanner(
                        doc = continueDoc,
                        onClick = { onOpenDocument(continueDoc.id) }
                    )
                }
            }

            // 3. Slim Two-Rectangle Switcher ("Libros" | "Series") ABOVE Search Bar + Square Filter Button
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Two slim rounded rectangles switching between all Books vs Book Series
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceAlt.copy(alpha = 0.75f))
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .padding(4.dp)
                            .testTag("library_mode_switcher"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (tab in LibraryViewTab.entries) {
                            val selected = activeTab == tab
                            val bgColor by animateColorAsState(
                                targetValue = if (selected) palette.accent else Color.Transparent,
                                label = "tab_bg"
                            )
                            val fgColor by animateColorAsState(
                                targetValue = if (selected) palette.onAccent else palette.inkMuted,
                                label = "tab_fg"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgColor)
                                    .clickable {
                                        activeTab = tab
                                        if (tab == LibraryViewTab.BOOKS) openedSeriesId = null
                                    }
                                    .testTag("library_tab_${tab.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = fgColor
                                )
                            }
                        }
                    }

                    // Search Bar + Square Filter Button on the right (replaces permanent format chip strip!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = if (activeTab == LibraryViewTab.SERIES && activeSeries == null) {
                                        "Buscar serie o saga…"
                                    } else {
                                        "Buscar título o autor…"
                                    },
                                    color = palette.inkMuted
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.inkMuted)
                            },
                            trailingIcon = if (searchQuery.isNotBlank()) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Limpiar búsqueda",
                                        tint = palette.inkMuted,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.accent,
                                unfocusedBorderColor = palette.border,
                                focusedContainerColor = palette.surface,
                                unfocusedContainerColor = palette.surface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("library_search_input")
                        )

                        // Square Filter Button (or "+ Nueva" square when on Series overview)
                        if (activeTab == LibraryViewTab.SERIES && activeSeries == null) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.accentSoft)
                                    .border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                    .clickable { showCreatePlaylistDialog = true }
                                    .testTag("create_playlist_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Nueva serie",
                                    tint = palette.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Box {
                                val filterActive = selectedFilter != "Todos"
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (filterActive) palette.accent else palette.surface)
                                        .border(
                                            width = 1.dp,
                                            color = if (filterActive) palette.accent else palette.border,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { filterMenuExpanded = true }
                                        .testTag("library_filter_square_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = "Filtrar por formato",
                                        tint = if (filterActive) palette.onAccent else palette.ink,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = filterMenuExpanded,
                                    onDismissRequest = { filterMenuExpanded = false },
                                    containerColor = palette.surface,
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    val filterOptions = listOf(
                                        "Todos" to "Todos los formatos",
                                        "EPUB" to "Libros EPUB",
                                        "PDF" to "PDF y Manhwa",
                                        "DOCX" to "Documentos DOCX",
                                        "Otros" to "TXT y FB2"
                                    )
                                    for ((key, label) in filterOptions) {
                                        val isChosen = selectedFilter == key
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isChosen) palette.accent else palette.ink
                                                )
                                            },
                                            trailingIcon = if (isChosen) {
                                                {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = palette.accent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else null,
                                            onClick = {
                                                selectedFilter = key
                                                filterMenuExpanded = false
                                            },
                                            modifier = Modifier.testTag("filter_option_$key")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Content Area: Either Series Overview OR Steam-Style Book Cover Grid
            if (activeTab == LibraryViewTab.SERIES && activeSeries == null) {
                // Series / Playlists Overview Grid
                if (filteredSeries.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TactileCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Aún no tienes series o playlists",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.ink
                                )
                                Text(
                                    text = "Crea una serie para agrupar tus sagas, manhwas o lecturas por tema.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkMuted
                                )
                                TactileButton(
                                    onClick = { showCreatePlaylistDialog = true },
                                    cornerRadius = 16.dp,
                                    lipDepth = 2.dp
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Crear primera serie", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(filteredSeries, key = { "series_${it.id}" }) { series ->
                        val memberDocs = remember(documents, playlistRefs, series.id) {
                            val ids = playlistRefs.filter { it.playlistId == series.id }.map { it.documentId }.toSet()
                            documents.filter { it.id in ids }
                        }
                        SeriesSteamCard(
                            series = series,
                            memberDocs = memberDocs,
                            onClick = { openedSeriesId = series.id }
                        )
                    }
                }
            } else {
                // If inside a specific Series, show compact back/manage header
                if (activeTab == LibraryViewTab.SERIES && activeSeries != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(palette.accentSoft)
                                .border(1.dp, palette.accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { openedSeriesId = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Volver a series",
                                    tint = palette.ink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeSeries.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.ink
                                )
                                Text(
                                    text = activeSeries.description.ifBlank { "${filteredDocs.size} libros en esta serie" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        viewModel.deletePlaylist(activeSeries)
                                        openedSeriesId = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Eliminar serie",
                                    tint = palette.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Steam-Style Book Cover Grid
                if (filteredDocs.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TactileCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoStories,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (activeSeries != null) "Esta serie aún está vacía" else "Sin resultados",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.ink
                                )
                                Text(
                                    text = if (activeSeries != null) {
                                        "Mantén pulsado cualquier libro en la pestaña Libros para agregarlo aquí."
                                    } else {
                                        "Ajusta el filtro o pulsa Importar para añadir un nuevo título."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkMuted
                                )
                            }
                        }
                    }
                } else {
                    items(filteredDocs, key = { it.id }) { doc ->
                        SteamCoverBookTile(
                            doc = doc,
                            onOpen = { onOpenDocument(doc.id) },
                            onLongPress = { optionsBook = doc }
                        )
                    }
                }
            }
        }
    }

    // Long-press Floating Book Action Window (matches Luma's soft tactile aesthetic)
    optionsBook?.let { doc ->
        BookOptionsFloatingDialog(
            doc = doc,
            onAddToPlaylist = {
                optionsBook = null
                bookForPlaylistModal = doc
            },
            onChangeCoverPhoto = {
                optionsBook = null
                pendingCoverBook = doc
                coverPhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRegenerateCover = {
                optionsBook = null
                viewModel.regenerateBookCover(doc)
            },
            onEditMetadata = {
                optionsBook = null
                bookForMetadataModal = doc
            },
            onDelete = {
                optionsBook = null
                viewModel.deleteDocument(doc)
            },
            onDismiss = { optionsBook = null }
        )
    }

    // Edit Book Name & Metadata Floating Window
    bookForMetadataModal?.let { doc ->
        EditBookMetadataDialog(
            doc = doc,
            onSave = { title, author, genre, year, synopsis, badgeTag ->
                viewModel.updateBookMetadata(doc, title, author, genre, year, synopsis, badgeTag)
                bookForMetadataModal = null
            },
            onDismiss = { bookForMetadataModal = null }
        )
    }

    // Centralized Settings Sheet (Prominent 7 Themes Grid, System Font, Menu Style, Dock Scale, Auto-Covers, Dictionary & AI API)
    if (showSettingsSheet) {
        AppSettingsSheet(
            prefs = prefs,
            sources = dictSources,
            initialTab = 0,
            onUpdatePrefs = viewModel::updatePreferences,
            onAddSource = viewModel::addDictionarySource,
            onToggleSource = viewModel::toggleDictionarySource,
            onDeleteSource = viewModel::deleteDictionarySource,
            onRegenerateCovers = viewModel::regenerateMissingCovers,
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Create Spotify-Style Book Series / Playlist Dialog
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onCreate = { name, desc, colorHex ->
                viewModel.createPlaylist(name, desc, colorHex)
                showCreatePlaylistDialog = false
            },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }

    // Add/Remove Book from Series / Playlists Dialog
    bookForPlaylistModal?.let { doc ->
        val memberships = playlistRefs.filter { it.documentId == doc.id }.map { it.playlistId }.toSet()
        AssignBookPlaylistsDialog(
            doc = doc,
            playlists = playlists,
            memberPlaylistIds = memberships,
            onToggleMembership = { plId, isMember ->
                viewModel.toggleBookInPlaylist(plId, doc.id, isMember)
            },
            onCreateNewPlaylist = {
                bookForPlaylistModal = null
                showCreatePlaylistDialog = true
            },
            onDismiss = { bookForPlaylistModal = null }
        )
    }
}

@Composable
private fun rememberDecodedCoverBitmap(coverPath: String?): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = coverPath) {
        value = if (coverPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(coverPath)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }.getOrNull()
            }
        }
    }
    return bitmap
}

/**
 * Pure Steam-style vertical cover poster tile (2:3 portrait aspect ratio).
 * Tap to open; Long-press to launch the floating book options window!
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SteamCoverBookTile(
    doc: DocumentEntity,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val coverBitmap = rememberDecodedCoverBitmap(doc.coverImagePath)
    val coverColor = Color(doc.coverColorHex.toInt())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("book_card_${doc.id}"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        val posterShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .shadow(
                    elevation = if (palette.isDark) 4.dp else 8.dp,
                    shape = posterShape,
                    ambientColor = Color.Black.copy(alpha = 0.24f)
                )
                .clip(posterShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(coverColor, coverColor.copy(alpha = 0.65f))
                    )
                )
                .border(1.dp, palette.border.copy(alpha = 0.7f), posterShape)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onLongPress
                )
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = "Carátula de ${doc.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Discreet format / genre pill top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = doc.genre.ifBlank { doc.badgeTag.ifBlank { doc.format } },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sleek reading progress strip along bottom of poster
            if (doc.progressPercent > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(doc.progressPercent.coerceIn(0f, 1f))
                            .height(5.dp)
                            .background(palette.accent)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = doc.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (doc.progressPercent > 0f) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${(doc.progressPercent * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }
            }
        }
    }
}

/**
 * Spotify/Steam inspired Series card showing preview artwork from its books + title + book count.
 */
@Composable
private fun SeriesSteamCard(
    series: PlaylistEntity,
    memberDocs: List<DocumentEntity>,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val accent = Color(series.accentColorHex.toInt())
    val leadCover = rememberDecodedCoverBitmap(memberDocs.firstOrNull()?.coverImagePath)
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_card_${series.id}"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
                .shadow(if (palette.isDark) 4.dp else 7.dp, shape)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(accent, accent.copy(alpha = 0.62f))
                    )
                )
                .border(1.dp, palette.border.copy(alpha = 0.65f), shape)
                .clickable(onClick = onClick)
        ) {
            if (leadCover != null) {
                Image(
                    bitmap = leadCover.asImageBitmap(),
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.66f))
                            )
                        )
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                )
            }

            // Book count badge top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${memberDocs.size} ${if (memberDocs.size == 1) "libro" else "libros"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (series.description.isNotBlank()) {
                    Text(
                        text = series.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Floating rounded window shown when long-pressing any book cover.
 * Provides: Add to Playlist/Series, Change Cover (Gallery Photo Picker / Auto-Regenerate),
 * Rename & Edit Metadata, and Delete Book.
 */
@Composable
private fun BookOptionsFloatingDialog(
    doc: DocumentEntity,
    onAddToPlaylist: () -> Unit,
    onChangeCoverPhoto: () -> Unit,
    onRegenerateCover: () -> Unit,
    onEditMetadata: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val coverBitmap = rememberDecodedCoverBitmap(doc.coverImagePath)
    val coverColor = Color(doc.coverColorHex.toInt())
    val shape = RoundedCornerShape(28.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, shape)
                .clip(shape)
                .background(palette.surface)
                .border(1.dp, palette.border, shape)
                .padding(18.dp)
                .testTag("book_options_floating_dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with miniature cover + title + metadata preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(coverColor)
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(doc.author)
                            if (doc.publisherYear.isNotBlank()) append(" · ${doc.publisherYear}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (doc.genre.isNotBlank()) {
                        Text(
                            text = doc.genre,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = palette.inkMuted, modifier = Modifier.size(18.dp))
                }
            }

            if (doc.synopsis.isNotBlank()) {
                Text(
                    text = doc.synopsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            Spacer(Modifier.height(2.dp))

            FloatingOptionRow(
                icon = Icons.Rounded.QueueMusic,
                title = "Poner en una serie o playlist",
                subtitle = "Agrupar con otros libros o sagas",
                onClick = onAddToPlaylist,
                testTag = "book_option_playlist"
            )
            FloatingOptionRow(
                icon = Icons.Rounded.Image,
                title = "Cambiar carátula",
                subtitle = "Elegir una imagen de tu galería",
                onClick = onChangeCoverPhoto,
                testTag = "book_option_cover_photo"
            )
            FloatingOptionRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = "Generar carátula automática",
                subtitle = "Crear portada con título y autor actual",
                onClick = onRegenerateCover,
                testTag = "book_option_cover_auto"
            )
            FloatingOptionRow(
                icon = Icons.Rounded.Edit,
                title = "Cambiar nombre y metadatos",
                subtitle = "Editar título, autor, género, año y sinopsis",
                onClick = onEditMetadata,
                testTag = "book_option_metadata"
            )
            FloatingOptionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "Eliminar libro",
                subtitle = "Quitar de tu biblioteca local",
                isDestructive = true,
                onClick = onDelete,
                testTag = "book_option_delete"
            )
        }
    }
}

@Composable
private fun FloatingOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalFolioPalette.current
    val fg = if (isDestructive) MaterialTheme.colorScheme.error else palette.ink
    val iconTint = if (isDestructive) MaterialTheme.colorScheme.error else palette.accent
    val bg = if (isDestructive) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
    } else {
        palette.surfaceAlt.copy(alpha = 0.65f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = fg
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDestructive) fg.copy(alpha = 0.78f) else palette.inkMuted
            )
        }
    }
}

@Composable
private fun EditBookMetadataDialog(
    doc: DocumentEntity,
    onSave: (title: String, author: String, genre: String, year: String, synopsis: String, badgeTag: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var title by remember(doc.id) { mutableStateOf(doc.title) }
    var author by remember(doc.id) { mutableStateOf(doc.author) }
    var genre by remember(doc.id) { mutableStateOf(doc.genre) }
    var year by remember(doc.id) { mutableStateOf(doc.publisherYear) }
    var badgeTag by remember(doc.id) { mutableStateOf(doc.badgeTag) }
    var synopsis by remember(doc.id) { mutableStateOf(doc.synopsis) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = RoundedCornerShape(26.dp),
        title = { Text("Nombre y metadatos", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título del libro") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("meta_title_input")
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Autor") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("meta_author_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Género / Saga") },
                        placeholder = { Text("Fantasía, Manhwa…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Año") },
                        placeholder = { Text("2025") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.7f)
                    )
                }
                OutlinedTextField(
                    value = badgeTag,
                    onValueChange = { badgeTag = it },
                    label = { Text("Etiqueta en carátula") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    label = { Text("Sinopsis o notas") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, author, genre, year, synopsis, badgeTag) },
                enabled = title.isNotBlank()
            ) {
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
private fun ContinueReadingSoftBanner(
    doc: DocumentEntity,
    onClick: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val coverBitmap = rememberDecodedCoverBitmap(doc.coverImagePath)
    val coverColor = Color(doc.coverColorHex.toInt())
    val remainingWords = (doc.totalWords * (1f - doc.progressPercent)).roundToInt().coerceAtLeast(0)
    val minsLeft = max(1, remainingWords / 210)

    TactileCard(
        onClick = onClick,
        containerColor = palette.surface,
        borderColor = palette.border,
        shadowColor = palette.borderShadow,
        cornerRadius = 24.dp,
        lipDepth = 2.dp,
        modifier = Modifier.testTag("continue_reading_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 86.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(coverColor)
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "CONTINUAR LEYENDO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.accent,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${doc.author} · ~${minsLeft} min restantes",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TactileProgressBar(
                        progress = doc.progressPercent,
                        height = 6.dp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(doc.progressPercent * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkMuted
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Continuar lectura",
                    tint = palette.onAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onCreate: (name: String, description: String, accentColorHex: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(PlaylistSwatches.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Nueva serie o playlist", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Agrupa sagas, manhwas o colecciones por tema como listas de reproducción.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la serie o playlist") },
                    placeholder = { Text("Ej. Saga Fantasía, Manhwas Favoritos…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (hex in PlaylistSwatches) {
                        val isSel = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex.toInt()))
                                .border(
                                    width = if (isSel) 2.5.dp else 0.dp,
                                    color = if (isSel) palette.ink else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSel) {
                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, description, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text("Crear", fontWeight = FontWeight.Bold, color = palette.accent)
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
private fun AssignBookPlaylistsDialog(
    doc: DocumentEntity,
    playlists: List<PlaylistEntity>,
    memberPlaylistIds: Set<Long>,
    onToggleMembership: (Long, Boolean) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Guardar en series / playlists",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlists.isEmpty()) {
                    Text(
                        text = "Aún no tienes series. Crea una para agrupar tus libros y sagas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                } else {
                    for (pl in playlists) {
                        val isMember = pl.id in memberPlaylistIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.surfaceAlt.copy(alpha = 0.6f))
                                .clickable { onToggleMembership(pl.id, isMember) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isMember,
                                onCheckedChange = { onToggleMembership(pl.id, isMember) },
                                colors = CheckboxDefaults.colors(checkedColor = palette.accent)
                            )
                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.ink,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo", fontWeight = FontWeight.Bold, color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCreateNewPlaylist) {
                Text("+ Nueva serie", color = palette.accent)
            }
        }
    )
}
