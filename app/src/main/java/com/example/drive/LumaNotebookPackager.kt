package com.example.drive

import android.content.Context
import android.util.Base64
import com.example.notebook.data.NotebookAssetStore
import com.example.notebook.data.NotebookDao
import com.example.notebook.data.NotebookEntity
import com.example.notebook.data.NotebookPageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Portable `.luma` notebook package serializer/deserializer for cross-device Google Drive sync.
 * Packages notebook metadata, all pages (`contentJson`), embedded image/PDF assets, and cover thumbnail
 * into a single self-contained `.luma` JSON file so any device connected to the same Drive folder
 * can reopen and continue editing seamlessly.
 */
object LumaNotebookPackager {
    private const val PACKAGE_FORMAT_VERSION = 1
    private const val MAX_TOTAL_ASSET_BYTES = 40L * 1024L * 1024L

    suspend fun exportNotebookPackage(
        context: Context,
        notebookDao: NotebookDao,
        notebookId: Long,
        destFile: File
    ): File = withContext(Dispatchers.IO) {
        val notebook = notebookDao.getNotebook(notebookId)
            ?: throw IllegalStateException("No se encontró el cuaderno local.")
        val pages = notebookDao.getPagesOnce(notebookId)

        val root = JSONObject().apply {
            put("lumaPackageVersion", PACKAGE_FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("notebook", JSONObject().apply {
                put("title", notebook.title)
                put("createdAt", notebook.createdAt)
                put("updatedAt", notebook.updatedAt)
                put("pageWidthPx", notebook.pageWidthPx)
                put("pageHeightPx", notebook.pageHeightPx)
                put("pageTemplate", notebook.pageTemplate)
            })
        }

        val pagesArr = JSONArray()
        for (page in pages) {
            pagesArr.put(JSONObject().apply {
                put("pageIndex", page.pageIndex)
                put("backgroundType", page.backgroundType)
                put("backgroundColor", page.backgroundColor)
                put("backgroundAssetPath", page.backgroundAssetPath)
                put("contentJson", page.contentJson)
                put("schemaVersion", page.schemaVersion)
            })
        }
        root.put("pages", pagesArr)

        // Embed cover thumbnail if available
        val thumbCandidate = notebook.coverImagePath.takeIf { it.isNotBlank() && File(it).exists() }
            ?: pages.firstOrNull()?.thumbnailPath?.takeIf { it.isNotBlank() && File(it).exists() }
        if (thumbCandidate != null) {
            runCatching {
                val bytes = File(thumbCandidate).readBytes()
                if (bytes.size <= 2 * 1024 * 1024) {
                    root.put("coverThumbnailBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
            }
        }

        // Embed notebook assets (background PDFs/images, pasted photos, PNG stickers)
        val assetsObj = JSONObject()
        val assetsDir = NotebookAssetStore.assetsDir(context, notebookId)
        var embeddedBytes = 0L
        if (assetsDir.exists() && assetsDir.isDirectory) {
            assetsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0L && embeddedBytes + file.length() <= MAX_TOTAL_ASSET_BYTES) {
                    runCatching {
                        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        assetsObj.put(file.name, b64)
                        embeddedBytes += file.length()
                    }
                }
            }
        }
        root.put("assets", assetsObj)

        destFile.parentFile?.mkdirs()
        destFile.writeText(root.toString(), Charsets.UTF_8)
        destFile
    }

    suspend fun importNotebookPackage(
        context: Context,
        notebookDao: NotebookDao,
        lumaFile: File,
        overwriteNotebookId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        if (!lumaFile.exists() || lumaFile.length() <= 0L) {
            throw IllegalArgumentException("El archivo .luma está vacío o no existe.")
        }
        val root = JSONObject(lumaFile.readText(Charsets.UTF_8))
        val nbJson = root.optJSONObject("notebook")
            ?: throw IllegalArgumentException("Formato de cuaderno .luma inválido.")

        val now = System.currentTimeMillis()
        val title = nbJson.optString("title", lumaFile.nameWithoutExtension).ifBlank { "Cuaderno importado" }
        val pageWidthPx = nbJson.optInt("pageWidthPx", NotebookEntity.DEFAULT_PAGE_WIDTH_PX).coerceIn(600, 3000)
        val pageHeightPx = nbJson.optInt("pageHeightPx", NotebookEntity.DEFAULT_PAGE_HEIGHT_PX).coerceIn(600, 3600)
        val pageTemplate = nbJson.optString("pageTemplate", "BLANK")

        val notebookId = if (overwriteNotebookId != null && overwriteNotebookId > 0L && notebookDao.getNotebook(overwriteNotebookId) != null) {
            notebookDao.insertNotebook(
                NotebookEntity(
                    id = overwriteNotebookId,
                    title = title,
                    createdAt = nbJson.optLong("createdAt", now),
                    updatedAt = nbJson.optLong("updatedAt", now),
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    pageTemplate = pageTemplate
                )
            )
            // Replace existing pages cleanly
            notebookDao.getPagesOnce(overwriteNotebookId).forEach { oldPage ->
                notebookDao.deletePage(oldPage.id)
            }
            overwriteNotebookId
        } else {
            notebookDao.insertNotebook(
                NotebookEntity(
                    title = title,
                    createdAt = nbJson.optLong("createdAt", now),
                    updatedAt = nbJson.optLong("updatedAt", now),
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    pageTemplate = pageTemplate
                )
            )
        }

        // Restore embedded assets with strict path-traversal confinement
        val assetsDir = NotebookAssetStore.assetsDir(context, notebookId)
        val assetsRootCanonical = assetsDir.canonicalPath + File.separator
        val assetsObj = root.optJSONObject("assets")
        if (assetsObj != null) {
            val keys = assetsObj.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                val safeName = DriveFolderLinkParser.sanitizeLocalFileName(rawKey)
                val target = File(assetsDir, safeName)
                if (!target.canonicalPath.startsWith(assetsRootCanonical)) continue
                val b64 = assetsObj.optString(rawKey)
                if (b64.isNotBlank()) {
                    runCatching {
                        target.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                    }
                }
            }
        }

        // Restore cover thumbnail if bundled
        var coverPath = ""
        val coverB64 = root.optString("coverThumbnailBase64")
        if (coverB64.isNotBlank()) {
            val thumbDir = NotebookAssetStore.thumbnailsDir(context, notebookId)
            val thumbFile = File(thumbDir, "drive_cover_${notebookId}.webp")
            runCatching {
                thumbFile.writeBytes(Base64.decode(coverB64, Base64.DEFAULT))
                coverPath = thumbFile.absolutePath
            }
        }

        val pagesArr = root.optJSONArray("pages")
        val importedPages = ArrayList<NotebookPageEntity>()
        if (pagesArr != null && pagesArr.length() > 0) {
            for (i in 0 until pagesArr.length()) {
                val pObj = pagesArr.optJSONObject(i) ?: continue
                importedPages.add(
                    NotebookPageEntity(
                        notebookId = notebookId,
                        pageIndex = pObj.optInt("pageIndex", i),
                        backgroundType = pObj.optString("backgroundType", "TEMPLATE"),
                        backgroundColor = pObj.optLong("backgroundColor", 0xFFFFFFFFL),
                        backgroundAssetPath = DriveFolderLinkParser.sanitizeLocalFileName(
                            pObj.optString("backgroundAssetPath", ""),
                            fallback = ""
                        ).takeIf { pObj.optString("backgroundAssetPath", "").isNotBlank() } ?: "",
                        contentJson = pObj.optString("contentJson", ""),
                        schemaVersion = pObj.optInt("schemaVersion", NotebookPageEntity.CURRENT_CONTENT_SCHEMA_VERSION),
                        thumbnailPath = if (i == 0) coverPath else ""
                    )
                )
            }
        }
        if (importedPages.isEmpty()) {
            importedPages.add(
                NotebookPageEntity(
                    notebookId = notebookId,
                    pageIndex = 0,
                    backgroundType = "TEMPLATE",
                    backgroundColor = 0xFFFFFFFFL,
                    thumbnailPath = coverPath
                )
            )
        }
        notebookDao.insertPages(importedPages)
        if (coverPath.isNotBlank()) {
            notebookDao.touchNotebook(notebookId, coverImagePath = coverPath)
        }
        notebookId
    }

    /**
     * Extracts just the bundled thumbnail from a `.luma` file for Drive library preview cards.
     */
    fun extractThumbnailFromPackage(lumaFile: File, destThumbFile: File): String? {
        return runCatching {
            val root = JSONObject(lumaFile.readText(Charsets.UTF_8))
            val b64 = root.optString("coverThumbnailBase64")
            if (b64.isBlank()) return@runCatching null
            destThumbFile.parentFile?.mkdirs()
            destThumbFile.writeBytes(Base64.decode(b64, Base64.DEFAULT))
            destThumbFile.absolutePath
        }.getOrNull()
    }
}
