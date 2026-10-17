package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.domain.usecase.FindPrefillByBarcodeUseCase
import com.medishelf.core.domain.usecase.ObserveDashboardCountsUseCase
import com.medishelf.core.domain.usecase.ObserveMedicinesByLocationUseCase
import com.medishelf.core.domain.usecase.ObserveMedicinesForMemberUseCase
import com.medishelf.core.domain.usecase.SaveFamilyMemberUseCase
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.validation.FamilyMemberValidationError
import com.medishelf.core.model.DashboardCounts
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.testing.FakeFamilyMemberRepository
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.FakeStorageLocationRepository
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testLocation
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class ObserveMedicineUseCasesTest {
    private val time = TestTimeProvider()
    private val members = FakeFamilyMemberRepository()
    private val locations = FakeStorageLocationRepository()
    private val medicines = FakeMedicineRepository(members, locations)
    private val today = time.today

    @Test
    fun dashboard_countsExpiredExpiringAndLowStock() = runTest {
        medicines.put(testMedicine(expiryDate = today.minusDays(1))) // expired (stored ACTIVE)
        medicines.put(testMedicine(status = MedicineStatus.EXPIRED, expiryDate = today.minusDays(9)))
        medicines.put(testMedicine(expiryDate = today.plusDays(30), quantity = 1.0, lowStockThreshold = 2.0))
        medicines.put(testMedicine(expiryDate = today.plusDays(31)))
        medicines.put(testMedicine(status = MedicineStatus.DISPOSED, expiryDate = today.plusDays(3)))

        val counts = ObserveDashboardCountsUseCase(medicines, time)().first()

        assertThat(counts).isEqualTo(DashboardCounts(expired = 2, expiringSoon = 1, lowStock = 1))
    }

    @Test
    fun memberView_listsTheirMedicines_withCondition_activeFirst() = runTest {
        val sara = members.save(testMember(name = "Sara"))
        val omar = members.save(testMember(name = "Omar"))
        medicines.put(testMedicine(brandName = "Old", status = MedicineStatus.FINISHED), listOf(AssignmentInput(sara, "Cough")))
        medicines.put(testMedicine(brandName = "Ventolin"), listOf(AssignmentInput(sara, "Asthma")))
        medicines.put(testMedicine(brandName = "Other"), listOf(AssignmentInput(omar, null)))

        val list = ObserveMedicinesForMemberUseCase(medicines, time)(sara).first()

        assertThat(list.map { it.details.medicine.brandName to it.condition })
            .containsExactly("Ventolin" to "Asthma", "Old" to "Cough").inOrder()
    }

    @Test
    fun locationView_groupsInCabinetItems_includingEmptyAndUnassigned() = runTest {
        val kitchen = locations.save(testLocation(name = "Kitchen"))
        locations.save(testLocation(name = "Car kit"))
        medicines.put(testMedicine(brandName = "A", storageLocationId = kitchen))
        medicines.put(testMedicine(brandName = "B", storageLocationId = kitchen, status = MedicineStatus.DISPOSED))
        medicines.put(testMedicine(brandName = "C"))
        medicines.put(testMedicine(brandName = "D", storageLocationId = 999)) // dangling id

        val groups = ObserveMedicinesByLocationUseCase(medicines, locations, time)().first()

        assertThat(groups.map { (it.location?.name) to it.medicines.map { m -> m.medicine.brandName } })
            .containsExactly(
                "Car kit" to emptyList<String>(),
                "Kitchen" to listOf("A"),
                null to listOf("C", "D"),
            ).inOrder()
    }

    @Test
    fun barcodePrefill_copiesProductFields_notPackFields() = runTest {
        medicines.put(
            testMedicine(brandName = "Old pack", barcode = "123", storageLocationId = 4)
                .copy(updatedAt = Instant.ofEpochSecond(1), batchNumber = "B1"),
        )
        medicines.put(
            testMedicine(brandName = "Newest pack", barcode = "123", quantity = 3.0)
                .copy(updatedAt = Instant.ofEpochSecond(2), batchNumber = "B2"),
        )
        val prefill = FindPrefillByBarcodeUseCase(medicines)

        val draft = prefill(" 123 ")!!

        assertThat(draft.brandName).isEqualTo("Newest pack")
        assertThat(draft.barcode).isEqualTo("123")
        assertThat(draft.batchNumber).isEmpty()
        assertThat(draft.quantity).isNull()
        assertThat(draft.expiryDate).isNull()
        assertThat(prefill("999")).isNull()
        assertThat(prefill(" ")).isNull()
    }

    @Test
    fun saveMember_validatesNameAndBirthDate() = runTest {
        val save = SaveFamilyMemberUseCase(members, time)

        assertThat(save(testMember(name = "  "))).isEqualTo(
            SaveResult.Invalid(setOf(FamilyMemberValidationError.NAME_REQUIRED)),
        )
        assertThat(save(testMember().copy(dateOfBirth = today.plusDays(1)))).isEqualTo(
            SaveResult.Invalid(setOf(FamilyMemberValidationError.DATE_OF_BIRTH_IN_FUTURE)),
        )
        val id = (save(testMember(name = " Sara ").copy(allergies = " ")) as SaveResult.Saved).id
        val saved = members.members.value.getValue(id)
        assertThat(saved.name).isEqualTo("Sara")
        assertThat(saved.allergies).isNull()
    }
}
