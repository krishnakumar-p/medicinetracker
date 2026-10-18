package com.medishelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.medishelf.core.database.entity.DoseLogEntity
import com.medishelf.core.database.entity.DoseScheduleEntity
import com.medishelf.core.database.entity.MedicineEventEntity
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.MedicineStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface DoseDao {
    @Query("SELECT * FROM dose_schedules ORDER BY start_date, id")
    fun observeSchedules(): Flow<List<DoseScheduleEntity>>

    @Query("SELECT * FROM dose_schedules WHERE id = :id")
    suspend fun getSchedule(id: Long): DoseScheduleEntity?

    @Query("SELECT * FROM dose_schedules WHERE medicine_id = :medicineId AND kind = :kind LIMIT 1")
    suspend fun findSchedule(medicineId: Long, kind: DoseScheduleKind): DoseScheduleEntity?

    @Query("SELECT * FROM dose_logs WHERE date BETWEEN :from AND :to")
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE schedule_id = :scheduleId AND date = :date AND slot = :slot")
    suspend fun findLog(scheduleId: Long, date: LocalDate, slot: Int): DoseLogEntity?

    @Insert
    suspend fun insertSchedule(schedule: DoseScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: DoseScheduleEntity)

    @Query("DELETE FROM dose_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: DoseLogEntity): Long

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("UPDATE medicines SET quantity = :quantity, status = :status, updated_at = :at WHERE id = :id")
    suspend fun updateMedicineStock(id: Long, quantity: Double, status: MedicineStatus, at: Instant)

    @Insert
    suspend fun insertEvent(event: MedicineEventEntity): Long

    /** Records the dose and lowers stock in one transaction. False if that dose was already taken. */
    @Transaction
    suspend fun takeDose(
        log: DoseLogEntity,
        medicineId: Long,
        quantity: Double,
        status: MedicineStatus,
        event: MedicineEventEntity,
    ): Boolean {
        if (insertLog(log) == -1L) return false
        updateMedicineStock(medicineId, quantity, status, event.at)
        insertEvent(event)
        return true
    }

    /** Removes a confirmed dose and puts its stock back, atomically. */
    @Transaction
    suspend fun undoDose(
        logId: Long,
        medicineId: Long,
        quantity: Double,
        status: MedicineStatus,
        event: MedicineEventEntity,
    ) {
        deleteLog(logId)
        updateMedicineStock(medicineId, quantity, status, event.at)
        insertEvent(event)
    }
}
