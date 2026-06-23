package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.AdPlacement
import com.whitefang.stepsofbabylon.domain.model.AdResult

interface RewardAdManager {
    suspend fun showRewardAd(placement: AdPlacement): AdResult

    fun isAdAvailable(placement: AdPlacement): Boolean
}
