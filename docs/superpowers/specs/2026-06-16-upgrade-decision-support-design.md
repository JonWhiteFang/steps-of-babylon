# Design Spec — Workshop Upgrade Decision Support (#29 / V1X-26, Gate F)

**Status:** Reviewed — passed the Adversarial Review Gate (35 raised → 21 surviving, **0 critical / 0 major**; all surviving findings applied in this revision). Ready for the implementation plan.
**Date:** 2026-06-16
**Issue:** #29 "Improve upgrade UX, readability, and decision support" · roadmap entry V1X-26
**Gate:** Closed-Test Readiness Gate **F** — "a player can tell whether an upgrade is worth buying" (`docs/plans/plan-FORWARD.md`)
**Scope discipline:** presentation + a small amount of net-new **pure** domain math. No schema change, no engine/economy change.

---

## 1. Problem & goal

A level-30 player faces ~24 Workshop upgrade options across three categories (Attack 9 / Defense 9 /
Utility 6 — `domain/model/UpgradeType.kt`, `domain/model/UpgradeCategory.kt`). Choosing well requires
arithmetic the player should not have to do: the Workshop card today shows the *current* effective
stat value and the next-level cost, but **not** what the stat becomes after purchase, nor any sense of
which upgrade gives the most combat benefit per Step spent (`presentation/workshop/UpgradeCard.kt`,
`presentation/workshop/WorkshopUiState.kt`).

**Goal:** give the player three decision aids on the Workshop screen, in increasing sophistication:

- **(C) Now → Next preview** — "155.3 → 159.2 dmg" beneath each upgrade.
- **(D) Value indicator** — for upgrades that affect combat, a comparable "+X.X% combat power per 1,000
  steps" number + a bar.
- **(A) Best Buy badge** — highlight the single highest-value upgrade the player can act on.

### In scope

- Workshop screen only.
- (A) Best Buy badge, (C) Now → Next preview, (D) value-per-step metric.
- One new pure combat-power index + one new pure ranking use case; reuse of the existing
  `DescribeUpgradeEffect` for the Now → Next strings.

### Explicitly out of scope (deferred, not dropped)

- **Cards equip-impact preview** — different model (no Step cost, 3-equip gate, separate
  `ApplyCardEffects` pass). Fast follow-up issue.
- **ROI sort / list reordering (B)** and **quick-buy multiplier ×5/Max (E)** — separable niceties;
  the Best Buy badge surfaces the winner without reordering, and batched buying is independent of
  decision support. Candidates for a later PR.
- **Readability / contrast / tap-target / outdoor-visibility theme** of #29's title — already addressed
  by the shipped Look-&-Feel Bundles A–E (design tokens, contrast pass, de-emoji). If a concrete
  readability gap on Workshop/Cards is found during implementation, file it as a separate follow-up
  rather than widening this PR.
- **Defense / Utility value ranking** — those categories get (C) only, no bar/badge (see §3.3 for why
  ranking heterogeneous non-combat stats is dishonest).
- **Pre-existing source-comment drift (not a code change here):** `UpgradeType.kt`'s "// Attack (8)"
  header comment is stale (the category has 9 entries since `RAPID_FIRE` was added). The spec's counts
  are correct; correcting the comment is a domain-source edit outside this presentation PR's scope —
  follow-up nit (review code-grounding BUG-4).

---

## 2. The combat-power index (the crux)

The feature hinges on one new pure function — a single comparable measure of an upgrade's combat
benefit. There is **no** existing aggregate-DPS function in the codebase (confirmed: only the per-hit
stochastic `CalculateDamage` and a UW-specific BLACK_HOLE dps). We introduce a deliberately simple,
**steady-state DPS proxy**:

```
combatPower(stats) =
    stats.damage
  × stats.attackSpeed
  × (1 + stats.critChance × (stats.critMultiplier − 1))
```

All three inputs are real fields on `domain/model/ResolvedStats.kt` (`damage`, `attackSpeed`,
`critChance`, `critMultiplier`). The third factor is the standard expected-crit multiplier
(`(1 + critChance × (critMultiplier − 1))`).

