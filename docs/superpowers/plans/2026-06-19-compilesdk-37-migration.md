# Plan — compileSdk 36 → 37 migration + dependency unblock

**Spec:** `docs/superpowers/specs/2026-06-19-compilesdk-37-migration.md` (adversarially reviewed)
**Branch:** `feat/compilesdk-37-migration` · **Status:** plan (pre-review)

Mechanical build-config change; no production Kotlin/Java touched. Tasks are ordered so the build is
verified before any doc edits, per the PR Task-List Convention (doc-sync is steps near the end).

## Task 1 — Raise compileSdk to 37 (3 modules)
- `app/build.gradle.kts:33` — `compileSdk = 36` → `compileSdk = 37`
- `baselineprofile/build.gradle.kts:9` — `compileSdk = 36` → `compileSdk = 37`
- `macrobenchmark/build.gradle.kts:8` — `compileSdk = 36` → `compileSdk = 37`
- Leave `targetSdk = 36` and `minSdk = 34` in all three UNCHANGED.

## Task 2 — Unblock the gated dependency pins (catalog)
In `gradle/libs.versions.toml`:
- L8 `lifecycle = "2.10.0"` → `lifecycle = "2.11.0"`
- L13 `coreKtx = "1.17.0"` → `coreKtx = "1.19.0"`
- L15 `sqliteKtx = "2.4.0"` → `sqliteKtx = "2.6.2"`
- Do NOT touch `healthConnect = "1.1.0"` (L22) — 1.2.x is alpha-only, out of scope.

## Task 3 — Rewrite the Health-Connect rationale comment (catalog L16-21)
Replace the trailing sentence (verbatim per spec amendment): drop "Revisit a 1.2.x beta/stable post-launch
when compileSdk moves to 37." → "compileSdk is now 37, so that half of the gate is cleared; HC 1.2.x stays
out until it reaches **beta/stable** — alpha AndroidX carries no API-stability guarantee and HC is
load-bearing." Repoint the L18-19 "1.2.x alphas now require SDK 37 / compileSdkExtension 19" so it reads as
historical context (what the gate WAS), not a live blocker.

