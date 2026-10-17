package com.medishelf.core.domain.backup

import java.io.InputStream
import java.io.OutputStream

data class BackupSummary(val medicines: Int, val members: Int, val locations: Int, val photos: Int)

sealed interface ImportResult {
    data class Success(val summary: BackupSummary) : ImportResult
    data object WrongPassword : ImportResult
    data object NotABackup : ImportResult
    /** Made by a newer MediShelf with a schema this version can't read. */
    data object TooNew : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * Full inventory backup as an AES-256 password-protected zip (data.json + photos). Settings and
 * the app PIN are device-specific and deliberately not included.
 */
interface BackupService {
    suspend fun export(output: OutputStream, password: CharArray): BackupSummary

    /** Replaces all current records with the backup's, atomically: on any failure nothing changes. */
    suspend fun import(input: InputStream, password: CharArray): ImportResult
}

/** Opens Storage Access Framework documents (Drive, Files, USB…) chosen by the user. */
interface DocumentStorage {
    suspend fun <T> write(uri: String, block: suspend (OutputStream) -> T): T
    suspend fun <T> read(uri: String, block: suspend (InputStream) -> T): T
}

enum class BackupPasswordProblem { TOO_SHORT, MISMATCH }

object BackupPasswordRules {
    const val MIN_LENGTH = 8

    fun validate(password: String, confirmation: String?): BackupPasswordProblem? = when {
        password.length < MIN_LENGTH -> BackupPasswordProblem.TOO_SHORT
        confirmation != null && confirmation != password -> BackupPasswordProblem.MISMATCH
        else -> null
    }
}
