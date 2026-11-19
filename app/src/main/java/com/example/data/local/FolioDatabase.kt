package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        PlaylistEntity::class,
        PlaylistDocumentCrossRef::class,
        HighlightNoteEntity::class,
        BookmarkEntity::class,
        DrawingLayerEntity::class,
        OcrPageCacheEntity::class,
        DictionarySourceEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FolioDatabase : RoomDatabase() {
    abstract fun folioDao(): FolioDao

    companion object {
        @Volatile
        private var INSTANCE: FolioDatabase? = null

        fun getInstance(context: Context): FolioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FolioDatabase::class.java,
                    "luma_reader.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
