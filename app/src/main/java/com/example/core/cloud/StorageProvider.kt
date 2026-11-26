package com.example.core.cloud

import android.content.Intent
import com.example.drive.DriveRepository
import com.example.drive.RemoteDriveFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class CloudProviderId(val displayName: String, val isExperimental: Boolean = false) {
    GOOGLE_DRIVE("Google Drive"),
    ONEDRIVE("OneDrive"),
    TERABOX("Terabox", isExperimental = true)
}

/**
 * Normalized cloud user profile (internal userId = OIDC `sub` claim from Google/Microsoft).
 */
data class CloudUserProfile(
    val userId: String,
    val provider: CloudProviderId,
    val email: String,
    val displayName: String,
    val photoUrl: String = ""
)

data class CloudFileMetadata(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val checksum: String = "",
    val modifiedTimeMs: Long = 0L,
    val parentId: String = "",
    val thumbnailUrl: String = "",
    val isFolder: Boolean = false
)

data class CloudFolderPage(
    val items: List<CloudFileMetadata>,
    val nextPageToken: String? = null
)

data class CloudChangeEvent(
    val fileId: String,
    val removed: Boolean,
    val metadata: CloudFileMetadata? = null
)

data class CloudChangesPage(
    val events: List<CloudChangeEvent>,
    val nextPageToken: String? = null,
    val newStartPageToken: String? = null
)

data class CloudUploadRequest(
    val localFile: File,
    val remoteName: String,
    val mimeType: String,
    val parentFolderId: String,
    val existingFileId: String? = null
)

data class CloudDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: String = "Android",
    val lastActiveAtMs: Long = System.currentTimeMillis(),
    val isCurrentDevice: Boolean = false
)

/**
 * Shared profile schema persisted in `appDataFolder/luma-profile.json` and merged field-by-field
 * by timestamp so up to 5 devices stay consistent without a proprietary backend.
 */
