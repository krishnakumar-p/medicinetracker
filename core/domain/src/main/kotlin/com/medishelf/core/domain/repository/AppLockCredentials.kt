package com.medishelf.core.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

sealed interface PinCheckResult {
    data object Correct : PinCheckResult
    /** Wrong PIN; [attemptsBeforeLockout] counts down to the next lockout. */
    data class Wrong(val attemptsBeforeLockout: Int) : PinCheckResult
    data class LockedOut(val until: Instant) : PinCheckResult
}

/** Stores only a salted, stretched hash of the PIN, plus failed-attempt state. */
interface AppLockCredentials {
    val hasPin: Flow<Boolean>
    suspend fun setPin(pin: String)
    suspend fun verify(pin: String): PinCheckResult
    /** Current lockout end, if a lockout is in force. */
    suspend fun lockedOutUntil(): Instant?
    suspend fun clear()
}
