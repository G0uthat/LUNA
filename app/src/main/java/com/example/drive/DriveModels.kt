package com.example.drive

import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class DriveSyncMode(val label: String, val subtitle: String) {
    MANUAL("Manual", "Solo sincroniza cuando presionas Sincronizar"),
    WIFI_ONLY("Solo con Wi-Fi", "Sube y actualiza automáticamente solo en Wi-Fi"),
    ALWAYS("Siempre", "Sincroniza automáticamente con Wi-Fi o datos móviles")
}

enum class DriveFileSyncStatus(val label: String) {
    CLOUD_ONLY("En la nube"),
    SYNCED("Sincronizado"),
    PENDING_UPLOAD("Pendiente"),
    UPLOADING("Subiendo"),
    DOWNLOADING("Descargando"),
    CONFLICT("Conflicto"),
    ERROR("Error");

    companion object {
        fun fromName(raw: String?): DriveFileSyncStatus =
            entries.firstOrNull { it.name == raw } ?: CLOUD_ONLY
    }
}

enum class DriveFileKind(val label: String) {
    FOLDER("Carpeta"),
    PDF("PDF"),
    DOCX("DOCX"),
    EPUB("EPUB"),
    PPTX("PPTX"),
    TXT("TXT"),
    FB2("FB2"),
    IMAGE("Imagen"),
    LUMA_NOTEBOOK("Cuaderno"),
    OTHER("Archivo");

    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val LUMA_MIME = "application/vnd.luma.notebook+json"

        fun resolve(name: String, mimeType: String): DriveFileKind {
            val lowerMime = mimeType.lowercase()
            val lowerName = name.lowercase()
            return when {
                lowerMime == FOLDER_MIME -> FOLDER
                lowerName.endsWith(".luma") || lowerName.endsWith(".luma.json") || lowerMime == LUMA_MIME -> LUMA_NOTEBOOK
                lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> PDF
                lowerMime.contains("wordprocessingml") || lowerMime == "application/vnd.google-apps.document" || lowerName.endsWith(".docx") -> DOCX
                lowerMime == "application/epub+zip" || lowerName.endsWith(".epub") -> EPUB
                lowerMime.contains("presentationml") || lowerMime == "application/vnd.google-apps.presentation" || lowerName.endsWith(".pptx") -> PPTX
                lowerMime == "text/plain" || lowerName.endsWith(".txt") -> TXT
                lowerMime.contains("fictionbook") || lowerName.endsWith(".fb2") -> FB2
                lowerMime.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") -> IMAGE
                else -> OTHER
            }
        }
    }
}

enum class DriveSortOrder(val label: String) {
    DATE_DESC("Fecha"),
    NAME_ASC("Nombre"),
    SIZE_DESC("Tamaño")
}

enum class DriveFilterKind(val label: String) {
    ALL("Todos"),
    PDF("PDF"),
    DOCX("DOCX"),
    IMAGES("Imágenes"),
    NOTEBOOKS("Cuadernos"),
    BOOKS("Otros libros")
}

@Entity(
    tableName = "drive_sources",
    indices = [
        Index(value = ["folderId"], unique = true),
        Index(value = ["isDefaultSaveTarget"])
    ]
)
data class DriveSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val folderId: String,
    val folderLink: String = "",
    val accentColorHex: Long = 0xFF2563EBL,
    val enabled: Boolean = true,
    val isDefaultSaveTarget: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "drive_files",
    indices = [
        Index(value = ["sourceId", "parentFolderId"]),
        Index(value = ["linkedDocumentId"]),
        Index(value = ["linkedNotebookId"]),
        Index(value = ["syncStatus"])
    ]
)
data class DriveFileEntity(
    @PrimaryKey val driveFileId: String,
    val sourceId: Long,
    val parentFolderId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val modifiedTimeMs: Long = 0L,
    val md5Checksum: String = "",
    val thumbnailLink: String = "",
    val localFilePath: String = "",
    val localThumbnailPath: String = "",
    val linkedDocumentId: Long = 0L,
    val linkedNotebookId: Long = 0L,
    val syncStatus: String = DriveFileSyncStatus.CLOUD_ONLY.name,
    val localModifiedAt: Long = 0L,
    val lastError: String = ""
) {
    val kind: DriveFileKind get() = DriveFileKind.resolve(name, mimeType)
    val status: DriveFileSyncStatus get() = DriveFileSyncStatus.fromName(syncStatus)
    val isFolder: Boolean get() = kind == DriveFileKind.FOLDER
    val isDownloadedLocally: Boolean get() = localFilePath.isNotBlank() && java.io.File(localFilePath).exists()
}

