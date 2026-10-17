package com.medishelf.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.scan.BarcodeNormalizer
import com.medishelf.core.domain.scan.Gs1Parser
import com.medishelf.core.domain.scan.LabelTextParser
import com.medishelf.core.model.LabelSuggestions
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.PackQuantity
import com.medishelf.core.model.RecognizedLine
import com.medishelf.core.model.ScanSuggestion
import com.medishelf.feature.scan.navigation.ScanMode
import com.medishelf.feature.scan.navigation.ScanRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/** The user's pick per field on the review screen; null = don't fill that field. */
data class PackSelection(
    val name: String? = null,
    val genericName: String? = null,
    val strength: String? = null,
    val form: MedicineForm? = null,
    val quantity: PackQuantity? = null,
    val manufacturer: String? = null,
    val expiry: LocalDate? = null,
    val batch: String? = null,
    val barcode: String? = null,
    val isPrescription: Boolean = false,
    val dosage: String? = null,
    val storage: String? = null,
)

sealed interface ScanUiState {
    /** Camera open. In PACK mode [sides] photos have been read so far. */
    data class Camera(
        val sides: Int = 0,
        val barcode: String? = null,
        val reading: Boolean = false,
        val lastSideEmpty: Boolean = false,
    ) : ScanUiState

    data object NothingRead : ScanUiState
    data class Review(val suggestions: LabelSuggestions, val selection: PackSelection, val sides: Int) : ScanUiState
    data class Done(val result: ScanSuggestion) : ScanUiState
}

/** What one still photo yielded: OCR lines and any barcodes in the same frame. */
data class SideReading(val lines: List<RecognizedLine>, val barcodes: List<String>)

@HiltViewModel
class ScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val time: TimeProvider,
) : ViewModel() {
    val mode: ScanMode = savedStateHandle.toRoute<ScanRoute>().mode

    private val state = MutableStateFlow<ScanUiState>(ScanUiState.Camera())
    val uiState: StateFlow<ScanUiState> = state.asStateFlow()

    private val lines = mutableListOf<RecognizedLine>()
    private val barcodes = linkedSetOf<String>()

    /** Live analyzer callback (any thread). */
    fun onBarcode(rawValue: String) {
        if (rawValue.isBlank()) return
        if (mode == ScanMode.BARCODE) {
            state.compareAndSet(ScanUiState.Camera(), ScanUiState.Done(barcodeSuggestion(rawValue)))
            return
        }
        synchronized(barcodes) { barcodes += rawValue }
        state.update { if (it is ScanUiState.Camera && it.barcode == null) it.copy(barcode = displayBarcode(rawValue)) else it }
    }

    fun onSideCaptureStarted() = state.update {
        if (it is ScanUiState.Camera && !it.reading) it.copy(reading = true, lastSideEmpty = false) else it
    }

    fun onSideRead(reading: SideReading) {
        lines += reading.lines
        synchronized(barcodes) { barcodes += reading.barcodes }
        state.update {
            val camera = it as? ScanUiState.Camera ?: return@update it
            camera.copy(
                sides = camera.sides + if (reading.lines.isEmpty() && reading.barcodes.isEmpty()) 0 else 1,
                reading = false,
                lastSideEmpty = reading.lines.isEmpty() && reading.barcodes.isEmpty(),
                barcode = camera.barcode ?: reading.barcodes.firstOrNull()?.let(::displayBarcode),
            )
        }
    }

    fun onSideFailed() = state.update {
        if (it is ScanUiState.Camera) it.copy(reading = false, lastSideEmpty = true) else it
    }

    /** All sides photographed: merge OCR from every side with the barcode(s) into one review. */
    fun onFinishPack() {
        val sides = (state.value as? ScanUiState.Camera)?.sides ?: return
        val today = time.today()
        var suggestions = LabelTextParser.parse(lines, today)
        val raw = synchronized(barcodes) { barcodes.toList() }
        // GS1 DataMatrix is machine-printed data: its expiry and batch beat OCR guesses.
        val gs1 = raw.firstNotNullOfOrNull { Gs1Parser.parse(it, today) }
        suggestions = suggestions.copy(
            barcode = gs1?.gtin?.let(BarcodeNormalizer::normalize) ?: raw.firstOrNull()?.let(BarcodeNormalizer::normalize),
            expiryDates = (listOfNotNull(gs1?.expiry) + suggestions.expiryDates).distinct(),
            batchNumbers = (listOfNotNull(gs1?.batch) + suggestions.batchNumbers).distinct(),
        )
        state.value = if (suggestions.isEmpty) {
            ScanUiState.NothingRead
        } else {
            ScanUiState.Review(suggestions, suggestions.bestGuess(), sides)
        }
    }

    fun onSelectionChange(transform: (PackSelection) -> PackSelection) = state.update {
        if (it is ScanUiState.Review) it.copy(selection = transform(it.selection)) else it
    }

    fun onConfirm() = state.update {
        if (it !is ScanUiState.Review) return@update it
        val s = it.selection
        ScanUiState.Done(
            ScanSuggestion(
                barcode = s.barcode,
                name = s.name,
                genericName = s.genericName,
                strength = s.strength,
                form = s.form,
                quantity = s.quantity?.amount,
                unit = s.quantity?.unit,
                manufacturer = s.manufacturer,
                expiryDate = s.expiry,
                batchNumber = s.batch,
                isPrescription = true.takeIf { s.isPrescription },
                dosageInstructions = s.dosage,
                storageNote = s.storage,
            ),
        )
    }

    /** Start over: forget every side read so far. */
    fun onRetry() {
        lines.clear()
        synchronized(barcodes) { barcodes.clear() }
        state.value = ScanUiState.Camera()
    }

    private fun barcodeSuggestion(raw: String): ScanSuggestion =
        Gs1Parser.parse(raw, time.today())?.let(Gs1Parser::toSuggestion)
            ?: ScanSuggestion(barcode = BarcodeNormalizer.normalize(raw))

    private fun displayBarcode(raw: String): String =
        Gs1Parser.parse(raw, time.today())?.gtin?.let(BarcodeNormalizer::normalize) ?: BarcodeNormalizer.normalize(raw)
}

/** Preselect the best guess per field; the user confirms or changes each one. */
private fun LabelSuggestions.bestGuess() = PackSelection(
    name = names.firstOrNull(),
    genericName = genericNames.firstOrNull(),
    strength = strengths.firstOrNull(),
    form = forms.firstOrNull(),
    quantity = quantities.firstOrNull(),
    manufacturer = manufacturers.firstOrNull(),
    expiry = expiryDates.firstOrNull(),
    batch = batchNumbers.firstOrNull(),
    barcode = barcode,
    isPrescription = isPrescription,
    dosage = dosageInstructions.firstOrNull(),
    storage = storageNotes.firstOrNull(),
)
