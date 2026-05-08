package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {

    @Query("SELECT * FROM milestone")
    fun getAll(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestone")
    suspend fun getAllOnce(): List<MilestoneEntity>

    @Query("SELECT * FROM milestone WHERE milestoneId = :id")
    suspend fun getByIdOnce(id: String): MilestoneEntity?

    @Upsert
    suspend fun upsert(entity: MilestoneEntity)

    /**
     * Atomically marks [milestoneId] as claimed and credits [gems] + [powerStones] to the
     * player's wallet in a single SQLite transaction. Returns `true` iff this call
     * transitioned the milestone from unclaimed → claimed (i.e. the reward was actually
     * credited); `false` when the milestone was already claimed.
     *
     * Post-RO-02 (B.2 PR 4): closes two previously-open correctness windows in
     * [com.whitefang.stepsofbabylon.domain.usecase.ClaimMilestone]:
     *  1. **Partial-failure gap** — a crash between the `addGems` / `addPowerStones` credits
     *     and the follow-up `upsert(... claimed = true)` could leave the wallet credited
     *     without the milestone marked as claimed, allowing double-credit on retry (or the
     *     reverse — milestone marked but no reward paid).
     *  2. **Double-claim race** — two concurrent claim calls could both read `claimed = false`
     *     and both credit the reward. Serialising the read-modify-write inside a Room
     *     transaction (SQLite's default SERIALIZABLE isolation) means the second transaction
     *     observes `claimed = true` and short-circuits to `false`.
     *
     * Room wraps this default-method body in a single transaction; the cross-DAO calls to
     * [playerDao] are safe because Room's transaction tracker is scoped to the underlying
     * [androidx.room.RoomDatabase], not to a specific DAO instance — both DAO calls share
     * the same SQLite transaction. Mirrors the pattern established by
     * [WorkshopDao.purchaseUpgradeAtomic] (B.2 PR 1) and
     * [DailyStepDao.creditBattleStepsAtomic] (B.2 PR 2).
     *
     * @param milestoneId stable identifier (enum name) for the milestone being claimed.
     * @param gems total Gems reward to credit; `0` or negative is a no-op on the gem side.
     * @param powerStones total Power Stones reward to credit; `0` or negative is a no-op.
     * @param claimedAt epoch-millis timestamp to record on the entity.
     * @param playerDao the player-profile DAO that owns the wallet + lifetime-counter writes.
     * @return `true` if the milestone transitioned to claimed and rewards were credited;
     *         `false` if it was already claimed (no writes performed in that case).
     */
    @Transaction
    suspend fun claimMilestoneAtomic(
        milestoneId: String,
        gems: Long,
        powerStones: Long,
        claimedAt: Long,
        playerDao: PlayerProfileDao,
    ): Boolean {
        val existing = getByIdOnce(milestoneId)
        if (existing?.claimed == true) return false
        upsert(
            MilestoneEntity(
                milestoneId = milestoneId,
                claimed = true,
                claimedAt = claimedAt,
            ),
        )
        if (gems > 0L) {
            playerDao.adjustGems(gems)
            playerDao.incrementGemsEarned(gems)
        }
        if (powerStones > 0L) {
            playerDao.adjustPowerStones(powerStones)
            playerDao.incrementPowerStonesEarned(powerStones)
        }
        return true
    }
}