> **Why no `× multishotTargets` (review BUG-1/BUG-2):** an earlier draft multiplied by
> `multishotTargets`. It is dropped deliberately. (a) On the Workshop screen the index is evaluated on
> **workshop-dimension** stats only (§5.1), and MULTISHOT is `isWorkshopVisible = false`
> (`UpgradeType.kt`) + seeds 0, so `multishotTargets` is **always exactly 1** there — the factor never
> varies across candidates and cannot affect the ranking or the Best Buy. (b) Keeping it would also be
> *dimensionally asymmetric*: `bounceCount` is an equally valid multi-hit throughput term (each bounce
> re-runs `CalculateDamage` for a full hit — `GameEngine.onProjectileHitEnemy`), so including multishot
> while omitting bounce/orbs would misstate "what counts." Dropping it keeps the index an honest
> **single-target steady-state** measure that matches its own prose. (Orbs are not analogous anyway —
> they deal flat `0.5 × damage` with no crit via a separate path.)

**Honesty constraints (load-bearing — these protect the player and survive adversarial review):**

- It is a **proxy**, not a faithful battle simulation. It is evaluated on the **workshop-dimension**
  `ResolvedStats` (lab and in-round dimensions zero, per §5.1's "permanent levels" framing — review
  INFO-4), and it intentionally ignores all other throughput/utility terms: `multishotTargets`,
  `bounceCount`, `orbCount`, `range`, `damagePerMeterBonus`, knockback, and all sustain. It models
  *steady-state single-target throughput* and nothing else. (Because the Workshop never feeds
  multishot/bounce/orbs into the ranked candidate set — they are Defense or non-workshop-visible — this
  omission never misranks an Attack candidate.)
- The UI **must call it "combat power," never "DPS."** Calling it DPS would over-claim precision the
  proxy does not have.
- It is a **comparison instrument**, not a balance input. It must never feed the battle engine,
  `Simulation`, or any economy/grant path. It exists only to rank and display. This is **structurally
  enforced, not just disciplined** (review INV-3): `combatPower` returns a bare `Double`, while the
  engine's only stat channels (`GameEngine.setStats`/`updateZigguratStats`/`applyStats`,
  `ZigguratState.updateStats`) accept `ResolvedStats` — so the proxy is type-incompatible with every
  real-stat sink and cannot be substituted for one. The "combat power" naming rule below is the
  remaining *player-facing* honesty guard.

---

## 3. Value metric, ranking & coverage

### 3.1 Value per step

```
value(upgrade) = ( combatPower(statsAt level+1) − combatPower(statsAt level) ) ÷ stepCost(level)
```

- `statsAt(level)` and `statsAt(level+1)` come from the **existing pure** `ResolveStats` use case
  (`domain/usecase/ResolveStats.kt`), incrementing the **workshop** dimension (see §5.1 for why this
  matters and is not the in-round dimension).
- `stepCost(level)` is the **existing pure** `CalculateUpgradeCost`
  (`domain/usecase/CalculateUpgradeCost.kt`, `ceil(baseCost × scaling^level)`).

### 3.2 Player-facing display number

The value bar's label reads **"+X.X% power / 1,000 steps"**, computed as:

```
percentPerKSteps = value(upgrade) ÷ combatPower(statsAt level) × 1000 × 100
```

**Why the percent form is safe for ranking:** dividing by `combatPower(statsAt level)` (the player's
*current* combat power) divides every candidate by the **same constant** at a given refresh, so it
**does not change the relative order**. Ranking on raw `value` and on `percentPerKSteps` yield the
identical Best Buy. We therefore get a scale-free, interpretable number for free, and may rank on
either — the spec ranks on raw `value` and displays `percentPerKSteps`.

> The percent form assumes `combatPower(statsAt level) > 0` — always true given positive base
> `damage`/`attackSpeed` (`ZigguratBaseStats`: 10.0 / 1.0) and crit factor ≥ 1. The implementation
> should nonetheless guard the division so a hypothetical future zero-base regression degrades to "no
> bar" rather than NaN/∞ (review INFO-1).

**Display precision (review consistency BUG-4):** `percentPerKSteps` is rendered to **one decimal
place** ("+X.X%"). Because the bar is shown only when `value != null` ⇒ `Δpower > 0` ⇒
`percentPerKSteps > 0`, the leading "+" is always valid (never zero/negative). A small-but-positive
value that would round to `+0.0%` (e.g. Critical Factor at very low crit chance) is **floored to
`+0.1%`** so a card that legitimately carries a bar never displays a contradictory `+0.0%`. All
numeric formatting uses `Locale.ROOT` (§5.3).

