package com.medishelf.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.scan.BarcodeNormalizer
import com.medishelf.core.domain.scan.Gs1Parser
import com.medishelf.core.domain.scan.LabelTextParser
import com.medishelf.core.model.LabelSuggestions
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

/** The user's pick per field on the review sheet; null = don't fill that field. */
data class LabelSelection(
    val name: String? = null,
    val strength: String? = null,
    val expiry: LocalDate? = null,
    val batch: String? = null,
)

sealed interface ScanUiState {
    data object Scanning : ScanUiState
    data object Reading : ScanUiState
    data object NothingRead : ScanUiState
    data class LabelReview(val suggestions: LabelSuggestions, val selection: LabelSelection) : ScanUiState
    data class Done(val result: ScanSuggestion) : ScanUiState
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val time: TimeProvider,
) : ViewModel() {
    val mode: ScanMode = savedStateHandle.toRoute<ScanRoute>().mode

    private val state = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val uiState: StateFlow<ScanUiState> = state.asStateFlow()

    /** Called from the analyzer thread; only the first code while scanning wins. */
    fun onBarcode(rawValue: String) {
        if (rawValue.isBlank()) return
        val result = Gs1Parser.parse(rawValue, time.today())?.let(Gs1Parser::toSuggestion)
            ?: ScanSuggestion(barcode = BarcodeNormalizer.normalize(rawValue))
        state.compareAndSet(ScanUiState.Scanning, ScanUiState.Done(result))
    }

    fun onLabelCaptureStarted() = state.update { if (it == ScanUiState.Scanning) ScanUiState.Reading else it }

    fun onLabelRead(lines: List<RecognizedLine>) {
        val suggestions = LabelTextParser.parse(lines, time.today())
        state.value = if (suggestions.isEmpty) {
            ScanUiState.NothingRead
        } else {
            // Preselect the best guess per field; the user confirms or changes it.
            ScanUiState.LabelReview(
                suggestions,
                LabelSelection(
                    name = suggestions.names.firstOrNull(),
                    strength = suggestions.strengths.firstOrNull(),
                    expiry = suggestions.expiryDates.firstOrNull(),
                    batch = suggestions.batchNumbers.firstOrNull(),
                ),
            )
        }
    }

    fun onLabelFailed() {
        state.value = ScanUiState.NothingRead
    }

    fun onSelectionChange(transform: (LabelSelection) -> LabelSelection) = state.update {
        if (it is ScanUiState.LabelReview) it.copy(selection = transform(it.selection)) else it
    }

    fun onConfirmLabel() = state.update {
        if (it !is ScanUiState.LabelReview) return@update it
        val s = it.selection
        ScanUiState.Done(ScanSuggestion(name = s.name, strength = s.strength, expiryDate = s.expiry, batchNumber = s.batch))
    }

    fun onRetry() {
        state.value = ScanUiState.Scanning
    }
}
