package com.medishelf.core.domain.usecase

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.DoseRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.StorageLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/** Which part of a record a search term was found in, in display priority order. */
enum class SearchField {
    NAME, COMPOSITION, STRENGTH, CONDITION, PRESCRIBED_FOR, PERSON, DOCTOR, MANUFACTURER, LOCATION,
    DOSAGE, BATCH, BARCODE, NOTES, RELATIONSHIP, ALLERGIES, ROOM,
}

/** [text] is the field's full value, for the UI to show (and highlight) as a snippet. */
data class FieldMatch(val field: SearchField, val text: String)

data class MedicineHit(
    val details: MedicineDetails,
    val effectiveStatus: MedicineStatus,
    val matches: List<FieldMatch>,
    /** On a dose schedule that is running today. */
    val onSchedule: Boolean,
)

data class MemberHit(val member: FamilyMember, val matches: List<FieldMatch>, val medicineCount: Int)

data class LocationHit(val location: StorageLocation, val matches: List<FieldMatch>, val medicineCount: Int)

data class SearchResults(
    val query: String = "",
    val medicines: List<MedicineHit> = emptyList(),
    val members: List<MemberHit> = emptyList(),
    val locations: List<LocationHit> = emptyList(),
) {
    val isEmpty: Boolean get() = medicines.isEmpty() && members.isEmpty() && locations.isEmpty()
    val total: Int get() = medicines.size + members.size + locations.size
}

/**
 * Search across everything recorded: medicines (names, composition, doctor, who and what
 * they're prescribed for, people, location, batch, barcode, notes…), family members and
 * storage locations. Case- and accent-insensitive; every word of the query must be found
 * somewhere in the record. Finished and disposed medicines are included, after the rest.
 */
object GlobalSearch {
    fun search(
        query: String,
        medicines: List<MedicineDetails>,
        members: List<FamilyMember>,
        locations: List<StorageLocation>,
        schedules: List<DoseSchedule>,
        today: LocalDate,
    ): SearchResults = index(medicines, members, locations, schedules, today).search(query)

    /** Normalises every field once, so each keystroke only does substring checks. */
    fun index(
        medicines: List<MedicineDetails>,
        members: List<FamilyMember>,
        locations: List<StorageLocation>,
        schedules: List<DoseSchedule>,
        today: LocalDate,
    ): SearchIndex {
        val inCabinet = medicines.filter { it.medicine.effectiveStatus(today).let { s -> s == MedicineStatus.ACTIVE || s == MedicineStatus.EXPIRED } }
        val scheduled = schedules.filter { it.isActiveOn(today) }.mapTo(HashSet()) { it.medicineId }
        val countByMember = inCabinet.flatMap { d -> d.assignments.map { it.member.id } }.groupingBy { it }.eachCount()
        val countByLocation = inCabinet.groupingBy { it.medicine.storageLocationId }.eachCount()
        return SearchIndex(
            medicines = medicines.map { details ->
                Entry(
                    MedicineHit(details, details.medicine.effectiveStatus(today), emptyList(), details.medicine.id in scheduled),
                    details.fields().keyed(),
                )
            },
            members = members.sortedBy { it.name.lowercase(Locale.ROOT) }.map { member ->
                Entry(
                    MemberHit(member, emptyList(), countByMember[member.id] ?: 0),
                    listOf(
                        FieldMatch(SearchField.NAME, member.name),
                        FieldMatch(SearchField.RELATIONSHIP, member.relationship),
                        FieldMatch(SearchField.ALLERGIES, member.allergies.orEmpty()),
                    ).keyed(),
                )
            },
            locations = locations.sortedBy { it.name.lowercase(Locale.ROOT) }.map { location ->
                Entry(
                    LocationHit(location, emptyList(), countByLocation[location.id] ?: 0),
                    listOf(
                        FieldMatch(SearchField.NAME, location.name),
                        FieldMatch(SearchField.ROOM, location.room),
                        FieldMatch(SearchField.NOTES, location.notes),
                    ).keyed(),
                )
            },
        )
    }

    internal class Entry<T>(val hit: T, val fields: List<Pair<FieldMatch, String>>)

