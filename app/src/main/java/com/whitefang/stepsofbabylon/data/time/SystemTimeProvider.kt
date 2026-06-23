package com.whitefang.stepsofbabylon.data.time

import com.whitefang.stepsofbabylon.domain.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [TimeProvider] backed by the real system clock.
 *
 * Tests use `test/fakes/FakeTimeProvider.kt` (introduced in B.1 PR 3) instead.
 */
@Singleton
class SystemTimeProvider
    @Inject
    constructor() : TimeProvider {
        override fun now(): Instant = Instant.now()

        override fun today(): LocalDate = LocalDate.now()
    }
