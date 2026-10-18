package com.medishelf.core.data.di

import com.medishelf.core.common.ApplicationScope
import com.medishelf.core.common.Dispatcher
import com.medishelf.core.common.MediShelfDispatchers
import com.medishelf.core.common.SystemTimeProvider
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.data.photo.AndroidPhotoStore
import com.medishelf.core.data.repository.ContentResolverDocumentStorage
import com.medishelf.core.data.repository.RoomDemoData
import com.medishelf.core.domain.backup.DocumentStorage
import com.medishelf.core.domain.demo.DemoData
import com.medishelf.core.data.repository.RoomConditionSuggestionRepository
import com.medishelf.core.data.repository.RoomDoseRepository
import com.medishelf.core.domain.repository.DoseRepository
import com.medishelf.core.data.repository.RoomFamilyMemberRepository
import com.medishelf.core.data.repository.RoomMedicineRepository
import com.medishelf.core.data.repository.RoomStorageLocationRepository
import com.medishelf.core.domain.repository.ConditionSuggestionRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.PhotoStore
import com.medishelf.core.domain.repository.StorageLocationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Public so instrumented/Robolectric app tests can swap it via @TestInstallIn. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds internal abstract fun medicineRepository(impl: RoomMedicineRepository): MedicineRepository
    @Binds internal abstract fun doseRepository(impl: RoomDoseRepository): DoseRepository
    @Binds internal abstract fun familyMemberRepository(impl: RoomFamilyMemberRepository): FamilyMemberRepository
    @Binds internal abstract fun storageLocationRepository(impl: RoomStorageLocationRepository): StorageLocationRepository
    @Binds internal abstract fun conditionSuggestionRepository(impl: RoomConditionSuggestionRepository): ConditionSuggestionRepository
    @Binds internal abstract fun documentStorage(impl: ContentResolverDocumentStorage): DocumentStorage
    @Binds internal abstract fun demoData(impl: RoomDemoData): DemoData
    @Binds internal abstract fun photoStore(impl: AndroidPhotoStore): PhotoStore
    @Binds abstract fun timeProvider(impl: SystemTimeProvider): TimeProvider
}

@Module
@InstallIn(SingletonComponent::class)
internal object DispatchersModule {
    @Provides
    @Dispatcher(MediShelfDispatchers.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(MediShelfDispatchers.DEFAULT)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@Dispatcher(MediShelfDispatchers.DEFAULT) dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
