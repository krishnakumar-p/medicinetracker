package com.medishelf.core.domain.repository

import com.medishelf.core.model.DoseLog
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicineStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface DoseRepository {
    fun observeSchedules(): Flow<List<DoseSchedule>>

    /** Confirmed doses dated [from]..[to], inclusive. */
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<DoseLog>>

    suspend fun getSchedule(id: Long): DoseSchedule?

    suspend fun findSchedule(medicineId: Long, kind: DoseScheduleKind): DoseSchedule?

    /** Inserts ([DoseSchedule.id] == 0) or updates. Returns the id. */
    suspend fun saveSchedule(schedule: DoseSchedule): Long

    /** Deletes the schedule and its dose log; stock already used stays used. */
    suspend fun deleteSchedule(id: Long)

    suspend fun findLog(scheduleId: Long, date: LocalDate, slot: Int): DoseLog?

    /**
     * Stores [log] and sets the medicine's stock and status, with [event], atomically.
     * Returns false (and changes nothing) if that dose was already recorded.
     */
    suspend fun takeDose(log: DoseLog, medicineId: Long, quantity: Double, status: MedicineStatus, event: MedicineEvent): Boolean

    /** Removes [log] and sets the medicine's stock and status, with [event], atomically. */
    suspend fun undoDose(log: DoseLog, medicineId: Long, quantity: Double, status: MedicineStatus, event: MedicineEvent)
}
