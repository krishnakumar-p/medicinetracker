package com.medishelf.core.data.repository

import androidx.datastore.core.DataStore
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.database.dao.FamilyMemberDao
import com.medishelf.core.database.dao.MedicineDao
import com.medishelf.core.database.dao.StorageLocationDao
import com.medishelf.core.datastore.StoredDemoIds
import com.medishelf.core.domain.demo.DemoData
import com.medishelf.core.domain.demo.DemoDataset
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicineEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class RoomDemoData @Inject constructor(
    private val ids: DataStore<StoredDemoIds>,
    private val members: FamilyMemberRepository,
    private val locations: StorageLocationRepository,
    private val medicines: MedicineRepository,
    private val medicineDao: MedicineDao,
    private val memberDao: FamilyMemberDao,
    private val locationDao: StorageLocationDao,
    private val time: TimeProvider,
) : DemoData {

    override val isLoaded: Flow<Boolean> = ids.data.map { it.medicines.isNotEmpty() || it.members.isNotEmpty() }

    override suspend fun load() {
        if (isLoaded.first()) return
        val now = time.now()
        val set = DemoDataset.build(time.today())
        val memberIds = set.members.map { members.save(it) }
        val locationIds = set.locations.map { locations.save(it) }
        val medicineIds = set.medicines.map { demo ->
            medicines.save(
                medicine = demo.medicine.copy(
                    storageLocationId = demo.locationIndex?.let(locationIds::get),
                    createdAt = now,
                    updatedAt = now,
                ),
                assignments = set.assignments(demo, memberIds),
                event = MedicineEvent(medicineId = 0, type = MedicineEventType.CREATED, toStatus = demo.medicine.status, note = "Demo data", at = now),
            )
        }
        ids.updateData { StoredDemoIds(memberIds, locationIds, medicineIds) }
    }

    override suspend fun clear() {
        val stored = ids.data.first()
        medicineDao.deleteByIds(stored.medicines) // cascades to links, photos rows and history
        stored.members.forEach { memberDao.delete(it) }
        stored.locations.forEach { locationDao.delete(it) }
        forget()
    }

    override suspend fun forget() {
        ids.updateData { StoredDemoIds() }
    }
}
