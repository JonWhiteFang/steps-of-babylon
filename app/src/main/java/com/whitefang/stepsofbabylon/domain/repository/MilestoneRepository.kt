package com.whitefang.stepsofbabylon.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain port over the `milestone` persistence (#227). #219: now also exposes the reactive
 * claimed-id stream the presentation layer used to read off the raw DAO (`milestoneDao.getAll()`),
 * so no ViewModel injects `MilestoneDao` directly.
 */
interface MilestoneRepository {
    /** Stable ids (enum names) of all already-claimed milestones. */
    suspend fun getClaimedMilestoneIds(): List<String>

    /** Reactive set of already-claimed milestone ids (Home/Missions only need the claimed set). */
    fun observeClaimedMilestoneIds(): Flow<Set<String>>

    /**
     * Atomically marks [milestoneId] claimed and credits the wallet in a single Room transaction.
     * Returns `true` iff this call transitioned unclaimed→claimed (reward actually credited),
     * `false` if it was already claimed. The impl injects `PlayerProfileDao` and hands it into the
     * DAO `@Transaction` so the credit stays inside the one DB-scoped transaction (#122/ADR-0027).
     */
    suspend fun claimMilestoneAtomic(
        milestoneId: String,
        gems: Long,
        powerStones: Long,
        claimedAt: Long,
    ): Boolean
}
