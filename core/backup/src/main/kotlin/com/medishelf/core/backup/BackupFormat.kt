package com.medishelf.core.backup

import com.medishelf.core.database.dao.DatabaseSnapshot
import com.medishelf.core.database.entity.ConditionSuggestionEntity
import com.medishelf.core.database.entity.FamilyMemberEntity
import com.medishelf.core.database.entity.MedicineEntity
import com.medishelf.core.database.entity.MedicineEventEntity
import com.medishelf.core.database.entity.MedicineMemberCrossRef
import com.medishelf.core.database.entity.MedicinePhotoEntity
import com.medishelf.core.database.entity.StorageLocationEntity
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.PhotoType
import com.medishelf.core.model.QuantityUnit
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * The backup file format, independent of the Room schema so the database can evolve.
 * Dates are ISO-8601, instants epoch millis, enums by name. Bump [CURRENT_SCHEMA] on breaking changes.
 */
internal const val BACKUP_FORMAT = "medishelf-backup"
internal const val CURRENT_SCHEMA = 1

@Serializable
internal data class BackupManifest(
    val format: String,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val appVersion: String,
    val medicines: Int,
    val photos: Int,
)

@Serializable internal data class MemberDto(val id: Long, val name: String, val relationship: String, val dateOfBirth: String?, val allergies: String?, val avatarColor: Int)
@Serializable internal data class LocationDto(val id: Long, val name: String, val room: String, val notes: String, val photoFileName: String?, val requiresRefrigeration: Boolean)
@Serializable internal data class MedicineDto(
    val id: Long, val brandName: String, val genericName: String, val strength: String, val form: String,
    val manufacturer: String, val batchNumber: String, val barcode: String?, val quantity: Double, val unit: String,
    val lowStockThreshold: Double?, val purchaseDate: String?, val expiryDate: String, val openedDate: String?,
    val useWithinDaysAfterOpening: Int?, val prescribedBy: String, val prescriptionDate: String?,
    val dosageInstructions: String, val storageLocationId: Long?, val isPrescription: Boolean, val notes: String,
    val status: String, val createdAt: Long, val updatedAt: Long,
)
@Serializable internal data class AssignmentDto(val medicineId: Long, val memberId: Long, val condition: String?)
@Serializable internal data class PhotoDto(val id: Long, val medicineId: Long, val type: String, val fileName: String, val createdAt: Long)
@Serializable internal data class EventDto(val id: Long, val medicineId: Long, val type: String, val quantityDelta: Double?, val fromStatus: String?, val toStatus: String?, val note: String?, val at: Long)
@Serializable internal data class SuggestionDto(val text: String, val lastUsedAt: Long)

@Serializable
internal data class BackupPayload(
    val members: List<MemberDto>,
    val locations: List<LocationDto>,
    val medicines: List<MedicineDto>,
    val assignments: List<AssignmentDto>,
    val photos: List<PhotoDto>,
    val events: List<EventDto>,
    val suggestions: List<SuggestionDto>,
) {
    /** File names referenced by records, i.e. the photos that belong in the archive. */
    val photoFiles: Set<String>
        get() = (photos.map { it.fileName } + locations.mapNotNull { it.photoFileName }).toSet()

    /** Catches truncated or hand-edited files before they reach the database. */
    fun isConsistent(): Boolean {
        val medicineIds = medicines.map { it.id }.toSet()
        val memberIds = members.map { it.id }.toSet()
        val locationIds = locations.map { it.id }.toSet()
        return medicineIds.size == medicines.size &&
            memberIds.size == members.size &&
            locationIds.size == locations.size &&
            medicines.all { it.storageLocationId == null || it.storageLocationId in locationIds } &&
            assignments.all { it.medicineId in medicineIds && it.memberId in memberIds } &&
            photos.all { it.medicineId in medicineIds && isSafeFileName(it.fileName) } &&
            locations.all { it.photoFileName == null || isSafeFileName(it.photoFileName) } &&
            events.all { it.medicineId in medicineIds }
    }
}

