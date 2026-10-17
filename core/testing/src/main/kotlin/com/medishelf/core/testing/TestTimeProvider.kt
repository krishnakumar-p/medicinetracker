package com.medishelf.core.testing

import com.medishelf.core.common.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TestTimeProvider(
    var today: LocalDate = LocalDate.of(2026, 1, 15),
) : TimeProvider {
    private var tick = 0L

    // Strictly increasing so event ordering is deterministic.
    override fun now(): Instant = today.atStartOfDay().toInstant(ZoneOffset.UTC).plusMillis(tick++)
    override fun today(): LocalDate = today
}
