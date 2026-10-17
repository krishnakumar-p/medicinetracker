package com.medishelf.core.domain.repository

import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicinePhoto
import com.medishelf.core.model.MedicineStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A member link as written by the editor; resolved to [com.medishelf.core.model.MedicineAssignment] on read. */
data class AssignmentInput(val memberId: Long, val condition: String?)

interface MedicineRepository {
    /** Every medicine, including finished and disposed history. */
    fun observeAll(): Flow<List<MedicineDetails>>

    fun observe(id: Long): Flow<MedicineDetails?>

    fun observeEvents(medicineId: Long): Flow<List<MedicineEvent>>

    suspend fun get(id: Long): MedicineDetails?

    /** Most recently updated medicine with this barcode, used to prefill a new entry. */
    suspend fun findLatestByBarcode(barcode: String): Medicine?

    /** ACTIVE medicines whose effective expiry is before [today]. */
    suspend fun getActiveExpiredBefore(today: LocalDate): List<Medicine>

    /**
     * Inserts ([Medicine.id] == 0) or updates the medicine, replaces its member links and
     * appends [event] in one transaction. Returns the medicine id.
     */
    suspend fun save(medicine: Medicine, assignments: List<AssignmentInput>, event: MedicineEvent): Long

    /** Updates quantity and status together and appends [event], atomically. */
    suspend fun updateQuantityAndStatus(id: Long, quantity: Double, status: MedicineStatus, event: MedicineEvent)

    suspend fun updateStatus(id: Long, status: MedicineStatus, event: MedicineEvent)

    suspend fun addPhoto(photo: MedicinePhoto): Long

    suspend fun removePhoto(photoId: Long)
}
