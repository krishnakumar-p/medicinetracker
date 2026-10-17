package com.medishelf.feature.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.common.InputImage
import com.medishelf.core.domain.scan.LabelTextParser
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.PackQuantity
import com.medishelf.core.model.QuantityUnit
import com.medishelf.feature.scan.camera.analyzeStill
import com.medishelf.feature.scan.camera.createBarcodeScanner
import com.medishelf.feature.scan.camera.createTextRecognizer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Runs the real on-device pipeline (bundled ML Kit OCR + barcode, then the label parser) on a
 * rendered pack side: the same code path as a camera still, minus the camera.
 */
@RunWith(AndroidJUnit4::class)
class PackScanDeviceTest {

    @Test
    fun renderedBoxSide_yieldsEveryField() = runBlocking {
        val recognizer = createTextRecognizer()
        val scanner = createBarcodeScanner()
        try {
            val reading = analyzeStill(InputImage.fromBitmap(renderPackSide(), 0), recognizer, scanner)
            val result = LabelTextParser.parse(reading.lines, LocalDate.now())

            assertThat(reading.barcodes).contains(EAN)
            assertThat(result.names.first()).isEqualTo("Panadol")
            assertThat(result.genericNames.first()).isEqualTo("Paracetamol")
            assertThat(result.strengths.first()).isEqualTo("500 mg")
            assertThat(result.forms.first()).isEqualTo(MedicineForm.TABLET)
            assertThat(result.quantities.first()).isEqualTo(PackQuantity(24.0, QuantityUnit.TABLETS))
            assertThat(result.expiryDates.first()).isEqualTo(LocalDate.of(2028, 2, 29))
            assertThat(result.batchNumbers.first()).isEqualTo("2B7781X")
            assertThat(result.manufacturers.first()).startsWith("GlaxoSmithKline")
            assertThat(result.dosageInstructions.first()).contains("Take 2 tablets")
            assertThat(result.storageNotes.first()).startsWith("Store below 25")
        } finally {
            recognizer.close()
            scanner.close()
        }
    }

    private fun renderPackSide(): Bitmap {
        val bitmap = Bitmap.createBitmap(1400, 2000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        var y = 170f
        fun line(text: String, size: Float, bold: Boolean = false) {
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(text, 80f, y, paint)
            y += size * 1.6f
        }
        line("PANADOL", 130f, bold = true)
        line("Paracetamol 500 mg", 64f)
        line("24 Film-coated Tablets", 52f)
        y += 30f
        line("Each film-coated tablet contains:", 36f)
        line("Paracetamol 500 mg", 36f)
        line("Dosage: Take 2 tablets every 4-6 hours.", 34f)
        line("Store below 25°C.", 34f)
        line("Manufactured by: GlaxoSmithKline Dungarvan Ltd, Ireland", 30f)
        line("BATCH: 2B7781X", 36f)
        line("EXP: 02/2028", 36f)
        drawEan13(canvas, EAN, left = 120f, top = y + 40f)
        return bitmap
    }

    /** Standard EAN-13 symbology: guard bars, L/G parity set by the first digit, R codes. */
    private fun drawEan13(canvas: Canvas, code: String, left: Float, top: Float) {
        val l = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
        val g = listOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
        val r = listOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
        val parity = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")
        val d = code.map { it - '0' }
        val bits = buildString {
            append("101")
            (1..6).forEach { i -> append(if (parity[d[0]][i - 1] == 'L') l[d[i]] else g[d[i]]) }
            append("01010")
            (7..12).forEach { i -> append(r[d[i]]) }
            append("101")
        }
        val module = 8f
        val paint = Paint().apply { color = Color.BLACK }
        bits.forEachIndexed { i, bit ->
            if (bit == '1') canvas.drawRect(left + i * module, top, left + (i + 1) * module, top + 260f, paint)
        }
    }

    private companion object {
        /** Includes its valid check digit (3); ML Kit rejects codes with a wrong one. */
        const val EAN = "6291000000013"
    }
}
