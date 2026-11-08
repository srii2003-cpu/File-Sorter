package com.narasa.imageorganiser.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The wrapped key file exists but can no longer be unwrapped (e.g. Keystore reset). */
class KeyUnavailableException(cause: Throwable? = null) : Exception("Database key cannot be unwrapped", cause)

/**
 * Holds the random 256-bit database key, wrapped by an Android Keystore AES-GCM key.
 * The wrapping key needs no user authentication, so background jobs can open the database.
 * File format in noBackupFilesDir: [version 1][12-byte IV][ciphertext + GCM tag].
 */
class DatabaseKeyStore(
    private val context: Context,
    private val keyAlias: String,
    private val keyFileName: String,
) {
    private val file: File get() = File(context.noBackupFilesDir, keyFileName)
    private val keyStore: KeyStore get() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun getOrCreateKey(): ByteArray = if (file.exists()) unwrap() else create()

    /** Deletes the wrapped key file and the Keystore wrapping key. */
    fun reset() {
        file.delete()
        keyStore.deleteEntry(keyAlias)
    }

    private fun create(): ByteArray {
        val dbKey = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, newWrappingKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(dbKey)
        file.writeBytes(byteArrayOf(FORMAT_VERSION) + iv + sealed)
        return dbKey
    }

    private fun unwrap(): ByteArray {
        try {
            val bytes = file.readBytes()
            if (bytes.size <= 1 + IV_BYTES || bytes[0] != FORMAT_VERSION) throw KeyUnavailableException()
            val wrappingKey = keyStore.getKey(keyAlias, null) as? SecretKey ?: throw KeyUnavailableException()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_BITS, bytes, 1, IV_BYTES))
            return cipher.doFinal(bytes, 1 + IV_BYTES, bytes.size - 1 - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw KeyUnavailableException(e)
        }
    }

    private fun newWrappingKey(): SecretKey {
        keyStore.deleteEntry(keyAlias)
        val spec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
