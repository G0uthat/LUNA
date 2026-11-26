package com.example.drive

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.data.local.DocumentEntity
import com.example.data.local.FolioDatabase
import com.example.data.local.FolioRepository
import com.example.data.model.DocumentFormat
import com.example.engine.CoverManager
import com.example.engine.DocumentParser
import com.example.notebook.data.NotebookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class DriveConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
    KEEP_BOTH
}

sealed interface DriveOpenTarget {
    data class OpenDocument(val documentId: Long) : DriveOpenTarget
    data class OpenNotebook(val notebookId: Long) : DriveOpenTarget
}

/**
 * Central coordinator for user-owned Google Drive BYOC integration:
 * - Encrypted credentials & OAuth 2.0 PKCE authentication (zero backend)
 * - Multiple Drive folder sources with colors, enable/disable, and default save target
 * - Paginated folder & subfolder reading + lazy thumbnail caching
 * - Confirmation-gated resumable downloads (pause/cancel/resume + low-storage check)
 * - Free local space without deleting from Drive
 * - Resumable uploads for books & `.luma` notebooks + conflict detection & resolution
 * - Offline sync queue with automatic ConnectivityManager network/Wi-Fi retry
 */
class DriveRepository private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = FolioDatabase.getInstance(appContext)
    private val driveDao = db.driveDao()
    private val folioRepository = FolioRepository(db.folioDao())
    private val notebookRepository = NotebookRepository(appContext, db.notebookDao())

    val vault = DriveSecureVault(appContext)
    val authManager = GoogleDriveAuthManager(appContext, vault)
    val apiClient = GoogleDriveApiClient(vault, authManager)

    val authSnapshot: StateFlow<DriveAuthSnapshot> = vault.authSnapshot
    val sources: Flow<List<DriveSourceEntity>> = driveDao.observeSources()
    val allFiles: Flow<List<DriveFileEntity>> = driveDao.observeAllFiles()
    val syncQueue: Flow<List<DriveSyncTaskEntity>> = driveDao.observeSyncQueue()

    private val _activeTransfer = MutableStateFlow<DriveTransferProgress?>(null)
    val activeTransfer: StateFlow<DriveTransferProgress?> = _activeTransfer.asStateFlow()

    private val _activeConflict = MutableStateFlow<DriveConflictItem?>(null)
    val activeConflict: StateFlow<DriveConflictItem?> = _activeConflict.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isOnline = MutableStateFlow(checkOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isOnWifi = MutableStateFlow(checkWifiNow())
    val isOnWifi: StateFlow<Boolean> = _isOnWifi.asStateFlow()

    private val nextPageTokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _hasMoreByFolder = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val hasMoreByFolder: StateFlow<Map<String, Boolean>> = _hasMoreByFolder.asStateFlow()

    private val transferPaused = AtomicBoolean(false)
    private val transferCancelled = AtomicBoolean(false)

    init {
        registerNetworkMonitor()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun postStatus(msg: String) {
        _statusMessage.value = msg
    }

    // ─── Auth & Configuration ─────────────────────────────────────────────────

    fun saveClientConfiguration(clientId: String, apiKey: String, useMinimalScope: Boolean) {
        vault.saveClientConfig(clientId, apiKey, useMinimalScope)
        postStatus("Credenciales guardadas localmente de forma cifrada")
    }

    fun saveClientConfigurationQuietly(clientId: String, apiKey: String, useMinimalScope: Boolean) {
        vault.saveClientConfig(clientId, apiKey, useMinimalScope)
    }

    fun setSyncMode(mode: DriveSyncMode) {
        vault.saveSyncMode(mode)
        if (canAutoSyncNow()) {
            scope.launch { processSyncQueue() }
        }
    }

    fun buildSignInIntent(): Result<Intent> = authManager.buildAuthorizationIntent()

    suspend fun handleAuthRedirect(uri: Uri): Result<String> {
        val result = authManager.handleAuthorizationCallback(uri)
        result.onSuccess { email ->
            postStatus("Conectado a Google Drive ($email)")
            refreshAllEnabledSources()
            processSyncQueue()
        }.onFailure { err ->
            postStatus(err.message ?: "No se pudo iniciar sesión en Google Drive")
        }
        return result
    }

    suspend fun connectManualTokenOrRedirect(input: String): Result<String> {
        val result = authManager.connectWithManualTokenOrRedirect(input)
        result.onSuccess { email ->
            postStatus("Sesión conectada ($email)")
            refreshAllEnabledSources()
        }.onFailure { err ->
            postStatus(err.message ?: "Token o enlace no válido")
        }
        return result
    }

    suspend fun signOut() {
        authManager.signOut()
        postStatus("Sesión de Google Drive desconectada")
    }

    // ─── Multiple Sources CRUD ────────────────────────────────────────────────

    suspend fun addSourceFromLink(
        rawLinkOrId: String,
        customName: String,
        accentColorHex: Long,
        makeDefault: Boolean
    ): Result<DriveSourceEntity> = withContext(Dispatchers.IO) {
        val folderId = DriveFolderLinkParser.extractFolderId(rawLinkOrId)
            ?: return@withContext Result.failure(
                DriveApiException(
                    400,
                    "Enlace inválido. Pega un enlace de carpeta de Google Drive (drive.google.com/drive/folders/…)."
                )
            )

        val existing = driveDao.getSourceByFolderId(folderId)
        if (existing != null) {
            return@withContext Result.failure(
                DriveApiException(409, "Esta carpeta ya está agregada como '${existing.name}'.")
            )
        }

        val validation = runCatching { apiClient.validateFolder(folderId) }
        val remoteInfo = validation.getOrElse { err ->
            val msg = (err as? DriveApiException)?.userMessage ?: err.localizedMessage ?: "No se pudo validar la carpeta."
            postStatus(msg)
            return@withContext Result.failure(err)
        }

        val currentSources = driveDao.getSourcesOnce()
        val shouldBeDefault = makeDefault || currentSources.isEmpty()
        if (shouldBeDefault) {
            driveDao.clearDefaultSourceFlags()
        }

        val entity = DriveSourceEntity(
            name = customName.trim().ifBlank { remoteInfo.name },
            folderId = folderId,
            folderLink = rawLinkOrId.trim(),
            accentColorHex = accentColorHex,
            enabled = true,
            isDefaultSaveTarget = shouldBeDefault,
            lastSyncedAt = 0L
        )
        val newId = driveDao.upsertSource(entity)
        val saved = entity.copy(id = newId)
        postStatus("Fuente '${saved.name}' validada y conectada")
        refreshFolder(saved.id, saved.folderId, loadMore = false)
        Result.success(saved)
    }

    suspend fun setSourceEnabled(sourceId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        driveDao.setSourceEnabled(sourceId, enabled)
    }

    suspend fun setDefaultSource(sourceId: Long) = withContext(Dispatchers.IO) {
        driveDao.clearDefaultSourceFlags()
        driveDao.setDefaultSourceFlag(sourceId)
        postStatus("Carpeta de guardado por defecto actualizada")
    }

    suspend fun updateSourceAppearance(sourceId: Long, name: String, colorHex: Long) = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isNotEmpty()) {
            driveDao.updateSourceAppearance(sourceId, clean, colorHex)
        }
    }

    suspend fun deleteSource(sourceId: Long) = withContext(Dispatchers.IO) {
        driveDao.deleteFilesForSource(sourceId)
        driveDao.deleteSourceById(sourceId)
        val remaining = driveDao.getSourcesOnce()
        if (remaining.isNotEmpty() && remaining.none { it.isDefaultSaveTarget }) {
            driveDao.setDefaultSourceFlag(remaining.first().id)
        }
        postStatus("Fuente desconectada (tus archivos en Drive siguen intactos)")
    }

    suspend fun getDefaultSource(): DriveSourceEntity? = withContext(Dispatchers.IO) {
        val all = driveDao.getSourcesOnce()
        all.firstOrNull { it.isDefaultSaveTarget && it.enabled } ?: all.firstOrNull { it.enabled }
    }

    // ─── Folder Browsing, Pagination & Lazy Thumbnails ────────────────────────

    suspend fun refreshAllEnabledSources() = withContext(Dispatchers.IO) {
        val enabled = driveDao.getSourcesOnce().filter { it.enabled }
        if (enabled.isEmpty()) return@withContext
        _isRefreshing.value = true
        try {
            var lastErr: String? = null
            for (src in enabled) {
                refreshFolder(src.id, src.folderId, loadMore = false).onFailure { err ->
                    lastErr = (err as? DriveApiException)?.userMessage ?: err.localizedMessage
                }
            }
            if (lastErr != null) {
                postStatus(lastErr!!)
            } else {
                postStatus("Fuentes de Drive actualizadas")
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    suspend fun refreshFolder(
        sourceId: Long,
        folderId: String,
        loadMore: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        val pageKey = "$sourceId:$folderId"
        val tokenToUse = if (loadMore) nextPageTokens[pageKey] else null
        if (loadMore && tokenToUse.isNullOrBlank()) return@withContext Result.success(0)

        _isRefreshing.value = true
        try {
            val page = apiClient.listFolderPage(folderId = folderId, pageToken = tokenToUse, pageSize = 50)
            if (page.nextPageToken != null) {
                nextPageTokens[pageKey] = page.nextPageToken
            } else {
                nextPageTokens.remove(pageKey)
            }
            _hasMoreByFolder.value = _hasMoreByFolder.value.toMutableMap().apply {
                put(pageKey, page.nextPageToken != null)
            }

            val existingMap = driveDao.getFolderFilesOnce(sourceId, folderId).associateBy { it.driveFileId }
            val mapped = page.files.map { remote ->
                val prev = existingMap[remote.id]
                val hasLocal = prev != null && prev.localFilePath.isNotBlank() && File(prev.localFilePath).exists()
                val nextStatus = when {
                    prev == null -> DriveFileSyncStatus.CLOUD_ONLY.name
                    prev.status == DriveFileSyncStatus.PENDING_UPLOAD && remote.modifiedTimeMs > prev.modifiedTimeMs -> {
                        // Both local & remote changed -> flag conflict!
                        _activeConflict.value = DriveConflictItem(
                            driveFileId = remote.id,
                            fileName = remote.name,
                            sourceId = sourceId,
                            parentFolderId = folderId,
                            localModifiedMs = prev.localModifiedAt,
                            remoteModifiedMs = remote.modifiedTimeMs,
                            localFilePath = prev.localFilePath,
                            linkedDocumentId = prev.linkedDocumentId,
                            linkedNotebookId = prev.linkedNotebookId
                        )
                        DriveFileSyncStatus.CONFLICT.name
                    }
                    prev.status == DriveFileSyncStatus.PENDING_UPLOAD -> DriveFileSyncStatus.PENDING_UPLOAD.name
                    hasLocal -> DriveFileSyncStatus.SYNCED.name
                    else -> DriveFileSyncStatus.CLOUD_ONLY.name
                }

                DriveFileEntity(
                    driveFileId = remote.id,
                    sourceId = sourceId,
                    parentFolderId = folderId,
                    name = remote.name,
                    mimeType = remote.mimeType,
                    sizeBytes = remote.sizeBytes,
                    modifiedTimeMs = remote.modifiedTimeMs,
                    md5Checksum = remote.md5Checksum,
                    thumbnailLink = remote.thumbnailLink,
                    localFilePath = if (hasLocal) prev!!.localFilePath else "",
                    localThumbnailPath = prev?.localThumbnailPath.orEmpty(),
                    linkedDocumentId = prev?.linkedDocumentId ?: 0L,
                    linkedNotebookId = prev?.linkedNotebookId ?: 0L,
                    syncStatus = nextStatus,
                    localModifiedAt = prev?.localModifiedAt ?: 0L,
                    lastError = ""
                )
            }
            driveDao.upsertFiles(mapped)

            if (!loadMore && page.nextPageToken == null) {
                val keepIds = mapped.map { it.driveFileId }
                if (keepIds.isNotEmpty()) {
                    driveDao.pruneMissingFolderFiles(sourceId, folderId, keepIds)
                }
            }
            driveDao.updateSourceLastSynced(sourceId, System.currentTimeMillis())
            Result.success(mapped.size)
        } catch (e: DriveApiException) {
            postStatus(e.userMessage)
            Result.failure(e)
        } catch (t: Throwable) {
            postStatus(t.localizedMessage ?: "No se pudo leer la carpeta de Drive")
            Result.failure(t)
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * Lazy-loads a single Drive file thumbnail on demand (never blocks UI scroll).
     */
    suspend fun ensureThumbnailLoaded(file: DriveFileEntity): String? = withContext(Dispatchers.IO) {
        if (file.localThumbnailPath.isNotBlank() && File(file.localThumbnailPath).exists()) {
            return@withContext file.localThumbnailPath
        }
        val thumbDir = File(appContext.cacheDir, "drive_thumbs").apply { mkdirs() }
        val destThumb = File(thumbDir, "thumb_${DriveFolderLinkParser.sanitizeLocalFileName(file.driveFileId)}.jpg")

        // If already downloaded locally, generate real first-page PDF/image/.luma thumbnail locally!
        if (file.isDownloadedLocally) {
            val localFile = File(file.localFilePath)
            val generated = when (file.kind) {
                DriveFileKind.PDF -> CoverManager.generateOrExtractCover(
                    context = appContext,
                    sourceFile = localFile,
                    format = DocumentFormat.PDF,
                    title = file.name.substringBeforeLast('.'),
                    author = "Google Drive",
                    accentColorHex = 0xFF2563EBL
                )
                DriveFileKind.LUMA_NOTEBOOK -> LumaNotebookPackager.extractThumbnailFromPackage(localFile, destThumb)
                DriveFileKind.IMAGE -> localFile.absolutePath
                else -> null
            }
            if (!generated.isNullOrBlank()) {
                driveDao.updateLocalThumbnail(file.driveFileId, generated)
                return@withContext generated
            }
        }

        if (file.thumbnailLink.isNotBlank()) {
            if (apiClient.downloadThumbnail(file.thumbnailLink, destThumb)) {
                driveDao.updateLocalThumbnail(file.driveFileId, destThumb.absolutePath)
                return@withContext destThumb.absolutePath
            }
        }
        null
    }

    // ─── Tap-to-Open, Resumable Download, Pause/Cancel & Free Space ───────────

    fun pauseActiveTransfer() {
        transferPaused.set(true)
        _activeTransfer.value = _activeTransfer.value?.copy(isPaused = true, speedBytesPerSec = 0L)
    }

    fun resumeActiveTransfer() {
        transferPaused.set(false)
        _activeTransfer.value = _activeTransfer.value?.copy(isPaused = false)
    }

    fun cancelActiveTransfer() {
        transferCancelled.set(true)
        transferPaused.set(false)
    }

    /**
     * Opens an already-downloaded Drive item immediately, or downloads it with resumable progress
     * and imports it into Luma's Reader or Study Notebook Studio.
     */
    suspend fun downloadAndOpenFile(file: DriveFileEntity): Result<DriveOpenTarget> = withContext(Dispatchers.IO) {
        // Already downloaded & linked -> open directly without asking!
        if (file.isDownloadedLocally) {
            resolveExistingOpenTarget(file)?.let { return@withContext Result.success(it) }
        }

        val cacheDir = File(appContext.filesDir, "drive_cache").apply { mkdirs() }
        val extSuffix = resolveExpectedExtension(file)
        val safeBase = DriveFolderLinkParser.sanitizeLocalFileName(file.name)
        val finalName = if (extSuffix.isNotEmpty() && !safeBase.lowercase().endsWith(extSuffix)) {
            "${safeBase}$extSuffix"
        } else safeBase
        val destFile = File(cacheDir, "${file.driveFileId}_$finalName")
        val canonicalRoot = cacheDir.canonicalPath + File.separator
        if (!destFile.canonicalPath.startsWith(canonicalRoot)) {
            return@withContext Result.failure(DriveApiException(400, "Nombre de archivo inválido."))
        }

        transferPaused.set(false)
        transferCancelled.set(false)
        driveDao.updateFileSyncStatus(file.driveFileId, DriveFileSyncStatus.DOWNLOADING.name)
        _activeTransfer.value = DriveTransferProgress(
            transferId = file.driveFileId,
            fileName = file.name,
            isUpload = false,
            bytesTransferred = 0L,
            totalBytes = file.sizeBytes,
            speedBytesPerSec = 0L
        )

        return@withContext try {
            val downloadedFile = apiClient.downloadFileResumable(
                driveFileId = file.driveFileId,
                mimeType = file.mimeType,
                destFile = destFile,
                expectedSizeBytes = file.sizeBytes,
                isPaused = { transferPaused.get() },
                isCancelled = { transferCancelled.get() },
                onProgress = { bytes, total, speed ->
                    _activeTransfer.value = DriveTransferProgress(
                        transferId = file.driveFileId,
                        fileName = file.name,
                        isUpload = false,
                        bytesTransferred = bytes,
                        totalBytes = total,
                        speedBytesPerSec = speed,
                        isPaused = transferPaused.get()
                    )
                }
            )
            _activeTransfer.value = null
            val openTarget = importLocalDriveFileToLuma(file, downloadedFile)
            Result.success(openTarget)
        } catch (e: GoogleDriveApiClient.DownloadCancelledException) {
            _activeTransfer.value = null
            driveDao.updateFileSyncStatus(file.driveFileId, DriveFileSyncStatus.CLOUD_ONLY.name)
            postStatus("Descarga cancelada")
            Result.failure(e)
        } catch (e: DriveApiException) {
            _activeTransfer.value = null
            driveDao.updateFileSyncStatus(file.driveFileId, DriveFileSyncStatus.ERROR.name, e.userMessage)
            postStatus(e.userMessage)
            Result.failure(e)
        } catch (t: Throwable) {
            _activeTransfer.value = null
            val msg = t.localizedMessage ?: "Error descargando archivo de Google Drive."
            driveDao.updateFileSyncStatus(file.driveFileId, DriveFileSyncStatus.ERROR.name, msg)
            postStatus(msg)
            Result.failure(t)
        }
    }

    private suspend fun resolveExistingOpenTarget(file: DriveFileEntity): DriveOpenTarget? {
        if (file.linkedNotebookId > 0L && notebookRepository.getNotebook(file.linkedNotebookId) != null) {
            return DriveOpenTarget.OpenNotebook(file.linkedNotebookId)
        }
        if (file.linkedDocumentId > 0L && folioRepository.getDocumentById(file.linkedDocumentId) != null) {
            return DriveOpenTarget.OpenDocument(file.linkedDocumentId)
        }
        val localFile = File(file.localFilePath)
        if (localFile.exists()) {
            return importLocalDriveFileToLuma(file, localFile)
        }
        return null
    }

    private suspend fun importLocalDriveFileToLuma(
        file: DriveFileEntity,
        localFile: File
    ): DriveOpenTarget {
        val kind = file.kind
        when (kind) {
            DriveFileKind.LUMA_NOTEBOOK -> {
                val nbId = LumaNotebookPackager.importNotebookPackage(
                    context = appContext,
                    notebookDao = db.notebookDao(),
                    lumaFile = localFile,
                    overwriteNotebookId = file.linkedNotebookId.takeIf { it > 0L }
                )
                val nb = notebookRepository.getNotebook(nbId)
                driveDao.upsertFile(
                    file.copy(
                        localFilePath = localFile.absolutePath,
                        localThumbnailPath = nb?.coverImagePath?.takeIf { it.isNotBlank() } ?: file.localThumbnailPath,
                        linkedNotebookId = nbId,
                        syncStatus = DriveFileSyncStatus.SYNCED.name,
                        localModifiedAt = System.currentTimeMillis(),
                        lastError = ""
                    )
                )
                return DriveOpenTarget.OpenNotebook(nbId)
            }
            DriveFileKind.IMAGE -> {
                val title = file.name.substringBeforeLast('.').ifBlank { "Imagen de Drive" }
                val nbId = notebookRepository.createNotebookFromImageUri(title, Uri.fromFile(localFile))
                driveDao.upsertFile(
                    file.copy(
                        localFilePath = localFile.absolutePath,
                        localThumbnailPath = localFile.absolutePath,
                        linkedNotebookId = nbId,
                        syncStatus = DriveFileSyncStatus.SYNCED.name,
                        localModifiedAt = System.currentTimeMillis(),
                        lastError = ""
                    )
                )
                return DriveOpenTarget.OpenNotebook(nbId)
            }
            else -> {
                val format = when (kind) {
                    DriveFileKind.PDF -> DocumentFormat.PDF
                    DriveFileKind.DOCX -> DocumentFormat.DOCX
                    DriveFileKind.EPUB -> DocumentFormat.EPUB
                    DriveFileKind.PPTX -> DocumentFormat.PPTX
                    DriveFileKind.TXT -> DocumentFormat.TXT
                    DriveFileKind.FB2 -> DocumentFormat.FB2
                    else -> DocumentFormat.fromExtension(localFile.extension)
                        ?: throw DriveApiException(415, "Formato no soportado para lectura directa: ${file.name}")
                }
                val parsed = DocumentParser.parse(appContext, localFile, format)
                val cleanTitle = parsed.title.ifBlank { file.name.substringBeforeLast('.') }
                val coverPath = CoverManager.generateOrExtractCover(
                    context = appContext,
                    sourceFile = localFile,
                    format = format,
                    title = cleanTitle,
                    author = parsed.author,
                    accentColorHex = 0xFF2563EBL
                )
                val parsedUnits = when {
                    parsed.pdfPageCount > 0 -> parsed.pdfPageCount
                    parsed.slides.isNotEmpty() -> parsed.slides.size
                    else -> parsed.chapters.size.coerceAtLeast(1)
                }
                val existingDoc = if (file.linkedDocumentId > 0L) folioRepository.getDocumentById(file.linkedDocumentId) else null
                val docId = if (existingDoc != null) {
                    folioRepository.insertDocument(
                        existingDoc.copy(
                            title = cleanTitle,
                            filePath = localFile.absolutePath,
                            coverImagePath = coverPath,
                            totalUnits = parsedUnits,
                            lastOpenedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    folioRepository.insertDocument(
                        DocumentEntity(
                            title = cleanTitle,
                            author = parsed.author.ifBlank { "Google Drive" },
                            format = format.name,
                            filePath = localFile.absolutePath,
                            coverImagePath = coverPath,
                            coverColorHex = 0xFF2563EBL,
                            badgeTag = "DRIVE",
                            totalUnits = parsedUnits,
                            lastOpenedAt = System.currentTimeMillis()
                        )
                    )
                }
                driveDao.upsertFile(
                    file.copy(
                        localFilePath = localFile.absolutePath,
                        localThumbnailPath = coverPath.ifBlank { file.localThumbnailPath },
                        linkedDocumentId = docId,
                        syncStatus = DriveFileSyncStatus.SYNCED.name,
                        localModifiedAt = System.currentTimeMillis(),
                        lastError = ""
                    )
                )
                return DriveOpenTarget.OpenDocument(docId)
            }
        }
    }

    /**
     * Deletes ONLY the local downloaded copy to reclaim storage; leaves the Drive file untouched.
     */
    suspend fun freeLocalSpace(file: DriveFileEntity): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        if (file.localFilePath.isNotBlank()) {
            val f = File(file.localFilePath)
            if (f.exists()) {
                reclaimed = f.length()
                runCatching { f.delete() }
            }
        }
        driveDao.clearLocalCopy(file.driveFileId)
        postStatus("Copia local eliminada (${DriveFolderLinkParser.formatBytes(reclaimed)} liberados). Tu archivo sigue en Drive.")
        reclaimed
    }

    // ─── Uploading, Syncing, Renaming, Moving, Deleting & Offline Queue ───────

    suspend fun syncDocumentToDrive(
        documentId: Long,
        targetSourceId: Long? = null,
        targetFolderId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val doc = folioRepository.getDocumentById(documentId)
            ?: return@withContext Result.failure(DriveApiException(404, "Documento local no encontrado."))
        val localFile = File(doc.filePath)
        if (!localFile.exists()) {
            return@withContext Result.failure(DriveApiException(404, "El archivo del libro ya no existe en el dispositivo."))
        }
        val source = targetSourceId?.let { driveDao.getSourceById(it) } ?: getDefaultSource()
            ?: return@withContext Result.failure(
                DriveApiException(400, "Agrega primero una carpeta de Google Drive en Ajustes → Fuentes.")
            )
        val folderId = targetFolderId?.takeIf { it.isNotBlank() } ?: source.folderId
        val linked = driveDao.getFileByLinkedDocumentId(documentId)
        val ext = doc.format.lowercase()
        val remoteName = if (doc.title.lowercase().endsWith(".$ext")) doc.title else "${doc.title}.$ext"
        val mime = mimeForFormat(doc.format)

        if (!_isOnline.value) {
            enqueuePendingTask(
                DriveSyncTaskEntity(
                    action = DriveSyncAction.UPLOAD_DOCUMENT.name,
                    driveFileId = linked?.driveFileId.orEmpty(),
                    sourceId = source.id,
                    targetFolderId = folderId,
                    localFilePath = localFile.absolutePath,
                    localDocumentId = documentId,
                    displayName = remoteName,
                    mimeType = mime
                )
            )
            linked?.let { driveDao.updateFileSyncStatus(it.driveFileId, DriveFileSyncStatus.PENDING_UPLOAD.name) }
            postStatus("Sin internet: '$remoteName' quedó en cola para sincronizar automáticamente")
            return@withContext Result.success(Unit)
        }

        uploadNowInternal(
            localFile = localFile,
            remoteName = remoteName,
            mimeType = mime,
            sourceId = source.id,
            parentFolderId = folderId,
            existingFile = linked,
            linkedDocumentId = documentId,
            linkedNotebookId = 0L,
            localThumbPath = doc.coverImagePath
        )
    }

    suspend fun syncNotebookToDrive(
        notebookId: Long,
        targetSourceId: Long? = null,
        targetFolderId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val nb = notebookRepository.getNotebook(notebookId)
            ?: return@withContext Result.failure(DriveApiException(404, "Cuaderno local no encontrado."))
        val source = targetSourceId?.let { driveDao.getSourceById(it) } ?: getDefaultSource()
            ?: return@withContext Result.failure(
                DriveApiException(400, "Agrega primero una carpeta de Google Drive en Ajustes → Fuentes.")
            )
        val folderId = targetFolderId?.takeIf { it.isNotBlank() } ?: source.folderId
        val linked = driveDao.getFileByLinkedNotebookId(notebookId)

        val pkgDir = File(appContext.filesDir, "drive_cache/notebooks").apply { mkdirs() }
        val safeTitle = DriveFolderLinkParser.sanitizeLocalFileName(nb.title, fallback = "Cuaderno")
        val pkgFile = File(pkgDir, "notebook_${notebookId}.luma")
        LumaNotebookPackager.exportNotebookPackage(appContext, db.notebookDao(), notebookId, pkgFile)
        val remoteName = "$safeTitle.luma"

        if (!_isOnline.value) {
            enqueuePendingTask(
                DriveSyncTaskEntity(
                    action = DriveSyncAction.UPLOAD_NOTEBOOK.name,
                    driveFileId = linked?.driveFileId.orEmpty(),
                    sourceId = source.id,
                    targetFolderId = folderId,
                    localFilePath = pkgFile.absolutePath,
                    localNotebookId = notebookId,
                    displayName = remoteName,
                    mimeType = DriveFileKind.LUMA_MIME
                )
            )
            linked?.let { driveDao.updateFileSyncStatus(it.driveFileId, DriveFileSyncStatus.PENDING_UPLOAD.name) }
            postStatus("Sin internet: cuaderno '${nb.title}' en cola para subir al volver la conexión")
            return@withContext Result.success(Unit)
        }

        uploadNowInternal(
            localFile = pkgFile,
            remoteName = remoteName,
            mimeType = DriveFileKind.LUMA_MIME,
            sourceId = source.id,
            parentFolderId = folderId,
            existingFile = linked,
            linkedDocumentId = 0L,
            linkedNotebookId = notebookId,
            localThumbPath = nb.coverImagePath
        )
    }

    suspend fun syncDriveFile(file: DriveFileEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (file.linkedNotebookId > 0L) {
            return@withContext syncNotebookToDrive(file.linkedNotebookId, file.sourceId, file.parentFolderId)
        }
        if (file.linkedDocumentId > 0L) {
            return@withContext syncDocumentToDrive(file.linkedDocumentId, file.sourceId, file.parentFolderId)
        }
        if (file.isDownloadedLocally) {
            return@withContext uploadNowInternal(
                localFile = File(file.localFilePath),
                remoteName = file.name,
                mimeType = file.mimeType,
                sourceId = file.sourceId,
                parentFolderId = file.parentFolderId,
                existingFile = file,
                linkedDocumentId = file.linkedDocumentId,
                linkedNotebookId = file.linkedNotebookId,
                localThumbPath = file.localThumbnailPath
            )
        }
        refreshFolder(file.sourceId, file.parentFolderId, loadMore = false).map { Unit }
    }

    private suspend fun uploadNowInternal(
        localFile: File,
        remoteName: String,
        mimeType: String,
        sourceId: Long,
        parentFolderId: String,
        existingFile: DriveFileEntity?,
        linkedDocumentId: Long,
        linkedNotebookId: Long,
        localThumbPath: String,
        ignoreConflict: Boolean = false
    ): Result<Unit> {
        // Conflict detection: if updating an existing Drive file and remote changed since our last sync
        if (!ignoreConflict && existingFile != null && existingFile.driveFileId.isNotBlank()) {
            val remoteMeta = runCatching { apiClient.getFileMetadata(existingFile.driveFileId) }.getOrNull()
            if (remoteMeta != null && existingFile.modifiedTimeMs > 0L && remoteMeta.modifiedTimeMs > existingFile.modifiedTimeMs + 2000L) {
                driveDao.updateFileSyncStatus(existingFile.driveFileId, DriveFileSyncStatus.CONFLICT.name)
                _activeConflict.value = DriveConflictItem(
                    driveFileId = existingFile.driveFileId,
                    fileName = remoteName,
                    sourceId = sourceId,
                    parentFolderId = parentFolderId,
                    localModifiedMs = localFile.lastModified(),
                    remoteModifiedMs = remoteMeta.modifiedTimeMs,
                    localFilePath = localFile.absolutePath,
                    linkedDocumentId = linkedDocumentId,
                    linkedNotebookId = linkedNotebookId
                )
                postStatus("Conflicto detectado en '$remoteName': elige qué versión conservar")
                return Result.success(Unit)
            }
        }

        existingFile?.let { driveDao.updateFileSyncStatus(it.driveFileId, DriveFileSyncStatus.UPLOADING.name) }
        _activeTransfer.value = DriveTransferProgress(
            transferId = existingFile?.driveFileId ?: remoteName,
            fileName = remoteName,
            isUpload = true,
            bytesTransferred = 0L,
            totalBytes = localFile.length(),
            speedBytesPerSec = 0L
        )
        return try {
            val remote = apiClient.uploadFileResumable(
                localFile = localFile,
                remoteName = remoteName,
                mimeType = mimeType,
                parentFolderId = parentFolderId,
                existingDriveFileId = existingFile?.driveFileId?.takeIf { it.isNotBlank() },
                onProgress = { uploaded, total, speed ->
                    _activeTransfer.value = DriveTransferProgress(
                        transferId = existingFile?.driveFileId ?: remoteName,
                        fileName = remoteName,
                        isUpload = true,
                        bytesTransferred = uploaded,
                        totalBytes = total,
                        speedBytesPerSec = speed
                    )
                }
            )
            _activeTransfer.value = null
            driveDao.upsertFile(
                DriveFileEntity(
                    driveFileId = remote.id,
                    sourceId = sourceId,
                    parentFolderId = parentFolderId,
                    name = remote.name,
                    mimeType = remote.mimeType,
                    sizeBytes = remote.sizeBytes.takeIf { it > 0L } ?: localFile.length(),
                    modifiedTimeMs = remote.modifiedTimeMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    md5Checksum = remote.md5Checksum,
                    thumbnailLink = remote.thumbnailLink,
                    localFilePath = localFile.absolutePath,
                    localThumbnailPath = localThumbPath,
                    linkedDocumentId = linkedDocumentId,
                    linkedNotebookId = linkedNotebookId,
                    syncStatus = DriveFileSyncStatus.SYNCED.name,
                    localModifiedAt = System.currentTimeMillis(),
                    lastError = ""
                )
            )
            postStatus("'$remoteName' sincronizado con Google Drive")
            Result.success(Unit)
        } catch (e: DriveApiException) {
            _activeTransfer.value = null
            if (e.isOffline) {
                enqueuePendingTask(
                    DriveSyncTaskEntity(
                        action = if (linkedNotebookId > 0L) DriveSyncAction.UPLOAD_NOTEBOOK.name else DriveSyncAction.UPLOAD_DOCUMENT.name,
                        driveFileId = existingFile?.driveFileId.orEmpty(),
                        sourceId = sourceId,
                        targetFolderId = parentFolderId,
                        localFilePath = localFile.absolutePath,
                        localDocumentId = linkedDocumentId,
                        localNotebookId = linkedNotebookId,
                        displayName = remoteName,
                        mimeType = mimeType
                    )
                )
                existingFile?.let { driveDao.updateFileSyncStatus(it.driveFileId, DriveFileSyncStatus.PENDING_UPLOAD.name) }
            } else {
                existingFile?.let { driveDao.updateFileSyncStatus(it.driveFileId, DriveFileSyncStatus.ERROR.name, e.userMessage) }
            }
            postStatus(e.userMessage)
            Result.failure(e)
        }
    }

    suspend fun resolveConflict(conflict: DriveConflictItem, resolution: DriveConflictResolution) = withContext(Dispatchers.IO) {
        _activeConflict.value = null
        val current = driveDao.getFileById(conflict.driveFileId) ?: return@withContext
        when (resolution) {
            DriveConflictResolution.KEEP_LOCAL -> {
                val localFile = File(conflict.localFilePath)
                if (localFile.exists()) {
                    uploadNowInternal(
                        localFile = localFile,
                        remoteName = conflict.fileName,
                        mimeType = current.mimeType,
                        sourceId = conflict.sourceId,
                        parentFolderId = conflict.parentFolderId,
                        existingFile = current,
                        linkedDocumentId = conflict.linkedDocumentId,
                        linkedNotebookId = conflict.linkedNotebookId,
                        localThumbPath = current.localThumbnailPath,
                        ignoreConflict = true
                    )
                }
            }
            DriveConflictResolution.KEEP_REMOTE -> {
                // Clear local copy so downloadAndOpenFile pulls fresh remote version
                driveDao.clearLocalCopy(current.driveFileId)
                downloadAndOpenFile(current.copy(localFilePath = "", syncStatus = DriveFileSyncStatus.CLOUD_ONLY.name))
                postStatus("Versión de Google Drive restaurada")
            }
            DriveConflictResolution.KEEP_BOTH -> {
                val localFile = File(conflict.localFilePath)
                if (localFile.exists()) {
                    val base = conflict.fileName.substringBeforeLast('.')
                    val ext = conflict.fileName.substringAfterLast('.', "")
                    val renamed = if (ext.isNotEmpty() && base != conflict.fileName) {
                        "$base (copia local).$ext"
                    } else "${conflict.fileName} (copia local)"
                    uploadNowInternal(
                        localFile = localFile,
                        remoteName = renamed,
                        mimeType = current.mimeType,
                        sourceId = conflict.sourceId,
                        parentFolderId = conflict.parentFolderId,
                        existingFile = null,
                        linkedDocumentId = conflict.linkedDocumentId,
                        linkedNotebookId = conflict.linkedNotebookId,
                        localThumbPath = current.localThumbnailPath,
                        ignoreConflict = true
                    )
                    driveDao.updateFileSyncStatus(current.driveFileId, DriveFileSyncStatus.SYNCED.name)
                    postStatus("Ambas versiones conservadas ('$renamed')")
                }
            }
        }
    }

    fun dismissConflictDialog() {
        _activeConflict.value = null
    }

    suspend fun renameRemoteFile(file: DriveFileEntity, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isEmpty()) return@withContext Result.success(Unit)
        if (!_isOnline.value) {
            enqueuePendingTask(
                DriveSyncTaskEntity(
                    action = DriveSyncAction.RENAME_REMOTE.name,
                    driveFileId = file.driveFileId,
                    sourceId = file.sourceId,
                    targetFolderId = file.parentFolderId,
                    displayName = clean
                )
            )
            driveDao.upsertFile(file.copy(name = clean, syncStatus = DriveFileSyncStatus.PENDING_UPLOAD.name))
            postStatus("Renombrado en cola para cuando vuelva internet")
            return@withContext Result.success(Unit)
        }
        runCatching {
            val updated = apiClient.renameFile(file.driveFileId, clean)
            driveDao.upsertFile(
                file.copy(
                    name = updated.name,
                    modifiedTimeMs = updated.modifiedTimeMs.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            )
            postStatus("Renombrado a '${updated.name}'")
        }.onFailure { err ->
            postStatus((err as? DriveApiException)?.userMessage ?: "No se pudo renombrar en Drive")
        }
    }

    suspend fun moveRemoteFile(file: DriveFileEntity, destinationSource: DriveSourceEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (!_isOnline.value) {
            enqueuePendingTask(
                DriveSyncTaskEntity(
                    action = DriveSyncAction.MOVE_REMOTE.name,
                    driveFileId = file.driveFileId,
                    sourceId = destinationSource.id,
                    targetFolderId = destinationSource.folderId,
                    previousFolderId = file.parentFolderId,
                    displayName = file.name
                )
            )
            driveDao.upsertFile(
                file.copy(
                    sourceId = destinationSource.id,
                    parentFolderId = destinationSource.folderId,
                    syncStatus = DriveFileSyncStatus.PENDING_UPLOAD.name
                )
            )
            postStatus("Movimiento a '${destinationSource.name}' en cola offline")
            return@withContext Result.success(Unit)
        }
        runCatching {
            apiClient.moveFile(file.driveFileId, destinationSource.folderId, file.parentFolderId)
            driveDao.upsertFile(
                file.copy(
                    sourceId = destinationSource.id,
                    parentFolderId = destinationSource.folderId
                )
            )
            postStatus("Movido a '${destinationSource.name}'")
        }.onFailure { err ->
            postStatus((err as? DriveApiException)?.userMessage ?: "No se pudo mover en Drive")
        }
    }

    suspend fun deleteRemoteFile(file: DriveFileEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (!_isOnline.value) {
            enqueuePendingTask(
                DriveSyncTaskEntity(
                    action = DriveSyncAction.DELETE_REMOTE.name,
                    driveFileId = file.driveFileId,
                    sourceId = file.sourceId,
                    displayName = file.name
                )
            )
            driveDao.deleteFileById(file.driveFileId)
            postStatus("Eliminación de Drive en cola offline")
            return@withContext Result.success(Unit)
        }
        runCatching {
            apiClient.deleteFile(file.driveFileId)
            if (file.localFilePath.isNotBlank()) {
                runCatching { File(file.localFilePath).delete() }
            }
            driveDao.deleteFileById(file.driveFileId)
            postStatus("'${file.name}' eliminado de Google Drive")
        }.onFailure { err ->
            postStatus((err as? DriveApiException)?.userMessage ?: "No se pudo eliminar de Drive")
        }
    }

    /**
     * Drains all pending offline tasks and refreshes enabled Drive folders.
     */
    suspend fun syncAllNow() = withContext(Dispatchers.IO) {
        if (!_isOnline.value) {
            postStatus("Sin conexión a internet. Tus cambios siguen seguros en la cola local.")
            return@withContext
        }
        _isSyncing.value = true
        try {
            processSyncQueue()
            refreshAllEnabledSources()
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun processSyncQueue() = withContext(Dispatchers.IO) {
        if (!_isOnline.value) return@withContext
        val tasks = driveDao.getSyncQueueOnce()
        if (tasks.isEmpty()) return@withContext
        _isSyncing.value = true
        try {
            for (task in tasks) {
                val action = runCatching { DriveSyncAction.valueOf(task.action) }.getOrNull() ?: continue
                val res = when (action) {
                    DriveSyncAction.UPLOAD_DOCUMENT -> {
                        if (task.localDocumentId > 0L) {
                            syncDocumentToDrive(task.localDocumentId, task.sourceId, task.targetFolderId)
                        } else Result.success(Unit)
                    }
                    DriveSyncAction.UPLOAD_NOTEBOOK -> {
                        if (task.localNotebookId > 0L) {
                            syncNotebookToDrive(task.localNotebookId, task.sourceId, task.targetFolderId)
                        } else Result.success(Unit)
                    }
                    DriveSyncAction.RENAME_REMOTE -> {
                        runCatching { apiClient.renameFile(task.driveFileId, task.displayName); Unit }
                    }
                    DriveSyncAction.MOVE_REMOTE -> {
                        runCatching { apiClient.moveFile(task.driveFileId, task.targetFolderId, task.previousFolderId); Unit }
                    }
                    DriveSyncAction.DELETE_REMOTE -> {
                        runCatching { apiClient.deleteFile(task.driveFileId); Unit }
                    }
                }
                if (res.isSuccess) {
                    driveDao.deleteSyncTask(task.taskId)
                } else {
                    val errMsg = res.exceptionOrNull()?.localizedMessage ?: "Error de sincronización"
                    driveDao.markSyncTaskError(task.taskId, errMsg)
                    if ((res.exceptionOrNull() as? DriveApiException)?.isOffline == true) break
                }
            }
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun enqueuePendingTask(task: DriveSyncTaskEntity) {
        driveDao.enqueueSyncTask(task)
    }

    private fun canAutoSyncNow(): Boolean {
        if (!_isOnline.value || !vault.authSnapshot.value.isSignedIn) return false
        return when (vault.getSyncMode()) {
            DriveSyncMode.MANUAL -> false
            DriveSyncMode.WIFI_ONLY -> _isOnWifi.value
            DriveSyncMode.ALWAYS -> true
        }
    }

    private fun registerNetworkMonitor() {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    _isOnWifi.value = checkWifiNow()
                    if (canAutoSyncNow()) {
                        scope.launch { processSyncQueue() }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val hasNet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    _isOnline.value = hasNet
                    _isOnWifi.value = wifi
                    if (hasNet && canAutoSyncNow()) {
                        scope.launch { processSyncQueue() }
                    }
                }

                override fun onLost(network: Network) {
                    _isOnline.value = checkOnlineNow()
                    _isOnWifi.value = checkWifiNow()
                }
            })
        }
    }

    private fun checkOnlineNow(): Boolean {
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    private fun checkWifiNow(): Boolean {
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(false)
    }

    private fun resolveExpectedExtension(file: DriveFileEntity): String = when (file.kind) {
        DriveFileKind.PDF -> ".pdf"
        DriveFileKind.DOCX -> ".docx"
        DriveFileKind.EPUB -> ".epub"
        DriveFileKind.PPTX -> ".pptx"
        DriveFileKind.TXT -> ".txt"
        DriveFileKind.FB2 -> ".fb2"
        DriveFileKind.LUMA_NOTEBOOK -> ".luma"
        else -> ""
    }

    private fun mimeForFormat(format: String): String = when (format.uppercase()) {
        "PDF" -> "application/pdf"
        "EPUB" -> "application/epub+zip"
        "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "TXT" -> "text/plain"
        "FB2" -> "application/x-fictionbook+xml"
        else -> "application/octet-stream"
    }

    companion object {
        @Volatile
        private var INSTANCE: DriveRepository? = null

        fun getInstance(context: Context): DriveRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DriveRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
