package com.medishelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.medishelf.core.database.entity.ConditionSuggestionEntity
import com.medishelf.core.database.entity.FamilyMemberEntity
import com.medishelf.core.database.entity.StorageLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members WHERE id = :id")
    fun observe(id: Long): Flow<FamilyMemberEntity?>

    @Upsert
    suspend fun upsert(member: FamilyMemberEntity): Long

    @Query("DELETE FROM family_members WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface StorageLocationDao {
    @Query("SELECT * FROM storage_locations ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StorageLocationEntity>>

    @Query("SELECT * FROM storage_locations WHERE id = :id")
    fun observe(id: Long): Flow<StorageLocationEntity?>

    @Upsert
    suspend fun upsert(location: StorageLocationEntity): Long

    @Query("DELETE FROM storage_locations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ConditionSuggestionDao {
    @Query("SELECT text FROM condition_suggestions ORDER BY last_used_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<String>>

    @Upsert
    suspend fun upsert(suggestions: List<ConditionSuggestionEntity>)
}
