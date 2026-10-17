package com.medishelf.core.domain.usecase

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.PhotoStore
import com.medishelf.core.model.MedicinePhoto
import com.medishelf.core.model.PhotoType
import javax.inject.Inject

class AddMedicinePhotoUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val photos: PhotoStore,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(medicineId: Long, type: PhotoType, sourceUri: String): Long {
        val fileName = photos.import(sourceUri)
        return try {
            medicines.addPhoto(MedicinePhoto(medicineId = medicineId, type = type, fileName = fileName, createdAt = time.now()))
        } catch (e: Exception) {
            // Don't leave an orphaned file if the row could not be written.
            photos.delete(fileName)
            throw e
        }
    }
}

class RemoveMedicinePhotoUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val photos: PhotoStore,
) {
    suspend operator fun invoke(photo: MedicinePhoto) {
        medicines.removePhoto(photo.id)
        photos.delete(photo.fileName)
    }
}
