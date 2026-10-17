package com.medishelf.di

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.data.di.DataModule
import com.medishelf.core.domain.repository.ConditionSuggestionRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.PhotoStore
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.testing.FakeConditionSuggestionRepository
import com.medishelf.core.testing.FakeFamilyMemberRepository
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.FakePhotoStore
import com.medishelf.core.testing.FakeStorageLocationRepository
import com.medishelf.core.testing.TestTimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Replaces Room/SQLCipher with in-memory fakes; the native SQLCipher lib can't load on the JVM. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {
    @Provides @Singleton fun members() = FakeFamilyMemberRepository()
    @Provides @Singleton fun locations() = FakeStorageLocationRepository()
    @Provides @Singleton fun medicines(m: FakeFamilyMemberRepository, l: FakeStorageLocationRepository) = FakeMedicineRepository(m, l)

    @Provides fun memberRepository(fake: FakeFamilyMemberRepository): FamilyMemberRepository = fake
    @Provides fun locationRepository(fake: FakeStorageLocationRepository): StorageLocationRepository = fake
    @Provides fun medicineRepository(fake: FakeMedicineRepository): MedicineRepository = fake
    @Provides @Singleton fun suggestions(): ConditionSuggestionRepository = FakeConditionSuggestionRepository()
    @Provides @Singleton fun photos(): PhotoStore = FakePhotoStore()
    @Provides @Singleton fun time(): TimeProvider = TestTimeProvider()
    @Provides @Singleton fun documents(): com.medishelf.core.domain.backup.DocumentStorage = com.medishelf.core.testing.FakeDocumentStorage()
    @Provides @Singleton fun demoData(): com.medishelf.core.domain.demo.DemoData = com.medishelf.core.testing.FakeDemoData()
}
