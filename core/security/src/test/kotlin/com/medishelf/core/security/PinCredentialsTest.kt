package com.medishelf.core.security

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.datastore.di.DataStoreModule
import com.medishelf.core.domain.repository.PinCheckResult
import com.medishelf.core.testing.TestTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PinCredentialsTest {
    private val hasher = PinHasher()

    @Test
    fun hasher_saltsAndVerifies() {
        val a = hasher.hash("2580", iterations = 1_000)
        val b = hasher.hash("2580", iterations = 1_000)
        assertThat(a.hash).isNotEqualTo(b.hash) // different salts
        assertThat(hasher.matches("2580", a)).isTrue()
        assertThat(hasher.matches("2581", a)).isFalse()
    }

    @Test
    fun credentials_storeOnlyHash_andThrottle() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val time = TestTimeProvider()
        val store = DataStoreModule.lockStateStore(context)
        val credentials = DataStoreAppLockCredentials(store, hasher, time, StandardTestDispatcher(testScheduler))

        credentials.setPin("2580")
        assertThat(credentials.hasPin.first()).isTrue()
        val onDisk = File(context.noBackupFilesDir, "lock_state.json").readText()
        assertThat(onDisk).doesNotContain("2580")

        repeat(4) { assertThat(credentials.verify("1111")).isInstanceOf(PinCheckResult.Wrong::class.java) }
        val locked = credentials.verify("1111")
        assertThat(locked).isInstanceOf(PinCheckResult.LockedOut::class.java)
        // Even the right PIN is refused during a lockout.
        assertThat(credentials.verify("2580")).isInstanceOf(PinCheckResult.LockedOut::class.java)

        time.today = time.today.plusDays(1)
        assertThat(credentials.verify("2580")).isEqualTo(PinCheckResult.Correct)
        assertThat(credentials.verify("1111")).isEqualTo(PinCheckResult.Wrong(4)) // counter was reset

        credentials.clear()
        assertThat(credentials.hasPin.first()).isFalse()
    }
}
