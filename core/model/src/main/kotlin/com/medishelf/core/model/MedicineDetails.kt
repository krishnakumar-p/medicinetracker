package com.medishelf.core.model

import java.time.Instant

/** A family member a medicine is prescribed for, and the condition it treats for them. */
data class MedicineAssignment(
    val member: FamilyMember,
    val condition: String? = null,
)

data class MedicinePhoto(
    val id: Long = 0,
    val medicineId: Long,
    val type: PhotoType,
    /** File name inside app-private photo storage, never an absolute path. */
    val fileName: String,
    val createdAt: Instant = Instant.EPOCH,
)

/** Medicine joined with its people, location and photos. */
data class MedicineDetails(
    val medicine: Medicine,
    val assignments: List<MedicineAssignment> = emptyList(),
    val location: StorageLocation? = null,
    val photos: List<MedicinePhoto> = emptyList(),
    /** Resolved [Medicine.prescribedForMemberId]. */
    val prescribedFor: FamilyMember? = null,
) {
    val conditions: List<String>
        get() = assignments.mapNotNull { it.condition?.takeIf(String::isNotBlank) }.distinct()
}

data class MedicineEvent(
    val id: Long = 0,
    val medicineId: Long,
    val type: MedicineEventType,
    val quantityDelta: Double? = null,
    val fromStatus: MedicineStatus? = null,
    val toStatus: MedicineStatus? = null,
    val note: String? = null,
    val at: Instant,
)
