package com.whitefang.stepsofbabylon.presentation.battle

import kotlin.math.floor

/**
 * #214: the pure decision layer for the battle TalkBack live region. Diffs the previous battle
 * snapshot against the next and returns what changed (or `null` if nothing announceable did) — so the
 * Compose live region only re-announces on a real transition, and combat is no longer silent to a blind
 * player. Pure Kotlin (no Android/Compose import) so it is JVM-unit-testable; the composable maps the
 * [BattleAnnouncement] to a localized `stringResource` and pushes it into the live-region node.
 *
 * Health is announced only on coarse quarter brackets (not every 200ms HP poll) so TalkBack isn't
 * spammed. `wavePhase` is the RAW engine enum name (`"SPAWNING"`/`"COOLDOWN"`/`""`); the composable
 * owns the enum→string mapping.
 */
data class BattleSnapshot(
    val currentWave: Int,
    val wavePhase: String,
    val currentHp: Double,
    val maxHp: Double,
    val roundEnded: Boolean,
    val battleError: Boolean,
)

sealed interface BattleAnnouncement {
    data class Wave(
        val wave: Int,
    ) : BattleAnnouncement

    /** Raw engine phase name (e.g. "SPAWNING"); the composable localizes it. */
    data class Phase(
        val rawPhase: String,
    ) : BattleAnnouncement

    /** Quarter bracket 0..4 (4 = full); the composable renders the percentage. */
    data class Health(
        val bucket: Int,
    ) : BattleAnnouncement

    data class RoundOver(
        val wave: Int,
    ) : BattleAnnouncement

    data object Error : BattleAnnouncement
}

/**
 * Health quarter bracket [0,4]; returns -1 when [maxHp] is non-positive (pre-round) so the caller can
 * treat it as "no health info yet" (guards divide-by-zero / NaN).
 */
fun healthBucket(
    currentHp: Double,
    maxHp: Double,
): Int {
    if (maxHp <= 0.0) return -1
    val frac = (currentHp / maxHp).coerceIn(0.0, 1.0)
    return floor(frac * 4).toInt().coerceIn(0, 4)
}

/**
 * Returns the single most-important announcement for the [prev]→[next] transition, or `null` if nothing
 * announceable changed. Priority: battleError > round-over > wave > phase > health. Returns `null`
 * during the pre-round window ([next].wavePhase blank OR [next].maxHp <= 0) so no spurious "Wave 1 /
 * Spawning" fires before the round is live.
 */
fun battleAnnouncement(
    prev: BattleSnapshot?,
    next: BattleSnapshot,
): BattleAnnouncement? {
    // Pre-round: nothing to announce until the spawner exists and HP is known.
    if (next.wavePhase.isBlank() || next.maxHp <= 0.0) return null

    if (next.battleError && prev?.battleError != true) return BattleAnnouncement.Error
    if (next.roundEnded && prev?.roundEnded != true) return BattleAnnouncement.RoundOver(next.currentWave)
    if (prev == null || next.currentWave != prev.currentWave) return BattleAnnouncement.Wave(next.currentWave)
    if (next.wavePhase != prev.wavePhase) return BattleAnnouncement.Phase(next.wavePhase)

    val nextBucket = healthBucket(next.currentHp, next.maxHp)
    val prevBucket = if (prev.maxHp > 0.0) healthBucket(prev.currentHp, prev.maxHp) else -1
    if (nextBucket >= 0 && nextBucket != prevBucket) return BattleAnnouncement.Health(nextBucket)

    return null
}
