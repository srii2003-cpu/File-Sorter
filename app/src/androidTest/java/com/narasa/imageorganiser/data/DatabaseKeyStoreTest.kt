package com.narasa.imageorganiser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.narasa.imageorganiser.data.crypto.DatabaseKeyStore
import com.narasa.imageorganiser.data.crypto.KeyUnavailableException
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseKeyStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val alias = "test_wrap_$id"
    private val fileName = "test_key_$id.bin"

    private fun store() = DatabaseKeyStore(context, alias, fileName)

    @After
    fun cleanUp() = store().reset()

    @Test
    fun createsThirtyTwoByteKey() {
        assertEquals(32, store().getOrCreateKey().size)
    }

    @Test
    fun sameKeyOnSecondCall() {
        val first = store().getOrCreateKey()
        assertArrayEquals(first, store().getOrCreateKey())
    }

    @Test
    fun keyFileDoesNotContainRawKey() {
        val key = store().getOrCreateKey()
        val file = File(context.noBackupFilesDir, fileName).readBytes()
        val containsKey = (0..file.size - key.size).any { start ->
            key.indices.all { file[start + it] == key[it] }
        }
        assertFalse(containsKey)
    }

    @Test
    fun aliasDeleted_throwsKeyUnavailable() {
        store().getOrCreateKey()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        assertThrows(KeyUnavailableException::class.java) { store().getOrCreateKey() }
    }

    @Test
    fun keyFileUnreadable_throwsKeyUnavailable() {
        // A directory where the key file should be makes readBytes() throw IOException.
        File(context.noBackupFilesDir, fileName).mkdirs()
        assertThrows(KeyUnavailableException::class.java) { store().getOrCreateKey() }
    }
}
