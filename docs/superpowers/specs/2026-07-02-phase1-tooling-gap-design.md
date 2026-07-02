# Phase 1 Tooling-Gap "Safety Baseline" — Design

> **Status:** approved (brainstorming) · **adversarial-review gate PASSED** (19 raised · 15 applied ·
> 4 refuted; 1 CRITICAL + 5 HIGH surviving, all applied) · **Date:** 2026-07-02 · **Author:** agent session
> **Source:** `docs/reviews/tooling-gap-assessment.md` (2026-07-02 audit) · Tracker **#389**
> **Scope:** the 6 Phase-1 "Safety baseline" findings — close every path where broken code ships green.

## Goal

Close the six Phase-1 "Safety baseline" tooling-gap findings, structured as **3 PRs grouped by kind**.
Phase 1's theme is *"close the paths where broken code ships green"*: an R8 break that reaches Play
before any CI build exercises it (#370), a leaked keystore/secret (#376), the two prose-only invariants
an AI agent is most likely to trip (#371 Steps-generation, #372 lock order), and the crash diagnostic
that never leaves the device (#374 + #380).

Findings (from tracker #389):

| Issue | ID | Kind | One-line |
|---|---|---|---|
| #370 | `cicd-1` (**major**) | CI config | Build the minified `release`/R8 variant in CI before a `v*` tag auto-publishes it |
| #376 | `sectooling-1` | CI + repo settings | Secret scanning / gitleaks / push protection |
| #371 | `ai-3` | new test | Machine-enforce "Steps are never generated in-game" (step-credit allowlist test) |
| #372 | `ai-2` | process + config | Battle-engine lock-order invariant prose-only → make `concurrency-reviewer` mandatory |
| #374 | `obs-2` | prod code | Crash breadcrumb dead-ends → give it a tap-gated report exit path |
| #380 | `obs-1` | doc | Add a post-release monitoring runbook to `release-checklist.md` |

## Grounding facts established during brainstorming (verified against HEAD)

These three facts reshaped the design vs. the audit's verbatim recommendations:

1. **The `play.licenseKey` fail-closed guard WILL trip on a bare `assembleRelease` (#370).**
   `app/build.gradle.kts:170-193` — `gradle.taskGraph.whenReady` matches `Regex("^(bundle|assemble|package).*Release$")`,
   excludes only `Benchmark`/`NonMinified`, and **hard-fails** the build when `play.licenseKey` is blank.
   So a naive `assembleRelease` in the PR gate would fail. → We inject a **throwaway dummy** `play.licenseKey`
   into `local.properties` in CI, never touch the guard.
2. **GitHub-native secret-scanning + push protection are ALREADY enabled (#376).**
   `gh api repos/JonWhiteFang/steps-of-babylon` → `secret_scanning: enabled`,
   `secret_scanning_push_protection: enabled` (public repo). What is left: the gitleaks workflow with a
   custom `*.jks` / `storePassword=` rule, and optionally flipping on
   `secret_scanning_non_provider_patterns` (currently `disabled`).
3. **detekt is `2.0.0-alpha.5` (#372).** Custom-rule authoring on the 2.0 alpha line has an unstable
   API, so the detekt lock-rule is a **timeboxed best-effort**, with a documented fallback.

The real step-credit surface (#371), traced in code — the audit's proposed allowlist
`{DailyStepManager, StepCrossValidator, ClaimSupplyDrop, AwardBattleSteps}` does **not** map cleanly.
The invariant boundary is the set of `PlayerProfileDao` methods that can write `currentStepBalance`
(verified by `rg 'currentStepBalance' PlayerProfileDao.kt` — **three** writers, not one):

- **`adjustStepBalance(delta)`** (`PlayerProfileDao.kt:38-40`) — `SET currentStepBalance = MAX(0, … + :delta)`.
  The relative credit/debit primitive. Reached two ways:
  - **(a)** `PlayerRepository.addSteps(...)` → `dao.adjustStepBalance(amount)` (`PlayerRepositoryImpl.kt:41`);
    callers of `.addSteps(`: `ClaimSupplyDrop.kt:33`, `StepCrossValidator.kt:152`, `DailyStepManager.kt:222/274/310`.
  - **(b)** the direct `DailyStepDao.creditBattleStepsAtomic` → `playerDao.adjustStepBalance(credited)`
    (`DailyStepDao.kt:188`) — the `AwardBattleSteps` battle path, which bypasses `addSteps` entirely.
  - `PlayerRepositoryImpl.spendSteps` calls `dao.adjustStepBalance(-amount)` (negated) — a DEBIT, must NOT be flagged.
- **`updateStepBalance(balance)`** (`PlayerProfileDao.kt:16-17`) — `SET currentStepBalance = :balance`, an **absolute
  setter** with **zero production callers today** (verified: `rg '\.updateStepBalance\('` → none). This is a latent
  landmine: a new reward path could do `dao.updateStepBalance(current + reward)` and credit Steps while touching
  neither `addSteps` nor `adjustStepBalance`. **The #371 test MUST cover this**, or it gives false assurance.
- **`upsert(PlayerProfileEntity)`** (`PlayerProfileDao.kt:13-14`) — full-row write incl. `currentStepBalance`.
  Sole production caller: `PlayerRepositoryImpl.ensureProfileExists` at :106 (`dao.upsert(PlayerProfileEntity())` —
  a default/zeroed row, profile-creation seam). Any other caller constructing an entity with a raised balance
  would credit Steps.
- **`adjustStepBalanceIfSufficient(cost)`** (`PlayerProfileDao.kt:51-54`) — the guarded-deduct SPEND primitive
  (`currentStepBalance - :cost WHERE … >= :cost`). A DEBIT; callers `WorkshopDao`/`PlayerRepositoryImpl.kt:47`.
  Note: the substring `adjustStepBalance(` is **not** contained in `adjustStepBalanceIfSufficient(` (the `I`
  breaks the token boundary), so a `.adjustStepBalance(` call-site scan does not match it — no special-casing
  needed, but the test's KDoc should record this so a future maintainer doesn't "fix" a phantom collision.

## PR-A — CI & supply-chain hardening (#370 + #376)

### #370 — assembleRelease in the PR gate
- Add an `assembleRelease` step to the `build-and-test` job in `.github/workflows/ci.yml`, gated on
  `needs.changes.outputs.code == 'true'` (same as every other heavy step), so R8/shrink runs on every
  **code** PR before a `v*` tag can auto-publish an unexercised release variant to Play internal.
- **Guard handling (do NOT modify the fail-closed guard):** a **separate CI step** — completing
  **before** Gradle is invoked (`local.properties` is loaded once at configuration time,
  `app/build.gradle.kts:31-34`; the `whenReady` guard reads it after) — writes a **throwaway non-blank**
  `play.licenseKey` into `local.properties` (e.g. `play.licenseKey=ci-nonpublishing-placeholder`). Must
  be its own `run:` step, not an inline prefix on the gradle command. This satisfies the `whenReady`
  guard for a non-publishing build while leaving the guard fail-closed for the real `release.yml`
  publish lane. `assembleRelease` needs **no keystore** (the release `signingConfig` is applied only
  `if (keystorePropertiesFile.exists())`, `app/build.gradle.kts:117-119` → produces an **unsigned**
  APK), so no signing secrets are introduced into the PR gate.
- **NDK note:** the release buildType declares `ndk { debugSymbolLevel = "FULL" }`
  (`app/build.gradle.kts:~128`), so `assembleRelease` runs native debug-symbol extraction on the
  SQLCipher `.so` libs. `android-actions/setup-android` provisions the SDK; confirm the R8/symbol step
  completes on the CI runner (an NDK may be pulled on demand) — if it stalls, this is the first thing
  to check. (Refuted as a blocker by the review, but flagged here as the known first-run risk.)
- Placement: its own step in `build-and-test` (not a new job) to reuse the Java/Android/Gradle setup.
- Cost: ~2–4 min added to the code-PR gate. Acceptable; this is the audit's #1 gap.
- **Verify:** `./run-gradle.sh assembleRelease` locally (with a dummy `play.licenseKey` in
  `local.properties`) succeeds — assert **`BUILD SUCCESSFUL` + the `:app:minifyReleaseWithR8` task ran**
  and that some `app/build/outputs/apk/release/*.apk` exists (the exact name is AGP-dependent — unsigned
  builds emit `app-release-unsigned.apk`, signed builds `app-release.apk`; don't hardcode either).

### #376 — gitleaks workflow + finish the GitHub-native posture
- Add `.github/workflows/gitleaks.yml`: SHA-pinned `gitleaks/gitleaks-action` (matching the repo's
  SHA-pin convention; Dependabot maintains pins), running on `pull_request` + `push: [main]`.
- Ship a `.gitleaks.toml` custom config extending the defaults with rules that catch what built-ins
  miss: binary keystores (`*.jks` / `*.keystore` file paths) and `.properties` password lines
  (`storePassword=`, `keyPassword=`, `play.licenseKey=`). Allowlist the committed *dummy*/test values
  and the CI placeholder so the gate isn't self-tripping.
- Enable `secret_scanning_non_provider_patterns` via
  `gh api -X PATCH repos/JonWhiteFang/steps-of-babylon -f 'security_and_analysis[secret_scanning_non_provider_patterns][status]=enabled'`
  (confirm exact call with the user before running). Secret-scanning + push-protection are already on.
- Update `docs/steering/security-model.md` to record the now-active secret-scanning posture (native
  scanning + push protection + gitleaks in CI + the custom keystore/password rules).

## PR-B — AI-safety tripwires (#371 + #372)

### #371 — StepCreditAllowlistTest
- New `app/src/test/java/com/whitefang/stepsofbabylon/architecture/StepCreditAllowlistTest.kt`,
  modeled on `DomainPurityTest` (walks `src/main` source, comment-stripped line scan, dependency-free,
  runs in the existing `testDebugUnitTest` suite). Enforces the **full `currentStepBalance` write
  surface**, not just `adjustStepBalance` — see the "Grounding facts" inventory above (three writers).
  All scans are **comment-stripped** (reuse `DomainPurityTest.stripComments`) and match **qualified
  call sites** (leading `.`) so interface **declarations** in `PlayerProfileDao.kt` are never flagged.
  - **Assertion 1 — `addSteps` callers:** the set of files containing a `.addSteps(` call ==
    exactly `{ClaimSupplyDrop.kt, StepCrossValidator.kt, DailyStepManager.kt}`. (Declarations use
    `fun addSteps(` — no leading dot — so `PlayerRepository.kt`/`PlayerRepositoryImpl.kt` decls aren't
    matched.) Any new caller fails with file+line.
  - **Assertion 2 — positive `.adjustStepBalance(` call sites:** scan for `.adjustStepBalance(`
    (leading dot → excludes the `PlayerProfileDao.kt:40` interface **declaration**) whose argument does
    **not** begin with `-` (i.e. not a negated debit). Allowlist == exactly
    `{PlayerRepositoryImpl.kt (addSteps), DailyStepDao.kt (creditBattleStepsAtomic)}`. The negated
    `PlayerRepositoryImpl.spendSteps` site (`.adjustStepBalance(-amount)`) is excluded by the sign test.
    (`.adjustStepBalanceIfSufficient(` does not contain the `.adjustStepBalance(` token — no collision.)
  - **Assertion 3 — absolute setter + full-row upsert (the landmine paths):**
    - `.updateStepBalance(` has an **empty** production-caller allowlist (zero callers today) — any new
      caller fails. (This is the absolute `SET currentStepBalance = :balance` setter.)
    - `dao.upsert(PlayerProfileEntity` construction is confined to `PlayerRepositoryImpl.kt`
      (`ensureProfileExists`, the profile-creation seam). A `PlayerProfileEntity(` constructed anywhere
      else in `src/main` and passed to `upsert` fails.
  - **Assertion 4 (belt-and-braces) — DAO write-surface pin:** any `@Query` in `PlayerProfileDao.kt`
    whose SQL writes `currentStepBalance` outside the sanctioned set
    `{adjustStepBalance, adjustStepBalanceIfSufficient, updateStepBalance}` fails — so a *new* DAO
    method writing the balance is caught even before a caller exists. (Scan the DAO file's `@Query`
    strings for `SET currentStepBalance` / `currentStepBalance =` and pin the method set.)
  - **Negative fixture (baked, not manual):** isolate the pure line-matching predicate from the file
    walk and unit-test it directly against a synthetic in-memory source containing a rogue `.addSteps(`
    and a positive `.adjustStepBalance(` — assert the matcher reports both. This regression-guards the
    scanner against silently rotting into a no-op (the #228 failure mode: a guard that never fails is
    indistinguishable from one that scans the wrong tree). Replaces the earlier "edit-and-revert"
    manual step.
  - Rationale: this locks the true invariant boundary — every `currentStepBalance`-raising primitive.
    The battle path (`Simulation.creditSteps` → `SimulationEvent` → `AwardBattleSteps` →
    `creditBattleStepsAtomic`) is the sanctioned bounded exception (ADR-0003) and terminates at
    `creditBattleStepsAtomic`, on the allowlist. A new reward path crediting Steps via *any* of the
    three writers → the test fails.
  - KDoc cites `CLAUDE.md` Key Domain Concepts + ADR-0003, mirroring `DomainPurityTest`'s doc style,
    and records why `adjustStepBalanceIfSufficient` is not a false-collision.
- **Verify:** `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCredit*'` passes at HEAD (all four
  assertions green); the baked negative fixture proves the matcher goes red on a planted credit site.

### #372 — concurrency-reviewer mandatory
- **Trigger scope (single source of truth — must be quoted identically everywhere it appears):**
  a diff touching **any** of: `presentation/battle/engine/**`, `presentation/battle/effects/**`,
  `data/local/*Dao.kt`, `data/repository/PlayerRepositoryImpl`, the domain spend/claim use cases, or
  "anything that structurally mutates a shared engine collection or moves a currency balance". This
  matches `.claude/agents/concurrency-reviewer.md` verbatim — the currency-moving repo/use-case surface
  is part of the trigger (it is where the #371 credit paths live), and must NOT be narrowed to
  "engine/DAO only" anywhere in the spec, CLAUDE.md, or the doc-sync.
- **Primary gate (chosen enforcement — deterministic + protocol):**
  - **Deterministic trigger:** extend the existing `PreToolUse` Edit|Write hook
    (`.claude/hooks/guard-sensitive-edits.sh`, which already fires `permissionDecision:"ask"` on path
    globs such as `app/schemas/*`) to also `ask` on edits under the trigger scope above. This is the
    one build-independent, always-on lever the repo already has — it fires regardless of ultracode
    state. Doc-sync then includes `.claude/hooks/guard-sensitive-edits.sh` (and `settings.json` if the
    glob list lives there).
  - **Protocol:** encode the mandatory `concurrency-reviewer` fan-out lane in `CLAUDE.md`'s Adversarial
    Review Gate section for the same scope. Explicitly acknowledge that the CLAUDE.md protocol form is
    silently disabled when ultracode is OFF (and invisible to non-Claude agents) — which is exactly why
    the hook is the primary deterministic trigger, not the prose.
- **Build tripwire (best-effort, timeboxed):** attempt a detekt custom rule flagging **nested
  lock acquisition** (a `synchronized`/`ReentrantLock` acquired while another is held) in the engine.
  - **Timebox / decision criterion:** the repo has **no** detekt custom-rule module today
    (`app/build.gradle.kts:11` applies detekt as a bare `alias(libs.plugins.detekt)`; no `detektPlugins`
    dependency; detekt is `2.0.0-alpha.5`). Authoring a `RuleSetProvider` requires a new
    Gradle module + a `detektPlugins` dependency + a `libs.versions.toml` entry. **Take the fallback
    unless a working `RuleSetProvider` on `2.0.0-alpha.5` is green within one focused implementation
    session** (i.e. the alpha `RuleSetProvider`/`Rule` API resolves and a self-test rule fires). If the
    detekt path IS taken, its doc footprint expands: `docs/steering/structure.md` (new module) +
    `docs/steering/source-files.md` (rule file) + `gradle/libs.versions.toml` + `docs/steering/tech.md`
    (new dependency) — add these to PR-B's doc-sync. **Default expectation: fallback + a deferred
    follow-up issue for the detekt rule.**
  - **Fallback (default) — a `DomainPurityTest`-style source-scan test.** It must be **comment-stripped**
    (reuse `DomainPurityTest.stripComments`) and **scoped to an explicit collaborator file allowlist**
    `{UWController, CombatResolver, BuffTickers, BattleRenderer}`, **explicitly excluding `GameEngine.kt`**
    (the sole sanctioned `entitiesLock` owner) **and `BattleHosts.kt`** (its `synchronized(entitiesLock)`
    reference at :45 is inside a KDoc — comment-stripping alone also handles this, but exclude it by name
    for clarity). Fail if a listed collaborator declares its own monitor: a `synchronized(` block,
    `ReentrantLock`, or a `= Any()`/`= Object()` monitor field (the "collaborators hold no monitor of
    their own" invariant — `CLAUDE.md` Battle Renderer note). Collaborators are verified clean at HEAD,
    so the test is green today. Report which path we landed on in the PR.

## PR-C — Crash observability (#374 + #380)

### #374 — crash-breadcrumb report exit path
- In `MainActivity.kt` (`LaunchedEffect` at ~277-283): the crash snackbar gains a **"Report" action**
  (add `actionLabel` to the existing `showSnackbar(crashNotice)` call).
  - On `SnackbarResult.ActionPerformed` → fire an `ACTION_SENDTO` `mailto:jonwhitefang@gmail.com`
    intent pre-filled with `EXTRA_SUBJECT` + `EXTRA_TEXT` containing `exceptionClass`, `message`,
    `stackPreview`, and app metadata (`versionName`/`versionCode`, Android release). Guarded-intent
    idiom (resolve before launch) so a device with no mail client is a safe no-op.
  - **REQUIRED — package-visibility `<queries>`:** targetSdk is 36, and the manifest has **no
    `<queries>` element** (verified). Under package-visibility filtering, `ACTION_SENDTO`/`mailto:`
    resolves to `null` **even on devices that have a mail client** — so the guarded-intent idiom would
    make Report a **permanent silent no-op**, defeating the whole feature. The `openPrivacyPolicy`
    precedent works only because `ACTION_VIEW` + `http(s)` is auto-visible; **`SENDTO`/`mailto` is
    not**. Add to `AndroidManifest.xml`:
    ```xml
    <queries>
      <intent>
        <action android:name="android.intent.action.SENDTO" />
        <data android:scheme="mailto" />
      </intent>
    </queries>
    ```
    This is an explicit PR-C task.
  - **`clear()` timing (accuracy fix):** the current code already calls `clear()` *after* the awaited
    `showSnackbar(crashNotice)` returns (`MainActivity.kt:280` then `:281`) — it is **not** called
    before. The change is only to add the `actionLabel` and, in the `ActionPerformed` branch, fire the
    intent **before** the existing `clear()`, so a Report tap sends the breadcrumb before it's cleared.
    (The audit's "immediate clear()" phrasing mischaracterized the current code; keep `clear()` after
    the awaited call, don't move it earlier.)
  - New string resources for the snackbar action label + email subject/body scaffold (localizable —
    the repo promotes `HardcodedText` to a lint error).
- Update the `#190 REL-1` inline comment (currently says "there is no in-app report channel to wire
  an action to" — that becomes false).
- **Testable seam:** extract the report-intent construction + guarded launch into a **top-level
  function** (mirror the existing top-level `openPrivacyPolicy(context)` / `requestBatteryExemption`),
  taking the breadcrumb fields as params and returning/launching the `ACTION_SENDTO` intent. The
  `LaunchedEffect` calls it in the `ActionPerformed` branch. This is the automatable seam (the
  `createComposeRule()` JVM lane launches ui-test-manifest's own host activity, never `MainActivity`,
  so the `LaunchedEffect` body itself is not drivable there — see Verification).

### #380 — post-release monitoring runbook
- Add a **"Post-release monitoring"** section to `docs/release/release-checklist.md` (currently ends
  at "Build Outputs"): 24h/72h Play Vitals cadence, crash-rate / ANR-rate thresholds, a roll-back
  trigger, and a cross-reference to the monitoring guidance in `plan-31-walkthrough.md`. Note that
  `mapping.txt` upload is already automated in `release.yml`.

## Cross-cutting

- **Doc sync (PR Task-List Convention — runs BEFORE the STATE/RUN_LOG update, in every PR):**
  - PR-A: `CHANGELOG.md`; `docs/steering/security-model.md` (#376 posture); STATE/RUN_LOG.
  - PR-B: `CLAUDE.md` (Testing headline count — **+3 for `StepCreditAllowlistTest`'s four `@Test`s
    counted as its methods; +1 more if the #372 fallback source-scan test lands**; Review-Gate wiring
    for #372); `docs/steering/source-files.md` (new test file(s)); `.claude/hooks/guard-sensitive-edits.sh`
    (+ `settings.json` if the glob list lives there — the #372 hook trigger); `CHANGELOG.md`;
    STATE/RUN_LOG. ADR for the #372 enforcement decision (hook + doc-protocol; detekt-rule deferred or
    landed). **If the detekt-rule path is taken:** also `docs/steering/structure.md` (new module),
    `gradle/libs.versions.toml` + `docs/steering/tech.md` (new dependency).
  - PR-C: `AndroidManifest.xml` (the `<queries>` element); `CHANGELOG.md`; STATE/RUN_LOG. **If the
    extracted-function Robolectric test lands** (it should — see #374 Testable seam), also bump the
    `CLAUDE.md` headline count +1 and add the test to `docs/steering/source-files.md`.
  - Tick tracker **#389** checkboxes as each PR merges.
- **Adversarial Review Gate:** each PR's implementation plan passes the mandatory review gate before
  implementation. PR-B's own diff (a new test + `CLAUDE.md`/hook/doc edits) does **not** touch the
  `concurrency-reviewer` trigger surface, so the new lane will not fire on PR-B itself; the wiring is
  validated by inspection/review, and the first real trigger will be the next engine/DAO/economy-
  touching PR. (Optionally dry-run the subagent once against a sample engine diff to confirm it loads.)
- **Sequencing:** PR-A → PR-B → PR-C. Independent branches off `main`; this order front-loads the
  only `severity:major` finding (#370).

## Out of scope (explicit)

- All Phase-2/3/4 findings (tracker #389).
- Modifying the fail-closed `play.licenseKey` guard **logic** (we only feed it a dummy value in CI).
- A third-party crash/analytics SDK (audit-confirmed N/A — offline-first posture; Play Vitals is the
  backstop; the gap is the local breadcrumb's *exit path* + the *runbook*, not a network SDK).
- Any change to the `release.yml` publish lane behavior (it already builds the real signed AAB).

## Verification summary

| PR | Command / check |
|---|---|
| A #370 | `./run-gradle.sh assembleRelease` (dummy `play.licenseKey`): `BUILD SUCCESSFUL` + `:app:minifyReleaseWithR8` ran + some `app/build/outputs/apk/release/*.apk` exists |
| A #376 | gitleaks action runs green on a clean tree; a planted `storePassword=` / `*.jks` test blob is caught locally; `gh api … secret-scanning/alerts` reachable; `secret_scanning_non_provider_patterns` enabled |
| B #371 | `./run-gradle.sh :app:testDebugUnitTest --tests '*StepCredit*'` green at HEAD (all four assertions); baked negative-fixture test proves the matcher goes red on a planted credit site |
| B #372 | hook `ask`s on an engine/effects/DAO/economy edit; CLAUDE.md wiring present with the verbatim trigger scope; `./run-gradle.sh :app:detekt` green (rule active) OR the comment-stripped, collaborator-scoped fallback source-scan test green |
| C #374 | JVM build green; `<queries>` present in manifest; extracted intent-builder Robolectric test asserts `action==ACTION_SENDTO`, `mailto` data, subject/body extras contain breadcrumb fields, no-resolver context = safe no-op; **manual** end-to-end (crash → relaunch → tap Report → composer opens on a device with a mail client) |
| C #380 | doc renders; cross-refs resolve |
