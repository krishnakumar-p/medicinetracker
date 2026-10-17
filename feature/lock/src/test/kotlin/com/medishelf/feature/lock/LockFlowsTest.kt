package com.medishelf.feature.lock

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.security.PinProblem
import com.medishelf.core.model.AppLockSettings
import com.medishelf.core.model.UserSettings
import com.medishelf.core.security.AppLockManager
import com.medishelf.core.testing.FakeAppLockCredentials
import com.medishelf.core.testing.FakeSettingsRepository
import com.medishelf.core.testing.MainDispatcherRule
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.feature.lock.navigation.PinFlow
import com.medishelf.feature.lock.navigation.PinFlowRoute
import com.medishelf.feature.lock.setup.PinFlowError
import com.medishelf.feature.lock.setup.PinFlowViewModel
import com.medishelf.feature.lock.setup.PinStep
import com.medishelf.feature.lock.unlock.LockViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LockFlowsTest {
    @get:Rule val main = MainDispatcherRule()
    private val time = TestTimeProvider()
    private val credentials = FakeAppLockCredentials(time)
    private val settings = FakeSettingsRepository()

    private fun pinFlow(flow: PinFlow) = PinFlowViewModel(SavedStateHandle(route = PinFlowRoute(flow)), credentials, settings)
    private fun PinFlowViewModel.enter(pin: String) {
        pin.forEach(::onDigit)
        onSubmit()
    }

    @Test
    fun setPin_rejectsWeakAndMismatch_thenEnablesLock() = runTest(main.testDispatcher) {
        val vm = pinFlow(PinFlow.SET)
        vm.enter("1234")
        assertThat(vm.uiState.value.error).isEqualTo(PinFlowError.Invalid(PinProblem.TOO_SIMPLE))

        vm.enter("2580")
        assertThat(vm.uiState.value.step).isEqualTo(PinStep.CONFIRM)
        vm.enter("2581")
        assertThat(vm.uiState.value.step).isEqualTo(PinStep.NEW)
        assertThat(vm.uiState.value.error).isEqualTo(PinFlowError.Invalid(PinProblem.MISMATCH))

        vm.enter("2580")
        vm.enter("2580")
        assertThat(vm.uiState.value.step).isEqualTo(PinStep.DONE)
        assertThat(credentials.pin).isEqualTo("2580")
        assertThat(settings.state.value.appLock.enabled).isTrue()
    }

    @Test
    fun disable_requiresCurrentPin() = runTest(main.testDispatcher) {
        credentials.setPin("2580")
        settings.updateAppLock { it.copy(enabled = true, biometricEnabled = true) }
        val vm = pinFlow(PinFlow.DISABLE)

        vm.enter("9999")
        assertThat(vm.uiState.value.error).isEqualTo(PinFlowError.WrongCurrent(4))
        assertThat(settings.state.value.appLock.enabled).isTrue()

        vm.enter("2580")
        assertThat(vm.uiState.value.step).isEqualTo(PinStep.DONE)
        assertThat(settings.state.value.appLock).isEqualTo(AppLockSettings(enabled = false, biometricEnabled = false))
        assertThat(credentials.pin).isNull()
    }

    @Test
    fun change_verifiesThenSetsNewPin() = runTest(main.testDispatcher) {
        credentials.setPin("2580")
        val vm = pinFlow(PinFlow.CHANGE)
        vm.enter("2580")
        assertThat(vm.uiState.value.step).isEqualTo(PinStep.NEW)
        vm.enter("4826")
        vm.enter("4826")
        assertThat(credentials.pin).isEqualTo("4826")
    }

    @Test
    fun lockScreen_unlocksOnCorrectPin_countsDownAfterTooMany() = runTest(main.testDispatcher) {
        credentials.setPin("2580")
        settings.state.value = UserSettings(appLock = AppLockSettings(enabled = true))
        val manager = AppLockManager(settings, credentials, time, backgroundScope)
        val vm = LockViewModel(credentials, manager, settings, time)
        backgroundScope.launch { vm.uiState.collect {} }

        "1111".forEach(vm::onDigit); vm.onSubmit()
        assertThat(vm.uiState.value.attemptsLeft).isEqualTo(4)
        assertThat(vm.uiState.value.pinLength).isEqualTo(0)

        repeat(4) { "1111".forEach(vm::onDigit); vm.onSubmit() }
        assertThat(vm.uiState.value.lockedOutSeconds).isGreaterThan(0L)
        assertThat(vm.uiState.value.canSubmit).isFalse()
    }

    @Test
    fun forgotPin_viaScreenLock_turnsLockOffAndKeepsNothingSecret() = runTest(main.testDispatcher) {
        credentials.setPin("2580")
        settings.state.value = UserSettings(appLock = AppLockSettings(enabled = true, biometricEnabled = true))
        val manager = AppLockManager(settings, credentials, time, backgroundScope)
        val vm = LockViewModel(credentials, manager, settings, time)

        vm.onForgotPinVerified()

        assertThat(credentials.pin).isNull()
        assertThat(settings.state.value.appLock.enabled).isFalse()
        assertThat(manager.lockState.value).isEqualTo(com.medishelf.core.security.LockState.UNLOCKED)
    }
}