    // Also keep each key without spaces, so "500mg" finds "500 mg".
    private fun List<FieldMatch>.keyed(): List<Pair<FieldMatch, String>> =
        filter { it.text.isNotBlank() }.map { it to normalize(it.text).let { k -> "$k ${k.replace(" ", "")}" } }

    private fun MedicineDetails.fields(): List<FieldMatch> = buildList {
        val m = medicine
        add(FieldMatch(SearchField.NAME, m.brandName))
        add(FieldMatch(SearchField.COMPOSITION, m.genericName))
        add(FieldMatch(SearchField.STRENGTH, m.strength))
        conditions.forEach { add(FieldMatch(SearchField.CONDITION, it)) }
        val prescribedFor = listOfNotNull(prescribedFor?.name, m.prescribedForReason.takeIf(String::isNotBlank))
        if (prescribedFor.isNotEmpty()) add(FieldMatch(SearchField.PRESCRIBED_FOR, prescribedFor.joinToString(" · ")))
        assignments.forEach { add(FieldMatch(SearchField.PERSON, it.member.name)) }
        add(FieldMatch(SearchField.DOCTOR, m.prescribedBy))
        add(FieldMatch(SearchField.MANUFACTURER, m.manufacturer))
        location?.let { add(FieldMatch(SearchField.LOCATION, listOf(it.name, it.room).filter(String::isNotBlank).joinToString(" · "))) }
        add(FieldMatch(SearchField.DOSAGE, m.dosageInstructions))
        add(FieldMatch(SearchField.BATCH, m.batchNumber))
        add(FieldMatch(SearchField.BARCODE, m.barcode.orEmpty()))
        add(FieldMatch(SearchField.NOTES, m.notes))
    }

    /** Lower case, accents removed, punctuation folded to spaces ("Dr. Hana" -> "dr hana"). */
    internal fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}%/+]+"), " ")
            .trim()
}

class SearchIndex internal constructor(
    private val medicines: List<GlobalSearch.Entry<MedicineHit>>,
    private val members: List<GlobalSearch.Entry<MemberHit>>,
    private val locations: List<GlobalSearch.Entry<LocationHit>>,
) {
    fun search(query: String): SearchResults {
        val terms = GlobalSearch.normalize(query).split(' ').filter(String::isNotEmpty)
        if (terms.isEmpty()) return SearchResults(query)
        val medicineHits = medicines.mapNotNull { e -> match(terms, e.fields)?.let { e.hit.copy(matches = it) } }.sortedWith(
            compareBy<MedicineHit> { it.effectiveStatus != MedicineStatus.ACTIVE && it.effectiveStatus != MedicineStatus.EXPIRED }
                .thenBy { it.matches.first().field.ordinal }
                .thenBy { it.details.medicine.displayName.lowercase(Locale.ROOT) },
        )
        return SearchResults(
            query = query,
            medicines = medicineHits,
            members = members.mapNotNull { e -> match(terms, e.fields)?.let { e.hit.copy(matches = it) } },
            locations = locations.mapNotNull { e -> match(terms, e.fields)?.let { e.hit.copy(matches = it) } },
        )
    }

    /** Fields containing a term, or null unless every term is found in at least one field. */
    private fun match(terms: List<String>, keyed: List<Pair<FieldMatch, String>>): List<FieldMatch>? {
        if (!terms.all { term -> keyed.any { (_, key) -> term in key } }) return null
        return keyed.filter { (_, key) -> terms.any { it in key } }.map { it.first }.distinct()
    }
}

class ObserveGlobalSearchUseCase @Inject constructor(
    private val medicines: MedicineRepository,
    private val members: FamilyMemberRepository,
    private val locations: StorageLocationRepository,
    private val doses: DoseRepository,
    private val time: TimeProvider,
) {
    operator fun invoke(query: Flow<String>): Flow<SearchResults> {
        // Re-indexed only when the data changes; typing just runs the substring checks.
        val index = combine(
            medicines.observeAll(),
            members.observeAll(),
            locations.observeAll(),
            doses.observeSchedules(),
        ) { all, people, places, schedules -> GlobalSearch.index(all, people, places, schedules, time.today()) }
        return combine(query, index) { q, i -> i.search(q) }
    }
}
