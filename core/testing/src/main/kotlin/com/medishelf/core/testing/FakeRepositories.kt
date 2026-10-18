package com.medishelf.core.testing

import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.domain.repository.ConditionSuggestionRepository
import com.medishelf.core.domain.repository.DoseRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.model.DoseLog
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineAssignment
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicinePhoto
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.StorageLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FakeFamilyMemberRepository : FamilyMemberRepository {
    val members = MutableStateFlow<Map<Long, FamilyMember>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<FamilyMember>> = members.map { it.values.sortedBy(FamilyMember::name) }
    override fun observe(id: Long): Flow<FamilyMember?> = members.map { it[id] }

    override suspend fun save(member: FamilyMember): Long {
        val id = if (member.id == 0L) nextId++ else member.id
        members.update { it + (id to member.copy(id = id)) }
        return id
    }

    override suspend fun delete(id: Long) = members.update { it - id }
}

class FakeStorageLocationRepository : StorageLocationRepository {
    val locations = MutableStateFlow<Map<Long, StorageLocation>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<StorageLocation>> = locations.map { it.values.toList() }
    override fun observe(id: Long): Flow<StorageLocation?> = locations.map { it[id] }

    override suspend fun save(location: StorageLocation): Long {
        val id = if (location.id == 0L) nextId++ else location.id
        locations.update { it + (id to location.copy(id = id)) }
        return id
    }

    override suspend fun delete(id: Long) = locations.update { it - id }
}

class FakeConditionSuggestionRepository : ConditionSuggestionRepository {
    val recent = MutableStateFlow<List<String>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<String>> = recent.map { it.take(limit) }

    override suspend fun recordUsed(conditions: Collection<String>) = recent.update { current ->
        (conditions.toList() + current).distinctBy(String::lowercase)
    }
}

/** In-memory medicine store that resolves members/locations like the Room implementation. */
class FakeMedicineRepository(
    private val members: FakeFamilyMemberRepository = FakeFamilyMemberRepository(),
    private val locations: FakeStorageLocationRepository = FakeStorageLocationRepository(),
) : MedicineRepository {
    private val medicines = MutableStateFlow<Map<Long, Medicine>>(emptyMap())
    private val links = MutableStateFlow<Map<Long, List<AssignmentInput>>>(emptyMap())
    private val photos = MutableStateFlow<List<MedicinePhoto>>(emptyList())
    val events = MutableStateFlow<List<MedicineEvent>>(emptyList())
    private var nextId = 1L
    private var nextPhotoId = 1L

    private val details: Flow<List<MedicineDetails>> =
        combine(medicines, links, photos, members.members, locations.locations) { meds, links, photos, people, places ->
            meds.values.map { medicine ->
                MedicineDetails(
                    medicine = medicine,
                    assignments = links[medicine.id].orEmpty().mapNotNull { link ->
                        people[link.memberId]?.let { MedicineAssignment(it, link.condition) }
                    },
                    location = medicine.storageLocationId?.let(places::get),
                    photos = photos.filter { it.medicineId == medicine.id },
                    prescribedFor = medicine.prescribedForMemberId?.let(people::get),
                )
            }
        }

    /** Seeds a medicine directly, bypassing use cases. */
    fun put(medicine: Medicine, assignments: List<AssignmentInput> = emptyList()): Long {
        val id = if (medicine.id == 0L) nextId++ else medicine.id.also { nextId = maxOf(nextId, it + 1) }
        medicines.update { it + (id to medicine.copy(id = id)) }
        links.update { it + (id to assignments) }
        return id
    }

    override fun observeAll(): Flow<List<MedicineDetails>> = details
    override fun observe(id: Long): Flow<MedicineDetails?> = details.map { all -> all.find { it.medicine.id == id } }
    override fun observeEvents(medicineId: Long): Flow<List<MedicineEvent>> =
        events.map { all -> all.filter { it.medicineId == medicineId }.reversed() }

    override suspend fun get(id: Long): MedicineDetails? {
        val medicine = medicines.value[id] ?: return null
        return MedicineDetails(
            medicine = medicine,
            assignments = links.value[id].orEmpty().mapNotNull { link ->
                members.members.value[link.memberId]?.let { MedicineAssignment(it, link.condition) }
            },
            location = medicine.storageLocationId?.let(locations.locations.value::get),
            prescribedFor = medicine.prescribedForMemberId?.let(members.members.value::get),
        )
    }

    override suspend fun findLatestByBarcode(barcode: String): Medicine? =
        medicines.value.values.filter { it.barcode == barcode }.maxByOrNull { it.updatedAt }

    override suspend fun getActiveExpiredBefore(today: LocalDate): List<Medicine> =
        medicines.value.values.filter { it.status == MedicineStatus.ACTIVE && it.effectiveExpiryDate.isBefore(today) }

    override suspend fun save(medicine: Medicine, assignments: List<AssignmentInput>, event: MedicineEvent): Long {
        val id = put(medicine, assignments)
        events.update { it + event.copy(medicineId = id) }
        return id
    }

    override suspend fun updateQuantityAndStatus(id: Long, quantity: Double, status: MedicineStatus, event: MedicineEvent) {
        medicines.update { all -> all + (id to all.getValue(id).copy(quantity = quantity, status = status)) }
        events.update { it + event }
    }

    override suspend fun updateStatus(id: Long, status: MedicineStatus, event: MedicineEvent) {
        medicines.update { all -> all + (id to all.getValue(id).copy(status = status)) }
        events.update { it + event }
    }

    override suspend fun addPhoto(photo: MedicinePhoto): Long {
        val id = nextPhotoId++
        photos.update { it + photo.copy(id = id) }
        return id
    }

    override suspend fun removePhoto(photoId: Long) = photos.update { all -> all.filterNot { it.id == photoId } }
}

