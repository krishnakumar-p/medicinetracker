package com.medishelf.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class MedicineTest {
    private val today = LocalDate.of(2026, 1, 15)
    private fun medicine(
        expiry: LocalDate = LocalDate.of(2026, 6, 1),
        opened: LocalDate? = null,
        useWithin: Int? = null,
        status: MedicineStatus = MedicineStatus.ACTIVE,
        quantity: Double = 10.0,
        threshold: Double? = null,
    ) = Medicine(
        brandName = "X",
        quantity = quantity,
        expiryDate = expiry,
        openedDate = opened,
        useWithinDaysAfterOpening = useWithin,
        status = status,
        lowStockThreshold = threshold,
    )

    @Test
    fun effectiveExpiry_isPrintedDate_whenNotOpened() {
        assertThat(medicine(useWithin = 30).effectiveExpiryDate).isEqualTo(LocalDate.of(2026, 6, 1))
    }

    @Test
    fun effectiveExpiry_usesOpenedDeadline_whenEarlier() {
        val m = medicine(opened = LocalDate.of(2026, 1, 1), useWithin = 28)
        assertThat(m.effectiveExpiryDate).isEqualTo(LocalDate.of(2026, 1, 29))
    }

    @Test
    fun effectiveExpiry_keepsPrintedDate_whenOpenedDeadlineIsLater() {
        val m = medicine(expiry = LocalDate.of(2026, 1, 20), opened = LocalDate.of(2026, 1, 1), useWithin = 90)
        assertThat(m.effectiveExpiryDate).isEqualTo(LocalDate.of(2026, 1, 20))
    }

    @Test
    fun expiryDayItself_isStillActive_andDayAfterIsExpired() {
        assertThat(medicine(expiry = today).effectiveStatus(today)).isEqualTo(MedicineStatus.ACTIVE)
        assertThat(medicine(expiry = today.minusDays(1)).effectiveStatus(today)).isEqualTo(MedicineStatus.EXPIRED)
    }

    @Test
    fun effectiveStatus_neverOverridesFinishedOrDisposed() {
        val past = today.minusDays(10)
        assertThat(medicine(expiry = past, status = MedicineStatus.DISPOSED).effectiveStatus(today))
            .isEqualTo(MedicineStatus.DISPOSED)
        assertThat(medicine(expiry = past, status = MedicineStatus.FINISHED).effectiveStatus(today))
            .isEqualTo(MedicineStatus.FINISHED)
    }

    @Test
    fun daysUntilExpiry_isSignedDayCount() {
        assertThat(medicine(expiry = today.plusDays(7)).daysUntilExpiry(today)).isEqualTo(7)
        assertThat(medicine(expiry = today.minusDays(2)).daysUntilExpiry(today)).isEqualTo(-2)
    }

    @Test
    fun lowStock_requiresThresholdAndActiveStatus() {
        assertThat(medicine(quantity = 2.0).isLowStock).isFalse()
        assertThat(medicine(quantity = 2.0, threshold = 2.0).isLowStock).isTrue()
        assertThat(medicine(quantity = 3.0, threshold = 2.0).isLowStock).isFalse()
        assertThat(medicine(quantity = 0.0, threshold = 2.0, status = MedicineStatus.FINISHED).isLowStock).isFalse()
    }
}
