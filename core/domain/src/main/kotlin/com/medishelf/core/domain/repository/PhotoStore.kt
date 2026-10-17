package com.medishelf.core.domain.repository

/**
 * App-private photo files. Callers only ever hold file names; absolute paths are resolved
 * at display time so the store can move (e.g. after a backup restore).
 */
interface PhotoStore {
    /**
     * Copies the image at [sourceUri] (a content:// URI from the photo picker or camera),
     * downscaled and re-encoded as JPEG, which also strips EXIF metadata such as GPS.
     * Returns the new file name.
     */
    suspend fun import(sourceUri: String): String

    fun absolutePath(fileName: String): String

    /** The app-private directory holding all photos (used by backup/restore). */
    val directoryPath: String

    suspend fun delete(fileName: String)
}
