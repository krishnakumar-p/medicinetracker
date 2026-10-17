package com.medishelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.medishelf.core.database.entity.ConditionSuggestionEntity
import com.medishelf.core.database.entity.FamilyMemberEntity
import com.medishelf.core.database.entity.MedicineEntity
import com.medishelf.core.database.entity.MedicineEventEntity
import com.medishelf.core.database.entity.MedicineMemberCrossRef
import com.medishelf.core.database.entity.MedicinePhotoEntity
import com.medishelf.core.database.entity.StorageLocationEntity

/** Every row in the database, IDs included, as read in one consistent transaction. */
data class DatabaseSnapshot(
    val members: List<FamilyMemberEntity>,
    val locations: List<StorageLocationEntity>,
    val medicines: List<MedicineEntity>,
    val assignments: List<MedicineMemberCrossRef>,
    val photos: List<MedicinePhotoEntity>,
    val events: List<MedicineEventEntity>,
    val suggestions: List<ConditionSuggestionEntity>,
)

@Dao
interface BackupDao {
    @Query("SELECT * FROM family_members") suspend fun members(): List<FamilyMemberEntity>
    @Query("SELECT * FROM storage_locations") suspend fun locations(): List<StorageLocationEntity>
    @Query("SELECT * FROM medicines") suspend fun medicines(): List<MedicineEntity>
    @Query("SELECT * FROM medicine_members") suspend fun assignments(): List<MedicineMemberCrossRef>
    @Query("SELECT * FROM medicine_photos") suspend fun photos(): List<MedicinePhotoEntity>
    @Query("SELECT * FROM medicine_events") suspend fun events(): List<MedicineEventEntity>
    @Query("SELECT * FROM condition_suggestions") suspend fun suggestions(): List<ConditionSuggestionEntity>

    @Insert suspend fun insertMembers(rows: List<FamilyMemberEntity>)
    @Insert suspend fun insertLocations(rows: List<StorageLocationEntity>)
    @Insert suspend fun insertMedicines(rows: List<MedicineEntity>)
    @Insert suspend fun insertAssignments(rows: List<MedicineMemberCrossRef>)
    @Insert suspend fun insertPhotos(rows: List<MedicinePhotoEntity>)
    @Insert suspend fun insertEvents(rows: List<MedicineEventEntity>)
    @Insert suspend fun insertSuggestions(rows: List<ConditionSuggestionEntity>)

    @Query("DELETE FROM medicine_events") suspend fun deleteEvents()
    @Query("DELETE FROM medicine_photos") suspend fun deletePhotos()
    @Query("DELETE FROM medicine_members") suspend fun deleteAssignments()
    @Query("DELETE FROM medicines") suspend fun deleteMedicines()
    @Query("DELETE FROM family_members") suspend fun deleteMembers()
    @Query("DELETE FROM storage_locations") suspend fun deleteLocations()
    @Query("DELETE FROM condition_suggestions") suspend fun deleteSuggestions()

    @Transaction
    suspend fun snapshot() = DatabaseSnapshot(members(), locations(), medicines(), assignments(), photos(), events(), suggestions())

    /** Swaps the whole inventory in one transaction: a failure part-way leaves the old data intact. */
    @Transaction
    suspend fun replaceAll(snapshot: DatabaseSnapshot) {
        deleteEvents(); deletePhotos(); deleteAssignments(); deleteMedicines()
        deleteMembers(); deleteLocations(); deleteSuggestions()
        insertMembers(snapshot.members)
        insertLocations(snapshot.locations)
        insertMedicines(snapshot.medicines)
        insertAssignments(snapshot.assignments)
        insertPhotos(snapshot.photos)
        insertEvents(snapshot.events)
        insertSuggestions(snapshot.suggestions)
    }
}
