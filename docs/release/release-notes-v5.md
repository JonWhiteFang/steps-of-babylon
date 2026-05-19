# Release Notes — v1.0.0 (versionCode 5)

**Build:** AAB at `app/build/outputs/bundle/release/app-release.aab`
**Tracks:** internal → closed (after smoke test PASS)
**Date built:** 2026-05-19
**Test count:** 609 JVM tests (BUILD SUCCESSFUL with `--rerun-tasks`)

---

## What's new — Play Console "What's new in this version" (≤500 chars)

Use this verbatim in the Play Console release notes box (English (United States) — `en-US`):

```
Lab research now boosts your tower! 8 of 10 research types power damage, health, cash, crits, regen, walking efficiency, weapon cooldowns, and the new Wave Skip — start rounds at higher waves. The in-round upgrade menu now shows the exact effect of each purchase (Now → Next). Auto-Upgrade AI and Enemy Intel are marked Coming Soon while we polish them. Plus regression fixes from v4: faster Chrono Field, cleaner Golden Ziggurat × Overdrive cash buffs.
```

Character count: 453 / 500 ✓

If Play Console rejects for length, fall back to the shorter version:

```
Lab research now powers your tower across 8 of 10 types — damage, health, cash, crits, regen, walking efficiency, UW cooldowns, plus Wave Skip (start at higher waves). In-round upgrades show exact Now → Next effect per purchase. Auto-Upgrade AI and Enemy Intel coming in a future update.
```

Character count: 287 / 500 ✓

---

## Tester recruitment notes (closed track opt-in email)

Use this when sending the opt-in URL to the ≥12 closed-track testers. Plain prose; no technical jargon.

> **Steps of Babylon — closed test, build v1.0.0 (5)**
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
> The in-round upgrade menu now shows a per-row "Now → Next" readout in gold below each upgrade's description, so you can see the exact damage / HP / range etc. each cash purchase will produce before you commit. Lab research bonuses flow through the readout too — a tester with Damage Research level 5 will see the boosted preview values.
>
> **What we're particularly looking for:**
>
> - Does each lab research type produce the bonus you'd expect after a few rounds at level 3+?
> - Does Wave Skip open the round on the correct wave (the HUD slide-in shows the wave number)?
> - Does the "Now → Next" readout match what actually changes when you buy an in-round upgrade?
> - Any walking-credit oddities (Step Efficiency bonus too high / too low, daily 50k cap not hitting cleanly)?
> - General feel — is the game more or less fun than v4? Does the readout add or subtract from the in-round flow?
>
> Plus all the previous v4 fixes: Chrono Field UW now slows enemies for real (was render-only), and the Golden Ziggurat × Overdrive cash multiplier no longer leaks across overdrive expirations.
>
> Thanks for testing! Please send any bug reports or feedback to <feedback email>.

---

## Internal release commit ladder (for the change-log row)

```
734beaa  chore(release): bump versionCode 4 -> 5 for RO-11 internal-track upload
4bcb71c  docs(ro-11): sync state, run log, AGENTS, source-files, CHANGELOG, plan
93f6ae8  feat(battle): in-round upgrade-effect readout per row (RO-11 #C / RO-10)
6b754c9  chore(labs): mark AUTO_UPGRADE_AI + ENEMY_INTEL as Coming Soon (RO-11 #B.2)
28337e5  feat(battle): wire WAVE_SKIP research to start rounds at higher wave (RO-11 #B.1)
14b0665  feat(steps): wire STEP_EFFICIENCY lab research into walking credit (RO-11 #A.3)
a4eca72  feat(battle): wire CASH_RESEARCH + UW_COOLDOWN engine multipliers (RO-11 #A.2)
d3dc4d6  feat(stats): wire DAMAGE/HEALTH/CRITICAL/REGEN_RESEARCH into ResolveStats (RO-11 #A.1)
```

### What's in the build

- **Phase A (closed-test blocker):** 7 simple Labs research multipliers wired into combat path / engine / step-credit. DAMAGE / HEALTH / CRITICAL / REGEN attach as a third multiplicative tier in `ResolveStats.invoke` (signature gains optional `labLevels: Map<ResearchType, Int>`); CASH and UW_COOLDOWN attach to `GameEngine` via two new `@Volatile` multiplier fields; STEP_EFFICIENCY combines additively with workshop STEP_MULTIPLIER under the existing shared +100% cap.
- **Phase B:** WAVE_SKIP wired through `WaveSpawner.startWave` constructor parameter, threaded all the way through `GameEngine.init` → `GameSurfaceView.configure` → `BattleViewModel.startWave` → `BattleScreen`. AUTO_UPGRADE_AI + ENEMY_INTEL gated as Coming Soon (`ResearchType.isComingSoon = true`, Labs UI badge, VM-level defensive guard); descriptions updated to "Reserved for v1.x — research progress preserved".
- **Phase C:** New `DescribeUpgradeEffect` use case + per-row "Now → Next" readout in `InRoundUpgradeMenu`. Use case shares the `ResolveStats` instance with `BattleViewModel` so the preview cannot drift from actual post-purchase stats. Format strings pinned to `Locale.ROOT` for deterministic English-only v1.0 output.

### Verification

- `./run-gradle.sh test --rerun-tasks` → 609 tests pass, 0 failures (was 572 pre-RO-11).
- `./run-gradle.sh bundleRelease` → BUILD SUCCESSFUL with clean R8 minify + lint vital + signing.

### Known v1.x deferrals (documented + tracked)

- `AUTO_UPGRADE_AI` real implementation — auto-purchase coroutine + optimal-upgrade definition (~2 days).
- `ENEMY_INTEL` real implementation — HP-bar gating + wave preview UI + boss telegraph banner.
- Cross-validator unit fix for combined STEP_MULTIPLIER + STEP_EFFICIENCY against `hcSteps` (RO-09 deferred #3).
- Workshop-screen surface of the same Now → Next readout (use case already supports it via the hidden-but-tested-for-reuse paths).
- `BattleViewModel` constructor refactor — now at 16 params, ADR + extraction candidate for v1.x.
- Live-price retry on transient network failure (Plan 31 PR B intentional v1 simplification).

---

## Acceptance smoke checks (run on device after upload)

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

**Plus regression checks (RO-08 + RO-09):**

- In-round ATTACK_SPEED purchase visibly speeds up the tower (RO-08).
- STEP_MULTIPLIER L5 produces ~ +5 % steps when walking; STEP_MULTIPLIER L5 + STEP_EFFICIENCY L5 stack to ~ +15 % combined (RO-08 + RO-11 #A.3).
- RECOVERY_PACKAGES heals during a wave at level ≥ 1 (RO-08).
- CHRONO_FIELD UW slows enemies during the 8 s window (RO-09 #1).
- Activate ASSAULT then GOLDEN_ZIGGURAT, let GOLDEN expire while ASSAULT still active → cash multiplier resets to 1.0 ×, NOT 5.0 × (RO-09 #2).

If any check fails, file a follow-up plan + fix before promoting internal v5 → closed track.
