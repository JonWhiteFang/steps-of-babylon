package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.time.TimeBaseline
import com.whitefang.stepsofbabylon.domain.time.TimeBaselineSource
import com.whitefang.stepsofbabylon.domain.time.TimeReading

/** Test double for [TimeBaselineSource]. Default: null baseline + a fixed reading → evaluate() returns
 *  Trusted with trustedWallClock == the reading's wall (no rollback, no jump). Tests can set [baseline]
 *  / [reading] to drive rollback / forward-jump cases. */
class FakeTimeBaselineSource(
    var baseline: TimeBaseline? = null,
    var reading: TimeReading = TimeReading(elapsedRealtime = 0, wallClock = 0),
) : TimeBaselineSource {
    override fun readTimeBaseline(): TimeBaseline? = baseline

    override fun currentTimeReading(): TimeReading = reading
}
