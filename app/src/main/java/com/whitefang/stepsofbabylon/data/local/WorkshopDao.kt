package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkshopDao {
    @Query("SELECT * FROM workshop_upgrade")
    fun getAll(): Flow<List<WorkshopUpgradeEntity>>

    @Query("SELECT * FROM workshop_upgrade WHERE upgradeType = :upgradeType")
    fun getByType(upgradeType: String): Flow<WorkshopUpgradeEntity?>

    @Query("SELECT * FROM workshop_upgrade WHERE upgradeType IN (:types)")
    fun getByCategory(types: List<String>): Flow<List<WorkshopUpgradeEntity>>

    @Upsert
    suspend fun upsert(entity: WorkshopUpgradeEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WorkshopUpgradeEntity>)

    /**
     * Atomically spends [cost] Steps via [playerDao] and sets the workshop upgrade level to [newLevel]
     * in a single SQLite transaction. If the player cannot afford the cost the transaction is a no-op.
     *
     * This closes two previously-open correctness windows:
     *  1. Partial-failure gap: a crash between `spendSteps` and `setUpgradeLevel` could previously
     *     charge the player without crediting the upgrade. Both writes now succeed or neither does.
     *  2. Double-tap race: two concurrent purchase clicks could both pass an in-memory affordability
     *     check and then double-spend. The guarded `WHERE balance >= :cost` clause on
     *     [PlayerProfileDao.adjustStepBalanceIfSufficient] means at most one transaction can succeed.
     *
     * Room wraps this default method body in a single transaction; DAO calls inside (including the
     * cross-DAO [PlayerProfileDao] call) share that transaction because Room's transaction tracker
     * is scoped to the underlying [androidx.room.RoomDatabase], not to a specific DAO instance.
     *
     * @param type the [com.whitefang.stepsofbabylon.domain.model.UpgradeType] name (stored as String in the entity)
     * @param newLevel the new level to set on success (caller computes `currentLevel + 1`)
     * @param cost the Step cost to deduct (caller computes via `CalculateUpgradeCost`)
     * @param playerDao the player profile DAO that owns the guarded deduct query
     * @return `true` if the player could afford the cost and both writes committed; `false` otherwise
     */
    @Transaction
    suspend fun purchaseUpgradeAtomic(
        type: String,
        newLevel: Int,
        cost: Long,
        playerDao: PlayerProfileDao,
    ): Boolean {
        val rowsUpdated = playerDao.adjustStepBalanceIfSufficient(cost)
        if (rowsUpdated == 0) return false
        upsert(WorkshopUpgradeEntity(upgradeType = type, level = newLevel))
        return true
    }
}
