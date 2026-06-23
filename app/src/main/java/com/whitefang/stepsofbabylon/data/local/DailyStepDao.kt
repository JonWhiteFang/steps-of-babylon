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
    fun getRange(
        startDate: String,
        endDate: String,
    ): Flow<List<DailyStepRecordEntity>>

    @Upsert
    suspend fun upsert(entity: DailyStepRecordEntity)

    @Query("SELECT * FROM daily_step_record WHERE date = :date")
    suspend fun getByDateOnce(date: String): DailyStepRecordEntity?

    @Query("UPDATE daily_step_record SET escrowSteps = 0, escrowSyncCount = 0 WHERE date = :date")
    suspend fun clearEscrow(date: String)

    @Query("SELECT COALESCE(SUM(creditedSteps), 0) FROM daily_step_record WHERE date BETWEEN :startDate AND :endDate")
    suspend fun sumCreditedSteps(
        startDate: String,
        endDate: String,
    ): Long

    // ---- #121: column-targeted daily-step upserts (lost-update fix) ----
    //
    // Each of the four daily_step_record writers (sensor / Health Connect / activity-minute /
    // escrow) used to go through a non-atomic read-copy-upsert in StepRepositoryImpl:
    // `getByDateOnce(date) -> upsert(existing.copy(field=...))`. Because @Upsert rewrites
    // EVERY column from the read snapshot, two INDEPENDENT components writing the SAME date
    // row concurrently (the foreground sensor service, the 15-min sync worker, and the
    // Health Connect cross-validator all run on different threads with no shared lock) would
    // clobber each other: the second writer's upsert persisted the OTHER columns at the stale
    // value it read, silently reverting the first writer's column. (#121 / audit finding #3.)
    //
    // These four UPSERTs mirror the [incrementBattleSteps] template — the INSERT half supplies
    // every NOT NULL column explicitly (SQLite evaluates NOT NULL before ON CONFLICT can
    // resolve the UNIQUE conflict, so a partial INSERT would abort with
    // `NOT NULL constraint failed`), and the `ON CONFLICT(date) DO UPDATE SET ...` half touches
    // ONLY this writer's own columns. Because each writer now mutates a disjoint column set,
    // concurrent writers can no longer overwrite each other's data — no Mutex required.
    // These overwrite (SET) their columns rather than increment, matching the original
    // semantics: the daily-step writers persist cumulative running totals.

    @Query(
        "INSERT INTO daily_step_record " +
            "(date, sensorSteps, healthConnectSteps, creditedSteps, escrowSteps, " +
            "escrowSyncCount, activityMinutes, stepEquivalents, battleStepsEarned, bossPsEarnedToday) " +
            "VALUES (:date, :sensorSteps, 0, :creditedSteps, 0, 0, '{}', 0, 0, 0) " +
            "ON CONFLICT(date) DO UPDATE SET sensorSteps = :sensorSteps, creditedSteps = :creditedSteps",
    )
    suspend fun setSensorAndCreditedSteps(
        date: String,
        sensorSteps: Long,
        creditedSteps: Long,
    )

    @Query(
        "INSERT INTO daily_step_record " +
            "(date, sensorSteps, healthConnectSteps, creditedSteps, escrowSteps, " +
            "escrowSyncCount, activityMinutes, stepEquivalents, battleStepsEarned, bossPsEarnedToday) " +
            "VALUES (:date, 0, :healthConnectSteps, 0, 0, 0, '{}', 0, 0, 0) " +
            "ON CONFLICT(date) DO UPDATE SET healthConnectSteps = :healthConnectSteps",
    )
    suspend fun setHealthConnectSteps(
        date: String,
        healthConnectSteps: Long,
    )

    @Query(
        "INSERT INTO daily_step_record " +
            "(date, sensorSteps, healthConnectSteps, creditedSteps, escrowSteps, " +
            "escrowSyncCount, activityMinutes, stepEquivalents, battleStepsEarned, bossPsEarnedToday) " +
            "VALUES (:date, 0, 0, 0, 0, 0, :activityMinutes, :stepEquivalents, 0, 0) " +
            "ON CONFLICT(date) DO UPDATE SET activityMinutes = :activityMinutes, stepEquivalents = :stepEquivalents",
    )
    suspend fun setActivityMinutes(
        date: String,
        activityMinutes: Map<String, Int>,
        stepEquivalents: Long,
    )

    @Query(
        "INSERT INTO daily_step_record " +
            "(date, sensorSteps, healthConnectSteps, creditedSteps, escrowSteps, " +
            "escrowSyncCount, activityMinutes, stepEquivalents, battleStepsEarned, bossPsEarnedToday) " +
            "VALUES (:date, 0, 0, 0, :escrowSteps, :escrowSyncCount, '{}', 0, 0, 0) " +
            "ON CONFLICT(date) DO UPDATE SET escrowSteps = :escrowSteps, escrowSyncCount = :escrowSyncCount",
    )
    suspend fun setEscrow(
        date: String,
        escrowSteps: Long,
        escrowSyncCount: Int,
    )

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
            "escrowSyncCount, activityMinutes, stepEquivalents, battleStepsEarned, bossPsEarnedToday) " +
            "VALUES (:date, 0, 0, 0, 0, 0, '{}', 0, :delta, 0) " +
            "ON CONFLICT(date) DO UPDATE SET battleStepsEarned = battleStepsEarned + :delta",
    )
    suspend fun incrementBattleSteps(
        date: String,
        delta: Long,
    )

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

    /**
     * Returns today's [DailyStepRecordEntity.bossPsEarnedToday], or 0 if no row exists.
     */
    @Query("SELECT COALESCE(bossPsEarnedToday, 0) FROM daily_step_record WHERE date = :date")
    suspend fun getBossPsEarnedToday(date: String): Long

    /**
     * UPSERT that increments [bossPsEarnedToday] by [delta], creating the row if needed.
     * Mirrors [incrementBattleSteps] shape.
     */
    @Query(
        "INSERT INTO daily_step_record " +
            "(date, sensorSteps, healthConnectSteps, creditedSteps, escrowSteps, " +
            "escrowSyncCount, activityMinutes, stepEquivalents, battleStepsEarned, bossPsEarnedToday) " +
            "VALUES (:date, 0, 0, 0, 0, 0, '{}', 0, 0, :delta) " +
            "ON CONFLICT(date) DO UPDATE SET bossPsEarnedToday = bossPsEarnedToday + :delta",
    )
    suspend fun incrementBossPs(
        date: String,
        delta: Long,
    )

    /**
     * Atomically credits boss-kill Power Stones to the player's wallet while respecting
     * the per-day cap. Mirrors [creditBattleStepsAtomic] shape.
     */
    @Transaction
    suspend fun creditBossPowerStonesAtomic(
        date: String,
        requested: Long,
        dailyCap: Long,
        playerDao: PlayerProfileDao,
    ): Long {
        if (requested <= 0L) return 0L
        val alreadyEarned = getBossPsEarnedToday(date)
        val remaining = (dailyCap - alreadyEarned).coerceAtLeast(0L)
        if (remaining <= 0L) return 0L
        val credited = min(requested, remaining)
        incrementBossPs(date, credited)
        playerDao.adjustPowerStones(credited)
        playerDao.incrementPowerStonesEarned(credited)
        return credited
    }
}
