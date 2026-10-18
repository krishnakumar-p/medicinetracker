package com.medishelf.core.domain.usecase

import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.DoseRepository
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.model.DoseLog
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.floor

/** One dose slot of [date]: slot 0 is the first dose of the day. */
data class DueDose(
    val schedule: DoseSchedule,
    val details: MedicineDetails,
    val member: FamilyMember?,
    val slot: Int,
    val log: DoseLog?,
    /** Not enough stock left for a full dose. */
    val outOfStock: Boolean,
    val expired: Boolean,
) {
    val taken: Boolean get() = log != null
}

enum class ScheduleState { UPCOMING, ACTIVE, ENDED }

/** A schedule with what the doses section needs to show about it. */
data class ScheduleOverview(
    val schedule: DoseSchedule,
    val details: MedicineDetails,
    val member: FamilyMember?,
    val state: ScheduleState,
    val takenToday: Int,
    /** Whole days of doses the current stock covers; null when the dose is zero. */
    val daysOfStockLeft: Int?,
    /** Stock runs out before [DoseSchedule.endDate], or within a week for an open-ended schedule. */
    val runsLow: Boolean,
)

data class DoseDay(
    val date: LocalDate,
    val due: List<DueDose>,
    val schedules: List<ScheduleOverview>,
) {
    val takenCount: Int get() = due.count { it.taken }
}

/**
 * Pure planning: which doses are due on [date] and how each schedule stands. Schedules of
 * finished or disposed medicines are listed but have no doses due.
 */
fun planDoses(
    date: LocalDate,
    schedules: List<DoseSchedule>,
    medicines: List<MedicineDetails>,
    members: List<FamilyMember>,
    logs: List<DoseLog>,
): DoseDay {
    val medicineById = medicines.associateBy { it.medicine.id }
    val memberById = members.associateBy { it.id }
    val logByKey = logs.filter { it.date == date }.associateBy { it.scheduleId to it.slot }
    val due = mutableListOf<DueDose>()
    val overviews = mutableListOf<ScheduleOverview>()
    schedules.forEach { schedule ->
        val details = medicineById[schedule.medicineId] ?: return@forEach
        val medicine = details.medicine
        val member = schedule.memberId?.let(memberById::get)
        val state = when {
            date.isBefore(schedule.startDate) -> ScheduleState.UPCOMING
            schedule.endDate != null && date.isAfter(schedule.endDate) -> ScheduleState.ENDED
            else -> ScheduleState.ACTIVE
        }
        val status = medicine.effectiveStatus(date)
        val inCabinet = status == MedicineStatus.ACTIVE || status == MedicineStatus.EXPIRED
        val takenToday = (0 until schedule.timesPerDay).count { (schedule.id to it) in logByKey }
        if (state == ScheduleState.ACTIVE && inCabinet) {
            repeat(schedule.timesPerDay) { slot ->
                val log = logByKey[schedule.id to slot]
                due += DueDose(
                    schedule = schedule,
                    details = details,
                    member = member,
                    slot = slot,
                    log = log,
                    outOfStock = log == null && medicine.quantity < schedule.dosePerIntake,
                    expired = status == MedicineStatus.EXPIRED,
                )
            }
        }
        val daily = schedule.dailyAmount
        val daysLeft = if (daily > 0) floor(medicine.quantity / daily).toInt() else null
        val runsLow = inCabinet && state != ScheduleState.ENDED && daysLeft != null && when (val end = schedule.endDate) {
            null -> daysLeft < LOW_STOCK_DAYS
            else -> date.plusDays(daysLeft.toLong()).isBefore(end)
        }
        overviews += ScheduleOverview(schedule, details, member, state, takenToday, daysLeft, runsLow)
    }
    val memberName: (DueDose) -> String = { it.member?.name.orEmpty().lowercase() }
    return DoseDay(
        date = date,
        due = due.sortedWith(compareBy<DueDose> { it.slot }.thenBy(memberName).thenBy { it.details.medicine.displayName.lowercase() }),
        schedules = overviews.sortedWith(
            compareBy<ScheduleOverview> {
                when (it.state) {
                    ScheduleState.ACTIVE -> 0
                    ScheduleState.UPCOMING -> 1
                    ScheduleState.ENDED -> 2
                }
            }
                .thenBy { it.details.medicine.displayName.lowercase() },
        ),
    )
}

