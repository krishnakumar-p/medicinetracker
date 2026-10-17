package com.medishelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.medishelf.core.database.entity.MedicineEntity
import com.medishelf.core.database.entity.MedicineEventEntity
import com.medishelf.core.database.entity.MedicineMemberCrossRef
import com.medishelf.core.database.entity.MedicinePhotoEntity
import com.medishelf.core.database.entity.MedicineWithRelations
import com.medishelf.core.model.MedicineStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface MedicineDao {

    @Transaction
    @Query("SELECT * FROM medicines ORDER BY expiry_date, brand_name")
    fun observeAll(): Flow<List<MedicineWithRelations>>

    @Transaction
    @Query("SELECT * FROM medicines WHERE id = :id")
    fun observe(id: Long): Flow<MedicineWithRelations?>

    @Transaction
    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun get(id: Long): MedicineWithRelations?

    @Query("SELECT * FROM medicines WHERE barcode = :barcode ORDER BY updated_at DESC LIMIT 1")
    suspend fun findLatestByBarcode(barcode: String): MedicineEntity?

    /**
     * ACTIVE rows whose effective expiry (earlier of printed expiry and opened + use-within days,
     * all stored as epoch days) is before [todayEpochDay].
     */
    @Query(
        """
        SELECT * FROM medicines
        WHERE status = 'ACTIVE'
          AND MIN(expiry_date, COALESCE(opened_date + use_within_days_after_opening, expiry_date)) < :todayEpochDay
        """,
    )
    suspend fun getActiveExpiredBefore(todayEpochDay: Long): List<MedicineEntity>

    @Insert
    suspend fun insert(medicine: MedicineEntity): Long

    @Update
    suspend fun update(medicine: MedicineEntity)

    @Query("UPDATE medicines SET quantity = :quantity, status = :status, updated_at = :at WHERE id = :id")
    suspend fun updateQuantityAndStatus(id: Long, quantity: Double, status: MedicineStatus, at: Instant)

    @Query("UPDATE medicines SET status = :status, updated_at = :at WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MedicineStatus, at: Instant)

    @Query("DELETE FROM medicine_members WHERE medicine_id = :medicineId")
    suspend fun deleteAssignments(medicineId: Long)

    @Insert
    suspend fun insertAssignments(assignments: List<MedicineMemberCrossRef>)

    @Insert
    suspend fun insertEvent(event: MedicineEventEntity): Long

    @Query("SELECT * FROM medicine_events WHERE medicine_id = :medicineId ORDER BY at DESC, id DESC")
    fun observeEvents(medicineId: Long): Flow<List<MedicineEventEntity>>

    @Insert
    suspend fun insertPhoto(photo: MedicinePhotoEntity): Long

    /** Hard delete: only used to remove debug demo data. Real records are never deleted. */
    @Query("DELETE FROM medicines WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM medicine_photos WHERE id = :photoId")
    suspend fun deletePhoto(photoId: Long)

    /** Upsert medicine + replace member links + append history entry, atomically. */
    @Transaction
    suspend fun save(
        medicine: MedicineEntity,
        assignments: List<MedicineMemberCrossRef>,
        event: MedicineEventEntity,
    ): Long {
        val id = if (medicine.id == 0L) insert(medicine) else medicine.id.also { update(medicine) }
        deleteAssignments(id)
        if (assignments.isNotEmpty()) insertAssignments(assignments.map { it.copy(medicineId = id) })
        insertEvent(event.copy(medicineId = id))
        return id
    }

    @Transaction
    suspend fun updateQuantityAndStatusWithEvent(
        id: Long,
        quantity: Double,
        status: MedicineStatus,
        event: MedicineEventEntity,
    ) {
        updateQuantityAndStatus(id, quantity, status, event.at)
        insertEvent(event)
    }

    @Transaction
    suspend fun updateStatusWithEvent(id: Long, status: MedicineStatus, event: MedicineEventEntity) {
        updateStatus(id, status, event.at)
        insertEvent(event)
    }
}
