package com.medishelf.core.domain.security

import java.time.Duration
import java.time.Instant

enum class PinProblem { TOO_SHORT, TOO_LONG, NOT_DIGITS, TOO_SIMPLE, MISMATCH }

object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /** Rejects malformed PINs and the most guessable ones (1111, 1234, 9876…). */
    fun validate(pin: String, confirmation: String? = null): PinProblem? = when {
        !pin.all { it in '0'..'9' } -> PinProblem.NOT_DIGITS
        pin.length < MIN_LENGTH -> PinProblem.TOO_SHORT
        pin.length > MAX_LENGTH -> PinProblem.TOO_LONG
        pin.toSet().size == 1 || isSequence(pin, 1) || isSequence(pin, -1) -> PinProblem.TOO_SIMPLE
        confirmation != null && confirmation != pin -> PinProblem.MISMATCH
        else -> null
    }

    private fun isSequence(pin: String, step: Int) = pin.zipWithNext().all { (a, b) -> b - a == step }
}

/**
 * Escalating lockout: a few free attempts, then 30 s, doubling per further failure, capped
 * at 15 min. Never wipes data: a forgotten PIN must not destroy a family's records.
 */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private val BASE = Duration.ofSeconds(30)
    private val MAX = Duration.ofMinutes(15)

    fun lockoutAfter(failedAttempts: Int, now: Instant): Instant? {
        if (failedAttempts < FREE_ATTEMPTS) return null
        val exponent = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(10)
        val duration = BASE.multipliedBy(1L shl exponent).let { if (it > MAX) MAX else it }
        return now.plus(duration)
    }

    fun attemptsBeforeLockout(failedAttempts: Int): Int = (FREE_ATTEMPTS - failedAttempts).coerceAtLeast(0)
}

object AutoLockPolicy {
    /** Lock on return to foreground if the app was away at least [timeoutSeconds]. */
    fun shouldLock(backgroundedAt: Instant?, now: Instant, timeoutSeconds: Int): Boolean =
        backgroundedAt != null && Duration.between(backgroundedAt, now).seconds >= timeoutSeconds
}
