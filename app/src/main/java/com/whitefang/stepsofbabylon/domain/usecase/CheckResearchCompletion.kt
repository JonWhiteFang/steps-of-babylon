package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.repository.LabRepository
import kotlinx.coroutines.flow.first

class CheckResearchCompletion(
    private val labRepository: LabRepository,
) {
    /**
     * @param now the TRUSTED current time. Callers under #211 pass the monotonically-capped trusted-now
     *   (the `TimeIntegrity` `trustedWallClock` anchor), NOT the raw wall-clock, so an in-session forward
     *   clock jump can't complete research early. The default is the raw clock for tests / non-guarded
     *   call sites.
     */
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): List<ResearchType> {
        val activeList = labRepository.observeActiveResearch().first()
        val completed = mutableListOf<ResearchType>()
        for (research in activeList) {
            if (now >= research.completesAt) {
                labRepository.completeResearch(research.type)
                completed += research.type
            }
        }
        return completed
    }
}
