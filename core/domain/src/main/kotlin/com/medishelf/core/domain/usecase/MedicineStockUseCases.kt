package com.medishelf.core.domain.usecase

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineStatus
import javax.inject.Inject

sealed interface ReduceQuantityResult {
    data class Updated(val newQuantity: Double, val finished: Boolean) : ReduceQuantityResult
    data object InvalidAmount : ReduceQuantityResult
    data object NotAllowed : ReduceQuantityResult
    data object NotFound : ReduceQuantityResult
}

/** "Mark used": lowers quantity (never below 0) and marks the item FINISHED when it runs out. */
class ReduceQuantityUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(id: Long, amount: Double): ReduceQuantityResult {
        if (amount.isNaN() || amount <= 0) return ReduceQuantityResult.InvalidAmount
        val medicine = medicines.get(id)?.medicine ?: return ReduceQuantityResult.NotFound
        if (medicine.status != MedicineStatus.ACTIVE && medicine.status != MedicineStatus.EXPIRED) {
            return ReduceQuantityResult.NotAllowed
        }
        val newQuantity = (medicine.quantity - amount).coerceAtLeast(0.0)
        val finished = newQuantity == 0.0
        val newStatus = if (finished) MedicineStatus.FINISHED else medicine.status
        medicines.updateQuantityAndStatus(
            id = id,
            quantity = newQuantity,
            status = newStatus,
            event = MedicineEvent(
                medicineId = id,
                type = MedicineEventType.QUANTITY_USED,
                quantityDelta = newQuantity - medicine.quantity,
                fromStatus = medicine.status.takeIf { finished },
                toStatus = newStatus.takeIf { finished },
                at = time.now(),
            ),
        )
        return ReduceQuantityResult.Updated(newQuantity, finished)
    }
}

/** Manual lifecycle changes. Every change is recorded in the medicine's history. */
class UpdateMedicineStatusUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val time: TimeProvider,
) {
    suspend fun markDisposed(id: Long, note: String? = null): Boolean =
        change(id, note) { MedicineStatus.DISPOSED }

    suspend fun markFinished(id: Long): Boolean = change(id, null) { current ->
        MedicineStatus.FINISHED.takeIf {
            current == MedicineStatus.ACTIVE || current == MedicineStatus.EXPIRED
        }
    }

    /** Undo a finish/dispose. Lands on ACTIVE or EXPIRED depending on today's date. */
    suspend fun restore(id: Long): Boolean {
        val medicine = medicines.get(id)?.medicine ?: return false
        if (medicine.status != MedicineStatus.FINISHED && medicine.status != MedicineStatus.DISPOSED) return false
        val target = medicine.copy(status = MedicineStatus.ACTIVE).effectiveStatus(time.today())
        return change(id, null) { target }
    }

    private suspend fun change(
        id: Long,
        note: String?,
        target: (MedicineStatus) -> MedicineStatus?,
    ): Boolean {
        val medicine = medicines.get(id)?.medicine ?: return false
        val newStatus = target(medicine.status) ?: return false
        if (newStatus == medicine.status) return false
        medicines.updateStatus(
            id = id,
            status = newStatus,
            event = MedicineEvent(
                medicineId = id,
                type = MedicineEventType.STATUS_CHANGED,
                fromStatus = medicine.status,
                toStatus = newStatus,
                note = note?.trim()?.takeIf(String::isNotEmpty),
                at = time.now(),
            ),
        )
        return true
    }
}

/** Moves ACTIVE medicines past their effective expiry to EXPIRED. Returns how many changed. */
class ReconcileExpiredStatusesUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(): Int {
        val expired = medicines.getActiveExpiredBefore(time.today())
        val now = time.now()
        expired.forEach { medicine ->
            medicines.updateStatus(
                id = medicine.id,
                status = MedicineStatus.EXPIRED,
                event = MedicineEvent(
                    medicineId = medicine.id,
                    type = MedicineEventType.AUTO_EXPIRED,
                    fromStatus = MedicineStatus.ACTIVE,
                    toStatus = MedicineStatus.EXPIRED,
                    at = now,
                ),
            )
        }
        return expired.size
    }
}