/** Plain file names only: blocks "../" zip-slip and absolute paths. */
internal fun isSafeFileName(name: String) = name.matches(Regex("[A-Za-z0-9._-]{1,100}")) && name != "." && name != ".."

internal fun DatabaseSnapshot.toPayload() = BackupPayload(
    members = members.map { MemberDto(it.id, it.name, it.relationship, it.dateOfBirth?.toString(), it.allergies, it.avatarColor) },
    locations = locations.map { LocationDto(it.id, it.name, it.room, it.notes, it.photoFileName, it.requiresRefrigeration) },
    medicines = medicines.map {
        MedicineDto(
            it.id, it.brandName, it.genericName, it.strength, it.form.name, it.manufacturer, it.batchNumber, it.barcode,
            it.quantity, it.unit.name, it.lowStockThreshold, it.purchaseDate?.toString(), it.expiryDate.toString(),
            it.openedDate?.toString(), it.useWithinDaysAfterOpening, it.prescribedBy, it.prescriptionDate?.toString(),
            it.dosageInstructions, it.storageLocationId, it.isPrescription, it.notes, it.status.name,
            it.createdAt.toEpochMilli(), it.updatedAt.toEpochMilli(),
        )
    },
    assignments = assignments.map { AssignmentDto(it.medicineId, it.memberId, it.condition) },
    photos = photos.map { PhotoDto(it.id, it.medicineId, it.type.name, it.fileName, it.createdAt.toEpochMilli()) },
    events = events.map { EventDto(it.id, it.medicineId, it.type.name, it.quantityDelta, it.fromStatus?.name, it.toStatus?.name, it.note, it.at.toEpochMilli()) },
    suggestions = suggestions.map { SuggestionDto(it.text, it.lastUsedAt.toEpochMilli()) },
)

/** Throws IllegalArgumentException on unknown enum names or malformed dates. */
internal fun BackupPayload.toSnapshot() = DatabaseSnapshot(
    members = members.map { FamilyMemberEntity(it.id, it.name, it.relationship, it.dateOfBirth?.let(LocalDate::parse), it.allergies, it.avatarColor) },
    locations = locations.map { StorageLocationEntity(it.id, it.name, it.room, it.notes, it.photoFileName, it.requiresRefrigeration) },
    medicines = medicines.map {
        MedicineEntity(
            id = it.id, brandName = it.brandName, genericName = it.genericName, strength = it.strength,
            form = MedicineForm.valueOf(it.form), manufacturer = it.manufacturer, batchNumber = it.batchNumber,
            barcode = it.barcode, quantity = it.quantity, unit = QuantityUnit.valueOf(it.unit),
            lowStockThreshold = it.lowStockThreshold, purchaseDate = it.purchaseDate?.let(LocalDate::parse),
            expiryDate = LocalDate.parse(it.expiryDate), openedDate = it.openedDate?.let(LocalDate::parse),
            useWithinDaysAfterOpening = it.useWithinDaysAfterOpening, prescribedBy = it.prescribedBy,
            prescriptionDate = it.prescriptionDate?.let(LocalDate::parse), dosageInstructions = it.dosageInstructions,
            storageLocationId = it.storageLocationId, isPrescription = it.isPrescription, notes = it.notes,
            status = MedicineStatus.valueOf(it.status), createdAt = Instant.ofEpochMilli(it.createdAt),
            updatedAt = Instant.ofEpochMilli(it.updatedAt),
        )
    },
    assignments = assignments.map { MedicineMemberCrossRef(it.medicineId, it.memberId, it.condition) },
    photos = photos.map { MedicinePhotoEntity(it.id, it.medicineId, PhotoType.valueOf(it.type), it.fileName, Instant.ofEpochMilli(it.createdAt)) },
    events = events.map {
        MedicineEventEntity(
            it.id, it.medicineId, MedicineEventType.valueOf(it.type), it.quantityDelta,
            it.fromStatus?.let(MedicineStatus::valueOf), it.toStatus?.let(MedicineStatus::valueOf), it.note, Instant.ofEpochMilli(it.at),
        )
    },
    suggestions = suggestions.map { ConditionSuggestionEntity(it.text, Instant.ofEpochMilli(it.lastUsedAt)) },
)
