package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.usecase.SaveMedicineUseCase
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.validation.DraftAssignment
import com.medishelf.core.domain.validation.MedicineDraft
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.testing.FakeConditionSuggestionRepository
import com.medishelf.core.testing.FakeFamilyMemberRepository
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testMember
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SaveMedicineUseCaseTest {
    private val time = TestTimeProvider()
    private val members = FakeFamilyMemberRepository()
    private val medicines = FakeMedicineRepository(members)
    private val suggestions = FakeConditionSuggestionRepository()
    private val save = SaveMedicineUseCase(medicines, suggestions, MedicineValidator(), time)

    private val draft = MedicineDraft(
        brandName = "  Ventolin ",
        genericName = "Salbutamol",
        quantity = 200.0,
        expiryDate = time.today.plusMonths(8),
        barcode = "  ",
    )

    @Test
    fun invalidDraft_isRejected_andNothingIsSaved() = runTest {
        val result = save(draft.copy(quantity = -1.0))

        assertThat(result).isEqualTo(SaveResult.Invalid(setOf(MedicineValidationError.QUANTITY_NEGATIVE)))
        assertThat(medicines.events.value).isEmpty()
    }

    @Test
    fun newMedicine_isTrimmed_savedActive_withCreatedEvent() = runTest {
        val id = (save(draft) as SaveResult.Saved).id

        val saved = medicines.get(id)!!.medicine
        assertThat(saved.brandName).isEqualTo("Ventolin")
        assertThat(saved.barcode).isNull()
        assertThat(saved.status).isEqualTo(MedicineStatus.ACTIVE)
        assertThat(saved.createdAt).isEqualTo(saved.updatedAt)
        assertThat(medicines.events.value.single().type).isEqualTo(MedicineEventType.CREATED)
    }

    @Test
    fun alreadyExpiredMedicine_isSavedAsExpired() = runTest {
        val id = (save(draft.copy(expiryDate = time.today.minusDays(1))) as SaveResult.Saved).id
        assertThat(medicines.get(id)!!.medicine.status).isEqualTo(MedicineStatus.EXPIRED)
    }

    @Test
    fun correctingExpiryOnEdit_movesExpiredBackToActive_andRecordsTransition() = runTest {
        val id = (save(draft.copy(expiryDate = time.today.minusDays(1))) as SaveResult.Saved).id

        save(draft.copy(id = id, expiryDate = time.today.plusYears(1)))

        assertThat(medicines.get(id)!!.medicine.status).isEqualTo(MedicineStatus.ACTIVE)
        val edit = medicines.events.value.last()
        assertThat(edit.type).isEqualTo(MedicineEventType.EDITED)
        assertThat(edit.fromStatus).isEqualTo(MedicineStatus.EXPIRED)
        assertThat(edit.toStatus).isEqualTo(MedicineStatus.ACTIVE)
    }

    @Test
    fun assignments_areDeduplicated_andConditionsBecomeSuggestions() = runTest {
        val mom = members.save(testMember(name = "Mom"))
        val dad = members.save(testMember(name = "Dad"))

        val id = (
            save(
                draft.copy(
                    assignments = listOf(
                        DraftAssignment(mom, " Asthma "),
                        DraftAssignment(mom, "duplicate"),
                        DraftAssignment(dad, ""),
                    ),
                ),
            ) as SaveResult.Saved
            ).id

        val assignments = medicines.get(id)!!.assignments
        assertThat(assignments.map { it.member.name to it.condition })
            .containsExactly("Mom" to "Asthma", "Dad" to null)
        assertThat(suggestions.recent.value).containsExactly("Asthma")
    }

    @Test(expected = IllegalStateException::class)
    fun editingUnknownMedicine_fails() = runTest {
        save(draft.copy(id = 99))
    }
}
