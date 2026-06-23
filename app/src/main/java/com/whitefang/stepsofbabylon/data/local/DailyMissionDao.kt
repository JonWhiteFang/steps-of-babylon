package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMissionDao {
    @Query("SELECT * FROM daily_mission WHERE date = :date")
    fun getByDate(date: String): Flow<List<DailyMissionEntity>>

    @Query("SELECT * FROM daily_mission WHERE date = :date")
    suspend fun getByDateOnce(date: String): List<DailyMissionEntity>

    /**
     * #127: `IGNORE` on the `(date, missionType)` unique index, so a duplicate daily mission (e.g.
     * a concurrent re-generation that lost the TOCTOU race on the read-then-insert check) is
     * silently dropped rather than throwing `SQLiteConstraintException` or creating a second
     * claimable row. The DB unique index is the authoritative guard; this just makes the insert
     * tolerate the collision gracefully.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DailyMissionEntity)

    /**
     * #127: generate a day's missions. The authoritative guard against duplicate daily missions is
     * the `(date, missionType)` unique index + [insert]'s `onConflict = IGNORE` — that, not this
     * method's transaction, is what closes the TOCTOU window. Room runs over a WAL connection pool
     * with default `DEFERRED` transactions, so two concurrent `generateForDate` calls on different
     * pooled connections can BOTH pass the emptiness `SELECT` before either writes; the index then
     * silently drops the loser's (byte-identical, date-seeded) duplicate rows. **Keep `insert` at
     * `IGNORE` and do not weaken the unique index** — the read-then-insert check is racy on its own.
     *
     * The `@Transaction` is a tidy batching wrapper: it makes the check + 3 inserts atomic so a
     * *partial* mission set is never observed, and the early-return skips redundant work on the
     * common already-generated path. It is correctness-belt-and-suspenders, not the guarantee.
     */
    @Transaction
    suspend fun generateForDate(
        date: String,
        missions: List<DailyMissionEntity>,
    ) {
        if (getByDateOnce(date).isNotEmpty()) return
        missions.forEach { insert(it) }
    }

    @Upsert
    suspend fun upsert(entity: DailyMissionEntity)

    @Query("UPDATE daily_mission SET progress = :progress, completed = :completed WHERE id = :id")
    suspend fun updateProgress(
        id: Int,
        progress: Int,
        completed: Boolean,
    )

    /**
     * Atomic guarded claim (#122). The `AND claimed = 0` clause + rows-affected return make the
     * claim idempotent — only the first call for [id] transitions it to claimed and returns `1`;
     * a concurrent/duplicate call returns `0`. Callers credit the reward only when this returns
     * `1`, closing the double-credit window on a rapid double-tap of the Claim button.
     *
     * @return rows affected — `1` on the first claim, `0` if already claimed / not found.
     */
    @Query("UPDATE daily_mission SET claimed = 1 WHERE id = :id AND claimed = 0")
    suspend fun markClaimed(id: Int): Int

    @Query("SELECT COUNT(*) FROM daily_mission WHERE date = :date AND completed = 1 AND claimed = 0")
    fun countClaimable(date: String): Flow<Int>
}
