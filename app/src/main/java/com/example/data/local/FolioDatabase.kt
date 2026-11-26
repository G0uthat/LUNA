package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.drive.DriveDao
import com.example.drive.DriveFileEntity
import com.example.drive.DriveSourceEntity
import com.example.drive.DriveSyncTaskEntity
import com.example.notebook.data.NotebookDao
import com.example.notebook.data.NotebookEntity
import com.example.notebook.data.NotebookPageEntity

@Database(
    entities = [
        DocumentEntity::class,
        PlaylistEntity::class,
        PlaylistDocumentCrossRef::class,
        HighlightNoteEntity::class,
        BookmarkEntity::class,
        DrawingLayerEntity::class,
        OcrPageCacheEntity::class,
        DictionarySourceEntity::class,
        NotebookEntity::class,
        NotebookPageEntity::class,
        DriveSourceEntity::class,
        DriveFileEntity::class,
        DriveSyncTaskEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class FolioDatabase : RoomDatabase() {
    abstract fun folioDao(): FolioDao
    abstract fun notebookDao(): NotebookDao
    abstract fun driveDao(): DriveDao

    companion object {
        @Volatile
        private var INSTANCE: FolioDatabase? = null

        /**
         * Non-destructive migration v1 -> v2: ensures playlists & playlist_documents tables exist
         * and adds extended metadata columns to documents if missing.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `accentColorHex` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_documents` (
                        `playlistId` INTEGER NOT NULL,
                        `documentId` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `documentId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_documents_playlistId` ON `playlist_documents` (`playlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_documents_documentId` ON `playlist_documents` (`documentId`)")
                addColumnIfMissing(db, "documents", "coverImagePath", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "documents", "genre", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "documents", "publisherYear", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "documents", "synopsis", "TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Non-destructive migration v2 -> v3: ensures extended metadata columns and auxiliary tables exist.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "documents", "coverImagePath", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "documents", "badgeTag", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "documents", "genre", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "documents", "publisherYear", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "documents", "synopsis", "TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Non-destructive migration v3 -> v4: adds covering SQLite indices for ORDER BY and WHERE clauses
         * across documents, playlists, highlights_notes, bookmarks, and dictionary_sources without
         * losing any user books, highlights, bookmarks, drawings, or OCR caches.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_lastOpenedAt` ON `documents` (`lastOpenedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_format` ON `documents` (`format`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_addedAt` ON `documents` (`addedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_createdAt` ON `playlists` (`createdAt`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_highlights_notes_documentId_chapterIndex_blockIndex_startChar` " +
                        "ON `highlights_notes` (`documentId`, `chapterIndex`, `blockIndex`, `startChar`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bookmarks_documentId_unitIndex_createdAt` " +
                        "ON `bookmarks` (`documentId`, `unitIndex`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bookmarks_documentId_locationKey` " +
                        "ON `bookmarks` (`documentId`, `locationKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dictionary_sources_sortOrder_id` " +
                        "ON `dictionary_sources` (`sortOrder`, `id`)"
                )
            }
        }

        /**
         * Non-destructive migration v4 -> v5: creates isolated Study Notebook tables (`notebooks` and
         * `notebook_pages` with FK CASCADE and covering indices) without touching any existing
         * library documents, highlights, bookmarks, reader drawings, or OCR caches.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notebooks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `coverImagePath` TEXT NOT NULL,
                        `pageWidthPx` INTEGER NOT NULL,
                        `pageHeightPx` INTEGER NOT NULL,
                        `pageTemplate` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notebooks_updatedAt` ON `notebooks` (`updatedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notebook_pages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `notebookId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `backgroundType` TEXT NOT NULL,
                        `backgroundColor` INTEGER NOT NULL,
                        `backgroundAssetPath` TEXT NOT NULL,
                        `contentJson` TEXT NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        FOREIGN KEY(`notebookId`) REFERENCES `notebooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notebook_pages_notebookId` ON `notebook_pages` (`notebookId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notebook_pages_notebookId_pageIndex` " +
                        "ON `notebook_pages` (`notebookId`, `pageIndex`)"
                )
            }
        }

        /**
         * Non-destructive migration v5 -> v6: creates Google Drive BYOC source, file index,
         * and offline sync queue tables without touching any existing documents or notebooks.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_sources` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `folderId` TEXT NOT NULL,
                        `folderLink` TEXT NOT NULL,
                        `accentColorHex` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `isDefaultSaveTarget` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_sources_folderId` ON `drive_sources` (`folderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_sources_isDefaultSaveTarget` ON `drive_sources` (`isDefaultSaveTarget`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_files` (
                        `driveFileId` TEXT NOT NULL,
                        `sourceId` INTEGER NOT NULL,
                        `parentFolderId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `modifiedTimeMs` INTEGER NOT NULL,
                        `md5Checksum` TEXT NOT NULL,
                        `thumbnailLink` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `localThumbnailPath` TEXT NOT NULL,
                        `linkedDocumentId` INTEGER NOT NULL,
                        `linkedNotebookId` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `localModifiedAt` INTEGER NOT NULL,
                        `lastError` TEXT NOT NULL,
                        PRIMARY KEY(`driveFileId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_files_sourceId_parentFolderId` ON `drive_files` (`sourceId`, `parentFolderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_files_linkedDocumentId` ON `drive_files` (`linkedDocumentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_files_linkedNotebookId` ON `drive_files` (`linkedNotebookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_files_syncStatus` ON `drive_files` (`syncStatus`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_sync_queue` (
                        `taskId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `action` TEXT NOT NULL,
                        `driveFileId` TEXT NOT NULL,
                        `sourceId` INTEGER NOT NULL,
                        `targetFolderId` TEXT NOT NULL,
                        `previousFolderId` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `localDocumentId` INTEGER NOT NULL,
                        `localNotebookId` INTEGER NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `lastError` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_sync_queue_createdAt` ON `drive_sync_queue` (`createdAt`)")
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDef: String
        ) {
            var exists = false
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameCol = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameCol >= 0 && cursor.getString(nameCol) == columnName) {
                        exists = true
                        break
                    }
                }
            }
            if (!exists) {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDef")
            }
        }

        fun getInstance(context: Context): FolioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FolioDatabase::class.java,
                    "luma_reader.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
