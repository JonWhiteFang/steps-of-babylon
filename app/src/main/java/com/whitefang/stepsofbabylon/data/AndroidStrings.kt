package com.whitefang.stepsofbabylon.data

import android.content.Context
import com.whitefang.stepsofbabylon.R
import com.whitefang.stepsofbabylon.domain.Strings

/**
 * Production [Strings] impl backed by Android string resources (V1X-13, ADR-0014).
 * Constructed directly from a [Context] by `GameSurfaceView` (mirrors how it builds
 * `SoundManager`), so no Hilt binding is required.
 */
class AndroidStrings(private val context: Context) : Strings {
    override fun healHp(hp: Int): String = context.getString(R.string.fx_heal_hp, hp)
    override fun rapidFireBurst(): String = context.getString(R.string.fx_rapid_fire)
    override fun cashReward(cash: Long): String = context.getString(R.string.fx_cash_reward, cash)
    override fun stepReward(steps: Long): String = context.getString(R.string.fx_step_reward, steps)
    override fun powerStoneReward(ps: Long): String = context.getString(R.string.fx_power_stone_reward, ps)
}
