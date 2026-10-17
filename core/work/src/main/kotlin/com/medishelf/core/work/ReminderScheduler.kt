package com.medishelf.core.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.medishelf.core.domain.reminders.ReminderTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderTrigger {
    /** (Re)schedules the daily check near [hourOfDay] local time. Safe to call repeatedly. */
    fun scheduleDaily(hourOfDay: Int, now: LocalDateTime = LocalDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(hourOfDay, now))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Debug aid and post-import refresh: run one check now. */
    override fun runNow() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build())
    }

    companion object {
        const val DAILY_WORK = "medishelf-expiry-check"
        const val ONE_OFF_WORK = "medishelf-expiry-check-now"

        internal fun delayUntil(hourOfDay: Int, now: LocalDateTime): Duration {
            var next = now.toLocalDate().atTime(LocalTime.of(hourOfDay, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
