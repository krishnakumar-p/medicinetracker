package com.medishelf.core.domain.validation

import java.time.LocalDate
import javax.inject.Inject

enum class MedicineValidationError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    EXPIRY_REQUIRED,
    QUANTITY_REQUIRED,
    QUANTITY_NEGATIVE,
    THRESHOLD_NEGATIVE,
    EXPIRY_NOT_AFTER_PURCHASE,
    PURCHASE_IN_FUTURE,
    OPENED_BEFORE_PURCHASE,
    OPENED_IN_FUTURE,
    USE_WITHIN_NOT_POSITIVE,
    PRESCRIPTION_IN_FUTURE,
}

class MedicineValidator @Inject constructor() {

    fun validate(draft: MedicineDraft, today: LocalDate): Set<MedicineValidationError> = buildSet {
        val name = draft.brandName.trim().ifEmpty { draft.genericName.trim() }
        when {
            name.isEmpty() -> add(MedicineValidationError.NAME_REQUIRED)
            name.length > MAX_NAME_LENGTH -> add(MedicineValidationError.NAME_TOO_LONG)
        }

        val quantity = draft.quantity
        when {
            quantity == null || quantity.isNaN() -> add(MedicineValidationError.QUANTITY_REQUIRED)
            quantity < 0 -> add(MedicineValidationError.QUANTITY_NEGATIVE)
        }
        draft.lowStockThreshold?.let { if (it.isNaN() || it < 0) add(MedicineValidationError.THRESHOLD_NEGATIVE) }

        val expiry = draft.expiryDate
        val purchase = draft.purchaseDate
        val opened = draft.openedDate
        if (expiry == null) add(MedicineValidationError.EXPIRY_REQUIRED)
        if (expiry != null && purchase != null && !expiry.isAfter(purchase)) {
            add(MedicineValidationError.EXPIRY_NOT_AFTER_PURCHASE)
        }
        if (purchase != null && purchase.isAfter(today)) add(MedicineValidationError.PURCHASE_IN_FUTURE)
        if (opened != null && purchase != null && opened.isBefore(purchase)) {
            add(MedicineValidationError.OPENED_BEFORE_PURCHASE)
        }
        if (opened != null && opened.isAfter(today)) add(MedicineValidationError.OPENED_IN_FUTURE)
        draft.useWithinDaysAfterOpening?.let { if (it <= 0) add(MedicineValidationError.USE_WITHIN_NOT_POSITIVE) }
        draft.prescriptionDate?.let { if (it.isAfter(today)) add(MedicineValidationError.PRESCRIPTION_IN_FUTURE) }
    }

    companion object {
        const val MAX_NAME_LENGTH = 120
    }
}
