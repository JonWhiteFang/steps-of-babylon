package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    suspend fun incrementCopyCount(cardType: String, delta: Int = 1)

    @Query("UPDATE card_inventory SET copyCount = copyCount - :amount, level = level + 1 WHERE id = :id AND copyCount >= :amount")
    suspend fun decrementCopiesAndLevelUp(id: Int, amount: Int): Int
}
