package com.medishelf.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * Where a schedule came from: added in the continuous-medication section, or the dose of a
 * prescription course (one per medicine, ending on the prescription end date).
 */
enum class DoseScheduleKind { CONTINUOUS, PRESCRIPTION }

/**
 * A regular dose of one medicine. Doses are due [timesPerDay] times a day from [startDate]
 * to [endDate] (open-ended when null), and stock drops only when a dose is confirmed as taken.
 */
data class DoseSchedule(
    val id: Long = 0,
    val medicineId: Long,
    /** Who takes it; null when not recorded. */
    val memberId: Long? = null,
    val kind: DoseScheduleKind = DoseScheduleKind.CONTINUOUS,
    /** Amount per dose, in the medicine's unit. */
    val dosePerIntake: Double,
    val timesPerDay: Int,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val createdAt: Instant = Instant.EPOCH,
) {
    fun isActiveOn(date: LocalDate): Boolean =
        !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))

    val dailyAmount: Double get() = dosePerIntake * timesPerDay

    companion object {
        const val MAX_TIMES_PER_DAY = 12
    }
}

/** One confirmed dose: slot [slot] (0-based) of [date] for [scheduleId], and how much stock it used. */
data class DoseLog(
    val id: Long = 0,
    val scheduleId: Long,
    val date: LocalDate,
    val slot: Int,
    val quantity: Double,
    val takenAt: Instant,
)
