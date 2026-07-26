package com.whitefang.stepsofbabylon.domain.battle.engine

import kotlin.random.Random

/**
 * Pure descriptors for the SCATTER-on-death child split (#306 Slice 2). Lifted verbatim from
 * `CombatResolver.handleEnemyDeath`: the count is rolled 2..3 (`random.nextInt(2, 4)` == the pre-hoist
 * `(2..3).random()`), each child gets half the parent's **maxHp** (as both its current and max HP) and half
 * its damage, and a fanned-out X offset `(i - count / 2f) * OFFSET_SPACING`.
 *
 * **`count / 2f` is FLOAT division and must stay that way** — `count` is an `Int` promoted against the `2f`
 * literal, so 3 children give `1.5f`, not `1`. That asymmetry is the pre-hoist behaviour; "tidying" it to
 * integer division would silently shift every SCATTER child's spawn X and break behaviour-preservation.
 * Pinned by `ScatterSplitTest`.
 *
 * The presentation `CombatResolver` maps these onto `EnemyEntity` children — child speed, ziggurat target,
 * and the onDeath/onMeleeHit lambdas stay presentation. No Android imports; holds no monitor.
 */
object ScatterSplit {
    private const val CHILD_HP_FRACTION = 0.5
    private const val CHILD_DAMAGE_FRACTION = 0.5
    private const val OFFSET_SPACING = 15f
    private const val COUNT_MIN = 2
    private const val COUNT_MAX_EXCLUSIVE = 4 // 2..3 inclusive

    /**
     * @property offsetX added to the parent's X for this child's spawn position.
     */
    data class Child(
        val hp: Double,
        val maxHp: Double,
        val damage: Double,
        val offsetX: Float,
    )

    fun children(
        parentMaxHp: Double,
        parentDamage: Double,
        random: Random,
    ): List<Child> {
        val count = random.nextInt(COUNT_MIN, COUNT_MAX_EXCLUSIVE)
        return (0 until count).map { i ->
            Child(
                hp = parentMaxHp * CHILD_HP_FRACTION,
                maxHp = parentMaxHp * CHILD_HP_FRACTION,
                damage = parentDamage * CHILD_DAMAGE_FRACTION,
                offsetX = (i - count / 2f) * OFFSET_SPACING,
            )
        }
    }
}
