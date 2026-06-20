package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.DailyMission

/**
 * Domain port over the `daily_mission` persistence (#227). Covers only the surface the mission
 * use cases need; presentation-layer direct reads (`getByDate` Flow, `countClaimable`) stay on the
 * raw DAO (presentation→data, tracked separately as #219).
 */
interface MissionRepository {
    /** Today's (or [date]'s) generated missions. Rows whose stored type no longer resolves to a
     *  [com.whitefang.stepsofbabylon.domain.model.DailyMissionType] are dropped by the impl. */
    suspend fun getMissionsForDate(date: String): List<DailyMission>

    /** Generate [date]'s mission set. Idempotent — the `(date, missionType)` unique index +
     *  `onConflict = IGNORE` is the authoritative guard (preserved in the impl). */
    suspend fun generateForDate(date: String, missions: List<DailyMission>)

    /** Guarded one-shot claim; returns rows affected (`1` first claim, `0` if already claimed). */
    suspend fun markClaimed(id: Int): Int

    /** Update a mission's progress + completion flag. */
    suspend fun updateProgress(id: Int, progress: Int, completed: Boolean)
}
