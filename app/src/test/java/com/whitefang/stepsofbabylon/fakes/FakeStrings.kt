package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.Strings
import com.whitefang.stepsofbabylon.domain.model.EnemyType

/**
 * Pure-Kotlin fake of [Strings] for JVM tests (no Android). Returns deterministic, recognizable
 * strings so a test can assert the seam was consulted (distinct from GameEngine's literal fallback).
 */
class FakeStrings : Strings {
    override fun healHp(hp: Int) = "FAKE_HEAL_$hp"
    override fun rapidFireBurst() = "FAKE_RAPID_FIRE"
    override fun cashReward(cash: Long) = "FAKE_CASH_$cash"
    override fun stepReward(steps: Long) = "FAKE_STEP_$steps"
    override fun powerStoneReward(ps: Long) = "FAKE_PS_$ps"
    override fun enemyTypeName(type: EnemyType) = "FAKE_${type.name}"
    override fun waveComposition(counts: Map<EnemyType, Int>) =
        "FAKE_COMP:" + counts.entries.joinToString(",") { "${it.value}:${it.key.name}" }
    override fun bossCountdown(waves: Int) = "FAKE_BOSS_$waves"
}
