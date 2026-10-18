package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.usecase.GlobalSearch
import com.medishelf.core.domain.usecase.SearchField
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineAssignment
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.StorageLocation
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import org.junit.Test
import java.time.LocalDate

class GlobalSearchTest {
    private val today = LocalDate.of(2026, 1, 15)
    private val sara = testMember(id = 1, name = "Sara Ahmed")
    private val omar = FamilyMember(id = 2, name = "Omar", relationship = "Father", allergies = "Penicillin", avatarColor = 0)
    private val fridge = StorageLocation(id = 1, name = "Fridge", room = "Kitchen")

    private val augmentin = MedicineDetails(
        medicine = testMedicine(id = 1, brandName = "Augmentin", genericName = "Amoxicillin / clavulanic acid").copy(
            strength = "625 mg", manufacturer = "GSK", prescribedBy = "Dr. Hana Saleh", prescribedForMemberId = 1,
            prescribedForReason = "Ear infection", storageLocationId = 1, batchNumber = "B12345",
        ),
        assignments = listOf(MedicineAssignment(sara, "Ear infection")),
        location = fridge,
        prescribedFor = sara,
    )
    private val panadol = MedicineDetails(
        medicine = testMedicine(id = 2, brandName = "Panadol", genericName = "Paracetamol").copy(strength = "500 mg", notes = "For fever"),
        assignments = listOf(MedicineAssignment(omar, "Headache")),
    )
    private val oldPanadol = MedicineDetails(
        medicine = testMedicine(id = 3, brandName = "Panadol Extra", genericName = "Paracetamol + Caffeine", status = MedicineStatus.DISPOSED),
    )
    private val all = listOf(oldPanadol, augmentin, panadol)
    private val schedule = DoseSchedule(id = 1, medicineId = 2, dosePerIntake = 1.0, timesPerDay = 1, startDate = today)

    private fun search(q: String) = GlobalSearch.search(q, all, listOf(sara, omar), listOf(fridge), listOf(schedule), today)

    @Test
    fun findsByEveryMedicineField() {
        mapOf(
            "augmentin" to SearchField.NAME,
            "clavulanic" to SearchField.COMPOSITION,
            "625mg" to SearchField.STRENGTH,
            "hana" to SearchField.DOCTOR,
            "dr. saleh" to SearchField.DOCTOR,
            "ear infection" to SearchField.CONDITION,
            "gsk" to SearchField.MANUFACTURER,
            "kitchen" to SearchField.LOCATION,
            "b12345" to SearchField.BATCH,
        ).forEach { (query, field) ->
            val hit = search(query).medicines.single { it.details.medicine.id == 1L }
            assertThat(hit.matches.map { it.field }).contains(field)
        }
        assertThat(search("sara").medicines.single().matches.map { it.field })
            .containsAtLeast(SearchField.PRESCRIBED_FOR, SearchField.PERSON)
    }

    @Test
    fun everyWordMustMatch_acrossFields_caseAndAccentInsensitive() {
        assertThat(search("AMOXICILLIN sara").medicines.map { it.details.medicine.brandName }).containsExactly("Augmentin")
        assertThat(search("amoxicillin omar").medicines).isEmpty()
        assertThat(search("Paracétamol").medicines).hasSize(2)
    }

    @Test
    fun historyComesLast_andScheduleIsFlagged() {
        val hits = search("panadol").medicines
        assertThat(hits.map { it.details.medicine.id }).containsExactly(2L, 3L).inOrder()
        assertThat(hits.first().onSchedule).isTrue()
        assertThat(hits.last().effectiveStatus).isEqualTo(MedicineStatus.DISPOSED)
    }

    @Test
    fun findsPeopleAndPlaces() {
        val people = search("penicillin")
        assertThat(people.members.single().member).isEqualTo(omar)
        assertThat(people.members.single().matches.single().field).isEqualTo(SearchField.ALLERGIES)
        assertThat(search("father").members.single().medicineCount).isEqualTo(1)
        assertThat(search("fridge").locations.single().medicineCount).isEqualTo(1)
        assertThat(search("   ").isEmpty).isTrue()
    }
}
