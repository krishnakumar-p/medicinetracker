package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.validation.MedicineDraft
import com.medishelf.core.domain.validation.MedicineValidationError.EXPIRY_NOT_AFTER_PURCHASE
import com.medishelf.core.domain.validation.MedicineValidationError.EXPIRY_REQUIRED
import com.medishelf.core.domain.validation.MedicineValidationError.NAME_REQUIRED
import com.medishelf.core.domain.validation.MedicineValidationError.NAME_TOO_LONG
import com.medishelf.core.domain.validation.MedicineValidationError.OPENED_BEFORE_PURCHASE
import com.medishelf.core.domain.validation.MedicineValidationError.OPENED_IN_FUTURE
import com.medishelf.core.domain.validation.MedicineValidationError.PRESCRIPTION_IN_FUTURE
import com.medishelf.core.domain.validation.MedicineValidationError.PURCHASE_IN_FUTURE
import com.medishelf.core.domain.validation.MedicineValidationError.QUANTITY_NEGATIVE
import com.medishelf.core.domain.validation.MedicineValidationError.QUANTITY_REQUIRED
import com.medishelf.core.domain.validation.MedicineValidationError.THRESHOLD_NEGATIVE
import com.medishelf.core.domain.validation.MedicineValidationError.USE_WITHIN_NOT_POSITIVE
import com.medishelf.core.domain.validation.MedicineValidator
import org.junit.Test
import java.time.LocalDate

class MedicineValidatorTest {
    private val validator = MedicineValidator()
    private val today = LocalDate.of(2026, 1, 15)
    private val valid = MedicineDraft(
        brandName = "Panadol",
        quantity = 10.0,
        purchaseDate = today.minusDays(5),
        expiryDate = today.plusYears(1),
    )

    private fun errors(draft: MedicineDraft) = validator.validate(draft, today)

    @Test
    fun validDraft_hasNoErrors() {
        assertThat(errors(valid)).isEmpty()
    }

    @Test
    fun genericNameAlone_satisfiesNameRequirement() {
        assertThat(errors(valid.copy(brandName = " ", genericName = "Ibuprofen"))).isEmpty()
        assertThat(errors(valid.copy(brandName = " ", genericName = ""))).containsExactly(NAME_REQUIRED)
        assertThat(errors(valid.copy(brandName = "x".repeat(121)))).containsExactly(NAME_TOO_LONG)
    }

    @Test
    fun quantity_mustBePresentAndNonNegative() {
        assertThat(errors(valid.copy(quantity = null))).containsExactly(QUANTITY_REQUIRED)
        assertThat(errors(valid.copy(quantity = Double.NaN))).containsExactly(QUANTITY_REQUIRED)
        assertThat(errors(valid.copy(quantity = -1.0))).containsExactly(QUANTITY_NEGATIVE)
        assertThat(errors(valid.copy(quantity = 0.0))).isEmpty()
        assertThat(errors(valid.copy(lowStockThreshold = -0.5))).containsExactly(THRESHOLD_NEGATIVE)
    }

    @Test
    fun expiry_isRequired_andMustBeAfterPurchase() {
        assertThat(errors(valid.copy(expiryDate = null))).containsExactly(EXPIRY_REQUIRED)
        assertThat(errors(valid.copy(expiryDate = valid.purchaseDate))).containsExactly(EXPIRY_NOT_AFTER_PURCHASE)
        assertThat(errors(valid.copy(expiryDate = valid.purchaseDate!!.minusDays(1))))
            .containsExactly(EXPIRY_NOT_AFTER_PURCHASE)
    }

    @Test
    fun alreadyExpiredMedicine_canStillBeRecorded() {
        assertThat(errors(valid.copy(purchaseDate = null, expiryDate = today.minusMonths(2)))).isEmpty()
    }

    @Test
    fun dates_cannotBeInTheFuture() {
        assertThat(errors(valid.copy(purchaseDate = today.plusDays(1)))).contains(PURCHASE_IN_FUTURE)
        assertThat(errors(valid.copy(openedDate = today.plusDays(1)))).containsExactly(OPENED_IN_FUTURE)
        assertThat(errors(valid.copy(prescriptionDate = today.plusDays(1)))).containsExactly(PRESCRIPTION_IN_FUTURE)
    }

    @Test
    fun opened_cannotPrecedePurchase() {
        assertThat(errors(valid.copy(openedDate = valid.purchaseDate!!.minusDays(1))))
            .containsExactly(OPENED_BEFORE_PURCHASE)
    }

    @Test
    fun useWithinDays_mustBePositive() {
        assertThat(errors(valid.copy(useWithinDaysAfterOpening = 0))).containsExactly(USE_WITHIN_NOT_POSITIVE)
        assertThat(errors(valid.copy(useWithinDaysAfterOpening = 28))).isEmpty()
    }

    @Test
    fun multipleProblems_areAllReported() {
        assertThat(errors(MedicineDraft())).containsExactly(NAME_REQUIRED, QUANTITY_REQUIRED, EXPIRY_REQUIRED)
    }
}
