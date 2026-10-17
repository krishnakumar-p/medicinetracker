package com.medishelf.core.model

enum class ExpiryWindow(val days: Long?) {
    ANY(null),
    EXPIRED(null),
    WITHIN_7_DAYS(7),
    WITHIN_30_DAYS(30),
    WITHIN_90_DAYS(90),
}

enum class MedicineSort { EXPIRY_ASCENDING, NAME_ASCENDING }

data class MedicineFilter(
    /** Matched case-insensitively against brand, generic, conditions and member names. */
    val query: String = "",
    val memberIds: Set<Long> = emptySet(),
    val locationIds: Set<Long> = emptySet(),
    /** Compared with the effective status. Defaults to what is still in the cabinet. */
    val statuses: Set<MedicineStatus> = DEFAULT_STATUSES,
    val forms: Set<MedicineForm> = emptySet(),
    val expiryWindow: ExpiryWindow = ExpiryWindow.ANY,
    val lowStockOnly: Boolean = false,
    val sort: MedicineSort = MedicineSort.EXPIRY_ASCENDING,
) {
    /** Number of narrowing filters set (search and sort excluded), for the "Filters (n)" badge. */
    val activeFilterCount: Int
        get() = listOf(
            memberIds.isNotEmpty(),
            locationIds.isNotEmpty(),
            statuses != DEFAULT_STATUSES,
            forms.isNotEmpty(),
            expiryWindow != ExpiryWindow.ANY,
            lowStockOnly,
        ).count { it }

    companion object {
        val DEFAULT_STATUSES = setOf(MedicineStatus.ACTIVE, MedicineStatus.EXPIRED)
    }
}

data class DashboardCounts(
    val expired: Int = 0,
    val expiringSoon: Int = 0,
    val lowStock: Int = 0,
)
