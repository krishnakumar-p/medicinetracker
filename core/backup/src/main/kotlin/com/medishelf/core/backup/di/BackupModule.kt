package com.medishelf.core.backup.di

import com.medishelf.core.backup.ZipBackupService
import com.medishelf.core.domain.backup.BackupService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackupModule {
    @Binds abstract fun backupService(impl: ZipBackupService): BackupService
}
