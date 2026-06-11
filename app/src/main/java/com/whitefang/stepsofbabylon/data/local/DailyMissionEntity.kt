package com.whitefang.stepsofbabylon.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// #127: unique index on (date, missionType) — the authoritative guard against duplicate daily
// missions. GenerateDailyMissions did a check-then-insert with no DB-level uniqueness, so two
// concurrent generations (Home + Missions VM inits) could both pass the emptiness check and each
// insert a full batch → 6 independently-claimable rows for one day (inflated Gem/PS payouts). The
// generator's RNG is date-seeded, so a raced second batch is byte-identical per category; this
// index collapses those duplicates while still permitting the 3 legitimate distinct missions/day.
@Entity(
    tableName = "daily_mission",
    indices = [Index(value = ["date", "missionType"], unique = true)],
)
data class DailyMissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val missionType: String,
    val target: Int,
    val progress: Int = 0,
    val rewardGems: Int = 0,
    val rewardPowerStones: Int = 0,
    val completed: Boolean = false,
    val claimed: Boolean = false,
)
