package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.usecase.DoseResult
import com.medishelf.core.domain.usecase.ObserveDoseDayUseCase
import com.medishelf.core.domain.usecase.SaveDoseScheduleUseCase
import com.medishelf.core.domain.usecase.SaveMedicineUseCase
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.usecase.ScheduleDraft
import com.medishelf.core.domain.usecase.ScheduleError
import com.medishelf.core.domain.usecase.ScheduleState
import com.medishelf.core.domain.usecase.StopDoseScheduleUseCase
import com.medishelf.core.domain.usecase.TakeDoseUseCase
import com.medishelf.core.domain.usecase.UndoDoseUseCase
import com.medishelf.core.domain.validation.MedicineDraft
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.testing.FakeConditionSuggestionRepository
import com.medishelf.core.testing.FakeDoseRepository
import com.medishelf.core.testing.FakeFamilyMemberRepository
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DoseUseCasesTest {
    private val time = TestTimeProvider()
    private val today = time.today
    private val members = FakeFamilyMemberRepository()
    private val medicines = FakeMedicineRepository(members)
    private val doses = FakeDoseRepository(medicines)
    private val saveSchedule = SaveDoseScheduleUseCase(doses, time)
    private val take = TakeDoseUseCase(doses, medicines, time)
    private val undo = UndoDoseUseCase(doses, medicines, time)
    private val observe = ObserveDoseDayUseCase(doses, medicines, members, time)

    private suspend fun continuous(medicineId: Long, dose: Double = 1.0, times: Int = 2, memberId: Long? = null): Long =
        (saveSchedule(ScheduleDraft(medicineId = medicineId, memberId = memberId, dosePerIntake = dose, timesPerDay = times)) as SaveResult.Saved).id

    @Test
    fun takeDose_lowersStock_onlyOncePerSlot_andUndoRestores() = runTest {
        val id = medicines.put(testMedicine(quantity = 10.0))
        val schedule = continuous(id, dose = 2.0)

        assertThat(take(schedule, slot = 0)).isEqualTo(DoseResult.Taken(8.0, finished = false))
        assertThat(take(schedule, slot = 0)).isEqualTo(DoseResult.AlreadyTaken)
        assertThat(take(schedule, slot = 1)).isEqualTo(DoseResult.Taken(6.0, finished = false))
        assertThat(take(schedule, slot = 2)).isEqualTo(DoseResult.NotDue)
        assertThat(medicines.get(id)!!.medicine.quantity).isEqualTo(6.0)
        assertThat(medicines.events.value.map { it.type }).containsExactly(MedicineEventType.DOSE_TAKEN, MedicineEventType.DOSE_TAKEN)

        assertThat(undo(schedule, slot = 0)).isEqualTo(DoseResult.Undone)
        assertThat(medicines.get(id)!!.medicine.quantity).isEqualTo(8.0)
        assertThat(observe().first().due.map { it.taken }).containsExactly(false, true).inOrder()
    }

    @Test
    fun lastDose_finishesPack_andUndoBringsItBack() = runTest {
        val id = medicines.put(testMedicine(quantity = 1.0))
        val schedule = continuous(id, dose = 1.0, times = 1)

        assertThat(take(schedule, 0)).isEqualTo(DoseResult.Taken(0.0, finished = true))
        assertThat(medicines.get(id)!!.medicine.status).isEqualTo(MedicineStatus.FINISHED)
        // Finished packs have no doses due.
        assertThat(observe().first().due).isEmpty()

        undo(schedule, 0)
        assertThat(medicines.get(id)!!.medicine.status).isEqualTo(MedicineStatus.ACTIVE)
        assertThat(medicines.get(id)!!.medicine.quantity).isEqualTo(1.0)
    }

    @Test
    fun notEnoughStock_isOutOfStock_andNothingChanges() = runTest {
        val id = medicines.put(testMedicine(quantity = 0.5))
        val schedule = continuous(id, dose = 1.0, times = 1)

        assertThat(take(schedule, 0)).isEqualTo(DoseResult.OutOfStock)
        assertThat(observe().first().due.single().outOfStock).isTrue()
        assertThat(medicines.get(id)!!.medicine.quantity).isEqualTo(0.5)
    }

    @Test
    fun prescriptionCourse_dueUntilEndDate_only() = runTest {
        val save = SaveMedicineUseCase(medicines, FakeConditionSuggestionRepository(), MedicineValidator(), time, doses)
        val sara = members.save(testMember(name = "Sara"))
        val result = save(
            MedicineDraft(
                brandName = "Augmentin", quantity = 20.0, expiryDate = today.plusYears(1), isPrescription = true,
                prescribedForMemberId = sara, prescribedForReason = "Ear infection",
                prescriptionStartDate = today.minusDays(2), prescriptionEndDate = today.plusDays(4),
                doseAmount = 1.0, dosesPerDay = 2,
            ),
        ) as SaveResult.Saved

        val schedule = doses.findSchedule(result.id, DoseScheduleKind.PRESCRIPTION)!!
        assertThat(schedule.memberId).isEqualTo(sara)
        assertThat(schedule.endDate).isEqualTo(today.plusDays(4))
        // The person it's prescribed for is linked, with the reason as the condition.
        assertThat(medicines.get(result.id)!!.assignments.single().condition).isEqualTo("Ear infection")
        assertThat(medicines.get(result.id)!!.prescribedFor!!.name).isEqualTo("Sara")

        val day = observe().first()
        assertThat(day.due).hasSize(2)
        assertThat(day.due.first().member!!.name).isEqualTo("Sara")
        // 20 tablets cover 10 days, past the end date: no warning.
        assertThat(day.schedules.single().runsLow).isFalse()

        time.today = today.plusDays(5)
        assertThat(take(schedule.id, 0)).isEqualTo(DoseResult.NotDue)
        val after = observe().first()
        assertThat(after.due).isEmpty()
        assertThat(after.schedules.single().state).isEqualTo(ScheduleState.ENDED)
    }

    @Test
    fun clearingCourseDose_removesPrescriptionSchedule() = runTest {
        val save = SaveMedicineUseCase(medicines, FakeConditionSuggestionRepository(), MedicineValidator(), time, doses)
        val draft = MedicineDraft(brandName = "Augmentin", quantity = 20.0, expiryDate = today.plusYears(1), doseAmount = 1.0, dosesPerDay = 3)
        val id = (save(draft) as SaveResult.Saved).id
        assertThat(doses.findSchedule(id, DoseScheduleKind.PRESCRIPTION)!!.startDate).isEqualTo(today)

        save(draft.copy(id = id, doseAmount = null, dosesPerDay = null))
        assertThat(doses.findSchedule(id, DoseScheduleKind.PRESCRIPTION)).isNull()
    }

    @Test
    fun validation_rejectsBadDoseAndDates() = runTest {
        val validator = MedicineValidator()
        val base = MedicineDraft(brandName = "X", quantity = 1.0, expiryDate = today.plusDays(10))
        assertThat(validator.validate(base.copy(doseAmount = 1.0), today)).contains(MedicineValidationError.DOSE_INCOMPLETE)
        assertThat(validator.validate(base.copy(doseAmount = 0.0, dosesPerDay = 13), today))
            .containsAtLeast(MedicineValidationError.DOSE_NOT_POSITIVE, MedicineValidationError.DOSES_PER_DAY_INVALID)
        assertThat(validator.validate(base.copy(prescriptionStartDate = today, prescriptionEndDate = today.minusDays(1)), today))
            .contains(MedicineValidationError.PRESCRIPTION_END_BEFORE_START)

        assertThat(saveSchedule.validate(ScheduleDraft(dosePerIntake = -1.0, timesPerDay = 0, endDate = today.minusDays(1))))
            .containsExactly(ScheduleError.MEDICINE_REQUIRED, ScheduleError.DOSE_NOT_POSITIVE, ScheduleError.TIMES_PER_DAY_INVALID, ScheduleError.END_BEFORE_START)
    }

    @Test
    fun continuous_warnsWhenUnderAWeekLeft_andStopKeepsHistory() = runTest {
        val id = medicines.put(testMedicine(quantity = 10.0))
        val schedule = continuous(id, dose = 1.0, times = 2)
        take(schedule, 0)

        val overview = observe().first().schedules.single()
        assertThat(overview.daysOfStockLeft).isEqualTo(4)
        assertThat(overview.runsLow).isTrue()
        assertThat(overview.takenToday).isEqualTo(1)

        assertThat(StopDoseScheduleUseCase(doses, time)(schedule)).isTrue()
        assertThat(doses.getSchedule(schedule)!!.endDate).isEqualTo(today)
        assertThat(doses.logs.value).hasSize(1)
        time.today = today.plusDays(1)
        assertThat(observe().first().due).isEmpty()
    }
}
