package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import java.time.Instant
import java.time.LocalDate

/**
 * Mutable test double for [TimeProvider]. Tests set [fixedDate] and [fixedInstant]
 * to control what the production code sees as "today" / "now". Either the date
 * or the instant may be advanced independently — callers that only care about
 * day buckets should bump [fixedDate]; callers that care about millisecond-
 * precision behaviour should bump [fixedInstant].
 *
 * Default state: 2026-05-07, midnight UTC (an arbitrary fixed point chosen to
 * make tests stable across years). Tests are free to override.
 */
class FakeTimeProvider(
    var fixedDate: LocalDate = LocalDate.of(2026, 5, 7),
    var fixedInstant: Instant = Instant.parse("2026-05-07T00:00:00Z"),
) : TimeProvider {
    override fun now(): Instant = fixedInstant
    override fun today(): LocalDate = fixedDate
}
