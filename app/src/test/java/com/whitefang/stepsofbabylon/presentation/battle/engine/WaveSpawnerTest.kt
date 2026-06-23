package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression guards for the [WaveSpawner.startWave] constructor parameter (RO-11 #B.1,
 * WAVE_SKIP lab research). Pre-RO-11 `currentWave` was hardcoded to `1`; spawner now
 * opens on the constructor's `startWave` value so [BattleViewModel] can map a WAVE_SKIP
 * lab-research level to a higher initial wave.
 */
class WaveSpawnerTest {
    private fun newSpawner(startWave: Int = 1): WaveSpawner =
        WaveSpawner(
            onSpawnEnemy = { _: EnemyEntity -> },
            zigguratX = 0f,
            zigguratY = 0f,
            onEnemyDeath = { _: EnemyEntity -> },
            onMeleeHit = { _: EnemyEntity, _: Double -> },
            onEnemyFireProjectile = { _: EnemyEntity, _: Float, _: Float, _: Float, _: Float, _: Double -> },
            startWave = startWave,
        )

    @Test
    fun `currentWave reads from startWave constructor argument`() {
        // Max WAVE_SKIP lab-research level is 10 → BattleViewModel passes 1 + 10 = 11.
        // The default `startWave = 1` path is implicitly covered by every existing
        // GameEngineTest (all of which call the default `engine.init(...)` and end up
        // on wave 1 — a regression there would surface in unrelated tests).
        val spawner = newSpawner(startWave = 11)
        assertEquals(
            11,
            spawner.currentWave,
            "WAVE_SKIP L10 must produce wave 11 at round start",
        )
    }

    // ---- V1X-15b: ENEMY_INTEL overlay helpers (getWaveComposition / wavesUntilNextBoss) ----

    @Test
    fun `getWaveComposition early non-boss wave is all BASIC and deterministic`() {
        // Wave 3: enemiesPerWave = min(5 + 2*2, 40) = 9; band <=5 is 100% BASIC; not a boss wave.
        val spawner = newSpawner()
        val comp = spawner.getWaveComposition(3)
        assertEquals(mapOf(EnemyType.BASIC to 9), comp, "wave 3 must be 9 BASIC, no boss")
        assertEquals(comp, spawner.getWaveComposition(3), "composition must be deterministic across calls")
    }

    @Test
    fun `getWaveComposition boss wave includes exactly one BOSS`() {
        // Wave 10: bossWaveInterval default 10 → boss wave. One BOSS split off the 23 slots.
        val comp = newSpawner().getWaveComposition(10)
        assertEquals(1, comp[EnemyType.BOSS], "a boss wave must contain exactly one BOSS")
        assertTrue(comp.containsKey(EnemyType.BASIC), "wave 10 band still spawns BASIC enemies")
    }

    @Test
    fun `wavesUntilNextBoss counts forward from currentWave`() {
        // Default interval 10. startWave 8 → 2 waves to wave 10. startWave 1 → 9. A boss wave
        // itself returns the full interval (the *next* boss, not the current one).
        assertEquals(2, newSpawner(startWave = 8).wavesUntilNextBoss())
        assertEquals(9, newSpawner(startWave = 1).wavesUntilNextBoss())
        assertEquals(10, newSpawner(startWave = 10).wavesUntilNextBoss())
    }
}
