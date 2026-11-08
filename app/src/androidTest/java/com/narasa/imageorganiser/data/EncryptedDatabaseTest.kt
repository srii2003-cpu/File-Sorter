package com.narasa.imageorganiser.data

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.narasa.imageorganiser.classifier.ImageStatus
import com.narasa.imageorganiser.classifier.Source
import com.narasa.imageorganiser.data.crypto.DatabaseKeyStore
import com.narasa.imageorganiser.data.db.DatabaseProvider
import com.narasa.imageorganiser.data.db.ImageEntity
import com.narasa.imageorganiser.data.db.OcrTextEntity
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val alias = "test_wrap_$id"
    private val keyFile = "test_key_$id.bin"
    private val dbName = "test_$id.db"
    private val dbFile: File get() = context.getDatabasePath(dbName)

    private fun keyStore() = DatabaseKeyStore(context, alias, keyFile)
    private fun provider() = DatabaseProvider(context, keyStore(), dbName)

    private fun sampleImage() = ImageEntity(
        mediaId = 1,
        source = Source.WHATSAPP,
        direction = null,
        takenDate = 0,
        dhash = null,
        width = 1,
        height = 1,
        primaryCategory = null,
        primaryReason = null,
        reviewReason = null,
        status = ImageStatus.PENDING,
        modelVersion = null,
    )

    /** Opens a database, inserts one image, closes it. */
    private fun seedOneImage() = runBlocking {
        val db = provider().get()
        db.imageDao().insert(sampleImage())
        db.close()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
        keyStore().reset()
    }

    @Test
    fun fts4Search_findsInsertedText() = runBlocking {
        val db = provider().get()
        val imageId = db.imageDao().insert(sampleImage())
        db.ocrDao().insert(OcrTextEntity(imageId = imageId, text = "Tutorial 3 Linear Algebra"))
        val hits = db.ocrDao().search("algebra")
        db.close()
        assertEquals(listOf(1L), hits.map { it.mediaId })
    }

    @Test
    fun dataSurvivesReopen() = runBlocking {
        seedOneImage()
        val p = provider()
        val count = p.get().imageDao().count()
        p.get().close()
        assertEquals(1, count)
        assertFalse(p.lastOpenRebuilt)
    }

    @Test
    fun fileHeaderIsNotPlainSqlite() {
        seedOneImage()
        val header = ByteArray(16).also { buf -> java.io.DataInputStream(dbFile.inputStream()).use { it.readFully(buf) } }
        assertNotEquals("SQLite format 3\u0000", String(header, Charsets.US_ASCII))
    }

    @Test
    fun plainSqliteCannotOpenFile() {
        seedOneImage()
        val keepFile = DatabaseErrorHandler { }
        assertThrows(SQLiteException::class.java) {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY, keepFile).use { db ->
                db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            }
        }
    }

    @Test
    fun keyLoss_rebuildsEmptyDatabase() = runBlocking {
        seedOneImage()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        val p = provider()
        val count = p.get().imageDao().count()
        p.get().close()
        assertEquals(0, count)
        assertTrue(p.lastOpenRebuilt)
    }

    @Test
    fun corruptFile_rebuildsEmptyDatabase() = runBlocking {
        seedOneImage()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        dbFile.writeBytes(ByteArray(4096).also { SecureRandom().nextBytes(it) })
        val p = provider()
        val count = p.get().imageDao().count()
        p.get().close()
        assertEquals(0, count)
        assertTrue(p.lastOpenRebuilt)
    }
}
