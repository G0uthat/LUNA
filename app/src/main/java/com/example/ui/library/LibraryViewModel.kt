package com.example.ui.library

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DictionarySourceEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.FolioRepository
import com.example.data.local.PlaylistDocumentCrossRef
import com.example.data.local.PlaylistEntity
import com.example.data.local.PreferencesRepository
import com.example.data.model.DocumentFormat
import com.example.data.model.ReaderPreferences
import com.example.engine.CoverManager
import com.example.engine.DocumentParser
import com.example.engine.SampleLibraryGenerator
import com.example.ui.components.LumaActionAnimType
import com.example.ui.components.LumaFeedbackEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LibraryViewModel(
    application: Application,
    private val repository: FolioRepository,
    private val preferencesRepository: PreferencesRepository
) : AndroidViewModel(application) {

    val documents: StateFlow<List<DocumentEntity>> = repository.documents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playlistRefs: StateFlow<List<PlaylistDocumentCrossRef>> = repository.playlistRefs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val preferences: StateFlow<ReaderPreferences> = preferencesRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderPreferences()
    )

    val dictionarySources: StateFlow<List<DictionarySourceEntity>> = repository.dictionarySources.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _feedbackEvent = MutableStateFlow<LumaFeedbackEvent?>(null)
    val feedbackEvent: StateFlow<LumaFeedbackEvent?> = _feedbackEvent.asStateFlow()

    private val coverColors = listOf(
        0xFF3E6AE1L,
        0xFF4F46E5L,
        0xFF0D9488L,
        0xFFBE185DL,
        0xFFD97706L,
        0xFF1E3A5FL
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultDictionarySources()
            SampleLibraryGenerator.ensureSampleDocuments(getApplication(), repository)
            ensureAllCoversExist(silent = true)
        }
    }

    fun clearFeedbackEvent() {
        _feedbackEvent.value = null
    }

    private fun emitFeedback(type: LumaActionAnimType, errorDetail: String? = null) {
        _feedbackEvent.value = LumaFeedbackEvent(type = type, errorDetail = errorDetail)
    }

    fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences(transform)
        }
    }

    // Playlists / Sagas (Spotify-style book collections)
    fun createPlaylist(name: String, description: String, accentColorHex: Long) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            repository.createPlaylist(cleanName, description.trim(), accentColorHex)
            emitFeedback(LumaActionAnimType.SAVED)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            emitFeedback(LumaActionAnimType.DELETED)
        }
    }

    fun toggleBookInPlaylist(playlistId: Long, documentId: Long, currentlyInPlaylist: Boolean) {
        viewModelScope.launch {
            if (currentlyInPlaylist) {
                repository.removeBookFromPlaylist(playlistId, documentId)
            } else {
                repository.addBookToPlaylist(playlistId, documentId)
            }
        }
    }

    // Automatic cover extraction / generation for any books missing covers
    fun regenerateMissingCovers() {
        viewModelScope.launch {
            ensureAllCoversExist(silent = false)
        }
    }

    private suspend fun ensureAllCoversExist(silent: Boolean) {
        val context = getApplication<Application>()
        val allDocs = repository.getAllDocumentsOnce()
        var updatedCount = 0
        for (doc in allDocs) {
            val existing = doc.coverImagePath
            if (existing.isNullOrBlank() || !File(existing).exists()) {
                val file = File(doc.filePath)
                if (!file.exists()) continue
                val format = DocumentFormat.fromExtension(doc.format) ?: DocumentFormat.EPUB
                val coverPath = CoverManager.generateOrExtractCover(
                    context = context,
                    sourceFile = file,
                    format = format,
                    title = doc.title,
                    author = doc.author,
                    accentColorHex = doc.coverColorHex
                )
                repository.updateDocumentCover(doc.id, coverPath)
                updatedCount++
            }
        }
        if (!silent) {
            emitFeedback(LumaActionAnimType.UPDATED)
        }
    }

    fun updateBookMetadata(
        doc: DocumentEntity,
        title: String,
        author: String,
        genre: String,
        publisherYear: String,
        synopsis: String,
        badgeTag: String
    ) {
        val cleanTitle = title.trim().ifEmpty { doc.title }
        val cleanAuthor = author.trim().ifEmpty { doc.author }
        val cleanGenre = genre.trim()
        val cleanYear = publisherYear.trim()
        val cleanSynopsis = synopsis.trim()
        val cleanBadge = badgeTag.trim().ifEmpty { doc.badgeTag.ifBlank { doc.format } }

        viewModelScope.launch {
            repository.updateDocumentMetadata(
                id = doc.id,
                title = cleanTitle,
                author = cleanAuthor,
                genre = cleanGenre,
                publisherYear = cleanYear,
                synopsis = cleanSynopsis,
                badgeTag = cleanBadge
            )
            emitFeedback(LumaActionAnimType.SAVED)
        }
    }

    fun updateBookCoverFromUri(doc: DocumentEntity, imageUri: Uri) {
        viewModelScope.launch {
            val savedPath = CoverManager.saveCustomCoverFromUri(getApplication(), imageUri)
            if (savedPath != null) {
                val oldPath = doc.coverImagePath
                repository.updateDocumentCover(doc.id, savedPath)
                if (oldPath.isNotBlank() && oldPath != savedPath) {
                    withContext(Dispatchers.IO) { runCatching { File(oldPath).delete() } }
                }
                emitFeedback(LumaActionAnimType.UPDATED)
            } else {
                emitFeedback(LumaActionAnimType.ERROR, "No se pudo cargar la imagen")
            }
        }
    }

    fun regenerateBookCover(doc: DocumentEntity) {
        viewModelScope.launch {
            val format = DocumentFormat.fromExtension(doc.format) ?: DocumentFormat.EPUB
            val newPath = CoverManager.regenerateArtisticCover(
                context = getApplication(),
                title = doc.title,
                author = doc.author,
                format = format,
                accentColorHex = doc.coverColorHex
            )
            val oldPath = doc.coverImagePath
            repository.updateDocumentCover(doc.id, newPath)
            if (oldPath.isNotBlank() && oldPath != newPath) {
                withContext(Dispatchers.IO) { runCatching { File(oldPath).delete() } }
            }
            emitFeedback(LumaActionAnimType.UPDATED)
        }
    }

    fun addDictionarySource(name: String, urlTemplate: String) {
        viewModelScope.launch {
            repository.addDictionarySource(name, urlTemplate)
            emitFeedback(LumaActionAnimType.SAVED)
        }
    }

    fun toggleDictionarySource(source: DictionarySourceEntity) {
        viewModelScope.launch { repository.toggleDictionarySource(source) }
    }

    fun deleteDictionarySource(id: Long) {
        viewModelScope.launch {
            repository.deleteDictionarySource(id)
            emitFeedback(LumaActionAnimType.DELETED)
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(doc.filePath).delete() }
            doc.coverImagePath?.let { path -> runCatching { File(path).delete() } }
            repository.deleteDocument(doc.id)
            emitFeedback(LumaActionAnimType.DELETED)
        }
    }

    fun importDocument(uri: Uri, onImportedOpen: (Long) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val context = getApplication<Application>()
                val displayName = resolveDisplayName(uri) ?: "documento_${System.currentTimeMillis()}.epub"
                val format = detectFormat(displayName, context.contentResolver.getType(uri))
                if (format == null) {
                    emitFeedback(LumaActionAnimType.ERROR, "Usa EPUB, PDF, DOCX, TXT o FB2")
                    return@launch
                }

                val booksDir = File(context.filesDir, "books").apply { mkdirs() }
                val safeName = "${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                val destFile = File(booksDir, safeName)

                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("No se pudo leer el archivo seleccionado.")
                }

                val parsed = DocumentParser.parse(context, destFile, format)
                val units = if (format == DocumentFormat.PDF) parsed.pdfPageCount else parsed.chapters.size
                val isImagePdf = format == DocumentFormat.PDF && parsed.pdfPageTexts.all { it.length < 30 }
                val accentColor = coverColors[(destFile.name.hashCode().and(0x7FFFFFFF)) % coverColors.size]

                // Automatically extract embedded cover (PDF page 1 / EPUB cover) or synthesize poster artwork
                val coverPath = CoverManager.generateOrExtractCover(
                    context = context,
                    sourceFile = destFile,
                    format = format,
                    title = parsed.title,
                    author = parsed.author,
                    accentColorHex = accentColor
                )

                val newId = repository.insertDocument(
                    DocumentEntity(
                        title = parsed.title,
                        author = parsed.author,
                        format = format.name,
                        filePath = destFile.absolutePath,
                        totalUnits = units.coerceAtLeast(1),
                        currentUnit = 0,
                        progressPercent = 0f,
                        totalWords = parsed.totalWords,
                        coverColorHex = accentColor,
                        coverImagePath = coverPath,
                        badgeTag = if (isImagePdf) "Manhwa OCR" else format.label
                    )
                )
                emitFeedback(LumaActionAnimType.IMPORTED)
                onImportedOpen(newId)
            } catch (e: Exception) {
                emitFeedback(LumaActionAnimType.ERROR, e.localizedMessage ?: "Archivo inválido")
            } finally {
                _isImporting.value = false
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun detectFormat(fileName: String, mimeType: String?): DocumentFormat? {
        DocumentFormat.fromFileName(fileName)?.let { return it }
        return when (mimeType?.lowercase()) {
            "application/epub+zip" -> DocumentFormat.EPUB
            "application/pdf" -> DocumentFormat.PDF
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DocumentFormat.DOCX
            "text/plain" -> DocumentFormat.TXT
            "application/x-fictionbook+xml", "text/xml" -> DocumentFormat.FB2
            else -> null
        }
    }

    class Factory(
        private val application: Application,
        private val repository: FolioRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(application, repository, preferencesRepository) as T
        }
    }
}
