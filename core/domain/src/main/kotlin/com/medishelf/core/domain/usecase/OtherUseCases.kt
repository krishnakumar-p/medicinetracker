package com.medishelf.core.domain.usecase

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.domain.validation.FamilyMemberValidationError
import com.medishelf.core.domain.validation.MedicineDraft
import com.medishelf.core.domain.validation.StorageLocationValidationError
import com.medishelf.core.domain.validation.validate
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.StorageLocation
import javax.inject.Inject

/**
 * Prefill for a new entry from a previously saved medicine with the same barcode.
 * Batch, dates, quantity and people are deliberately not copied: they differ per pack.
 */
class FindPrefillByBarcodeUseCase @Inject constructor(
    private val medicines: MedicineRepository,
) {
    suspend operator fun invoke(barcode: String): MedicineDraft? {
        val code = barcode.trim().takeIf(String::isNotEmpty) ?: return null
        val previous = medicines.findLatestByBarcode(code) ?: return null
        return MedicineDraft(
            brandName = previous.brandName,
            genericName = previous.genericName,
            strength = previous.strength,
            form = previous.form,
            manufacturer = previous.manufacturer,
            barcode = code,
            unit = previous.unit,
            lowStockThreshold = previous.lowStockThreshold,
            storageLocationId = previous.storageLocationId,
            isPrescription = previous.isPrescription,
        )
    }
}

class SaveFamilyMemberUseCase @Inject constructor(
    private val members: FamilyMemberRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(member: FamilyMember): SaveResult<FamilyMemberValidationError> {
        val trimmed = member.copy(
            name = member.name.trim(),
            relationship = member.relationship.trim(),
            allergies = member.allergies?.trim()?.takeIf(String::isNotEmpty),
        )
        val errors = trimmed.validate(time.today())
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        return SaveResult.Saved(members.save(trimmed))
    }
}

class SaveStorageLocationUseCase @Inject constructor(
    private val locations: StorageLocationRepository,
) {
    suspend operator fun invoke(location: StorageLocation): SaveResult<StorageLocationValidationError> {
        val trimmed = location.copy(
            name = location.name.trim(),
            room = location.room.trim(),
            notes = location.notes.trim(),
        )
        val errors = trimmed.validate()
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        return SaveResult.Saved(locations.save(trimmed))
    }
}
