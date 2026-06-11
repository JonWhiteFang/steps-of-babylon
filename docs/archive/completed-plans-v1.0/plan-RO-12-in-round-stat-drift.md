# Plan RO-12 — In-round stat drift bugfix bundle

| Field | Value |
|---|---|
| **Status** | Active |
| **Severity** | 🔴 **Critical** for Bug 1 (closed-test blocker) · 🟠 Moderate for Bugs 2 + 3 · 🟡 Cosmetic for Bug 4 |
| **Date opened** | 2026-05-19 |
| **Author** | Discovered during v5 internal-track on-device smoke test (Wave 4 screenshot) |
| **Predecessors** | RO-08 (4-fix upgrade-wiring bundle), RO-09 (3-fix pre-closed-test bundle), RO-11 (Labs wiring + in-round visibility) |
| **Target window** | Pre-closed-test — must land before promoting v5 → closed track |

---

## 1. Executive summary

The v5 (versionCode 5) internal-track build was installed on a physical device at 06:21 BST 2026-05-19. The `RO-11 #C` "Now → Next" readout rendered correctly on the DEFENSE tab, but the screenshot exposed a **stat drift between the in-round upgrade-effect readout and the live ziggurat HP** (1647 HP in the readout, 1568 HP in the top status bar — a ~5 % gap consistent with `HEALTH_RESEARCH` Lv 1).

Root-cause investigation surfaced **three real bugs** in the in-round stat-resolution paths plus a fourth display-precision issue:

| # | Bug | Severity | Visibility | RO-11-introduced? |
|---|---|---|---|---|
| 1 | `BattleViewModel.purchaseInRoundUpgrade` calls `resolveStats(workshopLevels, inRoundLevels)` without `labLevels` — strips lab research bonuses on every in-round purchase for the rest of the round. | 🔴 **Critical** | High — directly defeats RO-11 acceptance check #1 ("DAMAGE_RESEARCH L5 visibly hits harder") any time a player buys an in-round upgrade. | **Yes** — `labLevels` parameter added to `ResolveStats` in RO-11 #A.1; one call site missed during the wiring pass. |
| 2 | Same call site does not re-apply `ApplyCardEffects` after recomputing stats — strips card effects (WALKING_FORTRESS, GLASS_CANNON, IRON_SKIN, SHARP_SHOOTER, VAMPIRIC_TOUCH, CHAIN_REACTION) on every in-round purchase. | 🟠 Moderate | Medium — pre-existing since cards landed (Plan 17), but masked by smaller magnitudes; RO-11 made it visible by stacking lab on top. | **No** — pre-existing, but unmasked by RO-11 making the drift visible. |
| 3 | `DescribeUpgradeEffect` does not apply card effects either, so the readout drifts from the live engine when any stat-modifying card is equipped. The use case's documented contract ("guarantees the readout cannot drift from the actual post-purchase stats") is violated. | 🟠 Moderate | Medium — visible to any tester with WALKING_FORTRESS or GLASS_CANNON equipped. | **Yes** — introduced by RO-11 #C alongside the use case. |
| 4 | `HEALTH_REGEN` readout uses `%.1f/s` format. At base ~1.3/s and +2 %/level, per-level delta is ~0.026/s → rounds away. Readout shows "Now: 1.3/s → 1.3/s" — a real upgrade looks like a no-op. | 🟡 Cosmetic | Low — confusing but not gameplay-breaking. | **Yes** — display-precision oversight in RO-11 #C format string. |

**Recommendation: fix all four before promoting v5 → closed.** Bugs 1 + 2 are gameplay-correctness regressions that would surface as "research stops working after I buy an upgrade" tester feedback within the first round. Bugs 3 + 4 are trivial deltas while we are already touching this code.

---

## 2. Discovery + evidence

### Screenshot (Wave 4, DEFENSE tab open)

```
Top HP bar:        1568 / 1568  HP
HEALTH row:        Lv 2 · +3% max ziggurat health · $63
                   Now: 1647 HP → 1694 HP
HEALTH REGEN row:  Lv 0 · +2% health regen per second · $60
                   Now: 1.3/s → 1.3/s
```

`1568 / 1.06` ≈ `1479` → matches `ZigguratBaseStats.BASE_HEALTH × workshop HEALTH Lv 2 (+6 %)`.
`1647 / 1.06 / 1.05` ≈ `1479` → matches `BASE × workshop × lab HEALTH_RESEARCH Lv 1 (+5 %)`.