## Task 4 — Build verification (local, BEFORE doc edits)
1. `./run-gradle.sh :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → must be BUILD SUCCESSFUL,
   1126 JVM tests pass, zero new lint findings.
2. `./run-gradle.sh :baselineprofile:assemble :macrobenchmark:assemble` → benchmark modules type-check at 37
   (mirrors the CI step that the local :app build skips).
3. **Release-variant / R8** (spec amendment): `./run-gradle.sh :app:assembleRelease` (or the minify path) —
   verify R8 + compileSdk 37 don't interact badly. If signing config blocks it locally, document that and
   rely on the post-merge tag's release lane; note the limitation.
4. **Resolved-version regression check** (manual, spec amendment): `./run-gradle.sh -q :app:dependencies
   --configuration releaseRuntimeClasspath | grep -E 'core-ktx|lifecycle|sqlite'` **[plan-review: broadened
   grep — was core-ktx-only]** → confirm core-ktx resolves to **1.19.0**, lifecycle to **2.11.0**, sqlite to
   **2.6.2** (no clamp). Record the result in the RUN_LOG. (Already empirically confirmed on the scratch
   branch: core-ktx → 1.19.0, lifecycle → 2.11.0.)

## Task 5 — Doc-sync (only after the build is green)
Per the spec's amended doc-sync inventory:
- `gradle/libs.versions.toml` — HC comment (Task 3 already covers it).
- `CLAUDE.md:131` — `**Target/Compile SDK:** 36` → `**Compile SDK:** 37 · **Target SDK:** 36` (keep Min SDK 34).
- `README.md:30` — `- Android SDK 36 (compile/target), min SDK 34 (Android 14)` →
  `- compileSdk 37 / targetSdk 36, min SDK 34 (Android 14)`.
- `docs/steering/tech.md` — **[plan-review correction]** the version cells are on THREE different lines, not
  all "L34":
  - **L6** (Core-section prose) — `Target & Compile SDK: 36` → `Compile SDK 37 / Target SDK 36`.
  - **L27** — `| Lifecycle | 2.10.0 |` → `2.11.0`.
  - **L32** (HC row) — blocker→cleared framing ("compileSdk now 37; HC 1.2.x still alpha-only").
  - **L33** — `| SQLite KTX | 2.4.0 |` → `2.6.2`.
  - **L34** — `| Core KTX | 1.17.0 |` → `1.19.0`.
  - **L35** (Activity Compose) — parenthetical `(transitively resolves core-ktx to 1.18.0)` → `(direct
    core-ktx pin 1.19.0 now governs)` or drop. Edit by ROW NAME, not blind line number.
- `docs/plans/plan-32-ci.md` — **[plan-review correction]** TWO lines, both SURGICAL (don't touch
  targetSdk/minSdk/API-34):
  - **L33** = `| SDK | compileSdk/targetSdk 36, **minSdk 34** | emulator floor **API 34**; install platform
    36 via setup-android |` → split `compileSdk/targetSdk 36` into `compileSdk 37 / targetSdk 36` and change
    `install platform 36` → `install platform 37`. Leave `minSdk 34` + `API 34` untouched. **NOT a
    replace_all of "36"** (that would wrongly bump targetSdk + API).
  - **L53** = `... setup-android (ensure platform-36 + build-tools) ...` → `platform-37`.
- `CHANGELOG.md` — new `[Unreleased]` entry (migration + unblock + closes #199 + HC-still-alpha-gated note).
- DO NOT edit frozen/historical artifacts: prior RUN_LOG entries, `docs/archive/**`,
  `docs/external-reviews/**` **[plan-review: restored — matches CLAUDE.md frozen list]**,
  `docs/reviews/2026-06-17-*` (dated), shipped release-notes, shipped CHANGELOG `[1.0.x]` sections,
  `docs/superpowers/plans/2026-06-16-perf-battery-gate-g.md` (shipped). Their "compile/target 36" lines are
  historical-at-authoring and stay.

## Task 6 — STATE.md + RUN_LOG.md + ADR
- STATE.md: new CURRENT objective on top, demote prior; note the compileSdk-36 fragile-zone entry (if any)
  is now lifted; record the local platform-37 install command for reproducibility.
- RUN_LOG.md: new top entry (goal, what changed, the 4 verification results incl. release-assemble +
  resolved-version check, doc-sync list, what remains).
- **ADR** (spec says warranted — this reverses a documented deliberate pin): new
  `docs/agent/DECISIONS/ADR-00NN-compilesdk-37-migration.md` from the template — context (the 36 pin + why
  it was held), decision (raise to 37, targetSdk stays 36, HC stays 1.1.0), consequences (unblocks
  core-ktx/lifecycle/sqlite; HC still gated on beta/stable; CI auto-provisions). Find the next ADR number.

## Task 7 — Commit + PR + monitor + merge
- Commit on `feat/compilesdk-37-migration` (spec already committed there).
- Push, open PR (closes #199; references the #288 unblock).
- Monitor BOTH CI checks (PR gate + instrumented connected lane) — the PR-gate run is the authoritative
  proof CI auto-provisions platform 37. If it fails on platform-missing, apply the spec's setup-android
  fallback.
- Squash-merge + delete branch once green. Then #288 (Dependabot all-gradle) will rebase; the compileSdk
  bumps it carried are now satisfied.

## Verification summary (what "done" means)
- Local: testDebugUnitTest (1126) + lintDebug (0 new) + assembleDebug + benchmark assemble +
  assembleRelease all green; resolved core-ktx 1.19.0 / lifecycle 2.11.0 confirmed.
- CI: PR gate + connected lane green on the PR.
- Docs: every live SDK-36/compile-target assertion synced; ADR recorded; #199 closed.
