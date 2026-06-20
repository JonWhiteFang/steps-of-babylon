package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.DailyMission
import kotlinx.coroutines.flow.Flow

/**
 * Domain port over the `daily_mission` persistence (#227). #219: now also covers the reactive reads
 * the presentation layer used to make against the raw DAO (`observeMissionsForDate`,
 * `observeClaimableCount`), so no ViewModel injects `DailyMissionDao` directly.
 */
interface MissionRepository {
    /** Today's (or [date]'s) generated missions. Rows whose stored type no longer resolves to a
     *  [com.whitefang.stepsofbabylon.domain.model.DailyMissionType] are dropped by the impl. */
    suspend fun getMissionsForDate(date: String): List<DailyMission>

    /** Reactive stream of [date]'s missions (same unknown-type drop as [getMissionsForDate]). */
    fun observeMissionsForDate(date: String): Flow<List<DailyMission>>

    /** Reactive count of completed-but-unclaimed missions for [date] (Home/Missions badges). */
    fun observeClaimableCount(date: String): Flow<Int>

    /** Generate [date]'s mission set. Idempotent — the `(date, missionType)` unique index +
     *  `onConflict = IGNORE` is the authoritative guard (preserved in the impl). */
    suspend fun generateForDate(date: String, missions: List<DailyMission>)

    /** Guarded one-shot claim; returns rows affected (`1` first claim, `0` if already claimed). */
    suspend fun markClaimed(id: Int): Int

    /** Update a mission's progress + completion flag. */
    suspend fun updateProgress(id: Int, progress: Int, completed: Boolean)
}
