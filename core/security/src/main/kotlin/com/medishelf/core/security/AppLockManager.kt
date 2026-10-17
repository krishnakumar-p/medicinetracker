package com.medishelf.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.medishelf.core.common.ApplicationScope
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.AppLockCredentials
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.domain.security.AutoLockPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class LockState { LOADING, LOCKED, UNLOCKED }

/**
 * Process-wide lock state. Locked on cold start when app lock is on, and again when the app
 * returns from the background after the configured timeout. The UI shows the lock screen
 * over everything while LOCKED, so deep links and back stacks can't bypass it.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val credentials: AppLockCredentials,
    private val time: TimeProvider,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val state = MutableStateFlow(LockState.LOADING)
    val lockState: StateFlow<LockState> = state.asStateFlow()

    private var backgroundedAt: Instant? = null
    private var started = false

    /** Call once from Application.onCreate (main thread). */
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            state.value = if (isLockActive()) LockState.LOCKED else LockState.UNLOCKED
            // Turning the lock off (or losing the PIN) unlocks immediately.
            combine(settings.settings, credentials.hasPin) { s, hasPin -> s.appLock.enabled && hasPin }
                .collect { active -> if (!active) state.value = LockState.UNLOCKED }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = time.now()
    }

    override fun onStart(owner: LifecycleOwner) {
        val awaySince = backgroundedAt ?: return
        backgroundedAt = null
        scope.launch {
            val lock = settings.settings.first().appLock
            if (isLockActive() && AutoLockPolicy.shouldLock(awaySince, time.now(), lock.autoLockAfterSeconds)) {
                state.update { if (it == LockState.UNLOCKED) LockState.LOCKED else it }
            }
        }
    }

    fun unlock() {
        state.value = LockState.UNLOCKED
    }

    private suspend fun isLockActive(): Boolean =
        settings.settings.first().appLock.enabled && credentials.hasPin.first()
}
