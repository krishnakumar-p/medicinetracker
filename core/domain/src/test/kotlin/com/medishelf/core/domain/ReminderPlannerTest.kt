package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.reminders.ReminderLedger
import com.medishelf.core.domain.reminders.ReminderPlanner
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.ReminderSettings
import com.medishelf.core.testing.testMedicine
import org.junit.Test
import java.time.LocalDate

class ReminderPlannerTest {
    private val today = LocalDate.of(2026, 1, 15)
    private val settings = ReminderSettings()
    private fun item(id: Long, expiresIn: Long, qty: Double = 10.0, threshold: Double? = null, status: MedicineStatus = MedicineStatus.ACTIVE) =
        MedicineDetails(testMedicine(id = id, expiryDate = today.plusDays(expiresIn), quantity = qty, lowStockThreshold = threshold, status = status))

    @Test
    fun eachThresholdFiresOnce_andMissedDaysStillFire() {
        val meds = listOf(item(1, 29))
        val first = ReminderPlanner.plan(meds, today, settings, ReminderLedger())
        assertThat(first.expiring.map { it.details.medicine.id to it.daysLeft }).containsExactly(1L to 29L)

        val sameDay = ReminderPlanner.plan(meds, today, settings, first.ledger)
        assertThat(sameDay.isEmpty).isTrue()

        // Phone was off for a week: 29 -> 6 days crosses the 7-day threshold once.
        val later = ReminderPlanner.plan(meds, today.plusDays(23), settings, first.ledger)
        assertThat(later.expiring.single().daysLeft).isEqualTo(6)
        assertThat(ReminderPlanner.plan(meds, today.plusDays(23), settings, later.ledger).isEmpty).isTrue()
    }

    @Test
    fun firstRunInsideSeveralThresholds_sendsOneReminderNotThree() {
        val plan = ReminderPlanner.plan(listOf(item(1, 0)), today, settings, ReminderLedger())
        assertThat(plan.expiring).hasSize(1)
        assertThat(plan.ledger.sent.getValue(1)).containsExactly("exp30", "exp7", "exp1")
    }

    @Test
    fun expiredAndLowStock_onceUntilReArmed() {
        val meds = listOf(item(1, -1), item(2, 300, qty = 1.0, threshold = 2.0))
        val first = ReminderPlanner.plan(meds, today, settings, ReminderLedger())
        assertThat(first.expired.map { it.medicine.id }).containsExactly(1L)
        assertThat(first.lowStock.map { it.medicine.id }).containsExactly(2L)
        assertThat(ReminderPlanner.plan(meds, today, settings, first.ledger).isEmpty).isTrue()

        // Restocked, then runs low again: reminds again.
        val restocked = ReminderPlanner.plan(listOf(item(2, 300, qty = 10.0, threshold = 2.0)), today, settings, first.ledger)
        val lowAgain = ReminderPlanner.plan(listOf(item(2, 300, qty = 1.0, threshold = 2.0)), today, settings, restocked.ledger)
        assertThat(lowAgain.lowStock).hasSize(1)
    }

    @Test
    fun correctedExpiry_reArmsThresholds() {
        val first = ReminderPlanner.plan(listOf(item(1, 5)), today, settings, ReminderLedger())
        val corrected = ReminderPlanner.plan(listOf(item(1, 400)), today, settings, first.ledger)
        assertThat(corrected.ledger.sent[1]).isNull()
    }

    @Test
    fun respectsToggles_andIgnoresClosedItems() {
        val meds = listOf(
            item(1, -1),
            item(2, 3, qty = 0.0, threshold = 1.0),
            item(3, 2, status = MedicineStatus.DISPOSED),
            item(4, 2, status = MedicineStatus.FINISHED),
        )
        val quiet = settings.copy(notifyExpired = false, notifyLowStock = false)
        val plan = ReminderPlanner.plan(meds, today, quiet, ReminderLedger(mapOf(3L to setOf("exp7"))))
        assertThat(plan.expired).isEmpty()
        assertThat(plan.lowStock).isEmpty()
        assertThat(plan.expiring.map { it.details.medicine.id }).containsExactly(2L)
        assertThat(plan.ledger.sent.keys).containsExactly(1L, 2L) // stale entry for 3 dropped

        assertThat(ReminderPlanner.plan(meds, today, settings.copy(enabled = false), ReminderLedger()).isEmpty).isTrue()
    }
}