enum class DriveSyncAction {
    UPLOAD_DOCUMENT,
    UPLOAD_NOTEBOOK,
    RENAME_REMOTE,
    MOVE_REMOTE,
    DELETE_REMOTE
}

@Entity(
    tableName = "drive_sync_queue",
    indices = [Index(value = ["createdAt"])]
)
data class DriveSyncTaskEntity(
    @PrimaryKey(autoGenerate = true) val taskId: Long = 0L,
    val action: String,
    val driveFileId: String = "",
    val sourceId: Long = 0L,
    val targetFolderId: String = "",
    val previousFolderId: String = "",
    val localFilePath: String = "",
    val localDocumentId: Long = 0L,
    val localNotebookId: Long = 0L,
    val displayName: String = "",
    val mimeType: String = "",
    val retryCount: Int = 0,
    val lastError: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DriveDao {
    @Query("SELECT * FROM drive_sources ORDER BY isDefaultSaveTarget DESC, createdAt ASC")
    fun observeSources(): Flow<List<DriveSourceEntity>>

    @Query("SELECT * FROM drive_sources ORDER BY isDefaultSaveTarget DESC, createdAt ASC")
    suspend fun getSourcesOnce(): List<DriveSourceEntity>

    @Query("SELECT * FROM drive_sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: Long): DriveSourceEntity?

    @Query("SELECT * FROM drive_sources WHERE folderId = :folderId LIMIT 1")
    suspend fun getSourceByFolderId(folderId: String): DriveSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: DriveSourceEntity): Long

    @Query("UPDATE drive_sources SET isDefaultSaveTarget = 0")
    suspend fun clearDefaultSourceFlags()

    @Query("UPDATE drive_sources SET isDefaultSaveTarget = 1 WHERE id = :sourceId")
    suspend fun setDefaultSourceFlag(sourceId: Long)

    @Query("UPDATE drive_sources SET enabled = :enabled WHERE id = :sourceId")
    suspend fun setSourceEnabled(sourceId: Long, enabled: Boolean)

    @Query("UPDATE drive_sources SET name = :name, accentColorHex = :colorHex WHERE id = :sourceId")
    suspend fun updateSourceAppearance(sourceId: Long, name: String, colorHex: Long)

    @Query("UPDATE drive_sources SET lastSyncedAt = :timestamp WHERE id = :sourceId")
    suspend fun updateSourceLastSynced(sourceId: Long, timestamp: Long)

    @Query("DELETE FROM drive_sources WHERE id = :sourceId")
    suspend fun deleteSourceById(sourceId: Long)

    @Query("DELETE FROM drive_files WHERE sourceId = :sourceId")
    suspend fun deleteFilesForSource(sourceId: Long)

    @Query("SELECT * FROM drive_files ORDER BY CASE WHEN mimeType = 'application/vnd.google-apps.folder' THEN 0 ELSE 1 END, modifiedTimeMs DESC")
    fun observeAllFiles(): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE sourceId = :sourceId AND parentFolderId = :parentFolderId ORDER BY CASE WHEN mimeType = 'application/vnd.google-apps.folder' THEN 0 ELSE 1 END, modifiedTimeMs DESC")
    fun observeFolderFiles(sourceId: Long, parentFolderId: String): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getFileById(driveFileId: String): DriveFileEntity?

    @Query("SELECT * FROM drive_files WHERE linkedDocumentId = :documentId LIMIT 1")
    suspend fun getFileByLinkedDocumentId(documentId: Long): DriveFileEntity?

    @Query("SELECT * FROM drive_files WHERE linkedNotebookId = :notebookId LIMIT 1")
    suspend fun getFileByLinkedNotebookId(notebookId: Long): DriveFileEntity?

    @Query("SELECT * FROM drive_files WHERE sourceId = :sourceId AND parentFolderId = :parentFolderId")
    suspend fun getFolderFilesOnce(sourceId: Long, parentFolderId: String): List<DriveFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFiles(files: List<DriveFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: DriveFileEntity)

    @Query("DELETE FROM drive_files WHERE driveFileId = :driveFileId")
    suspend fun deleteFileById(driveFileId: String)

    @Query("DELETE FROM drive_files WHERE sourceId = :sourceId AND parentFolderId = :parentFolderId AND driveFileId NOT IN (:keepIds)")
    suspend fun pruneMissingFolderFiles(sourceId: Long, parentFolderId: String, keepIds: List<String>)

    @Query("UPDATE drive_files SET localFilePath = '', syncStatus = 'CLOUD_ONLY', localModifiedAt = 0 WHERE driveFileId = :driveFileId")
    suspend fun clearLocalCopy(driveFileId: String)

    @Query("UPDATE drive_files SET localThumbnailPath = :thumbPath WHERE driveFileId = :driveFileId")
    suspend fun updateLocalThumbnail(driveFileId: String, thumbPath: String)

    @Query("UPDATE drive_files SET syncStatus = :status, lastError = :error WHERE driveFileId = :driveFileId")
    suspend fun updateFileSyncStatus(driveFileId: String, status: String, error: String = "")

    @Query("SELECT * FROM drive_sync_queue ORDER BY createdAt ASC")
    fun observeSyncQueue(): Flow<List<DriveSyncTaskEntity>>

    @Query("SELECT * FROM drive_sync_queue ORDER BY createdAt ASC")
    suspend fun getSyncQueueOnce(): List<DriveSyncTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncTask(task: DriveSyncTaskEntity): Long

    @Query("DELETE FROM drive_sync_queue WHERE taskId = :taskId")
    suspend fun deleteSyncTask(taskId: Long)

    @Query("UPDATE drive_sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE taskId = :taskId")
    suspend fun markSyncTaskError(taskId: Long, error: String)
}

data class DriveAuthSnapshot(
    val clientId: String = "",
    val apiKey: String = "",
    val accountEmail: String = "",
    val isSignedIn: Boolean = false,
    val tokenExpiresAtMs: Long = 0L,
    val hasRefreshToken: Boolean = false,
    val useMinimalDriveFileScope: Boolean = false,
    val syncMode: DriveSyncMode = DriveSyncMode.WIFI_ONLY
)

data class DriveTransferProgress(
    val transferId: String,
    val fileName: String,
    val isUpload: Boolean,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val isPaused: Boolean = false
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val percent: Int get() = (fraction * 100f).toInt().coerceIn(0, 100)
}

data class DriveConflictItem(
    val driveFileId: String,
    val fileName: String,
    val sourceId: Long,
    val parentFolderId: String,
    val localModifiedMs: Long,
    val remoteModifiedMs: Long,
    val localFilePath: String,
    val linkedDocumentId: Long = 0L,
    val linkedNotebookId: Long = 0L
)

data class DriveBreadcrumb(
    val folderId: String,
    val title: String
)

class DriveApiException(
    val httpCode: Int,
    val userMessage: String,
    val isAuthError: Boolean = false,
    val isOffline: Boolean = false,
    cause: Throwable? = null
) : Exception(userMessage, cause)

/**
 * Pure parser and sanitizer for Google Drive folder links and IDs.
 * Supports:
 * - https://drive.google.com/drive/folders/FOLDER_ID
 * - https://drive.google.com/drive/u/0/folders/FOLDER_ID?usp=sharing
 * - https://drive.google.com/drive/mobile/folders/FOLDER_ID?resourcekey=...
 * - https://drive.google.com/open?id=FOLDER_ID
 * - Raw folder IDs (alphanumeric, '-', '_', 10..128 chars)
 */
object DriveFolderLinkParser {
    private val VALID_ID_REGEX = Regex("^[A-Za-z0-9_-]{10,128}$")
    private val FOLDERS_PATH_REGEX = Regex("/folders/([A-Za-z0-9_-]{10,128})")

    fun extractFolderId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (VALID_ID_REGEX.matches(trimmed)) return trimmed

        val parsedUri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = parsedUri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        val host = parsedUri.host?.lowercase().orEmpty()
        if (host != "drive.google.com" && host != "docs.google.com") return null

        val path = parsedUri.path.orEmpty()
        FOLDERS_PATH_REGEX.find(path)?.groupValues?.getOrNull(1)?.let { id ->
            if (VALID_ID_REGEX.matches(id)) return id
        }

        val queryId = parsedUri.getQueryParameter("id")?.trim().orEmpty()
        if (VALID_ID_REGEX.matches(queryId)) return queryId

        return null
    }

    fun isValidDriveId(id: String): Boolean = VALID_ID_REGEX.matches(id.trim())

    fun sanitizeLocalFileName(rawName: String, fallback: String = "archivo_drive"): String {
        val cleaned = rawName
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace("..", "_")
            .trim()
            .take(120)
        return if (cleaned.trim('_', '.', ' ').isBlank()) fallback else cleaned
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            else -> String.format(java.util.Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        return "${formatBytes(bytesPerSec)}/s"
    }
}

val DriveSourcePresetColors = listOf(
    0xFF2563EBL, // Azul Drive
    0xFF0D9488L, // Turquesa
    0xFF7C3AEDL, // Violeta
    0xFFD97706L, // Ámbar
    0xFFE11D48L, // Carmesí
    0xFF16A34AL  // Esmeralda
)
