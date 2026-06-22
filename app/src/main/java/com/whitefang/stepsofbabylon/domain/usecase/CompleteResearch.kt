package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import kotlinx.coroutines.flow.first

class CompleteResearch(
    private val labRepository: LabRepository,
) {
    sealed class Result {
        data class Completed(val newLevel: Int) : Result()
        data object NotReady : Result()
        data object NotActive : Result()
    }

    /**
     * @param type the research type to complete.
     * @param completesAt the epoch-millis timestamp at which this research slot expires.
     * @param now the TRUSTED current time. Callers under #211 pass the monotonically-capped trusted-now
     *   (the `TimeIntegrity` `trustedWallClock` anchor), NOT the raw wall-clock, so an in-session forward
     *   clock jump can't complete research early. The default is the raw clock for tests / non-guarded
     *   call sites.
     */
    suspend operator fun invoke(
        type: ResearchType,
        completesAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val activeList = labRepository.observeActiveResearch().first()
        if (activeList.none { it.type == type }) return Result.NotActive
        if (now < completesAt) return Result.NotReady

        val level = labRepository.getResearchLevel(type)
        labRepository.completeResearch(type)
        return Result.Completed(newLevel = level + 1)
    }
}
