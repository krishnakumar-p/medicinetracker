package com.medishelf.core.domain.reminders

/** Runs the reminder check once, outside the daily schedule (debug tools, after a restore). */
fun interface ReminderTrigger {
    fun runNow()
}
