package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FolioDatabase
import com.example.drive.DriveFileEntity
import com.example.drive.DriveFileKind
import com.example.drive.DriveFileSyncStatus
import com.example.drive.DriveFolderLinkParser
import com.example.drive.DriveSecureVault
import com.example.drive.DriveSourceEntity
import com.example.drive.DriveSyncAction
import com.example.drive.DriveSyncMode
import com.example.drive.DriveSyncTaskEntity
import com.example.drive.LumaNotebookPackager
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.data.NotebookRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveIntegrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun folderLinkParser_extractsIdsFromAllGoogleDriveFormatsAndRejectsInvalid() {
        val sampleId = "1aBcDeFgHiJkLmNoPqRsTuVwXyZ_01234"

        assertEquals(
            sampleId,
            DriveFolderLinkParser.extractFolderId("https://drive.google.com/drive/folders/$sampleId")
        )
        assertEquals(
            sampleId,
            DriveFolderLinkParser.extractFolderId("https://drive.google.com/drive/u/0/folders/$sampleId?usp=sharing")
        )
        assertEquals(
            sampleId,
            DriveFolderLinkParser.extractFolderId("https://drive.google.com/drive/mobile/folders/$sampleId?resourcekey=0-abc")
        )
        assertEquals(
            sampleId,
            DriveFolderLinkParser.extractFolderId("https://drive.google.com/open?id=$sampleId")
        )
        assertEquals(
            sampleId,
            DriveFolderLinkParser.extractFolderId(sampleId)
        )

        // Reject untrusted / malformed / non-Drive links
        assertNull(DriveFolderLinkParser.extractFolderId(""))
        assertNull(DriveFolderLinkParser.extractFolderId("https://evil.example.com/drive/folders/$sampleId"))
        assertNull(DriveFolderLinkParser.extractFolderId("javascript:alert(1)"))
        assertNull(DriveFolderLinkParser.extractFolderId("short"))
    }

    @Test
    fun fileNameSanitizer_preventsPathTraversalAndBlankNames() {
        val sanitized = DriveFolderLinkParser.sanitizeLocalFileName("../../etc/passwd\u0000.pdf")
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
        assertTrue(sanitized.endsWith(".pdf"))

        assertEquals("archivo_drive", DriveFolderLinkParser.sanitizeLocalFileName("   \t  "))
    }

    @Test
    fun secureVault_savesLoadsAndClearsCredentialsSafely() {
        val vault = DriveSecureVault(context)
        vault.saveClientConfig(
            clientId = "1234567890-abc.apps.googleusercontent.com",
            apiKey = "AIzaTestKeyForUnitVerificationOnly",
            useMinimalScope = false
        )
        vault.saveSyncMode(DriveSyncMode.WIFI_ONLY)
        vault.saveOAuthTokens(
            accessToken = "ya29.test-access-token",
            refreshToken = "1//test-refresh-token",
            expiresInSec = 3600L,
            accountEmail = "lector@example.com"
        )

        assertEquals("1234567890-abc.apps.googleusercontent.com", vault.getClientId())
        assertEquals("AIzaTestKeyForUnitVerificationOnly", vault.getApiKey())
        assertEquals(DriveSyncMode.WIFI_ONLY, vault.getSyncMode())
        assertEquals("ya29.test-access-token", vault.getAccessToken())
        assertEquals("1//test-refresh-token", vault.getRefreshToken())
        assertTrue(vault.authSnapshot.value.isSignedIn)
        assertEquals("lector@example.com", vault.authSnapshot.value.accountEmail)

        vault.clearAuthTokens()
        assertFalse(vault.authSnapshot.value.isSignedIn)
        assertEquals("", vault.getAccessToken())
        assertEquals("", vault.getRefreshToken())
        // Client ID stays configured after sign-out so user can reconnect quickly
        assertEquals("1234567890-abc.apps.googleusercontent.com", vault.getClientId())
    }

    @Test
    fun lumaNotebookPackager_roundTripExportsAndImportsWithPages(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = NotebookRepository(context, db.notebookDao())
            val originalId = repo.createNotebook(
                title = "Apuntes de Anatomía",
                template = NotebookPageTemplate.GRID,
                pageWidthPx = 1240,
                pageHeightPx = 1754,
                backgroundColor = 0xFFFFFFFFL
            )
            val originalPages = repo.getPagesOnce(originalId)
            assertTrue(originalPages.isNotEmpty())

            val outFile = File(context.cacheDir, "test_export_${System.nanoTime()}.luma")
            val exported = LumaNotebookPackager.exportNotebookPackage(
                context = context,
                notebookDao = db.notebookDao(),
                notebookId = originalId,
                destFile = outFile
            )
            assertTrue(exported.exists() && exported.length() > 100L)

            val importedId = LumaNotebookPackager.importNotebookPackage(
                context = context,
                notebookDao = db.notebookDao(),
                lumaFile = exported,
                overwriteNotebookId = null
            )
            assertTrue(importedId > 0L)
            assertTrue(importedId != originalId)

            val importedNb = db.notebookDao().getNotebook(importedId)
            assertNotNull(importedNb)
            assertEquals("Apuntes de Anatomía", importedNb?.title)
            assertEquals(NotebookPageTemplate.GRID.name, importedNb?.pageTemplate)

            val importedPages = db.notebookDao().getPagesOnce(importedId)
            assertEquals(originalPages.size, importedPages.size)
            runCatching { outFile.delete() }
        } finally {
            db.close()
        }
    }

    @Test
    fun driveDao_sourcesFilesAndSyncQueueCrudWorksInV6Schema(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.driveDao()
            val sourceId = dao.upsertSource(
                DriveSourceEntity(
                    name = "Universidad",
                    folderId = "1FolderIdExample00001",
                    folderLink = "https://drive.google.com/drive/folders/1FolderIdExample00001",
                    accentColorHex = 0xFF2563EBL,
                    enabled = true,
                    isDefaultSaveTarget = true
                )
            )
            assertTrue(sourceId > 0L)
            val sources = dao.getSourcesOnce()
            assertEquals(1, sources.size)
            assertEquals("Universidad", sources.first().name)

            dao.upsertFiles(
                listOf(
                    DriveFileEntity(
                        driveFileId = "file_pdf_01",
                        sourceId = sourceId,
                        parentFolderId = "1FolderIdExample00001",
                        name = "Cardiología.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 2_400_000L,
                        syncStatus = DriveFileSyncStatus.CLOUD_ONLY.name
                    ),
                    DriveFileEntity(
                        driveFileId = "file_nb_01",
                        sourceId = sourceId,
                        parentFolderId = "1FolderIdExample00001",
                        name = "Resumen.luma",
                        mimeType = DriveFileKind.LUMA_MIME,
                        sizeBytes = 85_000L,
                        syncStatus = DriveFileSyncStatus.SYNCED.name
                    )
                )
            )

            val loaded = dao.getFileById("file_pdf_01")
            assertNotNull(loaded)
            assertEquals(DriveFileKind.PDF, loaded?.kind)

            val taskId = dao.enqueueSyncTask(
                DriveSyncTaskEntity(
                    action = DriveSyncAction.UPLOAD_NOTEBOOK.name,
                    sourceId = sourceId,
                    targetFolderId = "1FolderIdExample00001",
                    localNotebookId = 7L,
                    displayName = "Resumen.luma"
                )
            )
            assertTrue(taskId > 0L)
            assertEquals(1, dao.getSyncQueueOnce().size)
            dao.deleteSyncTask(taskId)
            assertTrue(dao.getSyncQueueOnce().isEmpty())
        } finally {
            db.close()
        }
    }
}
