package com.medishelf.core.domain.repository

import com.medishelf.core.model.StorageLocation
import kotlinx.coroutines.flow.Flow

interface StorageLocationRepository {
    fun observeAll(): Flow<List<StorageLocation>>
    fun observe(id: Long): Flow<StorageLocation?>
    suspend fun save(location: StorageLocation): Long

    /** Removes the location. Medicines stored there become unassigned, never deleted. */
    suspend fun delete(id: Long)
}
