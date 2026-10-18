package com.medishelf.core.domain.usecase

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.domain.repository.ConditionSuggestionRepository
import com.medishelf.core.domain.repository.DoseRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.validation.MedicineDraft
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineStatus
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class SaveMedicineUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val suggestions: ConditionSuggestionRepository,
    private val validator: MedicineValidator,
    private val time: TimeProvider,
    private val doses: DoseRepository,
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

        val reason = medicine.prescribedForReason.takeIf(String::isNotEmpty)
        val assignments = draft.assignments
            .distinctBy { it.memberId }
            .map { AssignmentInput(it.memberId, it.condition?.trim()?.takeIf(String::isNotEmpty)) }
            .let { links ->
                // The person a prescription is for also takes the medicine: keep the per-person view in step.
                val forId = medicine.prescribedForMemberId
                when {
                    forId == null -> links
                    links.none { it.memberId == forId } -> links + AssignmentInput(forId, reason)
                    else -> links.map { if (it.memberId == forId && it.condition == null) it.copy(condition = reason) else it }
                }
            }

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
        syncPrescriptionSchedule(id, draft, medicine, today, now)
        return SaveResult.Saved(id)
    }

    /** The course dose lives in the dose schedule table, so the doses section sees it. */
    private suspend fun syncPrescriptionSchedule(
        id: Long,
        draft: MedicineDraft,
        medicine: Medicine,
        today: LocalDate,
        now: Instant,
    ) {
        val existing = doses.findSchedule(id, DoseScheduleKind.PRESCRIPTION)
        val amount = draft.doseAmount
        val times = draft.dosesPerDay
        if (amount == null || times == null) {
            existing?.let { doses.deleteSchedule(it.id) }
            return
        }
        val start = medicine.prescriptionStartDate ?: medicine.prescriptionDate ?: existing?.startDate ?: today
        doses.saveSchedule(
            DoseSchedule(
                id = existing?.id ?: 0,
                medicineId = id,
                memberId = medicine.prescribedForMemberId,
                kind = DoseScheduleKind.PRESCRIPTION,
                dosePerIntake = amount,
                timesPerDay = times,
                startDate = start,
                endDate = medicine.prescriptionEndDate,
                createdAt = existing?.createdAt ?: now,
            ),
        )
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
        prescribedForMemberId = prescribedForMemberId,
        prescribedForReason = prescribedForReason.trim(),
        prescriptionStartDate = prescriptionStartDate,
        prescriptionEndDate = prescriptionEndDate,
        dosageInstructions = dosageInstructions.trim(),
        storageLocationId = storageLocationId,
        isPrescription = isPrescription,
        notes = notes.trim(),
        status = existing?.status ?: MedicineStatus.ACTIVE,
    )
}
