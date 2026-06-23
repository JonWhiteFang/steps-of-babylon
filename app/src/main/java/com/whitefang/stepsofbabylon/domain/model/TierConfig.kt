package com.whitefang.stepsofbabylon.domain.model

object TierConfig {
    private val tiers =
        listOf(
            Tier(1, 0, 0, 1.0, emptyMap()),
            Tier(2, 50, 1, 1.8, emptyMap()),
            Tier(3, 50, 2, 2.6, emptyMap()),
            Tier(4, 50, 3, 3.4, emptyMap()),
            Tier(5, 75, 4, 4.2, emptyMap()),
            Tier(
                6,
                75,
                5,
                5.0,
                mapOf(
                    BattleCondition.ENEMY_SPEED to 10,
                ),
            ),
            Tier(
                7,
                100,
                6,
                6.0,
                mapOf(
                    BattleCondition.ORB_RESISTANCE to 20,
                    BattleCondition.ENEMY_SPEED to 15,
                ),
            ),
            Tier(
                8,
                100,
                7,
                7.2,
                mapOf(
                    BattleCondition.KNOCKBACK_RESISTANCE to 30,
                    BattleCondition.ARMORED_ENEMIES to 5,
                ),
            ),
            Tier(
                9,
                100,
                8,
                8.5,
                mapOf(
                    BattleCondition.THORN_RESISTANCE to 30,
                    BattleCondition.MORE_BOSSES to 7,
                ),
            ),
            Tier(
                10,
                150,
                9,
                10.0,
                mapOf(
                    BattleCondition.ORB_RESISTANCE to 20,
                    BattleCondition.KNOCKBACK_RESISTANCE to 30,
                    BattleCondition.ARMORED_ENEMIES to 5,
                    BattleCondition.THORN_RESISTANCE to 30,
                    BattleCondition.MORE_BOSSES to 7,
                    BattleCondition.ENEMY_SPEED to 20,
                    BattleCondition.ENEMY_ATTACK_SPEED to 15,
                ),
            ),
        )

    fun forTier(number: Int): Tier =
        tiers.firstOrNull { it.number == number }
            ?: throw IllegalArgumentException("Unknown tier: $number")
}
