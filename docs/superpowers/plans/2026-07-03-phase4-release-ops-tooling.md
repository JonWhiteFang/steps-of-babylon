# Phase-4 Release & Operations Tooling — Implementation Plan

**Date:** 2026-07-03 · **Tracker:** #389 (Phase 4) · **HEAD:** v1.0.12 / vc 28 / schema v12
**Findings:** #379 (`releaseops-1`), #383 (`releaseops-2`), #377 (`depmgmt-1`), #385 (`perf-1`, tracking-only)
**Status:** REVIEWED — passed the Adversarial Review Gate (see below); amendments applied.

## Adversarial Review Gate outcome (2026-07-03, ultracode ON)

5-dimension multi-agent fan-out (code-grounding · CI/git · framework/API · fragile-zone/invariants ·
scope/consistency) → adversarial refute-verify per finding → synthesis. **19 raised · 10 survived · 9
refuted.** All survivors applied:
- **S1+S2+S3 (major/major/minor, cicd-git):** PR-1's git-lookup rewritten — topology-independent
  `git tag -l 'v*' --sort=-v:refname` (not `describe "${REF}^"`), `|| true`-guarded tag lookup so the
  first-release skip is reachable, and pipefail-safe guarded-assignment + loud `::error::` parse idiom.
- **S4/S5/S6/S8/S9/S10 (framework-API + fragile + scope):** all mooted or reframed by the **#377 approach
  change** — S5 (major/confirmed: `play-services-oss-licenses:17.5.1` v2 activity pulls **alpha AndroidX
  Compose + Navigation3** into the AAB, violating the no-alpha-AAB discipline) **drove a developer decision
  to switch #377 to the static NOTICE asset**, which eliminates the plugin/library entirely (S4 v2-package,
  S6 AGP-support, S8 task-name, S9 CLAUDE.md-fragile-note-target, S10 resolveActivity-waffle all no longer
  apply).
- **S7 (major→minor, fragile):** reframed — no proguard keep rule / local `bundleRelease` needed; the
  PR-gate `assembleRelease` (`ci.yml:120`) already exercises R8+shrink, and the on-device render check is
  promoted to a REQUIRED release-checklist item.

The **new static-asset #377 section is a materially new artifact** and gets a lean single-dimension
re-review before PR-3 implementation (it was not part of the multi-agent pass above).

## Scope decisions (developer-confirmed 2026-07-03)

- **#377** → **Static build-time NOTICE asset surfaced read-only in `HelpScreen`** (REVISED from the
  oss-licenses-plugin route after review Survivor 5 — see PR-3). Zero new runtime dependencies; satisfies
  Apache-2.0 §4(d) without dragging alpha AndroidX Compose / Navigation3 into the AAB.
- **#385** → **Defer + keep tracking.** Numbers need real hardware; the issue says "file to track, not to
  rush." No fragile-zone `benchmark` build-type change this pass; refresh the tracking note only.