data class LumaCloudProfile(
    val schemaVersion: Int = 1,
    val userId: String = "",
    val displayName: String = "",
    val displayNameUpdatedAtMs: Long = 0L,
    val defaultProvider: String = CloudProviderId.GOOGLE_DRIVE.name,
    val defaultProviderUpdatedAtMs: Long = 0L,
    val autoSaveEnabled: Boolean = false,
    val autoSaveUpdatedAtMs: Long = 0L,
    val devices: List<CloudDeviceInfo> = emptyList()
) {
    fun toJson(): String = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("userId", userId)
        put("displayName", displayName)
        put("displayNameUpdatedAtMs", displayNameUpdatedAtMs)
        put("defaultProvider", defaultProvider)
        put("defaultProviderUpdatedAtMs", defaultProviderUpdatedAtMs)
        put("autoSaveEnabled", autoSaveEnabled)
        put("autoSaveUpdatedAtMs", autoSaveUpdatedAtMs)
        val arr = JSONArray()
        for (d in devices.take(CloudPublicConfig.MAX_SYNC_DEVICES)) {
            arr.put(JSONObject().apply {
                put("deviceId", d.deviceId)
                put("deviceName", sanitizePlainText(d.deviceName, 60))
                put("platform", sanitizePlainText(d.platform, 24))
                put("lastActiveAtMs", d.lastActiveAtMs)
            })
        }
        put("devices", arr)
    }.toString(2)

    companion object {
        fun merge(local: LumaCloudProfile, remote: LumaCloudProfile): LumaCloudProfile {
            val useRemoteName = remote.displayNameUpdatedAtMs > local.displayNameUpdatedAtMs &&
                remote.displayName.isNotBlank()
            val useRemoteProvider = remote.defaultProviderUpdatedAtMs > local.defaultProviderUpdatedAtMs
            val useRemoteAutoSave = remote.autoSaveUpdatedAtMs > local.autoSaveUpdatedAtMs

            val mergedDevices = LinkedHashMap<String, CloudDeviceInfo>()
            (local.devices + remote.devices).forEach { dev ->
                val prev = mergedDevices[dev.deviceId]
                if (prev == null || dev.lastActiveAtMs > prev.lastActiveAtMs) {
                    mergedDevices[dev.deviceId] = dev.copy(
                        isCurrentDevice = prev?.isCurrentDevice == true || dev.isCurrentDevice
                    )
                }
            }
            val topDevices = mergedDevices.values
                .sortedByDescending { it.lastActiveAtMs }
                .take(CloudPublicConfig.MAX_SYNC_DEVICES)

            return LumaCloudProfile(
                schemaVersion = maxOf(local.schemaVersion, remote.schemaVersion),
                userId = local.userId.ifBlank { remote.userId },
                displayName = if (useRemoteName) remote.displayName else local.displayName,
                displayNameUpdatedAtMs = maxOf(local.displayNameUpdatedAtMs, remote.displayNameUpdatedAtMs),
                defaultProvider = if (useRemoteProvider) remote.defaultProvider else local.defaultProvider,
                defaultProviderUpdatedAtMs = maxOf(local.defaultProviderUpdatedAtMs, remote.defaultProviderUpdatedAtMs),
                autoSaveEnabled = if (useRemoteAutoSave) remote.autoSaveEnabled else local.autoSaveEnabled,
                autoSaveUpdatedAtMs = maxOf(local.autoSaveUpdatedAtMs, remote.autoSaveUpdatedAtMs),
                devices = topDevices
            )
        }

        fun fromJson(raw: String): LumaCloudProfile? = runCatching {
            val obj = JSONObject(raw)
            val devs = mutableListOf<CloudDeviceInfo>()
            val arr = obj.optJSONArray("devices")
            if (arr != null) {
                for (i in 0 until arr.length().coerceAtMost(20)) {
                    val d = arr.optJSONObject(i) ?: continue
                    val id = sanitizePlainText(d.optString("deviceId"), 80)
                    if (id.isNotBlank()) {
                        devs += CloudDeviceInfo(
                            deviceId = id,
                            deviceName = sanitizePlainText(d.optString("deviceName", "Dispositivo"), 60),
                            platform = sanitizePlainText(d.optString("platform", "Android"), 24),
                            lastActiveAtMs = d.optLong("lastActiveAtMs", 0L)
                        )
                    }
                }
            }
            LumaCloudProfile(
                schemaVersion = obj.optInt("schemaVersion", 1),
                userId = sanitizePlainText(obj.optString("userId"), 128),
                displayName = sanitizePlainText(obj.optString("displayName"), 80),
                displayNameUpdatedAtMs = obj.optLong("displayNameUpdatedAtMs", 0L),
                defaultProvider = sanitizePlainText(obj.optString("defaultProvider", CloudProviderId.GOOGLE_DRIVE.name), 32),
                defaultProviderUpdatedAtMs = obj.optLong("defaultProviderUpdatedAtMs", 0L),
                autoSaveEnabled = obj.optBoolean("autoSaveEnabled", false),
                autoSaveUpdatedAtMs = obj.optLong("autoSaveUpdatedAtMs", 0L),
                devices = devs
            )
        }.getOrNull()
    }
}

/**
 * Strips HTML tags, control characters, and caps length so external JSON manifests/profiles
 * are always treated as untrusted data.
 */
fun sanitizePlainText(raw: String, maxLen: Int = 160): String {
    return raw
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "")
        .trim()
        .take(maxLen)
}

/**
 * Provider-agnostic cloud storage contract. The rest of Luma talks ONLY to this interface,
 * allowing Google Drive, OneDrive, and Terabox (local synced folder) to be swapped cleanly.
 */
interface StorageProvider {
    val providerId: CloudProviderId
    val isEnabledByFeatureFlag: Boolean

    fun buildSignInIntent(): Result<Intent>
    suspend fun signOut()
    suspend fun getUser(): CloudUserProfile?

    suspend fun listFolder(folderId: String, pageToken: String? = null): Result<CloudFolderPage>
    suspend fun createFolder(
        parentFolderId: String?,
        name: String,
        appProperties: Map<String, String> = emptyMap()
    ): Result<CloudFileMetadata>

