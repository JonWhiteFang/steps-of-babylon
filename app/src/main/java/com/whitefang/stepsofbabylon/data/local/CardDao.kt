package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM card_inventory")
    fun getAll(): Flow<List<CardInventoryEntity>>

    @Query("SELECT * FROM card_inventory WHERE isEquipped = 1")
    fun getEquipped(): Flow<List<CardInventoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CardInventoryEntity): Long

    @Update
    suspend fun update(entity: CardInventoryEntity)

    @Delete
    suspend fun delete(entity: CardInventoryEntity)

    @Query("SELECT COUNT(*) FROM card_inventory WHERE isEquipped = 1")
    fun countEquipped(): Flow<Int>

    @Query("SELECT * FROM card_inventory WHERE id = :id")
    suspend fun getById(id: Int): CardInventoryEntity?

    @Query("SELECT * FROM card_inventory WHERE cardType = :cardType")
    suspend fun getByType(cardType: String): CardInventoryEntity?

    @Query("UPDATE card_inventory SET copyCount = copyCount + :delta WHERE cardType = :cardType")
    suspend fun incrementCopyCount(
        cardType: String,
        delta: Int = 1,
    )

    @Query(
        "UPDATE card_inventory SET copyCount = copyCount - :amount, level = level + 1 WHERE id = :id AND copyCount >= :amount",
    )
    suspend fun decrementCopiesAndLevelUp(
        id: Int,
        amount: Int,
    ): Int

    /**
     * #236: opens a card pack — the guarded Gem deduct and all card-row writes commit or roll back
     * as one SQLite transaction, closing the partial-failure gap where a crash between
     * [PlayerProfileDao.spendGemsAtomic] and the card inserts permanently debited (real-money)
     * Gems with no cards delivered and no reconciliation record (unlike billing). Mirrors the
     * cross-DAO `@Transaction` pattern of [MilestoneDao.claimMilestoneAtomic]: Room scopes its
     * transaction to the underlying [androidx.room.RoomDatabase], not the DAO instance, so the
     * [playerDao] calls join this transaction.
     *
     * Rarity rolling stays in [com.whitefang.stepsofbabylon.domain.usecase.OpenCardPack] (it is
     * pure + seeded and must remain unit-testable); only the DB write set is made atomic here. The
     * caller passes the already-rolled [cardTypeNames]; this method decides per-name whether it is a
     * new card (insert) or a duplicate (copy-count increment), preserving the existing isNew
     * semantics, and re-reads [getByType] INSIDE the transaction.
     *
     * @param gemCost Gems to deduct; `0` (a free pack) skips the deduct entirely.
     * @param cardTypeNames the 3 pre-rolled [com.whitefang.stepsofbabylon.domain.model.CardType] names.
     * @param playerDao the wallet DAO that owns the guarded Gem deduct.
     * @return per-card `isNew` flags in [cardTypeNames] order, or `null` when the guarded deduct
     *         found insufficient Gems (no card rows written — the atomicity guarantee).
     */
    @Transaction
    suspend fun openCardPackAtomic(
        gemCost: Long,
        cardTypeNames: List<String>,
        playerDao: PlayerProfileDao,
    ): List<Boolean>? {
        if (gemCost > 0L && playerDao.spendGemsAtomic(gemCost) == 0) return null
        return cardTypeNames.map { name ->
            if (getByType(name) != null) {
                incrementCopyCount(name)
                false
            } else {
                insert(CardInventoryEntity(cardType = name))
                true
            }
        }
    }
}
