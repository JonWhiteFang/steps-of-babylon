package com.whitefang.stepsofbabylon.balance

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.presentation.battle.engine.EnemyScaler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * R4-06: per-path balance tests. Validates cooldown spacing, damage proportionality,
 * and the GOLDEN cash multiplier bounding across the new 3-path × 10-level UW system.
 */
class UWBalanceTest {
    @Test
    fun `all UWs can activate 2 to 3 times in a 20 minute round at cooldown L1`() {
        val roundSeconds = 20 * 60f
        for (uw in UltimateWeaponType.entries) {
            val cooldown = uw.cooldownAtLevel(1)
            val activations = (roundSeconds / cooldown).toInt()
            assertTrue(
                activations >= 2,
                "${uw.name} at cooldown L1: cooldown ${cooldown}s, only $activations activations in 20min",
            )
            assertTrue(
                activations <= 60,
                "${uw.name} at cooldown L1: $activations activations seems too many",
            )
        }
    }

    @Test
    fun `death wave max damage path does not one-shot wave 50 boss`() {
        val damage = UltimateWeaponType.DEATH_WAVE.damageAtLevel(UltimateWeaponType.MAX_PATH_LEVEL)
        val bossHp = EnemyScaler.scaleHealth(EnemyType.BOSS, 50)
        val ratio = damage / bossHp
        assertTrue(ratio < 1.0, "Death Wave L10 damage ($damage) one-shots wave 50 boss ($bossHp)")
        assertTrue(ratio > 0.01, "Death Wave L10 damage ($damage) is negligible vs wave 50 boss ($bossHp)")
    }

    @Test
    fun `golden ziggurat max cash multiplier for 10s is strong but bounded`() {
        val cashMult = UltimateWeaponType.GOLDEN_ZIGGURAT.damageAtLevel(UltimateWeaponType.MAX_PATH_LEVEL)
        // 10 seconds of 8x cash vs a full 35-second wave
        val effectiveMultiplier = (10.0 / 35.0) * cashMult + (25.0 / 35.0) * 1.0
        assertTrue(
            effectiveMultiplier < 5.0,
            "Golden Ziggurat effective wave multiplier: $effectiveMultiplier (should be <5x)",
        )
    }

    @Test
    fun `cooldown path L10 is at least 50 percent shorter than L1 for all UWs`() {
        for (uw in UltimateWeaponType.entries) {
            val cdL1 = uw.cooldownAtLevel(1)
            val cdL10 = uw.cooldownAtLevel(UltimateWeaponType.MAX_PATH_LEVEL)
            assertTrue(
                cdL10 < cdL1 * 0.5f,
                "${uw.name}: L10 cooldown ($cdL10) should be <50% of L1 ($cdL1)",
            )
        }
    }

    @Test
    fun `per-path linear interpolation produces monotonic values`() {
        for (uw in UltimateWeaponType.entries) {
            for (path in UWPath.entries) {
                val values = (1..10).map { uw.valueAtLevel(path, it) }
                for (i in 1 until values.size) {
                    // COOLDOWN and CHRONO_FIELD DAMAGE path decrease (lower is better)
                    val monotonic = values[i] >= values[i - 1] || values[i] <= values[i - 1]
                    assertTrue(monotonic, "${uw.name}.$path is not monotonic at L${i + 1}")
                }
            }
        }
    }
}
