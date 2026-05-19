package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Regression guards for the [WaveSpawner.startWave] constructor parameter (RO-11 #B.1,
 * WAVE_SKIP lab research). Pre-RO-11 `currentWave` was hardcoded to `1`; spawner now
 * opens on the constructor's `startWave` value so [BattleViewModel] can map a WAVE_SKIP
 * lab-research level to a higher initial wave.
 */
class WaveSpawnerTest {

    private fun newSpawner(startWave: Int = 1): WaveSpawner = WaveSpawner(
        onSpawnEnemy = { _: EnemyEntity -> },
        zigguratX = 0f,
        zigguratY = 0f,
        onEnemyDeath = { _: EnemyEntity -> },
        onMeleeHit = { _: EnemyEntity, _: Double -> },
        onEnemyFireProjectile = { _: Float, _: Float, _: Float, _: Float, _: Double -> },
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
}
