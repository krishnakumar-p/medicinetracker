package com.medishelf.feature.medicines.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.ConditionSuggestionRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.domain.usecase.FindPrefillByBarcodeUseCase
import com.medishelf.core.domain.usecase.SaveMedicineUseCase
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.ScanSuggestion
import com.medishelf.core.model.StorageLocation
import com.medishelf.core.ui.ScanResultTransfer
import com.medishelf.feature.medicines.navigation.MedicineEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Fields filled by a camera scan; the editor flags them for the user to check. */
enum class ScannedField { BARCODE, NAME, STRENGTH, EXPIRY, BATCH }

data class MedicineEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val prefilledFromBarcode: Boolean = false,
    val scannedFields: Set<ScannedField> = emptySet(),
    val form: MedicineFormState = MedicineFormState(),
    val errors: Set<MedicineValidationError> = emptySet(),
    val members: List<FamilyMember> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val conditionSuggestions: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val savedId: Long? = null,
)

private data class EditMeta(
    val isLoading: Boolean,
    val prefilled: Boolean = false,
    val scannedFields: Set<ScannedField> = emptySet(),
    val submitted: Boolean = false,
    val isSaving: Boolean = false,
    val savedId: Long? = null,
)

@HiltViewModel
class MedicineEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val medicines: MedicineRepository,
    members: FamilyMemberRepository,
    locations: StorageLocationRepository,
    suggestions: ConditionSuggestionRepository,
    private val findPrefill: FindPrefillByBarcodeUseCase,
    private val saveMedicine: SaveMedicineUseCase,
    private val validator: MedicineValidator,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MedicineEditRoute>()
    private val form = MutableStateFlow(MedicineFormState(barcode = route.barcode.orEmpty()))
    private val meta = MutableStateFlow(EditMeta(isLoading = true))

    val uiState: StateFlow<MedicineEditUiState> = combine(
        form,
        meta,
        members.observeAll(),
        locations.observeAll(),
        suggestions.observeRecent(),
    ) { form, meta, members, locations, suggestions ->
        MedicineEditUiState(
            isLoading = meta.isLoading,
            isNew = route.id == 0L,
            prefilledFromBarcode = meta.prefilled,
            scannedFields = meta.scannedFields,
            form = form,
            // Errors appear only after the first save attempt, then update live.
            errors = if (meta.submitted) validator.validate(form.toDraft(), time.today()) else emptySet(),
            members = members,
            locations = locations,
            conditionSuggestions = suggestions,
            isSaving = meta.isSaving,
            savedId = meta.savedId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicineEditUiState())

    init {
        viewModelScope.launch {
            if (route.id != 0L) {
                medicines.get(route.id)?.let { form.value = MedicineFormState.from(it) }
            }
            route.scanSuggestion()?.let { applyScan(it) }
            meta.update { it.copy(isLoading = false) }
        }
        // Results from a scanner opened by this editor arrive through the back stack.
        viewModelScope.launch { ScanResultTransfer.results(savedStateHandle).collect { applyScan(it) } }
    }

    /**
     * Product fields from a known barcode fill only an empty form; pack-specific values read
     * by the camera (expiry, batch) overwrite, since they describe the pack in hand.
     */
    private suspend fun applyScan(scan: ScanSuggestion) {
        val prefill = scan.barcode?.let { findPrefill(it) }
        val scanned = mutableSetOf<ScannedField>()
        form.update { current ->
            scanned.clear()
            var f = current
            if (prefill != null && f.brandName.isBlank()) {
                f = MedicineFormState.from(prefill).copy(
                    id = f.id,
                    quantityText = f.quantityText,
                    expiryDate = f.expiryDate,
                    batchNumber = f.batchNumber,
                    assignments = f.assignments,
                    notes = f.notes,
                )
            }
            scan.barcode?.let { f = f.copy(barcode = it); scanned += ScannedField.BARCODE }
            scan.name?.takeIf { f.brandName.isBlank() }?.let { f = f.copy(brandName = it); scanned += ScannedField.NAME }
            scan.strength?.takeIf { f.strength.isBlank() }?.let { f = f.copy(strength = it); scanned += ScannedField.STRENGTH }
            scan.expiryDate?.let { f = f.copy(expiryDate = it); scanned += ScannedField.EXPIRY }
            scan.batchNumber?.let { f = f.copy(batchNumber = it); scanned += ScannedField.BATCH }
            f
        }
        meta.update { it.copy(prefilled = it.prefilled || prefill != null, scannedFields = it.scannedFields + scanned) }
    }

    fun onFormChange(transform: (MedicineFormState) -> MedicineFormState) = form.update(transform)

    fun onToggleMember(memberId: Long) = form.update {
        it.copy(assignments = if (memberId in it.assignments) it.assignments - memberId else it.assignments + (memberId to ""))
    }

    fun onConditionChange(memberId: Long, condition: String) = form.update {
        it.copy(assignments = it.assignments + (memberId to condition))
    }

    fun onSave() {
        if (meta.value.isSaving) return
        meta.update { it.copy(submitted = true, isSaving = true) }
        viewModelScope.launch {
            val result = saveMedicine(form.value.toDraft())
            meta.update {
                it.copy(isSaving = false, savedId = (result as? SaveResult.Saved)?.id)
            }
        }
    }
}

private fun MedicineEditRoute.scanSuggestion(): ScanSuggestion? =
    ScanSuggestion(
        barcode = barcode?.takeIf(String::isNotBlank),
        name = name?.takeIf(String::isNotBlank),
        strength = strength?.takeIf(String::isNotBlank),
        expiryDate = expiry?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        batchNumber = batch?.takeIf(String::isNotBlank),
    ).takeUnless { it.isEmpty }
