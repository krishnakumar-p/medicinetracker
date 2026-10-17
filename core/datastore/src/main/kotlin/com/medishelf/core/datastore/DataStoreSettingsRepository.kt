package com.medishelf.core.datastore

import androidx.datastore.core.DataStore
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.model.AppLockSettings
import com.medishelf.core.model.ReminderSettings
import com.medishelf.core.model.ThemeMode
import com.medishelf.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<StoredSettings>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = store.data.map { it.toModel() }

    override suspend fun acceptDisclaimer(version: Int) {
        store.updateData { it.copy(disclaimerAcceptedVersion = maxOf(it.disclaimerAcceptedVersion, version)) }
    }

    override suspend fun updateAppLock(transform: (AppLockSettings) -> AppLockSettings) {
        store.updateData { stored ->
            val updated = transform(stored.toModel().appLock)
            stored.copy(
                lockEnabled = updated.enabled,
                biometricEnabled = updated.enabled && updated.biometricEnabled,
                autoLockAfterSeconds = updated.autoLockAfterSeconds.coerceAtLeast(AppLockSettings.MIN_AUTO_LOCK_SECONDS),
            )
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.updateData { it.copy(themeMode = mode.name) }
    }

    override suspend fun updateReminders(transform: (ReminderSettings) -> ReminderSettings) {
        store.updateData { stored ->
            val updated = transform(stored.toModel().reminders)
            stored.copy(
                remindersEnabled = updated.enabled,
                reminderDays = updated.daysBefore.filter { it > 0 }.sortedDescending(),
                notifyExpired = updated.notifyExpired,
                notifyLowStock = updated.notifyLowStock,
                reminderHour = updated.hourOfDay.coerceIn(0, 23),
            )
        }
    }

    private fun StoredSettings.toModel() = UserSettings(
        disclaimerAcceptedVersion = disclaimerAcceptedVersion,
        appLock = AppLockSettings(
            enabled = lockEnabled,
            biometricEnabled = biometricEnabled,
            autoLockAfterSeconds = autoLockAfterSeconds,
        ),
        themeMode = ThemeMode.entries.find { it.name == themeMode } ?: ThemeMode.SYSTEM,
        reminders = ReminderSettings(
            enabled = remindersEnabled,
            daysBefore = reminderDays.toSet(),
            notifyExpired = notifyExpired,
            notifyLowStock = notifyLowStock,
            hourOfDay = reminderHour,
        ),
    )
}
