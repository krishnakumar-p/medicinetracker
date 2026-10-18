package com.medishelf.feature.doses

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.designsystem.theme.MediShelfTheme
import com.medishelf.core.domain.usecase.ObserveDoseDayUseCase
import com.medishelf.core.domain.usecase.ObserveMedicinesUseCase
import com.medishelf.core.domain.usecase.SaveDoseScheduleUseCase
import com.medishelf.core.domain.usecase.ScheduleError
import com.medishelf.core.domain.usecase.StopDoseScheduleUseCase
import com.medishelf.core.domain.usecase.TakeDoseUseCase
import com.medishelf.core.domain.usecase.UndoDoseUseCase
import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.testing.FakeDoseRepository
import com.medishelf.core.testing.FakeFamilyMemberRepository
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.MainDispatcherRule
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import com.medishelf.feature.doses.edit.ScheduleEditViewModel
import com.medishelf.feature.doses.navigation.ScheduleEditRoute
import com.medishelf.feature.doses.today.DosesScreen
import com.medishelf.feature.doses.today.DosesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DosesTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val main = MainDispatcherRule()

    private val time = TestTimeProvider()
    private val members = FakeFamilyMemberRepository()
    private val medicines = FakeMedicineRepository(members)
    private val doses = FakeDoseRepository(medicines)

    private fun dosesViewModel() = DosesViewModel(
        ObserveDoseDayUseCase(doses, medicines, members, time),
        TakeDoseUseCase(doses, medicines, time),
        UndoDoseUseCase(doses, medicines, time),
        StopDoseScheduleUseCase(doses, time),
        time,
    )

    private fun editViewModel(route: ScheduleEditRoute) = ScheduleEditViewModel(
        SavedStateHandle(route = route),
        ObserveMedicinesUseCase(medicines, time),
        members,
        doses,
        SaveDoseScheduleUseCase(doses, time),
        time,
    )

    @Test
    fun tickingDoses_lowersStock_andShowsProgress() {
        val dad = runBlocking { members.save(testMember(name = "Dad")) }
        val id = medicines.put(testMedicine(brandName = "Concor", quantity = 30.0))
        val schedule = runBlocking {
            doses.saveSchedule(DoseSchedule(medicineId = id, memberId = dad, dosePerIntake = 1.0, timesPerDay = 2, startDate = time.today))
        }
        compose.setContent { MediShelfTheme { DosesScreen({}, {}, {}, {}, viewModel = dosesViewModel()) } }

        compose.onNodeWithText("Today: 0 of 2 taken").assertExists()
        compose.onNodeWithText("Continuous medication").assertExists()
        compose.onNodeWithTag("dose_${schedule}_0").assertIsOff().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("dose_${schedule}_0").assertIsOn()
        compose.onNodeWithText("Today: 1 of 2 taken").assertExists()
        assertThat(runBlocking { medicines.get(id)!!.medicine.quantity }).isEqualTo(29.0)

        // Un-tick puts the tablet back.
        compose.onNodeWithTag("dose_${schedule}_0").performClick()
        compose.waitForIdle()
        assertThat(runBlocking { medicines.get(id)!!.medicine.quantity }).isEqualTo(30.0)
    }

    @Test
    fun emptyState_explainsTheSection() {
        compose.setContent { MediShelfTheme { DosesScreen({}, {}, {}, {}, viewModel = dosesViewModel()) } }
        compose.onNodeWithText("No regular medication yet").assertExists()
    }

    @Test
    fun addContinuous_preselectsMedicineAndPerson_validates_andSaves() = runTest(main.testDispatcher) {
        val sara = members.save(testMember(name = "Sara"))
        val id = medicines.put(testMedicine(brandName = "Ventolin"), listOf(AssignmentInput(sara, "Asthma")))
        val vm = editViewModel(ScheduleEditRoute(medicineId = id))
        backgroundScope.launch { vm.uiState.collect {} }

        val loaded = vm.uiState.first { !it.isLoading }
        assertThat(loaded.form.medicineId).isEqualTo(id)
        assertThat(loaded.form.memberId).isEqualTo(sara)

        vm.onFormChange { it.copy(doseText = "0", timesText = "2") }
        vm.onSave()
        assertThat(vm.uiState.value.errors).containsExactly(ScheduleError.DOSE_NOT_POSITIVE)

        vm.onFormChange { it.copy(doseText = "2") }
        vm.onSave()
        assertThat(vm.uiState.first { it.saved }.saved).isTrue()
        val saved = doses.schedules.value.values.single()
        assertThat(saved.kind).isEqualTo(DoseScheduleKind.CONTINUOUS)
        assertThat(saved.dosePerIntake).isEqualTo(2.0)
        assertThat(saved.endDate).isNull()
    }
}
