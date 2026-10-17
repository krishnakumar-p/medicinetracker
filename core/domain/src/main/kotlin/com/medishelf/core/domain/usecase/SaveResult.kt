package com.medishelf.core.domain.usecase

sealed interface SaveResult<out E> {
    data class Saved(val id: Long) : SaveResult<Nothing>
    data class Invalid<E>(val errors: Set<E>) : SaveResult<E>
}