The **bar fill** is `percentPerKSteps` normalised against the maximum `percentPerKSteps` among the
current **bar-eligible set** (so the Best Buy bar is full and others are proportional) — **spec
default; if instead an absolute scale is chosen, its full-bar anchor value must be defined (§9 Q3).**
"Bar-eligible set" and "Best-Buy candidate set" denote the **same** set (Δpower > 0, not maxed, cost
computable) — see §3.4.

### 3.3 Coverage rule — who gets a bar + Best-Buy candidacy

> **A value bar and Best-Buy candidacy apply to an upgrade only when its `Δpower > 0`.**

This single mechanical rule (not a hardcoded allowlist) yields:

| Workshop-visible upgrade | Δpower > 0? | Treatment |
|---|---|---|
| Damage, Attack Speed, Critical Chance | yes | bar + Best-Buy candidacy + (C) |
| Critical Factor | yes **iff** critChance > 0 | bar + candidacy + (C) — naturally zero-valued until the player owns crit chance (synergy captured by the index) |
| Range, Damage-per-meter, Rapid Fire | no (not in index / runtime-variable / periodic burst) | (C) only |
| All Defense (Health, Regen, Defense %, Defense abs, Knockback, Thorns, Lifesteal, Death-Defy) | no | (C) only |
| All Utility (Cash Bonus, Cash/Wave, Interest, Free Upgrades, Step Multiplier, Recovery) | no | (C) only |

Computing the bar **only when `Δpower > 0`** keeps the list self-maintaining: if a stat later starts
feeding the index, it gains a bar automatically; no allowlist to drift.

> Note on visibility: MULTISHOT and BOUNCE_SHOT are `isWorkshopVisible = false`
> (`UpgradeType.kt`), and the VM additionally hides STEP_MULTIPLIER + RECOVERY_PACKAGES
> (`WorkshopViewModel.hiddenUpgrades`). The coverage rule operates over the **already-visible** set the
> Workshop renders; it does not change which upgrades are visible.

### 3.4 Best Buy selection

The **candidate set** is defined once and used for both bar normalisation (§3.2) and Best-Buy
selection: visible upgrades with **Δpower > 0, not maxed, cost computable**. (These three predicates
coincide in practice — a maxed multiplicative upgrade has `statsAt(level+1) == statsAt(level)` ⇒
`Δpower = 0`, and `CalculateUpgradeCost` is always ≥ the minimum `baseCost` > 0 — so the "not maxed"
and "cost computable" clauses are defensive, not separate filters.) Among that set:

1. **Prefer the highest-`value` upgrade the player can currently afford** (cost ≤ current Steps).
2. **If no candidate is affordable**, fall back to badging the highest-`value` candidate overall, in a
   **greyed "save up for this" state** (visually distinct from the actionable badge).
3. **Exactly one** upgrade carries the badge at any time (the ranking use case enforces this; ties
   broken deterministically — see §5.2).

---

## 4. Architecture — four units (Approach 1: pure use cases + thin UI)

| Unit | Layer | Responsibility | Depends on |
|---|---|---|---|
| **`CombatPower`** (new) | `domain/usecase` (pure) | `operator fun invoke(stats: ResolvedStats): Double` — the §2 index. Nothing else. | `ResolvedStats` |
| **`EvaluateUpgradeValue`** (new) | `domain/usecase` (pure) | Given the workshop-level map, current Steps balance, and the candidate `UpgradeType` set, returns `List<UpgradeValue>` with `valuePerStep`, `percentPerKSteps`, `barFraction`, `isBestBuy`, and `bestBuyAffordable` (per the result type below). Owns the §3.3 Δpower>0 filter, §3.4 Best-Buy rule, and the "exactly one badge" invariant. | `ResolveStats`, `CombatPower`, `CalculateUpgradeCost` |
| **`DescribeUpgradeEffect`** (existing, reused + extended) | `domain/usecase` (pure) | Now → Next strings (C). **Change:** add a workshop-dimension preview path (§5.1) so the Workshop screen previews the *permanent*-level next value. | unchanged deps |
| **`WorkshopViewModel` + `UpgradeCard` + `WorkshopUiState`** | `presentation` | VM composes the three use cases into new `UpgradeDisplayInfo` fields; `UpgradeCard` renders Now→Next text + value bar + Best-Buy badge (thin, no math). | the use cases |

