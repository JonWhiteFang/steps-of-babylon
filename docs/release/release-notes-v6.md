# Release Notes — v1.0.0 (versionCode 6)

> **Historical — internal build.** This documents an *internal-track build iteration* (versionCode 6)
> during v1.0.0 closed-test prep on 2026-05-19, not a production semver release. The live production
> versions are v1.0.1 (vc17) / v1.0.2 (vc18) — see `release-notes-v1.0.2.md`.

**Build:** AAB at `app/build/outputs/bundle/release/app-release.aab` (~18.5 MB)
**Tracks:** internal → closed (after smoke test PASS)
**Date built:** 2026-05-19
**Test count:** 615 JVM tests (BUILD SUCCESSFUL, 0 failures)
**Supersedes:** v5 (versionCode 5, uploaded internal 2026-05-19 morning, never promoted to closed)

---

## What's new — Play Console "What's new in this version" (≤500 chars)

Use this verbatim in the Play Console release notes box (English (United States) — `en-US`):

```
Lab research now powers your tower across 8 of 10 types — damage, health, cash, crits, regen, walking efficiency, UW cooldowns, plus Wave Skip (start at higher waves). The in-round upgrade menu shows the exact effect of each purchase (Now → Next), now in lockstep with research and equipped cards. Auto-Upgrade AI and Enemy Intel are marked Coming Soon while we polish them. Plus regression fixes: faster Chrono Field, cleaner Golden Ziggurat × Overdrive cash buffs.
```

Character count: 470 / 500 ✓

If Play Console rejects for length, fall back to the shorter version:

```
Lab research now powers your tower across 8 of 10 types — damage, health, cash, crits, regen, walking efficiency, UW cooldowns, plus Wave Skip (start at higher waves). In-round upgrades show exact Now → Next effect per purchase. Auto-Upgrade AI and Enemy Intel coming in a future update.
```

Character count: 287 / 500 ✓

---

## Tester recruitment notes (closed track opt-in email)

