package com.medishelf.core.domain.usecase

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.domain.repository.ConditionSuggestionRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.validation.MedicineDraft
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineStatus
import javax.inject.Inject

class SaveMedicineUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val suggestions: ConditionSuggestionRepository,
    private val validator: MedicineValidator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(draft: MedicineDraft): SaveResult<MedicineValidationError> {
        val today = time.today()
        val errors = validator.validate(draft, today)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val existing = if (draft.id != 0L) {
            checkNotNull(medicines.get(draft.id)) { "Medicine ${draft.id} not found" }.medicine
        } else {
            null
        }
        val now = time.now()
        val base = draft.toMedicine(existing).copy(updatedAt = now, createdAt = existing?.createdAt ?: now)
        // Editing dates can move an item in or out of EXPIRED; FINISHED/DISPOSED stay as they are.
        val status = when (base.status) {
            MedicineStatus.ACTIVE, MedicineStatus.EXPIRED ->
                if (base.effectiveExpiryDate.isBefore(today)) MedicineStatus.EXPIRED else MedicineStatus.ACTIVE
            else -> base.status
        }
        val medicine = base.copy(status = status)

        val assignments = draft.assignments
            .distinctBy { it.memberId }
            .map { AssignmentInput(it.memberId, it.condition?.trim()?.takeIf(String::isNotEmpty)) }

        val statusChanged = existing != null && existing.status != status
        val event = MedicineEvent(
            medicineId = draft.id,
            type = if (existing == null) MedicineEventType.CREATED else MedicineEventType.EDITED,
            fromStatus = existing?.status?.takeIf { statusChanged },
            toStatus = status.takeIf { existing == null || statusChanged },
            at = now,
        )
        val id = medicines.save(medicine, assignments, event)
        suggestions.recordUsed(assignments.mapNotNull { it.condition })
        return SaveResult.Saved(id)
    }

    private fun MedicineDraft.toMedicine(existing: Medicine?) = Medicine(
        id = id,
        brandName = brandName.trim(),
        genericName = genericName.trim(),
        strength = strength.trim(),
        form = form,
        manufacturer = manufacturer.trim(),
        batchNumber = batchNumber.trim(),
        barcode = barcode?.trim()?.takeIf(String::isNotEmpty),
        quantity = requireNotNull(quantity),
        unit = unit,
        lowStockThreshold = lowStockThreshold,
        purchaseDate = purchaseDate,
        expiryDate = requireNotNull(expiryDate),
        openedDate = openedDate,
        useWithinDaysAfterOpening = useWithinDaysAfterOpening,
        prescribedBy = prescribedBy.trim(),
        prescriptionDate = prescriptionDate,
        dosageInstructions = dosageInstructions.trim(),
        storageLocationId = storageLocationId,
        isPrescription = isPrescription,
        notes = notes.trim(),
        status = existing?.status ?: MedicineStatus.ACTIVE,
    )
}
