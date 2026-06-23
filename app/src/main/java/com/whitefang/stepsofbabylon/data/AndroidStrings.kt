package com.whitefang.stepsofbabylon.data

import android.content.Context
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.model.EnemyType

/**
 * Production [Strings] impl backed by Android string resources (V1X-13, ADR-0014).
 * Constructed directly from a [Context] by `GameSurfaceView` (mirrors how it builds
 * `SoundManager`), so no Hilt binding is required.
 */
class AndroidStrings(
    private val context: Context,
) : Strings {
    override fun healHp(hp: Int): String = context.getString(R.string.fx_heal_hp, hp)

    override fun rapidFireBurst(): String = context.getString(R.string.fx_rapid_fire)

    override fun cashReward(cash: Long): String = context.getString(R.string.fx_cash_reward, cash)

    override fun stepReward(steps: Long): String =
        context.resources.getQuantityString(
            R.plurals.fx_step_reward,
            steps.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            steps,
        )

    override fun powerStoneReward(ps: Long): String = context.getString(R.string.fx_power_stone_reward, ps)

    override fun enemyTypeName(type: EnemyType): String =
        context.getString(
            when (type) {
                EnemyType.BASIC -> R.string.enemy_basic
                EnemyType.FAST -> R.string.enemy_fast
                EnemyType.TANK -> R.string.enemy_tank
                EnemyType.RANGED -> R.string.enemy_ranged
                EnemyType.BOSS -> R.string.enemy_boss
                EnemyType.SCATTER -> R.string.enemy_scatter
            },
        )

    override fun waveComposition(counts: Map<EnemyType, Int>): String {
        // Preserve insertion order (WaveSpawner inserts BOSS first on boss waves). Do NOT sort.
        val sep = context.getString(R.string.wave_composition_separator)
        val list =
            counts.entries.joinToString(sep) { (type, n) ->
                context.getString(R.string.wave_comp_entry, n, enemyTypeName(type))
            }
        return context.getString(R.string.wave_composition, list)
    }

    override fun bossCountdown(waves: Int): String =
        context.resources.getQuantityString(R.plurals.boss_in_waves, waves, waves)
}
