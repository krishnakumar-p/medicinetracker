package com.medishelf.core.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.medishelf.core.common.Dispatcher
import com.medishelf.core.common.MediShelfDispatchers
import com.medishelf.core.domain.backup.DocumentStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

internal class ContentResolverDocumentStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(MediShelfDispatchers.IO) private val io: CoroutineDispatcher,
) : DocumentStorage {

    // "wt" truncates when the user picks an existing file to overwrite.
    override suspend fun <T> write(uri: String, block: suspend (OutputStream) -> T): T = withContext(io) {
        val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: throw FileNotFoundException(uri)
        stream.use { block(it) }
    }

    override suspend fun <T> read(uri: String, block: suspend (InputStream) -> T): T = withContext(io) {
        val stream = context.contentResolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException(uri)
        stream.use { block(it) }
    }
}
