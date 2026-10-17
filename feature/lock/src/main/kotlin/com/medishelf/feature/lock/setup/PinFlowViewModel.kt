package com.medishelf.feature.lock.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.domain.repository.AppLockCredentials
import com.medishelf.core.domain.repository.PinCheckResult
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.domain.security.PinProblem
import com.medishelf.core.domain.security.PinRules
import com.medishelf.feature.lock.navigation.PinFlow
import com.medishelf.feature.lock.navigation.PinFlowRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

enum class PinStep { CURRENT, NEW, CONFIRM, DONE }

sealed interface PinFlowError {
    data class Invalid(val problem: PinProblem) : PinFlowError
    data class WrongCurrent(val attemptsLeft: Int) : PinFlowError
    data class LockedOut(val until: Instant) : PinFlowError
}

data class PinFlowUiState(
    val flow: PinFlow,
    val step: PinStep,
    val pinLength: Int = 0,
    val error: PinFlowError? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class PinFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val credentials: AppLockCredentials,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val flow = savedStateHandle.toRoute<PinFlowRoute>().flow
    private var pin = ""
    private var firstEntry = ""

    private val state = MutableStateFlow(
        PinFlowUiState(flow = flow, step = if (flow == PinFlow.SET) PinStep.NEW else PinStep.CURRENT),
    )
    val uiState: StateFlow<PinFlowUiState> = state.asStateFlow()

    fun onDigit(digit: Char) {
        if (pin.length >= PinRules.MAX_LENGTH || state.value.busy) return
        pin += digit
        state.update { it.copy(pinLength = pin.length) }
    }

    fun onBackspace() {
        pin = pin.dropLast(1)
        state.update { it.copy(pinLength = pin.length) }
    }

    fun onSubmit() {
        val entered = pin
        pin = ""
        state.update { it.copy(pinLength = 0) }
        when (state.value.step) {
            PinStep.CURRENT -> verifyCurrent(entered)
            PinStep.NEW -> PinRules.validate(entered)?.let { problem ->
                state.update { it.copy(error = PinFlowError.Invalid(problem)) }
            } ?: run {
                firstEntry = entered
                state.update { it.copy(step = PinStep.CONFIRM, error = null) }
            }
            PinStep.CONFIRM -> PinRules.validate(firstEntry, entered)?.let { problem ->
                // Mismatch restarts from the first entry so the user isn't stuck.
                firstEntry = ""
                state.update { it.copy(step = PinStep.NEW, error = PinFlowError.Invalid(problem)) }
            } ?: save(firstEntry)
            PinStep.DONE -> Unit
        }
    }

    private fun verifyCurrent(entered: String) {
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = credentials.verify(entered)) {
                PinCheckResult.Correct -> if (flow == PinFlow.DISABLE) {
                    credentials.clear()
                    settings.updateAppLock { it.copy(enabled = false, biometricEnabled = false) }
                    state.update { it.copy(step = PinStep.DONE, busy = false, error = null) }
                } else {
                    state.update { it.copy(step = PinStep.NEW, busy = false, error = null) }
                }
                is PinCheckResult.Wrong -> state.update {
                    it.copy(busy = false, error = PinFlowError.WrongCurrent(result.attemptsBeforeLockout))
                }
                is PinCheckResult.LockedOut -> state.update { it.copy(busy = false, error = PinFlowError.LockedOut(result.until)) }
            }
        }
    }

    private fun save(newPin: String) {
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            credentials.setPin(newPin)
            settings.updateAppLock { it.copy(enabled = true) }
            firstEntry = ""
            state.update { it.copy(step = PinStep.DONE, busy = false, error = null) }
        }
    }
}