Both new use cases live in `domain/usecase/` and are therefore swept by `architecture/DomainPurityTest`
(build fails on any `android.`/`androidx.` import — review INV-1). They follow the existing
**no-Hilt-instantiation** pattern: the VM news them up directly (cf. `WorkshopViewModel` constructs
`ResolveStats()`/`CalculateUpgradeCost()` itself), so no Android/Hilt annotation leaks into `domain/`.

Result type (illustrative — exact shape decided in the plan):

```kotlin
data class UpgradeValue(
    val type: UpgradeType,
    val valuePerStep: Double,        // ranking key (raw Δpower ÷ cost)
    val percentPerKSteps: Double,    // display number ("+X.X% power / 1,000 steps")
    val barFraction: Float,          // normalised 0f..1f for the bar
    val isBestBuy: Boolean,
    val bestBuyAffordable: Boolean,  // false → greyed "save up" state
)
```

New `UpgradeDisplayInfo` fields (`WorkshopUiState.kt`, currently
`type,level,cost,isMaxed,canAfford,description,statValue`): add `nowNext: UpgradeEffectReadout?`,
`value: UpgradeValue?` (null for non-combat upgrades → renders no bar/badge).

The "exactly one Best Buy" rule and the affordable-first fallback live in **`EvaluateUpgradeValue`**
(tested domain code), never inline in the VM.

---

## 5. Data flow & correctness details

### 5.1 Workshop dimension, not in-round (a real correctness trap)

`DescribeUpgradeEffect` today increments the **in-round** level dimension
(`inRoundLevels + (type to current + 1)`, `DescribeUpgradeEffect.kt:71`) because it powers the
in-round upgrade menu. For **multiplicative** stats (e.g. `damage`, `attackSpeed`), `ResolveStats`
combines `base × (1 + ws·k) × (1 + ir·k) × (1 + lab·k)` — so incrementing the workshop dimension vs.
the in-round dimension produces **different** "Next" numbers when the other dimension is non-zero. The
Workshop screen is about **permanent** levels, so it MUST preview the workshop-dimension increment, or
the displayed "Next" value would be subtly wrong.

> The `(1 + lab·k)` term above is illustrative of the general shape — the **exact** per-stat factors
> differ (review BUG-3/INFO-4): `damage` carries *two* lab tiers (`DAMAGE_RESEARCH` +5%/lvl and
> `ENEMY_INTEL` +2%/lvl) while `attackSpeed` carries none. `EvaluateUpgradeValue`/`DescribeUpgradeEffect`
> call the **existing** `ResolveStats` rather than reimplementing this, so the per-stat factor count
> never needs restating; the dimension trap holds identically regardless. Note also that the
> **additive** stats (`critChance`/`critMultiplier` via `total(type) = ws + ir`) are dimension-agnostic
> — incrementing ws vs ir is identical for them — so the regression test in §7 pins the trap with a
> *multiplicative* stat, where it actually bites.

This spec adds a **distinct** workshop-dimension preview path to `DescribeUpgradeEffect` — not a one-line
swap of which map is incremented. The new path must re-derive its own baseline and cap from the
**workshop** dimension: `current = workshopLevels[type]`, the `isAtMax`/`next == null` gate against the
workshop-only cap (matching `PurchaseUpgrade`/`WorkshopViewModel`, which gate on the workshop level),
and increment `workshopLevels`. `EvaluateUpgradeValue` performs the **same** workshop-level increment
for its `statsAt(level+1)`; to guarantee the Now→Next string and the value-bar delta can't diverge on
the increment/cap semantics, the workshop-dimension `level → level+1 → ResolveStats` step should be a
single shared helper (or `DescribeUpgradeEffect`'s new path delegates to it). The §7 test covers an
at/near-`maxLevel` workshop case, not only the "differs-from-in-round" case (review INV-4/INV-6).

### 5.2 Refresh flow (in `WorkshopViewModel`)

```
workshopLevels ← WorkshopRepository.observeAllUpgrades()  (already loaded; WorkshopViewModel.kt:53 already calls ResolveStats)
stepsBalance   ← PlayerRepository.observeWallet().stepBalance   (NOT StepRepository — review FEAS-1)
for each visible upgrade:
    nowNext = DescribeUpgradeEffect(workshopLevels, …, type)          // (C), workshop-dimension
candidates = visible upgrades where ΔcombatPower > 0
values     = EvaluateUpgradeValue(workshopLevels, stepsBalance, candidates)   // (A,D)
→ map into UpgradeDisplayInfo(+nowNext, +value) → StateFlow → UpgradeCard
```

> Test wiring (review FEAS-1): the Steps balance comes from `PlayerRepository.observeWallet()`
> (faked by `FakePlayerRepository(PlayerProfile(stepBalance = …))`, as the existing
> `WorkshopViewModelTest` already does); levels from `WorkshopRepository.observeAllUpgrades()`
> (`FakeWorkshopRepository`). `FakeStepRepository` (daily step summaries) is **not** the balance source.

- Best Buy is **recomputed every refresh** (level-up, balance change, tab switch) — **never cached**.
- Best Buy is scoped to the **current visible category tab's** candidate set (the player is choosing
  within the tab they're looking at). *(Confirm in plan: if Best Buy should be global across tabs, it
  is a trivial change — but per-tab matches where the choice is being made.)*
