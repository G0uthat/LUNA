package com.example.engine

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.model.BlockType
import com.example.data.model.DocumentFormat
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedChapter
import com.example.data.model.ParsedDocument
import com.example.data.model.ParsedSlide
import com.example.data.model.TocEntry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

object DocumentParser {

    suspend fun parse(context: Context, file: File, format: DocumentFormat): ParsedDocument =
        withContext(Dispatchers.IO) {
            when (format) {
                DocumentFormat.EPUB -> parseEpub(file)
                DocumentFormat.DOCX -> parseDocx(file)
                DocumentFormat.PPTX -> parsePptx(context, file)
                DocumentFormat.PDF -> parsePdf(context, file)
                DocumentFormat.TXT -> parseTxt(file)
                DocumentFormat.FB2 -> parseFb2(file)
            }
        }

    private fun parseEpub(file: File): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        ZipFile(file).use { zip ->
            val containerXml = zip.getEntry("META-INF/container.xml")
                ?.let { zip.getInputStream(it).bufferedReader().use { r -> r.readText() } }
            val opfPath = containerXml?.let { extractOpfPath(it) }
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", true) }?.name

            val opfBaseDir = opfPath?.substringBeforeLast('/', "")?.let { if (it.isEmpty()) "" else "$it/" } ?: ""
            val opfXml = opfPath?.let { zip.getEntry(it) }
                ?.let { zip.getInputStream(it).bufferedReader().use { r -> r.readText() } }

            var title = fallbackTitle
            var author = "Autor desconocido"
            val manifest = mutableMapOf<String, String>()
            val spineIds = mutableListOf<String>()

            if (opfXml != null) {
                parseOpf(opfXml, onTitle = { if (it.isNotBlank()) title = it },
                    onAuthor = { if (it.isNotBlank()) author = it },
                    manifest = manifest, spineIds = spineIds)
            }

            val orderedPaths = if (spineIds.isNotEmpty()) {
                spineIds.mapNotNull { id -> manifest[id]?.let { resolveZipPath(opfBaseDir, it) } }
            } else {
                zip.entries().asSequence()
                    .filter {
                        val n = it.name.lowercase()
                        (n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")) &&
                            !n.contains("nav.") && !n.contains("toc.")
                    }
                    .map { it.name }
                    .sorted()
                    .toList()
            }

            val chapters = mutableListOf<ParsedChapter>()
            val toc = mutableListOf<TocEntry>()
            var totalWords = 0

            orderedPaths.forEachIndexed { idx, path ->
                val entry = zip.getEntry(path) ?: zip.getEntry(path.removePrefix("/"))
                if (entry != null) {
                    val xhtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val blocks = extractBlocksFromXhtml(xhtml)
                    if (blocks.isNotEmpty()) {
                        val chapterTitle = blocks.firstOrNull {
                            it.type == BlockType.HEADING_1 || it.type == BlockType.HEADING_2
                        }?.text?.take(70) ?: "Capítulo ${chapters.size + 1}"
                        val words = blocks.sumOf { countWords(it.text) }
                        totalWords += words
                        val chapterIdx = chapters.size
                        chapters.add(
                            ParsedChapter(
                                index = chapterIdx,
                                title = chapterTitle,
                                cfiPrefix = "epubcfi(/6/${(chapterIdx + 1) * 2}!)",
                                href = path,
                                blocks = blocks,
                                wordCount = words
                            )
                        )
                        toc.add(TocEntry(title = chapterTitle, targetIndex = chapterIdx, level = 0))
                    }
                }
            }

            if (chapters.isEmpty()) {
                val fallbackBlock = ParsedBlock(0, BlockType.PARAGRAPH, "No se encontró contenido legible en este archivo EPUB.")
                chapters.add(ParsedChapter(0, title, "epubcfi(/6/2!)", "", listOf(fallbackBlock), 8))
                toc.add(TocEntry(title, 0, 0))
            }

            return ParsedDocument(
                title = title,
                author = author,
                format = DocumentFormat.EPUB,
                chapters = chapters,
                toc = toc,
                totalWords = totalWords
            )
        }
    }

    private fun parseDocx(file: File): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        val allBlocks = mutableListOf<ParsedBlock>()
        var docTitle = fallbackTitle
        var docAuthor = "Documento DOCX"
        var nativeWidthPt = 595f
        var nativeHeightPt = 842f