- **Packaging** → 3 in-repo now (#379, #383, #377) as stacked PRs; #385 note folded into PR-3.

## Non-goals

- No `benchmark` build type on `:app` (fragile-zone change, no payoff without a device — #385 deferred).
- No `release.yml` `userFraction`/`status: inProgress` change (#383's production half is deferred until
  production lands — doc-only now).
- No new mechanics, no schema change, no economy/concurrency surface touched → **no `concurrency-reviewer`
  lane required** (none of `presentation/battle/engine/**`, effects, DAOs, `PlayerRepositoryImpl`, or the
  spend/claim use cases are touched).

---

## PR-1 — #379: versionCode-collision fail-fast guard (`release.yml`)

### What & why
The release lane (`.github/workflows/release.yml:45-56`) has a **Tag ↔ versionName** guard but **no check
that `versionCode` was bumped** past the last published build. A forgotten `versionCode` bump today wastes a
full signed run and fails only at Play upload (the documented v13 rejection). Play is the backstop — this is
a **fail-fast convenience**, not a safety gap.

### Change
Add one bash step, **immediately after** the existing "Tag ↔ versionName consistency guard" step
(`release.yml:45`), symmetric with it: `if: github.event_name == 'push'` (skip on manual dispatch — no tag).

**Algorithm (fail-fast, non-blocking-on-absence) — REVISED after review (S1/S2/S3):**
Use a **topology-independent, semver-sorted tag lookup** (NOT `git describe ... "${REF}^"`). The review
confirmed `git describe --tags --abbrev=0 "${REF}^"` (a) aborts the whole step under `set -euo pipefail`
when no prior tag exists (the first-release skip becomes unreachable — S1), and (b) follows only the
**first parent**, so a prior release tag living on a merged-in side branch is missed (S3). The tag-list form
avoids both and orders by version correctly:

1. **Parse the committed `versionCode`** from `app/build.gradle.kts:49` with a **pipefail-safe guarded
   assignment**, then a loud emptiness test (S2 — a parse miss must emit `::error::`, never a bare exit):
   ```bash
   cur=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+' | head -1 || true)
   [ -n "$cur" ] || { echo "::error::Could not parse current versionCode from app/build.gradle.kts"; exit 1; }
   ```
2. **Find the previous release tag** — highest-versioned `v*` tag that is NOT the current one
   (topology-independent; `|| true`-safe so "none" is empty, not an abort):
   ```bash
   prev_tag=$(git tag -l 'v*' --sort=-v:refname | grep -vxF "${GITHUB_REF_NAME}" | head -1 || true)
   if [ -z "$prev_tag" ]; then
     echo "No prior v* tag — first release, skipping versionCode-collision guard"; exit 0
   fi
   ```
   (`fetch-depth: 0` at `release.yml:30` guarantees all tags are present. `--sort=-v:refname` gives semver
   ordering; assumes tags are pushed in increasing version order — documented as the guard's one assumption.)
3. **Parse the previous tag's `versionCode`** with the same guarded/loud idiom:
   ```bash
   prev=$(git show "${prev_tag}:app/build.gradle.kts" | grep -oE 'versionCode = [0-9]+' | grep -oE '[0-9]+' | head -1 || true)
   [ -n "$prev" ] || { echo "::error::Could not parse versionCode from tag ${prev_tag}"; exit 1; }
   ```
4. **Assert** `cur > prev` numerically; on `<=`, `::error::` + `exit 1` naming the required bump.

**Robustness requirements (baked into the step):**
- `set -euo pipefail` (matches the existing guards) — but every command substitution that may legitimately
  return non-zero (the tag lookup, a grep that may miss) is `|| true`-guarded so pipefail can't turn an
  expected-empty into a silent step abort. `|| true` is scoped to those lookups **only** — never blanket.
- Every parse is a guarded assignment followed by an explicit `[ -n "$x" ]` emptiness test that emits
  `::error::` + exit 1. A parse miss is LOUD (S2). **Do NOT copy `release.yml:50`'s inline
  `$(grep ... | sed ...)` as the template — it is itself pipefail-vulnerable on a parse miss.**
- Optional integer-shape assert `[[ "$cur" =~ ^[0-9]+$ ]]` before the `-gt` comparison.
- Numeric comparison via `[ "$cur" -gt "$prev" ]` (arithmetic, not lexical).

### Files
- `.github/workflows/release.yml` — new step after line 56.

### Verification
- **Cannot run the release lane in CI from a PR** (it's `on: push: tags: ['v*']`). Verify by:
  - `bash -n` / shellcheck-style read-through of the step (syntax).
  - Locally simulate the core logic against the real repo: current committed vc = 28; previous `v*` tag
    (`v1.0.11`) committed vc = 27 → 28 > 27 passes. Craft a negative simulation (pretend current = 27) → fails.
    (Run the extracted comparison logic in a scratch shell against `git show v1.0.11:app/build.gradle.kts`.)
- No unit-test surface (workflow YAML).

### Docs to sync (PR-1)
- `docs/release/release-checklist.md` — the per-release "versionCode bumped" line
  (`release-checklist.md:23`) gains a note that the release lane now fail-fast-guards this.
- `CHANGELOG.md` — `[Unreleased]` entry for #379.
- `docs/agent/STATE.md` (current objective → Phase-4) + `docs/agent/RUN_LOG.md` (append).

### Task list (PR-1)
1. Branch `tooling/phase4-versioncode-guard` off `main`.
2. Add the guard step to `release.yml`.
3. Local logic simulation (positive + negative) against real tags.
4. **Sync current-state docs** (`release-checklist.md` note; CHANGELOG `[Unreleased]`).
5. **Update STATE.md + append RUN_LOG.md.**
6. Commit (with `Closes #379`), open PR, merge when green.

---

## PR-2 — #383: document internal-only / manual-production rollout+rollback story

### What & why
The upload step (`release.yml:153-162`) is `tracks: internal` + `status: completed` at 100% — no
`userFraction`, no scripted halt/rollback. Fine for internal today, but the automation **stops at internal**:
every production rollout/rollback becomes manual the moment production is reached. This is a **doc gap** now;
the code change (`userFraction`/`status: inProgress` dispatch input) is explicitly **deferred until
production lands** (per the issue).

### Change
Add a **"Rollout & rollback"** subsection to `docs/release/release-checklist.md` (natural home: after the
existing "Post-release monitoring (#380)" section at `release-checklist.md:59-75`, which already has the
rollback *trigger* — this documents the rollout *mechanism* the trigger acts on). Content:
- **Today (internal track):** the lane uploads `status: completed` at 100% to internal (cite
  `release.yml` tracks/status). There is no staged rollout or scripted rollback at internal — the track is
  small and internal-only; rollback = halt promotion + ship a fixed build with a new `versionCode` (this is
  the same lever #380's rollback trigger already names — cross-link, don't duplicate).
- **Manual production story (until automated):** production promotion is a **manual Play Console action**
  (Console → Production → create release from the internal artifact / staged %). Rollback in production =
  halt the staged rollout in Console + resume the previous release; there is no scripted path.
- **When production lands (deferred follow-up, pointer only):** add a `status: inProgress` + `userFraction`
  `workflow_dispatch` input to `release.yml` for a staged rollout. Reference #383. Do **not** implement now.

### Files
- `docs/release/release-checklist.md` — new "Rollout & rollback" subsection.

### Verification
- Doc-only: prose review; markdown renders; internal links resolve (#380 cross-link, `release.yml`
  path/line references accurate at HEAD).

### Docs to sync (PR-2)
- `CHANGELOG.md` — `[Unreleased]` entry for #383.
- STATE.md + RUN_LOG.md.

### Task list (PR-2)
1. **Rebase** `tooling/phase4-rollout-doc` onto updated `main` (after PR-1 merges) — avoids the newest-first
   doc-head collision (CHANGELOG/STATE/RUN_LOG) per the sequential-merge rule.
2. Add the "Rollout & rollback" subsection.
3. **Sync current-state docs** (CHANGELOG `[Unreleased]`).
4. **Update STATE.md + append RUN_LOG.md.**
5. Commit (with `Closes #383`), open PR, merge when green.

---

## PR-3 — #377: OSS-attribution surface (STATIC NOTICE ASSET → Help) + #385 tracking-note refresh

> **APPROACH REVISED after the Adversarial Review Gate (developer-confirmed 2026-07-03).** The original
> plan chose the `oss-licenses-plugin` + `OssLicensesMenuActivity` route. The review's **Survivor 5
> (major/confirmed)** proved that premise false: since library 17.4.0 the licenses activity was rebuilt on
> AndroidX Compose + **Navigation3**, so the current `play-services-oss-licenses:17.5.1` drags **ALPHA
> AndroidX Compose** (`material3:1.5.0-alpha17`, `lifecycle-*-compose:2.11.0-alpha03`) **and a new
> `androidx.navigation3` family** transitively **into the shipped AAB** — verified against the published
> `17.5.1` POM. That violates this project's **no-alpha-AndroidX-in-the-AAB discipline** (the rule #33
> enforced when moving Health Connect off alpha). The developer therefore chose the **static NOTICE asset**
> option. This section is the rewrite; it touches **no** `build.gradle.kts`, **no** new runtime dependency,
> **no** nav route, and **no** fragile zone — which also moots review survivors S4/S6/S8/S9/S10 (all
> plugin-specific) and reframes S7 (below).

### What & why (#377)
The proprietary AAB bundles Apache-2.0 / permissive libraries (Guava, Play Services, Health Connect,
Billing) with **no in-app notices / NOTICE file**. Apache-2.0 §4(d) attaches the attribution obligation to
the redistributed **binary**; the shipped app preserves none. **Med-severity compliance gap on the shipped
artifact.**

A **static build-time-generated NOTICE text asset**, surfaced read-only in `HelpScreen`, satisfies §4(d)
with **zero new runtime dependencies** (no alpha AndroidX, no Navigation3, no AAB weight). The trade-off vs
the plugin: the notices list is regenerated by a Gradle task rather than derived live at every build — so
it can drift from the resolved graph if not regenerated. We mitigate that by making regeneration a
committed, re-runnable Gradle task and a release-checklist step (below).

### Changes

**1. License-notices asset generation (Gradle, root or `:app` — NO fragile-zone edit):**
- Add a small standalone Gradle task `generateLicenseReport` in **`app/build.gradle.kts`** OR a dedicated
  `gradle/oss-notices.gradle.kts` script applied from `:app`. **CRITICAL fragile-zone rule:** the task
  must be added **at the very end of `app/build.gradle.kts`** (after the existing `kover { }` block, well
  clear of the `signingConfigs`/`buildTypes.release`/the `#124` `gradle.taskGraph.whenReady` guard at
  `build.gradle.kts:170-197`/`ndk`/`kover`), and must **not** register any task whose name matches
  `^(bundle|assemble|package).*Release$` (name it `generateLicenseReport`/`generateOssNotices` — neither
  starts with those verbs nor ends in `Release`, so the #124 guard cannot trip; confirm via `--dry-run`).
  - **Preferred low-risk implementation:** a **committed, hand-curated `assets`/`raw` text file** listing
    the direct third-party runtime dependencies + their licenses, regenerated by a task that reads
    `gradle/libs.versions.toml` (the single source) and writes the notice file. The dependency set is small
    and stable (Guava, Play Services Ads/Base, Health Connect, Billing, SQLCipher, AndroidX, Compose,
    Coroutines, Room, Hilt, WorkManager) — enumerate the direct `implementation` deps + their SPDX license
    (nearly all Apache-2.0; SQLCipher = BSD-style; note each). **Do NOT** pull a third-party
    license-scanner plugin (that reintroduces the dependency-weight problem this option exists to avoid).
  - The generated file lands as a **`res/raw/oss_notices.txt`** (raw resource, referenced by `R.raw.*`), so
    R8 resource-shrinking keeps it (a reachable `R.raw` reference is a keep root — see Verification).
  - Commit the generated `oss_notices.txt` so a fresh clone/CI build has it without running the generator;
    the task is for **regeneration**, not a build-time dependency of `assembleDebug`.

**2. Help surface (`presentation/help/HelpScreen.kt` — NO new nav route):**
- `HelpScreen` is a plain scrollable `Column` of `HelpSection(title, body)` (`HelpScreen.kt:20-77`). Add a
  final **"Open-source notices"** section that reads `R.raw.oss_notices` and renders it read-only.
  - Read the raw resource at composition via `LocalContext.current.resources.openRawResource(R.raw.oss_notices)`
    → `bufferedReader().readText()`, wrapped in `remember { }` (read once). Render inside the existing
    `HelpSection` idiom (a title `stringResource` + the loaded body String).
  - **No new `Screen` route** → `DeepLinkRoutingTest`'s exact-13-screen pin is untouched (Help already
    exists as `Screen.Help`). This is strictly additive content inside an existing screen.
- **i18n contract (ADR-0014):** the section **title** is a `stringResource(R.string.help_oss_title)`. The
  notice **body** is generated license text (proper nouns + license names — not translatable prose); it is
  loaded from the raw asset, NOT a hardcoded `Text("…")` literal, so `ComposeHardcodedStringTest` (#382) is
  not tripped (the body is a `String` variable, not a string literal argument). Confirm the section title
  uses `stringResource`.

**3. Strings (`res/values/strings.xml`, in the `help_*` block ~line 444):**
- `help_oss_title` = "📜 Open-source notices" (matches the emoji-prefixed `help_*` title convention).

**4. `#385 tracking-note refresh` (folded into PR-3, doc-only):**
Refresh `docs/performance/startup-baseline.md` §2 so #385's status is unambiguous: the non-debuggable
`benchmark` build type + captured startup/frame numbers are a **one-time on-device developer step** (not
CI-gated, per spec), explicitly deferred this pass. No new content contradicts ADR-0025 — only sharpens the
"deferred, tracked, developer-run" framing. (No `build.gradle.kts` change for #385.)

### Files (PR-3)
- `app/build.gradle.kts` **or** `gradle/oss-notices.gradle.kts` — the `generateLicenseReport` task
  (**additive, at EOF, clear of the #124 guard**).
- `app/src/main/res/raw/oss_notices.txt` — the committed generated notice (new file).
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/help/HelpScreen.kt` — new notices section.
- `app/src/main/res/values/strings.xml` — `help_oss_title`.
- `docs/performance/startup-baseline.md` — #385 note refresh.
- (**No** `libs.versions.toml`, **no** root `build.gradle.kts`, **no** `MainActivity.kt`, **no**
  `SettingsScreen.kt` — the static-asset route needs none of these.)

### Verification (PR-3)
- `./run-gradle.sh :app:assembleDebug` — build green; the raw asset is packaged.
- `./run-gradle.sh testDebugUnitTest` — full JVM suite green, incl. `ComposeHardcodedStringTest` (the new
  Help section uses `stringResource` for the title + a loaded-String body, no new prose literal),
  `PresentationPurityTest` (no `data.local` import), `DeepLinkRoutingTest` (unchanged — no new route).
- `./run-gradle.sh :app:lintDebug :app:lintRelease` — clean.
- **R8/shrink (S7):** the existing PR-gate `assembleRelease` step (`ci.yml:120`, minified + resource-shrunk,
  #370) already exercises R8 + resource-shrinking on every code PR — so an R8/shrink regression that dropped
  the `R.raw.oss_notices` resource would fail the PR gate automatically. **No new build step + no
  proguard-rules.pro keep rule needed** (the resource is referenced by an `R.raw` id → keep root). *(A raw
  resource referenced by a static `R.raw` id is NOT reachability-pruned by resource-shrinking; a
  `keep.xml`/`tools:keep` is unnecessary. Confirm the resource is present in the shrunk APK if in doubt.)*
- **On-device (developer step, add to release-checklist per S7):** open Help → scroll to "Open-source
  notices" → the notice text renders. This is the only thing no build lane can catch → make it a REQUIRED
  pre-tag checklist item in `docs/release/release-checklist.md`, not "noted not blocking".
- `--dry-run` confirm the `generateLicenseReport` task name does NOT match the #124 regex.

### Docs to sync (PR-3)
- `docs/steering/source-files.md` — `HelpScreen` responsibility note (new OSS-notices section) + the new
  `res/raw/oss_notices.txt` + generator task entry.
- `docs/release/release-checklist.md` — add "OSS notice renders in Help" pre-tag verification item (S7) +
  "regenerate `oss_notices.txt` if dependencies changed" note.
- `CHANGELOG.md` — `[Unreleased]` entry for #377 + #385 note.
- **ADR** — new **ADR-0041** (OSS-attribution surface: **why the static NOTICE asset over the
  oss-licenses-plugin** — the 17.5.1 v2-activity alpha-Compose/Navigation3 transitive weight vs the
  no-alpha-AAB discipline; §4(d) rationale; the drift trade-off + regeneration mitigation). **Warranted.**
- `CLAUDE.md` — only if warranted (a raw-asset OSS-notices surface is arguably a stable convention worth a
  one-line note near the Help/steering references; keep minimal). **NOT** a fragile-zone note (S9: CLAUDE.md
  delegates the live fragile-zone list to `STATE.md`; there is also no `build.gradle.kts` fragile change here
  to record).
- STATE.md + RUN_LOG.md.

### Task list (PR-3)
1. **Rebase** `tooling/phase4-oss-notices` onto updated `main` (after PR-2 merges).
2. Add the `generateLicenseReport` task (additive, at EOF of `app/build.gradle.kts` / a `gradle/` script) +
   run it to produce `res/raw/oss_notices.txt`; commit the generated file.
3. `HelpScreen.kt`: new "Open-source notices" section loading `R.raw.oss_notices` (read-once `remember`).
4. Strings: `help_oss_title`.
5. `./run-gradle.sh :app:assembleDebug` + `testDebugUnitTest` + `lintDebug`/`lintRelease` green.
6. Confirm R8/shrink keeps the raw asset (PR-gate `assembleRelease` covers it) + `--dry-run` task-name check.
7. Refresh #385 note in `startup-baseline.md`.
8. **Sync current-state docs** (source-files, release-checklist S7 item, CHANGELOG, ADR-0041).
9. **Update STATE.md + append RUN_LOG.md.**
10. Commit (with `Closes #377`), open PR, merge when green.

---

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| #379 guard aborts under `set -euo pipefail` on first release / parse miss (S1/S2) | `\|\| true`-guarded tag lookup → reachable first-release skip; guarded-assignment + explicit emptiness test → loud `::error::` on parse miss. |
| #379 misses a prior tag on a merged-in side branch (S3) | Topology-independent `git tag -l 'v*' --sort=-v:refname` (not `describe "${REF}^"`); documented assumption = tags pushed in increasing version order. |
| #377 static notice drifts from the resolved dependency graph | Regeneration is a committed re-runnable Gradle task + a release-checklist "regenerate if deps changed" item; the direct-dep set is small/stable. |
| R8/resource-shrinking drops the raw notice asset (S7) | `R.raw.oss_notices` is referenced by a static id → keep root; PR-gate `assembleRelease` (`ci.yml:120`) exercises R8+shrink on every PR; on-device render check is a REQUIRED pre-tag checklist item. |
| New Help section regresses `ComposeHardcodedStringTest` | Section title uses `stringResource`; body is a loaded-`String` variable (not a `Text("literal")`); the guard proves it in `testDebugUnitTest`. |
| A new Gradle task trips the #124 fail-closed release guard | Task named `generateLicenseReport`/`generateOssNotices` — no `bundle`/`assemble`/`package` prefix, no `Release` suffix; added at EOF clear of the guard; `--dry-run` confirms. |
| Stacked-PR doc-head collisions | Sequential merge + rebase each branch onto updated `main` before its PR (CLAUDE.md rule). |
| release.yml not runnable from a PR | Local logic simulation against real tags (v1.0.11 vc27 → v1.0.12 vc28); the lane fires only on a real `v*` tag. |

## Pending lean re-review (before PR-3)
The multi-agent gate above reviewed the plugin-route #377; the **static-asset #377 (PR-3)** is a new
artifact. Run a **lean single-agent grounding review** on the PR-3 section only (R.raw shrink-survival,
HelpScreen raw-read idiom, `generateLicenseReport` task placement vs #124, §4(d) coverage of the direct-dep
set, `ComposeHardcodedStringTest`/`DeepLinkRoutingTest` non-regression) before implementing PR-3. PR-1 and
PR-2 are fully reviewed and may proceed immediately.
