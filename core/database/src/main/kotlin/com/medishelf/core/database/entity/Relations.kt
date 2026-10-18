package com.medishelf.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AssignmentWithMember(
    @Embedded val crossRef: MedicineMemberCrossRef,
    @Relation(parentColumn = "member_id", entityColumn = "id")
    val member: FamilyMemberEntity,
)

data class MedicineWithRelations(
    @Embedded val medicine: MedicineEntity,
    @Relation(
        entity = MedicineMemberCrossRef::class,
        parentColumn = "id",
        entityColumn = "medicine_id",
    )
    val assignments: List<AssignmentWithMember>,
    @Relation(parentColumn = "storage_location_id", entityColumn = "id")
    val location: StorageLocationEntity?,
    @Relation(parentColumn = "id", entityColumn = "medicine_id")
    val photos: List<MedicinePhotoEntity>,
    @Relation(parentColumn = "prescribed_for_member_id", entityColumn = "id")
    val prescribedFor: FamilyMemberEntity?,
)
