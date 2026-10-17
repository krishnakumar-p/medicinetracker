package com.medishelf.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface ConditionSuggestionRepository {
    /** Most recently used conditions first. */
    fun observeRecent(limit: Int = 20): Flow<List<String>>

    suspend fun recordUsed(conditions: Collection<String>)
}
