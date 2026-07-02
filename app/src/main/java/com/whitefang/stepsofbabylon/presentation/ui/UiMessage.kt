package com.whitefang.stepsofbabylon.presentation.ui

import android.content.Context
import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.R

/**
 * A transient, localizable user message emitted by a ViewModel and resolved to a String at the
 * Compose call site (see any screen's `LaunchedEffect(state.userMessage)`). Carries a @StringRes id
 * (+ optional positional format args) so ViewModels stay Context-free and pure-JVM testable — tests
 * assert the UiMessage type/args, not English text. Mirrors the @StringRes pattern used by
 * Screen.secondaryTitle / CurrencyType.label (ADR-0014 phase 2/3). i18n #34 phase 3.
 */
sealed interface UiMessage {
    @get:StringRes
    val resId: Int
    val args: List<Any> get() = emptyList()

    // --- Currency / affordability ---
    data object NotEnoughGems : UiMessage {
        override val resId = R.string.msg_not_enough_gems
    }

    data object NotEnoughSteps : UiMessage {
        override val resId = R.string.msg_not_enough_steps
    }

    // --- Workshop / Labs / Cards level + slot states ---
    data object AlreadyMaxLevel : UiMessage {
        override val resId = R.string.msg_already_max_level
    }

    data object NoAffordableUpgrades : UiMessage {
        override val resId = R.string.msg_no_affordable_upgrades
    }

    data object CardAtMaxLevel : UiMessage {
        override val resId = R.string.msg_card_at_max_level
    }

    data object NotEnoughCopies : UiMessage {
        override val resId = R.string.msg_not_enough_copies
    }

    data object ResearchComingSoon : UiMessage {
        override val resId = R.string.msg_research_coming_soon
    }

    data object NoResearchSlot : UiMessage {
        override val resId = R.string.msg_no_research_slot
    }

    data object AlreadyResearching : UiMessage {
        override val resId = R.string.msg_already_researching
    }

    data object SeasonPassRequired : UiMessage {
        override val resId = R.string.msg_season_pass_required
    }

    data object FreeRushUsed : UiMessage {
        override val resId = R.string.msg_free_rush_used
    }

    data object NoActiveResearch : UiMessage {
        override val resId = R.string.msg_no_active_research
    }

    data object NotEnoughGemsOrMaxSlots : UiMessage {
        override val resId = R.string.msg_not_enough_gems_or_max_slots
    }

    // --- Missions ---
    data object NotEnoughStepsMission : UiMessage {
        override val resId = R.string.msg_mission_not_enough_steps
    }

    data object MilestoneAlreadyClaimed : UiMessage {
        override val resId = R.string.msg_milestone_already_claimed
    }

    /**
     * Missions UnknownCosmetic — %1$s = the cosmetic id being finalised.
     * (This is the concrete realization of the spec §4 generic `WithArgs(resId, args)` arg-case;
     * a named case is preferred over a generic (resId,args) pair for type-safety — intentional
     * divergence from the spec's illustrative `WithArgs`, per review finding F6.)
     */
    data class RewardUnavailable(
        val cosmeticId: String,
    ) : UiMessage {
        override val resId = R.string.msg_reward_unavailable
        override val args = listOf<Any>(cosmeticId)
    }

    // --- Ad flow (VM-side static fallbacks; the data-layer message itself arrives via Raw) ---
    data object AdCancelled : UiMessage {
        override val resId = R.string.msg_ad_cancelled
    }

    data object AdFailed : UiMessage {
        override val resId = R.string.msg_ad_failed
    }

    /**
     * Escape hatch for a message produced BELOW the presentation layer that is ALREADY localized
     * (a billing/ad Error whose text came from context.getString in the data layer — categories
     * A′-billing / A′-ads). Carries the resolved String verbatim. NEVER wrap an un-localized
     * lower-layer string in Raw.
     */
    data class Raw(
        val text: String,
    ) : UiMessage {
        override val resId: Int get() = 0 // unused; resolve() matches Raw before reading resId
    }

    companion object
}

/** Resolve to a display String. `Raw` is matched before `resId` is read, so `Raw.resId = 0` is never used. */
fun UiMessage.resolve(context: Context): String =
    when (this) {
        is UiMessage.Raw -> text
        else -> context.getString(resId, *args.toTypedArray())
    }