    suspend fun upload(
        request: CloudUploadRequest,
        onProgress: (uploadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit = { _, _, _ -> }
    ): Result<CloudFileMetadata>

    suspend fun download(
        fileId: String,
        mimeType: String,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit = { _, _, _ -> }
    ): Result<File>

    suspend fun rename(fileId: String, newName: String): Result<Unit>
    suspend fun move(fileId: String, targetFolderId: String, previousFolderId: String): Result<Unit>
    suspend fun delete(fileId: String): Result<Unit>
    suspend fun getChanges(pageToken: String?): Result<CloudChangesPage>
    suspend fun getMetadata(fileId: String): Result<CloudFileMetadata>
}

/**
 * Active Beta provider backed by official Google Identity OAuth 2.0 + Drive API v3.
 */
class GoogleDriveStorageProvider(
    private val driveRepo: DriveRepository
) : StorageProvider {
    override val providerId: CloudProviderId = CloudProviderId.GOOGLE_DRIVE
    override val isEnabledByFeatureFlag: Boolean = CloudFeatureFlags.googleDrive

    override fun buildSignInIntent(): Result<Intent> = driveRepo.buildSignInIntent()

    override suspend fun signOut() {
        driveRepo.signOut()
    }

    override suspend fun getUser(): CloudUserProfile? {
        val snap = driveRepo.authSnapshot.value
        if (!snap.isSignedIn) return null
        val email = snap.accountEmail.ifBlank { "usuario@gmail.com" }
        val derivedName = email.substringBefore('@').replace('.', ' ').replaceFirstChar { it.uppercase() }
        return CloudUserProfile(
            userId = "google:${email.lowercase()}",
            provider = CloudProviderId.GOOGLE_DRIVE,
            email = email,
            displayName = derivedName
        )
    }

    override suspend fun listFolder(folderId: String, pageToken: String?): Result<CloudFolderPage> = runCatching {
        val page = driveRepo.apiClient.listFolderPage(folderId, pageToken)
        CloudFolderPage(
            items = page.files.map { it.toCloudMetadata(folderId) },
            nextPageToken = page.nextPageToken
        )
    }

    override suspend fun createFolder(
        parentFolderId: String?,
        name: String,
        appProperties: Map<String, String>
    ): Result<CloudFileMetadata> = Result.failure(
        UnsupportedOperationException("La creación automática de carpetas Luma/ se activa en Etapa 2.")
    )

    override suspend fun upload(
        request: CloudUploadRequest,
        onProgress: (Long, Long, Long) -> Unit
    ): Result<CloudFileMetadata> = runCatching {
        val remote = driveRepo.apiClient.uploadFileResumable(
            localFile = request.localFile,
            remoteName = request.remoteName,
            mimeType = request.mimeType,
            parentFolderId = request.parentFolderId,
            existingDriveFileId = request.existingFileId,
            onProgress = onProgress
        )
        remote.toCloudMetadata(request.parentFolderId)
    }

    override suspend fun download(
        fileId: String,
        mimeType: String,
        destinationFile: File,
        onProgress: (Long, Long, Long) -> Unit
    ): Result<File> = runCatching {
        driveRepo.apiClient.downloadFileResumable(
            driveFileId = fileId,
            mimeType = mimeType,
            destFile = destinationFile,
            expectedSizeBytes = 0L,
            isPaused = { false },
            isCancelled = { false },
            onProgress = onProgress
        )
        destinationFile
    }

    override suspend fun rename(fileId: String, newName: String): Result<Unit> = runCatching {
        driveRepo.apiClient.renameFile(fileId, newName)
    }

    override suspend fun move(fileId: String, targetFolderId: String, previousFolderId: String): Result<Unit> = runCatching {
        driveRepo.apiClient.moveFile(fileId, targetFolderId, previousFolderId)
    }

    override suspend fun delete(fileId: String): Result<Unit> = runCatching {
        driveRepo.apiClient.deleteFile(fileId)
    }

    override suspend fun getChanges(pageToken: String?): Result<CloudChangesPage> =
        Result.success(CloudChangesPage(events = emptyList(), nextPageToken = null, newStartPageToken = pageToken))

    override suspend fun getMetadata(fileId: String): Result<CloudFileMetadata> = runCatching {
        val meta = driveRepo.apiClient.getFileMetadata(fileId)
        meta.toCloudMetadata("")
    }

    private fun RemoteDriveFile.toCloudMetadata(parentId: String): CloudFileMetadata = CloudFileMetadata(
        id = id,
        name = sanitizePlainText(name, 160),
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        checksum = md5Checksum,
        modifiedTimeMs = modifiedTimeMs,
        parentId = parentId,
        thumbnailUrl = thumbnailLink,
        isFolder = mimeType == "application/vnd.google-apps.folder"
    )
}

/**
 * Functional skeleton for Microsoft OneDrive (OAuth 2.0 PKCE + Graph API + Delta query).
 * Gated behind [CloudFeatureFlags.microsoft] (disabled in Beta).
 */
class OneDriveStorageProvider : StorageProvider {
    override val providerId: CloudProviderId = CloudProviderId.ONEDRIVE
    override val isEnabledByFeatureFlag: Boolean = CloudFeatureFlags.microsoft

