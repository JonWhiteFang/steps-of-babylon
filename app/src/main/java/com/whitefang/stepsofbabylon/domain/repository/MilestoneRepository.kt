package com.whitefang.stepsofbabylon.domain.repository

/**
 * Domain port over the `milestone` persistence (#227). The presentation layer reads the milestone
 * Flow directly off the DAO (presentation→data, #219); this port covers the use-case surface.
 */
interface MilestoneRepository {
    /** Stable ids (enum names) of all already-claimed milestones. */
    suspend fun getClaimedMilestoneIds(): List<String>

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
