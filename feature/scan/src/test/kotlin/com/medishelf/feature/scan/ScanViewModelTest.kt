package com.medishelf.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.google.common.truth.Truth.assertThat
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

    @Test
    fun retailBarcode_returnsNormalisedCodeOnly() {
        val vm = vm(ScanMode.BARCODE)
        vm.onBarcode("06291234567890")
        assertThat(vm.uiState.value).isEqualTo(ScanUiState.Done(ScanSuggestion(barcode = "6291234567890")))
    }

    @Test
    fun gs1DataMatrix_alsoCarriesExpiryAndBatch_andFirstScanWins() {
        val vm = vm(ScanMode.BARCODE)
        vm.onBarcode("010629123456789017271231" + "10B42")
        vm.onBarcode("1111111111111") // later frames are ignored

        val result = (vm.uiState.value as ScanUiState.Done).result
        assertThat(result.barcode).isEqualTo("6291234567890")
        assertThat(result.expiryDate).isEqualTo(LocalDate.of(2027, 12, 31))
        assertThat(result.batchNumber).isEqualTo("B42")
    }

    @Test
    fun label_preselectsBestGuesses_userCanDropAField_thenConfirm() {
        val vm = vm(ScanMode.LABEL)
        vm.onLabelCaptureStarted()
        assertThat(vm.uiState.value).isEqualTo(ScanUiState.Reading)

        vm.onLabelRead(listOf(RecognizedLine("BRUFEN", 80), RecognizedLine("Ibuprofen 400 mg", 30), RecognizedLine("EXP 06/2027", 20)))
        val review = vm.uiState.value as ScanUiState.LabelReview
        assertThat(review.selection.name).isEqualTo("Brufen")
        assertThat(review.selection.expiry).isEqualTo(LocalDate.of(2027, 6, 30))

        vm.onSelectionChange { it.copy(strength = null) }
        vm.onConfirmLabel()

        val result = (vm.uiState.value as ScanUiState.Done).result
        assertThat(result).isEqualTo(ScanSuggestion(name = "Brufen", expiryDate = LocalDate.of(2027, 6, 30)))
    }

    @Test
    fun unreadableLabel_offersRetry() {
        val vm = vm(ScanMode.LABEL)
        vm.onLabelRead(emptyList())
        assertThat(vm.uiState.value).isEqualTo(ScanUiState.NothingRead)
        vm.onRetry()
        assertThat(vm.uiState.value).isEqualTo(ScanUiState.Scanning)
        vm.onLabelFailed()
        assertThat(vm.uiState.value).isEqualTo(ScanUiState.NothingRead)
    }
}