/** An open-ended schedule warns when less than this many days of stock are left. */
const val LOW_STOCK_DAYS = 7

class ObserveDoseDayUseCase @Inject constructor(
    private val doses: DoseRepository,
    private val medicines: MedicineRepository,
    private val members: FamilyMemberRepository,
    private val time: TimeProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(date: Flow<LocalDate> = flowOf(time.today())): Flow<DoseDay> = date.flatMapLatest { day ->
        combine(doses.observeSchedules(), medicines.observeAll(), members.observeAll(), doses.observeLogs(day, day)) {
                schedules, all, people, logs ->
            planDoses(day, schedules, all, people, logs)
        }
    }
}

sealed interface DoseResult {
    data class Taken(val newQuantity: Double, val finished: Boolean) : DoseResult
    data object Undone : DoseResult
    data object AlreadyTaken : DoseResult
    data object NotDue : DoseResult
    data object OutOfStock : DoseResult
    data object NotFound : DoseResult
}

/** Confirms one dose: records it and lowers stock by the dose (never below 0), atomically. */
class TakeDoseUseCase @Inject constructor(
    private val doses: DoseRepository,
    private val medicines: MedicineRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(scheduleId: Long, slot: Int, date: LocalDate = time.today()): DoseResult {
        val schedule = doses.getSchedule(scheduleId) ?: return DoseResult.NotFound
        if (!schedule.isActiveOn(date) || slot !in 0 until schedule.timesPerDay || date.isAfter(time.today())) {
            return DoseResult.NotDue
        }
        if (doses.findLog(scheduleId, date, slot) != null) return DoseResult.AlreadyTaken
        val medicine = medicines.get(schedule.medicineId)?.medicine ?: return DoseResult.NotFound
        if (medicine.status != MedicineStatus.ACTIVE && medicine.status != MedicineStatus.EXPIRED) return DoseResult.OutOfStock
        if (medicine.quantity < schedule.dosePerIntake) return DoseResult.OutOfStock
        val newQuantity = medicine.quantity - schedule.dosePerIntake
        val finished = newQuantity <= 0.0
        val newStatus = if (finished) MedicineStatus.FINISHED else medicine.status
        val now = time.now()
        val recorded = doses.takeDose(
            log = DoseLog(scheduleId = scheduleId, date = date, slot = slot, quantity = schedule.dosePerIntake, takenAt = now),
            medicineId = medicine.id,
            quantity = newQuantity.coerceAtLeast(0.0),
            status = newStatus,
            event = MedicineEvent(
                medicineId = medicine.id,
                type = MedicineEventType.DOSE_TAKEN,
                quantityDelta = -schedule.dosePerIntake,
                fromStatus = medicine.status.takeIf { finished },
                toStatus = newStatus.takeIf { finished },
                at = now,
            ),
        )
        return if (recorded) DoseResult.Taken(newQuantity.coerceAtLeast(0.0), finished) else DoseResult.AlreadyTaken
    }
}

