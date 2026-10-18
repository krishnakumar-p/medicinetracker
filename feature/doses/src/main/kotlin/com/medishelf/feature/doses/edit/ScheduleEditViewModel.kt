package com.medishelf.feature.doses.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.DoseRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.usecase.ObserveMedicinesUseCase
import com.medishelf.core.domain.usecase.SaveDoseScheduleUseCase
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.usecase.ScheduleDraft
import com.medishelf.core.domain.usecase.ScheduleError
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineFilter
import com.medishelf.core.model.MedicineSort
import com.medishelf.core.ui.parseLocalizedInt
import com.medishelf.core.ui.parseLocalizedNumber
import com.medishelf.core.ui.toEditableString
import com.medishelf.feature.doses.navigation.ScheduleEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Numbers stay as text so partial input is never lost while typing. */
data class ScheduleForm(
    val medicineId: Long? = null,
    val memberId: Long? = null,
    val doseText: String = "1",
    val timesText: String = "1",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
) {
    fun toDraft(id: Long) = ScheduleDraft(
        id = id,
        medicineId = medicineId,
        memberId = memberId,
        dosePerIntake = parseLocalizedNumber(doseText),
        timesPerDay = parseLocalizedInt(timesText),
        startDate = startDate,
        endDate = endDate,
    )
}

data class ScheduleEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** A prescription course opened here: its dates come from the prescription. */
    val isCourse: Boolean = false,
    val form: ScheduleForm = ScheduleForm(),
    val medicines: List<MedicineDetails> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val errors: Set<ScheduleError> = emptySet(),
    val saved: Boolean = false,
)

private data class Meta(val isLoading: Boolean = true, val isCourse: Boolean = false, val submitted: Boolean = false, val saved: Boolean = false)

@HiltViewModel
class ScheduleEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMedicines: ObserveMedicinesUseCase,
    members: FamilyMemberRepository,
    private val doses: DoseRepository,
    private val saveSchedule: SaveDoseScheduleUseCase,
    time: TimeProvider,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ScheduleEditRoute>()
    private val form = MutableStateFlow(ScheduleForm(startDate = time.today()))
    private val meta = MutableStateFlow(Meta())
    // Only what is still in the cabinet can be taken.
    private val inCabinet = observeMedicines(flowOf(MedicineFilter(sort = MedicineSort.NAME_ASCENDING)))

    val uiState: StateFlow<ScheduleEditUiState> = combine(form, meta, inCabinet, members.observeAll()) { form, meta, medicines, people ->
        ScheduleEditUiState(
            isLoading = meta.isLoading,
            isNew = route.id == 0L,
            isCourse = meta.isCourse,
            form = form,
            medicines = medicines,
            members = people,
            errors = if (meta.submitted) saveSchedule.validate(form.toDraft(route.id)) else emptySet(),
            saved = meta.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleEditUiState())

    init {
        viewModelScope.launch {
            if (route.id != 0L) {
                doses.getSchedule(route.id)?.let { s ->
                    form.value = ScheduleForm(
                        medicineId = s.medicineId,
                        memberId = s.memberId,
                        doseText = s.dosePerIntake.toEditableString(),
                        timesText = s.timesPerDay.toString(),
                        startDate = s.startDate,
                        endDate = s.endDate,
                    )
                    meta.update { it.copy(isCourse = s.kind == DoseScheduleKind.PRESCRIPTION) }
                }
            } else if (route.medicineId != 0L) {
                val details = inCabinet.first().find { it.medicine.id == route.medicineId }
                form.update { it.copy(medicineId = details?.medicine?.id, memberId = details?.assignments?.singleOrNull()?.member?.id) }
            }
            meta.update { it.copy(isLoading = false) }
        }
    }

    fun onFormChange(transform: (ScheduleForm) -> ScheduleForm) = form.update(transform)

    /** Picking a medicine also picks its person when it has exactly one. */
    fun onMedicineSelected(id: Long) {
        val details = uiState.value.medicines.find { it.medicine.id == id }
        form.update { f ->
            f.copy(medicineId = id, memberId = f.memberId ?: details?.assignments?.singleOrNull()?.member?.id)
        }
    }

    fun onSave() = viewModelScope.launch {
        meta.update { it.copy(submitted = true) }
        if (saveSchedule(form.value.toDraft(route.id)) is SaveResult.Saved) meta.update { it.copy(saved = true) }
    }
}
