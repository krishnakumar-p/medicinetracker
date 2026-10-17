package com.medishelf.feature.medicines.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.PhotoStore
import com.medishelf.core.domain.usecase.AddMedicinePhotoUseCase
import com.medishelf.core.domain.usecase.ReduceQuantityResult
import com.medishelf.core.domain.usecase.ReduceQuantityUseCase
import com.medishelf.core.domain.usecase.RemoveMedicinePhotoUseCase
import com.medishelf.core.domain.usecase.UpdateMedicineStatusUseCase
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicinePhoto
import com.medishelf.core.model.PhotoType
import com.medishelf.feature.medicines.navigation.MedicineDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PhotoUi(val photo: MedicinePhoto, val path: String)

enum class DetailMessage { USED, FINISHED, DISPOSED, RESTORED, PHOTO_ADDED, PHOTO_FAILED, INVALID_AMOUNT }

data class MedicineDetailUiState(
    val isLoading: Boolean = true,
    val details: MedicineDetails? = null,
    val photos: List<PhotoUi> = emptyList(),
    val history: List<MedicineEvent> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
    val message: DetailMessage? = null,
)

@HiltViewModel
class MedicineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    medicines: MedicineRepository,
    private val photoStore: PhotoStore,
    private val reduceQuantity: ReduceQuantityUseCase,
    private val updateStatus: UpdateMedicineStatusUseCase,
    private val addPhoto: AddMedicinePhotoUseCase,
    private val removePhoto: RemoveMedicinePhotoUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<MedicineDetailRoute>().id
    private val message = MutableStateFlow<DetailMessage?>(null)

    val uiState: StateFlow<MedicineDetailUiState> = combine(
        medicines.observe(id),
        medicines.observeEvents(id),
        message,
    ) { details, history, message ->
        MedicineDetailUiState(
            isLoading = false,
            details = details,
            photos = details?.photos.orEmpty().map { PhotoUi(it, photoStore.absolutePath(it.fileName)) },
            history = history,
            today = time.today(),
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicineDetailUiState())

    fun onUse(amount: Double?) = viewModelScope.launch {
        message.value = when (reduceQuantity(id, amount ?: Double.NaN)) {
            is ReduceQuantityResult.Updated -> DetailMessage.USED
            ReduceQuantityResult.InvalidAmount -> DetailMessage.INVALID_AMOUNT
            ReduceQuantityResult.NotAllowed, ReduceQuantityResult.NotFound -> null
        }
    }

    fun onMarkFinished() = viewModelScope.launch {
        if (updateStatus.markFinished(id)) message.value = DetailMessage.FINISHED
    }

    fun onMarkDisposed(note: String) = viewModelScope.launch {
        if (updateStatus.markDisposed(id, note)) message.value = DetailMessage.DISPOSED
    }

    fun onRestore() = viewModelScope.launch {
        if (updateStatus.restore(id)) message.value = DetailMessage.RESTORED
    }

    fun onAddPhoto(type: PhotoType, uri: String) = viewModelScope.launch {
        message.value = try {
            addPhoto(id, type, uri)
            DetailMessage.PHOTO_ADDED
        } catch (e: Exception) {
            DetailMessage.PHOTO_FAILED
        }
    }

    fun onRemovePhoto(photo: MedicinePhoto) = viewModelScope.launch { removePhoto(photo) }

    fun onMessageShown() {
        message.value = null
    }
}
