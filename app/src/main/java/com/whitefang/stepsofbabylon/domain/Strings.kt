package com.whitefang.stepsofbabylon.domain

import com.whitefang.stepsofbabylon.domain.model.EnemyType

/**
 * Seam for engine-internal display strings (battle floating-text feedback) so the
 * battle engine / ViewModel can emit localized text without reading Android resources
 * directly — which keeps `GameEngineTest` pure-JVM (no Robolectric). Pure-Kotlin
 * interface (no Android imports); the production impl is `data/AndroidStrings`.
 * Introduced by V1X-13 (i18n phase 1, ADR-0014).
 */
interface Strings {
    /** Healing floating-text, e.g. "+12 HP" (RECOVERY_PACKAGES + LIFESTEAL). */
    fun healHp(hp: Int): String

    /** RAPID_FIRE burst announcement, e.g. "RAPID FIRE!". */
    fun rapidFireBurst(): String

    /** Per-kill cash floating-text, e.g. "+45". */
    fun cashReward(cash: Long): String

    /** Battle-step reward floating-text, e.g. "+3 Step". */
    fun stepReward(steps: Long): String

    /** Boss Power Stone reward floating-text, e.g. "+5 PS". */
    fun powerStoneReward(ps: Long): String

    /** Localized enemy-type display name, e.g. "Basic"/"Boss" (replaces raw EnemyType.name). */
    fun enemyTypeName(type: EnemyType): String

    /** Whole next-wave composition line, e.g. "Next: 1 Boss, 12 Basic" (no concatenation). */
    fun waveComposition(counts: Map<EnemyType, Int>): String

    /** Plural-correct boss countdown, e.g. "Boss next wave" / "Boss in 2 waves". */
    fun bossCountdown(waves: Int): String
}
