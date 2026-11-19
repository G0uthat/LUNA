package com.example.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.local.DocumentEntity
import com.example.data.local.HighlightNoteEntity
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.ExportFormatOption
import com.example.data.model.OcrPageResult
import com.example.data.model.ParsedDocument
import com.example.data.model.StandardPageFormat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Exports any document (PDF, EPUB, DOCX, TXT, FB2) with user freehand drawings and highlights
 * MERGED directly into the exported file while keeping all normal text (and OCR text on image PDFs)
 * 100% selectable and searchable.
 */
object MergedDocumentExporter {

    suspend fun exportToUri(
        context: Context,
        document: DocumentEntity,
        parsed: ParsedDocument,
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        highlights: List<HighlightNoteEntity>,
        ocrByPage: Map<Int, OcrPageResult>,
        formatOption: ExportFormatOption,
        destinationUri: Uri,
        standardPageFormat: StandardPageFormat = StandardPageFormat.AUTO
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                exportToStream(
                    context = context,
                    document = document,
                    parsed = parsed,
                    drawingsByLocation = drawingsByLocation,
                    highlights = highlights,
                    ocrByPage = ocrByPage,
                    formatOption = formatOption,
                    standardPageFormat = standardPageFormat,
                    outputStream = out
                )
            } ?: error("No se pudo abrir el destino para guardar el archivo.")
        }
    }

    suspend fun exportToInternalCache(
        context: Context,
        document: DocumentEntity,
        parsed: ParsedDocument,
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        highlights: List<HighlightNoteEntity>,
        ocrByPage: Map<Int, OcrPageResult>,
        formatOption: ExportFormatOption,
        standardPageFormat: StandardPageFormat = StandardPageFormat.AUTO
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
            val ext = resolveOutputExtension(document, formatOption)
            val safeName = document.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
            val outFile = File(exportDir, "${safeName}_anotado.$ext")
            FileOutputStream(outFile).use { out ->
                exportToStream(
                    context = context,
                    document = document,
                    parsed = parsed,
                    drawingsByLocation = drawingsByLocation,
                    highlights = highlights,
                    ocrByPage = ocrByPage,
                    formatOption = formatOption,
                    standardPageFormat = standardPageFormat,
                    outputStream = out
                )
            }
            outFile
        }
    }

    fun resolveOutputExtension(document: DocumentEntity, option: ExportFormatOption): String {
        if (option == ExportFormatOption.UNIVERSAL_PDF) return "pdf"
        return when (DocumentFormat.fromExtension(document.format)) {
            DocumentFormat.EPUB -> "epub"
            DocumentFormat.DOCX -> "docx"
            DocumentFormat.PDF -> "pdf"
            else -> "pdf"
        }
    }

    fun resolveOutputMimeType(document: DocumentEntity, option: ExportFormatOption): String {
        return when (resolveOutputExtension(document, option)) {
            "epub" -> DocumentFormat.EPUB.mimeType
            "docx" -> DocumentFormat.DOCX.mimeType
            else -> DocumentFormat.PDF.mimeType
        }
    }

    private fun exportToStream(
        context: Context,
        document: DocumentEntity,
        parsed: ParsedDocument,
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        highlights: List<HighlightNoteEntity>,
        ocrByPage: Map<Int, OcrPageResult>,
        formatOption: ExportFormatOption,
        standardPageFormat: StandardPageFormat,
        outputStream: OutputStream
    ) {
        val docFormat = DocumentFormat.fromExtension(document.format) ?: DocumentFormat.PDF
        val sourceFile = File(document.filePath)

        when {
            docFormat == DocumentFormat.PDF -> {
                exportMergedPdfWithPdfBox(
                    context = context,
                    sourceFile = sourceFile,
                    drawingsByLocation = drawingsByLocation,
                    ocrByPage = ocrByPage,
                    outputStream = outputStream
                )
            }
            docFormat == DocumentFormat.PPTX -> {
                exportPptxAsSelectablePdf(
                    parsed = parsed,
                    drawingsByLocation = drawingsByLocation,
                    standardPageFormat = standardPageFormat,
                    outputStream = outputStream
                )
            }
            formatOption == ExportFormatOption.NATIVE_MERGED && docFormat == DocumentFormat.EPUB -> {
                exportMergedEpub(
                    sourceFile = sourceFile,
                    parsed = parsed,
                    drawingsByLocation = drawingsByLocation,
                    highlights = highlights,
                    outputStream = outputStream
                )
            }
            formatOption == ExportFormatOption.NATIVE_MERGED && docFormat == DocumentFormat.DOCX -> {
                exportMergedDocx(
                    sourceFile = sourceFile,
                    highlights = highlights,
                    drawingsByLocation = drawingsByLocation,
                    outputStream = outputStream
                )
            }
            else -> {
                exportFluidDocumentAsSelectablePdf(
                    parsed = parsed,
                    drawingsByLocation = drawingsByLocation,
                    highlights = highlights,
                    standardPageFormat = standardPageFormat,
                    outputStream = outputStream
                )
            }
        }
    }

    /**
     * Merges freehand drawings (vector paths + highlighter alpha) and invisible selectable OCR text
     * directly onto an existing PDF without flattening or breaking original selectable text!
     */
    private fun exportMergedPdfWithPdfBox(
        context: Context,
        sourceFile: File,
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        ocrByPage: Map<Int, OcrPageResult>,
        outputStream: OutputStream
    ) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(sourceFile).use { pdDoc ->
            val totalPages = pdDoc.numberOfPages
            for (pageIndex in 0 until totalPages) {
                val page = pdDoc.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height
                val strokes = drawingsByLocation["pdf-page-$pageIndex"].orEmpty()
                val ocrPage = ocrByPage[pageIndex]

                if (strokes.isEmpty() && (ocrPage == null || ocrPage.blocks.isEmpty())) {
                    continue
                }

                PDPageContentStream(
                    pdDoc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    // 1. Embed invisible selectable OCR text layer for image-based PDFs / Manhwas
                    if (ocrPage != null && ocrPage.blocks.isNotEmpty()) {
                        cs.saveGraphicsState()
                        cs.setRenderingMode(RenderingMode.NEITHER)
                        for (block in ocrPage.blocks) {
                            for (word in block.words) {
                                val safeWord = sanitizeForWinAnsi(word.text)
                                if (safeWord.isBlank()) continue
                                val boxW = (word.right - word.left) * pageWidth
                                val boxH = ((word.bottom - word.top) * pageHeight).coerceIn(6f, 48f)
                                val pdfX = mediaBox.lowerLeftX + word.left * pageWidth
                                val pdfY = mediaBox.lowerLeftY + (1f - word.bottom) * pageHeight
                                val fontSize = (boxH * 0.85f).coerceIn(6f, 36f)
                                val textWidth = runCatching {
                                    PDType1Font.HELVETICA.getStringWidth(safeWord) / 1000f * fontSize
                                }.getOrDefault(boxW)
                                val hScale = if (textWidth > 1f && boxW > 1f) {
                                    ((boxW / textWidth) * 100f).coerceIn(40f, 220f)
                                } else {
                                    100f
                                }
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, fontSize)
                                cs.setHorizontalScaling(hScale)
                                cs.newLineAtOffset(pdfX, pdfY)
                                cs.showText(safeWord)
                                cs.endText()
                            }
                        }
                        cs.restoreGraphicsState()
                    }

                    // 2. Append vector freehand strokes directly over the page
                    for (stroke in strokes) {
                        if (stroke.points.size < 2) continue
                        cs.saveGraphicsState()
                        val gs = PDExtendedGraphicsState().apply {
                            strokingAlphaConstant = stroke.alpha.coerceIn(0.08f, 1f)
                        }
                        cs.setGraphicsStateParameters(gs)

                        val argb = stroke.colorArgb.toInt()
                        val r = ((argb shr 16) and 0xFF)
                        val g = ((argb shr 8) and 0xFF)
                        val b = (argb and 0xFF)
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth((stroke.widthNorm * pageWidth).coerceIn(1f, 36f))
                        cs.setLineCapStyle(1) // Round cap
                        cs.setLineJoinStyle(1) // Round join

                        val first = stroke.points.first()
                        cs.moveTo(
                            mediaBox.lowerLeftX + first.x * pageWidth,
                            mediaBox.lowerLeftY + (1f - first.y) * pageHeight
                        )
                        for (i in 1 until stroke.points.size) {
                            val pt = stroke.points[i]
                            cs.lineTo(
                                mediaBox.lowerLeftX + pt.x * pageWidth,
                                mediaBox.lowerLeftY + (1f - pt.y) * pageHeight
                            )
                        }
                        cs.stroke()
                        cs.restoreGraphicsState()
                    }
                }
            }
            pdDoc.save(outputStream)
        }
    }

    /**
     * Exports a merged EPUB keeping all XHTML text 100% selectable while overlaying
     * vector SVG strokes (pointer-events: none) and <mark> highlights inside each annotated chapter.
     */
    private fun exportMergedEpub(
        sourceFile: File,
        parsed: ParsedDocument,
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        highlights: List<HighlightNoteEntity>,
        outputStream: OutputStream
    ) {
        val chaptersByHref = parsed.chapters.associateBy { it.href }
        val highlightsByChapter = highlights.groupBy { it.chapterIndex }

        ZipFile(sourceFile).use { zip ->
            ZipOutputStream(outputStream).use { zos ->
                // Write mimetype first uncompressed per EPUB specification
                zip.getEntry("mimetype")?.let { mimeEntry ->
                    val bytes = zip.getInputStream(mimeEntry).use { it.readBytes() }
                    val stored = ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    }
                    zos.putNextEntry(stored)
                    zos.write(bytes)
                    zos.closeEntry()
                }

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == "mimetype") continue

                    val chapter = chaptersByHref[entry.name]
                    if (chapter != null) {
                        var xhtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        val chapterHighlights = highlightsByChapter[chapter.index].orEmpty()
                        val strokes = collectChapterStrokes(drawingsByLocation, chapter.cfiPrefix)

                        // Inject non-destructive <mark> highlights preserving selectable text
                        for (hl in chapterHighlights) {
                            val quote = hl.quotedText.trim()
                            if (quote.length >= 2 && xhtml.contains(quote)) {
                                val hexColor = String.format(Locale.US, "#%06X", hl.colorArgb.toInt() and 0xFFFFFF)
                                val marked = "<mark style=\"background:${hexColor}66;border-radius:3px;\">$quote</mark>"
                                xhtml = xhtml.replaceFirst(quote, marked)
                            }
                        }

                        // Inject vector SVG drawing overlay with pointer-events: none so normal text stays selectable!
                        if (strokes.isNotEmpty()) {
                            val svgOverlay = buildSvgOverlay(strokes)
                            val bodyCloseIdx = xhtml.lastIndexOf("</body>", ignoreCase = true)
                            xhtml = if (bodyCloseIdx >= 0) {
                                xhtml.substring(0, bodyCloseIdx) + "\n$svgOverlay\n" + xhtml.substring(bodyCloseIdx)
                            } else {
                                xhtml + "\n$svgOverlay"
                            }
                        }

                        val bytes = xhtml.toByteArray(Charsets.UTF_8)
                        zos.putNextEntry(ZipEntry(entry.name))
                        zos.write(bytes)
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * Re-packages DOCX preserving 100% editable/selectable text in word/document.xml,
     * applying OOXML highlights and appending an annotation summary section.
     */
    private fun exportMergedDocx(
        sourceFile: File,
        highlights: List<HighlightNoteEntity>,
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        outputStream: OutputStream
    ) {
        ZipFile(sourceFile).use { zip ->
            ZipOutputStream(outputStream).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == "word/document.xml") {
                        var xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        val notesAndDrawingCount = drawingsByLocation.values.sumOf { it.size }
                        if (highlights.isNotEmpty() || notesAndDrawingCount > 0) {
                            val summaryXml = buildString {
                                append("<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr><w:r><w:t>Anotaciones y Dibujos Fusionados (Folio)</w:t></w:r></w:p>")
                                for (hl in highlights) {
                                    val safeQuote = escapeXml(hl.quotedText)
                                    val safeNote = escapeXml(hl.noteText)
                                    append("<w:p><w:r><w:rPr><w:highlight w:val=\"yellow\"/></w:rPr><w:t>\"$safeQuote\"</w:t></w:r>")
                                    if (safeNote.isNotBlank()) {
                                        append("<w:r><w:t> — Nota: $safeNote</w:t></w:r>")
                                    }
                                    append("</w:p>")
                                }
                                if (notesAndDrawingCount > 0) {
                                    append("<w:p><w:r><w:t>Incluye $notesAndDrawingCount trazos vectoriales guardados (exporta en PDF universal para visualizar los trazos vectoriales directamente sobre cada página).</w:t></w:r></w:p>")
                                }
                            }
                            val bodyClose = xml.lastIndexOf("</w:body>")
                            if (bodyClose >= 0) {
                                xml = xml.substring(0, bodyClose) + summaryXml + xml.substring(bodyClose)
                            }
                        }
                        zos.putNextEntry(ZipEntry(entry.name))
                        zos.write(xml.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * Exports PowerPoint (.pptx) presentations as a high-clarity selectable Vector PDF matching the
     * authentic slide aspect ratio (e.g. 16:9, 4:3, or selected StandardPageFormat) with embedded
     * slide images and all user freehand strokes / signatures merged cleanly on top.
     */
    private fun exportPptxAsSelectablePdf(
        parsed: ParsedDocument,
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        standardPageFormat: StandardPageFormat,
        outputStream: OutputStream
    ) {
        val pdfDoc = PdfDocument()
        val (wPt, hPt) = standardPageFormat.resolveSizePt(parsed.nativePageWidthPt, parsed.nativePageHeightPt)
        val pageWidth = wPt.toInt().coerceIn(320, 2400)
        val pageHeight = hPt.toInt().coerceIn(240, 2400)
        val marginX = (pageWidth * 0.065f).coerceIn(28f, 64f)
        val marginY = (pageHeight * 0.075f).coerceIn(24f, 56f)

        val bgPaint = Paint().apply { color = Color.WHITE }
        val accentBarPaint = Paint().apply { color = Color.parseColor("#1D5BFF") }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = (pageHeight * 0.022f).coerceIn(9f, 13f)
        }

        parsed.slides.forEachIndexed { idx, slide ->
            val page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create())
            val canvas = page.canvas
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f, accentBarPaint)

            val hasImages = slide.imagePaths.isNotEmpty()
            val textAreaWidth = if (hasImages && pageWidth > pageHeight) {
                ((pageWidth - marginX * 2.5f) * 0.56f).toInt().coerceAtLeast(180)
            } else {
                (pageWidth - marginX * 2f).toInt().coerceAtLeast(180)
            }

            var y = marginY
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#101828")
                textSize = (pageHeight * 0.052f).coerceIn(18f, 30f)
                isFakeBoldText = true
            }
            val titleLayout = StaticLayout.Builder
                .obtain(slide.title, 0, slide.title.length, titlePaint, (pageWidth - marginX * 2f).toInt().coerceAtLeast(180))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .build()
            canvas.save()
            canvas.translate(marginX, y)
            titleLayout.draw(canvas)
            canvas.restore()
            y += titleLayout.height + 10f

            if (slide.subtitle.isNotBlank()) {
                val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#475467")
                    textSize = (pageHeight * 0.032f).coerceIn(12f, 18f)
                }
                val subLayout = StaticLayout.Builder
                    .obtain(slide.subtitle, 0, slide.subtitle.length, subPaint, textAreaWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.2f)
                    .build()
                canvas.save()
                canvas.translate(marginX, y)
                subLayout.draw(canvas)
                canvas.restore()
                y += subLayout.height + 12f
            }

            val bodyTopY = y
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1D2939")
                textSize = (pageHeight * 0.028f).coerceIn(11f, 16f)
            }
            for (block in slide.bodyBlocks) {
                val prefix = if (block.type == BlockType.LIST_ITEM) "•  " else ""
                val text = prefix + block.text
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, bodyPaint, textAreaWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.24f)
                    .build()
                if (y + layout.height > pageHeight - marginY - 18f) break
                canvas.save()
                canvas.translate(marginX, y)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + 8f
            }

            // Draw first embedded slide image neatly inside available space
            if (hasImages) {
                val imgPath = slide.imagePaths.first()
                runCatching {
                    val bmp = BitmapFactory.decodeFile(imgPath)
                    if (bmp != null) {
                        val destRect = if (pageWidth > pageHeight) {
                            val left = marginX + textAreaWidth + marginX * 0.5f
                            val right = pageWidth - marginX
                            val top = bodyTopY
                            val bottom = pageHeight - marginY - 22f
                            fitRect(bmp.width.toFloat(), bmp.height.toFloat(), RectF(left, top, right, bottom))
                        } else {
                            val top = (y + 8f).coerceAtMost(pageHeight * 0.55f)
                            val bottom = pageHeight - marginY - 22f
                            fitRect(bmp.width.toFloat(), bmp.height.toFloat(), RectF(marginX, top, pageWidth - marginX, bottom))
                        }
                        canvas.drawBitmap(bmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                        bmp.recycle()
                    }
                }
            }

            canvas.drawText(
                "${idx + 1} / ${parsed.slides.size}",
                pageWidth - marginX - 42f,
                pageHeight - 14f,
                footerPaint
            )

            val slideStrokes = drawingsByLocation["pptx-slide-$idx"].orEmpty() +
                drawingsByLocation["std-page-$idx"].orEmpty() +
                collectChapterStrokes(drawingsByLocation, "pptx(/slide/$idx)")
            if (slideStrokes.isNotEmpty()) {
                drawStrokesOnAndroidCanvas(canvas, slideStrokes, pageWidth.toFloat(), pageHeight.toFloat())
            }
            pdfDoc.finishPage(page)
        }

        pdfDoc.writeTo(outputStream)
        pdfDoc.close()
    }

    private fun fitRect(srcW: Float, srcH: Float, bounds: RectF): RectF {
        val bw = bounds.width().coerceAtLeast(1f)
        val bh = bounds.height().coerceAtLeast(1f)
        val scale = minOf(bw / srcW.coerceAtLeast(1f), bh / srcH.coerceAtLeast(1f))
        val dw = srcW * scale
        val dh = srcH * scale
        val left = bounds.left + (bw - dw) * 0.5f
        val top = bounds.top + (bh - dh) * 0.5f
        return RectF(left, top, left + dw, top + dh)
    }

    /**
     * Exports fluid documents (EPUB, DOCX, TXT, FB2) as a clean Vector PDF matching the selected
     * physical StandardPageFormat (A4, Carta, A3, Oficio, A5, or Auto file size), with all text
     * selectable via StaticLayout and all freehand drawings / signatures merged cleanly on top!
     */
    private fun exportFluidDocumentAsSelectablePdf(
        parsed: ParsedDocument,
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        highlights: List<HighlightNoteEntity>,
        standardPageFormat: StandardPageFormat,
        outputStream: OutputStream
    ) {
        val pdfDoc = PdfDocument()
        val (wPt, hPt) = standardPageFormat.resolveSizePt(parsed.nativePageWidthPt, parsed.nativePageHeightPt)
        val pageWidth = wPt.toInt().coerceIn(320, 2400)
        val pageHeight = hPt.toInt().coerceIn(320, 2400)
        val marginX = (pageWidth * 0.08f).coerceIn(32f, 68f)
        val marginY = (pageHeight * 0.07f).coerceIn(36f, 68f)
        val contentWidth = (pageWidth - marginX * 2).toInt().coerceAtLeast(180)
        val highlightsByChapter = highlights.groupBy { it.chapterIndex }

        var pageNumber = 1

        for (chapter in parsed.chapters) {
            val chapterStrokes = collectChapterStrokes(drawingsByLocation, chapter.cfiPrefix)
            val chapterHighlights = highlightsByChapter[chapter.index].orEmpty()

            var page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = marginY
            var strokesDrawnOnChapter = false

            fun finishCurrentPage(drawChapterStrokes: Boolean) {
                val stdPageStrokes = drawingsByLocation["std-page-${pageNumber - 1}"].orEmpty()
                val combined = buildList {
                    if (drawChapterStrokes) addAll(chapterStrokes)
                    addAll(stdPageStrokes)
                }
                if (combined.isNotEmpty()) {
                    drawStrokesOnAndroidCanvas(canvas, combined, pageWidth.toFloat(), pageHeight.toFloat())
                }
                pdfDoc.finishPage(page)
            }

            for (block in chapter.blocks) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#151C2B")
                    textSize = when (block.type) {
                        BlockType.HEADING_1 -> 20f
                        BlockType.HEADING_2 -> 16f
                        BlockType.QUOTE -> 13.5f
                        else -> 13f
                    }
                    isFakeBoldText = block.type == BlockType.HEADING_1 || block.type == BlockType.HEADING_2
                }

                val layout = StaticLayout.Builder
                    .obtain(block.text, 0, block.text.length, paint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(4f, 1.25f)
                    .build()

                if (y + layout.height > pageHeight - marginY) {
                    finishCurrentPage(!strokesDrawnOnChapter)
                    strokesDrawnOnChapter = true
                    pageNumber++
                    page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = marginY
                }

                // Soft highlight tint behind block if highlighted
                val matchingHl = chapterHighlights.firstOrNull { it.blockIndex == block.index }
                if (matchingHl != null) {
                    val hlPaint = Paint().apply {
                        color = matchingHl.colorArgb.toInt()
                        alpha = 65
                    }
                    canvas.drawRect(marginX - 4f, y - 2f, marginX + contentWidth + 4f, y + layout.height + 2f, hlPaint)
                }

                canvas.save()
                canvas.translate(marginX, y)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + if (block.type == BlockType.HEADING_1) 18f else 12f
            }

            finishCurrentPage(!strokesDrawnOnChapter)
            pageNumber++
        }

        pdfDoc.writeTo(outputStream)
        pdfDoc.close()
    }

    private fun drawStrokesOnAndroidCanvas(
        canvas: Canvas,
        strokes: List<DrawingStroke>,
        width: Float,
        height: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            paint.color = stroke.colorArgb.toInt()
            paint.alpha = (stroke.alpha * 255).toInt().coerceIn(20, 255)
            paint.strokeWidth = (stroke.widthNorm * width).coerceIn(1.5f, 34f)
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x * width, first.y * height)
            for (i in 1 until stroke.points.size) {
                val prev = stroke.points[i - 1]
                val curr = stroke.points[i]
                path.quadTo(
                    prev.x * width,
                    prev.y * height,
                    ((prev.x + curr.x) * 0.5f) * width,
                    ((prev.y + curr.y) * 0.5f) * height
                )
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun buildSvgOverlay(strokes: List<DrawingStroke>): String {
        val paths = strokes.mapNotNull { stroke ->
            if (stroke.points.size < 2) return@mapNotNull null
            val hex = String.format(Locale.US, "#%06X", stroke.colorArgb.toInt() and 0xFFFFFF)
            val strokeWidth = String.format(Locale.US, "%.2f", (stroke.widthNorm * 1000f).coerceIn(2f, 40f))
            val opacity = String.format(Locale.US, "%.2f", stroke.alpha.coerceIn(0.1f, 1f))
            val d = buildString {
                val first = stroke.points.first()
                append(String.format(Locale.US, "M %.1f %.1f", first.x * 1000f, first.y * 1500f))
                for (i in 1 until stroke.points.size) {
                    val pt = stroke.points[i]
                    append(String.format(Locale.US, " L %.1f %.1f", pt.x * 1000f, pt.y * 1500f))
                }
            }
            """<path d="$d" fill="none" stroke="$hex" stroke-width="$strokeWidth" stroke-opacity="$opacity" stroke-linecap="round" stroke-linejoin="round"/>"""
        }.joinToString("\n  ")

        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1500" preserveAspectRatio="none" style="position:fixed;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:9999;">
  $paths
</svg>"""
    }

    private fun sanitizeForWinAnsi(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch.code in 32..126 || ch.code in 160..255 -> sb.append(ch)
                ch == '“' || ch == '”' -> sb.append('"')
                ch == '‘' || ch == '’' -> sb.append('\'')
                ch == '—' || ch == '–' -> sb.append('-')
                ch == '…' -> sb.append("...")
                else -> sb.append(' ')
            }
        }
        return sb.toString().trim()
    }

    private fun collectChapterStrokes(
        drawingsByLocation: Map<String, List<DrawingStroke>>,
        cfiPrefix: String
    ): List<DrawingStroke> {
        val exact = drawingsByLocation[cfiPrefix].orEmpty()
        val sheetPrefix = "$cfiPrefix#"
        val sheetStrokes = drawingsByLocation.entries
            .filter { it.key.startsWith(sheetPrefix) }
            .sortedBy { it.key }
            .flatMap { it.value }
        return exact + sheetStrokes
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
