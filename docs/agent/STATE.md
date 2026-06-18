# Project State

One-page live snapshot. History lives in `docs/agent/RUN_LOG.md` (per-session) and `CHANGELOG.md`
(per-PR); decisions in `docs/agent/DECISIONS/`. Keep this file to ~one page — push detail there.

**Headline:** **v1.0.8 (versionCode 24) live on Play internal track** · **1069 JVM + 9 instrumented tests**
green · schema v12 · all closed-test Gate A–G in-repo items MERGED · **all 3 Gate H `severity:blocker`s MERGED:** #190 + #191
(crash visibility + the two reachable battle CMEs — PR #204, `d673386`) and #192 (privacy/Data-Safety
text — PR #205, `0019217`). **Remaining to promote internal → closed:** (a) the **manual Play Console
Data-Safety action** for #192 (documented in `docs/release/data-safety-form.md` — cannot be done from the
repo); (b) the 3 `severity:major` soak-hardening items (#193/#194/#195); (c) a `v*` release tag to ship
the merged #190/#191/#192 (currently `[Unreleased]`) to the internal track. Latest audit
(`docs/reviews/2026-06-18-complete-app-review.md`, supersedes 2026-06-17) verdict: **7/10 — continue
building** (keep shipping internal, NOT public-ready); it filed **38 net-new Med+ issues #224–#261 + Low
tracker #262** — none are internal-track blockers; 4 net-new HIGHs (#233/#236/#250/#261) are the
highest-leverage before-public work.

## Current objective

- **CURRENT — sorted the Dependabot PRs (#197–#203).** Reviewed 7 open bumps via the Adversarial Review
  Gate (8-agent `Workflow`, zero surviving critical/major) + a local combined build (`testDebugUnitTest +
  assembleDebug` BUILD SUCCESSFUL, 1069 green). **6 are safe** (AGP 9.2.1, Compose BOM 2026.05.01,
  activity-compose 1.13.0, robolectric 4.16.1, gradle/actions 6.2.0, setup-java 5.3.0) → landed as ONE
  combined build-verified PR (branch `deps/combined-validation`); the 6 Dependabot PRs closed as
  superseded. **#199 (core-ktx 1.17.0→1.19.0) CLOSED, deferred** — it hard-requires compileSdk ≥ 37 (which
  the project intentionally avoids, same as the Health Connect 1.2.x pin); revisit with a deliberate
  compileSdk-37 migration. No app source/schema/test-count change. Pre-existing follow-ups (not blockers):
  AGP-9.1 R8 repackaging is unexercised by the PR gate (only `release.yml`); a compileSdk-37 migration
  remains the gate for core-ktx/Health-Connect upgrades.
- **Previous objective (DONE) — ran the `complete-app-review` skill (2026-06-18 audit) + filed findings as issues + hardened
  the skill.** Second full audit (260 agents, ~14.3M tokens, ~73 min) → `docs/reviews/2026-06-18-complete-app-review.md`
  (900 lines, 20 sections + Refuted/Downgraded; 148 findings → 3 refuted, **7 high · 43 med · 95 low**).
  Verdict **7/10 — keep shipping internal, NOT public-ready**; surviving themes: background step-counting
  under-built vs the GDD risk register (battery-whitelist prompt absent, no-sensor silent fail, gap-fill
  rate-clamp), reactive monetization error-handling (offline IAP swallowed, pending-purchase auto-refund
  risk, non-atomic currency grant), config-change battle-state loss, supply-chain gaps (unvalidated
  wrapper jar, no dependency verification). Filed **38 net-new Med+ issues #224–#261** (after dedup'ing
  ~12 against #190–#223 + epics) + **new Low tracker #262** (superseded #223, now CLOSED). **Two skill
  bugs fixed this run:** (1) the `date` arg silently misfired (`reviewDate:null` → report written to the
  undated path; rescued via `mv`) — `extractReviewDate()` now accepts object/bare-string/JSON-string
  forms (7/7 unit tests); (2) the issue-plan proposed `area:*` labels that don't exist in this repo —
  `domainLabels()` now maps dimensions to real repo labels. No app code/schema/test change.
- **Previous objective (DONE) — added the `complete-app-review` skill (repo tooling) + date-stamped the review artifact.**
  New `.claude/skills/complete-app-review/` (SKILL.md + review-brief.md + review-workflow.js): an
  ultracode `Workflow` that runs the full 20-section audit and puts **every** finding through
  severity-scaled adversarial refutation by **separate** subagents (3 crit/high · 2 med · 1 low) before
  it reaches the report — built TDD-style per `superpowers:writing-skills` (baseline agents shortcut the
  3/2/1 rule; the law is now encoded in the workflow so it can't be). Output is now
  `docs/reviews/<YYYY-MM-DD>-complete-app-review.md` (dated for tracking/backlinking; a run no longer
  overwrites the prior one) — the existing report was renamed `2026-06-17-complete-app-review.md` and
  live backlinks (STATE, plan-FORWARD) repointed; historical refs (CHANGELOG #196, prior RUN_LOG,
  ADR-0026, design spec) left as-authored. Skill also emits a deduped **GitHub-issue plan**
  (propose-then-confirm; Med+ one-each, Lows bundled into one tracker per the #128 convention). No app
  code / schema / test change. **PENDING (this session):** file the 2026-06-17 findings as issues after
  developer vets the plan.
- **Gate H — all 3 `severity:blocker`s MERGED; promotion still needs a Console step + majors + a release
  tag.** #190/#191 (PR #204, `d673386`) and #192 (PR #205, `0019217`) are on `main`. The promotion path
  needs, in any order: **(a)** the **manual Play Console Data-Safety action** for #192 — declare the four
  AdMob-SDK data types + "Contains ads"=Yes + deletion URL per `docs/release/data-safety-form.md` (repo
  can't do this; must precede promotion); **(b)** the `severity:major` soak-hardening items —
  **#195** Missions day-rollover stale query (most self-contained, OPEN) + **#193** no-sensor signal (OPEN);
  **#194** (error states, UX-1) shows **CLOSED on GitHub (2026-06-17, COMPLETED) with no referencing
  PR/commit/comment — status unverified; reconcile before trusting the gate** — each remaining via
  spec→review-gate→TDD; **(c)** a `v*` release tag to ship the merged #190/#191/#192 (currently
  `[Unreleased]`) to the internal track. Then the promote decision is the developer's (plus Gate A audio
  feel + Gate E balance feel, both judgment-only). Lower-severity audit findings (architecture seam, A11Y
  contrast, no-Compose-UI-tests, wrapper validation, clock-tamper TIME-1, i18n) stay
  before-public/post-launch (review §18 Tiers 2–5), NOT blockers.
- **Previous objective (DONE) — privacy-policy hosting fixed (was DOWN → two Play Console publishing
  errors); now published from `site/` only.** PR #207 (`7d5c3e5`) MERGED. The hosted policy
  (`https://jonwhitefang.github.io/steps-of-babylon/`) had gone offline (GitHub Pages disabled), so Play
  Console flagged "Privacy policy page returns a page not found" + "Data deletion page returns a page not
  found". Root cause = hosting down; re-enabling exposed that the legacy "Deploy from branch → `/docs`"
  source was publishing the **entire internal `docs/` tree** publicly. Fix: new `site/` web root
  (`site/index.md` is now the SINGLE canonical policy — old `docs/release/privacy-policy.md` + `docs/index.md`
  deleted) + new SHA-pinned `.github/workflows/pages.yml` that builds + deploys **only `site/`**; Pages
  `build_type` flipped legacy→workflow. **Verified live:** policy URL + `#delete-data` → 200; internal docs
  (`agent/STATE.md`, `reviews/…`, `monetization.md`, `plans/…`) → 404. **URL unchanged → no Play Console
  re-entry.** ⚠️ **STILL PENDING (developer, in Play Console):** re-save / re-submit the two flagged items
  to force a re-crawl (Google caches the last crawl — links resolve now). This is SEPARATE from #192's
  manual Data-Safety declaration (still also pending).
- **Previous objective (DONE): #192 privacy/Data-Safety in-repo text — MERGED (PR #205, squash `0019217`).**
  Brought every disclosure in line with shipped v1.0.8 behaviour (real AdMob v25 + UMP v4 + Billing v8; the
  AdMob SDK auto-merges the ad-ID + ad-services permissions). Hosted policy (`privacy-policy.md` + the
  byte-identical Pages copy `docs/index.md`) rewritten present-tense with a new **Advertising Identifier**
  section, scoped no-upload claims, reconciled Children's-Privacy line, ad-ID reset path; in-app rationale
  (`HealthConnectPermissionActivity.kt`) de-falsified + email unified to `jonwhitefang@gmail.com`; new
  `docs/release/data-safety-form.md` (the manual Console answers — all FOUR AdMob-SDK data types
  collected+shared, verified against Google's published GMA disclosure). Adversarial Review Gate 13→4
  surviving→9 refuted (the 2 majors — Data-Safety under-declaration + Children's-Privacy contradiction —
  fixed). Text/compliance only, no ADR. **The manual Play Console Data-Safety action still remains.**
- **Previous objective (DONE): Gate H code blockers #190 + #191 — MERGED (PR #204, squash `d673386`).** Both
  `severity:blocker` code defects fixed spec→plan→TDD, both artifacts through the Adversarial Review Gate
  (spec 34→25; plan 18→14; both 0 unaddressed critical/major), 9 subagent-driven tasks + final whole-branch
  review, CI PR gate + instrumented lane green. **#190 (REL-1/REL-2):** local `CrashBreadcrumbStore` +
  chaining global uncaught-exception handler + guarded `GameLoopThread` (record→stop→`onLoopError`→"Battle
  error" overlay, no-persist of the corrupt round) + next-launch notice + `DataDeletionManager` wipe.
  **#191 (CONC-1/CONC-2):** `EffectEngine` `effectsLock` + `initUWs` under `entitiesLock` + `uwSnapshot()`.
  **1054→1069 JVM** (+15). ADR-0026.
- **Previous objective (DONE): promotion readiness assessed (internal → closed): READY pending manual
  sign-off; Gate-D fresh-install pass DONE; #187 Settings-scroll fix MERGED (PR #188, `af30e96`).** Ran a
  grounded gate audit (A–G + release-mechanics + open-issue sweep): every code-addressable gate item done,
  CI green, JVM 1054. **Executed the Gate-D pass on-device** (Pixel_6/API36, fresh install of HEAD `952a8bf`):
  full core loop (onboarding → 3 permission flows → Home → Workshop → Battle @4× → Round Over → reward
  persistence → Settings) with **zero observed crashes/ANRs/FATALs**. Surfaced + fixed **#187** (Settings
  didn't scroll → "Replay tutorial"/"Delete All Data" unreachable; PR #188 merged). *Superseded by the
  2026-06-17 complete-app review above, which found reachable (not observed) blockers the manual pass missed.*
- **Previous objective (DONE): #26 perf/battery (Gate G) in-repo slice — MERGED to `main`** (PR #184,
  squash `8f3c2ee`, merged 2026-06-16; CI PR gate + instrumented lane green). Spec + plan both passed the
  Adversarial Review Gate (spec 43→23 surviving; plan 63→48 surviving; both 0 unaddressed critical/major).
  Executed subagent-driven TDD, 13 commits, per-task spec+quality review. **Multi-module now:** `:app` +
  `:baselineprofile` + `:macrobenchmark` (the latter two are `com.android.test` dev-tooling, never
  shipped). Baseline Profile generated + committed (18,804 rules) on the Pixel_6/API36 emulator. Safe
  GC-churn fixes A28 (collision scratch buffers, under `entitiesLock`), A31 (cached CHRONO_FIELD Paint),
  A29 (`distinctUntilChanged` on the profile Flow) — all behaviour-preserving. #124 license guard narrowed
  to exclude benchmark variants (still fail-closed on shippable releases). **1045→1052 JVM**, no
  schema/engine-logic change. **Execution caught real AGP-9 facts the plan missed:** stable baselineprofile
  1.4.1 throws on AGP 9.0.1 → bumped to 1.5.0-alpha06 (dev-tooling only, user-approved); `kotlin.android`
  errors on a `com.android.test` module under AGP-9 built-in Kotlin → removed; multi-module classpath clash
  → root `apply false`. **ADR-0025.** Remaining for #26: device-only half (overnight idle-drain + OEM
  matrix + startup-timing numbers — needs a physical device + a non-debuggable benchmark build type) is
  `[deferred]` in plan-FORWARD Gate G.
- **Previous objective (DONE): #29 Workshop decision support (Gate F) — MERGED to `main`** (PR #182, squash
  `70ebf53`; #29 closed; CI PR gate + instrumented lane green; on-device verified on a Pixel_6 emulator).
  Presentation + pure domain math only: combat-power "value per step" bar + "Now → Next" preview + single
  "★ BEST BUY" badge (affordable-first, greyed "save up" fallback). **Gate F satisfied.** Deferred to
  follow-ups (per the spec): ROI-sort/reorder, quick-buy multiplier, Cards equip-preview, the readability
  theme (already covered by Bundles A–E). **1010→1045 JVM.** See Recently shipped + RUN_LOG.
- **Previous objective (DONE): Bundle E (#164) — SHIPPED in v1.0.8** (tag `v1.0.8`, release merge
  `26cc086` via PR #179; merged via PR #178, squash `9fd40b9`; #164 closed). The LAST of the five A–E
  look-&-feel review bundles — all A–E now shipped. Custom **Cinzel** display font (OFL) on
  Display+Headline tiers + the missing `displayMedium`/`displayLarge` tokens (guarded by
  `SobTypographyTest`); onboarding per-slide **biome journey** gradient (Gardens→Sands→Frozen→Celestial)
  cross-faded via pure `lerpArgb`/`crossfadeNeighborIndex` (`ColorLerp.kt`, static under reduced-motion) +
  legibility scrim + one-shot completion pulse (reuses `PurchasePulse`, **persist-first → pulse →
  navigate** so the gating/nav contract is preserved) + a vector **ziggurat emblem**
  (`ic_ziggurat_emblem.xml`) replacing the slide-1 🏛️ emoji. Spec + plan **both** passed the Adversarial
  Review Gate (spec: ~39 raised→13 surviving, 0 unaddressed critical/major; plan: ~25 raised→9 surviving,
  0 critical/major). Subagent-driven TDD, per-task spec+quality review. **996→1010 JVM** (+14:
  `SobTypographyTest` 2, `ColorLerpTest` 10, `OnboardingContentTest` +2). **ADR-0024.** (Execution caught
  a real defect both reviews missed: the OFL license `.txt` can't live in `res/font/` — the resource
  merger rejects non-font files there — so it's at `licenses/OFL-Cinzel.txt`.)
- **Bundle D (#163 collectibles rarity) — SHIPPED in v1.0.7** (tag `v1.0.7`,
  merge `2e10330`; PR #174 squash `d317fdc`, #163 closed; on-device feel sign-off done; release lane green).
  Presentation-only: shared `presentation/ui/Rarity.kt` (`RarityTier` palette + pure mapping/label/colour
  fns JVM-tested by `RarityTest` + `RarityBadge`/`EquippedChip`/`rarityBorder`) + `RaritySand` token; Cards +
  UW screens get the prominent treatment + EQUIPPED chip + cap hint; UW rarity from `unlockCost`; locked UWs
  dimmed; fixes the Epic/Power-Stone amethyst collision. **990→996 JVM.**
- **Bundle C (#162) — SHIPPED in v1.0.6** (tag `v1.0.6`, PR #172): greenfield haptics + claim/Post-Round
  celebrations + shared 1.12× purchase pulse.
- **NEW standing protocol — Adversarial Review Gate** (CLAUDE.md → Agent protocol): every design spec and
  implementation plan passes a code-grounded multi-dimension review → adversarial refute → confirmed-only
  synthesis Workflow **before** the next stage. Default-on under ultracode; if ultracode is off, flag the
  artifact as unreviewed and ask (don't silently skip).
- **Launch is judgment-gated, not clock-gated** (reframed in PR #145; `plan-FORWARD.md`). v1.0 is live
  on the Play Console **internal** track; we are deliberately staying in internal testing until the
  developer judges the game good enough to promote to **closed** test, made concrete by the Closed-Test
  Readiness Gate (A–G). The old ≥14-day-soak / ≥12-tester clock is a **Phase 2** concern that begins
  *after* promotion to closed — it does not gate the work now. All in-repo release plumbing is done
  (CI release lane live; v1.0.1 fired green).
- **In-repo work = the Closed-Test Readiness Gate** (`plan-FORWARD.md`, A–G). Shipped so far: a
  quick-clear of 8 audit Lows + the latent #35 crash (Gate B + D); **#124** billing signature verify;
  **#146** enemy-counter-drifts-negative; **#127** duplicate daily missions (schema v11→v12) — all
  Gate D; **#154** disable "buy more" at max consistently (Gate F UX polish); **#24** first-launch
  onboarding (**Gate C ticked** — shipped in v1.0.3, no schema change) (see Recently
  shipped). Still open in **Gate D**: **#128** (remaining ~21 Lows: perf/anti-cheat/security groups,
  deferred to v1.1 per the audit grouping). Bigger gate items: **#29** decision-support (Gate F) **MERGED**
  (PR #182, squash `70ebf53`, #29 closed); **#26** perf/battery (Gate G) in-repo slice **MERGED** (PR #184,
  squash `8f3c2ee`; device-measured half `[deferred]`); **#44** Labs Coming-Soon cleanup (Gate **B.1**)
  **MERGED** (PR #186, `952a8bf`, #44 closed). With #44, the in-repo Phase-1 gate surface is exhausted —
  and Gate **D** "clean fresh-install" is now ticked too (verified on-device 2026-06-17; surfaced + fixed
  #187, PR #188). The rest of the gate (A audio, E balance) is developer-judgment / manual, and Gate B.2
  (cosmetic "Coming Soon" framing) is satisfied-by separate cosmetic debt.

## Recently shipped (newest first — see RUN_LOG for detail)

- **2026-06-16 — #26 perf/battery (Gate G) in-repo slice — MERGED to `main`** (PR #184, squash `8f3c2ee`;
  CI PR gate + instrumented lane green). 13 commits on `feat/26-perf-battery-gate-g` (`36dea10`..`8d485f7`),
  squash-merged. Spec + plan both passed the Adversarial Review Gate
  (spec 43→23; plan 63→48; both 0 critical/major). **Measurement infra:** project is now multi-module
  (`:app` + `:baselineprofile` + `:macrobenchmark`, the latter two `com.android.test` dev-tooling, never
  shipped); `androidx.profileinstaller` (stable 1.4.1, the only shipping addition) + a committed Baseline
  Profile (`app/src/release/generated/baselineProfiles/baseline-prof.txt`, 18,804 rules / 1,114
  app-specific) generated on the Pixel_6/API36 emulator. benchmark/baselineprofile pinned **1.5.0-alpha06**
  (the AGP-9-supporting line — stable 1.4.1 throws on AGP 9.0.1; alpha confined to the non-shipping test
  modules, user-approved). **Safe GC-churn fixes (behaviour-preserving, TDD):** A28 — engine-owned scratch
  buffers replace 3 per-frame `filterIsInstance().filter{}` collision-sweep allocs (under `entitiesLock`,
  #118/#125/#146-safe); A31 — cached CHRONO_FIELD overlay `Paint`; A29 — `distinctUntilChanged` on
  `observeProfile/Wallet/Tier`. **#124 guard** narrowed (per-task `!Benchmark && !NonMinified` exclusion;
  still fail-closed on shippable releases — verified cases b/c empirically via `--dry-run`). **CI** PR gate
  now type-checks both benchmark modules (no perf-timing assertions). New docs
  `docs/performance/{battery-audit,startup-baseline}.md`. **1045→1052 JVM**, `testDebugUnitTest lintDebug
  assembleDebug` green; no schema/engine-logic/economy change. **Deferred (device-only):** overnight
  idle-drain + OEM matrix + startup-timing numbers (need a physical device + a non-debuggable benchmark
  build type) — `[deferred]` in plan-FORWARD Gate G. **ADR-0025.** Subagent-driven; execution caught 3 real
  AGP-9 facts the plan's stable-1.4.1 assumption missed.
- **2026-06-16 — #29 Workshop decision support (Gate F) — MERGED to `main`** (PR #182, squash `70ebf53`;
  #29 closed; CI PR gate + instrumented `connected` lane both green; on-device verified on a Pixel_6
  emulator). Presentation + pure domain math only. A combat-power "value per step" indicator + bar, a
  "Now → Next" stat preview on every upgrade, and a single "★ BEST BUY" badge (affordable-first, greyed
  "save up" fallback). New pure use cases `CombatPower` (DPS-proxy ranking index) / `EvaluateUpgradeValue`
  (+`UpgradeValue`) / `WorkshopLevels` (shared workshop-dimension helpers) +
  `DescribeUpgradeEffect.workshopPreview` + presentation `formatPowerPerKStepsLabel`; `WorkshopViewModel`
  wires per-tab Best Buy onto `UpgradeDisplayInfo.nowNext`/`value`; `UpgradeCard` renders them. Spec + plan
  **both** passed the Adversarial Review Gate (spec 35→21 surviving; plan 15→8 surviving + 1 real
  arithmetic bug caught via a refutation; both 0 critical/major); executed subagent-driven TDD (9 tasks,
  per-task spec+quality review + final whole-branch review). No schema/engine/economy change. **Gate F
  satisfied.** Deferred follow-ups: ROI-sort/reorder, quick-buy multiplier, Cards equip-preview, readability
  theme. **1010→1045 JVM** (+35). Also a small infra fix: gitignore `.kotlin` (Kotlin daemon scratch dir).
  On-device visual verification still pending.
- **2026-06-16 — v1.0.8 (versionCode 24) SHIPPED to Play internal track** (tag `v1.0.8`, release merge
  `26cc086`, via release PR #179). Ships **Bundle E (#164)** (PR #178, squash `9fd40b9`) + the **#171**
  battle bottom-chrome fix (PR #177). Release lane green; CHANGELOG `[Unreleased]`→`[1.0.8]`, new
  `release-notes-v1.0.8.md`. All five A–E look-&-feel bundles now shipped to internal. **1010 JVM** green.
- **2026-06-15 — Bundle E (#164) — SHIPPED in v1.0.8** (merged via PR #178, squash `9fd40b9`; #164
  closed; rolled to internal in v1.0.8 / `26cc086`). The last A–E look-&-feel bundle. Brainstormed (visual
  companion) → spec (`b7d3c94`, adversarial-reviewed: ~39→13 surviving, the major catch = the "gold
  shimmer reusing Bundle C" finish beat was unbuildable [no shimmer infra; `ClaimCelebration` is
  event-driven + unmounted by nav] → redefined as a `PurchasePulse` round-trip pulse, persist-first →
  pulse → navigate) → 8-task TDD plan (`9fb24c7` + fixes `9bb58e5`, adversarial-reviewed: ~25→9 surviving,
  0 critical/major; the scary transparent-Surface "black text" finding was *refuted* — Scaffold provides
  `onBackground`=Ivory as `LocalContentColor`). Executed subagent-driven (per-task spec+quality review).
  **(1)** Cinzel OFL font in `res/font/` (Regular+Bold) + `Cinzel` FontFamily in `Type.kt` on
  Display+Headline + new `displayMedium`/`displayLarge` (closes the latent `OnboardingScreen` fallback) +
  `SobTypographyTest`. **(2)** `ic_ziggurat_emblem.xml` vector emblem. **(3)** pure `ColorLerp.kt`
  (`lerpArgb` + `crossfadeNeighborIndex`, 10 JVM tests). **(4)** `OnboardingSlide` gains pure `biome:
  Biome?` + `art: OnboardingArt?` (+2 content tests). **(5)** `OnboardingScreen` biome gradient +
  cross-fade + scrim + emblem switch + completion pulse. versionCode 24 / 1.0.8 committed; CLAUDE.md
  headline + CHANGELOG + source-files synced. **996→1010 JVM**, `testDebugUnitTest lintDebug assembleDebug`
  green. **Execution caught a build-breaking defect both reviews missed:** the OFL `.txt` can't live in
  `res/font/` (resource merger rejects non-font files) → moved to `licenses/OFL-Cinzel.txt` (commit
  `bcf55e8`). **ADR-0024.** Feel sign-off done; merged via PR #178; shipped in v1.0.8 (tag `v1.0.8`,
  release merge `26cc086`, PR #179).
- **2026-06-15 — #171 battle bottom-chrome overlap fix — SHIPPED in v1.0.8** (merged via PR #177, merge
  `1643361`; rolled to internal in v1.0.8 / `26cc086`).
  Presentation-only. The battle bottom controls (speed `1x`/`2x`/`4x` + pause + upgrade) moved from a
  bottom-center Row to a **left vertical rail** (`BattleControlRail` at `CenterStart`), so they no longer
  overlap/clip the UW cooldown bar or the in-round upgrade menu (the reported bug). UW bar now owns
  bottom-center alone (`navigationBars` inset + 24dp). The in-round upgrade menu spans the **full screen
  width** along the bottom and clears the rail **vertically** — its fixed `IN_ROUND_MENU_HEIGHT` (280→240dp)
  sits its top below the rail's bottom, so the rail stays fully visible/tappable while shopping (list
  scrolls; no content lost). New `presentation/battle/ui/BattleControlRail.kt` (rail composable +
  `BattleControlRailDefaults.WIDTH`). Button bodies extracted **verbatim** — no behaviour change (only
  pause keeps its haptic). Spec **and** plan both passed the Adversarial Review Gate (spec: 21→14
  surviving/7 refuted, 0 critical/major; plan: 10→4 surviving/6 refuted). Subagent-driven TDD; **on-device
  verified at 1080×2400** (rail clears HUD; menu full-width; speed/pause/upgrade tappable with the menu
  open). `testDebugUnitTest lintDebug assembleDebug` green, **996 JVM** (the dev pivoted the menu from a
  left-pad-to-dodge-the-rail layout to full-width-clears-vertically after seeing it on-device, which retired
  the horizontal `menuStartPadding`/`GAP` coupling and its `BattleControlRailTest` — clearance is now a
  Compose layout fact, on-device-verified, not JVM-pinned). **Merged via PR #177 (merge `1643361`);
  shipped in v1.0.8.** No ADR (presentation-only;
  reuses the shared-`ui/` extraction pattern; design in the spec).
- **2026-06-15 — v1.0.7 (versionCode 23) SHIPPED to Play internal track** (tag `v1.0.7` on release merge
  `2e10330`, via release PR #175). Ships **Bundle D (#163)** — the collectibles rarity visual system —
  merged via PR #174 (squash `d317fdc`). Release collateral only (versionCode 22→23, versionName
  1.0.6→1.0.7, CHANGELOG `[Unreleased]`→`[1.0.7]`, new `release-notes-v1.0.7.md`). CI PR gate +
  instrumented lane green on both #174 and #175; **release lane green in 6m32s** — keystore decode + Play
  license-key write + **signature verify** + Play-internal upload all passed; GitHub Release `v1.0.7`
  published with the signed `app-release.aab` (15.6 MB) asset. Annotated-tag "What's new" (243 chars:
  rarity borders/badges + EQUIPPED chip + cap hint). **996 JVM** unchanged from the merge.
- **2026-06-14 — Bundle D (#163) IMPLEMENTATION (presentation-only; SHIPPED in v1.0.7 via PR #174).**
  Collectibles rarity visual system for UWs + Cards. Brainstormed (visual companion) → spec (`6ad0ce5`) →
  **spec adversarial review** (14 findings, 8 surviving, 6 refuted; fixes `1b3994e` — root fix: `color()`
  made a plain fun not `@Composable`, dissolving a Modifier workaround) → 6-task TDD plan (`871d7e4`) →
  **plan adversarial review** (6 findings, 5 surviving incl. 1 real spec/code divergence — locked-UW rarity
  wasn't dimmed; fixes `fdb6efe`). Executed via subagent-driven-development (per-task spec+quality review;
  Task-2 quality review caught + fixed an accent-bar corner-bleed by clipping to the card shape). New shared
  `presentation/ui/Rarity.kt` (`RarityTier` 3-tier palette + pure `color()`/`cardRarityTier`/`uwRarityTier`/
  labels + `@Composable RarityBadge`/`EquippedChip` + `Modifier.rarityBorder` = 3dp border + left accent bar)
  + `RaritySand` token. Cards + UW screens: prominent treatment + explicit `EquippedChip` + header cap hint;
  UW rarity from `unlockCost` (RARE/EPIC/LEGENDARY), locked UWs dimmed (alpha 0.5f). Fixes the latent
  Epic/Power-Stone amethyst collision. `testDebugUnitTest lintDebug assembleDebug` green; **990→996 JVM**
  (+6 `RarityTest`), 0 failures. Final whole-branch review READY TO MERGE (0 critical/major; diff is 100%
  presentation — 4 prod + 1 test + 3 docs). Merged via PR #174 (squash `d317fdc`, #163 closed); on-device
  feel sign-off done; shipped in v1.0.7 (see entry above). No ADR (presentation-only; reuses the
  shared-`ui/`-layer pattern; spec D1–D10 captured in the design doc).
- **2026-06-14 — Bundle C (#162) — SHIPPED in v1.0.6** (presentation-only; merged via PR #172, tag `v1.0.6`).
  Executed the 12-task adversarially-reviewed plan via subagent-driven-development (per-task spec+quality
  review; Opus for the CRITICAL Task 8 ticker-safe harness). Greenfield haptics (`HapticsPreferences` +
  `Haptics`/`rememberHaptics` + Settings toggle), shared 1.12× `PurchasePulse` across all spend buttons,
  `ClaimCelebration` chip fed by conflated `celebration` events on the Missions/Supplies VMs (emit on
  `Result.Success` only — `claimAll` once if ≥1 Success; the 3 `UnknownCosmetic` milestones never
  celebrate), Post-Round entrance (keyed on the round-end nullability, not identity) + staggered reward
  sting + Play-Again haptic. **Reverted a Task-7 implementer deviation** (SharedFlow → spec-mandated
  `Channel.CONFLATED`); Task 8's `while(true)` ticker hang avoided via `@VisibleForTesting cancelForTest()`
  + pure top-level label builders. `testDebugUnitTest lintDebug assembleDebug` green; **981→990 JVM**, 0
  failures; on-device smoke passed (toggle persists, Workshop spend works). Tactile/visual feel sign-off
  done; merged via PR #172; shipped in v1.0.6. No ADR (implements ADR-0022 feel direction; reuses the conflated-event + economy patterns).
- **2026-06-14 — Bundle C (#162) spec + plan + the Adversarial Review Gate protocol (docs-only, uncommitted to a PR yet).**
  Brainstormed Bundle C (feedback/feel) → spec (`95234a9`) → **spec adversarial review** (29 findings,
  25 surviving, 4 refuted; fixes `67cdbe1`) → 12-task TDD plan (`2fb26c8`) → **plan adversarial review**
  (36 findings, 29 surviving incl. 1 CRITICAL — bare `runTest{}` would hang on the Missions VM `while(true)`
  ticker; fixes `d068ce1`). Plan-review fixes: extracted pure `missionRewardLabel`/`supplyLabel` fns
  (testable without the VM), `@VisibleForTesting cancelForTest()` to stop the ticker, Success-arm-only
  milestone edit (preserve smart-quote escapes), explicit `remember`-import delete-list. **Codified the
  review process** into CLAUDE.md + START_HERE as the standing **Adversarial Review Gate** (`ce70351`),
  with the ultracode-off fallback (flag + ask). No production code yet — implementation is the next session.
- **2026-06-14 — full doc-drift sweep (post-v1.0.5; docs-only, in `[Unreleased]`).** Multi-agent audit
  (67 agents: ground-truth extraction → 11-lane fan-out + cross-doc-coherence + link-integrity lanes →
  per-finding adversarial verification → synthesis). 50 candidates → 47 verified → **31 confirmed fixes
  across 19 live docs**; 3 false positives cleared (dangling paths inside frozen release-notes — left
  untouched). Themes: version/test-count lag (v1.0.2/vc18/960 → v1.0.5/vc21/981 in README/GDD/master-plan);
  shipped-but-pending statuses (#161→PR #167/v1.0.4, #24→v1.0.3, Gate-D #124/#127/#146→v1.0.2 ticked in
  plan-FORWARD); `ResearchType` is **12 not 10** + only AUTO_UPGRADE_AI coming-soon (ENEMY_INTEL wired
  V1X-15b); deleted `Currency.kt` purged from structure/source-files; cloud-save migration v11→v12 → **v12→v13**
  (v11→v12 consumed by #127); lib-room destructive-fallback claim, lib-hilt example DAO names, instrumented
  file count 2→4, DAO providers 12→13, `track`→`tracks`, CardDust→CardCopy, CHANGELOG broken links (8).
  ADR-0005 got a permitted status-only amendment (StubBillingManager deleted). **981 JVM unchanged.**
- **2026-06-14 — v1.0.5 (versionCode 21) released to Play internal track** (tag `v1.0.5` on `92a66f8`,
  via release PR #170). Shipped the two post-v1.0.4 fixes from PR #169: the **Battle HUD vertical-offset**
  fix (player-visible) + the **`release.yml` `track`→`tracks`** deprecation rename. Release collateral
  only (versionCode 20→21, versionName 1.0.4→1.0.5, CHANGELOG `[Unreleased]`→`[1.0.5]`, new
  `release-notes-v1.0.5.md`). CI PR gate + instrumented lane green on #170; release lane green in 7m27s
  (signed AAB, `jarsigner -verify` + #124 license-key step passed; GitHub Release with AAB asset).
  **First release to exercise the new `tracks:` input — and the `track`-deprecation annotation that
  appeared on the v1.0.4 run is now GONE**, confirming the fix end-to-end. Approved player-facing
  "What's new" (188 bytes).
- **2026-06-14 — Post-v1.0.4 follow-ups + spot-check (PR #169 squash `85ce889`; docs `07429fc`).** Cleared
  every open item from the v1.0.4 release audit. **(1) Battle HUD vertical offset** — the in-round HUD
  `Column` carried a stale `top = 80.dp` (quit button `72.dp`) that double-counted a removed status-bar +
  ActionBar offset (`MainActivity` is edge-to-edge + Scaffold supplies the inset; #159 removed the
  ActionBar), leaving the wave header ~53dp below the engine health bar; fixed to `40.dp` / `32.dp`,
  reproduced + re-verified on the emulator. **(2) `release.yml` `track`→`tracks`** deprecation rename
  (verified non-breaking at the pinned action SHA). Both ride the **next** `v*` tag (they sit in
  CHANGELOG `[Unreleased]`, not v1.0.4 which already shipped). **(3) #160 navigate-away loading
  no-reflash** — **verified PASS** on-device via 30fps screen-recording: the `LoadingBox` spinner shows
  only on first cold tab entry, never on re-entry; no code change. Presentation + CI config only; **981
  JVM** unchanged; CI PR gate + instrumented lane green on #169.
- **2026-06-14 — v1.0.4 (versionCode 20) released to Play internal track** (tag `v1.0.4` on `1972f1a`,
  via release PR #168). Shipped the four presentation-only look-&-feel waves merged since v1.0.3/code19
  (#159 design tokens / ActionBar removal; #160 Bundle A; #161 Bundle B PR-B1 #166; #161 Bundle B PR-B2
  #167). **Release collateral only** — versionCode 19→20, versionName 1.0.3→1.0.4, CHANGELOG
  `[Unreleased]`→`[1.0.4]`, new `docs/release/release-notes-v1.0.4.md`. Pre-release adversarial audit
  (4 lenses + 2 refutations) confirmed: presentation-only claim holds against the real diff (no
  DAO/repo/entity/migration/di/service/engine touched), changelog accurate, versionCode 20 unused,
  #124 guard + signing + NDK symbols + release.yml intact. CI PR gate + instrumented `connected` lane
  both green on #168; release lane green (signed AAB uploaded, `jarsigner -verify` + #124 license-key
  step passed; GitHub Release with AAB asset). Approved warm player-facing "What's new" (426 chars).
  Two follow-ups + one spot-check it surfaced were all closed the same day (see the #169 entry above).
- **2026-06-13 — Look-&-feel Bundle B PR-B2: bottom-nav restore-wrong-screen bug fix (#161)** (PR #167,
  merged 2026-06-13 — merge commit `b4f2a2b`; shipped in v1.0.4). Second of the two Bundle-B PRs (PR-B1
  merged via #166). Done under `systematic-debugging` — **reproduced on-device before any fix**. The
  device repro *corrected the reported symptom*: the original "Cards → tap Home → Cards" path did NOT
  reproduce; the bug actually surfaces on returning to the **owning** tab
  (`Home→Workshop→Cards→Stats→Workshop` → lands on Cards). **Root cause:** the canonical multi-back-stack
  idiom (`popUpTo(Home){saveState}`+`restoreState`) saves/restores each tab's whole nested sub-stack, but
  in this flat NavHost Cards/Weapons are push-children of Workshop → folded into Workshop's saved branch
  → resurrected on tab re-entry. **Fix:** tab tap → tab root (`popUpTo(Home)`+`launchSingleTop`, no
  save/restore), extracted to a shared `bottomNavOptions()` builder. The obvious
  `popUpTo(graph.startDestination)` "fix" was confirmed a no-op (Home *is* the flat-graph start).
  System-Back + Home-tile pushes unaffected (don't route through BottomNavBar); cross-tab scroll no
  longer preserved (accepted). Guard: **`BottomNavRestoreTest`** (JVM `TestNavHostController` — drives the
  real shared NavOptions, no Compose rule), **red-before-green verified**; +`navigation-testing` test dep.
  (The Robolectric+Compose-UI-rule harness was abandoned after 6 infra failures — `ActivityScenario`
  can't resolve a host activity under Robolectric, PR-4736; `TestNavHostController` sidesteps it.)
  **979→981 JVM**; lint + assemble green; fix re-verified on-device. **ADR-0023.**
- **2026-06-13 — Look-&-feel Bundle B PR-B1: navigation back affordances (#161)** (PR #166, merged to
  `main`). First of **two sequential** Bundle-B PRs (PR-B2 above is the second).
  Spec → adversarially-reviewed plan (the plan review caught a wrong `WindowInsets(0)` inset approach and
  a per-class vs per-method test-count error — both fixed pre-build) → subagent-driven execution with
  per-task spec+quality review. **New shared `presentation/ui/SobTopAppBar.kt`** (`CenterAlignedTopAppBar`:
  centered title + ArrowBack) rendered ONCE in MainActivity's outer Scaffold `topBar`, gated by the new
  pure `Screen.secondaryTitle(route)` helper → appears on exactly the 8 push-nav secondary screens
  (Weapons/Cards/Supplies/Economy/Missions/Settings/Store/Help), null elsewhere (tabs/Battle/Onboarding).
  Back = `navigateUp()`. Default `TopAppBarDefaults.windowInsets` (bar self-pads the status bar — verified
  on-device the title/arrow sit below the status bar on all 8). Inline titles deleted from
  Settings/Help/Store/Economy/Supplies (now carried by the bar); Missions keeps its two section headers;
  Economy/Supplies actions right-aligned (+ Supplies action row hidden on empty state). **975→979 JVM**
  (+4 `ScreenSecondaryTitleTest`, Robolectric); `testDebugUnitTest lintDebug assembleDebug` green;
  on-device verified all 8 screens + negative cases (no bar on tabs/Battle/Onboarding). No
  engine/economy/concurrency touched; no `Screen.kt` route-list change. No ADR (the back-stack-contract
  ADR lands with PR-B2).
- **2026-06-13 — Look-&-feel Bundle A: correctness & a11y cleanup (#160)** (PR #165, squash `491815b`;
  merged to `main`; issue #160 closed; CI PR gate + `connected` instrumented lane both green). Second
  safe presentation-only wave off the
  2026-06-12 UX review (spec → adversarially-reviewed plan → subagent-driven execution with per-task
  spec+quality review + a final whole-branch Opus review). **New shared `presentation/ui/` layer:**
  `CurrencyDisplay.kt` (`CurrencyType` + `icon()/tint()/label()` + `CurrencyValue`/`CurrencyCost` +
  `formatCurrency` — single source of truth for currency presentation, themed-glyph art later = one-file
  swap), `LoadingBox.kt`, `EmptyState.kt`. **Finished the de-emoji sweep** — every UI-control/currency/
  status glyph across Labs/Cards/Store/Missions/Economy/Weapons/Onboarding/Battle-HUD → Material icons
  (decorative Help headings + onboarding slide icons incl. 🏛️ intentionally left → Bundle E). **a11y:**
  onboarding page-dots row-level `contentDescription` ("Page N of M"); correct per-site descriptions on
  new status icons. **Loading spinners** on 10 menu screens (added `isLoading` to Store + Weapons UiState;
  Battle excluded). **Workshop** defensive empty-state; **Settings** renamed `NotificationSettings*`→
  `Settings*` + retitled (route string unchanged). **Deleted dead `domain/model/Currency.kt`.** Other 4
  bundles tracked: #161 nav (+restore bug), #162 haptics/feel, #163 rarity visuals, #164 font/onboarding-art.
  **973→975 JVM** (+2 `CurrencyDisplayTest`); `testDebugUnitTest lintDebug` + `assembleDebug` green; the
  Battle file touched is the HUD pause glyph only (no engine/renderer). Instrumented + on-device
  navigate-away loading check pending (CI `connected` lane + manual).
- **2026-06-12 — Look-&-feel polish pass (presentation-only; Gate C/F UX)** (PR #159, squash `2dc9a08`;
  merged). Off a full UX/art-direction review (15-agent fan-out + adversarial verify + live on-device
  emulator walkthrough). Headline fixes: **removed the redundant platform ActionBar app-wide** (new
  `res/values/themes.xml` `NoActionBar` + `windowBackground`; `AndroidManifest` `android:theme`;
  pixel-verified the old #1A1B20 bar is now DeepBronze); **hid the bottom nav during onboarding**;
  added **design tokens** (`ui/theme/Type.kt` `SobTypography` + `Shape.kt` `SobShapes` + role/currency/
  status tokens in `Color.kt`, wired in `Theme.kt`); **fixed the LapisLazuli-as-text WCAG fail (1.45:1)**
  via a `LapisLight` token on the Home headline; **de-emoji'd** Home/Economy controls → Material icons;
  **palette-aligned** currency colours; fixed verified bugs (Cards double-Gems header, Stats legend label
  + `toArgb`, biome-title capitalization, Store "1 Gems" plural, thousands separators). 12 files changed
  + 4 new — zero engine/economy/concurrency touched. ADR-0022. (Bundle A above continues this work.)
- **2026-06-12 — #24 first-launch onboarding (Gate C)** (PR #157, merged 2026-06-12; released in
  v1.0.3). Gate-C slice of V1X-22: one-time 4-slide tutorial carousel (walk→spend→battle) + permission
  primer + Settings "Replay tutorial". New `data/onboarding/OnboardingPreferences` (device-local
  SharedPreferences flag — **no Room schema change**), `presentation/onboarding/*`. `MainActivity` chooses
  start destination from a synchronous flag read via pure `Screen.startDestination()`; only the
  cold-permission request branch is gated behind completion; permanent-denial recovery via
  Snackbar→app-settings. **Explain-only — no Steps grant** (rejected welcome-bonus; ADR-0021). Built
  spec-first + adversarially reviewed (spec & plan) before coding; subagent-driven with per-task
  spec+quality review. **960→973 JVM.** #24 stays OPEN for deferred retention scope (D2/D7, wave-5
  celebration, projected-reward estimates — pair with telemetry #23).
- **2026-06-11 — #154 disable "buy more" at max** (PR #156, squash `592097b`; issue closed; Gate F UX).
  At a purchasable's cap the buy control must be un-clickable + visually disabled, consistently. 3/4
  surfaces already correct; fixed the Workshop `UpgradeCard` outlier (`enabled = canAfford && !isMaxed`
  on the `Card`, + pinned `disabledContainerColor` so it keeps the Gold "MAX" tint). 5 regression tests
  pin the "canAfford==false at cap even with MAX_VALUE balance" state contract + no-op-spend guards.
  No schema/economy change (spend logic already refused at cap). **955→960 JVM.**
- **2026-06-11 — full doc-drift sweep** (PR #155, squash `8f1b5bc`; docs-only). 20-doc-cluster
  workflow audit (each finding adversarially re-verified against code) + a deeper manual residual pass.
  Fixed ~60 confirmed drift items across 25 docs: schema v11→v12 sweep, test-count → genericized/955,
  use-cases 32→36, UpgradeType 23→24, SupplyDropTrigger 4→3, nav routes 12→13, **Step Overdrive ghosts**
  (removed R4-01 → Rapid Fire) purged from GDD/battle-formulas/CONSTRAINTS/product, **Card Dust** refs
  (removed R4-08 → copy-based) corrected (kept the legacy `cardDust` DB column note), battle-formula
  errors (crit-research on multiplier not chance; multishot/bounce additive caps 11/10; cash
  fortune+card multipliers; UW per-path R4-06 table), step-tracking overlap-rule inversion + exercise
  types, jarsigner `-strict` contradiction, V1X dead-link + shipped-wave status. Restructures: CHANGELOG
  split into `[1.0.2]`/`[1.0.1]` (git-verified boundary) + fresh `[Unreleased]`; balance-report
  historical banner; release-checklist promoted version-agnostic; `docs/index.md` self-documenting
  comment. New docs: `docs/release/release-notes-v1.0.2.md`, `docs/steering/security-model.md`.
  **Also fixed a real infra bug:** `.gitignore` `release/` was unanchored (matched `docs/release/` too,
  silently swallowing new release docs) → anchored to `/release/`.
- **2026-06-11 — v1.0.2 released to Play internal track** (tag `v1.0.2` on `5298fae`). Shipped the
  whole batch since v1.0.1/code17 (#118–123, #125/126, #121, audit-Lows, #124, #146, #127). No bump
  needed — code 18 / 1.0.2 was already committed (PR #108) but never tagged. Release lane green:
  signed AAB uploaded with 5-bullet player-facing "What's new"; GitHub Release + AAB asset created.
  #124 license-key guard passed (signature verification active in the shipped build).
- **2026-06-11 — #127 duplicate daily missions** (PR #152, squash `605f0a9`; issue closed).
  Check-then-insert generator with no DB uniqueness → two concurrent VM inits each inserted a full
  batch → 6 claimable missions/day. Fix: `(date, missionType)` unique index + `@Insert(IGNORE)` +
  `@Transaction generateForDate`; **schema v11→v12** migration (`MIGRATION_11_12`) dedups via
  `GROUP BY` + `MAX()` (incl. `MAX(claimed)` so a claimed duplicate isn't resurrected). First
  migration with a dedicated test. TDD'd (real-Room); 5-lens adversarial review (11 findings, 4
  minor confirmed + all fixed). 948→955 JVM.
- **2026-06-11 — #146 enemy counter drifts negative** (PR #151, squash `6b5779a`; issue closed).
  Two confirmed causes: SCATTER children bypassed the only `enemiesAlive++`; `EnemyEntity.takeDamage`
  re-fired `onDeath` on a corpse (projectile path #125 didn't cover) → counter + cash/Step
  double-credit. Fix: new authoritative `GameEngine.aliveEnemyCount()` (derived from live entities
  under `entitiesLock`) replaces the removed hand-kept tally; `takeDamage` guarded `if (!isAlive)
  return 0.0`. TDD'd; 4-lens adversarial review (8 findings, 0 real, cause-#2 guard mutation-verified).
  945→948 JVM. No schema change.
- **2026-06-11 — #124 billing signature verification** (PR #148, squash `c610f46`; issue closed).
  New `PurchaseVerifier` seam: client-side `SHA1withRSA` Play-signature check + signed-product/token
  binding, gating both grant paths before `grantOnceAtomic`. Release-build fail-closed Gradle guard +
  CI `PLAY_LICENSE_KEY` secret so fail-open can't ship. TDD'd; two rounds of adversarial review (2
  confirmed findings — release fail-open + product binding — fixed and confirmed closed). 933→945 JVM.
  No schema change. **Deploy prerequisite satisfied:** `PLAY_LICENSE_KEY` secret set in the GitHub
  `release` environment — the next `v*` tag ships with verification active.
- **2026-06-11 — quick-clear audit-Low wave** (branch `fix/quick-clear-gate-b-d`). 8 trivial Lows
  (#16/#17/#20/#21/#22/#30/#33/#43) + the latent #35 card-pack crash, TDD'd + adversarially reviewed.
  HC moved off alpha → 1.1.0 stable (#33). No schema change. 908→933 JVM.
- **2026-06-11 — #121 `daily_step_record` lost-update** (PR #144). Column-targeted DAO upserts replace
  the read-copy-`@Upsert`; concurrent sensor/worker/HC writers no longer clobber. 899→908 JVM. No schema change.
- **2026-06-10 — #125 + #126 battle-perf** (PR #143). Game-loop catch-up clamp (#126) + single-pass
  `getAliveEnemies` (#125). 890→908 JVM across the two waves.
- **2026-06-10 — Dependabot wave** (PR #142). 6 bumps incl. Gradle 9.5.1 + JUnit Jupiter 6.1.0. Dashboard clean.
- **2026-06-10 — 5 Medium+ audit fixes** (#118/#119/#120/#122/#123, PRs #129–#133/#140/#141). 867→890 JVM.
- **2026-06-10 — Codebase audit + CLAUDE.md rewrite** (committed to `main`). 45 findings filed as #118–#128.
- **2026-06-10 — Kiro→Claude Code conversion** (PR #117, ADR-0019). Committed memory spine + SessionStart hook + `/checkpoint`.

## What works (current capabilities)

- **Gameplay:** Plans 01–30 + 10b + R + R2 + R3 + R4 complete. Full battle loop, Workshop/Labs/Cards/UWs,
  tier progression, biomes, walking encounters, anti-cheat, milestones/missions, stats/history.
- **First-launch onboarding (#24, Gate C — shipped in v1.0.3):** 4-slide tutorial carousel +
  contextual permission primer + Settings replay; explain-only (no Steps grant).
- **Battle engine:** simulation extracted to pure-domain `domain/battle/` (V1X-09 Phases 1–3 complete,
  ADR-0012) — `GameEngine` is a thin render shell delegating to `Simulation`.
- **Persistence:** Room schema v12 (13 entities, SQLCipher-encrypted), migrations v7→12, decrypt-fail wipe recovery.
- **Monetization:** real Play Billing v8 + AdMob v25 + UMP v4, device-verified; live Store prices.
- **Release infra:** Play Console listing live, internal track active; CI pipeline (Plan 32) — PR gate +
  instrumented emulator lane (both required on `main`) + `v*`-tag release lane to Play internal (ADR-0018);
  v1.0.1 fired green 2026-06-04. Dependabot dashboard clean.
- **Guardrails:** `DomainPurityTest` (machine-enforced domain purity, #27) + the concurrency/economy regression
  guards listed under fragile zones below.

## Known issues / debt

- **CLOSED-TRACK PROMOTION BLOCKERS (2026-06-17 complete-app review, Gate H) — all 3 MERGED:** **#190**
  (crash visibility + game-loop guard — REL-1/REL-2) + **#191** (two reachable battle CMEs — CONC-1/CONC-2)
  via PR #204 (`d673386`, ADR-0026); **#192** (privacy/Data-Safety text — PRIV-1/SEC-1) via PR #205
  (`0019217`). **#192 STILL requires a manual Play Console Data-Safety action** (declare the four AdMob-SDK
  data types per `docs/release/data-safety-form.md`) before promotion — a developer step, not code. Plus 3
  `severity:major` soak-hardening items still open: **#195** (Missions day-rollover stale query — STATE-1),
  **#194** (no error states — UX-1), **#193** (no-sensor silent dead-end — REL-3). And the merged blockers
  ship only on the next `v*` tag (currently `[Unreleased]`). Full report: `docs/reviews/2026-06-17-complete-app-review.md`.
- **Promotion gate:** the Closed-Test Readiness Gate (`plan-FORWARD.md` A–H) is the call to promote
  internal → closed; Gate H (above) must clear first. Google's ≥12-tester + ≥14-day-soak policy is a
  downstream Phase-2 step that only begins after that promotion.
- **Open audit Lows:** #128 (remaining ~21 Lows — perf/anti-cheat/security groups, deferred to v1.1).
  Plus the 2026-06-17 review's before-public/post-launch findings (architecture seam, A11Y contrast,
  no-Compose-UI-tests, Gradle-wrapper validation, clock-tamper TIME-1, i18n) — review §18 Tiers 2–5.
  (#124 purchase signature verification, #146 enemy-counter drift, #127 duplicate daily missions fixed 2026-06-11.)
- **RO-09 deferred (v1.x backlog):** #3 STEP_MULTIPLIER × cross-validator unit mismatch (needs schema migration);
  #4 currency lifetime-counter desync (display-only); #5 TOCTOU on gem/PS spend (lifetime drift, wallet correct);
  #6 per-kill credit on `viewModelScope` (≤1 step lost on mid-round nav-away — confirmed by the 2026-06-17 review:
  BossKilled PS on `viewModelScope` is lost on nav-away; StepReward correctly uses `applicationScope`).
- **Content/polish debt:** audio shipped (V1X-04/05/06 — 9 synthesized `.ogg` SFX + 2 BGM tracks via
  SoundPool/MediaPlayer; only the subjective "feel" assessment remains, Gate A); cosmetics — 5 ziggurat
  palettes plumbed (zig_jade + zig_obsidian store-purchasable; lapis/garden/sandals milestone-only), rest
  "Coming Soon" pending art. The 4 seeded projectile/enemy-skin cosmetics have no render path (#192-adjacent
  FEAT-1, before-public).
- **Phase B debt:** B.4 FollowOnPipeline + B.5 UpdateMissionProgress extraction (ADR-0004, ~1 week, zero user benefit — deferred).
- `BuildConfig.USE_REAL_ADS` consent-prefetch branch is JVM-untested (device-verified). Play "no debug symbols"
  warning persists on every upload (pre-stripped .so files — informational).
- _(Resolved 2026-06-14, post-v1.0.4 — shipping in v1.0.5/code21.)_
  **`release.yml` `track`→`tracks`** deprecation rename (verified non-breaking at the pinned action SHA);
  **Battle HUD vertical offset** — the stale `top = 80.dp` HUD pad double-counted the removed status-bar +
  ActionBar chrome (`MainActivity` is edge-to-edge + Scaffold supplies the inset); fixed to `40.dp` /
  quit-button `32.dp`, reproduced + re-verified on the emulator.

## Top priorities / next actions

Phase 1 (work down the Readiness Gate so the developer can decide to promote — the real current work):
1. **Manual Play Console Data-Safety action for #192** (cannot be done from the repo): in Play Console →
   App content → Data safety, declare the four AdMob-SDK data types + "Contains ads" = Yes + deletion URL
   per `docs/release/data-safety-form.md`. Required before promotion. **(Developer action.)**
2. **Soak-hardening (Gate H `severity:major`) — before/during the soak:** **#195** Missions day-rollover
   (STATE-1, most self-contained), **#194** error states (UX-1), **#193** no-sensor signal (REL-3). Not hard
   promotion gates but they degrade tester experience / feedback quality. Each via spec→review-gate→TDD.
3. **Cut a `v*` release** to ship the merged #190/#191/#192 (currently `[Unreleased]`) to the internal
   track (version bump + release notes + tag; the release lane handles signing + upload).
4. **(DONE — all 3 Gate H blockers MERGED:** #190/#191 PR #204 `d673386`; #192 PR #205 `0019217`.)
3. **Then the remaining developer-judgment / manual gate items:** **Gate A** in-play audio feel, **Gate E**
   early-tier balance feel. None is code-addressable; the promote decision is yours once Gate H clears,
   then a manual Play Console action on the uploaded AAB.
4. **All other in-repo gate items DONE:** look-&-feel bundles A–E (→ v1.0.4–v1.0.8); **#29** Gate F (MERGED `70ebf53`); **#26** Gate G in-repo slice (MERGED PR #184, device half `[deferred]`); **#44** Gate B.1 (MERGED PR #186); **#187** Settings-scroll (MERGED PR #188) from the Gate-D fresh-install pass.
5. **Deferred — not a blocker:** #128 remaining ~21 audit Lows → v1.1; the review's before-public/post-launch findings (review §18 Tiers 2–5); Gate B.2 cosmetic "Coming Soon" framing.

Phase 2 (only AFTER the developer promotes internal → closed):
6. **(External)** Recruit ≥12 testers; ≥14-day closed soak; apply for production access; staged rollout; tag `v1.0.0`.

Backlog (post-launch): V1X waves — see `docs/plans/plan-V1X-roadmap.md` (cloud save #36, i18n #34, telemetry #23, etc.).

## Do-not-touch / fragile zones

- `domain/model/` — stable; balance constants validated by regression tests. `BillingProduct.skuId()` is a stable public API.
- `domain/usecase/` — 36 use cases stable.
- `presentation/battle/effects/` — particle pool, effect engine, all visual effects.
- `gradle/libs.versions.toml` — single source for all dependency versions. `app/proguard-rules.pro` — hardened R8 rules.
- `app/build.gradle.kts` — signing config + AdMob production-ID wiring (don't break the test-ID fallback) + `ndk { debugSymbolLevel = "FULL" }`.
- `Screen.items by lazy` + `argumentFreeRoutes by lazy` — guard against sealed-class init-order NPE (commit 1872af9).
- `release/` — gitignored; `release/upload-keystore.jks` is irreplaceable (now Play-App-Signing-enrolled, mostly historical).
- **Live-price wiring (PR B)** — "fetch once on Store entry" is intentional for v1; don't add resume/locale refresh without re-deriving cache invalidation.
- **GOLDEN × overdrive `fortuneMultiplier` (RO-09 #2)** — 3-site "higher buff wins" invariant, guarded by 4 GameEngineTest entries.
- **`GameEngine.entities` thread-safety (#118)** — every structural mutation/iteration behind the private `entitiesLock`; guarded by `GameEngineConcurrencyTest`.
- **`GameEngine.uwStates` is on `entitiesLock` too (#191 CONC-2, ADR-0026)** — `updateUWs` iterates it under the tick lock; `initUWs` (main-thread replay) is now wrapped in `synchronized(entitiesLock)`; the 200ms VM poll reads `uwSnapshot()` (a list-structure copy under the lock), NOT `uwStates` directly. Don't add an unlocked `uwStates` structural mutation or re-point the poll at the raw list. Guarded by `GameEngineConcurrencyTest`'s replay-race test.
- **`EffectEngine` has its OWN `effectsLock` (#191 CONC-1, ADR-0026)** — `effects`/`pendingEffects` add/drain/render/clear are guarded; per-effect `update`/`render` + the Canvas draw run OUTSIDE it (snapshot idiom); `removeAll` is a deferred 2nd lock acquisition so `update→removeAll` order is preserved (no 1-frame effect-lifetime change). `pool`/`screenShake` stay loop-confined (unguarded). **Lock order is acyclic: `entitiesLock` (outer) → `effectsLock` (inner) — never the reverse** (EffectEngine holds no GameEngine ref). Guarded by `EffectEngineConcurrencyTest`.
- **Game-loop crash guard (#190 REL-2, ADR-0026)** — `GameLoopThread.run()` wraps per-tick `update()`/`render()` in `try/catch` → record breadcrumb → stop loop → `onLoopError`. The inner `lockCanvas/unlockCanvasAndPost` try/finally MUST stay nested inside the outer catch (render crash unlocks first). `BattleViewModel.onBattleLoopError` sets `battleError` + `roundEnded` (`@Volatile`) and must NOT set `eng.roundOver` (would persist the corrupt round). Don't remove the guard or run the loop unguarded. Guarded by `GameLoopThreadGuardTest` + 2 `BattleViewModelTest` entries.
- **GOLDEN damage layer (#119)** — GOLDEN is a re-derived `goldenDamageMult`, not a stat snapshot. Don't restore snapshot-and-overwrite.
- **Economy spend/claim contract (#122, ADR-0020)** — `spendGems`/`spendPowerStones`/`spendStepsIfSufficient` return Boolean; gate the grant on the result. One-shot claims use guarded `… AND claimed=0` + mark-first.
- **`DailyStepManager` Mutex (#120)** — credit read-check-write under a non-reentrant `Mutex`; don't add an un-locked counter mutation.
- **`GameEngine.getAliveEnemies()` must NOT be cached across a frame (#125)** — `takeDamage` re-fires `onDeath` on a dead enemy; a shared snapshot double-credits kills. Guarded by `R125` GameEngineTest.
- **HUD enemy count is derived, not tallied (#146)** — `GameEngine.aliveEnemyCount()` counts live `EnemyEntity` under `entitiesLock`; the desync-prone `WaveSpawner.enemiesAlive` tally was removed (SCATTER children bypassed its only `++`; `onDeath` re-fires double-counted). Don't reintroduce a hand-kept counter. `EnemyEntity.takeDamage` is guarded `if (!isAlive) return 0.0` (no corpse re-hit → no double-credit). Guarded by 3 `R146` GameEngineTest entries.
- **Game-loop frame clamp (#126)** — `SimulationMath.clampAccumulator` (`MAX_CATCHUP_TICKS = 8`); don't lower below ~8 (a 30fps@4× render legitimately needs ~7.9 ticks/frame). Guarded by `SimulationMathTest`.
- **`daily_step_record` writers must stay column-targeted (#121)** — disjoint-column `ON CONFLICT(date) DO UPDATE SET` upserts, NOT a whole-row read-copy-`@Upsert`. Guarded by `DailyStepDaoTest` + `StepRepositoryImplTest`.
- **`daily_mission` uniqueness is DB-level (#127)** — `(date, missionType)` unique index + `@Insert(onConflict = IGNORE)` is the authoritative guard against duplicate daily missions; the generator's read-then-insert check is racy on a WAL pool. Don't weaken the index or relax `IGNORE` back to plain `@Insert`. Schema v12; `MIGRATION_11_12` dedups via `GROUP BY` + `MAX()` (keeps `MAX(claimed)`). Guarded by `DailyMissionDaoTest` + `Migration11To12Test`.
- **Billing signature verification (#124, ADR-0005 amendment)** — every wallet grant goes through `PurchaseVerifier.isValidPurchase(originalJson, signature, expectedProductId, expectedPurchaseToken)` BEFORE `grantOnceAtomic`, on BOTH paths. The product+token binding is load-bearing (blocks replaying a signed cheap receipt for an expensive product) — don't credit off the caller's `product` without verifying first. `PLAY_LICENSE_KEY` blank → fail-open is debug/CI only; a **release** build with a blank key is hard-failed by the `app/build.gradle.kts` `taskGraph` guard + the `release.yml` `PLAY_LICENSE_KEY` secret step — don't weaken either or fail-open could ship. Guarded by `RealPurchaseVerifierTest` + `BillingManagerImplTest`.
- **Currency presentation is centralized (#160)** — all currency glyphs render via
  `presentation/ui/CurrencyDisplay.kt` (`CurrencyType.icon()/tint()` + `CurrencyValue`/`CurrencyCost`).
  Adopt themed-glyph art by swapping `icon()` in ONE place; don't reintroduce inline emoji/`%,d` currency
  text on screens. `formatCurrency` uses `Locale.US` grouping for deterministic output (pinned by
  `CurrencyDisplayTest`). The domain `Currency` enum was deleted as dead — `CurrencyType` is the
  presentation-layer home (carries Compose `icon()/tint()`, can't live in the Android-free domain).
- **Onboarding gating + flag location (#24, ADR-0021)** — the first-launch flag is device-local SharedPreferences (`OnboardingPreferences`), intentionally NOT Room (must not sync; reinstall re-shows). In `MainActivity`, `startDestination` reads it **synchronously** via pure `Screen.startDestination()`; **only** the cold-permission request branch is gated behind `onboardingComplete` (service-start/HC-chaining stay ungated — don't widen the gate or step counting breaks for granted users); the deep-link collector gates on live nav state (current route == Onboarding). `Screen.Onboarding` is deliberately **out of** `allScreens`/`argumentFreeRoutes`/`items` (not a public deep-link target) — keep it out (`DeepLinkRoutingTest` pins the exact-13 set). Onboarding is **explain-only — never grant Steps** (preserves the hard invariant). Guarded by `OnboardingRoutingTest` + `OnboardingPreferencesTest` + `OnboardingContentTest` + `OnboardingViewModelTest` + `DeepLinkRoutingTest` navigate_to guards.
- **Top-bar back affordance is centralized (#161, PR-B1)** — the back/up bar renders via ONE
  `presentation/ui/SobTopAppBar.kt` in MainActivity's **outer** Scaffold `topBar`, gated by the pure
  `Screen.secondaryTitle(route)` helper (returns the title for the 8 push-nav secondary screens, null
  for tabs/Battle/Onboarding/unknown). Don't reintroduce per-screen bars or thread an `onNavigateBack`
  param into screens; add/remove a screen's bar by editing `secondaryTitle` in ONE place (pinned by
  `ScreenSecondaryTitleTest`). The bar uses the **default** `TopAppBarDefaults.windowInsets` — the
  topBar self-pads the status bar; do **NOT** set `windowInsets = WindowInsets(0)` (that draws the
  title under the status bar — caught in plan review). Back = `navigateUp()`. `secondaryTitle` must
  NOT touch `Screen`'s `by lazy` route lists (no route change → `DeepLinkRoutingTest` unaffected).
- **Bottom-nav back-stack contract (#161, PR-B2, ADR-0023)** — a bottom-nav tab tap goes to the tab
  ROOT, via the shared `NavOptionsBuilder.bottomNavOptions()` (`BottomNavBar.kt`): `popUpTo(Home.route)`
  + `launchSingleTop`, **NO** `saveState`/`restoreState`. The save/restore idiom resurrected push-children
  (Cards/Weapons are flat-graph children of Workshop, not a nested sub-graph) on tab re-entry. Don't
  re-add save/restore unless you first restructure the graph so each tab owns a nested `navigation{}`
  sub-graph. `popUpTo(graph.startDestination)` is a no-op here (Home IS the flat-graph start — don't
  mistake it for a fix). The regression guard `BottomNavRestoreTest` reuses `bottomNavOptions()` to drive
  the exact NavOptions; keep the builder shared so the test can't drift from the bar. (Note: Compose-UI
  test rules don't work under Robolectric here — `ActivityScenario` can't resolve a host activity, PR-4736;
  use `TestNavHostController` for nav tests.)
- **Haptics + feel are centralized (#162, Bundle C)** — `Haptics`/`PurchasePulse`/`ClaimCelebration` live
  in `presentation/ui/`. Haptics fire via `View.performHapticFeedback` gated by `HapticsPreferences`
  (Settings toggle, default ON, read at *call* time so a toggle takes effect on the next tap — don't
  capture `isEnabled()` at `remember`). The shared 1.12× `PurchasePulse` is `graphicsLayer` scale (no
  reflow) — add it via `Modifier.pulseScale(rememberPulse())`; don't reintroduce a per-screen inline pulse.
  **Claim celebrations fire from a conflated `Channel` VM event** (`MissionsViewModel`/
  `UnclaimedSuppliesViewModel`.`celebration`), gated on `Result.Success` (never on failure/`UnknownCosmetic`;
  `claimAll` only on ≥1 Success via a left-of-`||` fold) — keep it a conflated `Channel`, NOT a
  `SharedFlow`/`StateFlow` (a replay=0 SharedFlow drops pre-subscriber emits + breaks the test harness).
  The Missions VM's `while(true)` init ticker means JVM tests that construct it MUST call the
  `@VisibleForTesting cancelForTest()` or `runTest` cleanup hangs; label *content* is covered by the pure
  top-level `missionRewardLabel`/`supplyLabel` builders (VM tests assert emission *count*). Post-Round
  entrance is hosted in `BattleScreen` keyed on the round-end **nullability** transition, NOT
  `RoundEndState` identity — watch-ad copies must not re-trigger it.
- **Collectibles rarity is centralized (#163, Bundle D)** — all rarity visuals render via
  `presentation/ui/Rarity.kt`. `RarityTier.color()` is a **plain fun** (NOT `@Composable`) so it's
  JVM-testable and callable from `Modifier.rarityBorder`; don't re-add `@Composable` (a parallel
  composable must delegate to it). UW rarity is **derived in the UI from `unlockCost`** (`uwRarityTier`:
  ≤60/61-89/≥90 → RARE/EPIC/LEGENDARY) — there is no domain rarity field; don't add one for this.
  `uwRarityTier`'s ranges (not exact-value matches) + `RarityTest`'s iterate-over-`entries` guard pin
  today's six costs while tolerating a re-price/7th UW. `rarityBorder` **clips to the card shape first**,
  then border + accent bar via `drawWithContent` (a `drawBehind` bar is occluded by the Material3
  container fill; an unclipped bar bleeds past the rounded corners). The Cards `EquippedChip` replaced
  the `primaryContainer` tint (D4 — chip is the sole equipped signal); don't reintroduce the tint. Locked
  UWs show **dimmed** rarity (alpha 0.5f on border + badge), not hidden (D6). Guarded by `RarityTest`
  (pure fns; the `@Composable` pieces are visual-only, verified on-device).
- **Battle bottom chrome is ONE coordinated layout (#171)** — speed/pause/upgrade live on the left rail
  (`BattleControlRail` at `Alignment.CenterStart`, fixed `BattleControlRailDefaults.WIDTH`, `railStartInset`
  = `WindowInsets.systemBars.union(displayCutout).only(Start)`); the UW cooldown bar owns bottom-center
  (`navigationBars` inset + 24dp); the in-round upgrade menu spans the **full screen width** at
  `BottomCenter` and clears the rail **vertically** — `InRoundUpgradeMenu`'s fixed `IN_ROUND_MENU_HEIGHT`
  (240dp) is short enough that the bottom-anchored sheet's top sits below the rail's bottom, so it never
  covers the rail's lower buttons (rail stays tappable while shopping; the list scrolls). **Don't
  reintroduce independent bottom-anchored `padding(bottom = …)` offsets** — that three-way contention is
  exactly the overlap #171 fixed. **Don't shrink the rail's vertical extent or grow `IN_ROUND_MENU_HEIGHT`
  past the rail's bottom edge** without re-checking on-device — that's the only thing keeping the full-width
  menu from covering the rail (the clearance is a Compose layout fact, not a single constant). Rail buttons
  are extracted **verbatim** (only pause has `haptics.tap()`; don't add haptics to the speed/upgrade
  buttons). Modifier order on the rail is width→verticalScroll→background→padding (background after scroll →
  pill wraps the viewport). Landscape HUD↔rail overlap is a known, accepted, de-scoped limitation (battle is
  portrait-designed; manifest doesn't lock orientation). No Compose-rule layout test (PR-4736) — on-device
  is the acceptance gate (verified at 1080×2400). (History: the menu first left-padded to dodge the rail
  horizontally via a `menuStartPadding`/`GAP` coupling + `BattleControlRailTest`; the dev pivoted it to
  full-width-clears-vertically after seeing it on-device, retiring that coupling + test.)
- **Onboarding biome theming + completion beat (#164, Bundle E, ADR-0024)** — the per-slide gradient is a
  SINGLE shared layer behind the `HorizontalPager` (a per-page `Box` can't cross-fade), computed from
  `pagerState.currentPageOffsetFraction` via the pure `crossfadeNeighborIndex` + `lerpArgb` in
  `presentation/ui/ColorLerp.kt` (clamps the neighbour to `[0,lastIndex]` so overscroll can't index OOB);
  reduced-motion → static per-page gradient (no offset blend). The root `Surface` is `Color.Transparent`
  so the gradient shows through — the title stays Ivory because the Scaffold above provides
  `onBackground`=Ivory as `LocalContentColor` (don't set an explicit wrong `contentColor`). The
  **completion beat MUST stay persist-first**: `finish()` calls `completeOnboarding()` FIRST +
  unconditionally, has an `if (finishing) return` latch, then triggers the pulse; `onFinished()` fires
  exactly once from `LaunchedEffect(finishing)` (immediate under reduced-motion). The pulse reuses
  `PurchasePulse` (`rememberPulse()`/`pulseScale()`) on the icon + the two `finish()` CTAs — NOT the
  "Enable step counting" button (it calls `onEnableStepCounting`). Don't reorder the final-slide
  `when` (granted→!asked→denied) or gate navigation on the animation. This sits ON TOP of the existing
  onboarding gating contract (ADR-0021) — that contract is unchanged. The slide model's new `biome`/`art`
  fields are pure (domain enum + marker enum) — keep `OnboardingSlide` Android-free (the JVM
  `OnboardingContentTest` depends on it). Cinzel re-themes Display+Headline app-wide incl. numeric
  Headline content (CurrencyDashboard balance, Home steps hero) — digit legibility is an on-device
  sign-off item, not assumed. **Font-license caveat:** the OFL `.txt` lives at `licenses/OFL-Cinzel.txt`,
  NOT `res/font/` (the AAPT resource merger rejects non-`.ttf/.otf/.xml` files under `res/font/`).
- **Combat-power index (#29)** — a display/ranking **PROXY** only — `CombatPower` returns a bare `Double`,
  must NEVER feed the engine (type-incompatible with the `ResolvedStats` stat-sinks). The value bar +
  Best-Buy badge apply only when **Δpower > 0** (Damage / Attack-Speed / Crit-Chance + Crit-Factor once
  crit chance > 0). Workshop previews increment the **WORKSHOP** dimension via `WorkshopLevels` (NOT
  in-round; spec §5.1). `EvaluateUpgradeValue` + `DescribeUpgradeEffect.workshopPreview` **share**
  `WorkshopLevels` so the Now→Next string + the value delta can't diverge.
- **Benchmark modules + AGP-9 plugin wiring (#26)** — `:baselineprofile` + `:macrobenchmark` are
  `com.android.test` **dev-tooling, never shipped**. benchmark/baselineprofile is pinned **1.5.0-alpha06**
  because stable 1.4.1 throws on AGP 9.0.1 at plugin-apply (`Module :app is not a supported android
  module`) — don't "downgrade to stable" without re-checking AGP-9 support. The two new plugins
  (`android.test`, `androidx.baselineprofile`) are declared `apply false` in the ROOT `build.gradle.kts`
  (pin the version once — a per-module version clashes with AGP on the classpath). **Do NOT add
  `kotlin.android`** to a `com.android.test` module — AGP 9 has built-in Kotlin and applying it errors.
  `profileinstaller` is the ONLY shipping addition (stable 1.4.1). The committed profile lives at
  `app/src/release/generated/baselineProfiles/baseline-prof.txt` (plugin-managed src; R8 + profileinstaller
  consume it). No `connectedBenchmarkReleaseAndroidTest` exists yet — capturing startup *numbers* needs a
  non-debuggable `benchmark` build type on `:app` (a fragile-zone change). ADR-0025.
- **#124 guard is benchmark-variant-aware (#26)** — the fail-closed license-key guard in
  `app/build.gradle.kts` matches the broad `^(bundle|assemble|package).*Release$` regex but excludes
  per-task `!contains("Benchmark") && !contains("NonMinified")` (the baselineprofile plugin auto-generates
  `benchmarkRelease`/`nonMinifiedRelease` which would otherwise false-trip it). Exclusion is **per-task**,
  NOT whole-graph: a combined `bundleRelease`+benchmark graph still hard-fails on a blank key. Don't relax
  to a literal allowlist (drops future flavored release tasks) or to a whole-graph check (fail-open risk).
- **Collision scratch buffers (#26 A28)** — `GameEngine` owns 3 reusable `ArrayList` scratch buffers
  (`projScratch`/`enemyScratch`/`enemyProjScratch`); the per-tick partition over `entities` fills them in
  ONE pass **inside `synchronized(entitiesLock)`** (#118) and hands them to `CollisionSystem.checkCollisions`
  (now takes pre-filtered typed lists, no `filterIsInstance`). The single `enemyScratch` fill serves the
  whole sweep — matching the old single snapshot, so the #146 `takeDamage` corpse-guard still prevents
  double-credit. These are per-tick (cleared every sweep), NOT the #125 cross-frame cache — and `getAliveEnemies()`
  is untouched. Don't move the partition out of the lock or re-filter mid-sweep. Guarded by
  `CollisionSystemScratchTest` + the `A28` GameEngineTest entries.
- **Cached CHRONO_FIELD Paint (#26 A31)** — the full-screen chrono overlay uses a cached
  `chronoOverlayPaint` field (colour `0x222196F3`, FILL), NOT a per-frame `Paint()` alloc. `setChronoActiveForTest`
  is a `@VisibleForTesting` seam. Guarded by `ChronoOverlayPaintTest` (instance-identity across two renders).
- **Profile-Flow dedup (#26 A29)** — `PlayerRepositoryImpl.observeProfile/observeWallet/observeTier` end with
  `.distinctUntilChanged()` AFTER the `.map{}` (dedupe the projected value). Works because `PlayerProfile`/
  `PlayerWallet` are data classes (structural equality). Suppresses no-op re-emissions to every screen
  ViewModel; safe because no consumer uses these as a bare trigger (all `.first()` or `combine`/`stateIn`
  value chains). Don't move the distinct before the map.
- **Labs surfaced-research filter is centralized (#44)** — which `ResearchType`s appear in Labs is decided
  by ONE pure helper, `ResearchType.surfacedInLabs()` (`entries.filterNot { it.isComingSoon }`,
  order-preserving). `LabsViewModel` builds `researchList` from it — don't re-inline
  `entries.filterNot { … }` at the call-site (re-introduces the drift this consolidated). `LabsScreen` no
  longer reads `isComingSoon` (the dead `COMING SOON` UI branches were removed) — don't re-add a per-card
  coming-soon branch; a hidden type never reaches a card. The `LabsViewModel.startResearch`
  `if (type.isComingSoon)` guard is the **reachable** second layer (blocks a Step spend if a future
  deep-link/quick-research caller bypasses the list) — keep it. Guarded by `ResearchTypeTest` (pins the
  helper BODY: excludes coming-soon + equals the 11 wired types); the call-site→helper wiring is held by
  code review + the guard, NOT a test (the VM's `while(true)` ticker blocks cheap VM-construction tests —
  an accepted coverage boundary, per the spec). `AUTO_UPGRADE_AI` stays the sole `isComingSoon=true`
  research type (deferred to v1.x; its real design = "auto-spends Cash on optimal upgrades during rounds").

## References

- **Memory loop:** `CLAUDE.md` (canonical guide; now incl. the **Adversarial Review Gate** for specs/plans) · `docs/agent/START_HERE.md` (contract) · `docs/agent/CONSTRAINTS.md` · SessionStart hook + `/checkpoint` skill.
- **Skills (`.claude/skills/`):** `checkpoint` (end-of-session memory write) · `complete-app-review` (ultracode `Workflow` for the full 20-section audit — every finding refuted by separate subagents 3/3/2/1; writes `docs/reviews/<date>-complete-app-review.md` + emits a propose-then-confirm GitHub-issue plan).
- **Look-&-feel bundle docs (all shipped):** Bundle E (#164, v1.0.8) spec `docs/superpowers/specs/2026-06-15-look-and-feel-bundle-e-design.md` + plan `docs/superpowers/plans/2026-06-15-look-and-feel-bundle-e.md` (both review-passed) · #171 spec/plan `docs/superpowers/{specs,plans}/2026-06-15-battle-bottom-chrome-overlap*.md` · Bundle D (#163, v1.0.7) spec `docs/superpowers/specs/2026-06-14-look-and-feel-bundle-d-design.md` + plan `docs/superpowers/plans/2026-06-14-look-and-feel-bundle-d.md` · Bundle C (#162) shipped in v1.0.6.
- **Plans:** `docs/plans/plan-FORWARD.md` (forward plan + Closed-Test Readiness Gate — start here) · `docs/plans/master-plan.md` (v1.0 completion record) · `docs/plans/plan-V1X-roadmap.md` (backlog of record). Completed v1.0 plan files archived under `docs/archive/completed-plans-v1.0/`.
- **Reference docs:** `docs/steering/` (tech, structure, source-files, lib-*) · `docs/architecture.md` · `docs/database-schema.md` · `docs/battle-formulas.md`.
- **Audit (run via the `complete-app-review` skill; dated reports are point-in-time artifacts):**
  `docs/external-reviews/2026-06-10-multi-agent-code-audit.md` (findings #118–#128) · `docs/reviews/2026-06-17-complete-app-review.md` (raised Gate H blockers #190–#192 + soak-hardening #193–#195) · **latest `docs/reviews/2026-06-18-complete-app-review.md` (7/10; 7 high · 43 med · 95 low; filed Med+ #224–#261 + Low tracker #262; 4 net-new HIGHs #233/#236/#250/#261).**
- **Release:** `docs/release/plan-31-walkthrough.md` · privacy policy `site/index.md` (canonical; published to GitHub Pages by `.github/workflows/pages.yml` — `site/` ONLY, not `docs/`) → hosted https://jonwhitefang.github.io/steps-of-babylon/ (delete-data: `#delete-data`) · listing copy `docs/release/play-store-listing.md`.
- **ADRs:** 0003 (Battle Step Rewards) · 0004 (FollowOnPipeline, deferred) · 0005 (Billing) · 0006 (Ads) · 0007 (ADV keystore) · 0010 (Cards copy-based) · 0012 (Simulation extraction) · 0014 (i18n) · 0015/0016 (STEP_MULTIPLIER / GPS dropped) · 0017 (ENEMY_INTEL) · 0018 (CI) · 0019 (Claude Code) · 0020 (economy atomicity) · 0021 (onboarding explain-only) · 0022 (design tokens + de-emoji) · 0023 (bottom-nav back-stack) · 0024 (Bundle E: custom font + onboarding biome theming + persist-first completion beat) · **0025 (#26 perf/battery Gate-G: multi-module benchmark tooling on AGP-9 [1.5.0-alpha, dev-only] + #124 guard narrowing + A28/A31/A29 GC-churn fixes)**. Full set in `docs/agent/DECISIONS/`.