- Maxed upgrades: excluded from candidacy; still render their (C) readout as "MAX" (existing behavior),
  no bar.

### 5.3 Edge cases & guards

- **Δpower ≤ 0** → no bar, not a candidate (one rule covers Range/RapidFire/Defense/Utility *and*
  Crit-Factor-at-zero-crit).
- **cost = 0 or maxed** → excluded from value (no divide-by-zero).
- **empty candidate set** (e.g. a tab with no combat upgrades, or all maxed) → no badge, no bars; (C)
  still shows.
- **ties on `value`** → deterministic tie-break by `UpgradeType` declaration order (so the badge does
  not flicker between equal options across refreshes).
- **Locale** → all new numeric formatting MUST use `Locale.ROOT` (route Now→Next through
  `DescribeUpgradeEffect`'s existing `fmt`, and format the value-bar "+X.X%" label via `Locale.ROOT` in
  a pure helper / the VM). **Do not pattern-match against `WorkshopViewModel.statValueFor`** — it uses
  the default-locale `"%.1f".format(...)` extension (a pre-existing latent inconsistency that would
  render "12,5" on a German device; fixing it is out of scope, but the new code must not copy it)
  (review INV-5).
- **Naming** → UI strings say "combat power" / "power"; never "DPS".

---

## 6. UI / presentation notes

- **Now → Next (C):** a line beneath the description, e.g. `155.3 → 159.2 dmg`; "MAX" when at cap.
  Reuses the existing `UpgradeEffectReadout` shape.
- **Value bar + label (D):** a thin horizontal bar (existing design tokens / `ColorLerp` available)
  with the "+X.X% power / 1,000 steps" label; rendered only when `value != null`.
- **Best Buy badge (A):** a static "★ BEST BUY" chip on the winning card. The affordable and
  unaffordable states use the **same chip and the same "★ BEST BUY" label** (`bestBuyAffordable` is a
  boolean, not a templated shortfall number); the unaffordable "save up for this" state is signalled by
  **reduced alpha** (following the established dim-by-alpha convention — `Rarity.kt`'s `alpha` param for
  locked UWs, `UpgradeCard`'s 0.55f unaffordable / 0.85f maxed dims; the plan picks the exact value).
  **Static chip — not a `PurchasePulse`** (the pulse is reserved for the spend moment). The exact
  token/alpha is decided in the plan (review BUG-5).
- **Reuse, don't reinvent:** lean on `presentation/ui/` building blocks (design tokens; `CurrencyDisplay`
  for the Step cost — Workshop currently prints raw Steps, adopting `CurrencyDisplay` here is a small
  win but **optional**, decided in the plan to keep scope tight). No new animation; no reduced-motion
  concern (bar + chip are static).
- **No layout test under Robolectric** — consistent with PR-4736; bar/badge rendering verified on-device.

---

## 7. Testing strategy

All load-bearing logic is pure → JVM unit tests (no emulator), TDD red→green:

- **`CombatPowerTest`** — base stats; each factor's contribution; crit synergy (crit factor at zero vs.
  non-zero crit chance); multishot scaling; monotonicity (more of any contributing stat ⇒ higher power).
- **`EvaluateUpgradeValueTest`** — correct winner; Δpower>0 filter excludes Range/Defense/Utility;
  maxed/cost-0 exclusion; affordable-first selection; **best-overall-greyed fallback when nothing
  affordable**; "exactly one Best Buy" invariant; deterministic tie-break; empty-candidate set;
  divide-by-zero guards.
