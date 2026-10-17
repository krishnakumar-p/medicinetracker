package com.medishelf.core.domain.repository

import com.medishelf.core.model.FamilyMember
import kotlinx.coroutines.flow.Flow

interface FamilyMemberRepository {
    fun observeAll(): Flow<List<FamilyMember>>
    fun observe(id: Long): Flow<FamilyMember?>
    suspend fun save(member: FamilyMember): Long

    /** Removes the member and their prescription links. Medicines themselves are kept. */
    suspend fun delete(id: Long)
}
