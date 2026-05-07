package com.whitefang.stepsofbabylon.domain.model

enum class SupplyDropTrigger(val message: String) {
    STEP_THRESHOLD("A supply crate was spotted on your path!"),
    DAILY_MILESTONE("10K steps! A rare supply drop has materialized."),
    RANDOM("Something shimmers ahead on the trail..."),
}
