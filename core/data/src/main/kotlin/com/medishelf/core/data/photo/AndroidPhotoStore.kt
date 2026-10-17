package com.medishelf.core.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import androidx.core.net.toUri
import com.medishelf.core.common.Dispatcher
import com.medishelf.core.common.MediShelfDispatchers
import com.medishelf.core.domain.repository.PhotoStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

internal class AndroidPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(MediShelfDispatchers.IO) private val io: CoroutineDispatcher,
) : PhotoStore {

    private val dir: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    override suspend fun import(sourceUri: String): String = withContext(io) {
        val bitmap = decodeScaled(sourceUri)
        val name = "${UUID.randomUUID()}.jpg"
        val tmp = File(dir, "$name.tmp")
        try {
            tmp.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG encode failed" }
            }
            check(tmp.renameTo(File(dir, name))) { "Could not store photo" }
        } finally {
            tmp.delete()
            bitmap.recycle()
        }
        name
    }

    override val directoryPath: String get() = dir.absolutePath

    override fun absolutePath(fileName: String): String = File(dir, File(fileName).name).absolutePath

    override suspend fun delete(fileName: String) {
        withContext(io) { File(dir, File(fileName).name).delete() }
    }

    private fun decodeScaled(sourceUri: String): Bitmap {
        val uri = sourceUri.toUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF orientation itself.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unsupported image")
        val rotation = resolver.openInputStream(uri).use { stream ->
            when (stream?.let(::ExifInterface)?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
        if (rotation == 0f) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
            .also { if (it != decoded) decoded.recycle() }
    }

    private companion object {
        const val DIRECTORY = "photos"
        const val MAX_DIMENSION = 2048
        const val JPEG_QUALITY = 85
    }
}
