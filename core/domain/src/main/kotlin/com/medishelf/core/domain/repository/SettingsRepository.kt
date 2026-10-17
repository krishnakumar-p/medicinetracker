package com.medishelf.core.domain.repository

import com.medishelf.core.model.AppLockSettings
import com.medishelf.core.model.ReminderSettings
import com.medishelf.core.model.ThemeMode
import com.medishelf.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun acceptDisclaimer(version: Int)
    suspend fun updateAppLock(transform: (AppLockSettings) -> AppLockSettings)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun updateReminders(transform: (ReminderSettings) -> ReminderSettings)
}
