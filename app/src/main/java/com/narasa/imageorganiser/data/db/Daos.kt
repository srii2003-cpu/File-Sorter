package com.narasa.imageorganiser.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ImageDao {
    @Insert
    suspend fun insert(image: ImageEntity): Long

    @Query("SELECT count(*) FROM image")
    suspend fun count(): Int
}

@Dao
interface OcrDao {
    @Insert
    suspend fun insert(row: OcrTextEntity)

    @Query("SELECT image.* FROM image JOIN ocr_text ON image.id = ocr_text.rowid WHERE ocr_text MATCH :query")
    suspend fun search(query: String): List<ImageEntity>
}
