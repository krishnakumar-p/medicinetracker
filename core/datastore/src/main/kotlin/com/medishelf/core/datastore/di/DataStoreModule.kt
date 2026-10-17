package com.medishelf.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.medishelf.core.datastore.DataStoreSettingsRepository
import com.medishelf.core.datastore.JsonSerializer
import com.medishelf.core.datastore.StoredDemoIds
import com.medishelf.core.datastore.StoredLockState
import com.medishelf.core.datastore.StoredReminderLedger
import com.medishelf.core.datastore.DataStoreReminderLedgerRepository
import com.medishelf.core.domain.reminders.ReminderLedgerRepository
import com.medishelf.core.datastore.StoredSettings
import com.medishelf.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<StoredSettings> = DataStoreFactory.create(
        serializer = JsonSerializer(StoredSettings.serializer(), StoredSettings()),
        corruptionHandler = ReplaceFileCorruptionHandler { StoredSettings() },
        produceFile = { File(context.noBackupFilesDir, "settings.json") },
    )

    /**
     * On corruption, reset to "no PIN" only together with lock disabled would weaken security,
     * so a corrupt lock file keeps failing loudly instead (handled by the caller).
     */
    @Provides
    @Singleton
    fun lockStateStore(@ApplicationContext context: Context): DataStore<StoredLockState> = DataStoreFactory.create(
        serializer = JsonSerializer(StoredLockState.serializer(), StoredLockState()),
        produceFile = { File(context.noBackupFilesDir, "lock_state.json") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
object AuxiliaryStoresModule {
    /** Losing the ledger only means a reminder may repeat once, so corruption resets it. */
    @Provides
    @Singleton
    fun reminderLedgerStore(@ApplicationContext context: Context): DataStore<StoredReminderLedger> = DataStoreFactory.create(
        serializer = JsonSerializer(StoredReminderLedger.serializer(), StoredReminderLedger()),
        corruptionHandler = ReplaceFileCorruptionHandler { StoredReminderLedger() },
        produceFile = { File(context.noBackupFilesDir, "reminder_ledger.json") },
    )

    @Provides
    @Singleton
    fun demoIdsStore(@ApplicationContext context: Context): DataStore<StoredDemoIds> = DataStoreFactory.create(
        serializer = JsonSerializer(StoredDemoIds.serializer(), StoredDemoIds()),
        corruptionHandler = ReplaceFileCorruptionHandler { StoredDemoIds() },
        produceFile = { File(context.noBackupFilesDir, "demo_ids.json") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsBindingModule {
    @Binds abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun reminderLedgerRepository(impl: DataStoreReminderLedgerRepository): ReminderLedgerRepository
}
