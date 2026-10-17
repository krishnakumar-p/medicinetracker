package com.medishelf.feature.medicines

import com.medishelf.core.domain.usecase.AddMedicinePhotoUseCase
import com.medishelf.core.domain.usecase.FindPrefillByBarcodeUseCase
import com.medishelf.core.domain.usecase.ObserveMedicinesUseCase
import com.medishelf.core.domain.usecase.ReduceQuantityUseCase
import com.medishelf.core.domain.usecase.RemoveMedicinePhotoUseCase
import com.medishelf.core.domain.usecase.SaveMedicineUseCase
import com.medishelf.core.domain.usecase.UpdateMedicineStatusUseCase
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.testing.FakeConditionSuggestionRepository
import com.medishelf.core.testing.FakeFamilyMemberRepository
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.FakePhotoStore
import com.medishelf.core.testing.FakeStorageLocationRepository
import com.medishelf.core.testing.TestTimeProvider
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import com.medishelf.feature.medicines.detail.MedicineDetailViewModel
import com.medishelf.feature.medicines.edit.MedicineEditViewModel
import com.medishelf.feature.medicines.list.MedicineListViewModel
import com.medishelf.feature.medicines.navigation.MedicineDetailRoute
import com.medishelf.feature.medicines.navigation.MedicineEditRoute
import com.medishelf.feature.medicines.navigation.MedicineListRoute

/** Wires real use cases to in-memory fakes, the same graph Hilt builds in the app. */
class MedicineTestFixture {
    val time = TestTimeProvider()
    val members = FakeFamilyMemberRepository()
    val locations = FakeStorageLocationRepository()
    val medicines = FakeMedicineRepository(members, locations)
    val suggestions = FakeConditionSuggestionRepository()
    val photos = FakePhotoStore()

    fun listViewModel(route: MedicineListRoute = MedicineListRoute()) = MedicineListViewModel(
        savedStateHandle = SavedStateHandle(route = route),
        observeMedicines = ObserveMedicinesUseCase(medicines, time),
        members = members,
        locations = locations,
        time = time,
    )

    fun editViewModel(
        route: MedicineEditRoute = MedicineEditRoute(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(route = route),
    ) = MedicineEditViewModel(
        savedStateHandle = savedStateHandle,
        medicines = medicines,
        members = members,
        locations = locations,
        suggestions = suggestions,
        findPrefill = FindPrefillByBarcodeUseCase(medicines),
        saveMedicine = SaveMedicineUseCase(medicines, suggestions, MedicineValidator(), time),
        validator = MedicineValidator(),
        time = time,
    )

    fun detailViewModel(id: Long) = MedicineDetailViewModel(
        savedStateHandle = SavedStateHandle(route = MedicineDetailRoute(id)),
        medicines = medicines,
        photoStore = photos,
        reduceQuantity = ReduceQuantityUseCase(medicines, time),
        updateStatus = UpdateMedicineStatusUseCase(medicines, time),
        addPhoto = AddMedicinePhotoUseCase(medicines, photos, time),
        removePhoto = RemoveMedicinePhotoUseCase(medicines, photos),
        time = time,
    )
}
