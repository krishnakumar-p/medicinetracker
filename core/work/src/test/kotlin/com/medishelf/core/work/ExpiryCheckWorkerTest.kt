package com.medishelf.core.work

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.usecase.ReconcileExpiredStatusesUseCase
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.notifications.ReminderNotifier
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.FakeReminderLedgerRepository
import com.medishelf.core.testing.FakeSettingsRepository
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testMedicine
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class ExpiryCheckWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val time = TestTimeProvider()
    private val medicines = FakeMedicineRepository()
    private val ledger = FakeReminderLedgerRepository()
    private val notifier = ReminderNotifier(context).apply { createChannels() }

    private fun worker() = TestListenableWorkerBuilder<ExpiryCheckWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker =
                ExpiryCheckWorker(appContext, params, ReconcileExpiredStatusesUseCase(medicines, time), medicines, FakeSettingsRepository(), ledger, notifier, time)
        })
        .build()

    @Test
    fun marksExpired_postsPrivateSummaries_onceOnly() = runTest {
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val expired = medicines.put(testMedicine(brandName = "Old syrup", expiryDate = time.today.minusDays(1)))
        medicines.put(testMedicine(brandName = "Eye drops", expiryDate = time.today.plusDays(6)))
        medicines.put(testMedicine(brandName = "Fresh", expiryDate = time.today.plusYears(1)))

        assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())

        assertThat(medicines.get(expired)!!.medicine.status).isEqualTo(MedicineStatus.EXPIRED)
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertThat(posted).hasSize(2)
        val texts = posted.map { it.extras.getCharSequence("android.bigText").toString() }
        assertThat(texts.joinToString()).contains("Eye drops")
        assertThat(texts.joinToString()).doesNotContain("Fresh")
        // Lock-screen version never names a medicine.
        assertThat(posted.all { it.publicVersion.extras.getCharSequence("android.text").toString().contains("Unlock") }).isTrue()

        context.getSystemService(NotificationManager::class.java).cancelAll()
        worker().doWork()
        assertThat(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications).isEmpty()
    }

    @Test
    fun withoutPermission_remindersStayPending() = runTest {
        shadowOf(context as Application).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        medicines.put(testMedicine(expiryDate = time.today.plusDays(3)))

        worker().doWork()

        assertThat(ledger.ledger.sent).isEmpty() // will fire once notifications are allowed
    }

    @Test
    fun dailyScheduleTargetsNextOccurrenceOfHour() {
        val morning = LocalDateTime.of(2026, 1, 15, 7, 30)
        assertThat(ReminderScheduler.delayUntil(9, morning)).isEqualTo(Duration.ofMinutes(90))
        assertThat(ReminderScheduler.delayUntil(9, morning.withHour(9).withMinute(0))).isEqualTo(Duration.ofHours(24))
        assertThat(ReminderScheduler.delayUntil(9, morning.withHour(22))).isEqualTo(Duration.ofHours(10).plusMinutes(30))
    }
}
