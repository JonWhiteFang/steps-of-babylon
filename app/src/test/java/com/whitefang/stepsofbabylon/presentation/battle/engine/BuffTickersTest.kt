package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.domain.model.ResolvedStats
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.fakes.FakeStrings
import com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngine
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ZigguratEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Focused pure-JVM unit tests for [BuffTickers] (#231 GameEngine decomposition) against a small fake
 * [BuffHost]. Exercises the in-round buff timers (RECOVERY_PACKAGES periodic heal, RAPID_FIRE burst)
 * directly — no Robolectric, no render() path. The stubbed Android `Paint()` is a no-op
 * (`testOptions.unitTests.isReturnDefaultValues = true`) and the LOGIC paths never read it.
 */
class BuffTickersTest {

    /** Mutable fake of [BuffHost]; flip [wavePhase] to drive SPAWNING vs COOLDOWN behaviour. */
    private class FakeBuffHost(
        override val ziggurat: ZigguratEntity?,
        override var wavePhase: WavePhase? = WavePhase.SPAWNING,
        override val effectEngine: EffectEngine? = EffectEngine(reducedMotion = true),
        override val strings: Strings? = FakeStrings(),
    ) : BuffHost {
        val levels = mutableMapOf<UpgradeType, Int>()
        override fun wsLevel(type: UpgradeType): Int = levels[type] ?: 0
    }

    /** A standalone ziggurat at full HP (no nearest-enemy targeting / fire side effects needed). */
    private fun makeZiggurat(): ZigguratEntity = ZigguratEntity(
        screenWidth = 1080f,
        screenHeight = 1920f,
        initialStats = ResolvedStats(),
        findNearestEnemies = { emptyList<EnemyEntity>() },
        onFireProjectile = { _, _, _, _ -> },
    )

    @Test
    fun `recovery heals during SPAWNING after one full interval when level greater than 0`() {
        val zig = makeZiggurat()
        zig.currentHp = zig.maxHp * 0.10 // leave room to heal
        val hpBefore = zig.currentHp
        val host = FakeBuffHost(zig, wavePhase = WavePhase.SPAWNING).apply {
            levels[UpgradeType.RECOVERY_PACKAGES] = 5
        }
        val tickers = BuffTickers(host)

        // A single tick longer than RECOVERY_INTERVAL_SECONDS crosses the heal threshold.
        tickers.tickRecovery(SimulationMath.RECOVERY_INTERVAL_SECONDS + 1f)

        assertTrue(
            zig.currentHp > hpBefore,
            "RECOVERY_PACKAGES (level 5) must heal the ziggurat during SPAWNING after one full " +
                "interval (before=$hpBefore, after=${zig.currentHp})",
        )
    }

    @Test
    fun `recovery does nothing during COOLDOWN phase`() {
        val zig = makeZiggurat()
        zig.currentHp = zig.maxHp * 0.10
        val hpBefore = zig.currentHp
        val host = FakeBuffHost(zig, wavePhase = WavePhase.COOLDOWN).apply {
            levels[UpgradeType.RECOVERY_PACKAGES] = 5
        }
        val tickers = BuffTickers(host)

        tickers.tickRecovery(SimulationMath.RECOVERY_INTERVAL_SECONDS + 1f)

        assertEquals(
            hpBefore,
            zig.currentHp,
            0.0001,
            "RECOVERY_PACKAGES must not heal during the COOLDOWN phase (HP unchanged)",
        )
    }

    @Test
    fun `rapid fire raises the multiplier above 1x after crossing the interval during SPAWNING`() {
        val zig = makeZiggurat()
        val host = FakeBuffHost(zig, wavePhase = WavePhase.SPAWNING).apply {
            levels[UpgradeType.RAPID_FIRE] = 1
        }
        val tickers = BuffTickers(host)

        // L1 interval is 60 s; a single tick past it arms the burst.
        tickers.tickRapidFire(61f)

        assertTrue(
            zig.rapidFireMultiplier > 1f,
            "RAPID_FIRE (level 1) must set rapidFireMultiplier above 1f after crossing the burst " +
                "interval during SPAWNING (got ${zig.rapidFireMultiplier})",
        )
    }
}