The readout includes lab research; the live ziggurat does not. They should match — the player has already purchased at least one in-round upgrade prior to the screenshot, and that purchase stripped the lab bonus from the engine.

### Code search

`BattleViewModel.kt:496`:

```kotlin
fun purchaseInRoundUpgrade(type: UpgradeType) {
    // ...
    inRoundLevels[type] = currentLevel + 1
    resolvedStats = resolveStats(workshopLevels, inRoundLevels)   // ← labLevels missing
    eng.updateZigguratStats(resolvedStats)                        // ← also no applyCardEffects
    eng.updateEffectiveLevels(combinedLevelsForCash())
    // ...
}
```

Compare with `init` and `playAgain`, both of which call `resolveStats(workshopLevels, emptyMap(), labLevels)` followed by `applyCardEffects(stats, equippedCards)`.

`grep` confirms exactly one call site is missing `labLevels`:

```text
$ grep -rn "resolveStats(workshopLevels, inRoundLevels)" app/src/main/
app/src/main/java/.../presentation/battle/BattleViewModel.kt:496
```

`DescribeUpgradeEffect.kt::format` has no path that consults `equippedCards` — the `format` function takes only `workshopLevels`, `inRoundLevels`, `labLevels`, and `type`. The use case's KDoc explicitly claims "Sharing the [DescribeUpgradeEffect] instance with [resolveStats] guarantees the readout cannot drift from the actual post-purchase stats" — which is true for lab research but false for cards.

### Why this slipped through earlier audits

- `BattleViewModelTest::init applies card effects` and `RO11 init reads WAVE_SKIP and exposes startWave` validate the round-start path but not the post-purchase resolution path. There was no regression test covering "in-round purchase preserves lab + card stats."
- `DescribeUpgradeEffectTest` covers all 25 readout permutations across upgrade types but never instantiates a card-equipped scenario; the test fixture passes `emptyMap()` everywhere relevant and an empty `equippedCards` is implied because the use case's API never exposed the parameter.
- The screenshot only became possible after v5 hit a real device with a real Lab + Workshop + in-round level combination. No JVM test was set up to exercise the post-purchase readout vs post-purchase engine state in lockstep.

---

## 3. Decision matrix

| Decision | Rationale |
|---|---|
| **Fix all 4 in a single PR** | Bugs 1 and 2 share the same call site. Bug 3's fix benefits from the same `ApplyCardEffects` instance threaded through. Bug 4 is a one-character format-string change. Bundling minimises versionCode bumps before the closed-track promotion. |
| **Extract a private `resolveCurrentStats(inRound: Map<UpgradeType, Int>)` helper on `BattleViewModel`** | Three call sites (init, playAgain, purchaseInRoundUpgrade) need the same `resolveStats → applyCardEffects` pipeline. Extracting it eliminates the drift-by-omission failure mode permanently rather than fixing one site and leaving the duplication. |
| **Thread `equippedCards` into `DescribeUpgradeEffect` as an optional parameter (default `emptyList()`)** | Existing test call sites and any future call sites that want pre-card stats keep their current behaviour. The Battle screen consumer passes the live `equippedCards` snapshot so the readout stays in lockstep with the engine. |
| **Bump `HEALTH_REGEN` readout format to `%.2f/s`** | The smallest meaningful upgrade (+2 % at base 1.3/s = +0.026/s) needs 2 decimal places to be visible. Other regen-like stats (`%.1f` for `KNOCKBACK`, `DAMAGE_PER_METER`, `THORN_DAMAGE`, `LIFESTEAL`) have larger per-level magnitudes and stay at 1 decimal. |
| **No schema / DI / public-API changes** | Bugs are localised to two files (`BattleViewModel.kt`, `DescribeUpgradeEffect.kt`) plus tests. No migration, no new repository, no Hilt module change. |

---

## 4. Fix sketches

### Bug 1 + 2 — `BattleViewModel.purchaseInRoundUpgrade`

