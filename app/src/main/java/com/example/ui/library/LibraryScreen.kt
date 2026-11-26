package com.example.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LibraryDocumentItem
import com.example.data.local.PlaylistEntity
import com.example.engine.SocialRepository
import com.example.ui.components.AccountProfileDialog
import com.example.ui.components.AppSettingsSheet
import com.example.ui.components.FriendsChatDialog
import com.example.ui.components.LibraryFilterDropdownOverlay
import com.example.ui.components.LumaActionFeedbackOverlay
import com.example.ui.components.LumaAppGuideDialog
import com.example.ui.components.LumaInteractiveTourOverlay
import com.example.ui.components.LumaPermissionsOnboardingDialog
import com.example.ui.components.LumaWideWordmark
import com.example.ui.components.ProfileDropdownCapsuleOverlay
import com.example.ui.components.QuickProfileSetupDialog
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.components.TourTargetBounds
import com.example.ui.components.UserAvatarCircle
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.LocalLumaMotion
import android.net.Uri
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.example.drive.DriveBreadcrumb
import com.example.drive.DriveBrowserHeaderBlock
import com.example.drive.DriveConflictResolutionDialog
import com.example.drive.DriveDestinationPickerDialog
import com.example.drive.DriveDownloadConfirmDialog
import com.example.drive.DriveEmptySetupCard
import com.example.drive.DriveFileContextMenuDialog
import com.example.drive.DriveFileEntity
import com.example.drive.DriveFileGridTile
import com.example.drive.DriveFileKind
import com.example.drive.DriveFileListRow
import com.example.drive.DriveFilterKind
import com.example.drive.DriveOpenTarget
import com.example.drive.DriveRepository
import com.example.drive.DriveSortOrder
import com.example.drive.DriveSourceEntity
import com.example.drive.DriveSyncMode
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.data.NotebookRepository
import com.example.notebook.data.NotebookSummaryItem
import com.example.notebook.ui.CreateNotebookDialog
import com.example.notebook.ui.EmptyNotebooksCard
import com.example.notebook.ui.NotebookGridCard
import com.example.notebook.ui.NotebookOptionsDialog
import com.example.core.cloud.CloudAccountManager
import com.example.core.cloud.CloudMyAccountDialog
import com.example.core.cloud.CloudSyncButtonState
import com.example.ui.components.SETTINGS_TAB_DRIVE_SOURCES
import com.example.ui.components.SETTINGS_TAB_GENERAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private enum class LibraryViewTab(val label: String) {
    BOOKS("Libros"),
    SERIES("Series"),
    NOTEBOOKS("Cuadernos"),
    DRIVE("Drive")
}

