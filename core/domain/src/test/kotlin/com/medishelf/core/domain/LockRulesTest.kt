package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.security.AutoLockPolicy
import com.medishelf.core.domain.security.LockoutPolicy
import com.medishelf.core.domain.security.PinProblem
import com.medishelf.core.domain.security.PinRules
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LockRulesTest {
    private val now = Instant.parse("2026-01-15T10:00:00Z")

    @Test
    fun pinRules() {
        assertThat(PinRules.validate("2580")).isNull()
        assertThat(PinRules.validate("258")).isEqualTo(PinProblem.TOO_SHORT)
        assertThat(PinRules.validate("123456789")).isEqualTo(PinProblem.TOO_LONG)
        assertThat(PinRules.validate("12a4")).isEqualTo(PinProblem.NOT_DIGITS)
        assertThat(PinRules.validate("٢٥٨٠")).isEqualTo(PinProblem.NOT_DIGITS)
        listOf("0000", "1234", "56789", "9876", "3210").forEach {
            assertThat(PinRules.validate(it)).isEqualTo(PinProblem.TOO_SIMPLE)
        }
        assertThat(PinRules.validate("2580", "2581")).isEqualTo(PinProblem.MISMATCH)
        assertThat(PinRules.validate("2580", "2580")).isNull()
    }

    @Test
    fun lockoutEscalatesAndCaps() {
        assertThat(LockoutPolicy.lockoutAfter(4, now)).isNull()
        assertThat(LockoutPolicy.lockoutAfter(5, now)).isEqualTo(now.plusSeconds(30))
        assertThat(LockoutPolicy.lockoutAfter(6, now)).isEqualTo(now.plusSeconds(60))
        assertThat(LockoutPolicy.lockoutAfter(8, now)).isEqualTo(now.plusSeconds(240))
        assertThat(LockoutPolicy.lockoutAfter(40, now)).isEqualTo(now.plus(Duration.ofMinutes(15)))
        assertThat(LockoutPolicy.attemptsBeforeLockout(3)).isEqualTo(2)
        assertThat(LockoutPolicy.attemptsBeforeLockout(9)).isEqualTo(0)
    }

    @Test
    fun autoLock() {
        assertThat(AutoLockPolicy.shouldLock(null, now, 60)).isFalse()
        assertThat(AutoLockPolicy.shouldLock(now.minusSeconds(59), now, 60)).isFalse()
        assertThat(AutoLockPolicy.shouldLock(now.minusSeconds(60), now, 60)).isTrue()
    }
}
