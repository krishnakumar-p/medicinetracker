package com.medishelf.feature.doses.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medishelf.core.domain.usecase.DoseDay
import com.medishelf.core.domain.usecase.DoseResult
import com.medishelf.core.domain.usecase.DueDose
import com.medishelf.core.domain.usecase.ObserveDoseDayUseCase
import com.medishelf.core.domain.usecase.StopDoseScheduleUseCase
import com.medishelf.core.domain.usecase.TakeDoseUseCase
import com.medishelf.core.domain.usecase.UndoDoseUseCase
import com.medishelf.core.common.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class DoseMessage { OUT_OF_STOCK, PACK_FINISHED, STOPPED }

data class DosesUiState(
    val isLoading: Boolean = true,
    val day: DoseDay = DoseDay(LocalDate.MIN, emptyList(), emptyList()),
    val message: DoseMessage? = null,
)

@HiltViewModel
class DosesViewModel @Inject constructor(
    observeDay: ObserveDoseDayUseCase,
    private val takeDose: TakeDoseUseCase,
    private val undoDose: UndoDoseUseCase,
    private val stopSchedule: StopDoseScheduleUseCase,
    private val time: TimeProvider,
) : ViewModel() {
    private val message = MutableStateFlow<DoseMessage?>(null)
    private val date = MutableStateFlow(time.today())

    val uiState: StateFlow<DosesUiState> = combine(observeDay(date), message) { day, message ->
        DosesUiState(isLoading = false, day = day, message = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DosesUiState())

    /** Ticks or un-ticks a dose; stock follows. */
    fun onToggle(dose: DueDose) = viewModelScope.launch {
        if (dose.taken) {
            undoDose(dose.schedule.id, dose.slot, dose.log!!.date)
            return@launch
        }
        message.value = when (val result = takeDose(dose.schedule.id, dose.slot)) {
            is DoseResult.Taken -> DoseMessage.PACK_FINISHED.takeIf { result.finished }
            DoseResult.OutOfStock -> DoseMessage.OUT_OF_STOCK
            else -> null
        }
    }

    fun onStop(scheduleId: Long) = viewModelScope.launch {
        if (stopSchedule(scheduleId)) message.value = DoseMessage.STOPPED
    }

    fun onMessageShown() { message.value = null }

    /** The screen may stay open past midnight: pick up the new day when it comes back. */
    fun onResume() { date.value = time.today() }
}
