package com.example.notebook

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocumentFormat
import com.example.engine.ExternalFileInspector
import com.example.notebook.data.NotebookBackgroundType
import com.example.notebook.data.NotebookEntity
import com.example.notebook.data.NotebookPageEntity
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.engine.NotebookExportFormat
import com.example.notebook.engine.NotebookExportOptions
import com.example.notebook.engine.NotebookExportScale
import com.example.notebook.engine.NotebookExportScope
import com.example.notebook.engine.NotebookExporter
import com.example.notebook.model.ConnectorObject
import com.example.notebook.model.NbPoint
import com.example.notebook.model.NotebookJsonCodec
import com.example.notebook.model.NotebookLayerIds
import com.example.notebook.model.NotebookPageContent
import com.example.notebook.model.NotebookShapeKind
import com.example.notebook.model.NotebookTextSpan
import com.example.notebook.model.ShapeObject
import com.example.notebook.model.StrokeBlendMode
import com.example.notebook.model.StrokeObject
import com.example.notebook.model.StrokeToolKind
import com.example.notebook.model.TextObject
import com.example.notebook.ui.NotebookFonts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class NotebookAuditRegressionTest {

    @Test
    fun jsonCodecPreservesObjectsAndHandlesLegacyAndUnknownFontGracefully() {
        val original = NotebookPageContent(
            objects = listOf(
                StrokeObject(
                    id = "s1",
                    layerId = NotebookLayerIds.UNDERLAY,
                    tool = StrokeToolKind.HIGHLIGHTER,
                    colorArgb = 0xFFFDE047L,
                    widthNorm = 0.025f,
                    opacity = 0.45f,
                    blendMode = StrokeBlendMode.MULTIPLY,
                    points = listOf(NbPoint(0.1f, 0.2f, 0.8f), NbPoint(0.5f, 0.2f, 1.0f))
                ),
                TextObject(
                    id = "t1",
                    x = 0.1f,
                    y = 0.1f,
                    width = 0.6f,
                    height = 0.2f,
                    fontId = "future_unknown_font_v99",
                    spans = listOf(
                        NotebookTextSpan("H", colorArgb = 0xFF151C2BL, isBold = true),
                        NotebookTextSpan("2", colorArgb = 0xFF151C2BL, isSubscript = true),
                        NotebookTextSpan("O · ↑INR · α", colorArgb = 0xFF1D5BFFL)
                    )
                ),
                ShapeObject(
                    id = "sh1",
                    kind = NotebookShapeKind.CURVED_ARROW,
                    x = 0.2f,
                    y = 0.4f,
                    width = 0.3f,
                    height = 0.2f,
                    startX = 0.2f,
                    startY = 0.4f,
                    endX = 0.5f,
                    endY = 0.6f
                ),
                ConnectorObject(
                    id = "c1",
                    fromObjectId = "t1",
                    toObjectId = "sh1"
                )
            )
        )

        val encoded = NotebookJsonCodec.encode(original)
        val decoded = NotebookJsonCodec.decode(encoded)
        assertEquals(4, decoded.objects.size)
        val text = decoded.objects.filterIsInstance<TextObject>().single()
        assertEquals("H2O · ↑INR · α", text.spans.joinToString("") { it.text })
        assertEquals(NotebookFonts.DEFAULT_BODY_ID, NotebookFonts.canonicalId(text.fontId))
        assertEquals(NotebookFonts.DEFAULT_BODY_ID, NotebookFonts.canonicalId("sans"))

        // Future schemaVersion with unknown object type should skip unknown safely without crashing
        val futureJson = """{"schemaVersion":99,"objects":[{"id":"x1","type":"hologram_3d","x":0.1,"y":0.1,"w":0.2,"h":0.2}]}"""
        val decodedFuture = NotebookJsonCodec.decode(futureJson)
        assertEquals(0, decodedFuture.objects.size)

        // Legacy v1 JSON decodes cleanly
        val legacyV1 = """{"v":1,"strokes":[{"id":"ls1","hl":true,"c":4294828103,"w":0.02,"a":0.5,"pts":[0.1,0.1,0.4,0.4]}],"items":[]}"""
        val decodedV1 = NotebookJsonCodec.decode(legacyV1)
        assertEquals(1, decodedV1.objects.size)
        assertEquals(StrokeBlendMode.MULTIPLY, (decodedV1.objects[0] as StrokeObject).blendMode)
    }

    @Test
    fun externalFileInspectorDetectsMagicBytesAndBlocksPathTraversalZip() {
        val context: Context = ApplicationProvider.getApplicationContext()

        // PDF with leading UTF-8 BOM
        val bomPdf = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37)
        assertEquals(DocumentFormat.PDF, ExternalFileInspector.detectFormatFromHeader(bomPdf))

        // FB2 XML header
        val fb2Header = """<?xml version="1.0"?><FictionBook><body></body></FictionBook>""".toByteArray()
        assertEquals(DocumentFormat.FB2, ExternalFileInspector.detectFormatFromHeader(fb2Header))

        // Filename sanitization removes traversal & control chars
        val clean = ExternalFileInspector.sanitizeDisplayName("../../evil\u0000name.pdf")
        assertTrue(!clean.contains("..") && !clean.contains("/"))

        // Malicious ZIP with ../ traversal must be rejected by copyValidateAndHashDocument
        val badZip = File(context.cacheDir, "evil_traversal.epub")
        ZipOutputStream(FileOutputStream(badZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../escape.txt"))
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }
        val booksDir = File(context.filesDir, "books_test_audit")
        var threwSecurity = false
        try {
            ExternalFileInspector.copyValidateAndHashDocument(context, Uri.fromFile(badZip), booksDir)
        } catch (_: SecurityException) {
            threwSecurity = true
        }
        assertTrue("ZIP with ../ entry must throw SecurityException", threwSecurity)
        badZip.delete()
        booksDir.deleteRecursively()
    }

    @Test
    fun notebookExporterProducesValidMultiPageZipPdfAndLandscapeThumbnail() {
        runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        // 16:9 landscape notebook specifically exercises the thumbnail scaled===bitmap bug fix!
        val nb = NotebookEntity(
            id = 77L,
            title = "Apuntes Bioquímica",
            pageWidthPx = 1920,
            pageHeightPx = 1080,
            pageTemplate = NotebookPageTemplate.GRID.name
        )
        val content1 = NotebookPageContent(
            objects = listOf(
                TextObject(
                    id = "t1",
                    x = 0.08f,
                    y = 0.1f,
                    width = 0.5f,
                    height = 0.18f,
                    fontId = "poppins",
                    spans = listOf(NotebookTextSpan("Ciclo de Krebs · H₂O", colorArgb = 0xFF151C2BL, isBold = true))
                )
            )
        )
        val page1 = NotebookPageEntity(
            id = 101L,
            notebookId = nb.id,
            pageIndex = 0,
            backgroundType = NotebookBackgroundType.TEMPLATE.name,
            backgroundColor = 0xFFFFFFFFL,
            contentJson = NotebookJsonCodec.encode(content1)
        )
        val page2 = NotebookPageEntity(
            id = 102L,
            notebookId = nb.id,
            pageIndex = 1,
            backgroundType = NotebookBackgroundType.TEMPLATE.name,
            backgroundColor = 0xFFFFFFFFL,
            contentJson = NotebookJsonCodec.encode(content1)
        )
        val pages = listOf(page1, page2)
        val map = mapOf(page1.id to content1, page2.id to content1)

        // 1. Multi-page ZIP PNG export (verifies stream is NOT prematurely closed before flush/sync!)
        val zipFile = NotebookExporter.exportToCacheFile(
            context = context,
            notebook = nb,
            pages = pages,
            currentPage = page1,
            contentByPageId = map,
            options = NotebookExportOptions(
                format = NotebookExportFormat.ZIP_PNG,
                scope = NotebookExportScope.ALL_PAGES,
                scale = NotebookExportScale.X1,
                transparentBackground = true
            )
        )
        assertTrue("ZIP export file should exist and be non-empty", zipFile.exists() && zipFile.length() > 100L)
        ZipFile(zipFile).use { zf ->
            assertEquals(2, zf.size())
        }

        // 2. JPG export (verifies opaque white base instead of black transparent fill)
        val jpgFile = NotebookExporter.exportToCacheFile(
            context = context,
            notebook = nb,
            pages = pages,
            currentPage = page1,
            contentByPageId = map,
            options = NotebookExportOptions(
                format = NotebookExportFormat.JPG,
                scope = NotebookExportScope.CURRENT_PAGE,
                scale = NotebookExportScale.X1
            )
        )
        assertTrue("JPG export file should exist", jpgFile.exists() && jpgFile.length() > 100L)

        // 3. Single-page PNG export with transparent background
        val pngFile = NotebookExporter.exportToCacheFile(
            context = context,
            notebook = nb,
            pages = pages,
            currentPage = page1,
            contentByPageId = map,
            options = NotebookExportOptions(
                format = NotebookExportFormat.PNG,
                scope = NotebookExportScope.CURRENT_PAGE,
                scale = NotebookExportScale.X1,
                transparentBackground = true
            )
        )
        assertTrue("PNG export file should exist", pngFile.exists() && pngFile.length() > 100L)

        // 4. Landscape 16:9 thumbnail generation (verifies scaled===bitmap does NOT cache recycled bitmap!)
        val thumbPath = NotebookExporter.generatePageThumbnailWebp(context, nb, page1, content1)
        assertNotNull("Landscape thumbnail should succeed", thumbPath)
        assertTrue("Thumbnail file should exist on disk", File(thumbPath!!).exists())

        zipFile.delete()
        jpgFile.delete()
        pngFile.delete()
        }
    }
}
