package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import kotlin.math.pow

object EnemyScaler {
    const val BASE_HEALTH = 50.0
    const val BASE_DAMAGE = 5.0
    const val BASE_SPEED = 80f
    const val SCALING_FACTOR = 1.05

    fun scaleHealth(type: EnemyType, wave: Int): Double =
        BASE_HEALTH * type.healthMultiplier * SCALING_FACTOR.pow(wave)

    fun scaleDamage(type: EnemyType, wave: Int): Double =
        BASE_DAMAGE * type.damageMultiplier * SCALING_FACTOR.pow(wave)

    fun scaleSpeed(type: EnemyType): Float =
        BASE_SPEED * type.speedMultiplier.toFloat()

    fun cashReward(type: EnemyType): Long = when (type) {
        EnemyType.BASIC -> 5
        EnemyType.FAST -> 3
        EnemyType.TANK -> 15
        EnemyType.RANGED -> 8
        EnemyType.BOSS -> 100
        EnemyType.SCATTER -> 6
    }

    /**
     * Flat per-enemy-type Step reward awarded on kill. Independent of wave,
     * Fortune overdrive, Cash Bonus upgrades, and Golden Ziggurat UW.
     * Subject to a 2,000 battle-Steps/day cap (see AwardBattleSteps).
     */
    fun stepReward(type: EnemyType): Long = when (type) {
        EnemyType.BASIC -> 1
        EnemyType.FAST -> 1
        EnemyType.TANK -> 3
        EnemyType.RANGED -> 2
        EnemyType.BOSS -> 10
        EnemyType.SCATTER -> 1
    }
}
