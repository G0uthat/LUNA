package com.example.drive

import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class RemoteDriveFolderInfo(
    val folderId: String,
    val name: String,
    val canEdit: Boolean
)

data class RemoteDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTimeMs: Long,
    val md5Checksum: String,
    val thumbnailLink: String,
    val parentId: String
)

data class RemoteDrivePage(
    val files: List<RemoteDriveFile>,
    val nextPageToken: String?
)

/**
 * Direct client for Google Drive REST API v3 (zero intermediate server).
 * Supports OAuth 2.0 Bearer tokens (with transparent auto-refresh on 401)
 * and optional user API Key fallback for reading publicly shared folders.
 */
class GoogleDriveApiClient(
    private val vault: DriveSecureVault,
    private val authManager: GoogleDriveAuthManager,
    private val httpClient: OkHttpClient = defaultDriveClient()
) {
    private suspend fun resolveAuthHeaderAndKey(requireWrite: Boolean): Pair<String?, String?> {
        val token = authManager.getValidAccessToken()
        if (!token.isNullOrBlank()) {
            return "Bearer $token" to null
        }
        val apiKey = vault.getApiKey()
        if (!requireWrite && apiKey.isNotBlank()) {
            return null to apiKey
        }
        throw DriveApiException(
            httpCode = 401,
            userMessage = if (vault.getClientId().isBlank()) {
                "El inicio de sesión no está disponible en este momento"
            } else {
                "Tu sesión de Google Drive expiró o no has iniciado sesión. Inicia sesión desde Mi cuenta."
            },
            isAuthError = true
        )
    }

    private suspend fun <T> executeWithRetry(
        requireWrite: Boolean = false,
        block: (authHeader: String?, apiKey: String?) -> T
    ): T {
        val (header, key) = resolveAuthHeaderAndKey(requireWrite)
        return try {
            block(header, key)
        } catch (e: DriveApiException) {
            if (e.httpCode == 401 && header != null) {
                val refreshed = authManager.getValidAccessToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank()) {
                    return block("Bearer $refreshed", null)
                }
            }
            throw e
        }
    }

    suspend fun validateFolder(folderId: String): RemoteDriveFolderInfo = withContext(Dispatchers.IO) {
        if (!DriveFolderLinkParser.isValidDriveId(folderId)) {
            throw DriveApiException(400, "El enlace o ID de carpeta de Google Drive no es válido.")
        }
        executeWithRetry(requireWrite = false) { authHeader, apiKey ->
            val urlBuilder = "$DRIVE_API_BASE/files/$folderId".toHttpUrl().newBuilder()
                .addQueryParameter("fields", "id,name,mimeType,trashed,capabilities(canAddChildren)")
                .addQueryParameter("supportsAllDrives", "true")
            if (!apiKey.isNullOrBlank()) urlBuilder.addQueryParameter("key", apiKey)

            val req = Request.Builder()
                .url(urlBuilder.build())
                .apply { if (!authHeader.isNullOrBlank()) header("Authorization", authHeader) }
                .get()
                .build()

            executeJsonRequest(req, folderContext = true).let { json ->
                if (json.optBoolean("trashed", false)) {
                    throw DriveApiException(404, "Esta carpeta está en la papelera de Google Drive.")
                }
                val mime = json.optString("mimeType")
                if (mime != DriveFileKind.FOLDER_MIME) {
                    throw DriveApiException(400, "El enlace corresponde a un archivo, no a una carpeta de Google Drive.")
                }
                val caps = json.optJSONObject("capabilities")
                RemoteDriveFolderInfo(
                    folderId = json.optString("id", folderId),
                    name = json.optString("name", "Carpeta de Drive").ifBlank { "Carpeta de Drive" },
                    canEdit = caps?.optBoolean("canAddChildren", authHeader != null) ?: (authHeader != null)
                )
            }
        }
    }

    suspend fun listFolderPage(
        folderId: String,
        pageToken: String? = null,
        pageSize: Int = 50
    ): RemoteDrivePage = withContext(Dispatchers.IO) {
        if (!DriveFolderLinkParser.isValidDriveId(folderId)) {
            throw DriveApiException(400, "ID de carpeta inválido.")
        }
        executeWithRetry(requireWrite = false) { authHeader, apiKey ->
            val urlBuilder = "$DRIVE_API_BASE/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$folderId' in parents and trashed = false")
                .addQueryParameter(
                    "fields",
                    "nextPageToken,files(id,name,mimeType,size,modifiedTime,md5Checksum,thumbnailLink,parents)"
                )
                .addQueryParameter("pageSize", pageSize.coerceIn(10, 100).toString())
                .addQueryParameter("orderBy", "folder,modifiedTime desc")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
            if (!pageToken.isNullOrBlank()) urlBuilder.addQueryParameter("pageToken", pageToken)
            if (!apiKey.isNullOrBlank()) urlBuilder.addQueryParameter("key", apiKey)

            val req = Request.Builder()
                .url(urlBuilder.build())
                .apply { if (!authHeader.isNullOrBlank()) header("Authorization", authHeader) }
                .get()
                .build()

            val json = executeJsonRequest(req, folderContext = true)
            val arr = json.optJSONArray("files")
            val list = ArrayList<RemoteDriveFile>(arr?.length() ?: 0)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(parseRemoteFile(obj, fallbackParent = folderId))
                }
            }
            RemoteDrivePage(
                files = list,
                nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun getFileMetadata(driveFileId: String): RemoteDriveFile = withContext(Dispatchers.IO) {
        if (!DriveFolderLinkParser.isValidDriveId(driveFileId)) {
            throw DriveApiException(400, "ID de archivo inválido.")
        }
        executeWithRetry(requireWrite = false) { authHeader, apiKey ->
            val urlBuilder = "$DRIVE_API_BASE/files/$driveFileId".toHttpUrl().newBuilder()
                .addQueryParameter("fields", "id,name,mimeType,size,modifiedTime,md5Checksum,thumbnailLink,parents,trashed")
                .addQueryParameter("supportsAllDrives", "true")
            if (!apiKey.isNullOrBlank()) urlBuilder.addQueryParameter("key", apiKey)
            val req = Request.Builder()
                .url(urlBuilder.build())
                .apply { if (!authHeader.isNullOrBlank()) header("Authorization", authHeader) }
                .get()
                .build()
            val json = executeJsonRequest(req)
            if (json.optBoolean("trashed", false)) {
                throw DriveApiException(404, "El archivo fue eliminado en Google Drive.")
            }
            parseRemoteFile(json, fallbackParent = "")
        }
    }

    /**
     * Resumable streaming download using HTTP `Range: bytes=N-` with live speed, pause, cancel,
     * automatic partial-file resume, and free-space verification via `StatFs`.
     */
    suspend fun downloadFileResumable(
        driveFileId: String,
        mimeType: String,
        destFile: File,
        expectedSizeBytes: Long,
        isPaused: () -> Boolean,
        isCancelled: () -> Boolean,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!DriveFolderLinkParser.isValidDriveId(driveFileId)) {
            throw DriveApiException(400, "ID de archivo de Drive inválido.")
        }
        val parentDir = destFile.parentFile ?: throw DriveApiException(500, "Ruta de guardado local inválida.")
        parentDir.mkdirs()

        // Verify device storage space before downloading
        val stat = runCatching { StatFs(parentDir.absolutePath) }.getOrNull()
        val availableBytes = stat?.availableBytes ?: Long.MAX_VALUE
        val neededBytes = if (expectedSizeBytes > 0L) expectedSizeBytes + (12L * 1024L * 1024L) else (16L * 1024L * 1024L)
        if (availableBytes < neededBytes) {
            throw DriveApiException(
                httpCode = 507,
                userMessage = "Espacio insuficiente en el dispositivo (disponible: ${DriveFolderLinkParser.formatBytes(availableBytes)}). Usa 'Liberar espacio' en archivos ya descargados."
            )
        }

        val partFile = File(parentDir, "${destFile.name}.part")
        val exportMime = googleWorkspaceExportMime(mimeType)

        executeWithRetry(requireWrite = false) { authHeader, apiKey ->
            var downloaded = if (exportMime == null && partFile.exists()) partFile.length() else 0L
            if (expectedSizeBytes > 0L && downloaded > expectedSizeBytes) {
                partFile.delete()
                downloaded = 0L
            }

            val urlBuilder = if (exportMime != null) {
                "$DRIVE_API_BASE/files/$driveFileId/export".toHttpUrl().newBuilder()
                    .addQueryParameter("mimeType", exportMime)
            } else {
                "$DRIVE_API_BASE/files/$driveFileId".toHttpUrl().newBuilder()
                    .addQueryParameter("alt", "media")
                    .addQueryParameter("supportsAllDrives", "true")
            }
            if (!apiKey.isNullOrBlank()) urlBuilder.addQueryParameter("key", apiKey)

            val reqBuilder = Request.Builder()
                .url(urlBuilder.build())
                .apply {
                    if (!authHeader.isNullOrBlank()) header("Authorization", authHeader)
                    if (exportMime == null && downloaded > 0L) {
                        header("Range", "bytes=$downloaded-")
                    }
                }
                .get()

            try {
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.code == 416) {
                        // Already fully downloaded
                        if (partFile.exists()) {
                            partFile.renameTo(destFile)
                            return@executeWithRetry destFile
                        }
                    }
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string().orEmpty()
                        throw mapHttpError(response.code, errBody)
                    }
                    val body = response.body ?: throw DriveApiException(500, "Google Drive devolvió una respuesta vacía.")
                    val appendMode = response.code == 206 && downloaded > 0L
                    if (!appendMode) {
                        downloaded = 0L
                    }
                    val contentLength = body.contentLength()
                    val total = when {
                        contentLength > 0L -> downloaded + contentLength
                        expectedSizeBytes > 0L -> expectedSizeBytes
                        else -> 0L
                    }

                    val buffer = ByteArray(32 * 1024)
                    var speedWindowBytes = 0L
                    var speedWindowStartMs = System.currentTimeMillis()
                    var currentSpeed = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, appendMode).use { out ->
                            while (true) {
                                if (isCancelled()) {
                                    throw DownloadCancelledException()
                                }
                                while (isPaused()) {
                                    if (isCancelled()) throw DownloadCancelledException()
                                    onProgress(downloaded, total, 0L)
                                    Thread.sleep(180L)
                                }
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                downloaded += read
                                speedWindowBytes += read
                                val now = System.currentTimeMillis()
                                val elapsed = now - speedWindowStartMs
                                if (elapsed >= 350L) {
                                    currentSpeed = ((speedWindowBytes * 1000L) / elapsed.coerceAtLeast(1L))
                                    speedWindowBytes = 0L
                                    speedWindowStartMs = now
                                    onProgress(downloaded, total, currentSpeed)
                                }
                            }
                            out.flush()
                        }
                    }
                    onProgress(downloaded, total.coerceAtLeast(downloaded), currentSpeed)
                    if (destFile.exists()) destFile.delete()
                    if (!partFile.renameTo(destFile)) {
                        partFile.copyTo(destFile, overwrite = true)
                        partFile.delete()
                    }
                    destFile
                }
            } catch (e: DownloadCancelledException) {
                partFile.delete()
                throw e
            } catch (e: IOException) {
                // Keep .part file intact so the next attempt resumes from exact byte offset!
                throw DriveApiException(
                    httpCode = 0,
                    userMessage = "Se interrumpió la conexión. Toca de nuevo para reanudar desde ${DriveFolderLinkParser.formatBytes(downloaded)}.",
                    isOffline = true,
                    cause = e
                )
            }
        }
    }

    /**
     * Genuine Google Drive API v3 Resumable Upload (`uploadType=resumable`) with chunked PUTs
     * so large PDFs, DOCX, images, and `.luma` notebooks upload reliably with live progress.
     */
    suspend fun uploadFileResumable(
        localFile: File,
        remoteName: String,
        mimeType: String,
        parentFolderId: String,
        existingDriveFileId: String? = null,
        onProgress: (bytesUploaded: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit = { _, _, _ -> }
    ): RemoteDriveFile = withContext(Dispatchers.IO) {
        if (!localFile.exists() || !localFile.canRead()) {
            throw DriveApiException(400, "No se encontró el archivo local para subir.")
        }
        if (!DriveFolderLinkParser.isValidDriveId(parentFolderId)) {
            throw DriveApiException(400, "Carpeta destino de Google Drive inválida.")
        }
        val totalBytes = localFile.length()

        executeWithRetry(requireWrite = true) { authHeader, _ ->
            val metadataJson = JSONObject().apply {
                put("name", remoteName)
                put("mimeType", mimeType)
                if (existingDriveFileId.isNullOrBlank()) {
                    put("parents", org.json.JSONArray().put(parentFolderId))
                }
            }

            val isUpdate = !existingDriveFileId.isNullOrBlank() && DriveFolderLinkParser.isValidDriveId(existingDriveFileId)
            val initUrl = if (isUpdate) {
                "$DRIVE_UPLOAD_BASE/files/$existingDriveFileId?uploadType=resumable&supportsAllDrives=true&fields=id,name,mimeType,size,modifiedTime,md5Checksum,thumbnailLink,parents"
            } else {
                "$DRIVE_UPLOAD_BASE/files?uploadType=resumable&supportsAllDrives=true&fields=id,name,mimeType,size,modifiedTime,md5Checksum,thumbnailLink,parents"
            }

            val initBody = metadataJson.toString().toRequestBody(JSON_MEDIA_TYPE)
            val initReq = Request.Builder()
                .url(initUrl)
                .header("Authorization", authHeader!!)
                .header("X-Upload-Content-Type", mimeType)
                .header("X-Upload-Content-Length", totalBytes.toString())
                .let { if (isUpdate) it.patch(initBody) else it.post(initBody) }
                .build()

            val uploadSessionUri = try {
                httpClient.newCall(initReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw mapHttpError(resp.code, resp.body?.string().orEmpty())
                    }
                    resp.header("Location")
                        ?: throw DriveApiException(500, "Google Drive no devolvió URL de subida reanudable.")
                }
            } catch (e: IOException) {
                throw DriveApiException(0, "Sin conexión a internet. El archivo quedó en la cola de sincronización.", isOffline = true, cause = e)
            }

            // Stream file in 2 MB chunks (multiple of 256 KB required by Google resumable upload spec)
            val chunkSize = 2 * 1024 * 1024
            var offset = 0L
            val contentMediaType = runCatching { mimeType.toMediaType() }.getOrDefault(OCTET_STREAM_MEDIA_TYPE)
            var finalJson: JSONObject? = null

            RandomAccessFile(localFile, "r").use { raf ->
                val chunkBuf = ByteArray(chunkSize)
                while (offset < totalBytes || (totalBytes == 0L && finalJson == null)) {
                    val toRead = if (totalBytes == 0L) 0 else minOf(chunkSize.toLong(), totalBytes - offset).toInt()
                    if (toRead > 0) {
                        raf.seek(offset)
                        raf.readFully(chunkBuf, 0, toRead)
                    }
                    val startMs = System.currentTimeMillis()
                    val endByte = (offset + toRead - 1L).coerceAtLeast(0L)
                    val contentRange = if (totalBytes == 0L) "bytes */0" else "bytes $offset-$endByte/$totalBytes"
                    val putReq = Request.Builder()
                        .url(uploadSessionUri)
                        .header("Content-Range", contentRange)
                        .put(chunkBuf.toRequestBody(contentMediaType, 0, toRead))
                        .build()

                    try {
                        httpClient.newCall(putReq).execute().use { resp ->
                            val respBody = resp.body?.string().orEmpty()
                            when (resp.code) {
                                200, 201 -> {
                                    offset = totalBytes
                                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                                    onProgress(totalBytes, totalBytes, (toRead * 1000L) / elapsed)
                                    finalJson = JSONObject(respBody)
                                }
                                308 -> {
                                    val rangeHeader = resp.header("Range")
                                    val confirmedEnd = rangeHeader?.substringAfter("-")?.toLongOrNull()
                                    offset = if (confirmedEnd != null) confirmedEnd + 1L else offset + toRead
                                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                                    onProgress(offset, totalBytes, (toRead * 1000L) / elapsed)
                                }
                                else -> throw mapHttpError(resp.code, respBody)
                            }
                        }
                    } catch (e: IOException) {
                        throw DriveApiException(
                            0,
                            "Conexión interrumpida al subir. Se reintentará automáticamente al volver la red.",
                            isOffline = true,
                            cause = e
                        )
                    }
                }
            }

            val resultObj = finalJson ?: throw DriveApiException(500, "La subida finalizó sin metadatos de respuesta.")
            parseRemoteFile(resultObj, fallbackParent = parentFolderId)
        }
    }

    suspend fun renameFile(driveFileId: String, newName: String): RemoteDriveFile = withContext(Dispatchers.IO) {
        if (!DriveFolderLinkParser.isValidDriveId(driveFileId)) {
            throw DriveApiException(400, "ID de archivo inválido.")
        }
        executeWithRetry(requireWrite = true) { authHeader, _ ->
            val body = JSONObject().put("name", newName.trim()).toString().toRequestBody(JSON_MEDIA_TYPE)
            val url = "$DRIVE_API_BASE/files/$driveFileId?supportsAllDrives=true&fields=id,name,mimeType,size,modifiedTime,md5Checksum,thumbnailLink,parents"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader!!)
                .patch(body)
                .build()
            parseRemoteFile(executeJsonRequest(req), fallbackParent = "")
        }
    }

    suspend fun moveFile(
        driveFileId: String,
        newParentFolderId: String,
        oldParentFolderId: String
    ): RemoteDriveFile = withContext(Dispatchers.IO) {
        if (!DriveFolderLinkParser.isValidDriveId(driveFileId) || !DriveFolderLinkParser.isValidDriveId(newParentFolderId)) {
            throw DriveApiException(400, "ID de archivo o carpeta destino inválido.")
        }
        executeWithRetry(requireWrite = true) { authHeader, _ ->
            val urlBuilder = "$DRIVE_API_BASE/files/$driveFileId".toHttpUrl().newBuilder()
                .addQueryParameter("addParents", newParentFolderId)
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("fields", "id,name,mimeType,size,modifiedTime,md5Checksum,thumbnailLink,parents")
            if (DriveFolderLinkParser.isValidDriveId(oldParentFolderId) && oldParentFolderId != newParentFolderId) {
                urlBuilder.addQueryParameter("removeParents", oldParentFolderId)
            }
            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", authHeader!!)
                .patch("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            parseRemoteFile(executeJsonRequest(req), fallbackParent = newParentFolderId)
        }
    }

    suspend fun deleteFile(driveFileId: String) = withContext(Dispatchers.IO) {
        if (!DriveFolderLinkParser.isValidDriveId(driveFileId)) {
            throw DriveApiException(400, "ID de archivo inválido.")
        }
        executeWithRetry(requireWrite = true) { authHeader, _ ->
            val url = "$DRIVE_API_BASE/files/$driveFileId?supportsAllDrives=true"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", authHeader!!)
                .delete()
                .build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 404) {
                        throw mapHttpError(resp.code, resp.body?.string().orEmpty())
                    }
                }
            } catch (e: IOException) {
                throw DriveApiException(0, "Sin conexión para eliminar de Google Drive.", isOffline = true, cause = e)
            }
        }
    }

    /**
     * Lazily fetches a Drive thumbnail image while restricting hostnames strictly to Google's CDN.
     */
    suspend fun downloadThumbnail(thumbnailLink: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        val parsed = runCatching { thumbnailLink.toHttpUrl() }.getOrNull() ?: return@withContext false
        val host = parsed.host.lowercase()
        val allowedHost = host.endsWith(".googleusercontent.com") ||
            host.endsWith(".google.com") ||
            host == "googleusercontent.com"
        if (parsed.scheme != "https" || !allowedHost) return@withContext false

        val token = authManager.getValidAccessToken()
        val req = Request.Builder()
            .url(parsed)
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .get()
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val bytes = resp.body?.bytes() ?: return@use false
                if (bytes.size < 64) return@use false
                destFile.parentFile?.mkdirs()
                destFile.writeBytes(bytes)
                true
            }
        }.getOrDefault(false)
    }

    private fun executeJsonRequest(request: Request, folderContext: Boolean = false): JSONObject {
        try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, raw, folderContext)
                }
                return if (raw.isBlank()) JSONObject() else JSONObject(raw)
            }
        } catch (e: DriveApiException) {
            throw e
        } catch (e: IOException) {
            throw DriveApiException(
                httpCode = 0,
                userMessage = "Sin conexión a internet. Verifica tu red e intenta de nuevo.",
                isOffline = true,
                cause = e
            )
        }
    }

    private fun mapHttpError(code: Int, rawBody: String, folderContext: Boolean = false): DriveApiException {
        val lower = rawBody.lowercase()
        return when (code) {
            401 -> DriveApiException(
                code,
                "Tu sesión de Google Drive expiró. Vuelve a iniciar sesión desde Mi cuenta.",
                isAuthError = true
            )
            403 -> when {
                lower.contains("storagequotaexceeded") || lower.contains("quota") ->
                    DriveApiException(code, "Tu cuenta de Google Drive alcanzó el límite de almacenamiento o cuota temporal.")
                lower.contains("accessnotconfigured") ->
                    DriveApiException(code, "Activa 'Google Drive API' en tu proyecto de Google Cloud Console.")
                else ->
                    DriveApiException(
                        code,
                        if (folderContext) {
                            "No tienes permisos para acceder a esta carpeta de Drive. Verifica que esté compartida con tu cuenta."
                        } else {
                            "No tienes permiso para leer o modificar este archivo en Google Drive."
                        }
                    )
            }
            404 -> DriveApiException(
                code,
                if (folderContext) {
                    "La carpeta no existe, fue eliminada o es privada sin sesión iniciada."
                } else {
                    "El archivo ya no existe en Google Drive."
                }
            )
            429 -> DriveApiException(code, "Demasiadas solicitudes a Google Drive. Espera unos segundos y reintenta.")
            else -> DriveApiException(code, "Google Drive respondió con error ($code). Intenta nuevamente.")
        }
    }

    private fun parseRemoteFile(json: JSONObject, fallbackParent: String): RemoteDriveFile {
        val parents = json.optJSONArray("parents")
        val firstParent = if (parents != null && parents.length() > 0) parents.optString(0, fallbackParent) else fallbackParent
        return RemoteDriveFile(
            id = json.optString("id"),
            name = json.optString("name", "Sin nombre"),
            mimeType = json.optString("mimeType", "application/octet-stream"),
            sizeBytes = json.optString("size").toLongOrNull() ?: json.optLong("size", 0L),
            modifiedTimeMs = parseRfc3339ToEpochMs(json.optString("modifiedTime")),
            md5Checksum = json.optString("md5Checksum", ""),
            thumbnailLink = json.optString("thumbnailLink", ""),
            parentId = firstParent
        )
    }

    private fun googleWorkspaceExportMime(mimeType: String): String? = when (mimeType) {
        "application/vnd.google-apps.document" ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "application/vnd.google-apps.presentation" ->
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> null
    }

    private fun parseRfc3339ToEpochMs(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (p in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    class DownloadCancelledException : Exception("Descarga cancelada por el usuario")

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()

        private fun defaultDriveClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
