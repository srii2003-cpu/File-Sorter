package com.narasa.imageorganiser.permission

import android.Manifest

enum class PhotoAccess { FULL, PARTIAL, NONE }

/** Which photo permission to ask for, and what was granted, on each Android version. */
object PhotoPermissions {
    fun required(sdkInt: Int): Array<String> = when {
        sdkInt < 33 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        sdkInt == 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        // Android 14+ needs both in one request so the user can pick "Select photos".
        else -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }

    fun access(sdkInt: Int, isGranted: (String) -> Boolean): PhotoAccess = when {
        sdkInt < 33 -> if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) PhotoAccess.FULL else PhotoAccess.NONE
        isGranted(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccess.FULL
        sdkInt >= 34 && isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.PARTIAL
        else -> PhotoAccess.NONE
    }
}
