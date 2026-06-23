package com.whitefang.stepsofbabylon.domain.model

enum class UpgradeType(
    val category: UpgradeCategory,
    /**
     * `false` for upgrades that are intentionally absent from the Workshop screen but still
     * surfaced via the in-round upgrade menu (Cash) or a Labs research path (Steps + time).
     * Default `true` (Workshop-visible) for the 21 standard upgrades. Currently MULTISHOT
     * and BOUNCE_SHOT are the only entries flagged `false` (R4-02b, 2026-05-23): they are
     * purchased in-round with Cash and/or researched in Labs via [ResearchType.MULTISHOT_RESEARCH] /
     * [ResearchType.BOUNCE_RESEARCH], and don't appear on the permanent-Steps Workshop screen.
     * `WorkshopViewModel` filters by this flag; in-round visibility is controlled separately
     * via `InRoundUpgradeMenu.hiddenInRound`.
     */
    val isWorkshopVisible: Boolean = true,
) {
    // Attack (8)
    DAMAGE(UpgradeCategory.ATTACK),
    ATTACK_SPEED(UpgradeCategory.ATTACK),
    CRITICAL_CHANCE(UpgradeCategory.ATTACK),
    CRITICAL_FACTOR(UpgradeCategory.ATTACK),
    RANGE(UpgradeCategory.ATTACK),
    MULTISHOT(UpgradeCategory.ATTACK, isWorkshopVisible = false),
    BOUNCE_SHOT(UpgradeCategory.ATTACK, isWorkshopVisible = false),
    DAMAGE_PER_METER(UpgradeCategory.ATTACK),
    RAPID_FIRE(UpgradeCategory.ATTACK),

    // Defense (9)
    HEALTH(UpgradeCategory.DEFENSE),
    HEALTH_REGEN(UpgradeCategory.DEFENSE),
    DEFENSE_PERCENT(UpgradeCategory.DEFENSE),
    DEFENSE_ABSOLUTE(UpgradeCategory.DEFENSE),
    KNOCKBACK(UpgradeCategory.DEFENSE),
    THORN_DAMAGE(UpgradeCategory.DEFENSE),
    ORBS(UpgradeCategory.DEFENSE),
    LIFESTEAL(UpgradeCategory.DEFENSE),
    DEATH_DEFY(UpgradeCategory.DEFENSE),

    // Utility (6)
    CASH_BONUS(UpgradeCategory.UTILITY),
    CASH_PER_WAVE(UpgradeCategory.UTILITY),
    INTEREST(UpgradeCategory.UTILITY),
    FREE_UPGRADES(UpgradeCategory.UTILITY),
    RECOVERY_PACKAGES(UpgradeCategory.UTILITY),
    STEP_MULTIPLIER(UpgradeCategory.UTILITY),
    ;

    companion object {
        private val configs: Map<UpgradeType, UpgradeConfig> =
            mapOf(
                // Attack
                DAMAGE to UpgradeConfig(50, 1.12, null, 2.0, "+2% base damage per level"),
                ATTACK_SPEED to UpgradeConfig(75, 1.15, null, 1.5, "+1.5% attacks per second"),
                CRITICAL_CHANCE to UpgradeConfig(100, 1.18, 160, 0.5, "+0.5% crit chance per level (cap 80%)"),
                CRITICAL_FACTOR to UpgradeConfig(120, 1.18, null, 0.1, "+0.1x crit multiplier per level"),
                RANGE to UpgradeConfig(80, 1.14, 150, 2.0, "+2% attack radius (cap 300%)"),
                MULTISHOT to
                    UpgradeConfig(
                        5_000,
                        1.5,
                        10,
                        1.0,
                        "+1 additional target per level (in-round Cash purchase or Labs research; cap 11 incl. baseline)",
                    ),
                BOUNCE_SHOT to
                    UpgradeConfig(
                        8_000,
                        1.5,
                        10,
                        1.0,
                        "+1 bounce per level (in-round Cash purchase or Labs research; cap 10)",
                    ),
                DAMAGE_PER_METER to UpgradeConfig(200, 1.16, null, 1.0, "+1% bonus damage based on enemy distance"),
                RAPID_FIRE to
                    UpgradeConfig(
                        2_000,
                        1.18,
                        10,
                        1.0,
                        "Periodic attack-speed burst (60s/5s/2.0\u00d7 \u2192 permanent/3.0\u00d7 at max)",
                    ),
                // Defense
                HEALTH to UpgradeConfig(50, 1.12, null, 3.0, "+3% max ziggurat health"),
                HEALTH_REGEN to UpgradeConfig(60, 1.13, null, 2.0, "+2% health regen per second"),
                DEFENSE_PERCENT to UpgradeConfig(100, 1.16, 250, 0.3, "+0.3% damage reduction (cap 75%)"),
                DEFENSE_ABSOLUTE to UpgradeConfig(80, 1.14, null, 1.0, "+flat damage blocked per hit"),
                KNOCKBACK to UpgradeConfig(90, 1.15, null, 2.0, "+2% knockback force"),
                THORN_DAMAGE to UpgradeConfig(150, 1.18, null, 1.0, "+1% damage reflected to attackers"),
                ORBS to UpgradeConfig(800, 1.22, 6, 1.0, "+1 orbiting projectile per level (cap 6)"),
                LIFESTEAL to UpgradeConfig(200, 1.20, 75, 0.2, "+0.2% damage dealt returned as health (cap 15%)"),
                DEATH_DEFY to UpgradeConfig(500, 1.28, 50, 1.0, "+1% chance to survive killing blow at 1 HP (cap 50%)"),
                // Utility
                CASH_BONUS to UpgradeConfig(40, 1.11, null, 3.0, "+3% cash earned during rounds"),
                CASH_PER_WAVE to UpgradeConfig(60, 1.13, null, 1.0, "+flat cash bonus at end of each wave"),
                INTEREST to UpgradeConfig(150, 1.18, 20, 0.5, "+0.5% interest on held cash between waves (cap 10%)"),
                FREE_UPGRADES to
                    UpgradeConfig(200, 1.20, 25, 1.0, "+1% chance for in-round upgrades to cost 0 (cap 25%)"),
                RECOVERY_PACKAGES to UpgradeConfig(300, 1.22, null, 1.0, "Periodic health restore drops during waves"),
                STEP_MULTIPLIER to
                    UpgradeConfig(
                        1_000,
                        1.35,
                        100,
                        1.0,
                        "Bonus steps from walking — asymptotic curve approaches +100%",
                    ),
            )

        fun configFor(type: UpgradeType): UpgradeConfig = configs.getValue(type)
    }

    val config: UpgradeConfig get() = configFor(this)
}
