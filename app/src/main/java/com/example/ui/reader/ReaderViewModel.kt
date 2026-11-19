package com.example.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookmarkEntity
import com.example.data.local.DictionarySourceEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.FolioRepository
import com.example.data.local.HighlightNoteEntity
import com.example.data.local.PreferencesRepository
import com.example.data.model.DocumentFormat
import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.ExportFormatOption
import com.example.data.model.OcrPageResult
import com.example.data.model.ParsedDocument
import com.example.data.model.ReaderPreferences
import com.example.data.model.SearchMatch
import com.example.data.model.SelectedWordContext
import com.example.data.model.WikipediaSummary
import com.example.engine.DocumentParser
import com.example.engine.MergedDocumentExporter
import com.example.engine.OcrEngine
import com.example.engine.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val document: DocumentEntity? = null,
    val parsed: ParsedDocument? = null,
    val currentUnitIndex: Int = 0,
    val currentSheetIndex: Int = 0,
    val totalSheetCount: Int = 1,
    val activeSheetLocationKey: String? = null,
    val jumpToChapterTrigger: Int = 0,
    val isDockExpanded: Boolean = false,
    val isDrawingMode: Boolean = false,
    val activeTool: DrawingTool = DrawingTool.PEN,
    val activeColorArgb: Long = 0xFF1D5BFFL,
    val activeWidthNorm: Float = 0.008f,
    val selectedWord: SelectedWordContext? = null,
    val instantTranslation: String? = null,
    val isTranslatingWord: Boolean = false,
    val wordTranslationStatus: String = "",
    val wikiSummary: WikipediaSummary? = null,
    val wikiError: String? = null,
    val isWikiLoading: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val showTranslatedManhwaOverlay: Boolean = false,
    val isProcessingOcrOrTranslation: Boolean = false,
    val ocrTranslationStatus: String = "",
    val isExporting: Boolean = false,
    val toastMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

