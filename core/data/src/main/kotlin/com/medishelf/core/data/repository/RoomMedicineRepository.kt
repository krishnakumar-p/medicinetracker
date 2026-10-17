package com.medishelf.core.data.repository

import com.medishelf.core.common.Dispatcher
import com.medishelf.core.common.MediShelfDispatchers
import com.medishelf.core.database.dao.MedicineDao
import com.medishelf.core.database.entity.MedicineMemberCrossRef
import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicinePhoto
import com.medishelf.core.model.MedicineStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

internal class RoomMedicineRepository @Inject constructor(
    private val dao: MedicineDao,
    @Dispatcher(MediShelfDispatchers.DEFAULT) private val mapDispatcher: CoroutineDispatcher,
) : MedicineRepository {

    override fun observeAll(): Flow<List<MedicineDetails>> =
        dao.observeAll().map { rows -> rows.map { it.asModel() } }.flowOn(mapDispatcher)

    override fun observe(id: Long): Flow<MedicineDetails?> = dao.observe(id).map { it?.asModel() }

    override fun observeEvents(medicineId: Long): Flow<List<MedicineEvent>> =
        dao.observeEvents(medicineId).map { rows -> rows.map { it.asModel() } }

    override suspend fun get(id: Long): MedicineDetails? = dao.get(id)?.asModel()

    override suspend fun findLatestByBarcode(barcode: String): Medicine? =
        dao.findLatestByBarcode(barcode)?.asModel()

    override suspend fun getActiveExpiredBefore(today: LocalDate): List<Medicine> =
        dao.getActiveExpiredBefore(today.toEpochDay()).map { it.asModel() }

    override suspend fun save(medicine: Medicine, assignments: List<AssignmentInput>, event: MedicineEvent): Long =
        dao.save(
            medicine = medicine.asEntity(),
            assignments = assignments.map { MedicineMemberCrossRef(medicine.id, it.memberId, it.condition) },
            event = event.asEntity(),
        )

    override suspend fun updateQuantityAndStatus(
        id: Long,
        quantity: Double,
        status: MedicineStatus,
        event: MedicineEvent,
    ) = dao.updateQuantityAndStatusWithEvent(id, quantity, status, event.asEntity())

    override suspend fun updateStatus(id: Long, status: MedicineStatus, event: MedicineEvent) =
        dao.updateStatusWithEvent(id, status, event.asEntity())

    override suspend fun addPhoto(photo: MedicinePhoto): Long = dao.insertPhoto(photo.asEntity())

    override suspend fun removePhoto(photoId: Long) = dao.deletePhoto(photoId)
}