private val SwitcherOuterShape = RoundedCornerShape(14.dp)
private val SwitcherTabShape = RoundedCornerShape(10.dp)
private val SearchInputShape = RoundedCornerShape(16.dp)
private val SeriesHeaderShape = RoundedCornerShape(18.dp)

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    notebookRepositoryProvider: () -> NotebookRepository,
    onOpenDocument: (Long) -> Unit,
    onOpenNotebook: (Long) -> Unit
) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val socialRepo = remember(context) { SocialRepository.getInstance(context) }

    // Narrow state slices: avoid collecting full ReaderPreferences or dictionarySources at Library root
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistRefs by viewModel.playlistRefs.collectAsStateWithLifecycle()
    val preferencesLoaded by viewModel.preferencesLoaded.collectAsStateWithLifecycle()
    val hasCompletedOnboardingFlow = remember(viewModel) {
        viewModel.preferences.map { it.hasCompletedOnboarding }.distinctUntilChanged()
    }
    val hasCompletedOnboarding by hasCompletedOnboardingFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.preferences.value.hasCompletedOnboarding
    )
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    // Signal Time-to-Full-Display (TTFD) for ART Baseline Profile & Macrobenchmark accuracy
    ReportDrawnWhen { preferencesLoaded }

    var activeTab by remember { mutableStateOf(LibraryViewTab.BOOKS) }
    var selectedFilter by remember { mutableStateOf("Todos") }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var openedSeriesId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var showProfileMenu by remember { mutableStateOf(false) }
    var showCloudMyAccountDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showFriendsDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableIntStateOf(SETTINGS_TAB_GENERAL) }
    var showAppGuide by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreateNotebookDialog by remember { mutableStateOf(false) }
    var optionsNotebook by remember { mutableStateOf<NotebookSummaryItem?>(null) }
    var isCreatingNotebook by remember { mutableStateOf(false) }

    // Shared cloud account & sync state coordinator (/core/cloud)
    val cloudAccountManager = remember(context) { CloudAccountManager.getInstance(context) }
    val cloudUiState by cloudAccountManager.uiState.collectAsStateWithLifecycle()

    // Lazy user-owned Google Drive v3 BYOC coordinator
    val driveRepo = remember(context) { DriveRepository.getInstance(context) }
    val driveSources by driveRepo.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val allDriveFiles by driveRepo.allFiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val driveSyncQueue by driveRepo.syncQueue.collectAsStateWithLifecycle(initialValue = emptyList())
    val driveConflict by driveRepo.activeConflict.collectAsStateWithLifecycle()
    val driveHasMoreByFolder by driveRepo.hasMoreByFolder.collectAsStateWithLifecycle()

    val enabledDriveSources = remember(driveSources) { driveSources.filter { it.enabled } }
    var selectedDriveSourceId by remember { mutableStateOf<Long?>(null) }
    var driveBreadcrumbs by remember { mutableStateOf<List<DriveBreadcrumb>>(emptyList()) }
    var driveFilterKind by remember { mutableStateOf(DriveFilterKind.ALL) }
    var driveSortOrder by remember { mutableStateOf(DriveSortOrder.DATE_DESC) }
    var driveIsGridView by remember { mutableStateOf(true) }
    var pendingDriveDownloadFile by remember { mutableStateOf<DriveFileEntity?>(null) }
    var optionsDriveFile by remember { mutableStateOf<DriveFileEntity?>(null) }
    var pendingUploadDocumentId by remember { mutableStateOf<Long?>(null) }
    var pendingUploadNotebookId by remember { mutableStateOf<Long?>(null) }

    fun openSettingsAt(tab: Int) {
        settingsInitialTab = tab
        showSettingsSheet = true
    }

    LaunchedEffect(activeTab, enabledDriveSources.size) {
        if (activeTab == LibraryViewTab.DRIVE && enabledDriveSources.isNotEmpty() && allDriveFiles.isEmpty()) {
            driveRepo.refreshAllEnabledSources()
        }
    }

    // Strictly lazy notebook list: NotebookRepository is NOT instantiated until user enters Cuadernos tab!
    var notebooksActivated by remember { mutableStateOf(false) }
    LaunchedEffect(activeTab) {
        if (activeTab == LibraryViewTab.NOTEBOOKS) {
            notebooksActivated = true
        }
    }
    var notebooks by remember { mutableStateOf<List<NotebookSummaryItem>>(emptyList()) }
    LaunchedEffect(notebooksActivated) {
        if (!notebooksActivated) return@LaunchedEffect
        notebookRepositoryProvider().notebookSummaries.collect { list ->
            notebooks = list
        }
    }

    var showPermissionsOnboarding by remember { mutableStateOf(false) }
    var showInteractiveTour by remember { mutableStateOf(false) }
    var onboardingAutoChecked by remember { mutableStateOf(false) }

    LaunchedEffect(preferencesLoaded, hasCompletedOnboarding) {
        if (preferencesLoaded && !onboardingAutoChecked) {
            onboardingAutoChecked = true
            if (!hasCompletedOnboarding) {
                showPermissionsOnboarding = true
            }
        }
    }

    // Measured root coordinates & live spotlight target bounds
    var rootLeftWindowPx by remember { mutableFloatStateOf(0f) }
    var rootTopWindowPx by remember { mutableFloatStateOf(0f) }
    var profileSphereTopInRootPx by remember { mutableFloatStateOf(0f) }
    var profileSphereRightInRootPx by remember { mutableFloatStateOf(0f) }
    var filterButtonBottomInRootPx by remember { mutableFloatStateOf(0f) }
    var tourTargets by remember { mutableStateOf(TourTargetBounds()) }

    fun Rect.toRootRelative(): Rect = Rect(
        left = (left - rootLeftWindowPx).coerceAtLeast(0f),
        top = (top - rootTopWindowPx).coerceAtLeast(0f),
        right = (right - rootLeftWindowPx).coerceAtLeast(0f),
        bottom = (bottom - rootTopWindowPx).coerceAtLeast(0f)
    )

    // Long-press floating book menu & sub-modals
    var optionsBook by remember { mutableStateOf<LibraryDocumentItem?>(null) }
    var bookForPlaylistModal by remember { mutableStateOf<LibraryDocumentItem?>(null) }
    var bookForMetadataModal by remember { mutableStateOf<LibraryDocumentItem?>(null) }
    var pendingCoverBook by remember { mutableStateOf<LibraryDocumentItem?>(null) }

    val anyFloatingMenuOpen by remember {
        derivedStateOf {
            showProfileMenu || showCloudMyAccountDialog || filterMenuExpanded || showAccountDialog || showFriendsDialog ||
                showSettingsSheet || showAppGuide || showPermissionsOnboarding ||
                optionsBook != null || showCreateNotebookDialog || optionsNotebook != null ||
                pendingDriveDownloadFile != null || optionsDriveFile != null ||
                driveConflict != null || pendingUploadDocumentId != null || pendingUploadNotebookId != null
        }
    }

    BackHandler(enabled = showInteractiveTour) {
        showInteractiveTour = false
        viewModel.updatePreferences { it.copy(hasCompletedOnboarding = true) }
    }
    BackHandler(enabled = !showInteractiveTour && (showProfileMenu || filterMenuExpanded)) {
        showProfileMenu = false
        filterMenuExpanded = false
    }
    BackHandler(
        enabled = !showInteractiveTour && !showProfileMenu && !filterMenuExpanded &&
            activeTab == LibraryViewTab.SERIES && openedSeriesId != null
    ) {
        openedSeriesId = null
    }
    BackHandler(
        enabled = !showInteractiveTour && !showProfileMenu && !filterMenuExpanded &&
            activeTab == LibraryViewTab.DRIVE && driveBreadcrumbs.size > 1
    ) {
        driveBreadcrumbs = driveBreadcrumbs.dropLast(1)
    }

    // Zero-permission SAF document picker (Play Policy compliant)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importDocument(uri, onImportedOpen = onOpenDocument)
        }
    }

    // Zero-permission Android Photo Picker for custom book cover selection
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

    // Zero-permission SAF PDF picker for creating a Study Notebook over PDF pages
    val notebookPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && !isCreatingNotebook) {
            isCreatingNotebook = true
            notebooksActivated = true
            coroutineScope.launch {
                try {
                    val info = withContext(Dispatchers.IO) { viewModel.resolveExternalFileInfo(uri) }
                    val tempFile = withContext(Dispatchers.IO) {
                        val tmp = File(context.cacheDir, "nb_pdf_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tmp).use { out -> input.copyTo(out) }
                        }
                        tmp
                    }
                    val title = info.displayName.substringBeforeLast('.').ifBlank { "Apuntes PDF" }
                    val newNbId = notebookRepositoryProvider().createNotebookFromPdf(title, tempFile)
                    runCatching { tempFile.delete() }
                    onOpenNotebook(newNbId)
                } finally {
                    isCreatingNotebook = false
                }
            }
        }
    }

    // Zero-permission Android Photo Picker for creating a Study Notebook over an image/slide
    val notebookImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && !isCreatingNotebook) {
            isCreatingNotebook = true
            notebooksActivated = true
            coroutineScope.launch {
                try {
                    val info = withContext(Dispatchers.IO) { viewModel.resolveExternalFileInfo(uri) }
                    val title = info.displayName.substringBeforeLast('.').ifBlank { "Apunte visual" }
                    val newNbId = notebookRepositoryProvider().createNotebookFromImageUri(title, uri)
                    onOpenNotebook(newNbId)
                } finally {
                    isCreatingNotebook = false
                }
            }
        }
    }

    val launchNotebookPdfPicker = remember(notebookPdfPickerLauncher) {
        { notebookPdfPickerLauncher.launch(arrayOf("application/pdf")) }
    }
    val launchNotebookImagePicker = remember(notebookImagePickerLauncher) {
        {
            notebookImagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val launchImport = remember(importLauncher) {
        {
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
    }

    // Pre-index playlist memberships & lookup map once instead of filtering per grid item
    val docsById = remember(documents) { documents.associateBy { it.id } }
    val docIdsByPlaylistId = remember(playlistRefs) {
        playlistRefs.groupBy({ it.playlistId }, { it.documentId })
    }
    val activeSeries by remember(playlists) {
        derivedStateOf { playlists.firstOrNull { it.id == openedSeriesId } }
    }
    val docIdsInActiveSeries by remember(docIdsByPlaylistId) {
        derivedStateOf {
            val sid = openedSeriesId ?: return@derivedStateOf null
            docIdsByPlaylistId[sid]?.toSet().orEmpty()
        }
    }
    val filteredDocs by remember(documents) {
        derivedStateOf {
            val seriesIds = docIdsInActiveSeries
            val filter = selectedFilter
            val query = searchQuery
            documents.filter { doc ->
                val inSeries = if (activeTab == LibraryViewTab.SERIES && seriesIds != null) doc.id in seriesIds else true
                val matchesFilter = when (filter) {
                    "Todos" -> true
                    "Otros" -> doc.format == "TXT" || doc.format == "FB2"
                    else -> doc.format.equals(filter, ignoreCase = true)
                }
                val matchesQuery = query.isBlank() ||
                    doc.title.contains(query, ignoreCase = true) ||
                    doc.author.contains(query, ignoreCase = true) ||
                    doc.genre.contains(query, ignoreCase = true)
                inSeries && matchesFilter && matchesQuery
            }
        }
    }
    val filteredSeries by remember(playlists) {
        derivedStateOf {
            val query = searchQuery
            if (query.isBlank()) playlists
            else playlists.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
        }
    }
    val filteredNotebooks by remember(notebooks) {
        derivedStateOf {
            val query = searchQuery
            if (query.isBlank()) notebooks
            else notebooks.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
    val activeDriveSource = remember(enabledDriveSources, selectedDriveSourceId) {
        enabledDriveSources.firstOrNull { it.id == selectedDriveSourceId }
    }
    val currentDriveFolderId = remember(activeDriveSource, driveBreadcrumbs) {
        driveBreadcrumbs.lastOrNull()?.folderId ?: activeDriveSource?.folderId
    }
    val currentDrivePageKey = remember(activeDriveSource, currentDriveFolderId) {
        if (activeDriveSource != null && !currentDriveFolderId.isNullOrBlank()) {
            "${activeDriveSource.id}:$currentDriveFolderId"
        } else null
    }
    val hasMoreDriveFiles = remember(driveHasMoreByFolder, currentDrivePageKey) {
        currentDrivePageKey != null && driveHasMoreByFolder[currentDrivePageKey] == true
    }
    val filteredDriveFiles by remember(
        allDriveFiles,
        enabledDriveSources,
        selectedDriveSourceId,
        currentDriveFolderId,
        driveFilterKind,
        driveSortOrder,
        searchQuery
    ) {
        derivedStateOf {
            val enabledIds = enabledDriveSources.map { it.id }.toSet()
            val rootFolderBySource = enabledDriveSources.associate { it.id to it.folderId }
            val query = searchQuery.trim()
            val base = allDriveFiles.filter { file ->
                if (file.sourceId !in enabledIds) return@filter false
                val inScope = when {
                    selectedDriveSourceId == null ->
                        if (query.isNotBlank()) true else file.parentFolderId == rootFolderBySource[file.sourceId]
                    currentDriveFolderId != null ->
                        file.sourceId == selectedDriveSourceId && file.parentFolderId == currentDriveFolderId
                    else -> file.sourceId == selectedDriveSourceId
                }
                if (!inScope) return@filter false
                val matchesKind = when (driveFilterKind) {
                    DriveFilterKind.ALL -> true
                    DriveFilterKind.PDF -> file.isFolder || file.kind == DriveFileKind.PDF
                    DriveFilterKind.DOCX -> file.isFolder || file.kind == DriveFileKind.DOCX
                    DriveFilterKind.IMAGES -> file.isFolder || file.kind == DriveFileKind.IMAGE
                    DriveFilterKind.NOTEBOOKS -> file.isFolder || file.kind == DriveFileKind.LUMA_NOTEBOOK
                    DriveFilterKind.BOOKS -> file.isFolder || file.kind in setOf(
                        DriveFileKind.EPUB,
                        DriveFileKind.PPTX,
                        DriveFileKind.TXT,
                        DriveFileKind.FB2
                    )
                }
                val matchesQuery = query.isBlank() || file.name.contains(query, ignoreCase = true)
                matchesKind && matchesQuery
            }
            val comparator: Comparator<DriveFileEntity> = when (driveSortOrder) {
                DriveSortOrder.DATE_DESC -> compareByDescending<DriveFileEntity> { it.isFolder }
                    .thenByDescending { it.modifiedTimeMs }
                DriveSortOrder.NAME_ASC -> compareByDescending<DriveFileEntity> { it.isFolder }
                    .thenBy { it.name.lowercase() }
                DriveSortOrder.SIZE_DESC -> compareByDescending<DriveFileEntity> { it.isFolder }
                    .thenByDescending { it.sizeBytes }
            }
            base.sortedWith(comparator)
        }
    }

    fun launchDownloadAndOpenDriveFile(file: DriveFileEntity) {
        coroutineScope.launch {
            driveRepo.downloadAndOpenFile(file).onSuccess { target ->
                when (target) {
                    is DriveOpenTarget.OpenDocument -> onOpenDocument(target.documentId)
                    is DriveOpenTarget.OpenNotebook -> {
                        notebooksActivated = true
                        onOpenNotebook(target.notebookId)
                    }
                }
            }
        }
    }

    fun handleTapDriveFile(file: DriveFileEntity) {
        if (file.isFolder) {
            val source = enabledDriveSources.firstOrNull { it.id == file.sourceId }
            if (selectedDriveSourceId != file.sourceId && source != null) {
                selectedDriveSourceId = source.id
                driveBreadcrumbs = listOf(
                    DriveBreadcrumb(source.folderId, source.name),
                    DriveBreadcrumb(file.driveFileId, file.name)
                )
            } else {
                val baseCrumbs = if (driveBreadcrumbs.isEmpty() && source != null) {
                    listOf(DriveBreadcrumb(source.folderId, source.name))
                } else {
                    driveBreadcrumbs
                }
                driveBreadcrumbs = baseCrumbs + DriveBreadcrumb(file.driveFileId, file.name)
            }
            coroutineScope.launch {
                driveRepo.refreshFolder(file.sourceId, file.driveFileId, loadMore = false)
            }
        } else if (file.isDownloadedLocally) {
            launchDownloadAndOpenDriveFile(file)
        } else {
            pendingDriveDownloadFile = file
        }
    }

    val continueDoc = remember(documents) { documents.firstOrNull() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenMaxWidth = maxWidth
        val isTabletLayout = screenMaxWidth >= 720.dp
        Scaffold(
            containerColor = palette.background,
            bottomBar = {
                if (!isTabletLayout) {
                    LibraryMobileBottomNavBar(
                        activeTab = activeTab,
                        pendingDriveCount = driveSyncQueue.size,
                        onSelectTab = { tab ->
                            activeTab = tab
                            openedSeriesId = null
                        },
                        onOpenSettings = { openSettingsAt(SETTINGS_TAB_GENERAL) }
                    )
                }
            }
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
                if (isTabletLayout) {
                val leftPaneWidth = (screenMaxWidth * 0.36f).coerceIn(300.dp, 400.dp)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // Left Panel: Compact Luma Brand Header + Proportioned Continue Reading + Quick Import
                    Column(
                        modifier = Modifier
                            .width(leftPaneWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LibraryHeaderRow(
                            bookCount = documents.size,
                            seriesCount = playlists.size,
                            socialRepo = socialRepo,
                            isImporting = isImporting,
                            onOpenGuide = { showAppGuide = true },
                            onToggleProfileMenu = { showProfileMenu = !showProfileMenu },
                            onHeaderRect = { rect -> tourTargets = tourTargets.copy(headerRect = rect.toRootRelative()) },
                            onGuideRect = { rect -> tourTargets = tourTargets.copy(guideButtonRect = rect.toRootRelative()) },
                            onProfileBounds = { topY, rect ->
                                profileSphereTopInRootPx = (topY - rootTopWindowPx).coerceAtLeast(0f)
                                profileSphereRightInRootPx = (rect.right - rootLeftWindowPx).coerceAtLeast(0f)
                                tourTargets = tourTargets.copy(profileSphereRect = rect.toRootRelative())
                            }
                        )

                        if (continueDoc != null) {
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

                        // Compact tablet studio quick-action card for importing books or creating notebooks/series
                        TactileCard(
                            containerColor = palette.surface,
                            borderColor = palette.border,
                            shadowColor = palette.borderShadow,
                            cornerRadius = 22.dp,
                            lipDepth = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TactileButton(
                                        onClick = launchImport,
                                        cornerRadius = 14.dp,
                                        lipDepth = 2.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tablet_quick_import_button")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Importar", fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(palette.accentSoft)
                                            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                            .clickable {
                                                notebooksActivated = true
                                                showCreateNotebookDialog = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp)
                                            .testTag("tablet_quick_new_notebook_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Draw,
                                                contentDescription = "Nuevo cuaderno",
                                                tint = palette.accent,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Text(
                                                text = "Cuaderno",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right Panel: Libros / Series / Cuadernos Switcher + Search/Filter + Catalog Grid
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LibraryTabsAndSearchControls(
                            activeTab = activeTab,
                            isSeriesOverview = activeTab == LibraryViewTab.SERIES && activeSeries == null,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            onSelectTab = { tab ->
                                activeTab = tab
                                openedSeriesId = null
                            },
                            onSearchQueryChange = { searchQuery = it },
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            onCreateNotebook = {
                                notebooksActivated = true
                                showCreateNotebookDialog = true
                            },
                            onRefreshDrive = {
                                coroutineScope.launch {
                                    val src = activeDriveSource
                                    val folder = currentDriveFolderId
                                    if (src != null && !folder.isNullOrBlank()) {
                                        driveRepo.refreshFolder(src.id, folder, loadMore = false)
                                    } else {
                                        driveRepo.refreshAllEnabledSources()
                                    }
                                }
                            },
                            onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
                            onTabsRect = { rect -> tourTargets = tourTargets.copy(tabsRect = rect.toRootRelative()) },
                            onSearchFilterRect = { rect -> tourTargets = tourTargets.copy(searchFilterRect = rect.toRootRelative()) },
                            onFilterButtonBottom = { bottomY ->
                                filterButtonBottomInRootPx = (bottomY - rootTopWindowPx).coerceAtLeast(0f)
                            }
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 142.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (activeTab == LibraryViewTab.DRIVE) {
                                driveBrowserGridItems(
                                    driveRepo = driveRepo,
                                    enabledSources = enabledDriveSources,
                                    selectedSourceId = selectedDriveSourceId,
                                    breadcrumbs = driveBreadcrumbs,
                                    filterKind = driveFilterKind,
                                    sortOrder = driveSortOrder,
                                    isGridView = driveIsGridView,
                                    pendingQueueCount = driveSyncQueue.size,
                                    files = filteredDriveFiles,
                                    hasMoreFiles = hasMoreDriveFiles,
                                    onSelectSource = { src ->
                                        selectedDriveSourceId = src?.id
                                        driveBreadcrumbs = if (src != null) {
                                            listOf(DriveBreadcrumb(src.folderId, src.name))
                                        } else emptyList()
                                        coroutineScope.launch {
                                            if (src != null) {
                                                driveRepo.refreshFolder(src.id, src.folderId, loadMore = false)
                                            } else {
                                                driveRepo.refreshAllEnabledSources()
                                            }
                                        }
                                    },
                                    onNavigateBreadcrumb = { idx ->
                                        val nextCrumbs = driveBreadcrumbs.take(idx + 1)
                                        driveBreadcrumbs = nextCrumbs
                                        val srcId = selectedDriveSourceId
                                        val targetFolder = nextCrumbs.lastOrNull()?.folderId
                                        if (srcId != null && !targetFolder.isNullOrBlank()) {
                                            coroutineScope.launch {
                                                driveRepo.refreshFolder(srcId, targetFolder, loadMore = false)
                                            }
                                        }
                                    },
                                    onSelectFilter = { driveFilterKind = it },
                                    onSelectSort = { driveSortOrder = it },
                                    onToggleGridView = { driveIsGridView = !driveIsGridView },
                                    onOpenDriveSettings = { openSettingsAt(SETTINGS_TAB_DRIVE_SOURCES) },
                                    onTapFile = ::handleTapDriveFile,
                                    onLongPressFile = { optionsDriveFile = it },
                                    onLoadMore = {
                                        val src = activeDriveSource
                                        val folder = currentDriveFolderId
                                        if (src != null && !folder.isNullOrBlank()) {
                                            coroutineScope.launch {
                                                driveRepo.refreshFolder(src.id, folder, loadMore = true)
                                            }
                                        }
                                    },
                                    onRefreshNow = {
                                        coroutineScope.launch {
                                            val src = activeDriveSource
                                            val folder = currentDriveFolderId
                                            if (src != null && !folder.isNullOrBlank()) {
                                                driveRepo.refreshFolder(src.id, folder, loadMore = false)
                                            } else {
                                                driveRepo.refreshAllEnabledSources()
                                            }
                                        }
                                    }
                                )
                            } else if (activeTab == LibraryViewTab.NOTEBOOKS) {
                                if (filteredNotebooks.isEmpty()) {
                                    item(key = "lib_notebooks_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_notebooks") {
                                        EmptyNotebooksCard(
                                            onCreateBlank = { showCreateNotebookDialog = true },
                                            onImportPdf = launchNotebookPdfPicker,
                                            onImportImage = launchNotebookImagePicker
                                        )
                                    }
                                } else {
                                    items(
                                        items = filteredNotebooks,
                                        key = { "nb_${it.id}" },
                                        contentType = { "notebook_card" }
                                    ) { nb ->
                                        Box(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = motion.tweenSpec(215),
                                                fadeOutSpec = motion.exitTween(185),
                                                placementSpec = motion.glideSpring()
                                            )
                                        ) {
                                            NotebookGridCard(
                                                notebook = nb,
                                                onOpen = { onOpenNotebook(nb.id) },
                                                onLongPress = { optionsNotebook = nb }
                                            )
                                        }
                                    }
                                }
                            } else if (activeTab == LibraryViewTab.SERIES && activeSeries == null) {
                                if (filteredSeries.isEmpty()) {
                                    item(key = "lib_series_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_series") {
                                        EmptySeriesCard(onCreate = { showCreatePlaylistDialog = true })
                                    }
                                } else {
                                    items(
                                        items = filteredSeries,
                                        key = { "series_${it.id}" },
                                        contentType = { "series_card" }
                                    ) { series ->
                                        val memberIds = docIdsByPlaylistId[series.id].orEmpty()
                                        val leadCoverPath = remember(memberIds, docsById) {
                                            memberIds.firstNotNullOfOrNull { docsById[it]?.coverImagePath }
                                        }
                                        Box(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = motion.tweenSpec(215),
                                                fadeOutSpec = motion.exitTween(185),
                                                placementSpec = motion.glideSpring()
                                            )
                                        ) {
                                            SeriesSteamCard(
                                                series = series,
                                                memberCount = memberIds.size,
                                                leadCoverPath = leadCoverPath,
                                                onClick = { openedSeriesId = series.id }
                                            )
                                        }
                                    }
                                }
                            } else {
                                if (activeTab == LibraryViewTab.SERIES && activeSeries != null) {
                                    val currentSeries = activeSeries!!
                                    item(key = "lib_series_detail_header", span = { GridItemSpan(maxLineSpan) }, contentType = "series_header") {
                                        SeriesDetailHeaderBar(
                                            series = currentSeries,
                                            docCount = filteredDocs.size,
                                            onBack = { openedSeriesId = null },
                                            onDelete = {
                                                viewModel.deletePlaylist(currentSeries)
                                                openedSeriesId = null
                                            }
                                        )
                                    }
                                }

                                if (filteredDocs.isEmpty()) {
                                    item(key = "lib_books_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_books") {
                                        EmptyBooksCard(isInSeries = activeSeries != null)
                                    }
                                } else {
                                    items(
                                        items = filteredDocs,
                                        key = { "book_${it.id}" },
                                        contentType = { "book_card" }
                                    ) { doc ->
                                        Box(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = motion.tweenSpec(215),
                                                fadeOutSpec = motion.exitTween(185),
                                                placementSpec = motion.glideSpring()
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
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Minimalist Full-Span "LUMA" Wordmark + Interactive Profile Sphere
                    item(key = "lib_header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        LibraryHeaderRow(
                            bookCount = documents.size,
                            seriesCount = playlists.size,
                            socialRepo = socialRepo,
                            isImporting = isImporting,
                            onOpenGuide = { showAppGuide = true },
                            onToggleProfileMenu = { showProfileMenu = !showProfileMenu },
                            onHeaderRect = { rect -> tourTargets = tourTargets.copy(headerRect = rect.toRootRelative()) },
                            onGuideRect = { rect -> tourTargets = tourTargets.copy(guideButtonRect = rect.toRootRelative()) },
                            onProfileBounds = { topY, rect ->
                                profileSphereTopInRootPx = (topY - rootTopWindowPx).coerceAtLeast(0f)
                                profileSphereRightInRootPx = (rect.right - rootLeftWindowPx).coerceAtLeast(0f)
                                tourTargets = tourTargets.copy(profileSphereRect = rect.toRootRelative())
                            }
                        )
                    }

                    // 2. Soft Continue Reading Banner
                    if (continueDoc != null && activeTab != LibraryViewTab.DRIVE) {
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

                    // 3. Tab Switcher ("Libros" | "Series" | "Cuadernos" | "Drive") ABOVE Search Bar + Action Button
                    item(key = "lib_tabs_and_search", span = { GridItemSpan(maxLineSpan) }, contentType = "controls") {
                        LibraryTabsAndSearchControls(
                            activeTab = activeTab,
                            isSeriesOverview = activeTab == LibraryViewTab.SERIES && activeSeries == null,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            onSelectTab = { tab ->
                                activeTab = tab
                                openedSeriesId = null
                            },
                            onSearchQueryChange = { searchQuery = it },
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            onCreateNotebook = {
                                notebooksActivated = true
                                showCreateNotebookDialog = true
                            },
                            onRefreshDrive = {
                                coroutineScope.launch {
                                    val src = activeDriveSource
                                    val folder = currentDriveFolderId
                                    if (src != null && !folder.isNullOrBlank()) {
                                        driveRepo.refreshFolder(src.id, folder, loadMore = false)
                                    } else {
                                        driveRepo.refreshAllEnabledSources()
                                    }
                                }
                            },
                            onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
                            onTabsRect = { rect -> tourTargets = tourTargets.copy(tabsRect = rect.toRootRelative()) },
                            onSearchFilterRect = { rect -> tourTargets = tourTargets.copy(searchFilterRect = rect.toRootRelative()) },
                            onFilterButtonBottom = { bottomY ->
                                filterButtonBottomInRootPx = (bottomY - rootTopWindowPx).coerceAtLeast(0f)
                            }
                        )
                    }

                    // 4. Content Area: Drive OR Cuadernos OR Series Overview OR Steam-Style Book Cover Grid
                    if (activeTab == LibraryViewTab.DRIVE) {
                        driveBrowserGridItems(
                            driveRepo = driveRepo,
                            enabledSources = enabledDriveSources,
                            selectedSourceId = selectedDriveSourceId,
                            breadcrumbs = driveBreadcrumbs,
                            filterKind = driveFilterKind,
                            sortOrder = driveSortOrder,
                            isGridView = driveIsGridView,
                            pendingQueueCount = driveSyncQueue.size,
                            files = filteredDriveFiles,
                            hasMoreFiles = hasMoreDriveFiles,
                            onSelectSource = { src ->
                                selectedDriveSourceId = src?.id
                                driveBreadcrumbs = if (src != null) {
                                    listOf(DriveBreadcrumb(src.folderId, src.name))
                                } else emptyList()
                                coroutineScope.launch {
                                    if (src != null) {
                                        driveRepo.refreshFolder(src.id, src.folderId, loadMore = false)
                                    } else {
                                        driveRepo.refreshAllEnabledSources()
                                    }
                                }
                            },
                            onNavigateBreadcrumb = { idx ->
                                val nextCrumbs = driveBreadcrumbs.take(idx + 1)
                                driveBreadcrumbs = nextCrumbs
                                val srcId = selectedDriveSourceId
                                val targetFolder = nextCrumbs.lastOrNull()?.folderId
                                if (srcId != null && !targetFolder.isNullOrBlank()) {
                                    coroutineScope.launch {
                                        driveRepo.refreshFolder(srcId, targetFolder, loadMore = false)
                                    }
                                }
                            },
                            onSelectFilter = { driveFilterKind = it },
                            onSelectSort = { driveSortOrder = it },
                            onToggleGridView = { driveIsGridView = !driveIsGridView },
                            onOpenDriveSettings = { openSettingsAt(SETTINGS_TAB_DRIVE_SOURCES) },
                            onTapFile = ::handleTapDriveFile,
                            onLongPressFile = { optionsDriveFile = it },
                            onLoadMore = {
                                val src = activeDriveSource
                                val folder = currentDriveFolderId
                                if (src != null && !folder.isNullOrBlank()) {
                                    coroutineScope.launch {
                                        driveRepo.refreshFolder(src.id, folder, loadMore = true)
                                    }
                                }
                            },
                            onRefreshNow = {
                                coroutineScope.launch {
                                    val src = activeDriveSource
                                    val folder = currentDriveFolderId
                                    if (src != null && !folder.isNullOrBlank()) {
                                        driveRepo.refreshFolder(src.id, folder, loadMore = false)
                                    } else {
                                        driveRepo.refreshAllEnabledSources()
                                    }
                                }
                            }
                        )
                    } else if (activeTab == LibraryViewTab.NOTEBOOKS) {
                        if (filteredNotebooks.isEmpty()) {
                            item(key = "lib_notebooks_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_notebooks") {
                                EmptyNotebooksCard(
                                    onCreateBlank = { showCreateNotebookDialog = true },
                                    onImportPdf = launchNotebookPdfPicker,
                                    onImportImage = launchNotebookImagePicker
                                )
                            }
                        } else {
                            items(
                                items = filteredNotebooks,
                                key = { "nb_${it.id}" },
                                contentType = { "notebook_card" }
                            ) { nb ->
                                Box(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = motion.tweenSpec(215),
                                        fadeOutSpec = motion.exitTween(185),
                                        placementSpec = motion.glideSpring()
                                    )
                                ) {
                                    NotebookGridCard(
                                        notebook = nb,
                                        onOpen = { onOpenNotebook(nb.id) },
                                        onLongPress = { optionsNotebook = nb }
                                    )
                                }
                            }
                        }
                    } else if (activeTab == LibraryViewTab.SERIES && activeSeries == null) {
                        if (filteredSeries.isEmpty()) {
                            item(key = "lib_series_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_series") {
                                EmptySeriesCard(onCreate = { showCreatePlaylistDialog = true })
                            }
                        } else {
                            items(
                                items = filteredSeries,
                                key = { "series_${it.id}" },
                                contentType = { "series_card" }
                            ) { series ->
                                val memberIds = docIdsByPlaylistId[series.id].orEmpty()
                                val leadCoverPath = remember(memberIds, docsById) {
                                    memberIds.firstNotNullOfOrNull { docsById[it]?.coverImagePath }
                                }
                                Box(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = motion.tweenSpec(215),
                                        fadeOutSpec = motion.exitTween(185),
                                        placementSpec = motion.glideSpring()
                                    )
                                ) {
                                    SeriesSteamCard(
                                        series = series,
                                        memberCount = memberIds.size,
                                        leadCoverPath = leadCoverPath,
                                        onClick = { openedSeriesId = series.id }
                                    )
                                }
                            }
                        }
                    } else {
                        if (activeTab == LibraryViewTab.SERIES && activeSeries != null) {
                            val currentSeries = activeSeries!!
                            item(key = "lib_series_detail_header", span = { GridItemSpan(maxLineSpan) }, contentType = "series_header") {
                                SeriesDetailHeaderBar(
                                    series = currentSeries,
                                    docCount = filteredDocs.size,
                                    onBack = { openedSeriesId = null },
                                    onDelete = {
                                        viewModel.deletePlaylist(currentSeries)
                                        openedSeriesId = null
                                    }
                                )
                            }
                        }

                        if (filteredDocs.isEmpty()) {
                            item(key = "lib_books_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_books") {
                                EmptyBooksCard(isInSeries = activeSeries != null)
                            }
                        } else {
                            items(
                                items = filteredDocs,
                                key = { "book_${it.id}" },
                                contentType = { "book_card" }
                            ) { doc ->
                                Box(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = motion.tweenSpec(215),
                                        fadeOutSpec = motion.exitTween(185),
                                        placementSpec = motion.glideSpring()
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
            }

            // Draw-phase frosted scrim replacing expensive animated full-screen Modifier.blur()
            LibraryFrostedMenuScrim(visible = anyFloatingMenuOpen)

            // Scoped Profile Dropdown Overlay (only collects social profile/friends when rendered)
            LibraryProfileDropdownHost(
                visible = showProfileMenu,
                anchorTopPx = profileSphereTopInRootPx,
                anchorRightPx = profileSphereRightInRootPx,
                socialRepo = socialRepo,
                isCloudSignedIn = cloudUiState.isSignedIn,
                cloudSyncButtonState = cloudUiState.syncButtonState,
                isImporting = isImporting,
                onDismiss = { showProfileMenu = false },
                onOpenCloudAccount = { showCloudMyAccountDialog = true },
                onOpenAccount = { showAccountDialog = true },
                onOpenFriends = { showFriendsDialog = true },
                onOpenSettings = { openSettingsAt(SETTINGS_TAB_GENERAL) },
                onOpenGuide = { showAppGuide = true },
                onImportDocument = launchImport
            )

            LibraryFilterDropdownOverlay(
                visible = filterMenuExpanded,
                anchorBottomPx = filterButtonBottomInRootPx,
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilter = it },
                onDismiss = { filterMenuExpanded = false }
            )

            // Isolated feedback host so action toast events never recompose LibraryScreen
            LibraryFeedbackHost(viewModel = viewModel)

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
    }

    // Long-press Floating Book Action Window
    optionsBook?.let { doc ->
        BookOptionsFloatingDialog(
            doc = doc,
            loadSynopsis = viewModel::loadDocumentSynopsis,
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
            onSyncToDrive = {
                optionsBook = null
                when {
                    enabledDriveSources.isEmpty() -> openSettingsAt(SETTINGS_TAB_DRIVE_SOURCES)
                    enabledDriveSources.size == 1 -> {
                        coroutineScope.launch {
                            driveRepo.syncDocumentToDrive(doc.id, enabledDriveSources.first().id)
                        }
                    }
                    else -> pendingUploadDocumentId = doc.id
                }
            },
            onDelete = {
                optionsBook = null
                viewModel.deleteDocument(doc)
            },
            onDismiss = { optionsBook = null }
        )
    }

    bookForMetadataModal?.let { doc ->
        EditBookMetadataDialog(
            doc = doc,
            loadSynopsis = viewModel::loadDocumentSynopsis,
            onSave = { title, author, genre, year, synopsis, badgeTag ->
                viewModel.updateBookMetadata(doc, title, author, genre, year, synopsis, badgeTag)
                bookForMetadataModal = null
            },
            onDismiss = { bookForMetadataModal = null }
        )
    }

    if (showCloudMyAccountDialog) {
        CloudMyAccountDialog(
            accountManager = cloudAccountManager,
            onOpenDriveSettings = { openSettingsAt(SETTINGS_TAB_DRIVE_SOURCES) },
            onDismiss = { showCloudMyAccountDialog = false }
        )
    }

    if (showAccountDialog) {
        AccountProfileDialog(
            socialRepo = socialRepo,
            currentOrRecentBookTitle = continueDoc?.title.orEmpty(),
            currentOrRecentBookProgressPct = ((continueDoc?.progressPercent ?: 0f) * 100f).roundToInt(),
            onDismiss = { showAccountDialog = false }
        )
    }

    if (showFriendsDialog) {
        FriendsChatDialog(
            socialRepo = socialRepo,
            currentOrRecentBookTitle = continueDoc?.title.orEmpty(),
            onDismiss = { showFriendsDialog = false }
        )
    }

    // Isolated Settings Host: collects full ReaderPreferences & dictionarySources ONLY while open!
    if (showSettingsSheet) {
        LibrarySettingsSheetHost(
            viewModel = viewModel,
            initialTab = settingsInitialTab,
            onReplayOnboarding = {
                showSettingsSheet = false
                showPermissionsOnboarding = true
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

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

    // Isolated Quick Profile Setup Host
    LibraryQuickProfileHost(
        socialRepo = socialRepo,
        canShow = preferencesLoaded &&
            hasCompletedOnboarding &&
            !showPermissionsOnboarding &&
            !showInteractiveTour &&
            !showAccountDialog &&
            !showFriendsDialog &&
            !showSettingsSheet &&
            !showAppGuide,
        onPickAvatar = {
            quickAvatarPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    )

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onCreate = { name, desc, colorHex ->
                viewModel.createPlaylist(name, desc, colorHex)
                showCreatePlaylistDialog = false
            },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }

    if (showCreateNotebookDialog) {
        CreateNotebookDialog(
            onCreate = { title, template, widthPx, heightPx, bgColor ->
                showCreateNotebookDialog = false
                notebooksActivated = true
                coroutineScope.launch {
                    val newId = notebookRepositoryProvider().createNotebook(
                        title = title,
                        template = template,
                        pageWidthPx = widthPx,
                        pageHeightPx = heightPx,
                        backgroundColor = bgColor
                    )
                    if (enabledDriveSources.isNotEmpty() && driveRepo.vault.getSyncMode() != DriveSyncMode.MANUAL) {
                        val defaultTarget = enabledDriveSources.firstOrNull { it.isDefaultSaveTarget }
                            ?: enabledDriveSources.first()
                        coroutineScope.launch {
                            driveRepo.syncNotebookToDrive(newId, defaultTarget.id)
                        }
                    }
                    onOpenNotebook(newId)
                }
            },
            onCreateFromPdf = launchNotebookPdfPicker,
            onCreateFromImage = launchNotebookImagePicker,
            onDismiss = { showCreateNotebookDialog = false }
        )
    }

    optionsNotebook?.let { nb ->
        NotebookOptionsDialog(
            notebook = nb,
            onRename = { newTitle ->
                coroutineScope.launch {
                    notebookRepositoryProvider().renameNotebook(nb.id, newTitle)
                }
            },
            onSyncToDrive = {
                when {
                    enabledDriveSources.isEmpty() -> openSettingsAt(SETTINGS_TAB_DRIVE_SOURCES)
                    enabledDriveSources.size == 1 -> {
                        coroutineScope.launch {
                            driveRepo.syncNotebookToDrive(nb.id, enabledDriveSources.first().id)
                        }
                    }
                    else -> pendingUploadNotebookId = nb.id
                }
            },
            onDelete = {
                coroutineScope.launch {
                    notebookRepositoryProvider().deleteNotebook(nb.id)
                }
            },
            onDismiss = { optionsNotebook = null }
        )
    }

    // Google Drive Download Confirmation Dialog ("¿Desea descargar [NOMBRE]? Tamaño: X MB")
    pendingDriveDownloadFile?.let { targetFile ->
        DriveDownloadConfirmDialog(
            file = targetFile,
            driveRepo = driveRepo,
            onConfirmDownload = {
                pendingDriveDownloadFile = null
                launchDownloadAndOpenDriveFile(targetFile)
            },
            onDismiss = { pendingDriveDownloadFile = null }
        )
    }

    // Google Drive Long-Press Context Menu
    optionsDriveFile?.let { file ->
        DriveFileContextMenuDialog(
            file = file,
            allSources = enabledDriveSources,
            onOpen = {
                optionsDriveFile = null
                handleTapDriveFile(file)
            },
            onDownload = {
                optionsDriveFile = null
                launchDownloadAndOpenDriveFile(file)
            },
            onSync = {
                optionsDriveFile = null
                coroutineScope.launch { driveRepo.syncDriveFile(file) }
            },
            onRename = { newName ->
                optionsDriveFile = null
                coroutineScope.launch { driveRepo.renameRemoteFile(file, newName) }
            },
            onMoveToSource = { targetSource ->
                optionsDriveFile = null
                coroutineScope.launch { driveRepo.moveRemoteFile(file, targetSource) }
            },
            onFreeLocalCopy = {
                optionsDriveFile = null
                coroutineScope.launch { driveRepo.freeLocalSpace(file) }
            },
            onDeleteFromDrive = {
                optionsDriveFile = null
                coroutineScope.launch { driveRepo.deleteRemoteFile(file) }
            },
            onDismiss = { optionsDriveFile = null }
        )
    }

    // Google Drive Version Conflict Resolution Dialog
    driveConflict?.let { conflict ->
        DriveConflictResolutionDialog(
            conflict = conflict,
            onResolve = { resolution ->
                coroutineScope.launch { driveRepo.resolveConflict(conflict, resolution) }
            },
            onDismiss = { driveRepo.dismissConflictDialog() }
        )
    }

    // Destination Picker for syncing a local Book to Google Drive
    pendingUploadDocumentId?.let { docId ->
        DriveDestinationPickerDialog(
            title = "Subir libro a Google Drive",
            sources = enabledDriveSources,
            allowLocalOnly = false,
            onSelectSource = { chosen ->
                pendingUploadDocumentId = null
                if (chosen != null) {
                    coroutineScope.launch { driveRepo.syncDocumentToDrive(docId, chosen.id) }
                }
            },
            onDismiss = { pendingUploadDocumentId = null }
        )
    }

    // Destination Picker for syncing a local Study Notebook (.luma) to Google Drive
    pendingUploadNotebookId?.let { nbId ->
        DriveDestinationPickerDialog(
            title = "Sincronizar cuaderno (.luma)",
            sources = enabledDriveSources,
            allowLocalOnly = false,
            onSelectSource = { chosen ->
                pendingUploadNotebookId = null
                if (chosen != null) {
                    coroutineScope.launch { driveRepo.syncNotebookToDrive(nbId, chosen.id) }
                }
            },
            onDismiss = { pendingUploadNotebookId = null }
        )
    }

    bookForPlaylistModal?.let { doc ->
        val memberships = remember(playlistRefs, doc.id) {
            playlistRefs.filter { it.documentId == doc.id }.map { it.playlistId }.toSet()
        }
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
private fun LibraryFrostedMenuScrim(visible: Boolean) {
    val palette = LocalFolioPalette.current
    val motion = LocalLumaMotion.current
    val scrimAlphaState = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motion.tweenSpec(205),
        label = "library_menu_scrim_alpha"
    )
    val scrimBrush = remember(palette.isDark, palette.background) {
        val base = if (palette.isDark) Color.Black else palette.background
        Brush.verticalGradient(
            colors = listOf(
                base.copy(alpha = if (palette.isDark) 0.34f else 0.24f),
                Color.Black.copy(alpha = if (palette.isDark) 0.24f else 0.14f),
                base.copy(alpha = if (palette.isDark) 0.34f else 0.24f)
            )
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val a = scrimAlphaState.value
                if (a > 0.005f) {
                    drawRect(brush = scrimBrush, alpha = a)
                }
            }
    )
}

@Composable
private fun LibraryHeaderRow(
    bookCount: Int,
    seriesCount: Int,
    socialRepo: SocialRepository,
    isImporting: Boolean,
    onOpenGuide: () -> Unit,
    onToggleProfileMenu: () -> Unit,
    onHeaderRect: (Rect) -> Unit,
    onGuideRect: (Rect) -> Unit,
    onProfileBounds: (Float, Rect) -> Unit
) {
    val palette = LocalFolioPalette.current
    val userProfile by socialRepo.profile.collectAsStateWithLifecycle()
    val friendsList by socialRepo.friends.collectAsStateWithLifecycle()
    val totalUnreadMessages by remember(friendsList) {
        derivedStateOf { friendsList.sumOf { it.unreadCount } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LumaWideWordmark(
            bookCount = bookCount,
            seriesCount = seriesCount,
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coords -> onHeaderRect(coords.boundsInWindow()) }
        )

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .onGloballyPositioned { coords -> onGuideRect(coords.boundsInWindow()) }
                .clip(CircleShape)
                .clickable(onClick = onOpenGuide)
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

        Box(
            modifier = Modifier
                .size(48.dp)
                .onGloballyPositioned { coords ->
                    onProfileBounds(coords.positionInWindow().y, coords.boundsInWindow())
                }
                .clip(CircleShape)
                .clickable(onClick = onToggleProfileMenu)
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

@Composable
private fun LibraryTabsAndSearchControls(
    activeTab: LibraryViewTab,
    isSeriesOverview: Boolean,
    searchQuery: String,
    selectedFilter: String,
    onSelectTab: (LibraryViewTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateNotebook: () -> Unit,
    onRefreshDrive: () -> Unit,
    onToggleFilterMenu: () -> Unit,
    onTabsRect: (Rect) -> Unit,
    onSearchFilterRect: (Rect) -> Unit,
    onFilterButtonBottom: (Float) -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords -> onTabsRect(coords.boundsInWindow()) }
                .clip(SwitcherOuterShape)
                .background(palette.surfaceAlt.copy(alpha = 0.75f))
                .border(1.dp, palette.border, SwitcherOuterShape)
                .padding(4.dp)
                .testTag("library_mode_switcher"),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (tab in LibraryViewTab.entries) {
                val selected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(SwitcherTabShape)
                        .background(if (selected) palette.accent else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .testTag("library_tab_${tab.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) palette.onAccent else palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords -> onSearchFilterRect(coords.boundsInWindow()) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = when {
                            activeTab == LibraryViewTab.DRIVE -> "Buscar en Google Drive…"
                            activeTab == LibraryViewTab.NOTEBOOKS -> "Buscar cuaderno de estudio…"
                            isSeriesOverview -> "Buscar serie o saga…"
                            else -> "Buscar título o autor…"
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
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                } else null,
                singleLine = true,
                shape = SearchInputShape,
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

            if (activeTab == LibraryViewTab.DRIVE) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(SearchInputShape)
                        .background(palette.accent)
                        .clickable(onClick = onRefreshDrive)
                        .testTag("drive_quick_refresh_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Actualizar carpetas de Drive",
                        tint = palette.onAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else if (activeTab == LibraryViewTab.NOTEBOOKS) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(SearchInputShape)
                        .background(palette.accent)
                        .clickable(onClick = onCreateNotebook)
                        .testTag("create_notebook_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Nuevo cuaderno",
                        tint = palette.onAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (isSeriesOverview) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(SearchInputShape)
                        .background(palette.accentSoft)
                        .border(1.dp, palette.accent.copy(alpha = 0.4f), SearchInputShape)
                        .clickable(onClick = onCreatePlaylist)
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
                            onFilterButtonBottom(coords.positionInWindow().y + coords.size.height)
                        }
                        .clip(SearchInputShape)
                        .background(if (filterActive) palette.accent else palette.surface)
                        .border(
                            width = 1.dp,
                            color = if (filterActive) palette.accent else palette.border,
                            shape = SearchInputShape
                        )
                        .clickable(onClick = onToggleFilterMenu)
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

@Composable
private fun SeriesDetailHeaderBar(
    series: PlaylistEntity,
    docCount: Int,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SeriesHeaderShape)
            .background(palette.accentSoft)
            .border(1.dp, palette.accent.copy(alpha = 0.25f), SeriesHeaderShape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
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
                text = series.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink
            )
            Text(
                text = series.description.ifBlank { "$docCount libros en esta serie" },
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
                .clickable(onClick = onDelete),
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

@Composable
private fun EmptySeriesCard(onCreate: () -> Unit) {
    val palette = LocalFolioPalette.current
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
                onClick = onCreate,
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

@Composable
private fun EmptyBooksCard(isInSeries: Boolean) {
    val palette = LocalFolioPalette.current
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
                text = if (isInSeries) "Esta serie aún está vacía" else "Sin resultados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = if (isInSeries) {
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

@Composable
private fun LibraryProfileDropdownHost(
    visible: Boolean,
    anchorTopPx: Float,
    anchorRightPx: Float,
    socialRepo: SocialRepository,
    isCloudSignedIn: Boolean,
    cloudSyncButtonState: CloudSyncButtonState,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onOpenCloudAccount: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onImportDocument: () -> Unit
) {
    val userProfile by socialRepo.profile.collectAsStateWithLifecycle()
    val friendsList by socialRepo.friends.collectAsStateWithLifecycle()
    val totalUnreadMessages by remember(friendsList) {
        derivedStateOf { friendsList.sumOf { it.unreadCount } }
    }
    ProfileDropdownCapsuleOverlay(
        visible = visible,
        anchorTopPx = anchorTopPx,
        anchorRightPx = anchorRightPx,
        userProfile = userProfile,
        unreadMessages = totalUnreadMessages,
        isImporting = isImporting,
        onDismiss = onDismiss,
        onOpenCloudAccount = onOpenCloudAccount,
        isCloudSignedIn = isCloudSignedIn,
        cloudSyncButtonState = cloudSyncButtonState,
        onOpenAccount = onOpenAccount,
        onOpenFriends = onOpenFriends,
        onOpenSettings = onOpenSettings,
        onOpenGuide = onOpenGuide,
        onImportDocument = onImportDocument
    )
}

@Composable
private fun LibraryFeedbackHost(viewModel: LibraryViewModel) {
    val feedbackEvent by viewModel.feedbackEvent.collectAsStateWithLifecycle()
    LumaActionFeedbackOverlay(
        event = feedbackEvent,
        onConsumed = viewModel::clearFeedbackEvent
    )
}

@Composable
private fun LibrarySettingsSheetHost(
    viewModel: LibraryViewModel,
    initialTab: Int = SETTINGS_TAB_GENERAL,
    onReplayOnboarding: () -> Unit,
    onDismiss: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val dictSources by viewModel.dictionarySources.collectAsStateWithLifecycle()
    AppSettingsSheet(
        prefs = prefs,
        sources = dictSources,
        initialTab = initialTab,
        onUpdatePrefs = viewModel::updatePreferences,
        onAddSource = viewModel::addDictionarySource,
        onToggleSource = viewModel::toggleDictionarySource,
        onDeleteSource = viewModel::deleteDictionarySource,
        onRegenerateCovers = viewModel::regenerateMissingCovers,
        onReplayOnboarding = onReplayOnboarding,
        onDismiss = onDismiss
    )
}

@Composable
private fun LibraryMobileBottomNavBar(
    activeTab: LibraryViewTab,
    pendingDriveCount: Int,
    onSelectTab: (LibraryViewTab) -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = palette.accent,
        selectedTextColor = palette.accent,
        indicatorColor = palette.accentSoft,
        unselectedIconColor = palette.inkMuted,
        unselectedTextColor = palette.inkMuted
    )
    NavigationBar(
        containerColor = palette.surface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .border(width = 1.dp, color = palette.border)
            .testTag("library_mobile_bottom_bar")
    ) {
        NavigationBarItem(
            selected = activeTab == LibraryViewTab.BOOKS || activeTab == LibraryViewTab.SERIES,
            onClick = { onSelectTab(LibraryViewTab.BOOKS) },
            icon = { Icon(Icons.Rounded.AutoStories, contentDescription = "Libros") },
            label = { Text("Libros", maxLines = 1) },
            colors = itemColors,
            modifier = Modifier.testTag("bottom_nav_books")
        )
        NavigationBarItem(
            selected = activeTab == LibraryViewTab.NOTEBOOKS,
            onClick = { onSelectTab(LibraryViewTab.NOTEBOOKS) },
            icon = { Icon(Icons.Rounded.Draw, contentDescription = "Cuadernos") },
            label = { Text("Cuadernos", maxLines = 1) },
            colors = itemColors,
            modifier = Modifier.testTag("bottom_nav_notebooks")
        )
        NavigationBarItem(
            selected = activeTab == LibraryViewTab.DRIVE,
            onClick = { onSelectTab(LibraryViewTab.DRIVE) },
            icon = {
                if (pendingDriveCount > 0) {
                    BadgedBox(badge = { Badge { Text(pendingDriveCount.toString()) } }) {
                        Icon(Icons.Rounded.CloudQueue, contentDescription = "Google Drive")
                    }
                } else {
                    Icon(Icons.Rounded.CloudQueue, contentDescription = "Google Drive")
                }
            },
            label = { Text("Drive", maxLines = 1) },
            colors = itemColors,
            modifier = Modifier.testTag("bottom_nav_drive")
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Rounded.Settings, contentDescription = "Ajustes") },
            label = { Text("Ajustes", maxLines = 1) },
            colors = itemColors,
            modifier = Modifier.testTag("bottom_nav_settings")
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.driveBrowserGridItems(
    driveRepo: DriveRepository,
    enabledSources: List<DriveSourceEntity>,
    selectedSourceId: Long?,
    breadcrumbs: List<DriveBreadcrumb>,
    filterKind: DriveFilterKind,
    sortOrder: DriveSortOrder,
    isGridView: Boolean,
    pendingQueueCount: Int,
    files: List<DriveFileEntity>,
    hasMoreFiles: Boolean,
    onSelectSource: (DriveSourceEntity?) -> Unit,
    onNavigateBreadcrumb: (Int) -> Unit,
    onSelectFilter: (DriveFilterKind) -> Unit,
    onSelectSort: (DriveSortOrder) -> Unit,
    onToggleGridView: () -> Unit,
    onOpenDriveSettings: () -> Unit,
    onTapFile: (DriveFileEntity) -> Unit,
    onLongPressFile: (DriveFileEntity) -> Unit,
    onLoadMore: () -> Unit,
    onRefreshNow: () -> Unit
) {
    if (enabledSources.isEmpty()) {
        item(key = "drive_empty_setup", span = { GridItemSpan(maxLineSpan) }, contentType = "drive_setup") {
            DriveEmptySetupCard(onOpenSettings = onOpenDriveSettings)
        }
        return
    }

    item(key = "drive_header_controls", span = { GridItemSpan(maxLineSpan) }, contentType = "drive_header") {
        DriveBrowserHeaderBlock(
            driveRepo = driveRepo,
            sources = enabledSources,
            selectedSourceId = selectedSourceId,
            breadcrumbs = breadcrumbs,
            filterKind = filterKind,
            sortOrder = sortOrder,
            isGridView = isGridView,
            pendingQueueCount = pendingQueueCount,
            onSelectSource = onSelectSource,
            onNavigateBreadcrumb = onNavigateBreadcrumb,
            onSelectFilter = onSelectFilter,
            onSelectSort = onSelectSort,
            onToggleGridView = onToggleGridView,
            onOpenDriveSettings = onOpenDriveSettings
        )
    }

    if (files.isEmpty()) {
        item(key = "drive_folder_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "drive_empty_folder") {
            val palette = LocalFolioPalette.current
            TactileCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "Sin archivos visibles en esta carpeta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = "Pulsa Actualizar para cargar los archivos más recientes desde tu Google Drive o cambia el filtro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                    TactileButton(
                        onClick = onRefreshNow,
                        cornerRadius = 14.dp,
                        lipDepth = 2.dp
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Actualizar carpeta", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else if (isGridView) {
        items(
            items = files,
            key = { "drive_grid_${it.driveFileId}" },
            contentType = { "drive_grid_tile" }
        ) { file ->
            DriveFileGridTile(
                file = file,
                driveRepo = driveRepo,
                onTap = { onTapFile(file) },
                onLongPress = { onLongPressFile(file) }
            )
        }
    } else {
        items(
            items = files,
            key = { "drive_list_${it.driveFileId}" },
            span = { GridItemSpan(maxLineSpan) },
            contentType = { "drive_list_row" }
        ) { file ->
            DriveFileListRow(
                file = file,
                driveRepo = driveRepo,
                onTap = { onTapFile(file) },
                onLongPress = { onLongPressFile(file) }
            )
        }
    }

    if (hasMoreFiles) {
        item(key = "drive_load_more", span = { GridItemSpan(maxLineSpan) }, contentType = "drive_load_more") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                TactileButton(
                    onClick = onLoadMore,
                    cornerRadius = 14.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier.testTag("drive_load_more_button")
                ) {
                    Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cargar más archivos", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LibraryQuickProfileHost(
    socialRepo: SocialRepository,
    canShow: Boolean,
    onPickAvatar: () -> Unit
) {
    if (!canShow) return
    val userProfile by socialRepo.profile.collectAsStateWithLifecycle()
    if (!userProfile.profileConfigured) {
        QuickProfileSetupDialog(
            profile = userProfile,
            onPickAvatar = onPickAvatar,
            onSave = { chosenName -> socialRepo.updateProfileName(chosenName) },
            onSkip = { socialRepo.markProfilePromptDismissed() }
        )
    }
}
