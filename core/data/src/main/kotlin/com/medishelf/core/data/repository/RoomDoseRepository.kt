package com.medishelf.core.data.repository

import com.medishelf.core.database.dao.DoseDao
import com.medishelf.core.domain.repository.DoseRepository
import com.medishelf.core.model.DoseLog
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

internal class RoomDoseRepository @Inject constructor(
    private val dao: DoseDao,
) : DoseRepository {
    override fun observeSchedules(): Flow<List<DoseSchedule>> =
        dao.observeSchedules().map { rows -> rows.map { it.asModel() } }

    override fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<DoseLog>> =
        dao.observeLogs(from, to).map { rows -> rows.map { it.asModel() } }

    override suspend fun getSchedule(id: Long): DoseSchedule? = dao.getSchedule(id)?.asModel()

    override suspend fun findSchedule(medicineId: Long, kind: DoseScheduleKind): DoseSchedule? =
        dao.findSchedule(medicineId, kind)?.asModel()

    override suspend fun saveSchedule(schedule: DoseSchedule): Long =
        if (schedule.id == 0L) {
            dao.insertSchedule(schedule.asEntity())
        } else {
            dao.updateSchedule(schedule.asEntity())
            schedule.id
        }

    override suspend fun deleteSchedule(id: Long) = dao.deleteSchedule(id)

    override suspend fun findLog(scheduleId: Long, date: LocalDate, slot: Int): DoseLog? =
        dao.findLog(scheduleId, date, slot)?.asModel()

    override suspend fun takeDose(
        log: DoseLog,
        medicineId: Long,
        quantity: Double,
        status: MedicineStatus,
        event: MedicineEvent,
    ): Boolean = dao.takeDose(log.asEntity(), medicineId, quantity, status, event.asEntity())

    override suspend fun undoDose(
        log: DoseLog,
        medicineId: Long,
        quantity: Double,
        status: MedicineStatus,
        event: MedicineEvent,
    ) = dao.undoDose(log.id, medicineId, quantity, status, event.asEntity())
}
