package com.medishelf.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.medishelf.core.database.dao.BackupDao
import com.medishelf.core.database.dao.ConditionSuggestionDao
import com.medishelf.core.database.dao.FamilyMemberDao
import com.medishelf.core.database.dao.MedicineDao
import com.medishelf.core.database.dao.StorageLocationDao
import com.medishelf.core.database.entity.ConditionSuggestionEntity
import com.medishelf.core.database.entity.FamilyMemberEntity
import com.medishelf.core.database.entity.MedicineEntity
import com.medishelf.core.database.entity.MedicineEventEntity
import com.medishelf.core.database.entity.MedicineMemberCrossRef
import com.medishelf.core.database.entity.MedicinePhotoEntity
import com.medishelf.core.database.entity.StorageLocationEntity

@Database(
    entities = [
        FamilyMemberEntity::class,
        StorageLocationEntity::class,
        MedicineEntity::class,
        MedicineMemberCrossRef::class,
        MedicinePhotoEntity::class,
        MedicineEventEntity::class,
        ConditionSuggestionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MediShelfDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun storageLocationDao(): StorageLocationDao
    abstract fun conditionSuggestionDao(): ConditionSuggestionDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "medishelf.db"
    }
}