Use this when sending the opt-in URL to the ≥12 closed-track testers. Plain prose; no technical jargon. (Same framing as v5 — testers will not see v5/v6 as separate; they're getting the cumulative build.)

> **Steps of Babylon — closed test, build v1.0.0 (6)**
>
> This build wires up the full Labs research system, which previously displayed numbers but didn't actually affect gameplay. Eight of the ten research types now power your tower:
>
> - **Damage / Health / Critical / Regen Research** — boost the matching tower stat
> - **Cash Research** — more cash from kills and wave completions
> - **Step Efficiency** — bonus credited steps when walking (stacks with Workshop's Step Multiplier)
> - **UW Cooldown** — your Ultimate Weapons recharge faster
> - **Wave Skip** — your rounds open at a higher wave (max +10)
>
> The remaining two (**Auto-Upgrade AI** and **Enemy Intel**) display a "COMING SOON" badge while we finish their UI work — your research progress on those will be preserved when they ship in a later update.
>
> The in-round upgrade menu now shows a per-row "Now → Next" readout in gold below each upgrade's description, so you can see the exact damage / HP / range etc. each cash purchase will produce before you commit. Lab research bonuses AND any equipped Cards (Walking Fortress, Glass Cannon, etc.) flow through both the readout and the live tower stats — what you preview is what you get.
>
> **What we're particularly looking for:**
>
> - Does each lab research type produce the bonus you'd expect after a few rounds at level 3+?
> - Does Wave Skip open the round on the correct wave (the HUD slide-in shows the wave number)?
> - Does the "Now → Next" readout match what actually changes when you buy an in-round upgrade?
> - With Walking Fortress or Glass Cannon equipped — do your tower stats stay correct after multiple in-round upgrades?
> - Any walking-credit oddities (Step Efficiency bonus too high / too low, daily 50k cap not hitting cleanly)?
> - General feel — is the game more or less fun than the previous build? Does the readout add or subtract from the in-round flow?
>
> Plus all the previous fixes: Chrono Field UW now slows enemies for real (was render-only), and the Golden Ziggurat × Overdrive cash multiplier no longer leaks across overdrive expirations.
>
> Thanks for testing! Please send any bug reports or feedback to <feedback email>.

---

## Internal release commit ladder (for the change-log row)

```
<vbump>  chore(release): bump versionCode 5 -> 6 for RO-12 internal-track upload
84ed394  fix(battle): preserve lab research + card stats across in-round upgrade purchase (RO-12)
734beaa  chore(release): bump versionCode 4 -> 5 for RO-11 internal-track upload  (v5)
4bcb71c  docs(ro-11): sync state, run log, AGENTS, source-files, CHANGELOG, plan
93f6ae8  feat(battle): in-round upgrade-effect readout per row (RO-11 #C / RO-10)
6b754c9  chore(labs): mark AUTO_UPGRADE_AI + ENEMY_INTEL as Coming Soon (RO-11 #B.2)
28337e5  feat(battle): wire WAVE_SKIP research to start rounds at higher wave (RO-11 #B.1)
14b0665  feat(steps): wire STEP_EFFICIENCY lab research into walking credit (RO-11 #A.3)
a4eca72  feat(battle): wire CASH_RESEARCH + UW_COOLDOWN engine multipliers (RO-11 #A.2)
d3dc4d6  feat(stats): wire DAMAGE/HEALTH/CRITICAL/REGEN_RESEARCH into ResolveStats (RO-11 #A.1)
```

### What changed vs v5

**v5 was uploaded to internal track 2026-05-19 morning and superseded by v6 the same day** before any closed-track promotion. The v5 on-device smoke test surfaced a bug bundle (RO-12) that would have surfaced as "research stops working after my first in-round upgrade" tester feedback. Fixed in commit `84ed394` before promoting:

- **`BattleViewModel.purchaseInRoundUpgrade`** now routes through a new `resolveCurrentStats(inRound)` private helper that runs the full live-engine pipeline (`resolveStats(workshop, inRound, lab) → applyCardEffects(stats, equippedCards).stats`). Pre-fix, this site called `resolveStats(workshopLevels, inRoundLevels)` directly — silently dropping `labLevels` (RO-11 wiring miss) AND skipping the post-resolve `applyCardEffects` step (pre-existing since cards landed). Effect on the player: every in-round upgrade purchase silently stripped lab research multipliers AND every stat-modifying card's effect (WALKING_FORTRESS, GLASS_CANNON, IRON_SKIN, SHARP_SHOOTER, VAMPIRIC_TOUCH, CHAIN_REACTION) for the rest of the round. RO-11 acceptance check #1 ("DAMAGE_RESEARCH L5 visibly hits harder") would have failed mid-round.
- **`DescribeUpgradeEffect`** now accepts an optional `equippedCards` argument and post-applies card effects before formatting the readout. Without this, a tester with WALKING_FORTRESS equipped would see a HEALTH "Now" value that disagreed with the live ziggurat HP bar by 50 % — exactly the kind of trust-breaking visual drift the use case was designed to prevent.
- **`HEALTH_REGEN` readout** format `%.1f/s` → `%.2f/s`. At base ~1.3/s and +2 % per level, the per-level delta is ~0.026/s, which rounds away under 1-decimal display. Pre-fix the readout showed "Now: 1.3/s → 1.3/s" for a real Lv 0 → Lv 1 upgrade — looked broken even though the math was correct.

### What's in the cumulative build (v5 + v6)

- **Phase A (RO-11, closed-test blocker):** 7 simple Labs research multipliers wired into combat path / engine / step-credit. DAMAGE / HEALTH / CRITICAL / REGEN attach as a third multiplicative tier in `ResolveStats.invoke`; CASH and UW_COOLDOWN attach to `GameEngine` via two new `@Volatile` multiplier fields; STEP_EFFICIENCY combines additively with workshop STEP_MULTIPLIER under the existing shared +100 % cap.
- **Phase B (RO-11):** WAVE_SKIP wired through `WaveSpawner.startWave` constructor parameter, threaded through `GameEngine.init` → `GameSurfaceView.configure` → `BattleViewModel.startWave` → `BattleScreen`. AUTO_UPGRADE_AI + ENEMY_INTEL gated as Coming Soon.
- **Phase C (RO-11):** New `DescribeUpgradeEffect` use case + per-row "Now → Next" readout in `InRoundUpgradeMenu`. Format strings pinned to `Locale.ROOT` for deterministic English-only v1.0 output.
- **RO-12 (new in v6):** `BattleViewModel.resolveCurrentStats(inRound)` helper preserves lab + card stats across in-round purchases; `DescribeUpgradeEffect` threads `equippedCards` through to mirror the live engine pipeline; `HEALTH_REGEN` readout bumped to 2-decimal precision.

### Verification

- `./run-gradle.sh testDebugUnitTest` → **615 tests pass, 0 failures** (was 572 pre-RO-11, 609 post-RO-11, 615 post-RO-12).
- `./run-gradle.sh bundleRelease` → BUILD SUCCESSFUL in 1m 12s with clean R8 minify + lint vital + signing. AAB 19,403,732 bytes (~18.5 MB).
- `jarsigner -verify` → **jar verified** (signed with the production upload keystore enrolled in Play App Signing).
- `output-metadata.json` confirms `versionCode = 6`, `versionName = "1.0.0"`, `applicationId = com.whitefang.stepsofbabylon`.

### Known v1.x deferrals (documented + tracked)

- `AUTO_UPGRADE_AI` real implementation — auto-purchase coroutine + optimal-upgrade definition (~2 days).
- `ENEMY_INTEL` real implementation — HP-bar gating + wave preview UI + boss telegraph banner.
- Cross-validator unit fix for combined STEP_MULTIPLIER + STEP_EFFICIENCY against `hcSteps` (RO-09 deferred #3).
- Workshop-screen surface of the same Now → Next readout (use case already supports it via the hidden-but-tested-for-reuse paths).
- `BattleViewModel` constructor refactor — now at 16 params, ADR + extraction candidate for v1.x.
- Live-price retry on transient network failure (Plan 31 PR B intentional v1 simplification).
- Per-format-string audit across remaining 25 readout types (RO-12 only fixed HEALTH_REGEN, the visibly-broken one; others have larger per-level magnitudes and don't need 2-decimal display today).

---

## Acceptance smoke checks (run on device after upload)

### RO-11 checks (carried over from v5, still required)

Per `docs/plans/plan-RO-11-labs-wiring.md` § 8:

1. Research **DAMAGE_RESEARCH** to L5 → start a round → tower visibly hits harder than a control round at L0.
2. Research **HEALTH_RESEARCH** to L5 → start a round → max-HP bar reads ≥ 25 % higher.
3. Research **CASH_RESEARCH** to L5 → complete a wave → cash earned per kill is ≥ 25 % above control.
4. Research **CRITICAL_RESEARCH** to L10 + Workshop CRITICAL_CHANCE high → crit damage ≥ 30 % above control.
5. Research **STEP_EFFICIENCY** to L5 → walk 100 sensor steps → daily-step record shows ≥ 110 credited.
6. Research **UW_COOLDOWN** to L10 → activate any UW → cooldown ring-fill takes ~ 70 % of baseline.
7. Research **WAVE_SKIP** to L5 → start a round → HUD shows "Wave 6" not "Wave 1".
8. Open the in-round upgrade menu → every visible upgrade row shows a "Now: X → Next: Y" line below its description.
9. AUTO_UPGRADE_AI + ENEMY_INTEL rows on the Labs screen show a "COMING SOON" badge and no Start button.

### RO-12 checks (new in v6 — directly verify the bug fixes)

Per `docs/plans/plan-RO-12-in-round-stat-drift.md` § 8:

10. With `HEALTH_RESEARCH` Lv ≥ 1 owned, start a round → top HP bar matches the HEALTH "Now" readout in the in-round upgrade menu (both include the lab bonus). **The v5 screenshot showed 1568 / 1647 — they should now read the same.**
11. Same as #10, then **buy any in-round upgrade** → top HP bar still matches the HEALTH "Now" readout (lab bonus survives the purchase).
12. With **WALKING_FORTRESS** equipped, start a round → top HP bar AND HEALTH "Now" readout both show the +50 % card bonus. Buy any in-round upgrade → both still show the card bonus.
13. Open the in-round upgrade menu, look at the **HEALTH_REGEN** row → readout shows two decimals (e.g. "1.00/s → 1.02/s"), not "1.3/s → 1.3/s".
14. Re-run RO-11 acceptance check #1 (DAMAGE_RESEARCH L5 visibly hits harder), but this time after buying ≥3 in-round DAMAGE purchases. Damage should still be ≥ +25 % vs an L0 control with the same in-round purchases.

### Plus regression checks (RO-08 + RO-09)

- In-round ATTACK_SPEED purchase visibly speeds up the tower (RO-08).
- STEP_MULTIPLIER L5 produces ~ +5 % steps when walking; STEP_MULTIPLIER L5 + STEP_EFFICIENCY L5 stack to ~ +15 % combined (RO-08 + RO-11 #A.3).
- RECOVERY_PACKAGES heals during a wave at level ≥ 1 (RO-08).
- CHRONO_FIELD UW slows enemies during the 8 s window (RO-09 #1).
- Activate ASSAULT then GOLDEN_ZIGGURAT, let GOLDEN expire while ASSAULT still active → cash multiplier resets to 1.0 ×, NOT 5.0 × (RO-09 #2).

If any check fails, file a follow-up plan + fix before promoting internal v6 → closed track.

---

## Upload checklist

- [ ] Bump `versionCode 5 → 6` in `app/build.gradle.kts` ✅ done
- [ ] `./run-gradle.sh bundleRelease` → BUILD SUCCESSFUL ✅ done
- [ ] `jarsigner -verify app/build/outputs/bundle/release/app-release.aab` → "jar verified" ✅ done
- [ ] Open Play Console → Steps of Babylon → Testing → Internal testing → Create new release
- [ ] Upload `app/build/outputs/bundle/release/app-release.aab`
- [ ] Paste the "What's new" 500-char text into the release notes box (`en-US`)
- [ ] Save → Review release → Start rollout to internal testing
- [ ] Wait ~5 min for Play Console processing
- [ ] Install v6 on a physical device via internal-testing opt-in URL
- [ ] Run all 14 acceptance checks above (9 RO-11 + 5 RO-12) plus the 5 RO-08/RO-09 regressions
- [ ] If green → promote internal v6 → closed track + recruit ≥12 testers
