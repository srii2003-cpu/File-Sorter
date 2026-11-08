package com.narasa.imageorganiser.permission

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Counts the images this app can see (under partial access: the user's selection). */
class SelectedPhotoCounter @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun count(): Int = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null,
        )?.use { it.count } ?: 0
    }
}
