package com.medishelf.core.data.repository

import com.medishelf.core.database.entity.DoseLogEntity
import com.medishelf.core.database.entity.DoseScheduleEntity
import com.medishelf.core.database.entity.FamilyMemberEntity
import com.medishelf.core.database.entity.MedicineEntity
import com.medishelf.core.database.entity.MedicineEventEntity
import com.medishelf.core.database.entity.MedicinePhotoEntity
import com.medishelf.core.database.entity.MedicineWithRelations
import com.medishelf.core.database.entity.StorageLocationEntity
import com.medishelf.core.model.DoseLog
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineAssignment
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicinePhoto
import com.medishelf.core.model.StorageLocation

internal fun FamilyMemberEntity.asModel() = FamilyMember(
    id = id,
    name = name,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    allergies = allergies,
    avatarColor = avatarColor,
)

internal fun FamilyMember.asEntity() = FamilyMemberEntity(
    id = id,
    name = name,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    allergies = allergies,
    avatarColor = avatarColor,
)

internal fun StorageLocationEntity.asModel() = StorageLocation(
    id = id,
    name = name,
    room = room,
    notes = notes,
    photoFileName = photoFileName,
    requiresRefrigeration = requiresRefrigeration,
)

internal fun StorageLocation.asEntity() = StorageLocationEntity(
    id = id,
    name = name,
    room = room,
    notes = notes,
    photoFileName = photoFileName,
    requiresRefrigeration = requiresRefrigeration,
)

internal fun MedicineEntity.asModel() = Medicine(
    id = id,
    brandName = brandName,
    genericName = genericName,
    strength = strength,
    form = form,
    manufacturer = manufacturer,
    batchNumber = batchNumber,
    barcode = barcode,
    quantity = quantity,
    unit = unit,
    lowStockThreshold = lowStockThreshold,
    purchaseDate = purchaseDate,
    expiryDate = expiryDate,
    openedDate = openedDate,
    useWithinDaysAfterOpening = useWithinDaysAfterOpening,
    prescribedBy = prescribedBy,
    prescriptionDate = prescriptionDate,
    prescribedForMemberId = prescribedForMemberId,
    prescribedForReason = prescribedForReason,
    prescriptionStartDate = prescriptionStartDate,
    prescriptionEndDate = prescriptionEndDate,
    dosageInstructions = dosageInstructions,
    storageLocationId = storageLocationId,
    isPrescription = isPrescription,
    notes = notes,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Medicine.asEntity() = MedicineEntity(
    id = id,
    brandName = brandName,
    genericName = genericName,
    strength = strength,
    form = form,
    manufacturer = manufacturer,
    batchNumber = batchNumber,
    barcode = barcode,
    quantity = quantity,
    unit = unit,
    lowStockThreshold = lowStockThreshold,
    purchaseDate = purchaseDate,
    expiryDate = expiryDate,
    openedDate = openedDate,
    useWithinDaysAfterOpening = useWithinDaysAfterOpening,
    prescribedBy = prescribedBy,
    prescriptionDate = prescriptionDate,
    prescribedForMemberId = prescribedForMemberId,
    prescribedForReason = prescribedForReason,
    prescriptionStartDate = prescriptionStartDate,
    prescriptionEndDate = prescriptionEndDate,
    dosageInstructions = dosageInstructions,
    storageLocationId = storageLocationId,
    isPrescription = isPrescription,
    notes = notes,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun MedicinePhotoEntity.asModel() =
    MedicinePhoto(id = id, medicineId = medicineId, type = type, fileName = fileName, createdAt = createdAt)

internal fun MedicinePhoto.asEntity() =
    MedicinePhotoEntity(id = id, medicineId = medicineId, type = type, fileName = fileName, createdAt = createdAt)

internal fun MedicineEventEntity.asModel() = MedicineEvent(
    id = id,
    medicineId = medicineId,
    type = type,
    quantityDelta = quantityDelta,
    fromStatus = fromStatus,
    toStatus = toStatus,
    note = note,
    at = at,
)

internal fun MedicineEvent.asEntity() = MedicineEventEntity(
    id = id,
    medicineId = medicineId,
    type = type,
    quantityDelta = quantityDelta,
    fromStatus = fromStatus,
    toStatus = toStatus,
    note = note,
    at = at,
)

internal fun MedicineWithRelations.asModel() = MedicineDetails(
    medicine = medicine.asModel(),
    assignments = assignments
        .map { MedicineAssignment(member = it.member.asModel(), condition = it.crossRef.condition) }
        .sortedBy { it.member.name.lowercase() },
    location = location?.asModel(),
    photos = photos.map { it.asModel() }.sortedBy { it.createdAt },
    prescribedFor = prescribedFor?.asModel(),
)

internal fun DoseScheduleEntity.asModel() = DoseSchedule(
    id = id,
    medicineId = medicineId,
    memberId = memberId,
    kind = kind,
    dosePerIntake = dosePerIntake,
    timesPerDay = timesPerDay,
    startDate = startDate,
    endDate = endDate,
    createdAt = createdAt,
)

internal fun DoseSchedule.asEntity() = DoseScheduleEntity(
    id = id,
    medicineId = medicineId,
    memberId = memberId,
    kind = kind,
    dosePerIntake = dosePerIntake,
    timesPerDay = timesPerDay,
    startDate = startDate,
    endDate = endDate,
    createdAt = createdAt,
)

internal fun DoseLogEntity.asModel() =
    DoseLog(id = id, scheduleId = scheduleId, date = date, slot = slot, quantity = quantity, takenAt = takenAt)

internal fun DoseLog.asEntity() =
    DoseLogEntity(id = id, scheduleId = scheduleId, date = date, slot = slot, quantity = quantity, takenAt = takenAt)