/** Un-ticks a dose taken by mistake and puts its stock back. */
class UndoDoseUseCase @Inject constructor(
    private val doses: DoseRepository,
    private val medicines: MedicineRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(scheduleId: Long, slot: Int, date: LocalDate = time.today()): DoseResult {
        val schedule = doses.getSchedule(scheduleId) ?: return DoseResult.NotFound
        val log = doses.findLog(scheduleId, date, slot) ?: return DoseResult.NotDue
        val medicine = medicines.get(schedule.medicineId)?.medicine ?: return DoseResult.NotFound
        // A dose that finished the pack brings it back into the cabinet.
        val newStatus = if (medicine.status == MedicineStatus.FINISHED) {
            medicine.copy(status = MedicineStatus.ACTIVE).effectiveStatus(time.today())
        } else {
            medicine.status
        }
        doses.undoDose(
            log = log,
            medicineId = medicine.id,
            quantity = medicine.quantity + log.quantity,
            status = newStatus,
            event = MedicineEvent(
                medicineId = medicine.id,
                type = MedicineEventType.DOSE_UNDONE,
                quantityDelta = log.quantity,
                fromStatus = medicine.status.takeIf { it != newStatus },
                toStatus = newStatus.takeIf { it != medicine.status },
                at = time.now(),
            ),
        )
        return DoseResult.Undone
    }
}

enum class ScheduleError { MEDICINE_REQUIRED, DOSE_NOT_POSITIVE, TIMES_PER_DAY_INVALID, END_BEFORE_START }

/** Raw input from the continuous-medication form. */
data class ScheduleDraft(
    val id: Long = 0,
    val medicineId: Long? = null,
    val memberId: Long? = null,
    val dosePerIntake: Double? = null,
    val timesPerDay: Int? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

/**
 * Adds or edits a continuous-medication schedule. Prescription-course schedules are owned by
 * the medicine editor, so editing one here keeps its kind.
 */
class SaveDoseScheduleUseCase @Inject constructor(
    private val doses: DoseRepository,
    private val time: TimeProvider,
) {
    fun validate(draft: ScheduleDraft): Set<ScheduleError> = buildSet {
        if (draft.medicineId == null) add(ScheduleError.MEDICINE_REQUIRED)
        val doseErrors = MedicineValidator.validateDose(draft.dosePerIntake ?: Double.NaN, draft.timesPerDay ?: 0)
        if (MedicineValidationError.DOSE_NOT_POSITIVE in doseErrors) add(ScheduleError.DOSE_NOT_POSITIVE)
        if (MedicineValidationError.DOSES_PER_DAY_INVALID in doseErrors) add(ScheduleError.TIMES_PER_DAY_INVALID)
        val start = draft.startDate ?: time.today()
        if (draft.endDate != null && draft.endDate.isBefore(start)) add(ScheduleError.END_BEFORE_START)
    }

    suspend operator fun invoke(draft: ScheduleDraft): SaveResult<ScheduleError> {
        val errors = validate(draft)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val existing = if (draft.id != 0L) doses.getSchedule(draft.id) else null
        val id = doses.saveSchedule(
            DoseSchedule(
                id = existing?.id ?: 0,
                medicineId = requireNotNull(draft.medicineId),
                memberId = draft.memberId,
                kind = existing?.kind ?: DoseScheduleKind.CONTINUOUS,
                dosePerIntake = requireNotNull(draft.dosePerIntake),
                timesPerDay = requireNotNull(draft.timesPerDay),
                startDate = draft.startDate ?: time.today(),
                endDate = draft.endDate,
                createdAt = existing?.createdAt ?: time.now(),
            ),
        )
        return SaveResult.Saved(id)
    }
}

/**
 * Stops a schedule from tomorrow on, keeping its dose history. A schedule that hasn't started
 * yet has no history, so it is removed instead.
 */
class StopDoseScheduleUseCase @Inject constructor(
    private val doses: DoseRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(scheduleId: Long): Boolean {
        val schedule = doses.getSchedule(scheduleId) ?: return false
        val today = time.today()
        val end = schedule.endDate
        if (schedule.startDate.isAfter(today)) {
            doses.deleteSchedule(scheduleId)
        } else if (end == null || end.isAfter(today)) {
            doses.saveSchedule(schedule.copy(endDate = today))
        } else {
            return false
        }
        return true
    }
}
