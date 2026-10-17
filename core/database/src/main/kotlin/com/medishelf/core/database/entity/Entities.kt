package com.medishelf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.PhotoType
import com.medishelf.core.model.QuantityUnit
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relationship: String,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: LocalDate?,
    val allergies: String?,
    @ColumnInfo(name = "avatar_color") val avatarColor: Int,
)

@Entity(tableName = "storage_locations")
data class StorageLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val room: String,
    val notes: String,
    @ColumnInfo(name = "photo_file_name") val photoFileName: String?,
    @ColumnInfo(name = "requires_refrigeration") val requiresRefrigeration: Boolean,
)

@Entity(
    tableName = "medicines",
    foreignKeys = [
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["storage_location_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("storage_location_id"),
        Index("barcode"),
        Index("status"),
        Index("expiry_date"),
    ],
)
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "brand_name") val brandName: String,
    @ColumnInfo(name = "generic_name") val genericName: String,
    val strength: String,
    val form: MedicineForm,
    val manufacturer: String,
    @ColumnInfo(name = "batch_number") val batchNumber: String,
    val barcode: String?,
    val quantity: Double,
    val unit: QuantityUnit,
    @ColumnInfo(name = "low_stock_threshold") val lowStockThreshold: Double?,
    @ColumnInfo(name = "purchase_date") val purchaseDate: LocalDate?,
    @ColumnInfo(name = "expiry_date") val expiryDate: LocalDate,
    @ColumnInfo(name = "opened_date") val openedDate: LocalDate?,
    @ColumnInfo(name = "use_within_days_after_opening") val useWithinDaysAfterOpening: Int?,
    @ColumnInfo(name = "prescribed_by") val prescribedBy: String,
    @ColumnInfo(name = "prescription_date") val prescriptionDate: LocalDate?,
    @ColumnInfo(name = "dosage_instructions") val dosageInstructions: String,
    @ColumnInfo(name = "storage_location_id") val storageLocationId: Long?,
    @ColumnInfo(name = "is_prescription") val isPrescription: Boolean,
    val notes: String,
    val status: MedicineStatus,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** Many-to-many medicine <-> member, carrying the condition it is prescribed for. */
@Entity(
    tableName = "medicine_members",
    primaryKeys = ["medicine_id", "member_id"],
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FamilyMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("member_id")],
)
data class MedicineMemberCrossRef(
    @ColumnInfo(name = "medicine_id") val medicineId: Long,
    @ColumnInfo(name = "member_id") val memberId: Long,
    val condition: String?,
)

@Entity(
    tableName = "medicine_photos",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicine_id")],
)
data class MedicinePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicine_id") val medicineId: Long,
    val type: PhotoType,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "medicine_events",
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicine_id")],
)
data class MedicineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicine_id") val medicineId: Long,
    val type: MedicineEventType,
    @ColumnInfo(name = "quantity_delta") val quantityDelta: Double?,
    @ColumnInfo(name = "from_status") val fromStatus: MedicineStatus?,
    @ColumnInfo(name = "to_status") val toStatus: MedicineStatus?,
    val note: String?,
    val at: Instant,
)

@Entity(tableName = "condition_suggestions")
data class ConditionSuggestionEntity(
    @PrimaryKey @ColumnInfo(collate = ColumnInfo.NOCASE) val text: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Instant,
)
