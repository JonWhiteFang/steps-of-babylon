# ADR-0041: OSS-attribution surface via a static build-generated NOTICE asset (not the oss-licenses plugin)

## Context
- The shipped proprietary AAB bundles Apache-2.0 / permissive third-party libraries (Compose, AndroidX,
  Room, Hilt, WorkManager, Health Connect, Guava, SQLCipher, Play Billing/Ads/UMP, Coroutines) but exposed
  **no in-app notices / NOTICE file**. Apache-2.0 **§4(d)** attaches the attribution obligation to the
  redistributed **binary**, so the shipped app must preserve the notices. Finding **#377 (`depmgmt-1`)**
  (Med) in the 2026-07-02 tooling-gap assessment.
- Two implementation options were on the table: **(1)** Google's `com.google.android.gms.oss-licenses-plugin`
  + `OssLicensesMenuActivity` (auto-derives notices from the resolved graph), or **(2)** a static
  build-time-generated NOTICE text asset surfaced read-only in the app.
- The developer initially chose option (1). The **Adversarial Review Gate** on the Phase-4 plan then
  surfaced a confirmed defect (Survivor 5): the current `play-services-oss-licenses:17.5.1` no longer ships
  a lightweight activity. Since **17.4.0** the licenses activity was rebuilt on **AndroidX Compose +
  Navigation3**, so 17.5.1's POM pulls — **transitively into the shipped AAB** — a new `androidx.navigation3`
  family (`navigation3-runtime`/`-ui` 1.1.0) **and ALPHA AndroidX Compose** (`material3:1.5.0-alpha17`,
  `lifecycle-*-compose:2.11.0-alpha03`). Verified against the published `17.5.1` POM vs the last pre-v2
  `17.3.0` POM.

## Decision
Implement #377 as **option (2): a static build-generated NOTICE asset**, surfaced read-only in `HelpScreen`.

- **Generator:** `tools/generate_oss_notices.py` (joins the existing `tools/` re-runnable-script convention:
  `generate_sfx.py`, `render_play_store_icon.py`) — **NOT a Gradle task in `app/build.gradle.kts`**. This
  keeps the fragile build file (release signing / AdMob wiring / #124 fail-closed guard / ndk / Kover) and
  the #124 release-task-graph reasoning **completely untouched** — a strict improvement over the plan's
  original "task at EOF of build.gradle.kts" idea.
- The generator derives the **coordinate list** (group:name + version) from `gradle/libs.versions.toml` (the
  single source of versions), so it can't silently drift on a version bump; the **license bodies** are a
  hand-curated map in the script (the catalog carries no SPDX id / license text).
- **Output:** `app/src/main/res/raw/oss_notices.txt` (committed). `HelpScreen` reads it once via
  `LocalContext.current.resources.openRawResource(R.raw.oss_notices).bufferedReader().use { readText() }`
  inside a `remember {}`, and renders it read-only through the existing `HelpSection(title, body)` — a new
  "Open-source notices" section, **no new nav route** (Help already exists; `DeepLinkRoutingTest`'s 13-route
  pin is untouched).
- Coverage is the **full direct `implementation` (shipping) set** — including the ones the plan's first
  short list omitted (`user-messaging-platform`, `profileinstaller`, the AndroidX family) — plus the
  forced-transitive `guava` (a security constraint that still ships). SQLCipher is cited under its **exact
  BSD-3-Clause + Zetetic clause**, not a loose "BSD-style".

## Alternatives considered
- **A: `oss-licenses-plugin` + `OssLicensesMenuActivity` (17.5.1)** — **REJECTED.** The 17.5.1 v2 activity
  drags **alpha AndroidX Compose + a new Navigation3 family** into the AAB (POM-verified), violating this
  project's **no-alpha-AndroidX-in-the-AAB discipline** (the rule #33 enforced when moving Health Connect off
  alpha). Spending that transitive weight + verification-metadata churn on a licenses screen isn't justified.
- **B: pin the legacy pre-v2 `play-services-oss-licenses:17.3.0`** — **REJECTED.** Avoids the alpha/Nav3
  weight but pins a deliberately-old, now-deprecated dependency Google may drop, and loses the v2-only
  `setActivityTitle` API. Deliberately shipping a stale, deprecated dep is worse than owning a static asset.
- **C: a third-party license-scanner Gradle plugin** — **REJECTED.** Reintroduces exactly the
  dependency-weight problem option (2) exists to avoid.
- **D: a Gradle task inside `app/build.gradle.kts`** (the plan's first static-asset shape) — **superseded**
  by the `tools/*.py` script: same output, but keeps the fragile build file and the #124 task-graph out of
  scope entirely.

## Consequences
- **Positive:** satisfies the Apache-2.0 §4(d) obligation on the shipped binary with **zero new runtime
  dependencies** — no alpha AndroidX, no Navigation3, no AAB weight, no new nav route, no fragile-zone edit,
  no #124-guard interaction. R8 keeps the asset (verified: the shrunk release APK retains `raw/oss_notices`
  = resource `0x7f0e0004`; a static `R.raw` id is a keep root — no proguard rule needed).
- **Negative / tradeoffs:** the notice is **static** — it can drift from the resolved graph. Mitigation:
  regeneration is a committed, re-runnable script + a `release-checklist.md` "regenerate if deps changed"
  item. Regeneration catches **added/removed deps, NOT license-text changes** (the bodies are hand-curated) —
  honestly scoped. Transitive-only deps aren't individually listed (the §4(d) obligation is met by covering
  the direct redistributed set; guava is listed as the one forced transitive that ships).
- **Verification not machine-catchable:** that the section actually *renders* is an on-device check (no
  Compose-launch on the JVM lane for this screen path) — promoted to a **REQUIRED pre-tag** item in
  `release-checklist.md` (S7).

## Links
- Plan + full review record: `docs/superpowers/plans/2026-07-03-phase4-release-ops-tooling.md` (Adversarial
  Review Gate: 19 raised / 10 survived / 9 refuted; Survivor 5 drove this decision; a lean re-review of the
  static-asset section passed with 3 minor fixes folded in).
- Files: `tools/generate_oss_notices.py`, `app/src/main/res/raw/oss_notices.txt`,
  `presentation/help/HelpScreen.kt`, `res/values/strings.xml` (`help_oss_title`).
- Related ADRs: ADR-0025 (#33 no-alpha-AndroidX-in-the-AAB precedent context), ADR-0018 (CI/release).
  Issue: #377 (`depmgmt-1`), tracker #389 (Phase 4).
