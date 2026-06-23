package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResearchType
import kotlin.math.pow

class CalculateResearchTime {
    operator fun invoke(
        type: ResearchType,
        currentLevel: Int,
    ): Double = type.baseTimeHours * type.timeScaling.pow(currentLevel)
}
