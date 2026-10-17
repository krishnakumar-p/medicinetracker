package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.usecase.applyFilter
import com.medishelf.core.model.ExpiryWindow
import com.medishelf.core.model.MedicineAssignment
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineFilter
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineSort
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import org.junit.Test
import java.time.LocalDate

class MedicineFilteringTest {
    private val today = LocalDate.of(2026, 1, 15)
    private val sara = testMember(id = 1, name = "Sara")
    private val omar = testMember(id = 2, name = "Omar")

    private val ventolin = MedicineDetails(
        testMedicine(id = 1, brandName = "Ventolin", genericName = "Salbutamol", expiryDate = today.plusDays(5), storageLocationId = 10)
            .copy(form = MedicineForm.INHALER),
        assignments = listOf(MedicineAssignment(sara, "Asthma")),
    )
    private val panadol = MedicineDetails(
        testMedicine(id = 2, brandName = "Panadol", genericName = "Paracetamol", expiryDate = today.plusDays(60)),
        assignments = listOf(MedicineAssignment(omar, "Fever"), MedicineAssignment(sara, null)),
    )
    private val oldSyrup = MedicineDetails(
        testMedicine(id = 3, brandName = "Amoxil", genericName = "Amoxicillin", expiryDate = today.minusDays(3))
            .copy(form = MedicineForm.SYRUP),
    )
    private val disposed = MedicineDetails(
        testMedicine(id = 4, brandName = "Brufen", status = MedicineStatus.DISPOSED),
    )
    private val all = listOf(panadol, disposed, oldSyrup, ventolin)

    private fun ids(filter: MedicineFilter) = all.applyFilter(filter, today).map { it.medicine.id }

    @Test
    fun default_showsInCabinetItems_sortedByExpiry() {
        assertThat(ids(MedicineFilter())).containsExactly(3L, 1L, 2L).inOrder()
    }

    @Test
    fun search_matchesBrandGenericConditionAndPerson_caseInsensitive() {
        assertThat(ids(MedicineFilter(query = "SALBU"))).containsExactly(1L)
        assertThat(ids(MedicineFilter(query = "fever"))).containsExactly(2L)
        assertThat(ids(MedicineFilter(query = "sara"))).containsExactly(1L, 2L)
        assertThat(ids(MedicineFilter(query = "sara asthma"))).containsExactly(1L)
        assertThat(ids(MedicineFilter(query = "   "))).hasSize(3)
    }

    @Test
    fun filters_byPersonLocationFormStatus() {
        assertThat(ids(MedicineFilter(memberIds = setOf(2)))).containsExactly(2L)
        assertThat(ids(MedicineFilter(locationIds = setOf(10)))).containsExactly(1L)
        assertThat(ids(MedicineFilter(forms = setOf(MedicineForm.SYRUP)))).containsExactly(3L)
        assertThat(ids(MedicineFilter(statuses = setOf(MedicineStatus.DISPOSED)))).containsExactly(4L)
        assertThat(ids(MedicineFilter(statuses = emptySet()))).hasSize(4)
    }

    @Test
    fun statusFilter_usesEffectiveStatus() {
        // Amoxil is stored ACTIVE but its date has passed.
        assertThat(ids(MedicineFilter(statuses = setOf(MedicineStatus.EXPIRED)))).containsExactly(3L)
    }

    @Test
    fun expiryWindows() {
        assertThat(ids(MedicineFilter(expiryWindow = ExpiryWindow.EXPIRED))).containsExactly(3L)
        assertThat(ids(MedicineFilter(expiryWindow = ExpiryWindow.WITHIN_7_DAYS))).containsExactly(1L)
        assertThat(ids(MedicineFilter(expiryWindow = ExpiryWindow.WITHIN_90_DAYS))).containsExactly(1L, 2L)
    }

    @Test
    fun lowStockOnly_andActiveFilterCount() {
        val low = MedicineDetails(testMedicine(id = 5, brandName = "Zyrtec", quantity = 1.0, lowStockThreshold = 3.0))
        val filter = MedicineFilter(lowStockOnly = true, memberIds = setOf(1))
        assertThat((all + low).applyFilter(MedicineFilter(lowStockOnly = true), today).map { it.medicine.id })
            .containsExactly(5L)
        assertThat(filter.activeFilterCount).isEqualTo(2)
        assertThat(MedicineFilter(query = "x").activeFilterCount).isEqualTo(0)
    }

    @Test
    fun sortByName() {
        assertThat(ids(MedicineFilter(sort = MedicineSort.NAME_ASCENDING))).containsExactly(3L, 2L, 1L).inOrder()
    }
}
