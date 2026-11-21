package com.example.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.HelpOutline
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
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
import com.example.engine.SocialRepository
import com.example.ui.components.AccountProfileDialog
import com.example.ui.components.AppSettingsSheet
import com.example.ui.components.FriendsChatDialog
import com.example.ui.components.LibraryFilterDropdownOverlay
import com.example.ui.components.LumaActionFeedbackOverlay
import com.example.ui.components.LumaAppGuideDialog
import com.example.ui.components.LumaCapsuleOptionRow
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.components.LumaInteractiveTourOverlay
import com.example.ui.components.LumaPermissionsOnboardingDialog
import com.example.ui.components.LumaWideWordmark
import com.example.ui.components.ProfileDropdownCapsuleOverlay
import com.example.ui.components.QuickProfileSetupDialog
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TactileProgressBar
import com.example.ui.components.TourTargetBounds
import com.example.ui.components.UserAvatarCircle
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
    val context = LocalContext.current
    val socialRepo = remember(context) { SocialRepository.getInstance(context) }
    val userProfile by socialRepo.profile.collectAsStateWithLifecycle()
    val friendsList by socialRepo.friends.collectAsStateWithLifecycle()
    val totalUnreadMessages = remember(friendsList) { friendsList.sumOf { it.unreadCount } }

    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistRefs by viewModel.playlistRefs.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val preferencesLoaded by viewModel.preferencesLoaded.collectAsStateWithLifecycle()
    val dictSources by viewModel.dictionarySources.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(LibraryViewTab.BOOKS) }
    var selectedFilter by remember { mutableStateOf("Todos") }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var openedSeriesId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var showProfileMenu by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showFriendsDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAppGuide by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    // First-launch Permissions popup -> XLNR-style interactive spotlight tour flow
    var showPermissionsOnboarding by remember { mutableStateOf(false) }
    var showInteractiveTour by remember { mutableStateOf(false) }
    var onboardingAutoChecked by remember { mutableStateOf(false) }

    LaunchedEffect(preferencesLoaded, prefs.hasCompletedOnboarding) {
        if (preferencesLoaded && !onboardingAutoChecked) {
            onboardingAutoChecked = true
            if (!prefs.hasCompletedOnboarding) {
                showPermissionsOnboarding = true
            }
        }
    }

    // Measured root coordinates & live spotlight target bounds
    var rootLeftWindowPx by remember { mutableFloatStateOf(0f) }
    var rootTopWindowPx by remember { mutableFloatStateOf(0f) }
    var profileSphereTopInRootPx by remember { mutableFloatStateOf(0f) }
    var filterButtonBottomInRootPx by remember { mutableFloatStateOf(0f) }
    var tourTargets by remember { mutableStateOf(TourTargetBounds()) }

    fun Rect.toRootRelative(): Rect = Rect(
        left = (left - rootLeftWindowPx).coerceAtLeast(0f),
        top = (top - rootTopWindowPx).coerceAtLeast(0f),
        right = (right - rootLeftWindowPx).coerceAtLeast(0f),
        bottom = (bottom - rootTopWindowPx).coerceAtLeast(0f)
    )

    // Long-press floating book menu & sub-modals
    var optionsBook by remember { mutableStateOf<DocumentEntity?>(null) }
    var bookForPlaylistModal by remember { mutableStateOf<DocumentEntity?>(null) }
    var bookForMetadataModal by remember { mutableStateOf<DocumentEntity?>(null) }
    var pendingCoverBook by remember { mutableStateOf<DocumentEntity?>(null) }

    // Soft 18% frosted blur on library content whenever ANY floating dropdown menu is open
    val anyFloatingMenuOpen = showProfileMenu || filterMenuExpanded ||
        showAccountDialog || showFriendsDialog || showSettingsSheet || showAppGuide ||
        showPermissionsOnboarding || optionsBook != null
    val backgroundBlurRadius by animateDpAsState(
        targetValue = if (anyFloatingMenuOpen) 6.dp else 0.dp,
        animationSpec = tween(220),
        label = "library_menu_blur"
    )

    // Back handler when interactive spotlight tour is active
    BackHandler(enabled = showInteractiveTour) {
        showInteractiveTour = false
        viewModel.updatePreferences { it.copy(hasCompletedOnboarding = true) }
    }

    // Back handler when floating profile or filter dropdown capsule is open
    BackHandler(enabled = !showInteractiveTour && (showProfileMenu || filterMenuExpanded)) {
        showProfileMenu = false
        filterMenuExpanded = false
    }

    // Back handler when inside a specific Series detail view
    BackHandler(enabled = !showInteractiveTour && !showProfileMenu && !filterMenuExpanded && activeTab == LibraryViewTab.SERIES && openedSeriesId != null) {
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

    // Zero-permission Android Photo Picker for first-launch quick profile photo
    val quickAvatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            socialRepo.updateAvatarFromUri(uri)
        }
    }

    val launchImport = {
        importLauncher.launch(
            arrayOf(
                "application/epub+zip",
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
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
        containerColor = palette.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    rootLeftWindowPx = pos.x
                    rootTopWindowPx = pos.y
                }
        ) {
            val gridMod = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .let { base -> if (backgroundBlurRadius > 0.dp) base.blur(radius = backgroundBlurRadius) else base }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = gridMod,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Minimalist Full-Span "LUMA" Wordmark + Interactive Profile Sphere
                item(key = "lib_header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LumaWideWordmark(
                            bookCount = documents.size,
                            seriesCount = playlists.size,
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { coords ->
                                    tourTargets = tourTargets.copy(
                                        headerRect = coords.boundsInWindow().toRootRelative()
                                    )
                                }
                        )

                        Spacer(Modifier.width(8.dp))

                        // Discreet 48.dp quick-access Guide button for beginners
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .onGloballyPositioned { coords ->
                                    tourTargets = tourTargets.copy(
                                        guideButtonRect = coords.boundsInWindow().toRootRelative()
                                    )
                                }
                                .clip(CircleShape)
                                .clickable { showAppGuide = true }
                                .testTag("library_guide_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(palette.surfaceAlt.copy(alpha = 0.85f))
                                    .border(1.dp, palette.border, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HelpOutline,
                                    contentDescription = "Abrir guía de uso de Luma",
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        // Profile Sphere: measured in root coords so the dropdown capsule aligns pixel-exact!
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .onGloballyPositioned { coords ->
                                    profileSphereTopInRootPx = (coords.positionInWindow().y - rootTopWindowPx)
                                        .coerceAtLeast(0f)
                                    tourTargets = tourTargets.copy(
                                        profileSphereRect = coords.boundsInWindow().toRootRelative()
                                    )
                                }
                                .clip(CircleShape)
                                .clickable { showProfileMenu = !showProfileMenu }
                                .testTag("library_account_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatarCircle(
                                displayName = userProfile.username.ifBlank { "C" },
                                avatarPath = userProfile.avatarPath,
                                avatarBase64 = "",
                                size = 44.dp,
                                showOnlineDot = userProfile.privacy.showOnlineStatus,
                                unreadBadge = totalUnreadMessages
                            )
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 2.4.dp,
                                    color = palette.accent
                                )
                            }
                        }
                    }
                }

            // 2. Soft Continue Reading Banner — stays fixed across Libros/Series switches so the layout never jumps!
            if (continueDoc != null) {
                item(key = "lib_continue_reading", span = { GridItemSpan(maxLineSpan) }, contentType = "continue") {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            tourTargets = tourTargets.copy(
                                continueRect = coords.boundsInWindow().toRootRelative()
                            )
                        }
                    ) {
                        ContinueReadingSoftBanner(
                            doc = continueDoc,
                            onClick = { onOpenDocument(continueDoc.id) }
                        )
                    }
                }
            }

            // 3. Slim Two-Rectangle Switcher ("Libros" | "Series") ABOVE Search Bar + Square Filter Button
            item(key = "lib_tabs_and_search", span = { GridItemSpan(maxLineSpan) }, contentType = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Two slim rounded rectangles switching cleanly in lockstep between Books and Series
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                tourTargets = tourTargets.copy(
                                    tabsRect = coords.boundsInWindow().toRootRelative()
                                )
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceAlt.copy(alpha = 0.75f))
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .padding(4.dp)
                            .testTag("library_mode_switcher"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (tab in LibraryViewTab.entries) {
                            val selected = activeTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) palette.accent else Color.Transparent)
                                    .clickable {
                                        activeTab = tab
                                        openedSeriesId = null
                                    }
                                    .testTag("library_tab_${tab.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) palette.onAccent else palette.inkMuted
                                )
                            }
                        }
                    }

                    // Search Bar + Square Filter Button on the right (replaces permanent format chip strip!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                tourTargets = tourTargets.copy(
                                    searchFilterRect = coords.boundsInWindow().toRootRelative()
                                )
                            },
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
                            val filterActive = selectedFilter != "Todos"
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .onGloballyPositioned { coords ->
                                        filterButtonBottomInRootPx =
                                            (coords.positionInWindow().y + coords.size.height - rootTopWindowPx)
                                                .coerceAtLeast(0f)
                                    }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (filterActive) palette.accent else palette.surface)
                                    .border(
                                        width = 1.dp,
                                        color = if (filterActive) palette.accent else palette.border,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { filterMenuExpanded = !filterMenuExpanded }
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
                        }
                    }
                }
            }

            // 4. Content Area: Either Series Overview OR Steam-Style Book Cover Grid
            if (activeTab == LibraryViewTab.SERIES && activeSeries == null) {
                // Series / Playlists Overview Grid
                if (filteredSeries.isEmpty()) {
                    item(key = "lib_series_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_series") {
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
                    items(
                        items = filteredSeries,
                        key = { "series_${it.id}" },
                        contentType = { "series_card" }
                    ) { series ->
                        val memberDocs = remember(documents, playlistRefs, series.id) {
                            val ids = playlistRefs.filter { it.playlistId == series.id }.map { it.documentId }.toSet()
                            documents.filter { it.id in ids }
                        }
                        Box(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(190),
                                placementSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        ) {
                            SeriesSteamCard(
                                series = series,
                                memberDocs = memberDocs,
                                onClick = { openedSeriesId = series.id }
                            )
                        }
                    }
                }
            } else {
                // If inside a specific Series, show compact back/manage header
                if (activeTab == LibraryViewTab.SERIES && activeSeries != null) {
                    item(key = "lib_series_detail_header", span = { GridItemSpan(maxLineSpan) }, contentType = "series_header") {
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
                    item(key = "lib_books_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_books") {
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
                    items(
                        items = filteredDocs,
                        key = { "book_${it.id}" },
                        contentType = { "book_card" }
                    ) { doc ->
                        Box(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(190),
                                placementSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        ) {
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

            // Floating Vertical Profile Capsule Menu aligned pixel-exact with the profile sphere
            ProfileDropdownCapsuleOverlay(
                visible = showProfileMenu,
                anchorTopPx = profileSphereTopInRootPx,
                userProfile = userProfile,
                unreadMessages = totalUnreadMessages,
                isImporting = isImporting,
                onDismiss = { showProfileMenu = false },
                onOpenAccount = { showAccountDialog = true },
                onOpenFriends = { showFriendsDialog = true },
                onOpenSettings = { showSettingsSheet = true },
                onOpenGuide = { showAppGuide = true },
                onImportDocument = launchImport
            )

            // Floating Vertical Filter Capsule Menu matching the exact profile dropdown aesthetic & animation
            LibraryFilterDropdownOverlay(
                visible = filterMenuExpanded,
                anchorBottomPx = filterButtonBottomInRootPx,
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilter = it },
                onDismiss = { filterMenuExpanded = false }
            )

            // Tactile micro-animations replacing clunky Android Snackbars
            LumaActionFeedbackOverlay(
                event = feedbackEvent,
                onConsumed = viewModel::clearFeedbackEvent
            )

            // XLNR-style interactive live-UI spotlight coach-mark tour (launched right after Permissions modal)
            LumaInteractiveTourOverlay(
                visible = showInteractiveTour,
                targets = tourTargets,
                onFinish = {
                    showInteractiveTour = false
                    viewModel.updatePreferences { it.copy(hasCompletedOnboarding = true) }
                }
            )
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

    // 1. Standalone Account Configuration Dialog (ONLY Profile, Avatar, Peer Code & Privacy)
    if (showAccountDialog) {
        AccountProfileDialog(
            socialRepo = socialRepo,
            currentOrRecentBookTitle = continueDoc?.title.orEmpty(),
            currentOrRecentBookProgressPct = ((continueDoc?.progressPercent ?: 0f) * 100f).roundToInt(),
            onDismiss = { showAccountDialog = false }
        )
    }

    // 2. Standalone Friends & Simple P2P Chat Dialog (ONLY Friends & Chat)
    if (showFriendsDialog) {
        FriendsChatDialog(
            socialRepo = socialRepo,
            currentOrRecentBookTitle = continueDoc?.title.orEmpty(),
            onDismiss = { showFriendsDialog = false }
        )
    }

    // 3. Standalone App Settings Sheet (Themes, Typography, Reader, Dictionary, Credits & Tour Replay)
    if (showSettingsSheet) {
        AppSettingsSheet(
            prefs = prefs,
            sources = dictSources,
            onUpdatePrefs = viewModel::updatePreferences,
            onAddSource = viewModel::addDictionarySource,
            onToggleSource = viewModel::toggleDictionarySource,
            onDeleteSource = viewModel::deleteDictionarySource,
            onRegenerateCovers = viewModel::regenerateMissingCovers,
            onReplayOnboarding = {
                showSettingsSheet = false
                showPermissionsOnboarding = true
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // 4. Interactive Illustrated Step-by-Step Guide
    if (showAppGuide) {
        LumaAppGuideDialog(
            documents = documents,
            onOpenSampleDocument = { docId ->
                showAppGuide = false
                viewModel.updatePreferences { it.copy(hasSeenGuide = true) }
                onOpenDocument(docId)
            },
            onDismiss = {
                showAppGuide = false
                viewModel.updatePreferences { it.copy(hasSeenGuide = true) }
            }
        )
    }

    // 5. Upfront Permissions Explanation Modal -> seamlessly hands off to XLNR-style Interactive Tour
    if (showPermissionsOnboarding) {
        LumaPermissionsOnboardingDialog(
            onStartTour = {
                showPermissionsOnboarding = false
                showInteractiveTour = true
            },
            onSkipAll = {
                showPermissionsOnboarding = false
                showInteractiveTour = false
                viewModel.updatePreferences { it.copy(hasCompletedOnboarding = true) }
            }
        )
    }

    // Optional first-launch local profile setup (only after onboarding & spotlight tour have completed)
    if (preferencesLoaded &&
        prefs.hasCompletedOnboarding &&
        !showPermissionsOnboarding &&
        !showInteractiveTour &&
        !userProfile.profileConfigured &&
        !showAccountDialog &&
        !showFriendsDialog &&
        !showSettingsSheet &&
        !showAppGuide
    ) {
        QuickProfileSetupDialog(
            profile = userProfile,
            onPickAvatar = {
                quickAvatarPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSave = { chosenName -> socialRepo.updateProfileName(chosenName) },
            onSkip = { socialRepo.markProfilePromptDismissed() }
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

private object CoverBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val bmp = cache.get(path)
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    fun put(path: String, bitmap: Bitmap) {
        cache.put(path, bitmap)
    }
}

@Composable
private fun rememberDecodedCoverBitmap(coverPath: String?): Bitmap? {
    val cached = remember(coverPath) { CoverBitmapCache.get(coverPath) }
    val bitmap by produceState<Bitmap?>(initialValue = cached, key1 = coverPath) {
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = if (coverPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(coverPath)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)?.also { decoded ->
                            CoverBitmapCache.put(coverPath, decoded)
                        }
                    } else {
                        null
                    }
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
    val coverColorDeep = lerp(coverColor, Color.Black, 0.36f)

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
                        colors = listOf(coverColor, coverColorDeep)
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
    val accentDeep = lerp(accent, Color.Black, 0.42f)
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
                        colors = listOf(accent, accentDeep)
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

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.2f),
        maxWidth = 360.dp,
        maxHeightFraction = 0.82f,
        testTag = "book_options_floating_dialog"
    ) { dismissAnimated ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with miniature cover + title + metadata preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 64.dp)
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable(onClick = dismissAnimated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = palette.ink, modifier = Modifier.size(18.dp))
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceAlt.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(horizontal = 6.dp),
                thickness = 1.dp,
                color = palette.border.copy(alpha = 0.6f)
            )

            LumaCapsuleOptionRow(
                icon = Icons.Rounded.QueueMusic,
                title = "PONER EN SERIE",
                subtitle = "Agrupar con otros libros o sagas",
                iconOnRight = true,
                onClick = onAddToPlaylist,
                testTag = "book_option_playlist"
            )
            LumaCapsuleOptionRow(
                icon = Icons.Rounded.Image,
                title = "CAMBIAR CARÁTULA",
                subtitle = "Elegir imagen de tu galería",
                iconOnRight = true,
                onClick = onChangeCoverPhoto,
                testTag = "book_option_cover_photo"
            )
            LumaCapsuleOptionRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = "PORTADA AUTOMÁTICA",
                subtitle = "Crear con título y autor actual",
                iconOnRight = true,
                onClick = onRegenerateCover,
                testTag = "book_option_cover_auto"
            )
            LumaCapsuleOptionRow(
                icon = Icons.Rounded.Edit,
                title = "EDITAR METADATOS",
                subtitle = "Título, autor, género y sinopsis",
                iconOnRight = true,
                onClick = onEditMetadata,
                testTag = "book_option_metadata"
            )
            LumaCapsuleOptionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "ELIMINAR LIBRO",
                subtitle = "Quitar de tu biblioteca local",
                isDestructive = true,
                iconOnRight = true,
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
