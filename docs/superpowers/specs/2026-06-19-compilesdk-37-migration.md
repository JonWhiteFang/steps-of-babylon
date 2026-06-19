# Spec — compileSdk 36 → 37 migration + dependency unblock

**Date:** 2026-06-19 · **Status:** spec (pre-review) · **Type:** build-infra / dependency
**Closes:** #199 (core-ktx unblock); unblocks the recurring compileSdk-37-gated Dependabot wave.

## Problem

The project pins `compileSdk = 36` across all three Android modules. That pin is a documented,
deliberate constraint that has **blocked a recurring set of dependency bumps** — core-ktx 1.19.0
(#199, closed-deferred), lifecycle 2.11.0, sqlite-ktx 2.6.2 — because those artifacts declare an AAR
metadata requirement of `compileSdk ≥ 37`. Dependabot's grouped `all-gradle` PR (#288) currently
**fails CI** at `:app:checkDebugAarMetadata` for exactly this reason. The developer wants to "nip this
in the bud" by raising compileSdk to 37 and unblocking the gated deps in one verified PR.

## Established facts (empirically verified this session, on a scratch branch)

1. **API 37 is stable and published.** With the latest cmdline-tools (15641748), `platforms;android-37.0`
   and `build-tools;37.0.0` appear on the **default/stable** channel (no `--channel=3`). The earlier
   "canary-only" reading was an artifact of outdated cmdline-tools (XML v3 can't parse the v4 stable
   listing). The package id is `platforms;android-37.0` (note the `.0`).
2. **Bare `compileSdk = 37` builds clean.** `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
   → BUILD SUCCESSFUL; 1126 JVM tests pass; **zero new lint findings**; no `compileSdkExtension` needed.
3. **compileSdk 37 + the unblocked deps build clean together.** With core-ktx 1.19.0 + lifecycle 2.11.0
   + sqlite-ktx 2.6.2, the same three-task build → BUILD SUCCESSFUL, tests pass, lint clean.
   `releaseRuntimeClasspath` resolves core-ktx → 1.19.0 and lifecycle → 2.11.0 (no clamp).
4. **CI auto-provisions API 37.** `android-actions/setup-android@v4.0.1` is invoked with no explicit
   platform pin; AGP downloads whatever `compileSdk` requests (SDK licenses pre-accepted). The #288
   failure was `checkDebugAarMetadata` (project compileSdk too low) — NOT "platform 37 not found" —
   confirming AGP gets past platform resolution on the runner.
5. **Blast radius is small.** The migration inventory (thorough repo sweep) found: 3 gradle pins;
   **no** runtime API guards (`Build.VERSION`/`@RequiresApi`/`SDK_INT` — zero hits); **no** lint-baseline
   or lint.xml; Robolectric tests pin `@Config(sdk=[34])` so they're insulated from the compile bump.
6. **AGP 9.2.1 already satisfies the "AGP ≥ 9.1" half** of the core-ktx/HC upgrade gate — only the
   compileSdk-37 half remained.

## Scope decision

**IN this PR:**
- `compileSdk = 36 → 37` in `app/`, `baselineprofile/`, `macrobenchmark/` build files. **`targetSdk`
  stays 36** (raising the runtime target is a separate behavioral decision requiring device testing of
  new OS behaviors; this PR is compile-only). **`minSdk` stays 34.**
- Unblock the previously-gated deps in `gradle/libs.versions.toml`: `coreKtx 1.17.0 → 1.19.0`,
  `lifecycle 2.10.0 → 2.11.0`, `sqliteKtx 2.4.0 → 2.6.2`.
- Doc-sync the rationale everywhere it's asserted (see inventory).

**OUT (explicitly):**
- **Health Connect stays at 1.1.0.** HC 1.2.x is **alpha-only** (`1.2.0-alpha04` latest; no beta/stable).
  The catalog policy is "revisit a 1.2.x beta/stable post-launch"; raising a load-bearing dep to an
  alpha contradicts that and isn't in #288. The catalog comment is *updated* to record that compileSdk-37
  is now done but HC remains gated on a 1.2.x **beta/stable**, not on compileSdk.
- **targetSdk bump** — separate concern (behavioral; needs device verification of API-37 OS changes).
- The rest of #288's bumps (compose-bom, work, billing 9.x, play-services-ads, coroutines 1.11.0,
  mockito-kotlin, kotlin-compose 2.4.0, gradle-wrapper 9.6.0) — those are NOT compileSdk-gated; they ride
  Dependabot's grouped PR #288, which will rebase and re-verify after this lands. Folding them in here
  would conflate a deliberate architectural change with routine bumps.

## Changes

### Code (build files)
- `app/build.gradle.kts:33` — `compileSdk = 37`
- `baselineprofile/build.gradle.kts:9` — `compileSdk = 37`
- `macrobenchmark/build.gradle.kts:8` — `compileSdk = 37`
- `gradle/libs.versions.toml` — `coreKtx = "1.19.0"`, `lifecycle = "2.11.0"`, `sqliteKtx = "2.6.2"`;
  rewrite the Health-Connect rationale comment (L16-21) to reflect compileSdk-37-done / HC-still-alpha-gated.

### Docs (current-state sync)
- `gradle/libs.versions.toml` HC comment (above). **[spec-review amendment]** The rewrite MUST replace
  the trailing clause "Revisit a 1.2.x beta/stable post-launch when compileSdk moves to 37." (else, with
  compileSdk now 37, it reads as "the trigger is met, go upgrade" — inverting the stay-on-1.1.0 decision).
  Verbatim replacement for that sentence: *"compileSdk is now 37, so that half of the gate is cleared; HC
  1.2.x stays out until it reaches **beta/stable** — alpha AndroidX carries no API-stability guarantee and
  HC is load-bearing."* Also drop/repoint the L18-19 "1.2.x alphas now require SDK 37 / compileSdkExtension
  19" so it reads as historical context, not a live blocker.
- `README.md:30` — full current line is `- Android SDK 36 (compile/target), min SDK 34 (Android 14)`; split
  to `- compileSdk 37 / targetSdk 36, min SDK 34 (Android 14)` (**keep the "(Android 14)" annotation**).
- `docs/steering/tech.md` — **THREE** sites (spec-review caught two beyond the HC row):
  - **L6** (Core section prose) — `... / Target & Compile SDK: 36` → `Compile SDK 37 / Target SDK 36`.
  - **L32** (HC row) — drop "1.2.x needs compileSdk 37" as the *blocker* framing → "compileSdk now 37;
    HC 1.2.x still alpha-only (gated on beta/stable)".
  - **L34** Core KTX version cell 1.17.0 → 1.19.0; lifecycle cell 2.10.0 → 2.11.0; sqlite cell → 2.6.2.
  - **L35** (Activity Compose row) — the parenthetical "(transitively resolves core-ktx to 1.18.0)" becomes
    stale/contradictory once core-ktx is directly pinned 1.19.0; rewrite to "(direct core-ktx pin 1.19.0
    now governs)" or drop it.
- `docs/plans/plan-32-ci.md` — **TWO** sites (spec-review caught a second):
  - **L33** — "compileSdk/targetSdk 36 … install platform 36" → "compileSdk 37 / targetSdk 36; setup-android
    provisions platform 37".
  - **L53** (or wherever the second "install platform-36 via setup-android" line is) — same platform-37 fix.
- `CHANGELOG.md` — new `[Unreleased]` entry (the migration + unblock + #199 close + HC-still-gated note).
- `CLAUDE.md` — Tech Stack line "Target/Compile SDK: 36" → "Compile SDK 37 · Target SDK 36" (it currently
  fuses them — verify exact wording on read).
- STATE.md + RUN_LOG.md per the PR Task-List Convention; an **ADR** (this reverses a documented deliberate
  pin — it warrants a decision record).
- Leave **frozen/historical** artifacts untouched: prior RUN_LOG entries, `docs/archive/**`,
  `docs/reviews/2026-06-17-*` (dated), `docs/external-reviews/**`, shipped release-notes, shipped CHANGELOG
  sections. The conflated "compile/target 36" lines inside those are historical-at-authoring and stay.

## Test / verification strategy

- No new unit tests (build-config change; nothing JVM-testable about a compileSdk int). The existing
  1126-test suite + lint + assembleDebug is the regression guard, run locally AND in CI.
- Local verification REQUIRES platform 37 installed (`platforms;android-37.0` + `build-tools;37.0.0`,
  installed this session). Document the install command in the RUN_LOG so it's reproducible.
- CI is the authoritative cross-environment check (clean runner, auto-provisioned SDK). The merged-PR CI
  run (PR gate + instrumented `connected` lane) must be green before merge.
- **Regression-guard the unblock**: confirm `releaseRuntimeClasspath` resolves core-ktx to 1.19.0 and
  lifecycle to 2.11.0 post-change (no silent clamp), as done on the scratch branch. **[spec-review
  amendment]** This is a one-time manual check; it does NOT survive a future Dependabot rebase. Accepted as
  manual for THIS PR (adding a CI resolved-version assertion is out of scope — it'd be its own infra task);
  note the manual check in the RUN_LOG so the next migration repeats it.
- **[spec-review amendment] Release-variant / R8 path.** The PR gate (`ci.yml`) builds only the **debug**
  variant (`assembleDebug`); the release variant (`bundleRelease` + R8/minify at compileSdk 37) is exercised
  only by `release.yml` on a `v*` tag — i.e. NOT by any blocking PR check. To avoid discovering an R8/compileSdk-37
  interaction only at release time, **locally run `./run-gradle.sh :app:assembleRelease`** (or `bundleRelease`)
  on the branch as part of verification (needs the signing config / or use the debug-signing fallback path),
  and record the result. This is local-only; the spec does NOT add a release build to the PR gate (that's a
  separate CI-cost decision), but the local release-assemble is a required verification step here.

## Risks & mitigations

- **R1 — API 37 is "new".** Android 17 / API 37 is recent. Mitigation: this is **compileSdk only**, not
  targetSdk — it changes the SDK we compile against (new APIs visible, new lint checks run) but NOT the
  runtime behavior the OS applies (that's targetSdk, staying 36). The app already runs on API 37 devices
  in compatibility mode regardless. Lint at 37 produced zero new findings.
- **R2 — CI runner can't get platform 37.** **[spec-review amendment — softened claim + fallback]** The
  #288 run got *past* platform resolution (failed at `checkDebugAarMetadata`, not platform-missing) — but
  precisely: #288 keeps `compileSdk = 36`, so that run proved the runner provisions platform *36* and that
  the AGP auto-download machinery works, NOT that it fetched 37. The stronger evidence is Fact #1 (platform
  37 is published + resolvable on the stable channel) + the version-agnostic auto-download (setup-android
  takes no platform pin). The PR's own CI run is the final confirmation; it fails closed (no bad merge).
  **Fallback if CI can't auto-provision android-37:** add an explicit install to the setup-android step
  using the known package ids `platforms;android-37.0` + `build-tools;37.0.0` (this turns "fails closed
  with no recourse" into a real mitigation).
- **R3 — new lint checks at API 37 fail the PR gate** (no baseline to absorb them). Mitigation: verified
  zero new lint findings locally; if CI surfaces any (different lint version), address them in-PR.
- **R4 — a transitive of the bumped deps drags something else to a 37+ floor or breaks a different
  module.** Mitigation: full three-task build verified locally incl. benchmark-module type-check in CI.
- **R5 — Robolectric/JDK interaction at higher SDK.** Mitigation: tests pin `@Config(sdk=[34])`; the
  documented "JDK 21 for SDK 36" Robolectric requirement is not triggered (and unchanged by a *compile*
  bump). 1126 tests pass locally at compileSdk 37.

## Out-of-scope follow-ups (note, don't do)
- targetSdk 37 (behavioral; device-tested).
- Health Connect 1.2.x (await beta/stable).
- The rest of #288's routine bumps (ride the grouped Dependabot PR post-merge).
