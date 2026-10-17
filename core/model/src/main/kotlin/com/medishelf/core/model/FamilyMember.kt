package com.medishelf.core.model

import java.time.LocalDate

data class FamilyMember(
    val id: Long = 0,
    val name: String,
    val relationship: String = "",
    val dateOfBirth: LocalDate? = null,
    val allergies: String? = null,
    /** ARGB color used for the member's avatar chip. */
    val avatarColor: Int,
)
