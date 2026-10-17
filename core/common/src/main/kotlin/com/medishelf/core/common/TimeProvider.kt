package com.medishelf.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Single source of "now" so date logic is deterministic in tests. */
interface TimeProvider {
    fun now(): Instant
    fun today(): LocalDate
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()

    // Resolve the zone on every call so a timezone change is picked up immediately.
    override fun today(): LocalDate = LocalDate.now(Clock.systemDefaultZone())
}
