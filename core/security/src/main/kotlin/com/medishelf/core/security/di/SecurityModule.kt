package com.medishelf.core.security.di

import com.medishelf.core.domain.repository.AppLockCredentials
import com.medishelf.core.security.DataStoreAppLockCredentials
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SecurityModule {
    @Binds abstract fun appLockCredentials(impl: DataStoreAppLockCredentials): AppLockCredentials
}
