package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import kotlinx.coroutines.flow.Flow

interface WalkingEncounterRepository {
    fun observeUnclaimed(): Flow<List<SupplyDrop>>
    fun observeHistory(limit: Int): Flow<List<SupplyDrop>>
    fun countUnclaimed(): Flow<Int>
    suspend fun getUnclaimedCount(): Int
    suspend fun createDrop(trigger: SupplyDropTrigger, reward: SupplyDropReward, rewardAmount: Int): Long

    /**
     * Atomically marks the drop [id] as claimed (#122). Returns `true` iff this call transitioned
     * the drop from unclaimed → claimed; `false` if it was already claimed. Callers MUST credit
     * the reward only when this returns `true` — the mark-first ordering closes the double-credit
     * window where two rapid taps both read `claimed = false` and both credit.
     */
    suspend fun claimDrop(id: Int): Boolean
    suspend fun enforceInboxCap(maxSize: Int)
}
