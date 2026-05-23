package com.whitefang.stepsofbabylon.domain.model

data class RoundState(
    val currentWave: Int,
    val cash: Long,
    val tempUpgrades: Map<UpgradeType, Int>,
    val towerCurrentHp: Double,
    val towerMaxHp: Double,
    val tier: Int,
) {
    companion object {
        fun initial(tier: Int, towerMaxHp: Double) = RoundState(
            currentWave = 1,
            cash = 0,
            tempUpgrades = emptyMap(),
            towerCurrentHp = towerMaxHp,
            towerMaxHp = towerMaxHp,
            tier = tier,
        )
    }
}
