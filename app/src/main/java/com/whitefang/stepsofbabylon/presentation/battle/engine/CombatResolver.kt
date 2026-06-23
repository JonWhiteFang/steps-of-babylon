package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationEvent
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.domain.model.TierConfig
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import com.whitefang.stepsofbabylon.domain.model.ZigguratBaseStats
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDamage
import com.whitefang.stepsofbabylon.domain.usecase.CalculateDefense
import com.whitefang.stepsofbabylon.presentation.audio.SoundEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.DeathEffect
import com.whitefang.stepsofbabylon.presentation.battle.effects.FloatingText
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Combat resolution for the battle simulation (#231 decomposition): projectile/orb hits, ziggurat
 * damage + defense + thorn + second-wind + death-defy, enemy death (reward credit/feedback + SCATTER
 * split), and wave-complete cash. Lifted verbatim from GameEngine; reward *arithmetic* delegates to
 * the pure-domain [SimulationMath] (#230). Reaches engine state via [CombatHost]. Loop-thread only —
 * invoked from inside the engine's held `entitiesLock` (collision callbacks, death handler); holds no
 * monitor of its own.
 */
class CombatResolver(
    private val host: CombatHost,
) {
    private val calculateDamage = CalculateDamage()
    private val calculateDefense = CalculateDefense()

    fun onProjectileHitEnemy(
        proj: ProjectileEntity,
        enemy: EnemyEntity,
    ) {
        val zig = host.ziggurat ?: return
        val stats = host.currentStats
        val dist = hypot(zig.originX - enemy.x, zig.originY - enemy.y)
        val result = calculateDamage(stats, dist)

        // #17: gate knockback + lifesteal on damage actually dealt — takeDamage returns 0.0
        // when the hit is fully absorbed by an armor charge, so an armored enemy no longer
        // grants free healing/CC on a hit that did no HP damage.
        val dealt = enemy.takeDamage(result.amount)
        proj.hitEnemies.add(enemy)
        proj.isAlive = false

        host.soundManager?.play(SoundEffect.HIT)

        if (dealt > 0.0 && stats.knockbackForce > 0f) {
            val dx = enemy.x - zig.originX
            val dy = enemy.y - zig.originY
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val kb = stats.knockbackForce * host.conditions.knockbackMultiplier
            enemy.applyKnockback(dx / d * kb, dy / d * kb)
        }
        if (dealt > 0.0 && stats.lifestealPercent > 0) {
            host.applyLifesteal(dealt * stats.lifestealPercent)
        }

        // Bounce shot
        if (proj.bouncesRemaining > 0) {
            val nextTarget =
                host
                    .aliveEnemies()
                    .asSequence()
                    .filter { it.isAlive && it !in proj.hitEnemies }
                    .minByOrNull { hypot(it.x - enemy.x, it.y - enemy.y) }
            if (nextTarget != null) {
                host.addPending(
                    ProjectileEntity(
                        startX = enemy.x,
                        startY = enemy.y,
                        targetX = nextTarget.x,
                        targetY = nextTarget.y,
                        speed = ZigguratBaseStats.PROJECTILE_SPEED,
                        bouncesRemaining = proj.bouncesRemaining - 1,
                        hitEnemies = proj.hitEnemies,
                    ),
                )
            }
        }
    }

    private fun onOrbHitEnemy(
        enemy: EnemyEntity,
        damage: Double,
    ) {
        // #17: gate knockback + lifesteal on damage actually dealt (0.0 when armor-absorbed).
        val dealt = enemy.takeDamage(damage)
        val zig = host.ziggurat ?: return
        val stats = host.currentStats
        if (dealt > 0.0 && stats.knockbackForce > 0f) {
            val dx = enemy.x - zig.originX
            val dy = enemy.y - zig.originY
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val kb = stats.knockbackForce * 0.5f * host.conditions.knockbackMultiplier
            enemy.applyKnockback(dx / d * kb, dy / d * kb)
        }
        if (dealt > 0.0 && stats.lifestealPercent > 0) {
            host.applyLifesteal(dealt * stats.lifestealPercent)
        }
    }

    fun onOrbHit(
        enemy: EnemyEntity,
        damage: Double,
    ) = onOrbHitEnemy(enemy, damage)

    fun applyDamageToZiggurat(
        rawDamage: Double,
        attacker: EnemyEntity?,
    ) {
        val zig = host.ziggurat ?: return
        val stats = host.currentStats
        val mitigated = calculateDefense(rawDamage, stats)
        if (zig.currentHp - mitigated <= 0.0 && stats.deathDefyChance > 0) {
            if (Random.nextDouble() < stats.deathDefyChance) {
                zig.currentHp = 1.0
                applyThorn(rawDamage, attacker)
                return
            }
        }
        if (zig.currentHp - mitigated <= 0.0 && host.secondWindHpPercent > 0.0 && host.consumeSecondWind()) {
            zig.currentHp = zig.maxHp * host.secondWindHpPercent
            applyThorn(rawDamage, attacker)
            return
        }
        val prevHpRatio = zig.currentHp / zig.maxHp
        zig.currentHp = (zig.currentHp - mitigated).coerceAtLeast(0.0)
        val newHpRatio = zig.currentHp / zig.maxHp
        // Screen shake when HP drops below 25%
        if (prevHpRatio > 0.25 && newHpRatio <= 0.25 && !host.reducedMotion) {
            host.effectEngine?.screenShake?.trigger(5f, 0.2f)
        }
        applyThorn(rawDamage, attacker)
    }

    private fun applyThorn(
        rawDamage: Double,
        attacker: EnemyEntity?,
    ) {
        if (attacker == null || !attacker.isAlive) return
        val reflection =
            SimulationMath.thornReflectionDamage(
                rawDamage = rawDamage,
                thornPercent = host.currentStats.thornPercent,
                conditionMultiplier = host.conditions.thornMultiplier.toDouble(),
            )
        if (reflection > 0) attacker.takeDamage(reflection)
    }

    fun handleWaveComplete(wave: Int) {
        // RO-11 #A.2: CASH_RESEARCH multiplies the wave-end cash payout.
        val waveCash =
            SimulationMath.waveCompleteCash(
                cashPerWaveLevel = host.wsLevel(UpgradeType.CASH_PER_WAVE),
                fortuneMultiplier = host.fortuneMultiplier,
                cashResearchMultiplier = host.cashResearchMultiplier,
            )
        host.simulation.creditCash(waveCash)
        host.simulation.applyInterest(host.wsLevel(UpgradeType.INTEREST))
    }

    fun handleEnemyDeath(enemy: EnemyEntity) {
        host.simulation.recordEnemyKilled()
        val baseCash = EnemyScaler.cashReward(enemy.enemyType)
        val tierMult = TierConfig.forTier(host.tier).cashMultiplier
        // RO-11 #A.2: CASH_RESEARCH multiplies the per-kill cash. Stacks multiplicatively with
        // workshop CASH_BONUS, tier cash multiplier, GOLDEN_ZIGGURAT UW, and the CASH_BONUS_GAIN card.
        // Default 1.0× means "no CASH_RESEARCH research".
        val killCash =
            SimulationMath.killCashReward(
                baseCash = baseCash,
                tierMultiplier = tierMult,
                cashBonusLevel = host.wsLevel(UpgradeType.CASH_BONUS),
                fortuneMultiplier = host.fortuneMultiplier,
                cashBonusPercent = host.cashBonusPercent,
                cashResearchMultiplier = host.cashResearchMultiplier,
            )
        host.simulation.creditCash(killCash)
        // #146: the on-screen enemy count is now derived live by [aliveEnemyCount] from the
        // entity list, so there is no hand-kept WaveSpawner tally to decrement here (the old
        // tally drifted negative — SCATTER children bypassed its only increment, and onDeath
        // re-fires double-decremented). The enemy is marked `!isAlive` by `takeDamage` before
        // this runs and is swept from `entities` at end of frame.

        host.soundManager?.play(SoundEffect.ENEMY_DEATH)

        // Death effect particles + floating cash text
        val fx = host.effectEngine
        if (fx != null) {
            if (!host.reducedMotion) DeathEffect.spawn(fx.pool, enemy.x, enemy.y, enemy.enemyType)
            fx.addEffect(FloatingText(enemy.x, enemy.y, host.strings?.cashReward(killCash) ?: "+$killCash"))
            // Boss death screen shake
            if (enemy.enemyType == EnemyType.BOSS && !host.reducedMotion) {
                fx.screenShake.trigger(8f, 0.3f)
            }
        }

        // Boss-kill Power Stone reward — tier-scaled, capped at 100/day. Emitted as a
        // SimulationEvent; BattleViewModel's collector awards the PS via AwardBossPowerStones.
        if (enemy.enemyType == EnemyType.BOSS) {
            host.simulation.emit(SimulationEvent.BossKilled(host.tier, enemy.x, enemy.y + 24f))
        }

        // Battle Step reward — flat per enemy type, independent of multipliers. Actual wallet
        // credit, cap enforcement, and floating-text spawn all live in the collector
        // (BattleViewModel) so a capped reward can be silently dropped without a misleading
        // "+N Step" indicator.
        val stepReward = EnemyScaler.stepReward(enemy.enemyType)
        if (stepReward > 0L) {
            host.simulation.creditSteps(stepReward)
            host.simulation.emit(SimulationEvent.StepReward(stepReward, enemy.x, enemy.y + 24f))
        }

        if (enemy.enemyType == EnemyType.SCATTER) {
            val zig = host.ziggurat ?: return
            val childCount = (2..3).random()
            repeat(childCount) { i ->
                val child =
                    EnemyEntity(
                        enemyType = EnemyType.BASIC,
                        currentHp = enemy.maxHp * 0.5,
                        maxHp = enemy.maxHp * 0.5,
                        speed = EnemyScaler.scaleSpeed(EnemyType.SCATTER) * host.conditions.enemySpeedMultiplier,
                        damage = enemy.damage * 0.5,
                        targetX = zig.originX,
                        targetY = zig.originY,
                        onDeath = ::handleEnemyDeath,
                        // R3-02: SCATTER child enemies also forward their attacker reference so
                        // THORN_DAMAGE reflects against them (same fix as the wave-spawner path —
                        // these melee-hit lambdas previously dropped the attacker).
                        onMeleeHit = { atk, dmg -> applyDamageToZiggurat(dmg, atk) },
                    ).apply {
                        x = enemy.x + (i - childCount / 2f) * 15f
                        y = enemy.y
                        initDistance()
                    }
                host.addPending(child)
            }
        }
    }
}
