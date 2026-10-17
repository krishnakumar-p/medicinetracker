package com.medishelf.core.backup

import android.content.Context
import com.medishelf.core.common.Dispatcher
import com.medishelf.core.common.MediShelfDispatchers
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.database.dao.BackupDao
import com.medishelf.core.domain.backup.BackupService
import com.medishelf.core.domain.backup.BackupSummary
import com.medishelf.core.domain.backup.ImportResult
import com.medishelf.core.domain.demo.DemoData
import com.medishelf.core.domain.reminders.ReminderLedger
import com.medishelf.core.domain.reminders.ReminderLedgerRepository
import com.medishelf.core.domain.repository.PhotoStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Archive layout (AES-256 zip; opens in 7-Zip with the password):
 *   manifest.json   format, schema version, counts
 *   data.json       every record (BackupPayload)
 *   photos/<name>   referenced photo files
 */
internal class ZipBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BackupDao,
    private val photoStore: PhotoStore,
    private val ledger: ReminderLedgerRepository,
    private val demoData: DemoData,
    private val time: TimeProvider,
    @Dispatcher(MediShelfDispatchers.IO) private val io: CoroutineDispatcher,
) : BackupService {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun export(output: OutputStream, password: CharArray): BackupSummary = withContext(io) {
        val payload = dao.snapshot().toPayload()
        val photosDir = File(photoStore.directoryPath)
        val photoFiles = payload.photoFiles.map { File(photosDir, it) }.filter(File::isFile)
        val manifest = BackupManifest(
            format = BACKUP_FORMAT,
            schemaVersion = CURRENT_SCHEMA,
            createdAtEpochMillis = time.now().toEpochMilli(),
            appVersion = appVersion(),
            medicines = payload.medicines.size,
            photos = photoFiles.size,
        )
        ZipOutputStream(output.buffered(), password).use { zip ->
            zip.entry(MANIFEST) { it.write(json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray()) }
            zip.entry(DATA) { it.write(json.encodeToString(BackupPayload.serializer(), payload).encodeToByteArray()) }
            photoFiles.forEach { file -> zip.entry(PHOTOS + file.name) { out -> file.inputStream().use { it.copyTo(out) } } }
        }
        BackupSummary(payload.medicines.size, payload.members.size, payload.locations.size, photoFiles.size)
    }

    override suspend fun import(input: InputStream, password: CharArray): ImportResult = withContext(io) {
        val staging = File(context.filesDir, STAGING).apply { deleteRecursively(); mkdirs() }
        try {
            val read = when (val r = readArchive(input, password, staging)) {
                is Read.Ok -> r
                is Read.Error -> return@withContext r.result
            }
            val snapshot = try {
                read.payload.toSnapshot()
            } catch (e: IllegalArgumentException) {
                return@withContext ImportResult.NotABackup
            }
            dao.replaceAll(snapshot)
            swapPhotos(staging)
            // IDs now belong to the restored records: old reminder/demo bookkeeping no longer applies.
            ledger.save(ReminderLedger())
            demoData.forget()
            ImportResult.Success(
                BackupSummary(snapshot.medicines.size, snapshot.members.size, snapshot.locations.size, read.photoCount),
            )
        } catch (e: Exception) {
            ImportResult.Failed(e.javaClass.simpleName)
        } finally {
            staging.deleteRecursively()
        }
    }

    private sealed interface Read {
        data class Ok(val payload: BackupPayload, val photoCount: Int) : Read
        data class Error(val result: ImportResult) : Read
    }

    private fun readArchive(input: InputStream, password: CharArray, staging: File): Read {
        var manifest: BackupManifest? = null
        var payload: BackupPayload? = null
        var photos = 0
        var totalBytes = 0L
        try {
            ZipInputStream(input.buffered(), password).use { zip ->
                var entries = 0
                while (true) {
                    val header = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) return Read.Error(ImportResult.NotABackup)
                    val name = header.fileName
                    when {
                        header.isDirectory -> Unit
                        name == MANIFEST -> manifest = json.decodeFromString(BackupManifest.serializer(), zip.readLimited(MAX_JSON_BYTES).decodeToString())
                        name == DATA -> payload = json.decodeFromString(BackupPayload.serializer(), zip.readLimited(MAX_JSON_BYTES).decodeToString())
                        name.startsWith(PHOTOS) -> {
                            val fileName = name.removePrefix(PHOTOS)
                            if (!isSafeFileName(fileName)) return Read.Error(ImportResult.NotABackup)
                            val bytes = zip.readLimited(MAX_PHOTO_BYTES)
                            totalBytes += bytes.size
                            if (totalBytes > MAX_TOTAL_BYTES) return Read.Error(ImportResult.NotABackup)
                            File(staging, fileName).writeBytes(bytes)
                            photos++
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            return Read.Error(if (e.type == ZipException.Type.WRONG_PASSWORD) ImportResult.WrongPassword else ImportResult.NotABackup)
        } catch (e: SerializationException) {
            return Read.Error(ImportResult.NotABackup)
        } catch (e: IllegalArgumentException) {
            return Read.Error(ImportResult.NotABackup)
        }
        val m = manifest ?: return Read.Error(ImportResult.NotABackup)
        if (m.format != BACKUP_FORMAT) return Read.Error(ImportResult.NotABackup)
        if (m.schemaVersion > CURRENT_SCHEMA) return Read.Error(ImportResult.TooNew)
        val p = payload ?: return Read.Error(ImportResult.NotABackup)
        if (!p.isConsistent()) return Read.Error(ImportResult.NotABackup)
        return Read.Ok(p, photos)
    }

    /** Old photos are only deleted once the new set is in place. */
    private fun swapPhotos(staging: File) {
        val live = File(photoStore.directoryPath)
        val old = File(context.filesDir, OLD_PHOTOS).apply { deleteRecursively() }
        if (live.exists()) check(live.renameTo(old)) { "Could not move old photos" }
        check(staging.renameTo(live)) { "Could not install restored photos" }
        old.deleteRecursively()
    }

    private fun ZipOutputStream.entry(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(
            ZipParameters().apply {
                fileNameInZip = name
                compressionMethod = CompressionMethod.DEFLATE
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            },
        )
        write(this)
        closeEntry()
    }

    /** Reads the current entry, refusing anything larger than [limit] (zip-bomb guard). */
    private fun ZipInputStream.readLimited(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n == -1) break
            total += n
            require(total <= limit) { "Entry too large" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private companion object {
        const val MANIFEST = "manifest.json"
        const val DATA = "data.json"
        const val PHOTOS = "photos/"
        const val STAGING = "photos_import"
        const val OLD_PHOTOS = "photos_previous"
        const val MAX_ENTRIES = 20_000
        const val MAX_JSON_BYTES = 64L * 1024 * 1024
        const val MAX_PHOTO_BYTES = 25L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
    }
}
