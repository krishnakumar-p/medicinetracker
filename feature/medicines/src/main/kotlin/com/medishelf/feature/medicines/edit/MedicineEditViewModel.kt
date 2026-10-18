package com.medishelf.feature.medicines.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.ConditionSuggestionRepository
import com.medishelf.core.domain.repository.DoseRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.domain.usecase.FindPrefillByBarcodeUseCase
import com.medishelf.core.domain.usecase.SaveMedicineUseCase
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.ScanSuggestion
import com.medishelf.core.model.StorageLocation
import com.medishelf.core.ui.ScanResultTransfer
import com.medishelf.core.ui.toEditableString
import com.medishelf.feature.medicines.navigation.MedicineEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fields filled by a camera scan; the editor flags them for the user to check. */
enum class ScannedField {
    BARCODE, NAME, GENERIC, STRENGTH, FORM, QUANTITY, MANUFACTURER, EXPIRY, BATCH, PRESCRIPTION, DOSAGE, NOTES,
    USE_WITHIN, PRESCRIBER, PRESCRIPTION_DATE, PURCHASE_DATE, PEOPLE, LOCATION, PRESCRIBED_FOR,
}

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
    private val doses: DoseRepository,
    private val members: FamilyMemberRepository,
    private val locations: StorageLocationRepository,
    suggestions: ConditionSuggestionRepository,
    private val findPrefill: FindPrefillByBarcodeUseCase,
    private val saveMedicine: SaveMedicineUseCase,
    private val validator: MedicineValidator,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MedicineEditRoute>()
    private val form = MutableStateFlow(MedicineFormState())
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
                medicines.get(route.id)?.let { details ->
                    form.value = MedicineFormState.from(details, doses.findSchedule(route.id, DoseScheduleKind.PRESCRIPTION))
                }
            }
            meta.update { it.copy(isLoading = false) }
        }
        // Results from a scanner opened by this editor arrive through the back stack.
        viewModelScope.launch { ScanResultTransfer.results(savedStateHandle).collect { applyScan(it) } }
    }

    /**
     * Applies a confirmed scan. Product details from a previously saved pack with the same
     * barcode fill an empty form first (the user typed those before, so they're trusted most).
     * Camera-read text then fills fields that are still empty; pack-specific values (expiry,
     * batch) overwrite, since they describe the pack in hand.
     */
    private suspend fun applyScan(scan: ScanSuggestion) {
        val prefill = scan.barcode?.let { findPrefill(it) }
        val patient = scan.patientName?.let { name -> matchMember(name, members.observeAll().first()) }
        val fridge = locations.observeAll().first().firstOrNull { it.requiresRefrigeration }
        val needsFridge = scan.storageNote?.let(::mentionsRefrigeration) == true
        val scanned = mutableSetOf<ScannedField>()
        form.update { current ->
            scanned.clear()
            var f = current
            val wasEmpty = f.brandName.isBlank() && f.genericName.isBlank()
            if (prefill != null && wasEmpty) {
                f = MedicineFormState.from(prefill).copy(
                    id = f.id,
                    quantityText = f.quantityText,
                    expiryDate = f.expiryDate,
                    batchNumber = f.batchNumber,
                    assignments = f.assignments,
                    notes = f.notes,
                )
            }
            fun fill(field: ScannedField, value: String?, isBlank: Boolean, set: (String) -> Unit) {
                if (value != null && isBlank) { set(value); scanned += field }
            }
            scan.barcode?.let { f = f.copy(barcode = it); scanned += ScannedField.BARCODE }
            fill(ScannedField.NAME, scan.name, f.brandName.isBlank()) { f = f.copy(brandName = it) }
            fill(ScannedField.GENERIC, scan.genericName, f.genericName.isBlank()) { f = f.copy(genericName = it) }
            fill(ScannedField.STRENGTH, scan.strength, f.strength.isBlank()) { f = f.copy(strength = it) }
            fill(ScannedField.MANUFACTURER, scan.manufacturer, f.manufacturer.isBlank()) { f = f.copy(manufacturer = it) }
            fill(ScannedField.DOSAGE, scan.dosageInstructions, f.dosageInstructions.isBlank()) { f = f.copy(dosageInstructions = it) }
            fill(ScannedField.NOTES, scan.storageNote, f.notes.isBlank()) { f = f.copy(notes = it) }
            // Form and unit always have a default value, so only a fresh entry takes them from the scan.
            scan.form?.takeIf { wasEmpty && prefill == null }?.let { f = f.copy(form = it); scanned += ScannedField.FORM }
            scan.quantity?.takeIf { f.quantityText.isBlank() }?.let { amount ->
                f = f.copy(quantityText = amount.toEditableString(), unit = scan.unit ?: f.unit)
                scanned += ScannedField.QUANTITY
            }
            if (scan.isPrescription == true && !f.isPrescription) { f = f.copy(isPrescription = true); scanned += ScannedField.PRESCRIPTION }
            scan.useWithinDays?.takeIf { f.useWithinText.isBlank() }?.let { f = f.copy(useWithinText = it.toString()); scanned += ScannedField.USE_WITHIN }
            fill(ScannedField.PRESCRIBER, scan.prescribedBy, f.prescribedBy.isBlank()) { f = f.copy(prescribedBy = it) }
            scan.prescriptionDate?.let { dispensed ->
                // A dispensing date is also when the pack was bought.
                if (f.prescriptionDate == null) { f = f.copy(prescriptionDate = dispensed); scanned += ScannedField.PRESCRIPTION_DATE }
                if (f.purchaseDate == null) { f = f.copy(purchaseDate = dispensed); scanned += ScannedField.PURCHASE_DATE }
            }
            // Pharmacy label: the patient and condition are who and what the prescription is for.
            if (patient != null && f.prescribedForMemberId == null) {
                f = f.copy(prescribedForMemberId = patient.id)
                scanned += ScannedField.PRESCRIBED_FOR
            }
            fill(ScannedField.PRESCRIBED_FOR, scan.condition, f.prescribedForReason.isBlank()) { f = f.copy(prescribedForReason = it) }
            // Also tick that family member, with the condition if known.
            if (patient != null && patient.id !in f.assignments) {
                f = f.copy(assignments = f.assignments + (patient.id to scan.condition.orEmpty()))
                scanned += ScannedField.PEOPLE
            } else {
                scan.condition?.let { condition ->
                    // No patient on the label: give the condition to the one ticked person without one.
                    val blank = f.assignments.filterValues(String::isBlank).keys
                    if (blank.size == 1) { f = f.copy(assignments = f.assignments + (blank.first() to condition)); scanned += ScannedField.PEOPLE }
                }
            }
            if (needsFridge && fridge != null && f.storageLocationId == null) {
                f = f.copy(storageLocationId = fridge.id)
                scanned += ScannedField.LOCATION
            }
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

/** Family member whose name matches the label: full name, or same first name. */
internal fun matchMember(labelName: String, candidates: List<FamilyMember>): FamilyMember? {
    val wanted = labelName.trim().lowercase()
    candidates.firstOrNull { it.name.trim().lowercase() == wanted }?.let { return it }
    val first = wanted.substringBefore(' ')
    return candidates.filter { it.name.trim().lowercase().substringBefore(' ') == first }.singleOrNull()
}

private fun mentionsRefrigeration(note: String): Boolean =
    Regex("""refrigerat|fridge|2\s*°?\s*c?\s*(?:-|–|to)\s*8\s*°""", RegexOption.IGNORE_CASE).containsMatchIn(note)
