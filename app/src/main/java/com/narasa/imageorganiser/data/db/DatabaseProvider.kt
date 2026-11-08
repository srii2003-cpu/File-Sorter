package com.narasa.imageorganiser.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.narasa.imageorganiser.data.crypto.DatabaseKeyStore
import com.narasa.imageorganiser.data.crypto.KeyUnavailableException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the SQLCipher-encrypted index. The index is a rebuildable cache: if the key is lost
 * or the file cannot be opened, the database and key are deleted and a fresh one is created.
 * Originals in shared storage are never touched. Call [get] off the main thread.
 */
class DatabaseProvider(
    private val context: Context,
    private val keyStore: DatabaseKeyStore,
    private val dbName: String,
) {
    private var database: AppDatabase? = null

    /** True if the last open had to delete and rebuild the database. */
    @Volatile
    var lastOpenRebuilt: Boolean = false
        private set

    @Synchronized
    fun get(): AppDatabase {
        database?.takeIf { it.isOpen }?.let { return it }
        System.loadLibrary("sqlcipher")
        val db = try {
            open(keyStore.getOrCreateKey())
        } catch (e: KeyUnavailableException) {
            rebuild()
        } catch (e: SQLiteException) {
            rebuild()
        }
        database = db
        return db
    }

    private fun rebuild(): AppDatabase {
        context.deleteDatabase(dbName)
        keyStore.reset()
        lastOpenRebuilt = true
        return open(keyStore.getOrCreateKey())
    }

    private fun open(key: ByteArray): AppDatabase {
        // SQLCipher raw-key form: the key is already random, so skip passphrase derivation.
        val rawKey = "x'${key.joinToString("") { "%02x".format(it) }}'".toByteArray()
        key.fill(0)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(rawKey))
            .build()
        try {
            db.openHelper.writableDatabase
        } catch (e: SQLiteException) {
            db.close()
            throw e
        }
        return db
    }
}
