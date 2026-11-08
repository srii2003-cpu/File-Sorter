package com.narasa.imageorganiser.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ImageEntity::class, OcrTextEntity::class, TagEntity::class, ImageTagEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao
    abstract fun ocrDao(): OcrDao
}