class ReaderViewModel(
    application: Application,
    private val documentId: Long,
    private val repository: FolioRepository,
    private val preferencesRepository: PreferencesRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<ReaderPreferences> = preferencesRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderPreferences()
    )

    val highlights: StateFlow<List<HighlightNoteEntity>> = repository.observeHighlights(documentId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.observeBookmarks(documentId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val drawingsByLocation: StateFlow<Map<String, List<DrawingStroke>>> =
        repository.observeDrawingsMap(documentId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val ocrByPage: StateFlow<Map<Int, OcrPageResult>> = repository.observeOcrPagesMap(documentId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val dictionarySources: StateFlow<List<DictionarySourceEntity>> =
        repository.dictionarySources.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // In-memory cache of rendered PDF page bitmaps for OCR & multimodal AI translation
    private val pageBitmaps = mutableMapOf<Int, Bitmap>()
    private val ocrRunningPages = mutableSetOf<Int>()

    // Undo / Redo stacks per locationKey
    private val undoStacks = mutableMapOf<String, ArrayDeque<List<DrawingStroke>>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<List<DrawingStroke>>>()
    private var wordLookupJob: Job? = null

    init {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val doc = repository.getDocumentById(documentId)
                    ?: error("Documento no encontrado en la biblioteca.")
                val file = File(doc.filePath)
                if (!file.exists()) error("El archivo local ya no existe en el dispositivo.")
                val format = DocumentFormat.fromExtension(doc.format) ?: DocumentFormat.EPUB
                val parsed = DocumentParser.parse(getApplication(), file, format)
                val totalUnits = if (format == DocumentFormat.PDF) parsed.pdfPageCount else parsed.chapters.size
                val startUnit = doc.currentUnit.coerceIn(0, (totalUnits - 1).coerceAtLeast(0))

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        document = doc,
                        parsed = parsed,
                        currentUnitIndex = startUnit
                    )
                }
                refreshUndoRedoFlags()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "No se pudo abrir el libro.")
                }
            }
        }
    }

    fun currentLocationKey(): String {
        val state = _uiState.value
        val parsed = state.parsed ?: return "unit-0"
        return if (parsed.format == DocumentFormat.PDF) {
            "pdf-page-${state.currentUnitIndex}"
        } else {
            state.activeSheetLocationKey
                ?: parsed.chapters.getOrNull(state.currentUnitIndex)?.cfiPrefix
                ?: "epubcfi(/6/2!)"
        }
    }

    fun onFluidSheetChanged(sheetIndex: Int, totalSheets: Int, locationKey: String, chapterIndex: Int) {
        val safeTotal = totalSheets.coerceAtLeast(1)
        val clampedSheet = sheetIndex.coerceIn(0, safeTotal - 1)
        val progress = if (safeTotal <= 1) 1f else (clampedSheet + 1).toFloat() / safeTotal.toFloat()
        _uiState.update {
            it.copy(
                currentUnitIndex = chapterIndex,
                currentSheetIndex = clampedSheet,
                totalSheetCount = safeTotal,
                activeSheetLocationKey = locationKey
            )
        }
        refreshUndoRedoFlags()
        viewModelScope.launch {
            repository.updateProgress(documentId, chapterIndex, progress)
        }
    }

    fun onUnitChanged(newIndex: Int, isExplicitJump: Boolean = false) {
        val state = _uiState.value
        val parsed = state.parsed ?: return
        val total = if (parsed.format == DocumentFormat.PDF) parsed.pdfPageCount else parsed.chapters.size
        val clamped = newIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        if (clamped == state.currentUnitIndex && !isExplicitJump) return

        val progress = if (total <= 1) 1f else (clamped + 1).toFloat() / total.toFloat()
        _uiState.update {
            it.copy(
                currentUnitIndex = clamped,
                selectedWord = null,
                jumpToChapterTrigger = if (isExplicitJump) it.jumpToChapterTrigger + 1 else it.jumpToChapterTrigger
            )
        }
        refreshUndoRedoFlags()

        viewModelScope.launch {
            repository.updateProgress(documentId, clamped, progress)
        }
    }

    fun toggleDock(expanded: Boolean? = null) {
        _uiState.update { it.copy(isDockExpanded = expanded ?: !it.isDockExpanded, selectedWord = null) }
    }

    fun toggleDrawingMode(enabled: Boolean? = null) {
        _uiState.update {
            it.copy(
                isDrawingMode = enabled ?: !it.isDrawingMode,
                isDockExpanded = false,
                selectedWord = null
            )
        }
        refreshUndoRedoFlags()
    }

    fun setDrawingTool(tool: DrawingTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun setDrawingColor(argb: Long) {
        _uiState.update { it.copy(activeColorArgb = argb) }
    }

    fun setDrawingWidth(widthNorm: Float) {
        _uiState.update { it.copy(activeWidthNorm = widthNorm) }
    }

    fun addStroke(locationKey: String, stroke: DrawingStroke) {
        val current = drawingsByLocation.value[locationKey].orEmpty()
        pushUndoSnapshot(locationKey, current)
        val updated = current + stroke
        viewModelScope.launch {
            repository.saveStrokes(documentId, locationKey, updated)
        }
        refreshUndoRedoFlags()
    }

    fun eraseStrokes(locationKey: String, idsToRemove: Set<String>) {
        if (idsToRemove.isEmpty()) return
        val current = drawingsByLocation.value[locationKey].orEmpty()
        val updated = current.filterNot { it.id in idsToRemove }
        if (updated.size == current.size) return
        pushUndoSnapshot(locationKey, current)
        viewModelScope.launch {
            repository.saveStrokes(documentId, locationKey, updated)
        }
        refreshUndoRedoFlags()
    }

    fun clearCurrentPageDrawings() {
        val loc = currentLocationKey()
        val current = drawingsByLocation.value[loc].orEmpty()
        if (current.isEmpty()) return
        pushUndoSnapshot(loc, current)
        viewModelScope.launch {
            repository.saveStrokes(documentId, loc, emptyList())
        }
        refreshUndoRedoFlags()
    }

    fun undoDrawing() {
        val loc = currentLocationKey()
        val stack = undoStacks[loc] ?: return
        if (stack.isEmpty()) return
        val current = drawingsByLocation.value[loc].orEmpty()
        redoStacks.getOrPut(loc) { ArrayDeque() }.addLast(current)
        val previous = stack.removeLast()
        viewModelScope.launch {
            repository.saveStrokes(documentId, loc, previous)
        }
        refreshUndoRedoFlags()
    }

    fun redoDrawing() {
        val loc = currentLocationKey()
        val stack = redoStacks[loc] ?: return
        if (stack.isEmpty()) return
        val current = drawingsByLocation.value[loc].orEmpty()
        undoStacks.getOrPut(loc) { ArrayDeque() }.addLast(current)
        val next = stack.removeLast()
        viewModelScope.launch {
            repository.saveStrokes(documentId, loc, next)
        }
        refreshUndoRedoFlags()
    }

    private fun pushUndoSnapshot(loc: String, snapshot: List<DrawingStroke>) {
        val stack = undoStacks.getOrPut(loc) { ArrayDeque() }
        stack.addLast(snapshot)
        if (stack.size > 30) stack.removeFirst()
        redoStacks[loc]?.clear()
    }

    private fun refreshUndoRedoFlags() {
        val loc = currentLocationKey()
        _uiState.update {
            it.copy(
                canUndo = !undoStacks[loc].isNullOrEmpty(),
                canRedo = !redoStacks[loc].isNullOrEmpty()
            )
        }
    }

    // Bookmarks
    fun toggleBookmarkCurrentPage() {
        val loc = currentLocationKey()
        val existing = bookmarks.value.firstOrNull { it.locationKey == loc }
        viewModelScope.launch {
            if (existing != null) {
                repository.deleteBookmarkByLocation(documentId, loc)
                showToast("Marcapáginas eliminado")
            } else {
                val parsed = _uiState.value.parsed
                val unit = _uiState.value.currentUnitIndex
                val label = if (parsed?.format == DocumentFormat.PDF) {
                    "Página ${unit + 1}"
                } else {
                    parsed?.chapters?.getOrNull(unit)?.title ?: "Sección ${unit + 1}"
                }
                repository.addBookmark(
                    BookmarkEntity(
                        documentId = documentId,
                        locationKey = loc,
                        unitIndex = unit,
                        label = label
                    )
                )
                showToast("Página marcada: $label")
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    // Highlights & Notes
    fun addHighlightFromSelection(colorArgb: Long, noteText: String = "") {
        val sel = _uiState.value.selectedWord ?: return
        val unit = _uiState.value.currentUnitIndex
        viewModelScope.launch {
            repository.addHighlight(
                HighlightNoteEntity(
                    documentId = documentId,
                    locationKey = sel.locationKey,
                    chapterIndex = unit,
                    blockIndex = sel.blockIndex,
                    startChar = sel.startChar,
                    endChar = sel.endChar,
                    quotedText = sel.word,
                    colorArgb = colorArgb,
                    noteText = noteText
                )
            )
            _uiState.update { it.copy(selectedWord = null) }
            showToast(if (noteText.isBlank()) "Fragmento subrayado" else "Nota guardada")
        }
    }

    fun updateHighlightNote(id: Long, noteText: String, colorArgb: Long) {
        viewModelScope.launch {
            repository.updateHighlightNote(id, noteText, colorArgb)
            showToast("Nota actualizada")
        }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch { repository.deleteHighlight(id) }
    }

    // Word Selection -> Instant EN/Auto -> ES Translation + Wikipedia ES REST Summary
    fun onWordSelected(context: SelectedWordContext) {
        wordLookupJob?.cancel()
        _uiState.update {
            it.copy(
                selectedWord = context,
                isDockExpanded = false,
                instantTranslation = null,
                isTranslatingWord = true,
                wordTranslationStatus = "Traduciendo al español…",
                wikiSummary = null,
                wikiError = null,
                isWikiLoading = true
            )
        }

        wordLookupJob = viewModelScope.launch {
            val prefs = preferences.value
            // Run Translation and Wikipedia ES concurrently
            launch {
                val transRes = TranslationService.translateWordToSpanish(
                    word = context.word,
                    contextSnippet = context.snippet,
                    prefs = prefs,
                    onStatus = { status ->
                        _uiState.update { it.copy(wordTranslationStatus = status) }
                    }
                )
                _uiState.update {
                    it.copy(
                        isTranslatingWord = false,
                        instantTranslation = transRes.getOrElse { err ->
                            "No se pudo traducir (${err.localizedMessage ?: "sin conexión"})"
                        }
                    )
                }
            }

            launch {
                val wikiRes = TranslationService.fetchWikipediaSummary(context.word)
                _uiState.update {
                    it.copy(
                        isWikiLoading = false,
                        wikiSummary = wikiRes.getOrNull(),
                        wikiError = wikiRes.exceptionOrNull()?.localizedMessage
                    )
                }
            }
        }
    }

    fun dismissWordPopover() {
        wordLookupJob?.cancel()
        _uiState.update { it.copy(selectedWord = null) }
    }

    // PDF / Manhwa On-Device OCR & Full-Page Translation
    fun onPdfPageBitmapReady(pageIndex: Int, bitmap: Bitmap) {
        pageBitmaps[pageIndex] = bitmap
        if (preferences.value.autoOcrPdf && ocrByPage.value[pageIndex] == null) {
            runOcrOnPage(pageIndex, bitmap, silent = true)
        }
    }

    private fun runOcrOnPage(pageIndex: Int, bitmap: Bitmap, silent: Boolean) {
        if (!ocrRunningPages.add(pageIndex)) return
        viewModelScope.launch {
            if (!silent) {
                _uiState.update {
                    it.copy(
                        isProcessingOcrOrTranslation = true,
                        ocrTranslationStatus = "Escaneando texto OCR en página ${pageIndex + 1}…"
                    )
                }
            }
            try {
                val ocrResult = OcrEngine.recognizePage(bitmap, pageIndex)
                repository.saveOcrPage(documentId, ocrResult)
            } catch (e: Exception) {
                if (!silent) {
                    showToast("Error en OCR: ${e.localizedMessage ?: "no disponible"}")
                }
            } finally {
                ocrRunningPages.remove(pageIndex)
                if (!silent) {
                    _uiState.update { it.copy(isProcessingOcrOrTranslation = false) }
                }
            }
        }
    }

    fun toggleOrRunManhwaPageTranslation() {
        val pageIndex = _uiState.value.currentUnitIndex
        val existing = ocrByPage.value[pageIndex]

        // If already translated, simply toggle overlay visibility
        if (existing != null && existing.isTranslated) {
            _uiState.update { it.copy(showTranslatedManhwaOverlay = !it.showTranslatedManhwaOverlay) }
            return
        }

        val bitmap = pageBitmaps[pageIndex]
        if (bitmap == null) {
            showToast("Espera un instante a que cargue la página PDF.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingOcrOrTranslation = true,
                    ocrTranslationStatus = "Detectando globos de texto con OCR…"
                )
            }
            try {
                val ocr = existing ?: OcrEngine.recognizePage(bitmap, pageIndex).also {
                    repository.saveOcrPage(documentId, it)
                }
                if (ocr.blocks.isEmpty()) {
                    showToast("No se detectó texto en esta página.")
                    return@launch
                }

                val translatedRes = TranslationService.translateManhwaBlocks(
                    blocks = ocr.blocks,
                    pageBitmap = bitmap,
                    prefs = preferences.value,
                    onProgress = { msg ->
                        _uiState.update { it.copy(ocrTranslationStatus = msg) }
                    }
                )

                val translatedBlocks = translatedRes.getOrThrow()
                val updatedOcr = ocr.copy(blocks = translatedBlocks, isTranslated = true)
                repository.saveOcrPage(documentId, updatedOcr)
                _uiState.update { it.copy(showTranslatedManhwaOverlay = true) }
                showToast("Página traducida (${translatedBlocks.size} bloques)")
            } catch (e: Exception) {
                showToast("Error al traducir: ${e.localizedMessage ?: "revisa tu conexión o API key"}")
            } finally {
                _uiState.update { it.copy(isProcessingOcrOrTranslation = false) }
            }
        }
    }

    // In-Book Text Search (works across EPUB/DOCX/TXT/FB2 + PDF text + OCR cached pages!)
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(searchMatches = emptyList()) }
            return
        }
        val parsed = _uiState.value.parsed ?: return
        viewModelScope.launch {
            val matches = withContext(Dispatchers.Default) {
                val found = mutableListOf<SearchMatch>()
                if (parsed.format == DocumentFormat.PDF) {
                    val ocrMap = ocrByPage.value
                    for (p in 0 until parsed.pdfPageCount) {
                        val nativeText = parsed.pdfPageTexts.getOrNull(p).orEmpty()
                        val ocrText = ocrMap[p]?.blocks?.joinToString(" ") { it.originalText }.orEmpty()
                        val combined = if (nativeText.isNotBlank()) nativeText else ocrText
                        val idx = combined.indexOf(trimmed, ignoreCase = true)
                        if (idx >= 0) {
                            val start = (idx - 40).coerceAtLeast(0)
                            val end = (idx + trimmed.length + 70).coerceAtMost(combined.length)
                            found.add(
                                SearchMatch(
                                    targetIndex = p,
                                    chapterTitle = "Página ${p + 1}",
                                    snippet = "…${combined.substring(start, end).trim()}…"
                                )
                            )
                        }
                    }
                } else {
                    for (chapter in parsed.chapters) {
                        for (block in chapter.blocks) {
                            val idx = block.text.indexOf(trimmed, ignoreCase = true)
                            if (idx >= 0) {
                                val start = (idx - 35).coerceAtLeast(0)
                                val end = (idx + trimmed.length + 75).coerceAtMost(block.text.length)
                                found.add(
                                    SearchMatch(
                                        targetIndex = chapter.index,
                                        chapterTitle = chapter.title,
                                        snippet = "…${block.text.substring(start, end).trim()}…",
                                        blockIndex = block.index
                                    )
                                )
                                if (found.size >= 40) break
                            }
                        }
                        if (found.size >= 40) break
                    }
                }
                found
            }
            _uiState.update { it.copy(searchMatches = matches) }
        }
    }

    // Merged Export (Save to SAF Uri OR Export to FileProvider cache to share)
    fun exportMergedToUri(uri: Uri, option: ExportFormatOption, onFinished: () -> Unit) {
        val doc = _uiState.value.document ?: return
        val parsed = _uiState.value.parsed ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val drawings = repository.getDrawingsMapOnce(documentId)
                val hl = repository.getHighlightsOnce(documentId)
                val ocr = repository.getOcrPagesMapOnce(documentId)
                val res = MergedDocumentExporter.exportToUri(
                    context = getApplication(),
                    document = doc,
                    parsed = parsed,
                    drawingsByLocation = drawings,
                    highlights = hl,
                    ocrByPage = ocr,
                    formatOption = option,
                    destinationUri = uri
                )
                if (res.isSuccess) {
                    showToast("Exportado con dibujos fusionados y texto seleccionable")
                    onFinished()
                } else {
                    showToast("Error al exportar: ${res.exceptionOrNull()?.localizedMessage}")
                }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun exportMergedForShare(option: ExportFormatOption, onFileReady: (File, String) -> Unit) {
        val doc = _uiState.value.document ?: return
        val parsed = _uiState.value.parsed ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val drawings = repository.getDrawingsMapOnce(documentId)
                val hl = repository.getHighlightsOnce(documentId)
                val ocr = repository.getOcrPagesMapOnce(documentId)
                val res = MergedDocumentExporter.exportToInternalCache(
                    context = getApplication(),
                    document = doc,
                    parsed = parsed,
                    drawingsByLocation = drawings,
                    highlights = hl,
                    ocrByPage = ocr,
                    formatOption = option
                )
                val file = res.getOrNull()
                if (file != null) {
                    val mime = MergedDocumentExporter.resolveOutputMimeType(doc, option)
                    onFileReady(file, mime)
                } else {
                    showToast("Error al preparar exportación: ${res.exceptionOrNull()?.localizedMessage}")
                }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch { preferencesRepository.updatePreferences(transform) }
    }

    fun addDictionarySource(name: String, urlTemplate: String) {
        viewModelScope.launch {
            repository.addDictionarySource(name, urlTemplate)
            showToast("Fuente \"$name\" añadida")
        }
    }

    fun toggleDictionarySource(source: DictionarySourceEntity) {
        viewModelScope.launch { repository.toggleDictionarySource(source) }
    }

    fun deleteDictionarySource(id: Long) {
        viewModelScope.launch { repository.deleteDictionarySource(id) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    class Factory(
        private val application: Application,
        private val documentId: Long,
        private val repository: FolioRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return ReaderViewModel(application, documentId, repository, preferencesRepository) as T
        }
    }
}