class FakePhotoStore : com.medishelf.core.domain.repository.PhotoStore {
    val files = mutableSetOf<String>()
    var failNextImport = false
    private var counter = 0

    override suspend fun import(sourceUri: String): String {
        if (failNextImport) {
            failNextImport = false
            error("decode failed")
        }
        return "photo${++counter}.jpg".also(files::add)
    }

    override fun absolutePath(fileName: String): String = "/fake/photos/$fileName"
    override val directoryPath: String = "/fake/photos"

    override suspend fun delete(fileName: String) {
        files.remove(fileName)
    }
}

class FakeSettingsRepository(initial: com.medishelf.core.model.UserSettings = com.medishelf.core.model.UserSettings()) :
    com.medishelf.core.domain.repository.SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<com.medishelf.core.model.UserSettings> = state

    override suspend fun acceptDisclaimer(version: Int) = state.update { it.copy(disclaimerAcceptedVersion = version) }

    override suspend fun updateAppLock(
        transform: (com.medishelf.core.model.AppLockSettings) -> com.medishelf.core.model.AppLockSettings,
    ) = state.update { it.copy(appLock = transform(it.appLock)) }

    override suspend fun setThemeMode(mode: com.medishelf.core.model.ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun updateReminders(
        transform: (com.medishelf.core.model.ReminderSettings) -> com.medishelf.core.model.ReminderSettings,
    ) = state.update { it.copy(reminders = transform(it.reminders)) }
}

/** Plain-text PIN fake with the real lockout policy. */
class FakeAppLockCredentials(private val time: TestTimeProvider = TestTimeProvider()) :
    com.medishelf.core.domain.repository.AppLockCredentials {
    var pin: String? = null
    private var failed = 0
    private var until: java.time.Instant? = null
    private val hasPinState = MutableStateFlow(false)
    override val hasPin: Flow<Boolean> = hasPinState

    override suspend fun setPin(pin: String) {
        this.pin = pin
        failed = 0
        until = null
        hasPinState.value = true
    }

    override suspend fun verify(pin: String): com.medishelf.core.domain.repository.PinCheckResult {
        val now = time.now()
        until?.takeIf { it.isAfter(now) }?.let { return com.medishelf.core.domain.repository.PinCheckResult.LockedOut(it) }
        if (pin == this.pin) {
            failed = 0
            return com.medishelf.core.domain.repository.PinCheckResult.Correct
        }
        failed++
        until = com.medishelf.core.domain.security.LockoutPolicy.lockoutAfter(failed, now)
        return until?.let { com.medishelf.core.domain.repository.PinCheckResult.LockedOut(it) }
            ?: com.medishelf.core.domain.repository.PinCheckResult.Wrong(com.medishelf.core.domain.security.LockoutPolicy.attemptsBeforeLockout(failed))
    }

    override suspend fun lockedOutUntil(): java.time.Instant? = until?.takeIf { it.isAfter(time.now()) }

    override suspend fun clear() {
        pin = null
        hasPinState.value = false
    }
}

