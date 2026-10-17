package com.medishelf.core.security

import androidx.datastore.core.DataStore
import com.medishelf.core.common.Dispatcher
import com.medishelf.core.common.MediShelfDispatchers
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.datastore.StoredLockState
import com.medishelf.core.domain.repository.AppLockCredentials
import com.medishelf.core.domain.repository.PinCheckResult
import com.medishelf.core.domain.security.LockoutPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DataStoreAppLockCredentials @Inject constructor(
    private val store: DataStore<StoredLockState>,
    private val hasher: PinHasher,
    private val time: TimeProvider,
    @Dispatcher(MediShelfDispatchers.DEFAULT) private val cpu: CoroutineDispatcher,
) : AppLockCredentials {

    // Serialises verify() so parallel attempts can't bypass the failed-attempt counter.
    private val mutex = Mutex()

    override val hasPin: Flow<Boolean> = store.data.map { it.pinHash != null }

    override suspend fun setPin(pin: String) {
        val hashed = withContext(cpu) { hasher.hash(pin) }
        store.updateData {
            StoredLockState(pinHash = hashed.hash, pinSalt = hashed.salt, pinIterations = hashed.iterations)
        }
    }

    override suspend fun verify(pin: String): PinCheckResult = mutex.withLock {
        val state = store.data.first()
        val now = time.now()
        lockout(state, now)?.let { return PinCheckResult.LockedOut(it) }
        val hash = state.pinHash ?: return PinCheckResult.Wrong(0)
        val ok = withContext(cpu) {
            hasher.matches(pin, PinHasher.Hashed(hash, checkNotNull(state.pinSalt), state.pinIterations))
        }
        if (ok) {
            store.updateData { it.copy(failedAttempts = 0, lockedOutUntilEpochMillis = 0) }
            return PinCheckResult.Correct
        }
        val failed = state.failedAttempts + 1
        val until = LockoutPolicy.lockoutAfter(failed, now)
        store.updateData { it.copy(failedAttempts = failed, lockedOutUntilEpochMillis = until?.toEpochMilli() ?: 0) }
        return if (until != null) PinCheckResult.LockedOut(until) else PinCheckResult.Wrong(LockoutPolicy.attemptsBeforeLockout(failed))
    }

    override suspend fun lockedOutUntil(): Instant? = lockout(store.data.first(), time.now())

    override suspend fun clear() {
        store.updateData { StoredLockState() }
    }

    private fun lockout(state: StoredLockState, now: Instant): Instant? =
        Instant.ofEpochMilli(state.lockedOutUntilEpochMillis).takeIf { it.isAfter(now) }
}
