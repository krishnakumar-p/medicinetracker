package com.medishelf.feature.medicines.edit

import com.medishelf.core.domain.validation.DraftAssignment
import com.medishelf.core.domain.validation.MedicineDraft
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.ui.parseLocalizedInt
import com.medishelf.core.ui.parseLocalizedNumber
import com.medishelf.core.ui.toEditableString
import java.time.LocalDate

/** Editor state. Numbers stay as text so partial input ("1.") is never lost while typing. */
data class MedicineFormState(
    val id: Long = 0,
    val brandName: String = "",
    val genericName: String = "",
    val strength: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val manufacturer: String = "",
    val batchNumber: String = "",
    val barcode: String = "",
    val quantityText: String = "",
    val unit: QuantityUnit = QuantityUnit.TABLETS,
    val thresholdText: String = "",
    val purchaseDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val openedDate: LocalDate? = null,
    val useWithinText: String = "",
    val isPrescription: Boolean = false,
    val prescribedBy: String = "",
    val prescriptionDate: LocalDate? = null,
    val prescribedForMemberId: Long? = null,
    val prescribedForReason: String = "",
    val prescriptionStartDate: LocalDate? = null,
    val prescriptionEndDate: LocalDate? = null,
    val doseAmountText: String = "",
    val dosesPerDayText: String = "",
    val dosageInstructions: String = "",
    val storageLocationId: Long? = null,
    val notes: String = "",
    /** memberId -> condition, in the order members were ticked. */
    val assignments: Map<Long, String> = emptyMap(),
) {
    /** Unparseable numbers become values the validator rejects, so they surface as field errors. */
    fun toDraft() = MedicineDraft(
        id = id,
        brandName = brandName,
        genericName = genericName,
        strength = strength,
        form = form,
        manufacturer = manufacturer,
        batchNumber = batchNumber,
        barcode = barcode,
        quantity = parseLocalizedNumber(quantityText),
        unit = unit,
        lowStockThreshold = thresholdText.takeIf(String::isNotBlank)?.let { parseLocalizedNumber(it) ?: -1.0 },
        purchaseDate = purchaseDate,
        expiryDate = expiryDate,
        openedDate = openedDate,
        useWithinDaysAfterOpening = useWithinText.takeIf(String::isNotBlank)?.let { parseLocalizedInt(it) ?: 0 },
        prescribedBy = prescribedBy,
        prescriptionDate = prescriptionDate,
        prescribedForMemberId = prescribedForMemberId,
        prescribedForReason = prescribedForReason,
        prescriptionStartDate = prescriptionStartDate,
        prescriptionEndDate = prescriptionEndDate,
        doseAmount = doseAmountText.takeIf(String::isNotBlank)?.let { parseLocalizedNumber(it) ?: -1.0 },
        dosesPerDay = dosesPerDayText.takeIf(String::isNotBlank)?.let { parseLocalizedInt(it) ?: 0 },
        dosageInstructions = dosageInstructions,
        storageLocationId = storageLocationId,
        isPrescription = isPrescription,
        notes = notes,
        assignments = assignments.map { (memberId, condition) -> DraftAssignment(memberId, condition) },
    )

    companion object {
        /** [schedule] is the medicine's prescription-course dose, if any. */
        fun from(details: MedicineDetails, schedule: DoseSchedule? = null): MedicineFormState = with(details.medicine) {
            MedicineFormState(
                id = id,
                brandName = brandName,
                genericName = genericName,
                strength = strength,
                form = form,
                manufacturer = manufacturer,
                batchNumber = batchNumber,
                barcode = barcode.orEmpty(),
                quantityText = quantity.toEditableString(),
                unit = unit,
                thresholdText = lowStockThreshold?.toEditableString().orEmpty(),
                purchaseDate = purchaseDate,
                expiryDate = expiryDate,
                openedDate = openedDate,
                useWithinText = useWithinDaysAfterOpening?.toString().orEmpty(),
                isPrescription = isPrescription,
                prescribedBy = prescribedBy,
                prescriptionDate = prescriptionDate,
                prescribedForMemberId = prescribedForMemberId,
                prescribedForReason = prescribedForReason,
                prescriptionStartDate = prescriptionStartDate,
                prescriptionEndDate = prescriptionEndDate,
                doseAmountText = schedule?.dosePerIntake?.toEditableString().orEmpty(),
                dosesPerDayText = schedule?.timesPerDay?.toString().orEmpty(),
                dosageInstructions = dosageInstructions,
                storageLocationId = storageLocationId,
                notes = notes,
                assignments = details.assignments.associate { it.member.id to it.condition.orEmpty() },
            )
        }

        fun from(draft: MedicineDraft): MedicineFormState = MedicineFormState(
            brandName = draft.brandName,
            genericName = draft.genericName,
            strength = draft.strength,
            form = draft.form,
            manufacturer = draft.manufacturer,
            barcode = draft.barcode.orEmpty(),
            unit = draft.unit,
            thresholdText = draft.lowStockThreshold?.toEditableString().orEmpty(),
            isPrescription = draft.isPrescription,
            storageLocationId = draft.storageLocationId,
        )
    }
}
