package com.medishelf.core.domain.validation

import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.StorageLocation
import java.time.LocalDate

enum class FamilyMemberValidationError { NAME_REQUIRED, DATE_OF_BIRTH_IN_FUTURE }

enum class StorageLocationValidationError { NAME_REQUIRED }

fun FamilyMember.validate(today: LocalDate): Set<FamilyMemberValidationError> = buildSet {
    if (name.isBlank()) add(FamilyMemberValidationError.NAME_REQUIRED)
    if (dateOfBirth?.isAfter(today) == true) add(FamilyMemberValidationError.DATE_OF_BIRTH_IN_FUTURE)
}

fun StorageLocation.validate(): Set<StorageLocationValidationError> = buildSet {
    if (name.isBlank()) add(StorageLocationValidationError.NAME_REQUIRED)
}