- **`DescribeUpgradeEffectTest`** (extend) — the new workshop-dimension path yields the correct
  permanent-level "Next" value, and it **differs** from the in-round path for a multiplicative stat
  when the in-round dimension is non-zero (pins §5.1).
- **`WorkshopViewModelTest`** (repository fakes per `test/fakes/`) — new `UpgradeDisplayInfo` fields
  populated; Best Buy flips correctly when levels/balance change; per-tab scoping.
- **No Compose-rule UI test** (PR-4736) — on-device verification of bar/badge/greyed-fallback.

The new `CombatPowerTest`/`EvaluateUpgradeValueTest` MUST NOT re-assert the balance constants /
cost-curve values that `ResolveStatsTest` and `CalculateUpgradeCostTest` already pin — those remain the
single source of truth; the new tests assert *index/ranking* behaviour, not stat coefficients, so no
second source of truth can drift (review INV-2). No `UpgradeConfig`/`ResolveStats` coefficient is edited.

**Doc-sync obligations (PR Task-List Convention, CLAUDE.md — review scope-BUG-2).** Before the
STATE/RUN_LOG step, this PR's task list must: add `docs/steering/source-files.md` entries for the new
`CombatPower.kt` / `EvaluateUpgradeValue.kt` (+ their test files); add a `CHANGELOG.md` section; bump
the **headline JVM test count** in CLAUDE.md (exact delta set during implementation); then append
`docs/agent/STATE.md` + `docs/agent/RUN_LOG.md`. (The plan owns the full ordered task list and is itself
re-reviewed by the Adversarial Review Gate.)

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Proxy mistaken for true DPS by players or reviewers | Name it "combat power"; document the ignored fields in code KDoc; never feed it to the engine. |
| Workshop "Next" value wrong (in-round vs workshop dimension) | §5.1 — workshop-dimension preview path + a regression test pinning the difference. |
| Best Buy points at something unbuyable | §3.4 affordable-first with greyed fallback. |
| Badge flicker on ties / refreshes | Deterministic tie-break by enum order; recompute-not-cache is stable given stable inputs. |
| Scope creep into Cards / sort / quick-buy / readability | §1 out-of-scope list; each is a separate follow-up. |
| Touching a fragile zone | None touched: no engine/economy/schema/concurrency change. `ResolvedStats` is **read**, not modified; `domain/model/` balance constants untouched. |

---

## 9. Open questions for review

1. **Best Buy scope:** per-current-tab (spec default) vs. global across all three tabs? Per-tab matches
   where the choice is made; global would need an "overall best" affordance. *(Low-cost to switch.)*
2. **Adopt `CurrencyDisplay` on Workshop** as part of this PR (small readability win, slight scope
   growth) or leave Workshop's raw-Steps rendering untouched? Spec leans **leave it** to keep scope tight.
3. **Bar normalisation** — **default = relative-max** (normalise against the candidate-set maximum). The
   alternative (an absolute scale) is the only thing left open, and if chosen must define its full-bar
   anchor value (a fixed `percentPerKSteps` clamp, or a historical session max). Relative is more
   legible; absolute is more honest across sessions.

---

## 10. Traceability

- Issue #29 / roadmap V1X-26 (`docs/plans/plan-V1X-roadmap.md`).
- Gate F (`docs/plans/plan-FORWARD.md`).
- Reuses: `ResolveStats`, `CalculateUpgradeCost`, `DescribeUpgradeEffect`, `presentation/ui/` tokens.
- Follows the existing pure-use-case + thin-UI **code structure** of `DescribeUpgradeEffect` (and,
  structurally, `QuickInvest`). **Caveat (review scope-BUG-1):** `QuickInvest` (cheapest-affordable
  pick) is currently **dead UI** — its FAB was removed (commit `5e3530c`), leaving the VM method +
  use case + a stale Help-text mention with no on-screen affordance. Best Buy (highest *value*) is a
  different selection rule and intentionally supersedes it; do **not** model new wiring on QuickInvest's
  (absent) UI path. The orphaned QuickInvest button/Help-text drift is pre-existing and **out of scope**
  for this PR (file as a separate follow-up).
- Subject to the **Adversarial Review Gate** (CLAUDE.md) before a plan is written — **passed**
  (see Status header; 35 raised → 21 surviving applied → 0 critical/major).
