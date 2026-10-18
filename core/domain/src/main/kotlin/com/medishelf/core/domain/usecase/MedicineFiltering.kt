package com.medishelf.core.domain.usecase

import com.medishelf.core.model.ExpiryWindow
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineFilter
import com.medishelf.core.model.MedicineSort
import java.time.LocalDate
import java.util.Locale

/**
 * In-memory filter and sort. A household inventory is at most a few thousand rows, so this
 * stays well under a frame and keeps the rules in pure, unit-tested Kotlin.
 */
fun List<MedicineDetails>.applyFilter(filter: MedicineFilter, today: LocalDate): List<MedicineDetails> {
    val terms = filter.query.normalized().split(' ').filter(String::isNotEmpty)
    return asSequence()
        .filter { filter.statuses.isEmpty() || it.medicine.effectiveStatus(today) in filter.statuses }
        .filter { filter.forms.isEmpty() || it.medicine.form in filter.forms }
        .filter { filter.locationIds.isEmpty() || it.medicine.storageLocationId in filter.locationIds }
        .filter { details ->
            filter.memberIds.isEmpty() || details.assignments.any { it.member.id in filter.memberIds }
        }
        .filter { it.matchesWindow(filter.expiryWindow, today) }
        .filter { !filter.lowStockOnly || it.medicine.isLowStock }
        .filter { details -> terms.isEmpty() || details.searchText().let { text -> terms.all(text::contains) } }
        .sortedWith(comparatorFor(filter.sort))
        .toList()
}

private fun MedicineDetails.matchesWindow(window: ExpiryWindow, today: LocalDate): Boolean {
    val days = medicine.daysUntilExpiry(today)
    return when (window) {
        ExpiryWindow.ANY -> true
        ExpiryWindow.EXPIRED -> days < 0
        else -> days in 0..window.days!!
    }
}

private fun MedicineDetails.searchText(): String = buildString {
    append(medicine.brandName).append(' ')
    append(medicine.genericName).append(' ')
    append(medicine.manufacturer).append(' ')
    append(medicine.prescribedBy).append(' ')
    append(medicine.prescribedForReason).append(' ')
    prescribedFor?.let { append(it.name).append(' ') }
    assignments.forEach { append(it.member.name).append(' ').append(it.condition.orEmpty()).append(' ') }
}.normalized()

private fun String.normalized() = trim().lowercase(Locale.ROOT)

private val nameKey: (MedicineDetails) -> String = { it.medicine.displayName.lowercase(Locale.ROOT) }

private fun comparatorFor(sort: MedicineSort): Comparator<MedicineDetails> = when (sort) {
    MedicineSort.EXPIRY_ASCENDING -> compareBy<MedicineDetails> { it.medicine.effectiveExpiryDate }.thenBy(nameKey)
    MedicineSort.NAME_ASCENDING -> compareBy(nameKey).thenBy { it.medicine.effectiveExpiryDate }
}
