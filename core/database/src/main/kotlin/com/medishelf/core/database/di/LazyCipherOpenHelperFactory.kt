package com.medishelf.core.database.di

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Defers SQLCipher setup (native library load + Keystore key unwrap) until the first real
 * database access. Room creates the open helper eagerly inside build(), which Hilt may run on
 * the main thread; queries themselves run on Room's IO executor, so the cost moves there.
 */
internal class LazyCipherOpenHelperFactory(
    private val rawKey: () -> ByteArray,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        LazyHelper(configuration)

    private inner class LazyHelper(private val configuration: SupportSQLiteOpenHelper.Configuration) : SupportSQLiteOpenHelper {
        @Volatile private var writeAheadLogging: Boolean? = null

        private val lazyDelegate: Lazy<SupportSQLiteOpenHelper> = lazy {
            System.loadLibrary("sqlcipher")
            SupportOpenHelperFactory(rawKey()).create(configuration).also { helper ->
                writeAheadLogging?.let(helper::setWriteAheadLoggingEnabled)
            }
        }
        private val delegate: SupportSQLiteOpenHelper get() = lazyDelegate.value

        override val databaseName: String? get() = configuration.name

        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            writeAheadLogging = enabled
            if (lazyDelegate.isInitialized()) delegate.setWriteAheadLoggingEnabled(enabled)
        }

        override val writableDatabase: SupportSQLiteDatabase get() = delegate.writableDatabase
        override val readableDatabase: SupportSQLiteDatabase get() = delegate.readableDatabase

        override fun close() {
            if (lazyDelegate.isInitialized()) delegate.close()
        }
    }
}

/**
 * SQLCipher raw-key syntax (x'<64 hex chars>'). The passphrase is already 256 bits of
 * SecureRandom output, so PBKDF2 stretching adds nothing but ~0.5 s per connection open.
 */
internal fun ByteArray.toSqlCipherRawKey(): ByteArray =
    ("x'" + joinToString("") { "%02x".format(it) } + "'").toByteArray(Charsets.US_ASCII)
