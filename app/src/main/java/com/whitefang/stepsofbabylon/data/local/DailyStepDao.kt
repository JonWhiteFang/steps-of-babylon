package com.whitefang.stepsofbabylon.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.math.min

@Dao
interface DailyStepDao {

    @Query("SELECT * FROM daily_step_record WHERE date = :date")
    fun getByDate(date: String): Flow<DailyStepRecordEntity?>

    @Query("SELECT * FROM daily_step_record WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getRange(startDate: String, endDate: String): Flow<List<DailyStepRecordEntity>>

    @Upsert
    suspend fun upsert(entity: DailyStepRecordEntity)

    @Query("SELECT * FROM daily_step_record WHERE date = :date")
    suspend fun getByDateOnce(date: String): DailyStepRecordEntity?

    @Query("UPDATE daily_step_record SET escrowSteps = 0, escrowSyncCount = 0 WHERE date = :date")
    suspend fun clearEscrow(date: String)

    @Query("SELECT COALESCE(SUM(creditedSteps), 0) FROM daily_step_record WHERE date BETWEEN :startDate AND :endDate")
    suspend fun sumCreditedSteps(startDate: String, endDate: String): Long

    /**
     * Returns today's [DailyStepRecordEntity.battleStepsEarned], or 0 if no
     * row exists for [date] yet.
     */
    @Query("SELECT COALESCE(battleStepsEarned, 0) FROM daily_step_record WHERE date = :date")
    suspend fun getBattleStepsEarned(date: String): Long

    /**
     * Adds [delta] to the [DailyStepRecordEntity.battleStepsEarned] counter for [date],
     * creating the row (with all other NOT NULL fields defaulted to zero / `'{}'`) if it
     * does not yet exist. The INSERT half supplies every column explicitly so SQLite's
     * NOT NULL checks pass regardless of whether the row already exists; if the row does
     * exist, the `ON CONFLICT(date)` clause switches to an UPDATE that touches only
     * `battleStepsEarned`, preserving any existing sensor / HC / escrow data.
     *
     * Safe to call before any walking step has been recorded on a given day — this is the
     * hot path when the first battle of a fresh install lands before the step sensor has
     * ticked in today's row. (An earlier implementation omitted the other columns from the
     * INSERT list and crashed with `SQLiteConstraintException: NOT NULL constraint failed:
     * daily_step_record.sensorSteps` in that scenario, because SQLite evaluates NOT NULL
     * before the ON CONFLICT clause; `ON CONFLICT(date)` only catches UNIQUE violations,
     * not NOT NULL violations. See `DailyStepDaoTest.\`incrementBattleSteps succeeds on
     * empty table\`` for the regression guard.)
     *
     * The `activityMinutes` column stores a JSON-encoded `Map<String, Int>` via
     * [Converters.fromStringIntMap]; `'{}'` is the round-trip representation of an empty
     * map and matches the [DailyStepRecordEntity.activityMinutes] Kotlin default.
     */
    @Query(
        "INSERT INTO daily_step_record " +
            "(date, sensorSteps, healthConnectSteps, creditedSteps, escrowSteps, " +
            "escrowSyncCount, activityMinutes, stepEquivalents, battleStepsEarned) " +
            "VALUES (:date, 0, 0, 0, 0, 0, '{}', 0, :delta) " +
            "ON CONFLICT(date) DO UPDATE SET battleStepsEarned = battleStepsEarned + :delta",
    )
    suspend fun incrementBattleSteps(date: String, delta: Long)

    /**
     * Atomically credits a per-enemy battle-step reward to the player's wallet while respecting
     * the per-day battle-step cap. The entire read-compute-write is wrapped in a single SQLite
     * transaction so either both the `battleStepsEarned` counter and the wallet balance advance
     * together, or neither does.
     *
     * This closes two previously-open correctness windows in `AwardBattleSteps`:
     *  1. **Partial-failure gap** \u2014 a crash between `incrementBattleSteps` and
     *     `playerRepository.addSteps` could leave the wallet credited without the cap counter
     *     moving (allowing double-credit on retry) or vice versa.
     *  2. **Concurrent-kill race** \u2014 two kills arriving while only `1` battle-step of headroom
     *     remains could both read `alreadyEarned = cap - 1`, both compute `credited = 1`, and
     *     both call `addSteps(1)` \u2014 crediting `2` steps against a `1`-step cap window. Serialising
     *     the read-modify-write inside a Room transaction closes this.
     *
     * Room wraps this default method body in a single transaction; the cross-DAO call to
     * [playerDao] is safe because Room's transaction tracker is scoped to the underlying
     * [androidx.room.RoomDatabase], not to a specific DAO instance \u2014 both DAO calls share the
     * same SQLite transaction.
     *
     * @param date ISO `yyyy-MM-dd` date bucket the cap is tracked against.
     * @param requested Requested reward amount (flat per-enemy reward from caller). Non-positive
     *                  values short-circuit to `0L`.
     * @param dailyCap Hard per-day cap (typically `AwardBattleSteps.DAILY_BATTLE_STEP_CAP`).
     * @param playerDao The player-profile DAO that owns the wallet-balance write.
     * @return Amount actually credited \u2014 `0` when the cap is already exhausted; a partial amount
     *         when only part of the request fits under the cap; [requested] otherwise.
     */
    @Transaction
    suspend fun creditBattleStepsAtomic(
        date: String,
        requested: Long,
        dailyCap: Long,
        playerDao: PlayerProfileDao,
    ): Long {
        if (requested <= 0L) return 0L
        val alreadyEarned = getBattleStepsEarned(date)
        val remaining = (dailyCap - alreadyEarned).coerceAtLeast(0L)
        if (remaining <= 0L) return 0L
        val credited = min(requested, remaining)
        incrementBattleSteps(date, credited)
        playerDao.adjustStepBalance(credited)
        return credited
    }
}