```kotlin
// New private helper, used by init / playAgain / purchaseInRoundUpgrade.
private fun resolveCurrentStats(inRound: Map<UpgradeType, Int>): ResolvedStats {
    val raw = resolveStats(workshopLevels, inRound, labLevels)
    return applyCardEffects(raw, equippedCards).stats
}

fun purchaseInRoundUpgrade(type: UpgradeType) {
    // ... existing affordability / freeChance / cost path unchanged ...
    inRoundLevels[type] = currentLevel + 1
    resolvedStats = resolveCurrentStats(inRoundLevels)   // ← lab + cards preserved
    eng.updateZigguratStats(resolvedStats)
    eng.updateEffectiveLevels(combinedLevelsForCash())
    // ... rest unchanged ...
}
```

`init` and `playAgain` migrate to the same helper for symmetry. The non-stat card-effect side outputs (`cardCashBonus`, `cardSecondWind`, `cardGemMultiplier`) are computed in init / playAgain only — they are static for the round and don't change with in-round purchases — so they don't need to move into the helper.

### Bug 3 — `DescribeUpgradeEffect`

```kotlin
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
        // ... existing isAtMax / nextInRound logic ...
        val currentReadout = format(workshopLevels, inRoundLevels, labLevels, equippedCards, type)
        val nextReadout = if (isAtMax) null else format(workshopLevels, nextInRound, labLevels, equippedCards, type)
        return UpgradeEffectReadout(currentReadout, nextReadout)
    }

    private fun format(
        workshopLevels: Map<UpgradeType, Int>,
        inRoundLevels: Map<UpgradeType, Int>,
        labLevels: Map<ResearchType, Int>,
        equippedCards: List<OwnedCard>,
        type: UpgradeType,
    ): String {
        val raw = resolveStats(workshopLevels, inRoundLevels, labLevels)
        val stats = applyCardEffects(raw, equippedCards).stats   // ← match live engine
        // ... existing when(type) branches read `stats` unchanged ...
    }
}
```

`BattleViewModel.describeEffect(type)` then passes `equippedCards` as the new arg.

### Bug 4 — `HEALTH_REGEN` precision

```kotlin
UpgradeType.HEALTH_REGEN -> fmt("%.2f/s", stats.healthRegen)
```

One character (`%.1f` → `%.2f`). Existing `DescribeUpgradeEffectTest::HEALTH_REGEN uses per-second suffix and 1-decimal format` updates from `"1.2/s"` to `"1.20/s"`; assertion message and test name updated.

---

## 5. Test plan

| New test | File | What it guards against |
|---|---|---|
| `RO12 in-round purchase preserves lab research bonus` | `BattleViewModelTest` | Set lab `HEALTH_RESEARCH` to L4 (+20 %), workshop `HEALTH` to L0; verify `vm.resolvedStats.maxHealth ≈ BASE × 1.20` after `purchaseInRoundUpgrade(HEALTH)` (a 2nd in-round level later, the multiplicative chain is still preserved). Direct regression for the missing `labLevels` arg. |
| `RO12 in-round purchase preserves card effects` | `BattleViewModelTest` | Equip `WALKING_FORTRESS` Lv 1 (+10 % maxHealth); take a snapshot of `vm.resolvedStats.maxHealth` post-init; call `purchaseInRoundUpgrade(HEALTH)`; verify the new max HP is the post-card value scaled by the in-round HEALTH bump, not the pre-card value. |
| `RO12 describeEffect HEALTH respects equipped WALKING_FORTRESS` | `DescribeUpgradeEffectTest` | Pass an equipped WALKING_FORTRESS via the new optional arg; verify readout reflects post-card max HP (ResolvedStats × WF multiplier), not raw `ResolveStats` output. |
| `HEALTH_REGEN uses 2-decimal format` | `DescribeUpgradeEffectTest` (rename of existing test) | New expected value `"1.20/s"` (post-fix) vs old `"1.2/s"`. The test itself replaces the existing `HEALTH_REGEN uses per-second suffix and 1-decimal format` test. |

Test-count delta target: **+3 net** (3 new + 1 modified). 609 → 612.

`BattleViewModelTest` continues to use `runInTransaction = { block -> block() }` override to bypass `Room.withTransaction` against the mock `AppDatabase`. The new tests don't end the round, so the persistence path is not exercised.

---

## 6. Acceptance criteria — ready for closed test

