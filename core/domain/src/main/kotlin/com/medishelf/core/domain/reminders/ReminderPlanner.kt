package com.medishelf.core.domain.reminders

import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.ReminderSettings
import java.time.LocalDate

/** Which reminders were already sent per medicine, so each threshold fires once. */
data class ReminderLedger(val sent: Map<Long, Set<String>> = emptyMap())

data class ExpiringItem(val details: MedicineDetails, val daysLeft: Long)

data class ReminderPlan(
    val expiring: List<ExpiringItem> = emptyList(),
    val expired: List<MedicineDetails> = emptyList(),
    val lowStock: List<MedicineDetails> = emptyList(),
    val ledger: ReminderLedger = ReminderLedger(),
) {
    val isEmpty: Boolean get() = expiring.isEmpty() && expired.isEmpty() && lowStock.isEmpty()
}

interface ReminderLedgerRepository {
    suspend fun get(): ReminderLedger
    suspend fun save(ledger: ReminderLedger)
}

/**
 * Decides what the daily job should announce. Threshold-crossing rather than exact-day
 * matching, so a missed day (phone off, Doze) still produces the reminder once. Editing a
 * medicine back out of a threshold, or restocking, re-arms that reminder.
 */
object ReminderPlanner {
    private const val EXPIRED = "expired"
    private const val LOW = "low"
    private fun expKey(days: Int) = "exp$days"

    fun plan(
        medicines: List<MedicineDetails>,
        today: LocalDate,
        settings: ReminderSettings,
        ledger: ReminderLedger,
    ): ReminderPlan {
        if (!settings.enabled) return ReminderPlan(ledger = ledger)
        val expiring = mutableListOf<ExpiringItem>()
        val expired = mutableListOf<MedicineDetails>()
        val low = mutableListOf<MedicineDetails>()
        val newLedger = mutableMapOf<Long, Set<String>>()

        medicines.forEach { details ->
            val medicine = details.medicine
            val status = medicine.effectiveStatus(today)
            if (status != MedicineStatus.ACTIVE && status != MedicineStatus.EXPIRED) return@forEach
            val previous = ledger.sent[medicine.id].orEmpty()
            val keys = previous.toMutableSet()

            if (status == MedicineStatus.EXPIRED) {
                if (EXPIRED !in previous && settings.notifyExpired) expired += details
                keys += EXPIRED
            } else {
                keys -= EXPIRED
                val days = medicine.daysUntilExpiry(today)
                val crossed = settings.daysBefore.filter { days <= it }
                keys.removeAll { it.startsWith("exp") && it != EXPIRED && it.removePrefix("exp").toIntOrNull() !in crossed }
                if (crossed.any { expKey(it) !in previous }) expiring += ExpiringItem(details, days)
                keys += crossed.map(::expKey)
            }

            if (medicine.isLowStock) {
                if (LOW !in previous && settings.notifyLowStock) low += details
                keys += LOW
            } else {
                keys -= LOW
            }
            if (keys.isNotEmpty()) newLedger[medicine.id] = keys
        }
        return ReminderPlan(
            expiring = expiring.sortedBy { it.daysLeft },
            expired = expired,
            lowStock = low,
            ledger = ReminderLedger(newLedger),
        )
    }
}
