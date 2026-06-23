package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CosmeticDao {
    @Query("SELECT * FROM cosmetics")
    fun observeAll(): Flow<List<CosmeticEntity>>

    @Query("SELECT * FROM cosmetics WHERE isOwned = 1")
    fun observeOwned(): Flow<List<CosmeticEntity>>

    @Query("SELECT * FROM cosmetics WHERE isEquipped = 1")
    fun observeEquipped(): Flow<List<CosmeticEntity>>

    @Query("SELECT COUNT(*) FROM cosmetics")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: CosmeticEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CosmeticEntity>)

    @Query("UPDATE cosmetics SET isOwned = 1 WHERE cosmeticId = :cosmeticId")
    suspend fun markOwned(cosmeticId: String)

    @Query("UPDATE cosmetics SET isEquipped = 1 WHERE cosmeticId = :cosmeticId")
    suspend fun equip(cosmeticId: String)

    @Query("UPDATE cosmetics SET isEquipped = 0 WHERE cosmeticId = :cosmeticId")
    suspend fun unequip(cosmeticId: String)

    @Query("UPDATE cosmetics SET isEquipped = 0 WHERE category = :category")
    suspend fun unequipCategory(category: String)
}
