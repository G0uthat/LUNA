package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DocumentEntity
import com.example.data.local.HighlightNoteEntity
import com.example.data.local.PreferencesRepository
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.ExportFormatOption
import com.example.data.model.OcrBlockBox
import com.example.data.model.OcrPageResult
import com.example.data.model.OcrWordBox
import com.example.data.model.ReadingTheme
import com.example.data.model.StrokePoint
import com.example.data.model.TargetFpsOption
import com.example.engine.CoverManager
import com.example.engine.DocumentParser
import com.example.engine.LumaDiskCacheManager
import com.example.engine.ParsedDocumentDiskCache
import com.example.engine.TranslationService
import com.example.notebook.data.NotebookBackgroundType
import com.example.notebook.data.NotebookPageTemplate
import com.example.notebook.engine.NotebookCanvasRenderer
import com.example.notebook.model.NbPoint
import com.example.notebook.model.NotebookLayerIds
import com.example.notebook.model.NotebookPageContent
import com.example.notebook.model.StrokeObject
import com.example.notebook.model.StrokeToolKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AllFormatsAndSettingsAuditTest {

    private lateinit var context: Context
    private lateinit var workDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workDir = File(context.cacheDir, "audit_test_${System.nanoTime()}").apply { mkdirs() }
        ParsedDocumentDiskCache.clearMemoryCache()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. EPUB: URL-encoded + ".." paths, Spanish/numeric HTML entities, h4, cover crop & safe merged export
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun epub_parsesUrlEncodedPathsEntitiesHeadingsCoverAndSafeMergedExport() = runBlocking {
        val epubFile = createTestEpub(File(workDir, "novela_prueba.epub"))
        val parsed = DocumentParser.parse(context, epubFile, DocumentFormat.EPUB)

        assertEquals(DocumentFormat.EPUB, parsed.format)
        assertEquals("El Niño y la Brújula", parsed.title)
        assertEquals("Autora Española", parsed.author)
        assertEquals(1, parsed.chapters.size)

        val chapter = parsed.chapters.first()
        val allText = chapter.blocks.joinToString("\n") { it.text }
        assertTrue("Expected Spanish named/numeric entities decoded: $allText", allText.contains("¿Quién soñó — con café?"))
        assertTrue("Expected guillemets decoded: $allText", allText.contains("«Hola»"))
        assertTrue("Expected h4 preserved as HEADING_2", chapter.blocks.any { it.type == BlockType.HEADING_2 && it.text.contains("Sección menor") })

        // Cover extraction preserves aspect ratio (420x620)
        val coverPath = CoverManager.generateOrExtractCover(context, epubFile, DocumentFormat.EPUB, parsed.title, parsed.author, 101L)
        assertTrue(File(coverPath).exists() && File(coverPath).length() > 0L)

        // Merged EPUB export: quote matches BOTH <title> in <head> AND <p> in <body>; <head> MUST remain untouched!
        val docEntity = DocumentEntity(
            id = 101L,
            title = parsed.title,
            author = parsed.author,
            format = "EPUB",
            filePath = epubFile.absolutePath,
            coverImagePath = coverPath,
            totalUnits = parsed.chapters.size
        )
        val highlights = listOf(
            HighlightNoteEntity(
                id = 1L,
                documentId = 101L,
                locationKey = chapter.cfiPrefix,
                chapterIndex = 0,
                blockIndex = 1,
                startChar = 3,
                endChar = 10,
                quotedText = "Brújula",
                noteText = "Palabra clave",
                colorArgb = 0xFFFFE066L
            )
        )
        val strokes = mapOf(
            chapter.cfiPrefix to listOf(
                DrawingStroke(
                    id = "s1",
                    tool = DrawingTool.PEN,
                    colorArgb = 0xFF1D5BFFL,
                    widthNorm = 0.005f,
                    alpha = 1f,
                    points = listOf(StrokePoint(0.1f, 0.2f), StrokePoint(0.5f, 0.6f))
                )
            )
        )

        val exported = com.example.engine.MergedDocumentExporter.exportToInternalCache(
            context = context,
            document = docEntity,
            parsed = parsed,
            drawingsByLocation = strokes,
            highlights = highlights,
            ocrByPage = emptyMap(),
            formatOption = ExportFormatOption.NATIVE_MERGED
        ).getOrThrow()

        ZipFile(exported).use { zip ->
            val xhtml = zip.getInputStream(zip.getEntry("OEBPS/text/cap 1.xhtml")).bufferedReader().readText()
            val headSection = xhtml.substringBefore("<body")
            val bodySection = xhtml.substringAfter("<body")
            assertFalse("<head> must never be mutated with <mark>: $headSection", headSection.contains("<mark"))
            assertTrue("<body> must contain <mark> highlight: $bodySection", bodySection.contains("<mark"))
            assertTrue("<body> must contain vector SVG overlay: $bodySection", bodySection.contains("<svg"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. FB2: Inline tags inside <p>, ISO-8859-1 encoding auto-detect, <subtitle>, <cite>, <v>
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun fb2_parsesNestedInlineTagsAndIso8859EncodingWithoutDroppingParagraphs() = runBlocking {
        val fb2File = File(workDir, "cuento_inline.fb2")
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <author><first-name>Sofía</first-name><last-name>Núñez</last-name></author>
                  <book-title>Crónica del Mañana</book-title>
                </title-info>
              </description>
              <body>
                <section>
                  <title><p>Capítulo <emphasis>Primero</emphasis></p></title>
                  <subtitle>Subtítulo <strong>clave</strong></subtitle>
                  <p>Texto con <emphasis>cursiva</emphasis>, <strong>negrita</strong> y <a href="#n1">enlace</a> en español.</p>
                  <cite><p>Cita célebre con <strong>énfasis</strong>.</p></cite>
                  <poem><stanza><v>Verso libre bajo la luna.</v></stanza></poem>
                </section>
              </body>
            </FictionBook>
        """.trimIndent()
        fb2File.writeBytes(xml.toByteArray(Charsets.ISO_8859_1))

        val parsed = DocumentParser.parse(context, fb2File, DocumentFormat.FB2)
        assertEquals("Crónica del Mañana", parsed.title)
        assertEquals("Sofía Núñez", parsed.author)
        assertEquals(1, parsed.chapters.size)

        val blocks = parsed.chapters.first().blocks
        assertTrue("Inline-formatted title must be preserved", blocks.any { it.type == BlockType.HEADING_1 && it.text == "Capítulo Primero" })
        assertTrue("Subtitle must be HEADING_2", blocks.any { it.type == BlockType.HEADING_2 && it.text == "Subtítulo clave" })
        assertTrue(
            "Paragraph with nested <emphasis>/<strong>/<a> must NOT be dropped",
            blocks.any { it.type == BlockType.PARAGRAPH && it.text.contains("Texto con cursiva, negrita y enlace en español.") }
        )
        assertTrue("Cite paragraph must be QUOTE", blocks.any { it.type == BlockType.QUOTE && it.text.contains("Cita célebre") })
        assertTrue("Poem verse must be QUOTE", blocks.any { it.type == BlockType.QUOTE && it.text.contains("Verso libre") })
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. DOCX: Nested <w:p> inside textboxes, localized Spanish Heading styles, merged DOCX export
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun docx_parsesNestedTextboxParagraphsSpanishHeadingsAndMergedExport() = runBlocking {
        val docxFile = createTestDocx(File(workDir, "apuntes_medicos.docx"))
        val parsed = DocumentParser.parse(context, docxFile, DocumentFormat.DOCX)

        assertEquals(DocumentFormat.DOCX, parsed.format)
        assertEquals("Guía de Cardiología", parsed.title)
        assertEquals("Dra. Elena Ruiz", parsed.author)
        assertEquals(2, parsed.chapters.size)
        assertEquals("Introducción Clínica", parsed.chapters[0].title)
        assertEquals("Tratamiento Avanzado", parsed.chapters[1].title)

        val allText = parsed.chapters.flatMap { it.blocks }.joinToString("\n") { it.text }
        assertTrue("Inner textbox paragraph preserved", allText.contains("Recuadro de dosis urgente"))
        assertTrue("Outer paragraph text after nested textbox MUST NOT be dropped: $allText", allText.contains("Inicio exterior") && allText.contains("continuación exterior intacta"))

        // Merged DOCX export
        val docEntity = DocumentEntity(
            id = 202L,
            title = parsed.title,
            author = parsed.author,
            format = "DOCX",
            filePath = docxFile.absolutePath,
            coverImagePath = "",
            totalUnits = parsed.chapters.size
        )
        val exported = com.example.engine.MergedDocumentExporter.exportToInternalCache(
            context = context,
            document = docEntity,
            parsed = parsed,
            drawingsByLocation = emptyMap(),
            highlights = listOf(
                HighlightNoteEntity(
                    id = 2L,
                    documentId = 202L,
                    locationKey = "docx(/section/0)",
                    chapterIndex = 0,
                    blockIndex = 1,
                    startChar = 0,
                    endChar = 13,
                    quotedText = "dosis urgente",
                    noteText = "Revisar en guardia",
                    colorArgb = 0xFFFFE066L
                )
            ),
            ocrByPage = emptyMap(),
            formatOption = ExportFormatOption.NATIVE_MERGED
        ).getOrThrow()

        ZipFile(exported).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
            assertTrue(xml.contains("Anotaciones y Dibujos Fusionados (Luma)"))
            assertTrue(xml.contains("Revisar en guardia"))
            assertTrue(xml.contains("Tratamiento Avanzado"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. PPTX: 16:9 slide geometry, theme colors, tables, notes, disk cache round-trip
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun pptx_parsesSlidesThemeTablesNotesAndDiskCacheRoundTrip() = runBlocking {
        val pptxFile = createTestPptx(File(workDir, "clase_fisiologia.pptx"))
        val parsed = DocumentParser.parse(context, pptxFile, DocumentFormat.PPTX)

        assertEquals(DocumentFormat.PPTX, parsed.format)
        assertEquals("Fisiología Renal", parsed.title)
        assertEquals("Dr. Marco Silva", parsed.author)
        assertEquals(1, parsed.slides.size)
        val slide = parsed.slides.first()
        assertEquals("Filtración Glomerular", slide.title)
        assertTrue("Expected speaker notes parsed", slide.notes.contains("Explicar presión oncótica"))
        assertTrue("Expected table cell parsed", slide.bodyBlocks.any { it.text.contains("TFG") && it.text.contains("125 mL/min") })

        // Verify ParsedDocumentDiskCache round-trip preserves slide elements & notes
        ParsedDocumentDiskCache.clearMemoryCache()
        val cached = ParsedDocumentDiskCache.read(context, pptxFile, DocumentFormat.PPTX)
        assertNotNull("Expected PPTX disk cache hit", cached)
        assertEquals(parsed.slides.size, cached!!.slides.size)
        assertEquals(slide.notes, cached.slides.first().notes)
        assertEquals(slide.elements.size, cached.slides.first().elements.size)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5. TXT: UTF-8 BOM stripping & Windows-1252 Spanish fallback
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun txt_stripsUtf8BomAndFallsBackToWindows1252ForSpanishAnsiFiles() = runBlocking {
        // UTF-8 with BOM
        val bomFile = File(workDir, "bom_utf8.txt")
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Prólogo\n\nEste texto tiene acentos áéíóúñ.".toByteArray(Charsets.UTF_8)
        bomFile.writeBytes(bomBytes)

        val parsedBom = DocumentParser.parse(context, bomFile, DocumentFormat.TXT)
        val firstBlock = parsedBom.chapters.first().blocks.first().text
        assertFalse("BOM must be stripped from first line", firstBlock.startsWith("\uFEFF"))
        assertEquals("Prólogo", firstBlock)

        // Windows-1252 Spanish file (invalid UTF-8 bytes)
        val ansiFile = File(workDir, "ansi_espanol.txt")
        val cp1252 = Charset.forName("windows-1252")
        ansiFile.writeBytes("Canción de España\n\nEl niño soñó con música clásica.".toByteArray(cp1252))

        val parsedAnsi = DocumentParser.parse(context, ansiFile, DocumentFormat.TXT)
        val ansiText = parsedAnsi.chapters.first().blocks.joinToString("\n") { it.text }
        assertFalse("Must not produce replacement character : $ansiText", ansiText.contains('\uFFFD'))
        assertTrue("Expected decoded Spanish text: $ansiText", ansiText.contains("Canción de España") && ansiText.contains("niño soñó"))
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6. PDF: Multi-page PDFBox fallback parsing, disk cache, and merged vector + OCR PDF export
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun pdf_parsesMultiPageWithPdfBoxFallbackAndExportsMergedStrokesAndOcr() = runBlocking {
        PDFBoxResourceLoader.init(context.applicationContext)
        val pdfFile = createTestPdf(File(workDir, "manual_dos_paginas.pdf"))

        val parsed = DocumentParser.parse(context, pdfFile, DocumentFormat.PDF)
        assertEquals(DocumentFormat.PDF, parsed.format)
        assertEquals("Manual Clinico", parsed.title)
        assertEquals("All 2 pages must be parsed even when native PdfRenderer is absent on JVM", 2, parsed.pdfPageCount)
        assertTrue(parsed.pdfPageTexts[0].contains("Pagina uno contenido clinico"))
        assertTrue(parsed.pdfPageTexts[1].contains("Pagina dos protocolo final"))

        // Export merged PDF with vector stroke + invisible selectable OCR word layer via PDFBox
        val docEntity = DocumentEntity(
            id = 303L,
            title = parsed.title,
            author = parsed.author,
            format = "PDF",
            filePath = pdfFile.absolutePath,
            coverImagePath = "",
            totalUnits = parsed.pdfPageCount
        )
        val strokes = mapOf(
            "pdf-page-0" to listOf(
                DrawingStroke(
                    id = "pdf_s1",
                    tool = DrawingTool.HIGHLIGHTER,
                    colorArgb = 0xFFFFEB3BL,
                    widthNorm = 0.02f,
                    alpha = 0.35f,
                    points = listOf(StrokePoint(0.1f, 0.2f), StrokePoint(0.7f, 0.2f))
                )
            )
        )
        val ocr = mapOf(
            0 to OcrPageResult(
                pageIndex = 0,
                blocks = listOf(
                    OcrBlockBox(
                        id = 0,
                        originalText = "OCRTAGVISIBLE",
                        translatedText = null,
                        left = 0.1f,
                        top = 0.4f,
                        right = 0.5f,
                        bottom = 0.46f,
                        words = listOf(OcrWordBox("OCRTAGVISIBLE", 0.1f, 0.4f, 0.5f, 0.46f))
                    )
                )
            )
        )

        val exportedPdf = com.example.engine.MergedDocumentExporter.exportToInternalCache(
            context = context,
            document = docEntity,
            parsed = parsed,
            drawingsByLocation = strokes,
            highlights = emptyList(),
            ocrByPage = ocr,
            formatOption = ExportFormatOption.NATIVE_MERGED
        ).getOrThrow()

        assertTrue(exportedPdf.exists() && exportedPdf.length() > pdfFile.length())
        PDDocument.load(exportedPdf).use { merged ->
            assertEquals(2, merged.numberOfPages)
            val extracted = PDFTextStripper().getText(merged)
            assertTrue("Original selectable PDF text preserved", extracted.contains("Pagina uno contenido clinico"))
            assertTrue("Invisible selectable OCR layer embedded", extracted.contains("OCRTAGVISIBLE"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 7. SETTINGS, PREFERENCES CONCURRENCY & DISK CACHE AUDIT
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun settings_preferencesRepositoryHandlesConcurrentTogglesAndClampsBounds() = runBlocking {
        val repo = PreferencesRepository.getInstance(context)
        repo.awaitInitialPreferences()

        // Fire rapid back-to-back discrete toggles (tests writeMutex + pendingWrites fix)
        val jobs = listOf(
            async { repo.updatePreferences { it.copy(theme = ReadingTheme.SEPIA) } },
            async { repo.updatePreferences { it.copy(showFpsCounter = true) } },
            async { repo.updatePreferences { it.copy(targetFps = TargetFpsOption.FPS_120) } },
            async { repo.updatePreferences { it.copy(useStandardPageView = true) } },
            async { repo.updatePreferences { it.copy(nightModeEnabled = true, nightStartHour = 28, nightEndHour = -3) } },
            async { repo.updatePreferences { it.copy(fontSizeSp = 99f, readerBrightness = 2.5f, diskCacheLimitMb = 50) } },
            async {
                repo.updatePreferences {
                    it.copy(
                        autoHideNotebookChromeEnabled = true,
                        autoHideNotebookChromeLandscapeOnly = false,
                        autoHideNotebookChromeDelaySec = 99,
                        autoHideNotebookFadeSpeedMs = 15
                    )
                }
            }
        )
        jobs.awaitAll()
        repo.flushAndAwaitPersistence()

        val finalPrefs = repo.preferencesFlow.value
        assertEquals(ReadingTheme.SEPIA, finalPrefs.theme)
        assertTrue(finalPrefs.showFpsCounter)
        assertEquals(TargetFpsOption.FPS_120, finalPrefs.targetFps)
        assertTrue(finalPrefs.useStandardPageView)
        assertTrue(finalPrefs.nightModeEnabled)
        assertEquals(23, finalPrefs.nightStartHour)
        assertEquals(0, finalPrefs.nightEndHour)
        assertEquals(28f, finalPrefs.fontSizeSp, 0.01f)
        assertEquals(1f, finalPrefs.readerBrightness, 0.01f)
        assertEquals(LumaDiskCacheManager.MIN_LIMIT_MB, finalPrefs.diskCacheLimitMb)
        assertTrue(finalPrefs.autoHideNotebookChromeEnabled)
        assertFalse(finalPrefs.autoHideNotebookChromeLandscapeOnly)
        assertEquals(30, finalPrefs.autoHideNotebookChromeDelaySec)
        assertEquals(80, finalPrefs.autoHideNotebookFadeSpeedMs)

        // Verify TranslationService URL builder + word cleaner
        assertEquals("corazón", TranslationService.cleanWord("«corazón»,"))
        assertEquals(
            "https://dle.rae.es/hola%20mundo",
            TranslationService.buildTemplatedUrl("https://dle.rae.es/{palabra}", "hola mundo")
        )
    }

    @Test
    fun settings_diskCacheManagerInspectsEvictsPptxMediaAndClearsSafelyWithoutTouchingBooks() = runBlocking {
        // Create protected user book + regenerable cache files
        val bookFile = File(File(context.filesDir, "books").apply { mkdirs() }, "mi_libro.epub").apply {
            writeBytes(ByteArray(2048) { 7 })
        }
        val pptxMediaFile = File(File(context.cacheDir, "pptx_media/test_deck").apply { mkdirs() }, "img1.png").apply {
            writeBytes(ByteArray(4096) { 3 })
        }
        val exportTemp = File(File(context.filesDir, "exports").apply { mkdirs() }, "temp_export.pdf").apply {
            writeBytes(ByteArray(4096) { 5 })
        }

        val before = LumaDiskCacheManager.inspectUsage(context)
        assertTrue("Expected cache bytes reported > 0", before.totalBytes >= 8192L)

        val freed = LumaDiskCacheManager.clearAllCaches(context)
        assertTrue("Expected reclaimed bytes > 0, got $freed", freed >= 8192L)
        assertFalse("Temp export must be cleared", exportTemp.exists())
        assertFalse("PPTX media cache must be cleared", pptxMediaFile.exists())
        assertTrue("User books must NEVER be deleted by Limpiar caché", bookFile.exists())
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 8. LOCAL JVM MICRO-BENCHMARK BASELINE (Stroke Render Latency + Cold vs Cached Parse)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun jvmBaseline_measureStrokeRenderLatencyAndCacheAcceleration() = runBlocking {
        // A. Stroke rendering latency baseline across 60 live-drawing frames (Canvas software pipeline)
        val bitmap = Bitmap.createBitmap(1080, 1527, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val existingStrokes = List(15) { s ->
            StrokeObject(
                id = "st_$s",
                layerId = NotebookLayerIds.INK,
                tool = StrokeToolKind.PEN,
                colorArgb = 0xFF1E293BL,
                widthNorm = 0.004f,
                opacity = 1f,
                points = List(40) { i -> NbPoint(0.1f + i * 0.02f, 0.1f + s * 0.05f + (i % 3) * 0.004f, 0.7f) }
            )
        }
        val pageContent = NotebookPageContent(objects = existingStrokes)
        val frameTimesMs = DoubleArray(60)

        // Warmup 10 frames
        repeat(10) {
            NotebookCanvasRenderer.drawPage(
                canvas = canvas,
                widthPx = 1080f,
                heightPx = 1527f,
                template = NotebookPageTemplate.GRID,
                backgroundType = NotebookBackgroundType.TEMPLATE,
                backgroundColorArgb = 0xFFFFFFFFL,
                backgroundBitmap = null,
                content = pageContent,
                assetBitmapProvider = { null }
            )
        }

        // Measure 60 live-stroke frames
        for (f in 0 until 60) {
            val liveStroke = StrokeObject(
                id = "live",
                layerId = NotebookLayerIds.INK,
                tool = StrokeToolKind.PEN,
                colorArgb = 0xFF2563EBL,
                widthNorm = 0.0045f,
                opacity = 1f,
                points = List(f + 4) { p -> NbPoint(0.15f + p * 0.01f, 0.50f + (p % 5) * 0.003f, 0.8f) }
            )
            val t0 = System.nanoTime()
            NotebookCanvasRenderer.drawPage(
                canvas = canvas,
                widthPx = 1080f,
                heightPx = 1527f,
                template = NotebookPageTemplate.GRID,
                backgroundType = NotebookBackgroundType.TEMPLATE,
                backgroundColorArgb = 0xFFFFFFFFL,
                backgroundBitmap = null,
                content = pageContent,
                assetBitmapProvider = { null },
                liveStroke = liveStroke
            )
            frameTimesMs[f] = (System.nanoTime() - t0) / 1_000_000.0
        }
        bitmap.recycle()

        val sortedFrames = frameTimesMs.sorted()
        val avgFrameMs = frameTimesMs.average()
        val p50FrameMs = sortedFrames[30]
        val p95FrameMs = sortedFrames[57]

        // B. Cold parse vs Disk Cache hit vs RAM Cache hit baseline
        LumaDiskCacheManager.clearAllCaches(context)
        val benchBooksDir = File(context.filesDir, "books_bench").apply { mkdirs() }
        val epubFile = createTestEpub(File(benchBooksDir, "bench_book.epub"))

        val tCold0 = System.nanoTime()
        val coldDoc = DocumentParser.parse(context, epubFile, DocumentFormat.EPUB)
        val coldMs = (System.nanoTime() - tCold0) / 1_000_000.0

        ParsedDocumentDiskCache.clearMemoryCache()
        val tDisk0 = System.nanoTime()
        val diskDoc = DocumentParser.parse(context, epubFile, DocumentFormat.EPUB)
        val diskMs = (System.nanoTime() - tDisk0) / 1_000_000.0

        val tRam0 = System.nanoTime()
        val ramDoc = DocumentParser.parse(context, epubFile, DocumentFormat.EPUB)
        val ramMs = (System.nanoTime() - tRam0) / 1_000_000.0

        assertEquals(coldDoc.title, diskDoc.title)
        assertEquals(coldDoc.title, ramDoc.title)
        println(
            "LUMA_JVM_BASELINE | stroke_frame_avg=${"%.2f".format(avgFrameMs)}ms " +
                "p50=${"%.2f".format(p50FrameMs)}ms p95=${"%.2f".format(p95FrameMs)}ms | " +
                "epub_cold=${"%.2f".format(coldMs)}ms disk_cache=${"%.2f".format(diskMs)}ms ram_cache=${"%.3f".format(ramMs)}ms"
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Test fixture builders
    // ═════════════════════════════════════════════════════════════════════════

    private fun createTestEpub(target: File): File {
        val coverBmp = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 90, 180))
        }
        val coverBytes = ByteArrayOutputStream().also {
            coverBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            coverBmp.recycle()
        }.toByteArray()

        ZipOutputStream(FileOutputStream(target)).use { zos ->
            fun addText(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addText("mimetype", "application/epub+zip")
            addText(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent()
            )
            addText(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>El Niño y la Brújula</dc:title>
                    <dc:creator>Autora Española</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="ch1" href="nested/../text/cap%201.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cov" href="images/cover.png" media-type="image/png"/>
                  </manifest>
                  <spine>
                    <itemref idref="ch1"/>
                  </spine>
                </package>
                """.trimIndent()
            )
            addText(
                "OEBPS/text/cap 1.xhtml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Brújula</title></head>
                  <body>
                    <h1>Capítulo Uno</h1>
                    <p>La Brújula giraba. &iquest;Qui&eacute;n so&ntilde;&oacute; &#x2014; con caf&#233;? &laquo;Hola&raquo;</p>
                    <h4>Sección menor</h4>
                  </body>
                </html>
                """.trimIndent()
            )
            zos.putNextEntry(ZipEntry("OEBPS/images/cover.png"))
            zos.write(coverBytes)
            zos.closeEntry()
        }
        return target
    }

    private fun createTestDocx(target: File): File {
        ZipOutputStream(FileOutputStream(target)).use { zos ->
            fun addText(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addText(
                "docProps/core.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                                   xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>Guía de Cardiología</dc:title>
                  <dc:creator>Dra. Elena Ruiz</dc:creator>
                </cp:coreProperties>
                """.trimIndent()
            )
            addText(
                "word/document.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:pPr><w:pStyle w:val="Titulo1"/></w:pPr><w:r><w:t>Introducción Clínica</w:t></w:r></w:p>
                    <w:p>
                      <w:r><w:t>Inicio exterior </w:t></w:r>
                      <w:r><w:drawing><w:txbxContent><w:p><w:r><w:t>Recuadro de dosis urgente</w:t></w:r></w:p></w:txbxContent></w:drawing></w:r>
                      <w:r><w:t>continuación exterior intacta.</w:t></w:r>
                    </w:p>
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Tratamiento Avanzado</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Seguimiento ambulatorio.</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """.trimIndent()
            )
        }
        return target
    }

    private fun createTestPptx(target: File): File {
        ZipOutputStream(FileOutputStream(target)).use { zos ->
            fun addText(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addText(
                "docProps/core.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                                   xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>Fisiología Renal</dc:title>
                  <dc:creator>Dr. Marco Silva</dc:creator>
                </cp:coreProperties>
                """.trimIndent()
            )
            addText(
                "ppt/presentation.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst>
                  <p:sldSz cx="12192000" cy="6858000"/>
                </p:presentation>
                """.trimIndent()
            )
            addText(
                "ppt/_rels/presentation.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
                </Relationships>
                """.trimIndent()
            )
            addText(
                "ppt/slides/_rels/slide1.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide" Target="..\notesSlides\notesSlide1.xml"/>
                </Relationships>
                """.trimIndent()
            )
            addText(
                "ppt/slides/slide1.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                       xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                  <p:cSld>
                    <p:spTree>
                      <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="Title 1"/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="457200" y="274638"/><a:ext cx="11277600" cy="1143000"/></a:xfrm></p:spPr>
                        <p:txBody><a:p><a:r><a:t>Filtración Glomerular</a:t></a:r></a:p></p:txBody>
                      </p:sp>
                      <p:graphicFrame>
                        <p:xfrm><a:off x="457200" y="1800000"/><a:ext cx="8000000" cy="1200000"/></p:xfrm>
                        <a:graphic><a:graphicData>
                          <a:tbl>
                            <a:tr>
                              <a:tc><a:txBody><a:p><a:r><a:t>TFG</a:t></a:r></a:p></a:txBody></a:tc>
                              <a:tc><a:txBody><a:p><a:r><a:t>125 mL/min</a:t></a:r></a:p></a:txBody></a:tc>
                            </a:tr>
                          </a:tbl>
                        </a:graphicData></a:graphic>
                      </p:graphicFrame>
                    </p:spTree>
                  </p:cSld>
                </p:sld>
                """.trimIndent()
            )
            addText(
                "ppt/notesSlides/notesSlide1.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <p:notes xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                         xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                  <p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>Explicar presión oncótica en detalle.</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld>
                </p:notes>
                """.trimIndent()
            )
        }
        return target
    }

    private fun createTestPdf(target: File): File {
        PDDocument().use { doc ->
            doc.documentInformation.title = "Manual Clinico"
            doc.documentInformation.author = "Luma QA"

            val page1 = PDPage(PDRectangle.A4)
            doc.addPage(page1)
            PDPageContentStream(doc, page1).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(60f, 740f)
                cs.showText("Pagina uno contenido clinico importante.")
                cs.endText()
            }

            val page2 = PDPage(PDRectangle.A4)
            doc.addPage(page2)
            PDPageContentStream(doc, page2).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(60f, 740f)
                cs.showText("Pagina dos protocolo final de seguimiento.")
                cs.endText()
            }

            doc.save(target)
        }
        return target
    }
}
