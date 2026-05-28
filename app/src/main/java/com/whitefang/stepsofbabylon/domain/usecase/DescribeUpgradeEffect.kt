package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.OwnedCard
import com.whitefang.stepsofbabylon.domain.model.RapidFireSchedule
import com.whitefang.stepsofbabylon.domain.model.ResearchType
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import java.util.Locale
import kotlin.math.min

/**
 * Per-row "Now → Next" readout shown beneath each in-round upgrade entry (RO-11 #C).
 *
 * @property current Human-readable string for the upgrade's effect at the current
 *   workshop + in-round + lab level combination.
 * @property next Human-readable string for the effect after one more in-round purchase,
 *   or `null` when the upgrade is already at its [UpgradeConfig.maxLevel] (or, for capped
 *   stats, at its effective cap). UI renders "Now: <current> → Next: <next>" when [next]
 *   is non-null and "Now: <current> (MAX)" otherwise.
 */
data class UpgradeEffectReadout(
    val current: String,
    val next: String?,
)

/**
 * Translates the player's current upgrade levels into a human-readable Now → Next preview
 * that the in-round upgrade menu surfaces beneath each row (RO-11 #C, originally tracked
 * as RO-10). Pre-RO-11 the menu showed only the description text ("+2% base damage per
 * level") and the cost — players had no way to see the actual numerical effect of a
 * purchase until they were back at the post-round summary screen.
 *
 * **Stat resolution:** stat-bearing upgrades (DAMAGE / ATTACK_SPEED / HEALTH / etc.) call
 * [ResolveStats] twice — once at the current level snapshot, once with the relevant
 * in-round level incremented — and format the relevant [com.whitefang.stepsofbabylon.domain.model.ResolvedStats]
 * field. This keeps the readout in sync with the multiplicative formula in [ResolveStats]
 * (workshop × in-round × lab) without duplicating the math.
 *
 * **Card effects** ([ApplyCardEffects]) are post-applied to the resolved stats before
 * formatting (RO-12). The live engine pipeline runs `resolveStats → applyCardEffects →
 * engine.setStats(...)`; mirroring that order here keeps the readout in lockstep with the
 * actual ziggurat values. Without this step a player with WALKING_FORTRESS or GLASS_CANNON
 * equipped would see a HEALTH "Now" value that disagreed with the top HP bar by the
 * card's percentage. Cards have no effect on the four cash-utility upgrades or the two
 * hidden-from-in-round upgrades, so those branches read the level map directly and the
 * card-effects pass is a no-op for them.
 *
 * **Cash-utility upgrades** (CASH_BONUS / CASH_PER_WAVE / INTEREST / FREE_UPGRADES) and
 * the two hidden-from-in-round upgrades (STEP_MULTIPLIER / RECOVERY_PACKAGES) compute
 * directly from [UpgradeConfig.effectPerLevel] because they don't pass through
 * [com.whitefang.stepsofbabylon.domain.model.ResolvedStats]. Capped utilities clamp the
 * displayed percentage to the cap.
 *
 * Pure Kotlin — no Android imports — so the use case lives in `domain/usecase/` and is
 * test-friendly via JVM unit tests.
 */
