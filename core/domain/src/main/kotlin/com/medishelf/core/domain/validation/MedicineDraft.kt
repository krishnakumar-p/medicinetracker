package com.medishelf.core.domain.validation

import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.QuantityUnit
import java.time.LocalDate

/** Raw editor input. Nullable where the form may still be incomplete. */
data class MedicineDraft(
    val id: Long = 0,
    val brandName: String = "",
    val genericName: String = "",
    val strength: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val manufacturer: String = "",
    val batchNumber: String = "",
    val barcode: String? = null,
    val quantity: Double? = null,
    val unit: QuantityUnit = QuantityUnit.TABLETS,
    val lowStockThreshold: Double? = null,
    val purchaseDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val openedDate: LocalDate? = null,
    val useWithinDaysAfterOpening: Int? = null,
    val prescribedBy: String = "",
    val prescriptionDate: LocalDate? = null,
    val prescribedForMemberId: Long? = null,
    val prescribedForReason: String = "",
    val prescriptionStartDate: LocalDate? = null,
    val prescriptionEndDate: LocalDate? = null,
    /** Dose of the prescription course; with [dosesPerDay], makes doses due until the end date. */
    val doseAmount: Double? = null,
    val dosesPerDay: Int? = null,
    val dosageInstructions: String = "",
    val storageLocationId: Long? = null,
    val isPrescription: Boolean = false,
    val notes: String = "",
    val assignments: List<DraftAssignment> = emptyList(),
)

data class DraftAssignment(val memberId: Long, val condition: String? = null)
