package com.whitefang.stepsofbabylon.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_step_record")
data class DailyStepRecordEntity(
    @PrimaryKey val date: String, // ISO yyyy-MM-dd
    val sensorSteps: Long = 0,
    val healthConnectSteps: Long = 0,
    val creditedSteps: Long = 0,
    val escrowSteps: Long = 0,
    val escrowSyncCount: Int = 0,
    val activityMinutes: Map<String, Int> = emptyMap(),
    val stepEquivalents: Long = 0,
    /**
     * Steps earned from battle enemy kills this day. Tracked separately from
     * [creditedSteps] (which is walking-only) and enforced against the
     * per-day battle-Step cap in AwardBattleSteps. Added in DB v8.
     */
    val battleStepsEarned: Long = 0,
    /**
     * Power Stones earned from boss kills this day. Enforced against the
     * per-day boss-PS cap (100) in AwardBossPowerStones. Added in DB v10 (R4-07).
     */
    val bossPsEarnedToday: Long = 0,
)
