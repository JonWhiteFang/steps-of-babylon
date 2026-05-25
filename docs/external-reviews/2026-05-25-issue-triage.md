# GitHub Issue Triage — 2026-05-25

Snapshot of all 33 open GitHub issues on `JonWhiteFang/steps-of-babylon` as of 2026-05-25T19:45 BST. Each issue was read against the current source tree (commit `2169cf9` on `main`, post-#18 fix), diagnosed, and a comment was posted on every open issue. This document summarises the findings for v1.x roadmap planning.

**Issues #19 (UWs not auto-triggering) and #20 (Rapid Fire not visible in Workshop) are excluded** — they are being fixed in branch `fix/19-20-uw-autotrigger-and-seeding`.

## Summary

| Status                         | Count | Issues |
|---|---|---|
| Closed-track blockers (excluded — fixing now) | 2     | #19, #20 |
| Verified-accurate detailed findings           | 16    | #32, #33, #34, #36, #37, #38, #39, #40, #41, #42, #43, #45, #46, #47, #48, #49, #50 |
| Already tracked debt (cross-ref to STATE.md)  | 4     | #35, #44, #45, #51 |
| Roadmap proposals (no specific bug)           | 11    | #21–31 |

`#45` appears in two rows because it is both an accurate finding and tracked in STATE.md.

## Verified-accurate detailed findings (16)

These issues describe specific, verifiable bugs or gaps. Each was confirmed against current source. Each has a diagnostic comment on GitHub with `file:line` references. Listed in suggested priority order for v1.x.

### Tier 1 — Closed-track polish (consider before production rollout)

#### #48 — No in-app data deletion UI
- **Status:** Confirmed. `NotificationSettingsScreen.kt` has no Delete Data action; `grep` for `delete|clear` in `presentation/` returns nothing.
- **Why polish-tier:** Play Store user data policy "best practice" for apps collecting health data. Currently compliant via documented external Settings → Storage path, but in-app option is the policy-review-safe choice.
- **Cost:** ~1 day. SQLCipher DB wipe + 6 SharedPreferences clears + Keystore key deletion + WorkManager cancel + Activity recreate.
- **Risk if deferred:** Possible Play Console policy flag during production review.

#### #47 — Season Pass UI lacks expiry display + cancel link + comparison
- **Status:** Partial UI exists; gaps confirmed in `StoreScreen.kt:93–115`.
- **Why polish-tier:** `seasonPassExpiry` is in `StoreUiState` but unsurfaced. No deep-link to Play subscription management. Missing free-vs-paid comparison.
- **Cost:** ~2 hours for expiry-date display + Play subscription deep-link button. ~1 day for full revamp.
- **Risk if deferred:** Possible Play policy issue (subscription cancel flow). Conversion-rate gap.

#### #33 — 9 navigation screens share `Icons.Default.Star`
- **Status:** Confirmed. `Screen.kt` lines 13–24.
- **Why polish-tier:** UX confusion; first-impression issue for closed-test feedback.
- **Cost:** ~30 min. `compose-material-icons-extended` is already on the dep list (added for R4-04).
- **Risk if deferred:** TalkBack accessibility issue (#33 also labelled `accessibility`).

### Tier 2 — Visible-but-not-blocking (v1.x patch backlog)

#### #38 — Sound assets are placeholder sine-wave tones
- **Status:** Tracked in STATE.md "Known issues". `app/src/main/res/raw/` confirmed to hold placeholders.
- **Cost:** ~1 day for content swap (Kenney.nl CC0). No code changes — keep existing resource IDs.
- **Note:** Pairs with #46 (SoundPool throttle). The throttle's "shoot sound feels quieter at high attack speeds" symptom needs both fixes to feel right.

#### #46 — SoundPool 100ms shoot throttle hides high-attack-speed feedback
- **Status:** Confirmed. `SoundManager.kt:32–37`. Hard 100 ms gate caps at 10 SHOOTs/sec audibly.
- **Combines badly with R4-03 RAPID_FIRE L10** (3.0× attack speed) and high MULTISHOT levels.
- **Cost:** ~1-2 hours for frequency-aware throttle (scale gate by `attackInterval`).
- **Risk if deferred:** Late-game weapon feedback feels hollow — directly contradicts the "reward for upgrading" feedback loop.

#### #39 — No background music system
- **Status:** Confirmed. `grep` for `MediaPlayer|ExoPlayer` returns nothing.
- **Cost:** ~2-3 days (MediaPlayer + audio-focus listener + 2 source tracks + Settings UI).
- **Risk if deferred:** Quality-of-life gap; battle screen is silent between SFX events.

#### #34 — All UI strings hardcoded; `strings.xml` has only one entry
- **Status:** Confirmed. Spot-checks across BattleScreen, NotificationSettingsScreen, WorkshopScreen.
- **Cost:** Very large — battle renderer alone has dozens of strings; engine-internal floating-text strings need a `Strings` interface in `domain/` to avoid breaking layer rules.
- **Risk if deferred:** Hard blocker for non-English markets. Lint's `HardcodedText` rule is suppressed or escaping; worth investigating.
- **Recommendation:** v1.x phased extraction starting with battle screen + workshop labels + notification text.

#### #45 — 7 cosmetics show "Coming Soon" in Store
- **Status:** Tracked in STATE.md. Pipeline ready for ZIGGURAT_SKIN; 4 of 7 still need palettes (3 zigs) or new render-side override consumers (2 projectiles + 2 enemies).
- **Cost asymmetric:** ~30 min for one ziggurat skin (content edit only); ~1 day each for projectile or enemy categories (new override consumers).
- **Recommendation:** Ship one ziggurat skin (`zig_obsidian` as dark-stone palette) for v1.0; defer projectile/enemy categories.

### Tier 3 — Architecture / testing investments (v1.x major refactor)

#### #32 — No `androidTest/` directory; no instrumented tests
- **Status:** Confirmed. Glob `androidTest/**` returns zero files.
- **Cost:** ~1 week to set up `androidTest/` infrastructure; ~2 weeks of follow-on coverage iteration.
- **Highest-value coverage:** Battle screen surface lifecycle + state restoration (would catch R3-01 and #19), Store IAP flow, deep-link routing.
- **Tooling on dep list already:** Robolectric 4.14.1, AndroidX Test Core 1.6.1, Compose UI Test (transitive via BOM).
- **Recommendation:** Second-most-impactful post-launch item. Pairs with #21 (proposal), #25 (replay testing), #37 (extract simulation core).

#### #42 — Repository implementations have almost no unit tests
- **Status:** Confirmed. Only `CosmeticRepositoryImplTest` exists; 7 other repo impls have zero direct tests.
- **Cost:** ~30-40 new tests for the 4 high-priority impls (Workshop / Player / UltimateWeapon / Card).
- **Bug-coverage analysis:** Issue #20's seeding bug (and the matching Lab seeding bug) would have been caught by direct repo tests with pre-seeded historical rows. Issue #18 (card pack persistence) likewise.
- **Recommendation:** Highest-leverage testing work for v1.x. The fix for #20 in this PR establishes the pattern.

#### #37 — `GameEngine` lives in `presentation/` violating Clean Architecture
- **Status:** Confirmed. `GameEngine.kt` imports `android.graphics.Canvas`. Engine is ~700 lines of simulation + entity-system + auto-trigger + cosmetic-resolution logic that's logically domain code.
- **Cost (Option C — partial extraction):** ~3-4 days. Move simulation tick + entity collection to `domain/battle/engine/Simulation.kt`; keep `GameEngine` as the rendering host.
- **Recommendation:** Pairs with #32 (instrumented tests) — gives tests a pure-Kotlin simulation seam to drive.

### Tier 4 — Content / design gaps (v1.x design work)

#### #43 — `CELESTIAL_GATE` biome (Tier 11+) is unreachable
- **Status:** Confirmed. `TierConfig.kt` caps at Tier 10; `Biome.CELESTIAL_GATE` is `11..Int.MAX_VALUE`.
- **Recommendation:** Mark explicitly as v1.x via an `isComingSoon` flag (analogous to `ResearchType.isComingSoon`). Cheapest, honest, and reaches design-decision parity with the existing precedent.

#### #41 — Weekly Challenges have no dedicated screen
- **Status:** Data layer fully implemented; no screen. Currency Dashboard surfaces summary form.
- **Cost (lightweight v1.0):** ~1 day to expand existing `Weekly` section in CurrencyDashboardScreen with target / progress / time-remaining / history.
- **Cost (heavyweight v1.x):** 3-5 days for dedicated screen + nav route + future challenge variants.
- **Recommendation:** Lightweight expansion if closed-track feedback demands; otherwise defer entirely.

#### #36 — No cloud save / data backup path
- **Status:** Confirmed. `allowBackup="false"` correctly set; no Snapshots API integration.
- **Recommendation:** Settings warning at minimum for v1.0 (~30 min); Play Games Services Snapshots API for v1.1 (~3-5 days).
- **Trust risk:** Real for a fitness game where progression = real walking effort.

#### #50 — No social sharing for personal bests / biome unlocks
- **Status:** Confirmed. `grep` for `ShareCompat|ACTION_SEND` returns nothing.
- **Cost:** ~1 hour for text-share at PostRoundOverlay (`ShareCompat.IntentBuilder`); ~2-3 days for image-share (offscreen Canvas + FileProvider).
- **Recommendation:** Text-share for closed-track polish; image-share v1.x.

#### #49 — STEP_MULTIPLIER hard-caps at 100% — worthless at max
- **Status:** Confirmed. `UpgradeType.kt` config: `cap = 100, scaling = 1.35`. Lv 100 cost ≈ 10^14 Steps (effectively impossible).
- **Cost (Option 1 — asymptotic):** ~1 day, formula change only.
- **Cross-cutting concern:** Interacts with anti-cheat — RO-09 deferred finding #3 (STEP_MULTIPLIER × CV unit mismatch) should be designed alongside any cap change.
- **Recommendation:** Coordinate with the v1.x anti-cheat patch.

#### #40 — GPS / Exploration Mode in GDD but not implemented
- **Status:** Confirmed. No `LocationManager` / `FusedLocationProviderClient` anywhere.
- **Recommendation:** **Either** remove from GDD §2.3 for v1.0 (cheapest, keeps the design doc honest), **or** ship as opt-in v1.x feature with privacy + battery work + Play Store data-safety re-review (~1-2 weeks).

## Already tracked debt (4)

These are referenced in STATE.md or known design decisions. The diagnostic comments confirm the state and add cross-references.

#### #35 — TOCTOU race on Gem/Power Stone spend
- Tracked as RO-09 deferred finding #5. Wallet stays correct; lifetime counter can drift positive under contention.
- **Fix shape proven:** `creditBattleStepsAtomic` / `creditBossPowerStonesAtomic` pattern. Apply `spendGemsAtomic` / `spendPowerStonesAtomic` similarly.
- **Severity:** Cosmetic (Stats screen "lifetime spent" can over-count).
- **Recommendation:** v1.x patch.

#### #51 — Per-kill battle-step credit on `viewModelScope`
- Tracked as RO-09 deferred finding #6. ≤1 step lost per pending callback on mid-round nav-away.
- **Fix shape proven:** Same as B.3 PR 2's `onCleared` migration to `@ApplicationScope CoroutineScope`. Single-line change once the scope is in scope (already injected in BattleViewModel).
- **Severity:** Cosmetic (lifetime-step display drift only).
- **Recommendation:** v1.x patch.

#### #44 — `AUTO_UPGRADE_AI` and `ENEMY_INTEL` permanently "Coming Soon"
- Tracked in STATE.md and `ResearchType.kt` via `isComingSoon`. Regression-guarded by `ResearchTypeTest` set-equality contract.
- **Open design decision:** hide from Labs UI vs spec ENEMY_INTEL for v1.x.
- **Recommendation:** Hide from UI for v1.0 if no design appetite for ENEMY_INTEL pre-launch.

#### #45 — 7 cosmetics show "Coming Soon"
- See Tier 2 above for the cost-asymmetric remediation analysis.

## Roadmap proposals (11)

Issues #21–31 are well-thought-out v1.x strategic proposals without specific bugs to diagnose:

| # | Title | Tier |
|---|---|---|
| 21 | Add Android instrumentation and Compose UI test coverage | Pairs with #32 |
| 22 | Implement progression integrity and anti-exploit heuristics | Existing anti-cheat is solid; this is post-launch enhancement |
| 23 | Add privacy-safe telemetry and gameplay analytics abstraction | v1.x feature |
| 24 | Improve onboarding, retention pacing, and first-session UX | v1.x design work |
| 25 | Add deterministic replay testing and gameplay golden files | Pairs with #37 simulation extraction |
| 26 | Improve mobile runtime performance, startup time, and battery efficiency | Baseline Profiles work |
| 27 | Plan future modularisation and architectural constraint enforcement | Pairs with #37 |
| 28 | Expand long-term progression systems and meta-progression depth | v2.x scope |
| 29 | Improve upgrade UX, readability, and decision support | v1.x polish |
| 30 | Build internal balancing and economy simulation tooling | Pairs with #25 |
| 31 | Strengthen thematic identity, accessibility, and monetisation strategy | v1.x cross-cutting |

All 11 received uniform triage notes pointing at the v1.0 blockers and current closed-track focus.

## Suggested phasing for v1.x roadmap

A possible v1.x patch sequencing (assuming closed track promotes cleanly post-#19 + #20 fixes):

1. **v1.0.1 (urgent polish):** #48 (data delete), #47 (Season Pass UI), #33 (icons). 1 PR, ~2 days.
2. **v1.0.2 (audio):** #38 (real SFX), #46 (throttle), #39 (background music). Asset-bundle PR, ~1 week.
3. **v1.1 (testing infrastructure):** #32 (instrumented tests) + #42 (repo tests) + #37 (simulation extraction). Major refactor PR, ~3-4 weeks.
4. **v1.1 (debt cleanup):** #35, #51 (atomic transactions). 1 PR alongside v1.1.
5. **v1.2 (cloud save):** #36 (Snapshots API). 1 PR, ~1 week.
6. **v1.2 (i18n preparation):** #34 (string extraction phase 1). 1 PR, ~2 weeks.

Cross-cutting items (#43, #44, #45 visual content, #49 STEP_MULTIPLIER) folded into appropriate patches based on bandwidth.

## Cross-references

- Diagnostic comments live on each GitHub issue (`gh issue view N` to read).
- This document is a snapshot — issue states drift. Authoritative state is GitHub issues + STATE.md + RUN_LOG.md.
- The companion fix work for #19 + #20 lives on branch `fix/19-20-uw-autotrigger-and-seeding`.