class DescribeUpgradeEffect(
    private val resolveStats: ResolveStats = ResolveStats(),
    private val applyCardEffects: ApplyCardEffects = ApplyCardEffects(),
) {

    operator fun invoke(
        workshopLevels: Map<UpgradeType, Int>,
        inRoundLevels: Map<UpgradeType, Int>,
        labLevels: Map<ResearchType, Int>,
        type: UpgradeType,
        equippedCards: List<OwnedCard> = emptyList(),
    ): UpgradeEffectReadout {
        val current = inRoundLevels[type] ?: 0
        val maxLevel = type.config.maxLevel
        val isAtMax = maxLevel != null && (workshopLevels[type] ?: 0) + current >= maxLevel
        val nextInRound = if (isAtMax) inRoundLevels else inRoundLevels + (type to current + 1)

        val currentReadout = format(workshopLevels, inRoundLevels, labLevels, equippedCards, type)
        val nextReadout = if (isAtMax) null else format(workshopLevels, nextInRound, labLevels, equippedCards, type)
        return UpgradeEffectReadout(currentReadout, nextReadout)
    }

    /**
     * Formats a single point in upgrade-level space. For stat-bearing upgrades this calls
     * [ResolveStats], post-applies [ApplyCardEffects] to mirror the live engine pipeline
     * (RO-12), and reads the relevant [com.whitefang.stepsofbabylon.domain.model.ResolvedStats]
     * field; for cash utilities + hidden upgrades it reads [UpgradeConfig.effectPerLevel]
     * directly. The two paths intentionally don't share format strings — utility upgrades
     * have semantic prefixes ("+X%", "+X cash/wave") while stat upgrades use unit suffixes
     * ("X dmg", "X HP", "X/s").
     */
    private fun format(
        workshopLevels: Map<UpgradeType, Int>,
        inRoundLevels: Map<UpgradeType, Int>,
        labLevels: Map<ResearchType, Int>,
        equippedCards: List<OwnedCard>,
        type: UpgradeType,
    ): String {
        val raw = resolveStats(workshopLevels, inRoundLevels, labLevels)
        // Mirror BattleViewModel's resolve → applyCardEffects pipeline so the readout shows
        // the same ResolvedStats values the engine and ziggurat see. Card effects modify
        // damage / attackSpeed / maxHealth / defenseAbsolute / critChance / lifestealPercent /
        // bounceCount fields used by the stat-bearing branches below; non-stat fields
        // (cashBonusPercent, secondWindHpPercent, gemMultiplier) are returned via
        // CardEffectResult and are not consumed by this formatter.
        val stats = applyCardEffects(raw, equippedCards).stats
        return when (type) {
            // Multiplicative stats (workshop × in-round × lab outer multipliers).
            UpgradeType.DAMAGE -> fmt("%.1f dmg", stats.damage)
            UpgradeType.ATTACK_SPEED -> fmt("%.2f/s", stats.attackSpeed)
            UpgradeType.RANGE -> fmt("%.0f px", stats.range.toDouble())
            UpgradeType.HEALTH -> fmt("%.0f HP", stats.maxHealth)
            // 2-decimal precision needed at HEALTH_REGEN's small magnitude (RO-12). +2 %/lvl
            // on a base ~1.3/s is +0.026/s, which rounds away under %.1f and produces a
            // misleading "Now: 1.3/s → 1.3/s" readout for a real upgrade.
            UpgradeType.HEALTH_REGEN -> fmt("%.2f/s", stats.healthRegen)
            UpgradeType.KNOCKBACK -> fmt("%.1f px", stats.knockbackForce.toDouble())

            // Additive percentage caps (capped within ResolveStats already).
            UpgradeType.CRITICAL_CHANCE -> fmt("%.1f%%", stats.critChance * 100.0)
            UpgradeType.CRITICAL_FACTOR -> fmt("\u00d7%.2f", stats.critMultiplier)
            UpgradeType.DEFENSE_PERCENT -> fmt("%.1f%%", stats.defensePercent * 100.0)
            UpgradeType.DEFENSE_ABSOLUTE -> fmt("+%.0f blocked", stats.defenseAbsolute)
            UpgradeType.THORN_DAMAGE -> fmt("%.1f%% reflect", stats.thornPercent * 100.0)
            UpgradeType.LIFESTEAL -> fmt("%.1f%%", stats.lifestealPercent * 100.0)
            UpgradeType.DAMAGE_PER_METER -> fmt("+%.1f%%/m", stats.damagePerMeterBonus * 100.0)
            UpgradeType.DEATH_DEFY -> fmt("%.0f%%", stats.deathDefyChance * 100.0)

            // Discrete-step upgrades (every 20 / 15 / 1 level crosses a threshold).
            UpgradeType.MULTISHOT -> formatTargets(stats.multishotTargets)
            UpgradeType.BOUNCE_SHOT -> formatBounces(stats.bounceCount)
            UpgradeType.ORBS -> formatOrbs(stats.orbCount)

            // Cash-utility upgrades — no ResolvedStats representation, computed directly
            // from `effectPerLevel`. Combined level = workshop + in-round (matches the
            // `combinedLevelsForCash()` semantics in BattleViewModel).
            UpgradeType.CASH_BONUS -> {
                val total = (workshopLevels[type] ?: 0) + (inRoundLevels[type] ?: 0)
                fmt("+%.0f%% cash", total * type.config.effectPerLevel)
            }
            UpgradeType.CASH_PER_WAVE -> {
                val total = (workshopLevels[type] ?: 0) + (inRoundLevels[type] ?: 0)
                fmt("+%.0f cash/wave", total * type.config.effectPerLevel)
            }
            UpgradeType.INTEREST -> {
                val total = (workshopLevels[type] ?: 0) + (inRoundLevels[type] ?: 0)
                val pct = min(total * type.config.effectPerLevel, 10.0)
                fmt("%.1f%% interest", pct)
            }
            UpgradeType.FREE_UPGRADES -> {
                val total = (workshopLevels[type] ?: 0) + (inRoundLevels[type] ?: 0)
                val pct = min(total * type.config.effectPerLevel, 25.0)
                fmt("%.0f%% free", pct)
            }

            // Hidden-from-in-round upgrades — included so the same use case can power a
            // future Workshop-screen readout without a second formatter.
            // V1X-18 / ADR-0015: STEP_MULTIPLIER uses asymptotic curve `1 - (1-0.05)^level`,
            // delegating to SimulationMath.stepMultiplierBonus for the math. Format with 2
            // decimal places so adjacent levels at the high end (L99 = 99.41% vs L100 = 99.44%)
            // remain visibly distinct — the dead-content problem V1X-18 fixes.
            UpgradeType.STEP_MULTIPLIER -> {
                val total = (workshopLevels[type] ?: 0) + (inRoundLevels[type] ?: 0)
                val bonus = com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath.stepMultiplierBonus(total)
                val pct = bonus * 100.0
                fmt("+%.2f%% steps", pct)
            }
            UpgradeType.RECOVERY_PACKAGES -> {
                val total = (workshopLevels[type] ?: 0) + (inRoundLevels[type] ?: 0)
                val pct = min(total * type.config.effectPerLevel, 50.0)
                fmt("+%.0f%% heal", pct)
            }

            // Rapid Fire (R4-03) — Workshop-only, periodic attack-speed burst. Reads the
            // workshop level only because RAPID_FIRE is hidden from the in-round menu (see
            // `InRoundUpgradeMenu.hiddenInRound`). The readout shape mirrors the upgrade's
            // description: "60s/5s/2.0\u00d7" at L1, scaling to "permanent/3.0\u00d7" at L10.
            UpgradeType.RAPID_FIRE -> {
                val total = (workshopLevels[type] ?: 0) + (inRoundLevels[type] ?: 0)
                formatRapidFire(total)
            }
        }
    }

    private fun formatTargets(n: Int): String = if (n == 1) "1 target" else "$n targets"
    private fun formatBounces(n: Int): String = if (n == 1) "1 bounce" else "$n bounces"
    private fun formatOrbs(n: Int): String = if (n == 1) "1 orb" else "$n orbs"

    /**
     * Renders the RAPID_FIRE Now / Next readout (R4-03). At L0 the upgrade is unpurchased
     * and produces no burst, so the readout shows "inactive" — contrasts visibly with the
     * L1 "60s/5s/2.0\u00d7" preview so the player can see what the first purchase unlocks.
     * At L10 the duration matches the interval and the buff is permanent, so the format
     * collapses to "permanent/3.0\u00d7". Intermediate levels show the full
     * (interval, duration, multiplier) triple. All three values are linearly interpolated
     * by [RapidFireSchedule] so this formatter and [GameEngine.tickRapidFire] always agree.
     */
    private fun formatRapidFire(level: Int): String {
        if (level <= 0) return "inactive"
        val interval = RapidFireSchedule.interval(level)
        val duration = RapidFireSchedule.duration(level)
        val multiplier = RapidFireSchedule.multiplier(level)
        return if (RapidFireSchedule.isPermanent(level)) {
            fmt("permanent/%.1f\u00d7", multiplier.toDouble())
        } else {
            "${fmt("%.0fs", interval.toDouble())}/${fmt("%.0fs", duration.toDouble())}/${fmt("%.1f\u00d7", multiplier.toDouble())}"
        }
    }

    /**
     * Locale-independent number formatter. Pinning to [Locale.ROOT] guarantees the readout
     * is identical across devices (a German user's `%.1f` would otherwise produce `"12,5"`
     * instead of `"12.5"`, which would diverge from the rest of the English-only v1.0 UI
     * strings). Localization of the readout itself is a v2.0 effort — see plan-RO-11 § 9
     * open question #5.
     */
    private fun fmt(pattern: String, value: Double): String =
        String.format(Locale.ROOT, pattern, value)
}
