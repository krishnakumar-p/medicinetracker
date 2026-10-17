package com.medishelf.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.PackQuantity
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.model.RecognizedLine
import com.medishelf.core.model.ScanSuggestion
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.feature.scan.navigation.ScanMode
import com.medishelf.feature.scan.navigation.ScanRoute
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ScanViewModelTest {
    private val time = TestTimeProvider()
    private fun vm(mode: ScanMode) = ScanViewModel(SavedStateHandle(route = ScanRoute(mode)), time)
    private fun side(vararg lines: Pair<String, Int>, barcodes: List<String> = emptyList()) =
        SideReading(lines.map { RecognizedLine(it.first, it.second) }, barcodes)

    @Test
    fun barcodeMode_returnsImmediately_firstScanWins() {
        val vm = vm(ScanMode.BARCODE)
        vm.onBarcode("06291234567890")
        vm.onBarcode("1111111111111")
        assertThat(vm.uiState.value).isEqualTo(ScanUiState.Done(ScanSuggestion(barcode = "6291234567890")))
    }

    @Test
    fun packMode_mergesAllSides_andGs1BeatsOcrForExpiryAndBatch() {
        val vm = vm(ScanMode.PACK)
        vm.onBarcode("010629123456789017271231" + "10B42") // live preview sees the DataMatrix
        assertThat((vm.uiState.value as ScanUiState.Camera).barcode).isEqualTo("6291234567890")

        vm.onSideCaptureStarted()
        vm.onSideRead(side("BRUFEN®" to 80, "Ibuprofen 400 mg" to 30, "30 film-coated tablets" to 20))
        vm.onSideCaptureStarted()
        vm.onSideRead(side("Manufactured by: Abbott Laboratories Ltd, UK" to 14, "EXP 06/2027  LOT X9" to 14))
        assertThat((vm.uiState.value as ScanUiState.Camera).sides).isEqualTo(2)

        vm.onFinishPack()
        val review = vm.uiState.value as ScanUiState.Review
        with(review.selection) {
            assertThat(name).isEqualTo("Brufen")
            assertThat(genericName).isEqualTo("Ibuprofen")
            assertThat(strength).isEqualTo("400 mg")
            assertThat(form).isEqualTo(MedicineForm.TABLET)
            assertThat(quantity).isEqualTo(PackQuantity(30.0, QuantityUnit.TABLETS))
            assertThat(manufacturer).isEqualTo("Abbott Laboratories Ltd")
            assertThat(barcode).isEqualTo("6291234567890")
            assertThat(expiry).isEqualTo(LocalDate.of(2027, 12, 31)) // GS1, not the OCR'd 06/2027
            assertThat(batch).isEqualTo("B42")
        }
        assertThat(review.suggestions.expiryDates).contains(LocalDate.of(2027, 6, 30)) // still offered
    }

    @Test
    fun confirm_dropsFieldsTheUserDeselected() {
        val vm = vm(ScanMode.PACK)
        vm.onSideRead(side("ZYRTEC" to 60, "Cetirizine 10 mg" to 20, "POM" to 14))
        vm.onFinishPack()
        vm.onSelectionChange { it.copy(strength = null, isPrescription = false) }
        vm.onConfirm()

        val result = (vm.uiState.value as ScanUiState.Done).result
        assertThat(result.name).isEqualTo("Zyrtec")
        assertThat(result.genericName).isEqualTo("Cetirizine")
        assertThat(result.strength).isNull()
        assertThat(result.isPrescription).isNull()
    }

    @Test
    fun emptyPhotos_areNotCounted_andNothingReadOffersRetry() {
        val vm = vm(ScanMode.PACK)
        vm.onSideCaptureStarted()
        vm.onSideRead(side())
        val camera = vm.uiState.value as ScanUiState.Camera
        assertThat(camera.sides).isEqualTo(0)
        assertThat(camera.lastSideEmpty).isTrue()

        vm.onSideFailed()
        vm.onFinishPack()
        assertThat(vm.uiState.value).isEqualTo(ScanUiState.NothingRead)
        vm.onRetry()
        assertThat(vm.uiState.value).isEqualTo(ScanUiState.Camera())
    }
}
