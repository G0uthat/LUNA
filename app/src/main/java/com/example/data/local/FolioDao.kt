package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolioDao {
    // Documents
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    suspend fun getAllDocumentsOnce(): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("UPDATE documents SET coverImagePath = :coverPath WHERE id = :id")
    suspend fun updateDocumentCover(id: Long, coverPath: String)

    @Query(
        "UPDATE documents SET title = :title, author = :author, genre = :genre, " +
            "publisherYear = :publisherYear, synopsis = :synopsis, badgeTag = :badgeTag WHERE id = :id"
    )
    suspend fun updateDocumentMetadata(
        id: Long,
        title: String,
        author: String,
        genre: String,
        publisherYear: String,
        synopsis: String,
        badgeTag: String
    )

    @Query("UPDATE documents SET currentUnit = :unit, progressPercent = :progress, lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, unit: Int, progress: Float, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    // Playlists (Spotify-style book collections / sagas)
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_documents")
    fun observeAllPlaylistRefs(): Flow<List<PlaylistDocumentCrossRef>>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDocumentToPlaylist(ref: PlaylistDocumentCrossRef)

    @Query("DELETE FROM playlist_documents WHERE playlistId = :playlistId AND documentId = :documentId")
    suspend fun removeDocumentFromPlaylist(playlistId: Long, documentId: Long)

    // Highlights & Notes
    @Query("SELECT * FROM highlights_notes WHERE documentId = :documentId ORDER BY chapterIndex ASC, blockIndex ASC, startChar ASC")
    fun observeHighlights(documentId: Long): Flow<List<HighlightNoteEntity>>

    @Query("SELECT * FROM highlights_notes WHERE documentId = :documentId")
    suspend fun getHighlightsOnce(documentId: Long): List<HighlightNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightNoteEntity): Long

    @Query("UPDATE highlights_notes SET noteText = :noteText, colorArgb = :colorArgb WHERE id = :id")
    suspend fun updateHighlightNote(id: Long, noteText: String, colorArgb: Long)

    @Query("DELETE FROM highlights_notes WHERE id = :id")
    suspend fun deleteHighlight(id: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY unitIndex ASC, createdAt DESC")
    fun observeBookmarks(documentId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId AND locationKey = :locationKey")
    suspend fun deleteBookmarkByLocation(documentId: Long, locationKey: String)

    // Drawing Layers
    @Query("SELECT * FROM drawing_layers WHERE documentId = :documentId")
    fun observeDrawingLayers(documentId: Long): Flow<List<DrawingLayerEntity>>

    @Query("SELECT * FROM drawing_layers WHERE documentId = :documentId")
    suspend fun getDrawingLayersOnce(documentId: Long): List<DrawingLayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrawingLayer(layer: DrawingLayerEntity)

    @Query("DELETE FROM drawing_layers WHERE documentId = :documentId AND locationKey = :locationKey")
    suspend fun deleteDrawingLayer(documentId: Long, locationKey: String)

    // OCR Cache
    @Query("SELECT * FROM ocr_page_cache WHERE documentId = :documentId")
    fun observeOcrPages(documentId: Long): Flow<List<OcrPageCacheEntity>>

    @Query("SELECT * FROM ocr_page_cache WHERE documentId = :documentId")
    suspend fun getOcrPagesOnce(documentId: Long): List<OcrPageCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOcrPage(entity: OcrPageCacheEntity)

    // Dictionary Sources
    @Query("SELECT * FROM dictionary_sources ORDER BY sortOrder ASC, id ASC")
    fun observeDictionarySources(): Flow<List<DictionarySourceEntity>>

    @Query("SELECT COUNT(*) FROM dictionary_sources")
    suspend fun getDictionarySourceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDictionarySource(source: DictionarySourceEntity): Long

    @Update
    suspend fun updateDictionarySource(source: DictionarySourceEntity)

    @Query("DELETE FROM dictionary_sources WHERE id = :id")
    suspend fun deleteDictionarySource(id: Long)
}
