package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.usecase.ReconcileExpiredStatusesUseCase
import com.medishelf.core.domain.usecase.ReduceQuantityResult
import com.medishelf.core.domain.usecase.ReduceQuantityUseCase
import com.medishelf.core.domain.usecase.UpdateMedicineStatusUseCase
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testMedicine
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MedicineStockUseCasesTest {
    private val time = TestTimeProvider()
    private val medicines = FakeMedicineRepository()
    private val reduce = ReduceQuantityUseCase(medicines, time)
    private val status = UpdateMedicineStatusUseCase(medicines, time)
    private val reconcile = ReconcileExpiredStatusesUseCase(medicines, time)

    @Test
    fun reduce_lowersQuantity_andRecordsDelta() = runTest {
        val id = medicines.put(testMedicine(quantity = 10.0))

        assertThat(reduce(id, 2.0)).isEqualTo(ReduceQuantityResult.Updated(8.0, finished = false))
        assertThat(medicines.events.value.single().quantityDelta).isEqualTo(-2.0)
    }

    @Test
    fun reduce_clampsAtZero_andMarksFinished() = runTest {
        val id = medicines.put(testMedicine(quantity = 1.0))

        assertThat(reduce(id, 5.0)).isEqualTo(ReduceQuantityResult.Updated(0.0, finished = true))
        val medicine = medicines.get(id)!!.medicine
        assertThat(medicine.status).isEqualTo(MedicineStatus.FINISHED)
        assertThat(medicines.events.value.single().quantityDelta).isEqualTo(-1.0)
    }

    @Test
    fun reduce_rejectsBadInput_andClosedItems() = runTest {
        val id = medicines.put(testMedicine())
        val disposed = medicines.put(testMedicine(status = MedicineStatus.DISPOSED))

        assertThat(reduce(id, 0.0)).isEqualTo(ReduceQuantityResult.InvalidAmount)
        assertThat(reduce(id, Double.NaN)).isEqualTo(ReduceQuantityResult.InvalidAmount)
        assertThat(reduce(99, 1.0)).isEqualTo(ReduceQuantityResult.NotFound)
        assertThat(reduce(disposed, 1.0)).isEqualTo(ReduceQuantityResult.NotAllowed)
    }

    @Test
    fun dispose_keepsRecord_withNoteInHistory() = runTest {
        val id = medicines.put(testMedicine())

        assertThat(status.markDisposed(id, " pharmacy take-back ")).isTrue()

        assertThat(medicines.get(id)!!.medicine.status).isEqualTo(MedicineStatus.DISPOSED)
        val event = medicines.events.value.single()
        assertThat(event.fromStatus).isEqualTo(MedicineStatus.ACTIVE)
        assertThat(event.note).isEqualTo("pharmacy take-back")
        assertThat(status.markDisposed(id)).isFalse()
    }

    @Test
    fun finish_onlyFromInCabinetStates() = runTest {
        val id = medicines.put(testMedicine(status = MedicineStatus.DISPOSED))
        assertThat(status.markFinished(id)).isFalse()
    }

    @Test
    fun restore_landsOnExpired_whenDatePassed() = runTest {
        val id = medicines.put(testMedicine(status = MedicineStatus.FINISHED, expiryDate = time.today.minusDays(3)))

        assertThat(status.restore(id)).isTrue()
        assertThat(medicines.get(id)!!.medicine.status).isEqualTo(MedicineStatus.EXPIRED)
    }

    @Test
    fun reconcile_expiresOnlyActivePastEffectiveExpiry() = runTest {
        val printedPast = medicines.put(testMedicine(expiryDate = time.today.minusDays(1)))
        val openedPast = medicines.put(
            testMedicine(openedDate = time.today.minusDays(40), useWithinDaysAfterOpening = 28),
        )
        val fine = medicines.put(testMedicine(expiryDate = time.today))
        val finished = medicines.put(testMedicine(status = MedicineStatus.FINISHED, expiryDate = time.today.minusDays(9)))

        assertThat(reconcile()).isEqualTo(2)

        assertThat(medicines.get(printedPast)!!.medicine.status).isEqualTo(MedicineStatus.EXPIRED)
        assertThat(medicines.get(openedPast)!!.medicine.status).isEqualTo(MedicineStatus.EXPIRED)
        assertThat(medicines.get(fine)!!.medicine.status).isEqualTo(MedicineStatus.ACTIVE)
        assertThat(medicines.get(finished)!!.medicine.status).isEqualTo(MedicineStatus.FINISHED)
        assertThat(medicines.events.value.map { it.type }.distinct()).containsExactly(MedicineEventType.AUTO_EXPIRED)
        assertThat(reconcile()).isEqualTo(0)
    }
}
