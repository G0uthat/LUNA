package com.example.engine

import com.example.data.model.BlockType
import com.example.data.model.ParsedBlock
import com.example.data.model.ParsedChapter
import com.example.data.model.ReaderPreferences
import kotlin.math.ceil
import kotlin.math.max

data class FluidSheetBlock(
    val block: ParsedBlock,
    val charOffsetInBlock: Int = 0
)

data class FluidSheet(
    val globalIndex: Int,
    val chapterIndex: Int,
    val sheetInChapter: Int,
    val chapterTitle: String,
    val cfiPrefix: String,
    val locationKey: String,
    val blocks: List<FluidSheetBlock>
)

/**
 * Splits reflowable book chapters (EPUB, DOCX, TXT, FB2) into discrete physical book sheets ("hojas")
 * based on active typography and viewport size so every page has its own anchored coordinate space
 * for freehand drawings (both in paged modes and continuous vertical scroll mode).
 */
object FluidPaginator {

    fun paginate(
        chapters: List<ParsedChapter>,
        prefs: ReaderPreferences,
        viewportWidthDp: Float,
        viewportHeightDp: Float
    ): List<FluidSheet> {
        if (chapters.isEmpty()) return emptyList()

        val usableWidthDp = (viewportWidthDp.coerceAtMost(760f) - prefs.horizontalMarginDp * 2f)
            .coerceAtLeast(180f)
        // Reserve comfortable top/bottom page padding & subtle sheet footer space
        val usableHeightDp = (viewportHeightDp - 132f).coerceAtLeast(280f)

        val sheets = mutableListOf<FluidSheet>()
        var globalIdx = 0

        for (chapter in chapters) {
            var sheetInChapter = 0
            var currentBlocks = mutableListOf<FluidSheetBlock>()
            var currentHeightDp = 0f

            fun flushSheet() {
                if (currentBlocks.isEmpty()) return
                sheets.add(
                    FluidSheet(
                        globalIndex = globalIdx++,
                        chapterIndex = chapter.index,
                        sheetInChapter = sheetInChapter,
                        chapterTitle = chapter.title,
                        cfiPrefix = chapter.cfiPrefix,
                        locationKey = "${chapter.cfiPrefix}#sheet-$sheetInChapter",
                        blocks = currentBlocks.toList()
                    )
                )
                sheetInChapter++
                currentBlocks = mutableListOf()
                currentHeightDp = 0f
            }

            if (chapter.blocks.isEmpty()) {
                sheets.add(
                    FluidSheet(
                        globalIndex = globalIdx++,
                        chapterIndex = chapter.index,
                        sheetInChapter = 0,
                        chapterTitle = chapter.title,
                        cfiPrefix = chapter.cfiPrefix,
                        locationKey = "${chapter.cfiPrefix}#sheet-0",
                        blocks = emptyList()
                    )
                )
                continue
            }

            for (block in chapter.blocks) {
                val chunks = splitBlockIfNeeded(block, prefs, usableWidthDp, usableHeightDp)
                for (chunk in chunks) {
                    val chunkHeight = estimateBlockHeightDp(chunk.block, prefs, usableWidthDp)
                    val gap = if (currentBlocks.isEmpty()) 0f else 14f

                    if (currentBlocks.isNotEmpty() && currentHeightDp + gap + chunkHeight > usableHeightDp) {
                        flushSheet()
                    }
                    currentBlocks.add(chunk)
                    currentHeightDp += (if (currentBlocks.size == 1) 0f else 14f) + chunkHeight
                }
            }

            flushSheet()
        }

        return sheets
    }

    private fun fontSizeFor(type: BlockType, baseSp: Float): Float = when (type) {
        BlockType.HEADING_1 -> baseSp * 1.42f
        BlockType.HEADING_2 -> baseSp * 1.18f
        BlockType.QUOTE -> baseSp * 0.96f
        else -> baseSp
    }

    private fun estimateBlockHeightDp(
        block: ParsedBlock,
        prefs: ReaderPreferences,
        usableWidthDp: Float
    ): Float {
        val effectiveWidth = if (block.type == BlockType.QUOTE) {
            (usableWidthDp - 44f).coerceAtLeast(140f)
        } else {
            usableWidthDp
        }
        val fontSize = fontSizeFor(block.type, prefs.fontSizeSp)
        val avgCharWidth = fontSize * 0.55f
        val charsPerLine = max(14, (effectiveWidth / avgCharWidth).toInt())

        val lineCount = countWrappedLines(block.text, charsPerLine)
        val lineHeightDp = fontSize * prefs.lineHeightMultiplier * 1.06f
        val extraPadding = when (block.type) {
            BlockType.HEADING_1 -> 12f
            BlockType.HEADING_2 -> 8f
            BlockType.QUOTE -> 28f
            else -> 2f
        }
        return lineCount * lineHeightDp + extraPadding
    }

    private fun splitBlockIfNeeded(
        block: ParsedBlock,
        prefs: ReaderPreferences,
        usableWidthDp: Float,
        usableHeightDp: Float
    ): List<FluidSheetBlock> {
        val fullHeight = estimateBlockHeightDp(block, prefs, usableWidthDp)
        if (fullHeight <= usableHeightDp * 0.92f || block.text.length < 120) {
            return listOf(FluidSheetBlock(block = block, charOffsetInBlock = 0))
        }

        val fontSize = fontSizeFor(block.type, prefs.fontSizeSp)
        val charsPerLine = max(14, (usableWidthDp / (fontSize * 0.55f)).toInt())
        val lineHeightDp = fontSize * prefs.lineHeightMultiplier * 1.06f
        val maxLinesPerChunk = max(4, ((usableHeightDp * 0.84f) / lineHeightDp).toInt())
        val approxMaxChars = max(100, maxLinesPerChunk * charsPerLine)

        val result = mutableListOf<FluidSheetBlock>()
        val text = block.text
        var cursor = 0
        while (cursor < text.length) {
            val remaining = text.length - cursor
            if (remaining <= approxMaxChars) {
                result.add(
                    FluidSheetBlock(
                        block = block.copy(text = text.substring(cursor)),
                        charOffsetInBlock = cursor
                    )
                )
                break
            }
            var splitAt = (cursor + approxMaxChars).coerceAtMost(text.length)
            // Prefer splitting cleanly at a space boundary
            val lastSpace = text.lastIndexOf(' ', splitAt)
            if (lastSpace > cursor + approxMaxChars / 2) {
                splitAt = lastSpace
            }
            val piece = text.substring(cursor, splitAt).trimEnd()
            if (piece.isNotEmpty()) {
                result.add(
                    FluidSheetBlock(
                        block = block.copy(text = piece),
                        charOffsetInBlock = cursor
                    )
                )
            }
            cursor = splitAt
            while (cursor < text.length && text[cursor] == ' ') cursor++
        }
        return result.ifEmpty { listOf(FluidSheetBlock(block = block, charOffsetInBlock = 0)) }
    }

    private fun countWrappedLines(text: String, charsPerLine: Int): Int {
        if (text.isBlank()) return 1
        var lines = 0
        for (rawLine in text.split('\n')) {
            if (rawLine.isEmpty()) {
                lines += 1
            } else {
                lines += ceil(rawLine.length.toFloat() / charsPerLine.toFloat()).toInt().coerceAtLeast(1)
            }
        }
        return lines.coerceAtLeast(1)
    }
}
