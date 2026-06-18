# Release Notes — v1.0.9 (versionCode 25)

**Track:** Play Console **internal**
**Tag:** `v1.0.9` · **Supersedes:** `v1.0.8` (versionCode 24, 2026-06-16)
**Release lane:** automated — `v*` tag → CI `release.yml` → signed AAB → Play internal track. See `docs/plans/plan-32-ci.md` + ADR-0018.

---

## What's new — Play Console "What's new in this version" (≤500 chars)

This is the message published to the Play internal track via the annotated tag (`release.yml`
reads `git tag -l --format='%(contents)'` into `distribution/whatsnew/whatsnew-en-US`, capped at
Play's 500-char limit). Verbatim (297 chars):

```
Smarter upgrades, smoother battles:

• The Workshop now previews exactly what each upgrade does — and flags the best-value buy
• Settings scrolls properly again (reach Replay Tutorial & Delete Data)
• Steadier battles: crash fixes plus sound that survives app-switching
• Faster startup

Keep walking!
```

---

## What shipped (developer detail)

Everything accumulated since v1.0.8 (23 commits). Unlike v1.0.7→v1.0.8 (two presentation-only changes),
this release bundles a feature, several reliability fixes, a startup-perf slice, and infra/compliance
work. Full per-change detail is in `CHANGELOG.md` under `[1.0.9]` and the entries beneath it.

### Player-facing

- **#29 — Workshop upgrade decision support (Gate F).** Now→Next stat preview on every upgrade; a
  combat-power "value per step" indicator + bar on combat upgrades (Damage / Attack Speed / Critical
  Chance, and Critical Factor once crit chance > 0); a single "★ BEST BUY" badge (affordable-first,
  greyed "save up" fallback). Presentation + pure-domain math only — new `CombatPower` /
  `EvaluateUpgradeValue` / `WorkshopLevels` use cases; no schema/engine/economy change.
- **#187 — Settings screen now scrolls.** The root layout was a non-scrolling `Column(fillMaxSize())`,
  so on overflow (Pixel 6, 1080×2400) the **Replay Tutorial** (#24) and **Delete All Data** (compliance)
  cards were unreachable below the fold. Added `verticalScroll(rememberScrollState())`.
- **#190/#191 — crash visibility + two reachable battle crashes.** No more silent process death during a
  soak: a device-local `CrashBreadcrumbStore` + a chaining global uncaught-exception handler; the game
  loop's per-tick `update()`/`render()` is wrapped in a `try/catch` that records a breadcrumb, stops the
  loop, and shows a non-dismissable "Battle error" overlay instead of dying. Two reachable
  `ConcurrentModificationException`s (`EffectEngine` + `uwStates` mutated off the loop thread) closed with
  the same lock discipline as #118. (ADR-0026.)
- **#245 — battle SFX survive background→resume.** `GameSurfaceView` released its `SoundManager` on
  `surfaceDestroyed` but never recreated it, so every post-resume `play(...)` was a silent no-op. New
  `ensureSoundManager()`/`releaseSoundManager()` seams rebuild + re-point the engine on `surfaceCreated`.
- **#26 — faster cold start (Gate G).** A committed Baseline Profile gives ART profile-guided compilation
  on first install for the cold-launch → Home path, plus three behaviour-preserving GC-churn fixes
  (collision-sweep scratch buffers, a cached overlay Paint, `distinctUntilChanged` on shared Flows). The
  Baseline-Profile/Macrobenchmark tooling lives in two `com.android.test` modules that **never ship**.

### Not player-visible (infra / compliance)

- **Reliability hardening (#244/#246/#232/#247)** — FGS `startForeground()` crash-path guard; nullable
  `MusicManager.createPlayer` (degrades to silent on a null `MediaPlayer`); `DailyStepManager` pipeline
  errors now surfaced via a logging seam instead of a silent `catch{}`; `DataDeletionManager` wipe gap
  closed (`onboarding_prefs` + `haptics_prefs`) with a source-scan coverage test that fails on drift.
- **Dependabot wave (#197/#198/#200–#203)** — 6 safe bumps (AGP 9.2.1, Compose BOM 2026.05.01,
  activity-compose 1.13.0, robolectric 4.16.1, gradle/actions 6.2.0, setup-java 5.3.0); core-ktx 1.19.0
  deferred (needs compileSdk 37).
- **Privacy-policy hosting (#192, #207)** — Data-Safety text reconciled to the live AdMob+UMP stack;
  GitHub Pages now publishes `site/` only (not the internal `docs/` tree); public URL unchanged.

> **1081 JVM tests** + 9 instrumented (up from 1010 JVM at v1.0.8). The added JVM coverage is concentrated
> in #29 (+35), #190/#191 (+15), the reliability wave (+12), and #26 (+7). `@Composable` visual pieces
> remain untested per the house norm — verified by on-device sign-off.

---

## Provenance (review trail)

Each substantive change went through the repo's **Adversarial Review Gate** (spec and/or plan reviewed by
a multi-agent fan-out → adversarial refutation → synthesis) before implementation, then subagent-driven
TDD with per-task + final whole-branch review. Detail per change:

- **#29** Gate F — spec 21→11 surviving, plan 15→1 surviving (0 critical/major). Closes Gate F.
- **#190/#191** Gate H blockers — spec 34→25 surviving, plan 18→14 surviving (0 unaddressed
  critical/major). 9-task subagent-driven TDD. ADR-0026.
- **#192** Gate H blocker — 13 raised → 4 surviving (DS-1 verified against Google's published GMA SDK
  Data-Safety disclosure). The **manual Play Console Data-Safety form** action (`docs/release/data-safety-form.md`)
  is a separate human step and is **not** completed by cutting this tag.
- **Reliability wave** — 4-dimension fan-out → per-finding refute (23 agents); 19 findings → 0 critical,
  0 confirmed major; 6 amendments applied.
- **Dependabot** — 8-agent breaking-change review (zero surviving critical/major) + a local combined
  `testDebugUnitTest + assembleDebug`.

---

## Verification (release lane)

- CI `release.yml` builds the **committed** `versionCode` (no auto-bump — Play rejects reused codes).
  versionCode advanced 24 → 25; versionName 1.0.8 → 1.0.9 (the bump rides in with this release PR).
- `bundleRelease` runs R8 minify + lint-vital + signing; `jarsigner -verify` confirms the AAB is signed
  with the production upload keystore (Play App Signing enrolled).
- The release environment requires the `PLAY_LICENSE_KEY` secret — a release build with a blank key is
  hard-failed by the `app/build.gradle.kts` taskGraph guard so the billing fail-open can never ship.
- JVM unit suite green locally + in CI (1081 tests, 0 failures); CI PR gate + instrumented lane gate this
  release PR before merge. On-device feel/visual sign-off is the developer's call.

---

## Next

Promotion to the **closed** track remains judgment-gated on the Closed-Test Readiness Gate (see
`docs/plans/plan-FORWARD.md`). All three Gate H `severity:blocker`s (#190/#191/#192) are merged and ride
in this release; the remaining promotion prerequisites are the **manual Play Console Data-Safety action**
(#192, `docs/release/data-safety-form.md` — cannot be done from the repo) and the three `severity:major`
soak-hardening items (#193/#194/#195). The 2026-06-18 audit verdict is **7/10 — keep shipping internal,
not public-ready**; its 4 net-new HIGHs (#233/#236/#250/#261) are the highest-leverage before-public work.
