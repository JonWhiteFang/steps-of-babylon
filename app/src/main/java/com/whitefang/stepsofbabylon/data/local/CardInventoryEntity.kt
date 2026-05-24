package com.whitefang.stepsofbabylon.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "card_inventory",
    indices = [Index(value = ["cardType"], unique = true)],
)
data class CardInventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cardType: String,
    val level: Int = 1,
    val isEquipped: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val copyCount: Int = 1,
)
