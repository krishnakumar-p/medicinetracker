package com.medishelf.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Medicine(
    val id: Long = 0,
    val brandName: String,
    /** Generic name / active ingredients (drug composition). */
    val genericName: String = "",
    val strength: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val manufacturer: String = "",
    val batchNumber: String = "",
    val barcode: String? = null,
    val quantity: Double,
    val unit: QuantityUnit = QuantityUnit.TABLETS,
    /** Null means no low-stock alert for this medicine. */
    val lowStockThreshold: Double? = null,
    val purchaseDate: LocalDate? = null,
    /** Printed expiry date; treated as the last day the medicine is usable. */
    val expiryDate: LocalDate,
    val openedDate: LocalDate? = null,
    val useWithinDaysAfterOpening: Int? = null,
    val prescribedBy: String = "",
    val prescriptionDate: LocalDate? = null,
    val dosageInstructions: String = "",
    val storageLocationId: Long? = null,
    val isPrescription: Boolean = false,
    val notes: String = "",
    val status: MedicineStatus = MedicineStatus.ACTIVE,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    /** Earlier of the printed expiry and the "use within X days after opening" deadline. */
    val effectiveExpiryDate: LocalDate
        get() {
            val opened = openedDate ?: return expiryDate
            val days = useWithinDaysAfterOpening ?: return expiryDate
            return minOf(expiryDate, opened.plusDays(days.toLong()))
        }

    /** Negative when already expired, 0 when expiring today. */
    fun daysUntilExpiry(today: LocalDate): Long = ChronoUnit.DAYS.between(today, effectiveExpiryDate)

    /** Status as the user should see it today, without waiting for the reconcile job. */
    fun effectiveStatus(today: LocalDate): MedicineStatus =
        if (status == MedicineStatus.ACTIVE && effectiveExpiryDate.isBefore(today)) {
            MedicineStatus.EXPIRED
        } else {
            status
        }

    val isLowStock: Boolean
        get() = status == MedicineStatus.ACTIVE &&
            lowStockThreshold != null &&
            quantity <= lowStockThreshold

    val displayName: String
        get() = brandName.ifBlank { genericName }
}
