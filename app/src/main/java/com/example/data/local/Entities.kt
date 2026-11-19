package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val format: String, // EPUB, PDF, DOCX, TXT, FB2
    val filePath: String,
    val coverImagePath: String = "",
    val totalUnits: Int, // chapters or PDF pages
    val currentUnit: Int = 0,
    val progressPercent: Float = 0f,
    val totalWords: Int = 0,
    val coverColorHex: Long = 0xFF2B64F5,
    val badgeTag: String = "",
    val genre: String = "",
    val publisherYear: String = "",
    val synopsis: String = "",
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val accentColorHex: Long = 0xFF2B64F5,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_documents",
    primaryKeys = ["playlistId", "documentId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("documentId")]
)
data class PlaylistDocumentCrossRef(
    val playlistId: Long,
    val documentId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "highlights_notes",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId"), Index("locationKey")]
)
data class HighlightNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val locationKey: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val quotedText: String,
    val colorArgb: Long,
    val noteText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val locationKey: String,
    val unitIndex: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "drawing_layers",
    primaryKeys = ["documentId", "locationKey"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class DrawingLayerEntity(
    val documentId: Long,
    val locationKey: String,
    val strokesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ocr_page_cache",
    primaryKeys = ["documentId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class OcrPageCacheEntity(
    val documentId: Long,
    val pageIndex: Int,
    val ocrJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dictionary_sources")
data class DictionarySourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val urlTemplate: String,
    val isWikipediaApi: Boolean = false,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0
)
