package com.medishelf

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.medishelf.core.common.ApplicationScope
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.notifications.ReminderNotifier
import com.medishelf.core.security.AppLockManager
import com.medishelf.core.work.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MediShelfApplication : Application(), Configuration.Provider {
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var settings: SettingsRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appLockManager.start()
        notifier.createChannels()
        // Keep the daily check aligned with the chosen reminder hour.
        appScope.launch {
            settings.settings.map { it.reminders.hourOfDay }.distinctUntilChanged().collect(scheduler::scheduleDaily)
        }
    }
}
