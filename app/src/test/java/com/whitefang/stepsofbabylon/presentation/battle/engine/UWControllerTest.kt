package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Focused pure-JVM unit tests for [UWController] (#231 GameEngine decomposition) against a small fake
 * [UWHost]. Exercises the GOLDEN_ZIGGURAT fortune-multiplier activation/expiry lifecycle and the
 * #119 GOLDEN re-layer passthrough directly — no Robolectric, no render(). With `aliveEnemies()` empty
 * the auto-trigger never fires spuriously. The stubbed Android `Paint()` is a no-op
 * (`testOptions.unitTests.isReturnDefaultValues = true`); UW logic never reads it.
 */
class UWControllerTest {

    private class FakeUWHost(
        override val ziggurat: ZigguratEntity?,
        override var currentStats: ResolvedStats = ResolvedStats(damage = 10.0),
    ) : UWHost {
        override val screenWidth: Float = 1080f
        override val screenHeight: Float = 1920f
        override val reducedMotion: Boolean = true
        override val uwCooldownMultiplier: Float = 1f
        override val effectEngine: EffectEngine? = EffectEngine(reducedMotion = true)
        override val soundManager = null
        override fun applyStats(stats: ResolvedStats) { currentStats = stats }
        override fun aliveEnemies(): List<EnemyEntity> = emptyList()
    }

    private fun makeZiggurat(): ZigguratEntity = ZigguratEntity(
        screenWidth = 1080f,
        screenHeight = 1920f,
        initialStats = ResolvedStats(damage = 10.0),
        findNearestEnemies = { emptyList<EnemyEntity>() },
        onFireProjectile = { _, _, _, _ -> },
    )

    @Test
    fun `GOLDEN_ZIGGURAT activation raises fortuneMultiplier above 1x and expiry resets it`() {
        val host = FakeUWHost(makeZiggurat())
        val controller = UWController(host, Simulation())
        controller.initUWs(
            listOf(
                OwnedWeapon(
                    type = UltimateWeaponType.GOLDEN_ZIGGURAT,
                    damageLevel = 1,
                    secondaryLevel = 1,
                    cooldownLevel = 1,
                    isUnlocked = true,
                    isEquipped = true,
                ),
            ),
        )

        controller.activateUW(0)

        // GOLDEN damageAtLevel(1) = 2.0 (cash multiplier from the DAMAGE path).
        assertTrue(
            controller.fortuneMultiplier > 1.0,
            "GOLDEN_ZIGGURAT activation must raise fortuneMultiplier above 1.0× " +
                "(got ${controller.fortuneMultiplier})",
        )

        // A single long tick advances the UW timers well past the 10 s effect duration so the GOLDEN
        // expiry branch resets fortuneMultiplier. With aliveEnemies() empty the auto-trigger cannot
        // re-arm it within the same tick.
        controller.update(1000f)

        assertEquals(
            1.0,
            controller.fortuneMultiplier,
            0.0001,
            "GOLDEN_ZIGGURAT expiry must reset fortuneMultiplier to 1.0× (sole writer)",
        )
    }

    @Test
    fun `relayerBaseStats returns the base unchanged when GOLDEN is inactive`() {
        val host = FakeUWHost(makeZiggurat())
        val controller = UWController(host, Simulation())

        // No GOLDEN activated → relayerBaseStats is a passthrough.
        val result = controller.relayerBaseStats(ResolvedStats(damage = 42.0))

        assertEquals(
            42.0,
            result.damage,
            0.0001,
            "relayerBaseStats must return the base stats unchanged while GOLDEN is inactive",
        )
    }
}