        ZipFile(file).use { zip ->
            zip.getEntry("docProps/core.xml")?.let { coreEntry ->
                val coreXml = zip.getInputStream(coreEntry).bufferedReader().use { it.readText() }
                extractTagText(coreXml, "title")?.takeIf { it.isNotBlank() }?.let { docTitle = it }
                extractTagText(coreXml, "creator")?.takeIf { it.isNotBlank() }?.let { docAuthor = it }
            }

            val docXmlEntry = zip.getEntry("word/document.xml")
            if (docXmlEntry != null) {
                val xml = zip.getInputStream(docXmlEntry).bufferedReader().use { it.readText() }
                val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                val parser = factory.newPullParser()
                parser.setInput(StringReader(xml))

                var inParagraph = false
                var currentStyle = ""
                val paraBuilder = StringBuilder()

                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name.substringAfter(':')
                            when (tag) {
                                "pgSz" -> {
                                    var wTwips: Float? = null
                                    var hTwips: Float? = null
                                    var orient = ""
                                    for (i in 0 until parser.attributeCount) {
                                        val attr = parser.getAttributeName(i).substringAfter(':')
                                        val v = parser.getAttributeValue(i)
                                        when (attr) {
                                            "w" -> wTwips = v.toFloatOrNull()
                                            "h" -> hTwips = v.toFloatOrNull()
                                            "orient" -> orient = v
                                        }
                                    }
                                    if (wTwips != null && hTwips != null && wTwips > 0f && hTwips > 0f) {
                                        var wPt = wTwips / 20f
                                        var hPt = hTwips / 20f
                                        if (orient.equals("landscape", ignoreCase = true) && wPt < hPt) {
                                            val tmp = wPt
                                            wPt = hPt
                                            hPt = tmp
                                        }
                                        nativeWidthPt = wPt.coerceIn(240f, 2400f)
                                        nativeHeightPt = hPt.coerceIn(240f, 2400f)
                                    }
                                }
                                "p" -> {
                                    inParagraph = true
                                    currentStyle = ""
                                    paraBuilder.clear()
                                }
                                "pStyle" -> {
                                    for (i in 0 until parser.attributeCount) {
                                        if (parser.getAttributeName(i).endsWith("val")) {
                                            currentStyle = parser.getAttributeValue(i)
                                        }
                                    }
                                }
                                "br" -> if (inParagraph) paraBuilder.append('\n')
                                "tab" -> if (inParagraph) paraBuilder.append("  ")
                                "t" -> {
                                    if (inParagraph) {
                                        paraBuilder.append(parser.nextText())
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val tag = parser.name.substringAfter(':')
                            if (tag == "p" && inParagraph) {
                                val text = paraBuilder.toString().trim()
                                if (text.isNotEmpty()) {
                                    val blockType = when {
                                        currentStyle.contains("Heading1", true) ||
                                            currentStyle.contains("Title", true) ||
                                            currentStyle.equals("1", true) -> BlockType.HEADING_1
                                        currentStyle.contains("Heading2", true) ||
                                            currentStyle.contains("Heading3", true) ||
                                            currentStyle.contains("Subtitle", true) -> BlockType.HEADING_2
                                        currentStyle.contains("Quote", true) -> BlockType.QUOTE
                                        currentStyle.contains("List", true) -> BlockType.LIST_ITEM
                                        else -> BlockType.PARAGRAPH
                                    }
                                    allBlocks.add(ParsedBlock(allBlocks.size, blockType, text))
                                }
                                inParagraph = false
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }

        // Split DOCX into chapters at HEADING_1 boundaries for clean navigation & TOC
        val chapters = mutableListOf<ParsedChapter>()
        val toc = mutableListOf<TocEntry>()
        var currentTitle = docTitle
        var currentBlocks = mutableListOf<ParsedBlock>()

        fun flushChapter() {
            if (currentBlocks.isEmpty()) return
            val idx = chapters.size
            val reindexed = currentBlocks.mapIndexed { i, b -> b.copy(index = i) }
            val words = reindexed.sumOf { countWords(it.text) }
            chapters.add(
                ParsedChapter(
                    index = idx,
                    title = currentTitle,
                    cfiPrefix = "docx(/section/$idx)",
                    href = "section_$idx",
                    blocks = reindexed,
                    wordCount = words
                )
            )
            toc.add(TocEntry(currentTitle, idx, 0))
            currentBlocks = mutableListOf()
        }

        for (block in allBlocks) {
            if (block.type == BlockType.HEADING_1 && currentBlocks.isNotEmpty()) {
                flushChapter()
                currentTitle = block.text.take(70)
            } else if (block.type == BlockType.HEADING_1 && currentBlocks.isEmpty()) {
                currentTitle = block.text.take(70)
            }
            currentBlocks.add(block)
        }
        flushChapter()

        if (chapters.isEmpty()) {
            val emptyBlock = ParsedBlock(0, BlockType.PARAGRAPH, "El documento DOCX no contiene texto visible.")
            chapters.add(ParsedChapter(0, docTitle, "docx(/section/0)", "section_0", listOf(emptyBlock), 7))
            toc.add(TocEntry(docTitle, 0, 0))
        }

        return ParsedDocument(
            title = docTitle,
            author = docAuthor,
            format = DocumentFormat.DOCX,
            chapters = chapters,
            toc = toc,
            totalWords = chapters.sumOf { it.wordCount },
            nativePageWidthPt = nativeWidthPt,
            nativePageHeightPt = nativeHeightPt,
            detectedFormatLabel = detectPageFormatLabel(nativeWidthPt, nativeHeightPt)
        )
    }

    private fun parsePptx(context: Context, file: File): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        var docTitle = fallbackTitle
        var docAuthor = "Presentación PowerPoint"
        var slideWidthPt = 960f
        var slideHeightPt = 540f

        val slides = mutableListOf<ParsedSlide>()
        val chapters = mutableListOf<ParsedChapter>()
        val toc = mutableListOf<TocEntry>()
        val slideRegex = Regex("^ppt/slides/slide(\\d+)\\.xml$", RegexOption.IGNORE_CASE)
        val mediaCacheDir = File(
            context.cacheDir,
            "pptx_media/${kotlin.math.abs(file.absolutePath.hashCode())}_${file.lastModified()}"
        ).apply { mkdirs() }

        ZipFile(file).use { zip ->
            zip.getEntry("docProps/core.xml")?.let { coreEntry ->
                val coreXml = zip.getInputStream(coreEntry).bufferedReader().use { it.readText() }
                extractTagText(coreXml, "title")?.takeIf { it.isNotBlank() }?.let { docTitle = it }
                extractTagText(coreXml, "creator")?.takeIf { it.isNotBlank() }?.let { docAuthor = it }
            }

            // Read authentic PowerPoint slide dimensions (cx/cy in EMUs: 12700 EMU = 1 pt)
            zip.getEntry("ppt/presentation.xml")?.let { presEntry ->
                val presXml = zip.getInputStream(presEntry).bufferedReader().use { it.readText() }
                runCatching {
                    val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                    val parser = factory.newPullParser()
                    parser.setInput(StringReader(presXml))
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "sldSz") {
                            var cx: Long? = null
                            var cy: Long? = null
                            for (i in 0 until parser.attributeCount) {
                                when (parser.getAttributeName(i).substringAfter(':')) {
                                    "cx" -> cx = parser.getAttributeValue(i).toLongOrNull()
                                    "cy" -> cy = parser.getAttributeValue(i).toLongOrNull()
                                }
                            }
                            if (cx != null && cy != null && cx > 0L && cy > 0L) {
                                slideWidthPt = (cx.toFloat() / 12700f).coerceIn(320f, 2400f)
                                slideHeightPt = (cy.toFloat() / 12700f).coerceIn(240f, 2400f)
                            }
                            break
                        }
                        event = parser.next()
                    }
                }
            }

            val orderedSlideEntries = zip.entries().asSequence()
                .mapNotNull { entry ->
                    val m = slideRegex.matchEntire(entry.name) ?: return@mapNotNull null
                    val num = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    num to entry
                }
                .sortedBy { it.first }
                .toList()

            orderedSlideEntries.forEachIndexed { slideIdx, (slideNum, slideEntry) ->
                val slideXml = zip.getInputStream(slideEntry).bufferedReader().use { it.readText() }

                // Parse slide relationships to locate embedded media and speaker notes
                val relsPath = "ppt/slides/_rels/slide$slideNum.xml.rels"
                val relTargets = mutableMapOf<String, Pair<String, String>>() // rId -> (type, target)
                zip.getEntry(relsPath)?.let { relsEntry ->
                    val relsXml = zip.getInputStream(relsEntry).bufferedReader().use { it.readText() }
                    runCatching {
                        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                        val rp = factory.newPullParser()
                        rp.setInput(StringReader(relsXml))
                        var ev = rp.eventType
                        while (ev != XmlPullParser.END_DOCUMENT) {
                            if (ev == XmlPullParser.START_TAG && rp.name.substringAfter(':') == "Relationship") {
                                val id = rp.getAttributeValue(null, "Id").orEmpty()
                                val type = rp.getAttributeValue(null, "Type").orEmpty()
                                val target = rp.getAttributeValue(null, "Target").orEmpty()
                                if (id.isNotBlank() && target.isNotBlank()) {
                                    relTargets[id] = type to target
                                }
                            }
                            ev = rp.next()
                        }
                    }
                }

                val extracted = extractPptxSlideContent(slideXml)
                val imagePaths = mutableListOf<String>()
                for (embedId in extracted.imageRelIds) {
                    val target = relTargets[embedId]?.second ?: continue
                    val resolvedZipPath = resolveZipPath("ppt/slides/", target)
                    val mediaEntry = zip.getEntry(resolvedZipPath) ?: continue
                    val ext = resolvedZipPath.substringAfterLast('.', "png").lowercase()
                    if (ext !in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")) continue
                    val cachedImg = File(mediaCacheDir, "s${slideNum}_${embedId}.$ext")
                    if (!cachedImg.exists() || cachedImg.length() == 0L) {
                        runCatching {
                            zip.getInputStream(mediaEntry).use { inp ->
                                cachedImg.outputStream().use { out -> inp.copyTo(out) }
                            }
                        }
                    }
                    if (cachedImg.exists() && cachedImg.length() > 0L) {
                        imagePaths.add(cachedImg.absolutePath)
                    }
                }

                // Extract speaker notes if linked
                val notesTarget = relTargets.values
                    .firstOrNull { it.first.contains("notesSlide", ignoreCase = true) }
                    ?.second
                val notesText = notesTarget?.let { target ->
                    val notesZipPath = resolveZipPath("ppt/slides/", target)
                    zip.getEntry(notesZipPath)?.let { ne ->
                        val notesXml = zip.getInputStream(ne).bufferedReader().use { it.readText() }
                        extractPlainDrawingMlText(notesXml)
                    }
                }.orEmpty()

                val slideTitle = extracted.title.ifBlank { "Diapositiva ${slideIdx + 1}" }
                val bodyBlocks = extracted.bodyBlocks.ifEmpty {
                    if (imagePaths.isEmpty() && extracted.subtitle.isBlank()) {
                        listOf(ParsedBlock(0, BlockType.PARAGRAPH, "Diapositiva ${slideIdx + 1}"))
                    } else {
                        emptyList()
                    }
                }

                val parsedSlide = ParsedSlide(
                    index = slideIdx,
                    title = slideTitle,
                    subtitle = extracted.subtitle,
                    bodyBlocks = bodyBlocks,
                    notes = notesText,
                    imagePaths = imagePaths
                )
                slides.add(parsedSlide)

                val chapterBlocks = buildList {
                    var bIdx = 0
                    add(ParsedBlock(bIdx++, BlockType.HEADING_1, slideTitle))
                    if (extracted.subtitle.isNotBlank()) {
                        add(ParsedBlock(bIdx++, BlockType.HEADING_2, extracted.subtitle))
                    }
                    for (b in bodyBlocks) {
                        add(b.copy(index = bIdx++))
                    }
                    if (notesText.isNotBlank()) {
                        add(ParsedBlock(bIdx++, BlockType.QUOTE, "Notas: $notesText"))
                    }
                }
                val words = chapterBlocks.sumOf { countWords(it.text) }
                chapters.add(
                    ParsedChapter(
                        index = slideIdx,
                        title = "${slideIdx + 1}. ${slideTitle.take(54)}",
                        cfiPrefix = "pptx(/slide/$slideIdx)",
                        href = "slide_${slideIdx + 1}",
                        blocks = chapterBlocks,
                        wordCount = words
                    )
                )
                toc.add(TocEntry(title = "${slideIdx + 1}. ${slideTitle.take(56)}", targetIndex = slideIdx, level = 0))
            }
        }

        if (slides.isEmpty()) {
            val fallbackSlide = ParsedSlide(
                index = 0,
                title = docTitle,
                bodyBlocks = listOf(ParsedBlock(0, BlockType.PARAGRAPH, "No se encontraron diapositivas visibles en este archivo PPTX."))
            )
            slides.add(fallbackSlide)
            chapters.add(
                ParsedChapter(
                    index = 0,
                    title = docTitle,
                    cfiPrefix = "pptx(/slide/0)",
                    href = "slide_1",
                    blocks = fallbackSlide.bodyBlocks,
                    wordCount = 9
                )
            )
            toc.add(TocEntry(docTitle, 0, 0))
        }

        return ParsedDocument(
            title = docTitle,
            author = docAuthor,
            format = DocumentFormat.PPTX,
            chapters = chapters,
            toc = toc,
            totalWords = chapters.sumOf { it.wordCount }.coerceAtLeast(slides.size * 25),
            slides = slides,
            nativePageWidthPt = slideWidthPt,
            nativePageHeightPt = slideHeightPt,
            detectedFormatLabel = detectPageFormatLabel(slideWidthPt, slideHeightPt, isPresentation = true)
        )
    }

    private data class ExtractedPptxSlide(
        val title: String,
        val subtitle: String,
        val bodyBlocks: List<ParsedBlock>,
        val imageRelIds: List<String>
    )

    private fun extractPptxSlideContent(slideXml: String): ExtractedPptxSlide {
        var title = ""
        var subtitle = ""
        val bodyBlocks = mutableListOf<ParsedBlock>()
        val imageRelIds = mutableListOf<String>()

        runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(slideXml))

            var inShape = false
            var currentPhType = ""
            var inParagraph = false
            var isBulletParagraph = false
            val paraBuilder = StringBuilder()

            var inTableRow = false
            var inTableCell = false
            val currentRowCells = mutableListOf<String>()
            val currentCellBuilder = StringBuilder()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.substringAfter(':')
                        when (tag) {
                            "sp" -> {
                                inShape = true
                                currentPhType = ""
                            }
                            "ph" -> {
                                if (inShape) {
                                    for (i in 0 until parser.attributeCount) {
                                        if (parser.getAttributeName(i).substringAfter(':') == "type") {
                                            currentPhType = parser.getAttributeValue(i)
                                        }
                                    }
                                }
                            }
                            "blip" -> {
                                for (i in 0 until parser.attributeCount) {
                                    val attr = parser.getAttributeName(i).substringAfter(':')
                                    if (attr == "embed" || attr == "link") {
                                        val rId = parser.getAttributeValue(i)
                                        if (rId.isNotBlank() && rId !in imageRelIds) {
                                            imageRelIds.add(rId)
                                        }
                                    }
                                }
                            }
                            "tr" -> {
                                inTableRow = true
                                currentRowCells.clear()
                            }
                            "tc" -> {
                                if (inTableRow) {
                                    inTableCell = true
                                    currentCellBuilder.clear()
                                }
                            }
                            "p" -> {
                                if (inShape && !inTableCell) {
                                    inParagraph = true
                                    isBulletParagraph = false
                                    paraBuilder.clear()
                                }
                            }
                            "pPr" -> {
                                if (inParagraph) {
                                    val lvl = (0 until parser.attributeCount)
                                        .firstOrNull { parser.getAttributeName(it).substringAfter(':') == "lvl" }
                                        ?.let { parser.getAttributeValue(it).toIntOrNull() } ?: 0
                                    if (lvl > 0) isBulletParagraph = true
                                }
                            }
                            "buChar", "buAutoNum" -> {
                                if (inParagraph) isBulletParagraph = true
                            }
                            "br" -> {
                                if (inParagraph) paraBuilder.append('\n')
                                if (inTableCell) currentCellBuilder.append(' ')
                            }
                            "t" -> {
                                val txt = parser.nextText()
                                if (inTableCell) {
                                    currentCellBuilder.append(txt)
                                } else if (inParagraph) {
                                    paraBuilder.append(txt)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.substringAfter(':')
                        when (tag) {
                            "p" -> {
                                if (inParagraph && inShape && !inTableCell) {
                                    val text = paraBuilder.toString().trim()
                                    if (text.isNotEmpty()) {
                                        val isTitlePh = currentPhType.equals("title", true) ||
                                            currentPhType.equals("ctrTitle", true)
                                        val isSubPh = currentPhType.equals("subTitle", true)
                                        val isFooterPh = currentPhType.equals("sldNum", true) ||
                                            currentPhType.equals("dt", true) ||
                                            currentPhType.equals("ftr", true)

                                        when {
                                            isFooterPh -> Unit
                                            isTitlePh && title.isBlank() -> title = text
                                            isTitlePh -> title = "$title $text"
                                            isSubPh && subtitle.isBlank() -> subtitle = text
                                            isSubPh -> subtitle = "$subtitle\n$text"
                                            title.isBlank() && bodyBlocks.isEmpty() && text.length <= 90 -> title = text
                                            else -> {
                                                val bType = if (isBulletParagraph || currentPhType.equals("body", true)) {
                                                    BlockType.LIST_ITEM
                                                } else {
                                                    BlockType.PARAGRAPH
                                                }
                                                bodyBlocks.add(ParsedBlock(bodyBlocks.size, bType, text))
                                            }
                                        }
                                    }
                                    inParagraph = false
                                }
                            }
                            "sp" -> {
                                inShape = false
                                currentPhType = ""
                            }
                            "tc" -> {
                                if (inTableCell) {
                                    val cell = currentCellBuilder.toString().trim()
                                    if (cell.isNotEmpty()) currentRowCells.add(cell)
                                    inTableCell = false
                                }
                            }
                            "tr" -> {
                                if (inTableRow) {
                                    if (currentRowCells.isNotEmpty()) {
                                        bodyBlocks.add(
                                            ParsedBlock(
                                                bodyBlocks.size,
                                                BlockType.PARAGRAPH,
                                                currentRowCells.joinToString("  ·  ")
                                            )
                                        )
                                    }
                                    inTableRow = false
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        return ExtractedPptxSlide(
            title = title.trim(),
            subtitle = subtitle.trim(),
            bodyBlocks = bodyBlocks,
            imageRelIds = imageRelIds
        )
    }

    private fun extractPlainDrawingMlText(xml: String): String {
        val sb = StringBuilder()
        runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val tag = parser.name.substringAfter(':')
                    if (tag == "t") {
                        val txt = parser.nextText().trim()
                        if (txt.isNotEmpty()) {
                            if (sb.isNotEmpty()) sb.append(' ')
                            sb.append(txt)
                        }
                    }
                }
                event = parser.next()
            }
        }
        return sb.toString().trim()
    }

    private fun detectPageFormatLabel(
        widthPt: Float,
        heightPt: Float,
        isPresentation: Boolean = false
    ): String {
        val w = widthPt.coerceAtLeast(1f)
        val h = heightPt.coerceAtLeast(1f)
        val ratio = w / h
        if (isPresentation || ratio > 1.25f) {
            if (kotlin.math.abs(ratio - (16f / 9f)) < 0.12f) return "Slide 16:9"
            if (kotlin.math.abs(ratio - (4f / 3f)) < 0.10f) return "Slide 4:3"
        }
        val minSide = minOf(w, h)
        val maxSide = maxOf(w, h)
        val isLandscape = w > h * 1.05f
        val base = when {
            kotlin.math.abs(minSide - 595f) < 18f && kotlin.math.abs(maxSide - 842f) < 22f -> "A4"
            kotlin.math.abs(minSide - 612f) < 18f && kotlin.math.abs(maxSide - 792f) < 22f -> "Carta"
            kotlin.math.abs(minSide - 612f) < 18f && kotlin.math.abs(maxSide - 1008f) < 28f -> "Oficio"
            kotlin.math.abs(minSide - 842f) < 24f && kotlin.math.abs(maxSide - 1191f) < 30f -> "A3"
            kotlin.math.abs(minSide - 420f) < 16f && kotlin.math.abs(maxSide - 595f) < 20f -> "A5"
            else -> {
                val mmW = (w * 25.4f / 72f).toInt()
                val mmH = (h * 25.4f / 72f).toInt()
                "${mmW}×${mmH} mm"
            }
        }
        return if (isLandscape && !base.contains("×")) "$base Horiz." else base
    }

    private fun parsePdf(context: Context, file: File): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        var pageCount = 1
        var nativeWidthPt = 595f
        var nativeHeightPt = 842f
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { firstPage ->
                            nativeWidthPt = firstPage.width.toFloat().coerceIn(240f, 2400f)
                            nativeHeightPt = firstPage.height.toFloat().coerceIn(240f, 2400f)
                        }
                    }
                }
            }
        }

