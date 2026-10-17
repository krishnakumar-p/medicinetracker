package com.medishelf.feature.scan.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.medishelf.core.model.RecognizedLine
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Formats printed on medicine packs: retail EAN/UPC, Code 128/39 and GS1 DataMatrix. */
internal fun createBarcodeScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_DATA_MATRIX,
        )
        .build(),
)

/** Frame analyzer; frames are dropped while the previous one is still being decoded. */
internal class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: return proxy.close()
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                // Prefer GS1 DataMatrix: it also carries expiry and batch.
                val best = codes.firstOrNull { it.format == Barcode.FORMAT_DATA_MATRIX } ?: codes.firstOrNull()
                best?.rawValue?.let(onBarcode)
            }
            .addOnCompleteListener { proxy.close() }
    }
}

/** Captures one still frame and runs on-device Latin text recognition on it. */
@OptIn(ExperimentalGetImage::class)
internal suspend fun captureAndReadText(capture: ImageCapture, executor: Executor): List<RecognizedLine> {
    val proxy = suspendCancellableCoroutine { cont ->
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = cont.resume(image)
                override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
            },
        )
    }
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        val media = proxy.image ?: return emptyList()
        val text = recognizer.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)).await()
        text.textBlocks.flatMap { block ->
            block.lines.map { line -> RecognizedLine(line.text, line.boundingBox?.height() ?: 0) }
        }
    } finally {
        proxy.close()
        recognizer.close()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
