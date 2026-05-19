package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.data.local.DailyMissionDao
import com.whitefang.stepsofbabylon.domain.model.DailyMissionType
import java.time.LocalDate

/**
 * Increments today's COMPLETE_RESEARCH daily-mission progress when one or more research
 * projects have actually completed.
 *
 * Encapsulates the DAO lookup + progress update logic in a single testable unit so the
 * call sites in [com.whitefang.stepsofbabylon.presentation.labs.LabsViewModel]
 * (init / rushResearch / freeRush) cannot diverge — anyone calling the use case gets the
 * same gating semantics for free.
 *
 * Idempotent at the row level: if today's mission row is missing, already claimed, or
 * already completed, the use case is a no-op. Exceptions from the DAO are swallowed
 * (matches the prior `LabsViewModel.updateResearchMission` fail-open contract — a
 * transient DAO outage must not crash the Labs screen).
 *
 * R3-03 / GitHub #1: this use case must NOT advance the mission counter when
 * [completedCount] is zero or negative. The gating guard below (the early `return` on
 * `completedCount <= 0`) closes the false-trigger that previously fired every time the
 * Labs screen was opened, even with no in-flight research.
 */
class UpdateCompleteResearchMissionProgress(
    private val dailyMissionDao: DailyMissionDao,
) {
    suspend operator fun invoke(
        completedCount: Int,
        today: String = LocalDate.now().toString(),
    ) {
        // R3-03 gating: caller reports zero (or negative) completions => no mission tick.
        // This is the entire bug fix — opening Labs with nothing in flight previously
        // ticked COMPLETE_RESEARCH unconditionally because the historical helper ignored
        // its caller's signal of "how much actually finished".
        if (completedCount <= 0) return
        try {
            val missions = dailyMissionDao.getByDateOnce(today)
            val m = missions.find {
                it.missionType == DailyMissionType.COMPLETE_RESEARCH.name &&
                    !it.claimed &&
                    !it.completed
            } ?: return
            // Additive progress with cap: a single auto-complete batch can finish more
            // than one research project at once, so we sum the count into the existing
            // progress and clamp to the mission target. For COMPLETE_RESEARCH the target
            // is 1, so the clamp degenerates to the historical "set to 1, completed=true"
            // behaviour for the common single-completion path.
            val newProgress = (m.progress + completedCount).coerceAtMost(m.target)
            val isComplete = newProgress >= m.target
            dailyMissionDao.updateProgress(m.id, newProgress, isComplete)
        } catch (_: Exception) {
            // Swallowed: matches the prior LabsViewModel.updateResearchMission contract.
        }
    }
}
