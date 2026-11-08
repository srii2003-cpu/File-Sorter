package com.narasa.imageorganiser.permission

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoPermissionsTest {
    private val readExternal = "android.permission.READ_EXTERNAL_STORAGE"
    private val mediaImages = "android.permission.READ_MEDIA_IMAGES"
    private val userSelected = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    private fun granted(vararg names: String): (String) -> Boolean = { it in names }

    @Test fun sdk30_requiresReadExternalOnly() = assertArrayEquals(arrayOf(readExternal), PhotoPermissions.required(30))
    @Test fun sdk32_requiresReadExternalOnly() = assertArrayEquals(arrayOf(readExternal), PhotoPermissions.required(32))
    @Test fun sdk33_requiresMediaImagesOnly() = assertArrayEquals(arrayOf(mediaImages), PhotoPermissions.required(33))
    @Test fun sdk34_requiresBoth() = assertArrayEquals(arrayOf(mediaImages, userSelected), PhotoPermissions.required(34))
    @Test fun sdk35_requiresBoth() = assertArrayEquals(arrayOf(mediaImages, userSelected), PhotoPermissions.required(35))

    @Test fun sdk30_granted_isFull() = assertEquals(PhotoAccess.FULL, PhotoPermissions.access(30, granted(readExternal)))
    @Test fun sdk30_denied_isNone() = assertEquals(PhotoAccess.NONE, PhotoPermissions.access(30, granted()))
    @Test fun sdk33_granted_isFull() = assertEquals(PhotoAccess.FULL, PhotoPermissions.access(33, granted(mediaImages)))
    @Test fun sdk33_readExternalOnly_isNone() = assertEquals(PhotoAccess.NONE, PhotoPermissions.access(33, granted(readExternal)))
    @Test fun sdk34_mediaImages_isFull() = assertEquals(PhotoAccess.FULL, PhotoPermissions.access(34, granted(mediaImages, userSelected)))
    @Test fun sdk34_userSelectedOnly_isPartial() = assertEquals(PhotoAccess.PARTIAL, PhotoPermissions.access(34, granted(userSelected)))
    @Test fun sdk35_nothing_isNone() = assertEquals(PhotoAccess.NONE, PhotoPermissions.access(35, granted()))
}