        val pageTexts = MutableList(pageCount) { "" }
        var docTitle = fallbackTitle
        var docAuthor = "Documento PDF"

        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file).use { pdDoc ->
                pdDoc.documentInformation?.title?.takeIf { it.isNotBlank() }?.let { docTitle = it }
                pdDoc.documentInformation?.author?.takeIf { it.isNotBlank() }?.let { docAuthor = it }
                val stripper = PDFTextStripper()
                val total = pdDoc.numberOfPages.coerceAtMost(pageCount)
                for (p in 0 until total) {
                    stripper.startPage = p + 1
                    stripper.endPage = p + 1
                    pageTexts[p] = runCatching { stripper.getText(pdDoc).trim() }.getOrDefault("")
                }
            }
        }

        val toc = (0 until pageCount).map { idx ->
            val preview = pageTexts[idx].lineSequence().firstOrNull { it.isNotBlank() }?.take(42)
            val label = if (!preview.isNullOrBlank()) "Pág. ${idx + 1} · $preview" else "Página ${idx + 1}"
            TocEntry(title = label, targetIndex = idx, level = 0)
        }

        val totalWords = pageTexts.sumOf { countWords(it) }.coerceAtLeast(pageCount * 60)
        return ParsedDocument(
            title = docTitle,
            author = docAuthor,
            format = DocumentFormat.PDF,
            chapters = emptyList(),
            toc = toc,
            totalWords = totalWords,
            pdfPageCount = pageCount,
            pdfPageTexts = pageTexts,
            nativePageWidthPt = nativeWidthPt,
            nativePageHeightPt = nativeHeightPt,
            detectedFormatLabel = detectPageFormatLabel(nativeWidthPt, nativeHeightPt)
        )
    }

    private fun parseTxt(file: File): ParsedDocument {
        val title = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        val content = file.readText(Charsets.UTF_8)
        val rawParagraphs = content.split(Regex("\\r?\\n\\s*\\r?\\n"))
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }

        val chunkSize = 14
        val chapters = mutableListOf<ParsedChapter>()
        val toc = mutableListOf<TocEntry>()

        if (rawParagraphs.isEmpty()) {
            val b = ParsedBlock(0, BlockType.PARAGRAPH, "Archivo de texto vacío.")
            chapters.add(ParsedChapter(0, title, "txt(/section/0)", "0", listOf(b), 4))
            toc.add(TocEntry(title, 0, 0))
        } else {
            rawParagraphs.chunked(chunkSize).forEachIndexed { idx, chunk ->
                val blocks = chunk.mapIndexed { bIdx, text ->
                    val type = if (bIdx == 0 && text.length < 72 && !text.endsWith(".")) {
                        BlockType.HEADING_1
                    } else {
                        BlockType.PARAGRAPH
                    }
                    ParsedBlock(bIdx, type, text)
                }
                val chTitle = blocks.firstOrNull { it.type == BlockType.HEADING_1 }?.text
                    ?: "Sección ${idx + 1}"
                chapters.add(
                    ParsedChapter(
                        index = idx,
                        title = chTitle,
                        cfiPrefix = "txt(/section/$idx)",
                        href = "section_$idx",
                        blocks = blocks,
                        wordCount = blocks.sumOf { countWords(it.text) }
                    )
                )
                toc.add(TocEntry(chTitle, idx, 0))
            }
        }

        return ParsedDocument(
            title = title,
            author = "Texto plano",
            format = DocumentFormat.TXT,
            chapters = chapters,
            toc = toc,
            totalWords = chapters.sumOf { it.wordCount }
        )
    }

    private fun parseFb2(file: File): ParsedDocument {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ')
        val xml = file.readText(Charsets.UTF_8)
        val title = extractTagText(xml, "book-title")?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val author = extractTagText(xml, "first-name")?.let { first ->
            val last = extractTagText(xml, "last-name").orEmpty()
            "$first $last".trim()
        }?.takeIf { it.isNotBlank() } ?: "FictionBook 2"

        val blocks = mutableListOf<ParsedBlock>()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var inBody = false
        var inTitle = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "body" -> inBody = true
                        "title" -> if (inBody) inTitle = true
                        "p" -> if (inBody) {
                            val txt = parser.nextText().trim()
                            if (txt.isNotEmpty()) {
                                blocks.add(
                                    ParsedBlock(
                                        blocks.size,
                                        if (inTitle) BlockType.HEADING_1 else BlockType.PARAGRAPH,
                                        txt
                                    )
                                )
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "body" -> inBody = false
                        "title" -> inTitle = false
                    }
                }
            }
            event = parser.next()
        }

        val chapters = mutableListOf<ParsedChapter>()
        val toc = mutableListOf<TocEntry>()
        var currentTitle = title
        var currentList = mutableListOf<ParsedBlock>()

        fun flush() {
            if (currentList.isEmpty()) return
            val idx = chapters.size
            val reindexed = currentList.mapIndexed { i, b -> b.copy(index = i) }
            chapters.add(
                ParsedChapter(
                    index = idx,
                    title = currentTitle,
                    cfiPrefix = "fb2(/section/$idx)",
                    href = "section_$idx",
                    blocks = reindexed,
                    wordCount = reindexed.sumOf { countWords(it.text) }
                )
            )
            toc.add(TocEntry(currentTitle, idx, 0))
            currentList = mutableListOf()
        }

        for (b in blocks) {
            if (b.type == BlockType.HEADING_1 && currentList.isNotEmpty()) {
                flush()
                currentTitle = b.text.take(70)
            } else if (b.type == BlockType.HEADING_1) {
                currentTitle = b.text.take(70)
            }
            currentList.add(b)
        }
        flush()

        if (chapters.isEmpty()) {
            val empty = ParsedBlock(0, BlockType.PARAGRAPH, "No se encontró texto en este archivo FB2.")
            chapters.add(ParsedChapter(0, title, "fb2(/section/0)", "section_0", listOf(empty), 7))
            toc.add(TocEntry(title, 0, 0))
        }

        return ParsedDocument(
            title = title,
            author = author,
            format = DocumentFormat.FB2,
            chapters = chapters,
            toc = toc,
            totalWords = chapters.sumOf { it.wordCount }
        )
    }

    private fun extractOpfPath(containerXml: String): String? {
        val match = Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(containerXml)
        return match?.groupValues?.getOrNull(1)
    }

    private fun parseOpf(
        opfXml: String,
        onTitle: (String) -> Unit,
        onAuthor: (String) -> Unit,
        manifest: MutableMap<String, String>,
        spineIds: MutableList<String>
    ) {
        runCatching {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(opfXml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val localName = parser.name.substringAfter(':').lowercase()
                    when (localName) {
                        "title" -> onTitle(parser.nextText().trim())
                        "creator" -> onAuthor(parser.nextText().trim())
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                                manifest[id] = href
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (!idref.isNullOrBlank()) spineIds.add(idref)
                        }
                    }
                }
                event = parser.next()
            }
        }
    }

    private fun extractBlocksFromXhtml(xhtml: String): List<ParsedBlock> {
        val bodyContent = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE)
            .find(xhtml)?.groupValues?.getOrNull(1) ?: xhtml

        val cleaned = bodyContent
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<svg[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), "")

        val blockRegex = Regex("<(h1|h2|h3|p|blockquote|li)(?:\\s[^>]*)?>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)
        val blocks = mutableListOf<ParsedBlock>()

        blockRegex.findAll(cleaned).forEach { match ->
            val tag = match.groupValues[1].lowercase()
            val inner = stripHtmlTags(match.groupValues[2])
            if (inner.isNotBlank()) {
                val type = when (tag) {
                    "h1" -> BlockType.HEADING_1
                    "h2", "h3" -> BlockType.HEADING_2
                    "blockquote" -> BlockType.QUOTE
                    "li" -> BlockType.LIST_ITEM
                    else -> BlockType.PARAGRAPH
                }
                blocks.add(ParsedBlock(blocks.size, type, inner))
            }
        }

        if (blocks.isEmpty()) {
            val plain = stripHtmlTags(cleaned)
            if (plain.isNotBlank()) {
                blocks.add(ParsedBlock(0, BlockType.PARAGRAPH, plain))
            }
        }
        return blocks
    }

    private fun stripHtmlTags(raw: String): String {
        return raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun extractTagText(xml: String, tagName: String): String? {
        val regex = Regex("<(?:[a-zA-Z0-9_-]+:)?$tagName[^>]*>([\\s\\S]*?)</(?:[a-zA-Z0-9_-]+:)?$tagName>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.let { stripHtmlTags(it) }
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val cleanHref = href.substringBefore('#').removePrefix("./")
        return if (baseDir.isEmpty()) cleanHref else "$baseDir$cleanHref"
    }

    private fun countWords(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}
