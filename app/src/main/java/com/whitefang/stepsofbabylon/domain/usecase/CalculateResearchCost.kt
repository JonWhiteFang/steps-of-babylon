package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.ResearchType
import kotlin.math.ceil
import kotlin.math.pow

class CalculateResearchCost {
    operator fun invoke(
        type: ResearchType,
        currentLevel: Int,
    ): Long = ceil(type.baseCostSteps * type.costScaling.pow(currentLevel)).toLong()
}