    private fun disabledResult(): Result<Nothing> = Result.failure(
        IllegalStateException("Microsoft OneDrive está desactivado en esta fase Beta.")
    )

    override fun buildSignInIntent(): Result<Intent> = disabledResult()
    override suspend fun signOut() = Unit
    override suspend fun getUser(): CloudUserProfile? = null
    override suspend fun listFolder(folderId: String, pageToken: String?): Result<CloudFolderPage> = disabledResult()
    override suspend fun createFolder(parentFolderId: String?, name: String, appProperties: Map<String, String>): Result<CloudFileMetadata> = disabledResult()
    override suspend fun upload(request: CloudUploadRequest, onProgress: (Long, Long, Long) -> Unit): Result<CloudFileMetadata> = disabledResult()
    override suspend fun download(fileId: String, mimeType: String, destinationFile: File, onProgress: (Long, Long, Long) -> Unit): Result<File> = disabledResult()
    override suspend fun rename(fileId: String, newName: String): Result<Unit> = disabledResult()
    override suspend fun move(fileId: String, targetFolderId: String, previousFolderId: String): Result<Unit> = disabledResult()
    override suspend fun delete(fileId: String): Result<Unit> = disabledResult()
    override suspend fun getChanges(pageToken: String?): Result<CloudChangesPage> = disabledResult()
    override suspend fun getMetadata(fileId: String): Result<CloudFileMetadata> = disabledResult()
}

/**
 * Functional skeleton for Terabox in "local synced folder" mode (zero unofficial APIs,
 * zero credential scraping — relies exclusively on the user's official Terabox app syncing a folder).
 * Gated behind [CloudFeatureFlags.terabox] (disabled in Beta).
 */
class TeraboxStorageProvider : StorageProvider {
    override val providerId: CloudProviderId = CloudProviderId.TERABOX
    override val isEnabledByFeatureFlag: Boolean = CloudFeatureFlags.terabox

    private fun disabledResult(): Result<Nothing> = Result.failure(
        IllegalStateException("Terabox (modo carpeta local sincronizada) está desactivado en esta fase Beta.")
    )

    override fun buildSignInIntent(): Result<Intent> = disabledResult()
    override suspend fun signOut() = Unit
    override suspend fun getUser(): CloudUserProfile? = null
    override suspend fun listFolder(folderId: String, pageToken: String?): Result<CloudFolderPage> = disabledResult()
    override suspend fun createFolder(parentFolderId: String?, name: String, appProperties: Map<String, String>): Result<CloudFileMetadata> = disabledResult()
    override suspend fun upload(request: CloudUploadRequest, onProgress: (Long, Long, Long) -> Unit): Result<CloudFileMetadata> = disabledResult()
    override suspend fun download(fileId: String, mimeType: String, destinationFile: File, onProgress: (Long, Long, Long) -> Unit): Result<File> = disabledResult()
    override suspend fun rename(fileId: String, newName: String): Result<Unit> = disabledResult()
    override suspend fun move(fileId: String, targetFolderId: String, previousFolderId: String): Result<Unit> = disabledResult()
    override suspend fun delete(fileId: String): Result<Unit> = disabledResult()
    override suspend fun getChanges(pageToken: String?): Result<CloudChangesPage> = disabledResult()
    override suspend fun getMetadata(fileId: String): Result<CloudFileMetadata> = disabledResult()
}
