package com.medishelf.core.database.di

import android.content.Context
import androidx.room.Room
import com.medishelf.core.database.MediShelfDatabase
import com.medishelf.core.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager,
    ): MediShelfDatabase {
        val factory = LazyCipherOpenHelperFactory { keyManager.getOrCreatePassphrase().toSqlCipherRawKey() }
        return Room.databaseBuilder(context, MediShelfDatabase::class.java, MediShelfDatabase.NAME)
            .openHelperFactory(factory)
            .build()
    }

    @Provides fun medicineDao(db: MediShelfDatabase) = db.medicineDao()
    @Provides fun familyMemberDao(db: MediShelfDatabase) = db.familyMemberDao()
    @Provides fun storageLocationDao(db: MediShelfDatabase) = db.storageLocationDao()
    @Provides fun conditionSuggestionDao(db: MediShelfDatabase) = db.conditionSuggestionDao()
    @Provides fun backupDao(db: MediShelfDatabase) = db.backupDao()
}
