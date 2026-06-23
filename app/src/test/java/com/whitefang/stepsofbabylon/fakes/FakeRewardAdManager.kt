package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.AdPlacement
import com.whitefang.stepsofbabylon.domain.model.AdResult
import com.whitefang.stepsofbabylon.domain.repository.RewardAdManager

/**
 * Test double for [RewardAdManager].
 *
 * Configuration knobs:
 * - [nextResult] — single fallback returned when [resultQueue] is empty.
 *   Default [AdResult.Rewarded].
 * - [resultQueue] — per-call script. First non-empty entry is consumed per
 *   [showRewardAd] call; empty falls back to [nextResult].
 * - [availabilityOverrides] — per-[AdPlacement] availability map.
 *   Keys present here win over [defaultAvailable]. Keys absent use
 *   [defaultAvailable]. Lets a test simulate "rewarded video unavailable for
 *   placement X but ok for Y".
 * - [shown] — append-only log of every [showRewardAd] invocation by placement.
 */
class FakeRewardAdManager : RewardAdManager {
    var nextResult: AdResult = AdResult.Rewarded
    val resultQueue: ArrayDeque<AdResult> = ArrayDeque()
    var defaultAvailable: Boolean = true
    val availabilityOverrides: MutableMap<AdPlacement, Boolean> = mutableMapOf()
    val shown: MutableList<AdPlacement> = mutableListOf()

    override suspend fun showRewardAd(placement: AdPlacement): AdResult {
        shown += placement
        return if (resultQueue.isNotEmpty()) resultQueue.removeFirst() else nextResult
    }

    override fun isAdAvailable(placement: AdPlacement): Boolean = availabilityOverrides[placement] ?: defaultAvailable
}
