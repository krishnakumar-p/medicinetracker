package com.medishelf

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.model.UserSettings
import com.medishelf.core.security.LockState
import org.junit.Test

class AppGateTest {
    private val accepted = UserSettings(disclaimerAcceptedVersion = UserSettings.CURRENT_DISCLAIMER_VERSION)

    @Test
    fun gateOrder_dbThenDisclaimerThenLock() {
        assertThat(gateFor(null, accepted, LockState.UNLOCKED)).isEqualTo(AppGate.LOADING)
        assertThat(gateFor(true, accepted, LockState.LOADING)).isEqualTo(AppGate.LOADING)
        assertThat(gateFor(false, UserSettings(), LockState.LOCKED)).isEqualTo(AppGate.DATABASE_UNAVAILABLE)
        assertThat(gateFor(true, UserSettings(), LockState.LOCKED)).isEqualTo(AppGate.DISCLAIMER)
        assertThat(gateFor(true, accepted, LockState.LOCKED)).isEqualTo(AppGate.LOCKED)
        assertThat(gateFor(true, accepted, LockState.UNLOCKED)).isEqualTo(AppGate.READY)
    }

    @Test
    fun disclaimerVersionBump_showsItAgain() {
        assertThat(gateFor(true, UserSettings(disclaimerAcceptedVersion = 0), LockState.UNLOCKED)).isEqualTo(AppGate.DISCLAIMER)
    }
}
