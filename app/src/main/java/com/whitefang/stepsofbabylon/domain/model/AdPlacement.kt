package com.whitefang.stepsofbabylon.domain.model

enum class AdPlacement { POST_ROUND_GEM, POST_ROUND_DOUBLE_PS, DAILY_FREE_CARD_PACK }

sealed class AdResult {
    data object Rewarded : AdResult()

    data object Cancelled : AdResult()

    data class Error(
        val message: String,
    ) : AdResult()
}