- [ ] Bug 1: `purchaseInRoundUpgrade` calls a stat-resolution path that includes `labLevels`.
- [ ] Bug 2: `purchaseInRoundUpgrade` re-applies `ApplyCardEffects` (or shares a helper that does).
- [ ] Bug 3: `DescribeUpgradeEffect` accepts `equippedCards` and applies card effects.
- [ ] Bug 4: `HEALTH_REGEN` readout shows 2 decimals so Lv 0 → Lv 1 produces a visibly different number.
- [ ] All 3 new regression tests pass.
- [ ] `./run-gradle.sh testDebugUnitTest` BUILD SUCCESSFUL — JVM test count moves 609 → 612.
- [ ] `./run-gradle.sh bundleRelease` BUILD SUCCESSFUL.
- [ ] Doc sweep per `.kiro/steering/11-agent-protocol.md` PR Task-List Convention: AGENTS.md test count, source-files.md updates, CHANGELOG.md entry under `[Unreleased]` named "RO-12 — In-round stat drift fixes".
- [ ] STATE.md current-objective updated.
- [ ] RUN_LOG.md entry appended.
- [ ] versionCode bump (5 → 6) **deferred to the upload PR**, so this PR is reviewable in isolation.

---

## 7. Risk register

| Risk | Mitigation |
|---|---|
| Helper extraction in `BattleViewModel` accidentally drops one of init / playAgain's side outputs (`cardCashBonus`, `cardSecondWind`, `cardGemMultiplier`). | Only the `stats` field of `ApplyCardEffects.invoke` result moves into the helper. Existing init / playAgain code that reads `cardResult.cashBonusPercent` etc. stays inline, recomputed alongside the helper call. Verified via `RO08 STEP_SURGE level 1 doubles the watchGemAd reward` keeping its current pass. |
| Adding `equippedCards` arg to `DescribeUpgradeEffect.invoke` changes the use case's signature in a way an external caller breaks. | The use case is consumed by exactly one site (`BattleViewModel.describeEffect`) and 26 test entries. The new arg is optional with `emptyList()` default; existing call sites compile unchanged. Verified via grep: `grep -rn "DescribeUpgradeEffect(" app/`. |
| Bumping `HEALTH_REGEN` to 2 decimals could push the readout past the available column width on small screens. | The readout already uses `%.2f` for `ATTACK_SPEED` ("1.15/s") and `%.2f` for `CRITICAL_FACTOR` ("×2.50") so the column tolerates 2 decimals. `1.30/s` is one character wider than `1.3/s` — well within budget. |
| `BattleViewModelTest` regression test for card effects requires equipping a card before init runs, which races with the VM's launch. | Existing test `init applies card effects` already does this via `cardRepo.cards.value = listOf(...)` before `createVm()`. The new tests follow the same pattern and `advanceUntilIdle()` to drain the init launch. |

---

## 8. On-device verification (post-merge)

Once v6 (versionCode 6) is uploaded to the internal track, repeat the v5 smoke test plus these RO-12-specific checks:

1. With `HEALTH_RESEARCH` Lv ≥ 1, start a round. Top HP bar and HEALTH "Now" readout match.
2. Buy any in-round upgrade. Top HP bar still matches HEALTH "Now" readout — neither drops.
3. With `WALKING_FORTRESS` equipped, top HP bar and HEALTH "Now" readout match (both include the +10 %).
4. HEALTH_REGEN row shows "Now: X.XX/s → Y.YY/s" with two decimals; Lv 0 → Lv 1 visibly different.
5. Re-run RO-11 acceptance check #1: at DAMAGE_RESEARCH L5 + at least one in-round DAMAGE upgrade purchase, the projectile damage is still ≥ +25 % vs L0 control.

If all five pass, the underlying stat-drift root cause is closed and v6 can be promoted to the closed track.

---

## 9. Out of scope (deferred)

- Per-format-string audit across all 25 `DescribeUpgradeEffect` upgrade types (Bug 4 is the only visibly-broken one; the others all have larger per-level deltas). Track as v1.x polish.
- Localization of readout format strings (already deferred per RO-11 § 9 open question #5).
- Deferred RO-09 findings #3–#6 (cross-validator unit fix, lifetime-counter desync, TOCTOU spend race, per-kill credit on `viewModelScope`) remain in the v1.x patch backlog.
- RO-11 deferred items: AUTO_UPGRADE_AI + ENEMY_INTEL real implementations remain v1.x.
