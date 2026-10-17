package com.medishelf.feature.lock.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.AppLockCredentials
import com.medishelf.core.domain.repository.PinCheckResult
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.domain.security.PinRules
import com.medishelf.core.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class LockUiState(
    val pinLength: Int = 0,
    val attemptsLeft: Int? = null,
    val lockedOutSeconds: Long = 0,
    val biometricEnabled: Boolean = false,
    val verifying: Boolean = false,
) {
    val canSubmit: Boolean get() = pinLength >= PinRules.MIN_LENGTH && !verifying && lockedOutSeconds == 0L
}

private data class Entry(val pin: String = "", val attemptsLeft: Int? = null, val verifying: Boolean = false)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val credentials: AppLockCredentials,
    private val lockManager: AppLockManager,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val entry = MutableStateFlow(Entry())
    private val lockedUntil = MutableStateFlow<Instant?>(null)
    private val now = MutableStateFlow(time.now())

    val uiState: StateFlow<LockUiState> = combine(entry, lockedUntil, now, settings.settings) { e, until, now, s ->
        LockUiState(
            pinLength = e.pin.length,
            attemptsLeft = e.attemptsLeft,
            lockedOutSeconds = until?.let { Duration.between(now, it).seconds.coerceAtLeast(0) + 1 }?.takeIf { until.isAfter(now) } ?: 0,
            biometricEnabled = s.appLock.biometricEnabled,
            verifying = e.verifying,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUiState())

    init {
        viewModelScope.launch { credentials.lockedOutUntil()?.let(::startCountdown) }
    }

    fun onDigit(digit: Char) = entry.update {
        if (it.pin.length >= PinRules.MAX_LENGTH || it.verifying) it else it.copy(pin = it.pin + digit)
    }

    fun onBackspace() = entry.update { it.copy(pin = it.pin.dropLast(1)) }

    fun onSubmit() {
        val pin = entry.value.pin
        if (pin.length < PinRules.MIN_LENGTH || entry.value.verifying) return
        entry.update { it.copy(verifying = true) }
        viewModelScope.launch {
            when (val result = credentials.verify(pin)) {
                PinCheckResult.Correct -> {
                    entry.value = Entry()
                    lockManager.unlock()
                }
                is PinCheckResult.Wrong -> entry.value = Entry(attemptsLeft = result.attemptsBeforeLockout)
                is PinCheckResult.LockedOut -> {
                    entry.value = Entry()
                    startCountdown(result.until)
                }
            }
        }
    }

    fun onBiometricSuccess() = lockManager.unlock()

    /**
     * The phone's screen lock was confirmed: the owner is present, so turn the app lock off
     * (they can set a new PIN in Settings). Records are never deleted for a forgotten PIN.
     */
    fun onForgotPinVerified() = viewModelScope.launch {
        credentials.clear()
        settings.updateAppLock { it.copy(enabled = false, biometricEnabled = false) }
        lockManager.unlock()
    }

    private fun startCountdown(until: Instant) {
        lockedUntil.value = until
        viewModelScope.launch {
            while (isActive && time.now().isBefore(until)) {
                now.value = time.now()
                delay(1_000)
            }
            now.value = time.now()
            lockedUntil.value = null
        }
    }
}
