package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.SupplyDrop
import com.whitefang.stepsofbabylon.domain.model.SupplyDropReward
import com.whitefang.stepsofbabylon.domain.model.SupplyDropTrigger
import kotlin.random.Random

class GenerateSupplyDrop(
    private val random: Random = Random,
) {
    companion object {
        const val MAX_INBOX = 10
        private const val MILESTONE_THRESHOLD = 10_000L
        private const val STEP_THRESHOLD_INTERVAL = 2_000L
        private const val THRESHOLD_CHANCE_PER_100 = 0.05
        private const val RANDOM_CHANCE_PER_500 = 0.01
    }

    operator fun invoke(
        dailyCreditedSteps: Long,
        lastCheckSteps: Long,
        timestampMs: Long,
        unclaimedCount: Int,
    ): SupplyDrop? {
        if (unclaimedCount >= MAX_INBOX) return null
        if (dailyCreditedSteps <= lastCheckSteps) return null

        // Priority 1: Daily milestone (10k boundary crossing)
        if (dailyCreditedSteps >= MILESTONE_THRESHOLD && lastCheckSteps < MILESTONE_THRESHOLD) {
            return makeDrop(SupplyDropTrigger.DAILY_MILESTONE, SupplyDropReward.GEMS, 5, timestampMs)
        }

        val delta = dailyCreditedSteps - lastCheckSteps

        // Priority 2: Step threshold — 2,000-step boundary crossed
        val prevBoundary = lastCheckSteps / STEP_THRESHOLD_INTERVAL
        val currBoundary = dailyCreditedSteps / STEP_THRESHOLD_INTERVAL
        if (currBoundary > prevBoundary) {
            // #22: one 5%-roll opportunity per 100-step sub-interval of this delta (min one).
            // Pre-fix this was `((stepsAfterBoundary + delta).coerceAtMost(delta) / 100)…`, but
            // since stepsAfterBoundary >= 0 the coerceAtMost always selected `delta`, making the
            // stepsAfterBoundary term dead. Simplified to the equivalent expression — identical
            // shipped cadence, dead boundary-relative math removed.
            val checks = (delta / 100).coerceAtLeast(1)
            for (i in 0 until checks) {
                if (random.nextDouble() < THRESHOLD_CHANCE_PER_100) {
                    return if (random.nextBoolean()) {
                        makeDrop(
                            SupplyDropTrigger.STEP_THRESHOLD,
                            SupplyDropReward.STEPS,
                            random.nextInt(50, 201),
                            timestampMs,
                        )
                    } else {
                        makeDrop(
                            SupplyDropTrigger.STEP_THRESHOLD,
                            SupplyDropReward.GEMS,
                            random.nextInt(1, 4),
                            timestampMs,
                        )
                    }
                }
            }
        }

        // Priority 3: Random — 1% per 500 steps
        val randomChecks = delta / 500
        for (i in 0 until randomChecks) {
            if (random.nextDouble() < RANDOM_CHANCE_PER_500) {
                return rollRandomReward(timestampMs)
            }
        }

        return null
    }

    private fun rollRandomReward(timestampMs: Long): SupplyDrop =
        when (random.nextInt(4)) {
            0 -> makeDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.STEPS, random.nextInt(100, 301), timestampMs)
            1 -> makeDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.GEMS, random.nextInt(1, 3), timestampMs)
            2 -> makeDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.POWER_STONES, 1, timestampMs)
            else -> makeDrop(SupplyDropTrigger.RANDOM, SupplyDropReward.CARD_COPY, random.nextInt(0, 9), timestampMs)
        }

    private fun makeDrop(
        trigger: SupplyDropTrigger,
        reward: SupplyDropReward,
        amount: Int,
        timestampMs: Long,
    ) = SupplyDrop(
        id = 0,
        trigger = trigger,
        reward = reward,
        rewardAmount = amount,
        claimed = false,
        createdAt = timestampMs,
    )
}
