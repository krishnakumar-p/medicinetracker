package com.medishelf.core.data.repository

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.database.dao.ConditionSuggestionDao
import com.medishelf.core.database.dao.FamilyMemberDao
import com.medishelf.core.database.dao.StorageLocationDao
import com.medishelf.core.database.entity.ConditionSuggestionEntity
import com.medishelf.core.domain.repository.ConditionSuggestionRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.StorageLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class RoomFamilyMemberRepository @Inject constructor(
    private val dao: FamilyMemberDao,
) : FamilyMemberRepository {
    override fun observeAll(): Flow<List<FamilyMember>> = dao.observeAll().map { rows -> rows.map { it.asModel() } }
    override fun observe(id: Long): Flow<FamilyMember?> = dao.observe(id).map { it?.asModel() }

    // @Upsert returns -1 when it updated an existing row.
    override suspend fun save(member: FamilyMember): Long =
        dao.upsert(member.asEntity()).takeIf { it != -1L } ?: member.id

    override suspend fun delete(id: Long) = dao.delete(id)
}

internal class RoomStorageLocationRepository @Inject constructor(
    private val dao: StorageLocationDao,
) : StorageLocationRepository {
    override fun observeAll(): Flow<List<StorageLocation>> = dao.observeAll().map { rows -> rows.map { it.asModel() } }
    override fun observe(id: Long): Flow<StorageLocation?> = dao.observe(id).map { it?.asModel() }

    override suspend fun save(location: StorageLocation): Long =
        dao.upsert(location.asEntity()).takeIf { it != -1L } ?: location.id

    override suspend fun delete(id: Long) = dao.delete(id)
}

internal class RoomConditionSuggestionRepository @Inject constructor(
    private val dao: ConditionSuggestionDao,
    private val time: TimeProvider,
) : ConditionSuggestionRepository {
    override fun observeRecent(limit: Int): Flow<List<String>> = dao.observeRecent(limit)

    override suspend fun recordUsed(conditions: Collection<String>) {
        val now = time.now()
        val rows = conditions
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
            .map { ConditionSuggestionEntity(text = it, lastUsedAt = now) }
        if (rows.isNotEmpty()) dao.upsert(rows)
    }
}
