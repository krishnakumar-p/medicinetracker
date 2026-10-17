package com.medishelf.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** The wrapped key exists but can no longer be unwrapped (e.g. Keystore was reset). */
class DatabaseKeyUnavailableException(cause: Throwable) :
    IllegalStateException("Database key cannot be unwrapped", cause)

/**
 * Owns the SQLCipher passphrase: 32 random bytes, wrapped with a non-exportable AES-256-GCM
 * Keystore key and stored in no-backup storage. Not bound to user authentication so the
 * background expiry job can open the database while the app is locked; the app lock
 * protects the UI, this protects data at rest.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyFile: File get() = File(context.noBackupFilesDir, KEY_FILE)

    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val file = keyFile
        return if (file.exists()) unwrap(file.readBytes()) else createAndStore(file)
    }

    fun hasKey(): Boolean = keyFile.exists()

    /** Forgets the key. Only for "start fresh" after the key became unusable: data is lost. */
    @Synchronized
    fun reset() {
        keyFile.delete()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun createAndStore(file: File): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey()) }
        val blob = cipher.iv + cipher.doFinal(passphrase)
        // Write-then-rename so a crash never leaves a truncated key file.
        val tmp = File(file.parentFile, "$KEY_FILE.tmp")
        tmp.writeBytes(blob)
        check(tmp.renameTo(file)) { "Could not persist database key" }
        return passphrase
    }

    private fun unwrap(blob: ByteArray): ByteArray = try {
        val iv = blob.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, existingWrappingKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    } catch (e: Exception) {
        throw DatabaseKeyUnavailableException(e)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingWrappingKey(): SecretKey =
        checkNotNull(keyStore().getKey(KEY_ALIAS, null) as? SecretKey) { "Wrapping key missing" }

    private fun getOrCreateWrappingKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "medishelf_db_kek"
        const val KEY_FILE = "db.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