class FakeReminderLedgerRepository : com.medishelf.core.domain.reminders.ReminderLedgerRepository {
    var ledger = com.medishelf.core.domain.reminders.ReminderLedger()
    override suspend fun get() = ledger
    override suspend fun save(ledger: com.medishelf.core.domain.reminders.ReminderLedger) {
        this.ledger = ledger
    }
}

class FakeDemoData : com.medishelf.core.domain.demo.DemoData {
    val loaded = MutableStateFlow(false)
    var forgotten = false
    override val isLoaded: Flow<Boolean> = loaded
    override suspend fun load() { loaded.value = true }
    override suspend fun clear() { loaded.value = false }
    override suspend fun forget() { forgotten = true; loaded.value = false }
}

/** In-memory documents keyed by URI string. */
class FakeDocumentStorage : com.medishelf.core.domain.backup.DocumentStorage {
    val files = mutableMapOf<String, ByteArray>()
    override suspend fun <T> write(uri: String, block: suspend (java.io.OutputStream) -> T): T {
        val out = java.io.ByteArrayOutputStream()
        return block(out).also { files[uri] = out.toByteArray() }
    }
    override suspend fun <T> read(uri: String, block: suspend (java.io.InputStream) -> T): T =
        block(java.io.ByteArrayInputStream(files.getValue(uri)))
}

/** In-memory schedules and dose log; stock changes go through [medicines] like the Room DAO. */
class FakeDoseRepository(
    private val medicines: FakeMedicineRepository = FakeMedicineRepository(),
) : DoseRepository {
    val schedules = MutableStateFlow<Map<Long, DoseSchedule>>(emptyMap())
    val logs = MutableStateFlow<List<DoseLog>>(emptyList())
    private var nextId = 1L
    private var nextLogId = 1L

    override fun observeSchedules(): Flow<List<DoseSchedule>> = schedules.map { it.values.sortedBy(DoseSchedule::id) }

    override fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<DoseLog>> =
        logs.map { all -> all.filter { !it.date.isBefore(from) && !it.date.isAfter(to) } }

    override suspend fun getSchedule(id: Long): DoseSchedule? = schedules.value[id]

    override suspend fun findSchedule(medicineId: Long, kind: DoseScheduleKind): DoseSchedule? =
        schedules.value.values.firstOrNull { it.medicineId == medicineId && it.kind == kind }

    override suspend fun saveSchedule(schedule: DoseSchedule): Long {
        val id = if (schedule.id == 0L) nextId++ else schedule.id
        schedules.update { it + (id to schedule.copy(id = id)) }
        return id
    }

    override suspend fun deleteSchedule(id: Long) {
        schedules.update { it - id }
        logs.update { all -> all.filterNot { it.scheduleId == id } }
    }

    override suspend fun findLog(scheduleId: Long, date: LocalDate, slot: Int): DoseLog? =
        logs.value.firstOrNull { it.scheduleId == scheduleId && it.date == date && it.slot == slot }

    override suspend fun takeDose(log: DoseLog, medicineId: Long, quantity: Double, status: MedicineStatus, event: MedicineEvent): Boolean {
        if (findLog(log.scheduleId, log.date, log.slot) != null) return false
        logs.update { it + log.copy(id = nextLogId++) }
        medicines.updateQuantityAndStatus(medicineId, quantity, status, event)
        return true
    }

    override suspend fun undoDose(log: DoseLog, medicineId: Long, quantity: Double, status: MedicineStatus, event: MedicineEvent) {
        logs.update { all -> all.filterNot { it.id == log.id } }
        medicines.updateQuantityAndStatus(medicineId, quantity, status, event)
    }
}
