package com.medishelf.core.domain.usecase

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.model.DashboardCounts
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineFilter
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.StorageLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveMedicinesUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val time: TimeProvider,
) {
    /** Re-filters in memory on every filter change; the database is only re-queried on writes. */
    operator fun invoke(filter: Flow<MedicineFilter>): Flow<List<MedicineDetails>> =
        combine(medicines.observeAll(), filter) { all, current -> all.applyFilter(current, time.today()) }
}

class ObserveDashboardCountsUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val time: TimeProvider,
) {
    operator fun invoke(expiringWithinDays: Long = DEFAULT_EXPIRING_WINDOW_DAYS): Flow<DashboardCounts> =
        medicines.observeAll().map { all ->
            val today = time.today()
            var expired = 0
            var expiringSoon = 0
            var lowStock = 0
            all.forEach { (medicine) ->
                when (medicine.effectiveStatus(today)) {
                    MedicineStatus.EXPIRED -> expired++
                    MedicineStatus.ACTIVE -> {
                        if (medicine.daysUntilExpiry(today) <= expiringWithinDays) expiringSoon++
                        if (medicine.isLowStock) lowStock++
                    }
                    else -> Unit
                }
            }
            DashboardCounts(expired = expired, expiringSoon = expiringSoon, lowStock = lowStock)
        }

    companion object {
        const val DEFAULT_EXPIRING_WINDOW_DAYS = 30L
    }
}

/** One medicine as prescribed for a specific person. */
data class MemberMedicine(
    val details: MedicineDetails,
    val condition: String?,
    val effectiveStatus: MedicineStatus,
)

class ObserveMedicinesForMemberUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val time: TimeProvider,
) {
    /** Everything ever prescribed for the member: in-cabinet items first, then history. */
    operator fun invoke(memberId: Long): Flow<List<MemberMedicine>> =
        medicines.observeAll().map { all ->
            val today = time.today()
            all.mapNotNull { details ->
                val assignment = details.assignments.firstOrNull { it.member.id == memberId } ?: return@mapNotNull null
                MemberMedicine(details, assignment.condition, details.medicine.effectiveStatus(today))
            }.sortedWith(
                compareBy<MemberMedicine> { it.effectiveStatus.ordinal }
                    .thenBy { it.details.medicine.effectiveExpiryDate },
            )
        }
}

/** A storage location and what is currently kept there. [location] null = not assigned. */
data class LocationGroup(
    val location: StorageLocation?,
    val medicines: List<MedicineDetails>,
)

class ObserveMedicinesByLocationUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val locations: StorageLocationRepository,
    private val time: TimeProvider,
) {
    operator fun invoke(): Flow<List<LocationGroup>> =
        combine(locations.observeAll(), medicines.observeAll()) { allLocations, all ->
            val inCabinet = all
                .applyFilter(MedicineFilter(), time.today())
                .groupBy { it.medicine.storageLocationId }
            buildList {
                allLocations.sortedBy { it.name.lowercase() }.forEach { location ->
                    add(LocationGroup(location, inCabinet[location.id].orEmpty()))
                }
                // Medicines whose location id points nowhere are treated as unassigned too.
                val knownIds = allLocations.mapTo(HashSet()) { it.id }
                val unassigned = inCabinet.filterKeys { it == null || it !in knownIds }.values.flatten()
                if (unassigned.isNotEmpty()) add(LocationGroup(null, unassigned))
            }
        }
}
