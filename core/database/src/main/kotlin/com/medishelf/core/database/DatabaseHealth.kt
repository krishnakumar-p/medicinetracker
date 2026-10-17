package com.medishelf.core.database

import android.content.Context
import com.medishelf.core.common.Dispatcher
import com.medishelf.core.common.MediShelfDispatchers
import com.medishelf.core.security.DatabaseKeyManager
import com.medishelf.core.security.DatabaseKeyUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Detects an encrypted database that can no longer be opened (Keystore wiped by a device
 * reset/security update, or the key file lost) so the app can offer recovery instead of crashing.
 */
class DatabaseHealth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: DatabaseKeyManager,
    @Dispatcher(MediShelfDispatchers.IO) private val io: CoroutineDispatcher,
) {
    suspend fun isUsable(): Boolean = withContext(io) {
        val dbExists = context.getDatabasePath(MediShelfDatabase.NAME).exists()
        when {
            dbExists && !keyManager.hasKey() -> false
            else -> try {
                keyManager.getOrCreatePassphrase()
                true
            } catch (e: DatabaseKeyUnavailableException) {
                false
            }
        }
    }

    /** Deletes the inventory, its photos and the key. Settings and the app PIN are kept. */
    suspend fun startFresh() = withContext(io) {
        context.deleteDatabase(MediShelfDatabase.NAME)
        File(context.filesDir, "photos").deleteRecursively()
        keyManager.reset()
    }
}
