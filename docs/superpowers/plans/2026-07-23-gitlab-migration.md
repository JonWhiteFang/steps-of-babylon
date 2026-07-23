# GitHub → GitLab Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `steps-of-babylon` from GitHub (`JonWhiteFang/steps-of-babylon`) to `gitlab.com/kn0ck3r-group/steps-of-babylon` — porting six of the seven Actions workflows to one `.gitlab-ci.yml` (the seventh, `instrumented.yml`, is demoted to local-only — 2026-07-23 spike), moving the privacy-policy URL to a forge-neutral hostname, importing history/issues/tags, and updating every piece of automation that shells out to `gh`/GitHub — with GitHub archived (never deleted) as the canonical resolver for historical `#N` citations.

> **Amended 2026-07-23 from Phase-0 spike results** (`docs/migration/phase0-spike.md`): (1) target namespace is **`kn0ck3r-group`** (personal `kn0ck3r` = 0 CI minutes; group = 10k/mo); (2) the **instrumented lane is demoted to local-only** — Q1 FAIL (no `/dev/kvm` on gitlab.com shared runners; owner declined self-hosted runner + Firebase). So: no `instrumented` job, no `«RUNNER_TAG»`, no Task 1.0; Task 1.4 is a local-only note; the `instrumented` stage + PIPELINE_KIND are removed. (3) Secret Push Protection confirmed available → enable it, no regression note.

**Architecture:** Phased with a pre-migration go/no-go spike (spec Approach A). Phase 0 (spike) and Phase 1 (CI port on a *scratch* import) touch nothing in the real repo — the real `main` is untouched until Phase 3's single-sitting cutover. Phase 2 (privacy-URL move) is an independent normal PR whose new URL must be served by a **durable** Pages owner (not the throwaway scratch project) before the release that embeds it ships. CI-config artifacts (`.gitlab-ci.yml`, `ci/*.sh`, `renovate.json`) are authored on the migration branch (harmless/inert on GitHub — GitHub ignores them), proven on the scratch GitLab import, and land in GitLab `main` at cutover.

**Tech Stack:** GitLab CI (`.gitlab-ci.yml`), GitLab GitHub-importer, GitLab Pages (Jekyll + minima), `glab` CLI + raw GitLab REST API, self-hosted Renovate (`renovate/renovate` image), osv-scanner binary, gitleaks binary, Fastlane `supply` in its own pinned job (chosen over Gradle Play Publisher — no GPP plugin is wired today; see Task 1.5). App code: Kotlin/Compose (Phase 2 only — one constant + two locale strings + one test).

## Global Constraints

Every task's requirements implicitly include this section. Values copied verbatim from the spec.

- **Nothing destructive, ever.** The GitHub repo is **archived, not deleted** — it stays intact, read-only, forever, and is the canonical resolver for every historical `#N` citation. Historical docs / issue citations are **never bulk-rewritten**.
- **The real repo is untouched through Phases 0–1.** Spike + CI-port iteration happen on throwaway/scratch GitLab projects. The migration branch may carry the inert config artifacts (GitHub ignores `.gitlab-ci.yml`), but `main` is not modified until Phase 3.
- **Release lane is a 1:1 behavioral port.** Protected `v*` tag → wrapper-JAR validation + guards → build **signed** AAB → publish to Play track `internal`, status `completed` → GitLab Release carrying the AAB as a **durable, non-expiring** asset. Package `com.whitefang.stepsofbabylon`. No versionCode auto-bump (CI builds the committed code; Play rejects reused codes). `resource_group: play-release` serializes release pipelines. The current lane's `workflow_dispatch` fallback and GitHub auto-`generate_release_notes` do not port 1:1 — each is recorded as an accepted regression in ADR-0044 (Task 4.3).
- **`PLAY_LICENSE_KEY` is mandatory for release** — a blank value must **fail the release build by design** (#124; the `app/build.gradle.kts` guard). Never weaken to fail-open.
- **The forum can never cause a store release.** Only the owner may push a `v*` tag → protected `v*` tags, owner-only.
- **Merge gating (recreated by hand — the importer drops all branch protection):** `only_allow_merge_if_pipeline_succeeds = true`; merge-commit-only (no squash); protected `main`; protected `v*` tags. GitLab gates on the **whole pipeline** green (not named checks) — every job in an MR pipeline must deserve to block merge. `workflow: rules:` must guarantee an MR pipeline always exists.
- **Docs-only fast path is a fail-safe inversion** — skip the heavy gate only when EVERY changed path is allowlisted (`docs/**`, `*.md`, `.claude/**`, `.mcp.json`); any unknown path **or unknown diff base** → full gate. `rules:changes` cannot express this — it stays a scripted classifier.
- **Trigger matrix, not a generic rules block.** `workflow: rules:` enumerates `merge_request_event`, branch push, **tag push** (arrives as source `push` with `CI_COMMIT_TAG` set — a branch-only push rule silently suppresses every release), `schedule`, `web`; schedules/manual runs are disambiguated by a `PIPELINE_KIND=osv|renovate|pages` variable, and **every schedule/web job gates on its exact `PIPELINE_KIND`** so one manual run can't fire all lanes.
- **Full digest/SHA-pinning** — EVERY external container image, downloaded binary, AND Ruby gem is pinned (image → `@sha256:…`; binary → SHA-256 checksum; gem → a committed `Gemfile.lock`). No `:latest` or bare tag survives the Phase-1 proof (Task 1.10 pin-resolution checklist). Matches ADR-0018 decision #5; Renovate maintains the pins.
- **Instrumented tests are local-only (2026-07-23 spike)** — Q1 FAIL (no `/dev/kvm` on gitlab.com shared runners; owner declined self-hosted runner + Firebase). The 9 `:app:connectedDebugAndroidTest` tests run on a device before a release, NOT in the pipeline (accepted regression — they blocked MRs on GitHub — recorded in ADR-0044). All GitLab jobs run on shared runners in `kn0ck3r-group` (10k min/mo), so no `default.tags` and no self-hosted runner exist.
- **Accepted, documented regressions** (recorded in ADR-0044, never silently assumed ported): GitHub dependency-submission API (retired); Code-Scanning SARIF dashboard (osv-scan artifact-only); Secret Push Protection (if tier-gated — Phase-0 Q3); gitleaks PR summary-comment; release `workflow_dispatch`; GitHub auto-generated release notes; **the instrumented lane demoted to local-only** (no `/dev/kvm` on shared runners — 2026-07-23 spike).
- **PR Task-List Convention (CLAUDE.md).** Every code/config-changing PR's task list ends with, in order, immediately before its final commit: (1) sync affected current-state docs; (2) update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`. See "PR structure" below.
- **Out of scope:** release cadence / versioning / track-promotion changes; rewriting historical docs; privacy-policy *content* (URL/host + in-app constant only); GitLab paid-tier features (merge trains, security dashboards).

## PR structure (finding-19 fix — the migration lands as these PRs)

Interim commits within a branch are fine; the **two mandatory steps** run before each PR's *final* commit.

| PR | Phase | Branch | Content | Final-commit doc steps |
|---|---|---|---|---|
| PR-1 | Phase 1 | `ci/gitlab-pipeline` | `.gitlab-ci.yml` + `ci/*.sh` + `renovate.json` (+ migration runbooks) | Task 1.11 |
| PR-2 | Phase 2 | `chore/privacy-url` | URL constant + test + locale strings + site/data-safety refs | Task 2.2 Step 9 |
| PR-3 | Phase 3 | `chore/gitlab-automation` | `gh`→`glab` automation + cutover runbook | Task 3.2 Step 6 |
| PR-4 | Phase 4 | `docs/gitlab-sweep` | Doc sweep + ADR-0044 | Task 4.4 (via `/checkpoint`) |

Phases 0/3-cutover/4-human are infra/human operations, not PRs.

---

## File / artifact map

Authored by the agent (committed to the relevant PR branch unless noted):

- `.gitlab-ci.yml` — the ported pipeline (all lanes). Root of repo. Inert on GitHub.
- `ci/classify-diff.sh` — docs-only fast-path classifier (exports a dotenv verdict).
- `ci/validate-wrapper.sh` — gradle-wrapper.jar checksum validation (GitLab has no first-party wrapper-validation action).
- `ci/prepare-whatsnew.sh` — release-notes-from-annotated-tag (ported from `release.yml`).
- `renovate.json` + `Gemfile` + `Gemfile.lock` — Renovate policy; pinned Jekyll/minima for Pages.
- `docs/migration/phase0-spike.md`, `phase1-ci-port.md`, `phase3-cutover-runbook.md` — runbooks.
- `docs/agent/DECISIONS/ADR-0044-gitlab-migration.md` — the migration ADR.
- Phase-2 code: `PrivacyPolicy.kt`, `PrivacyPolicyUrlTest.kt`, `values/strings.xml`, `values-es/strings.xml`, `site/_config.yml`, `site/index.md`, `docs/release/data-safety-form.md`.
- Phase-3 automation edits: `.claude/skills/{checkpoint,release,complete-app-review,new-migration}/**`, `.agent-forum/{startup,shutdown,message-guidance,security}.md`, `docs/agent/BACKLOG.md`.
- Phase-4 doc sweep: `README.md`, `CLAUDE.md`, `docs/steering/{tech,security-model,source-files}.md`, `docs/release/release-checklist.md`, `docs/plans/plan-32-ci.md`, `docs/agent/DECISIONS/ADR-0018-ci-github-actions.md`, `app/build.gradle.kts` (comment-only), and (cross-repo MR) `agents/babylon-agent.yaml` in the agent-forum repo.

**Empirically-resolved tokens** (deferred by the spec — resolved in-phase, not guessed; the ONLY permitted `«...»` tokens):
- `«ANDROID_IMAGE»` — digest-pinned Android SDK container image (Phase 1, Task 1.2 Step 1).
- `«FASTLANE_IMAGE»` — digest-pinned Fastlane container for the publish job (Phase 1, Task 1.5).
- `«NEW_URL»` — the privacy hostname (Phase 2 website-agent thread).
- Plus per-image/gem digests collected by the Task-1.10 pin checklist.

---

# PHASE 0 — Spike (go/no-go; nothing else moves first)

> **✅ PHASE 0 COMPLETE (2026-07-23).** Runbook authored + spike run + results recorded in
> `docs/migration/phase0-spike.md`. Outcome: **Q1 FAIL** (no `/dev/kvm` on shared runners →
> instrumented demoted to local-only), **Q2** (personal namespace 0 min → target `kn0ck3r-group`,
> 10k min/mo), **Q3** (Secret Push Protection available → enable, no regression). The `«RUNNER_TAG»`
> decision paths in the Task-0.1 template below are moot (resolved to "no runner"); kept as the run record.

### Task 0.1: Author the spike runbook + throwaway pipeline  ✅ DONE

**Files:** Create `docs/migration/phase0-spike.md`

**Interfaces:** Produces a filled go/no-go matrix (KVM present + a real instrumented test runs? minutes budget? Secret Push Protection on tier?) whose outcome sets `«RUNNER_TAG»` for Task 1.4 and the gitleaks regression note for Task 1.6.

- [ ] **Step 1: Write the spike runbook** with this content (the throwaway pipeline **runs `InfrastructureSmokeTest.harnessBoots`, not just a boot** — finding 1 — because a bare boot proves nothing about APK install, KSP/Hilt generation, or instrumentation dispatch):

````markdown
# Phase 0 — Migration Spike (go/no-go)

Throwaway GitLab project `kn0ck3r/sob-spike` (delete after). Nothing in the real repo moves until this passes.
Import a scratch copy of the repo into the spike project so the pipeline has the real Gradle build + the
`InfrastructureSmokeTest` androidTest to exercise.

## Throwaway `.gitlab-ci.yml`

```yaml
stages: [probe]

kvm-emulator-probe:
  stage: probe
  # Candidate tags: try shared runners first (omit tags), then a self-hosted tag if KVM is absent.
  image: ghcr.io/cirruslabs/android-sdk:34
  rules: [{ if: '$CI_PIPELINE_SOURCE == "web"' }]
  script:
    - echo "=== /dev/kvm ===" ; ls -l /dev/kvm || { echo "NO KVM — Q1 FAIL"; exit 1; }
    - echo "=== nproc / mem ===" ; nproc ; free -h
    - yes | sdkmanager "system-images;android-34;google_apis;x86_64" >/dev/null
    - echo no | avdmanager create avd -n probe -k "system-images;android-34;google_apis;x86_64" --force
    - $ANDROID_HOME/emulator/emulator -avd probe -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot-save -accel on &
    - $ANDROID_HOME/platform-tools/adb wait-for-device
    - timeout 300 bash -c 'until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = "1" ]; do sleep 3; done'
    - adb shell settings put global window_animation_scale 0
    # THE REAL PROOF: install the debug + androidTest APKs and run ONE instrumented test end-to-end.
    - ./gradlew :app:connectedDebugAndroidTest --tests '*InfrastructureSmokeTest*'
    - echo "SMOKE TEST PASSED — Q1 PASS"
```

Run it manually (CI/CD → Pipelines → Run pipeline). Record wall-clock + minutes consumed.

## Go/no-go questions

| # | Question | How measured | Finding |
|---|---|---|---|
| Q1 | `/dev/kvm` present AND `InfrastructureSmokeTest.harnessBoots` runs green on the emulator on our tier | pipeline above | _fill_ |
| Q2 | Public-project compute-minute allowance on our tier; est. current CI volume | Settings → Usage Quotas + current Actions run counts | _fill_ |
| Q3 | Is GitLab Secret Push Protection available on our tier? | Settings → Repository → Secret push protection toggle present? | _fill_ |

## Decision matrix

- **Q1 PASS on shared runners** → instrumented lane uses shared runners; `«RUNNER_TAG»` = the shared-runner tag.
- **Q1 FAIL** → provision a hardened self-hosted runner (below) → `«RUNNER_TAG»` = its tag; else Firebase Test Lab / demote-to-local.
- **Q2** informs whether a self-hosted runner is also needed for minute headroom.
- **Q3 absent** → record prevention→detection as an accepted regression in ADR-0044 with an incident-response note (Tasks 1.6 / 4.3).

## Self-hosted fallback hardening (part of the go/no-go)

On a PUBLIC repo, MR pipelines execute checked-out build code — a job tag is NOT isolation. If Q1 fails, the runner must be a dedicated **disposable VM/container** with `/dev/kvm` passthrough, **per-job reset**, **no release/forum credentials**, restricted network + filesystem, no access to other projects. If unachievable, the fallback decision reopens (Firebase Test Lab / demote-to-local). Provisioning + hardening sign-off is a [HUMAN] task (Task 1.0).
````

- [ ] **Step 2: Verify fences balance**

Run: `python3 -c "import pathlib; print('OK' if pathlib.Path('docs/migration/phase0-spike.md').read_text().count('\`\`\`') % 2 == 0 else 'UNBALANCED')"`
Expected: `OK`

- [ ] **Step 3: Commit** (Phase-0 runbook is docs-only — no code steps needed)

```bash
git add docs/migration/phase0-spike.md
git commit -m "docs: Phase 0 migration spike runbook (KVM + real instrumented test go/no-go)"
```

- [ ] **Step 4: [HUMAN] Run the spike; record findings.** Create `kn0ck3r/sob-spike`, import a scratch copy, run the pipeline, fill Q1–Q3, commit the filled runbook. **This is the go/no-go gate — do not start Task 1.4 until `«RUNNER_TAG»` is decided.** Delete the spike project afterward.

---

# PHASE 1 — CI port on a scratch import (PR-1; real repo untouched)

> Phase-1 artifacts are authored on `ci/gitlab-pipeline` and iterated against a **scratch** GitLab import (`kn0ck3r/sob-scratch`). Inert on GitHub. The release lane runs in **validate-only** mode on the scratch project (`RELEASE_VALIDATE_ONLY=true`) but still proves the uploader.

### Task 1.0: ~~Self-hosted runner provisioning~~ — DROPPED (2026-07-23 spike)

Q1 FAILED (no `/dev/kvm` on shared runners) and the owner chose **demote-to-local** over a self-hosted runner. No runner to provision, no `«RUNNER_TAG»`. The instrumented lane is not ported (Task 1.4).

### Task 1.1: `ci/` scripts — classifier + wrapper validator

**Files:** Create `ci/classify-diff.sh`, `ci/classify-diff.test.sh`, `ci/validate-wrapper.sh`

**Interfaces:** `classify-diff.sh` writes `CODE=true|false` (dotenv) — `false` iff every changed path matches `^docs/|\.md$|^\.claude/|^\.mcp\.json$`; unknown/invalid base ⇒ `true`. `validate-wrapper.sh` fails if `gradle/wrapper/gradle-wrapper.jar` doesn't match a committed expected SHA-256, BEFORE any Gradle call.

- [ ] **Step 1: Write the failing classifier test** `ci/classify-diff.test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cd "$tmp"; git init -q; git config user.email t@t; git config user.name t
mkdir -p docs .claude app/src; echo x > README.md; echo x > app/src/A.kt
git add -A; git commit -qm base
base="$(git rev-parse HEAD)"
verdict() { bash "$here/classify-diff.sh" "$1" HEAD 2>/dev/null | grep -oE 'CODE=(true|false)' | cut -d= -f2; }
echo more >> docs/x.md; git add -A; git commit -qm docs
[ "$(verdict "$base")" = "false" ] && echo "PASS: docs-only" || { echo "FAIL docs-only"; exit 1; }
echo more >> app/src/A.kt; git add -A; git commit -qm code
[ "$(verdict "$base")" = "true" ] && echo "PASS: code path" || { echo "FAIL code"; exit 1; }
[ "$(verdict 0000000000000000000000000000000000000000)" = "true" ] && echo "PASS: unknown-base fail-safe" || { echo "FAIL unknown-base"; exit 1; }
echo "ALL PASS"
```

- [ ] **Step 2: Run it — verify it fails**

Run: `bash ci/classify-diff.test.sh`
Expected: FAIL — `classify-diff.sh: No such file or directory`

- [ ] **Step 3: Write `ci/classify-diff.sh`:**

```bash
#!/usr/bin/env bash
# Docs-only fast-path classifier (ports ci.yml's fail-safe inversion; rules:changes can't express it).
# Usage: classify-diff.sh <base_sha> <head_sha>  → prints "CODE=true|false".
set -uo pipefail
base="${1:-}"; head="${2:-HEAD}"
emit() { echo "CODE=$1"; echo "$2" >&2; }
if [ -z "$base" ] || [ "$base" = "0000000000000000000000000000000000000000" ] || ! git rev-parse -q --verify "$base^{commit}" >/dev/null; then
  emit true "Unknown/invalid diff base ($base) → full gate."; exit 0
fi
files="$(git diff --name-only "$base" "$head")" || { emit true "git diff failed → full gate."; exit 0; }
echo "Changed files:" >&2; echo "$files" >&2
non_docs="$(printf '%s\n' "$files" | grep -vE '^$' | grep -vE '^docs/|\.md$|^\.claude/|^\.mcp\.json$' || true)"
if [ -z "$non_docs" ]; then emit false "docs/tooling-only → heavy gate skipped."
else emit true "code change → full gate (first non-docs: $(printf '%s' "$non_docs" | head -1))."; fi
```

- [ ] **Step 4: Run the test — verify it passes**

Run: `chmod +x ci/classify-diff.sh && bash ci/classify-diff.test.sh`
Expected: `PASS: docs-only ... PASS: code path ... PASS: unknown-base fail-safe ... ALL PASS`

- [ ] **Step 5: Write `ci/validate-wrapper.sh`** (finding 4 — reproduce #212; GitLab has no `gradle/actions/wrapper-validation`):

```bash
#!/usr/bin/env bash
# Validate the checked-in gradle-wrapper.jar BEFORE any Gradle invocation (#212 port).
# EXPECTED_WRAPPER_SHA256 is resolved in Phase 1 (Task 1.10 pin checklist) from the known-good
# checksum for the wrapper's Gradle version (gradle/actions/wrapper-validation's checksum set, or
# https://gradle.org/release-checksums). Renovate updates it on a wrapper bump.
set -euo pipefail
EXPECTED_WRAPPER_SHA256="«resolved in Task 1.10 — the gradle-wrapper.jar known-good SHA-256»"
JAR="gradle/wrapper/gradle-wrapper.jar"
test -f "$JAR" || { echo "Missing $JAR"; exit 1; }
actual="$(sha256sum "$JAR" | cut -d' ' -f1)"
[ "$actual" = "$EXPECTED_WRAPPER_SHA256" ] || { echo "gradle-wrapper.jar SHA $actual != expected $EXPECTED_WRAPPER_SHA256"; exit 1; }
echo "gradle-wrapper.jar validated ($actual)"
```

- [ ] **Step 6: Commit**

```bash
git add ci/classify-diff.sh ci/classify-diff.test.sh ci/validate-wrapper.sh
git commit -m "ci: docs-only classifier + gradle-wrapper.jar validator (GitLab CI)"
```

### Task 1.2: `.gitlab-ci.yml` skeleton — trigger matrix, stages, classify job

**Files:** Create `.gitlab-ci.yml`

- [ ] **Step 1: [DECISION] Resolve `«ANDROID_IMAGE»`.** On the scratch project run `image: ghcr.io/cirruslabs/android-sdk:34` once and read the pulled digest, or `crane digest ghcr.io/cirruslabs/android-sdk:34`. Record `ghcr.io/cirruslabs/android-sdk:34@sha256:<digest>`.

- [ ] **Step 2: Write the skeleton** (findings 2, 3 — no `default.tags`; `PIPELINE_KIND`-guarded schedule/web; `entrypoint:[""]` on the git image):

```yaml
# Ported from seven GitHub Actions workflows (Plan 32 / ADR-0018 → ADR-0044). One pipeline.
# Trigger matrix: MR events, branch push, TAG push (source=push + CI_COMMIT_TAG), schedule + web
# disambiguated by PIPELINE_KIND. Docs-only fast path via ci/classify-diff.sh (fail-safe inversion).
default:
  image: «ANDROID_IMAGE»
  # All jobs run on shared runners in kn0ck3r-group (10k min/mo). No self-hosted runner exists
  # (instrumented lane demoted to local-only — 2026-07-23 spike, no /dev/kvm on shared runners).

stages: [classify, gate, verify, security, pages, release-build, release-publish, release-object, renovate]

variables:
  GRADLE_USER_HOME: "$CI_PROJECT_DIR/.gradle"

workflow:
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_COMMIT_TAG =~ /^v/'                                   # tag push (release) — explicit
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == "main"'
    - if: '$CI_PIPELINE_SOURCE == "schedule"'                         # PIPELINE_KIND set on the schedule
    - if: '$CI_PIPELINE_SOURCE == "web"'                             # PIPELINE_KIND optional; jobs self-gate
    # else: no pipeline. (An MR always matches rule 1 → the merge gate always has a pipeline.)

# ---- classify: docs-only fast path. Runs on MR + main push; on schedule/web it is ABSENT, so every
# downstream job needs it OPTIONALLY and falls back to CODE=true (findings 2). ----------------------
classify:
  stage: classify
  image:
    name: alpine/git:2.45.2   # pin by digest in Task 1.10; entrypoint cleared so the job script runs in a shell
    entrypoint: [""]
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == "main"'
  variables: { GIT_DEPTH: 0 }
  script:
    - |
      if [ "$CI_PIPELINE_SOURCE" = "merge_request_event" ]; then
        base="$CI_MERGE_REQUEST_DIFF_BASE_SHA"; head="$CI_COMMIT_SHA"
      else base="$CI_COMMIT_BEFORE_SHA"; head="$CI_COMMIT_SHA"; fi
      bash ci/classify-diff.sh "$base" "$head" | tee classify.env
  artifacts: { reports: { dotenv: classify.env } }
```

- [ ] **Step 3: YAML-syntax check**

Run: `python3 -c "import yaml; yaml.safe_load(open('.gitlab-ci.yml')); print('YAML OK')"`
Expected: `YAML OK` (GitLab-semantic lint is `glab ci lint` on the scratch project — Task 1.10)

- [ ] **Step 4: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "ci: .gitlab-ci.yml skeleton — trigger matrix + docs-only classify job"
```

### Task 1.3: Core-gate + ktlint jobs (ports `ci.yml`)

**Files:** Modify `.gitlab-ci.yml`

**Interfaces:** Consumes `CODE` (classify dotenv, optional). Reproduces every `build-and-test` + `ktlint` step incl. wrapper validation (finding 4).

- [ ] **Step 1: Append `core-gate`:**

```yaml
# ---- core-gate: ports ci.yml build-and-test (FULL contract) --------------------------------------
core-gate:
  stage: gate
  needs: [{ job: classify, optional: true }]   # optional → present on MR/main, absent-safe elsewhere
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == "main"'
  variables: { GIT_DEPTH: 50 }
  cache: { key: "gradle-$CI_COMMIT_REF_SLUG", paths: [.gradle/caches, .gradle/wrapper] }
  script:
    - if [ "${CODE:-true}" != "true" ]; then echo "Docs-only — heavy gate skipped."; exit 0; fi
    - bash ci/validate-wrapper.sh                                    # #212 wrapper JAR validation BEFORE gradle
    - ./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug --stacktrace
    - echo 'play.licenseKey=ci-nonpublishing-placeholder' >> local.properties   # #370 unsigned R8 guard
    - ./gradlew assembleRelease --stacktrace
    - rm -f local.properties
    - ./gradlew :app:koverXmlReport :app:koverHtmlReport --stacktrace   # #218 informational whole-app
    - ./gradlew :app:koverVerifyDebug --stacktrace                     # #373 fragile-zone ratchet (GATING)
    - ./gradlew :baselineprofile:assemble :macrobenchmark:assemble --stacktrace   # benchmark type-check
    - git add -N app/schemas                                            # Room schema-drift guard (#254)
    - git diff --exit-code app/schemas || { echo "Room schema drift — commit app/schemas"; exit 1; }
    - test -z "$(git status --porcelain app/schemas)" || { echo "Untracked schema files"; git status --porcelain app/schemas; exit 1; }
    - ./gradlew :app:detekt --stacktrace
  after_script: [ "rm -f local.properties" ]
  artifacts:
    when: always
    paths: ["**/build/reports/**", "**/build/test-results/**", "**/build/outputs/apk/debug/*.apk"]
    expire_in: 7 days
```

- [ ] **Step 2: Append the standalone `ktlint` job** (finding 6 — a JRE-17 image, and `sha256sum` since `lint-kotlin.sh` verifies the ktlint binary):

```yaml
# ---- ktlint: standalone SHA-pinned binary, parallel to core-gate. Needs a JRE (ktlint runs on the JVM).
ktlint:
  stage: gate
  needs: [{ job: classify, optional: true }]
  image: eclipse-temurin:17-jre   # pin by digest in Task 1.10; ktlint --version invokes java
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == "main"'
  script:
    - if [ "${CODE:-true}" != "true" ]; then echo "Docs-only — ktlint skipped."; exit 0; fi
    - apt-get update -qq && apt-get install -y -qq curl coreutils   # coreutils provides sha256sum; lint-kotlin.sh uses it
    - ./lint-kotlin.sh   # locates/downloads ktlint 1.8.0 + verifies its SHA-256 itself (single source of truth)
```

> **Note (finding 6):** `lint-kotlin.sh` owns the ktlint version+hash and verifies the binary. Confirm on the scratch run whether it calls `shasum` or `sha256sum`; if `shasum`, either install `perl`/`libdigest-sha-perl` (provides `shasum`) or make the repo script portably prefer `sha256sum`. Decide + record in Task 1.10.

- [ ] **Step 3: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "ci: core-gate (full ci.yml contract + wrapper validation) + JRE-backed ktlint job"
```

### Task 1.4: Instrumented lane — NOT PORTED (local-only, 2026-07-23 spike)

Q1 FAILED (no `/dev/kvm` on gitlab.com shared runners) and the owner chose **demote-to-local**. There is **no `instrumented` job** in `.gitlab-ci.yml` (the `instrumented` stage was removed from the skeleton in Task 1.2). The 9 `:app:connectedDebugAndroidTest` tests are run **on a device before each release** — a [HUMAN] pre-release step, folded into `/release` (Task 3.2 automation update) and the release-checklist (Task 4.2).

- [ ] **Step 1: Document the local-only contract** in `docs/migration/phase1-ci-port.md` (Task 1.10) and the release checklist: before pushing a `v*` tag, run `./run-gradle.sh :app:connectedDebugAndroidTest` on a connected API-34+ device and confirm green. This is the replacement gate for the retired CI lane (accepted regression, ADR-0044). **No job, no commit here** — this task is a note, not a `.gitlab-ci.yml` change.

### Task 1.5: Release lane — three jobs (ports `release.yml`) + prove the uploader

Findings 7, 8, 9, 10. The single monolithic job is split so (a) the Fastlane uploader runs in its own pinned container, (b) the durable-asset URL flows via dotenv to a downstream release-object job, (c) publish-vs-validate is an explicit variable.

**Files:** Modify `.gitlab-ci.yml`; create `ci/prepare-whatsnew.sh`

**Interfaces:** Consumes the **ten** protected CI variables + the publish-control var. `release-build` produces the signed AAB + `mapping.txt` + `whatsnew` as job artifacts and `AAB_URL` via dotenv; `release-publish` consumes them; `release-object` links `AAB_URL`.

- [ ] **Step 1: [DECISION] `«FASTLANE_IMAGE»` + prove `supply`.** No Gradle Play Publisher plugin is wired (`app/build.gradle.kts` has none — finding 8), so the uploader is **Fastlane `supply` in its own pinned container** (not GPP, not `--dry-run`). On the scratch project prove `fastlane supply --validate_only --package_name com.whitefang.stepsofbabylon --track internal --aab <aab> --mapping <mapping.txt> --metadata_path <whatsnew-parent> --json_key <sa.json>` authenticates + validates. Record `«FASTLANE_IMAGE»` = `fastlane/fastlane@sha256:<digest>` and the exact invocation.

- [ ] **Step 2: Write `ci/prepare-whatsnew.sh`:**

```bash
#!/usr/bin/env bash
# Play "What's new" from the annotated tag message, capped at Play's 500-char limit.
set -euo pipefail
tag="${1:?tag required}"
mkdir -p distribution/whatsnew
notes="$(git tag -l --format='%(contents)' "$tag")"
if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then notes="Bug fixes and improvements."; fi
printf '%s' "$notes" | head -c 500 > distribution/whatsnew/whatsnew-en-US
echo "----- whatsnew-en-US -----"; cat distribution/whatsnew/whatsnew-en-US
```

- [ ] **Step 3: Append `release-build`** (Android image — guards, build, sign-verify, durable upload, pass artifacts on):

```yaml
# ---- release-build: protected v* tag → signed AAB + durable asset. Publishing is a downstream job.
release-build:
  stage: release-build
  rules: [{ if: '$CI_COMMIT_TAG =~ /^v/' }]   # protected tag → protected vars available
  resource_group: play-release
  timeout: 30m
  variables: { GIT_DEPTH: 0 }   # all tags (versionCode guard) + annotated tag object (notes)
  script:
    - bash ci/validate-wrapper.sh
    - |
      tag="${CI_COMMIT_TAG#v}"; vn="$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
      [ "$tag" = "$vn" ] || { echo "Tag $CI_COMMIT_TAG != committed versionName ($vn)"; exit 1; }
    - |
      cur=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+' | head -1)
      prev_tag=$(git tag -l 'v*' --sort=-v:refname | grep -vxF "$CI_COMMIT_TAG" | head -1 || true)
      if [ -n "$prev_tag" ]; then
        prev=$(git show "$prev_tag:app/build.gradle.kts" | grep -oE 'versionCode = [0-9]+' | grep -oE '[0-9]+' | head -1)
        [ "$cur" -gt "$prev" ] || { echo "versionCode $cur not > $prev_tag ($prev)"; exit 1; }
        echo "versionCode $prev → $cur OK"
      else echo "First release — no prior tag."; fi
    - ./gradlew testDebugUnitTest
    - mkdir -p release && echo "$UPLOAD_KEYSTORE_BASE64" | base64 -d > release/upload-keystore.jks
    - |
      cat > keystore.properties <<EOF
      storeFile=release/upload-keystore.jks
      storePassword=$KEYSTORE_STORE_PASSWORD
      keyAlias=$KEYSTORE_KEY_ALIAS
      keyPassword=$KEYSTORE_KEY_PASSWORD
      EOF
    - |
      [ -n "${ADMOB_APP_ID:-}" ] && echo "admob.appId=$ADMOB_APP_ID" >> local.properties || true
      [ -n "${ADMOB_AD_UNIT_POST_ROUND_GEM:-}" ] && echo "admob.adUnit.postRoundGem=$ADMOB_AD_UNIT_POST_ROUND_GEM" >> local.properties || true
      [ -n "${ADMOB_AD_UNIT_POST_ROUND_DOUBLE_PS:-}" ] && echo "admob.adUnit.postRoundDoublePs=$ADMOB_AD_UNIT_POST_ROUND_DOUBLE_PS" >> local.properties || true
      [ -n "${ADMOB_AD_UNIT_DAILY_FREE_CARD_PACK:-}" ] && echo "admob.adUnit.dailyFreeCardPack=$ADMOB_AD_UNIT_DAILY_FREE_CARD_PACK" >> local.properties || true
    - |
      [ -n "${PLAY_LICENSE_KEY:-}" ] || { echo "PLAY_LICENSE_KEY empty — #124 would ship fail-open. Refusing."; exit 1; }
      echo "play.licenseKey=$PLAY_LICENSE_KEY" >> local.properties
    - ./gradlew bundleRelease
    - |
      AAB=app/build/outputs/bundle/release/app-release.aab
      jarsigner -verify "$AAB"
      norm() { grep -m1 'SHA256:' | sed -E 's/.*SHA256:[[:space:]]*//; s/[: ]//g' | tr 'a-f' 'A-F'; }
      expected="$(keytool -list -v -keystore release/upload-keystore.jks -alias "$KEYSTORE_KEY_ALIAS" -storepass "$KEYSTORE_STORE_PASSWORD" | norm)"
      actual="$(keytool -printcert -jarfile "$AAB" | norm)"
      [ -n "$expected" ] && [ "$expected" = "$actual" ] || { echo "AAB signer $actual != upload key $expected"; exit 1; }
    - bash ci/prepare-whatsnew.sh "$CI_COMMIT_TAG"
    # Durable asset → Generic Package Registry (non-expiring); publish AAB URL to dotenv for release-object.
    - |
      AAB=app/build/outputs/bundle/release/app-release.aab
      PKG="$CI_API_V4_URL/projects/$CI_PROJECT_ID/packages/generic/steps-of-babylon/$CI_COMMIT_TAG/app-release.aab"
      curl --fail --header "JOB-TOKEN: $CI_JOB_TOKEN" --upload-file "$AAB" "$PKG"
      echo "AAB_URL=$PKG" >> release.env
  artifacts:
    reports: { dotenv: release.env }
    paths:
      - app/build/outputs/bundle/release/app-release.aab
      - app/build/outputs/mapping/release/mapping.txt
      - distribution/whatsnew/
    expire_in: 1 day   # short-lived hand-off to release-publish; the DURABLE copy is in the package registry
  after_script: [ "rm -f release/upload-keystore.jks keystore.properties local.properties" ]
```

- [ ] **Step 4: Append `release-publish`** (Fastlane image; publish-vs-validate on `RELEASE_VALIDATE_ONLY` — finding 7's inert-gate fix, inverted so the REAL project publishes by default):

```yaml
# ---- release-publish: Fastlane supply in its own pinned container. Scratch sets RELEASE_VALIDATE_ONLY=true.
release-publish:
  stage: release-publish
  image: «FASTLANE_IMAGE»   # fastlane/fastlane@sha256:<digest> (Task 1.5 Step 1)
  rules: [{ if: '$CI_COMMIT_TAG =~ /^v/' }]
  resource_group: play-release
  needs: [release-build]     # consumes the AAB + mapping + whatsnew artifacts
  script:
    - echo "$PLAY_SERVICE_ACCOUNT_JSON" > sa.json
    - |
      COMMON="--package_name com.whitefang.stepsofbabylon --track internal \
        --aab app/build/outputs/bundle/release/app-release.aab \
        --mapping app/build/outputs/mapping/release/mapping.txt \
        --metadata_path distribution --json_key sa.json"
      if [ "${RELEASE_VALIDATE_ONLY:-false}" = "true" ]; then
        echo "VALIDATE-ONLY (scratch): proving the uploader without publishing."
        fastlane supply $COMMON --validate_only
      else
        fastlane supply $COMMON --release_status completed
      fi
  after_script: [ "rm -f sa.json" ]
```

> **Note:** `distribution/whatsnew/whatsnew-en-US` is where `supply --metadata_path distribution` reads the "What's new" copy (mirror Play's `metadata/android/en-US/changelogs` layout if `supply` requires it — confirm the exact path convention on the scratch validate run, Task 1.10).

- [ ] **Step 5: Append `release-object`** (finding 9 — the GitLab Release is created by a downstream job on the release-cli image, `needs` the dotenv from `release-build`):

```yaml
# ---- release-object: create the GitLab Release linking the durable AAB asset. release-cli image. ---
release-object:
  stage: release-object
  image: registry.gitlab.com/gitlab-org/release-cli:latest   # pin by digest in Task 1.10
  rules: [{ if: '$CI_COMMIT_TAG =~ /^v/' }]
  needs: [release-build]   # inherits AAB_URL from release-build's dotenv report
  script: [ "echo 'Creating GitLab Release for $CI_COMMIT_TAG → $AAB_URL'" ]
  release:
    tag_name: '$CI_COMMIT_TAG'
    description: 'Automated release $CI_COMMIT_TAG'   # (GitHub auto-notes retired — ADR-0044 accepted regression)
    assets: { links: [ { name: 'app-release.aab', url: '$AAB_URL' } ] }
```

- [ ] **Step 6: Commit**

```bash
git add .gitlab-ci.yml ci/prepare-whatsnew.sh
git commit -m "ci: release lane — build/publish/release split (Fastlane supply + durable GitLab Release asset)"
```

### Task 1.6: gitleaks job (ports `gitleaks.yml`)

**Files:** Modify `.gitlab-ci.yml`

- [ ] **Step 1: Append `gitleaks`** (full history; SARIF artifact; PR-comment retired):

```yaml
# ---- gitleaks: secret scan, FULL history (GIT_DEPTH:0 — shallow silently weakens it) --------------
gitleaks:
  stage: security
  image: zricethezav/gitleaks:v8.18.4   # pin by digest in Task 1.10
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == "main"'
  variables: { GIT_DEPTH: 0 }
  script:
    - gitleaks detect --source . --config .gitleaks.toml --report-format sarif --report-path gitleaks.sarif --redact --exit-code 1
  artifacts: { when: always, paths: [gitleaks.sarif], expire_in: 30 days }
```

- [ ] **Step 2:** If Phase-0 Q3 = Secret Push Protection absent, carry the prevention→detection regression + incident-response note to ADR-0044 (Task 4.3). PR summary-comment retired (accepted).

- [ ] **Step 3: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "ci: gitleaks job — full-history secret scan (SARIF artifact)"
```

### Task 1.7: osv-scan job (ports `osv-scan.yml`)

**Files:** Modify `.gitlab-ci.yml`

- [ ] **Step 1: Append `osv-scan`** (finding 13 — `allow_failure` makes findings non-gating; do NOT `|| true` the scanner, so a tool crash still shows red):

```yaml
# ---- osv-scan: full dep set vs OSV.dev. Non-gating via allow_failure (NOT ||true — a broken scanner
# must still surface). Weekly + main + manual. SARIF artifact only (Code-Scanning dashboard retired). --
osv-scan:
  stage: security
  image: ghcr.io/google/osv-scanner:v2.3.8   # pin by digest in Task 1.10
  rules:
    - if: '$CI_PIPELINE_SOURCE == "schedule" && $PIPELINE_KIND == "osv"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == "main"'
    - if: '$CI_PIPELINE_SOURCE == "web" && $PIPELINE_KIND == "osv"'
  allow_failure: true   # vulns found → osv-scanner exits non-zero → job yellow (visible, non-blocking)
  script:
    - osv-scanner scan -r ./ --format table            # human-readable to the job log
    - osv-scanner scan -r ./ --format sarif > osv.sarif
  artifacts: { when: always, paths: [osv.sarif], expire_in: 30 days }
```

- [ ] **Step 2: Commit**

```bash
git add .gitlab-ci.yml
git commit -m "ci: osv-scan job — non-gating supply-chain scan (tool failure still visible)"
```

### Task 1.8: Pages job (ports `pages.yml`)

**Files:** Modify `.gitlab-ci.yml`; create `Gemfile`, `Gemfile.lock`

**Interfaces:** Builds `public/` from a **pinned** Jekyll+minima build of `site/`; asserts `public/index.html` exists, has the privacy heading, preserves `#delete-data`; publishes ONLY `site/` output. `resource_group: pages` (finding 11 — reproduce the current serialized, non-cancelling deploy).

- [ ] **Step 1: Create a pinned `Gemfile`** (finding 11 — `site/_config.yml` declares `theme: minima`; a bare `jekyll` install won't resolve it):

```ruby
source "https://rubygems.org"
gem "jekyll", "4.3.3"
gem "minima", "2.5.1"
```

- [ ] **Step 2: Generate the lockfile** (run in `site/` context or root — commit `Gemfile.lock` so the build is reproducible/pinned):

Run: `bundle install` (or `bundle lock` if Ruby isn't local — the scratch job produces it; commit whatever the pinned resolve yields)
Expected: a `Gemfile.lock` pinning jekyll 4.3.3 + minima 2.5.1 + transitive gems.

- [ ] **Step 3: Append the `pages` job:**

```yaml
# ---- pages: pinned Jekyll+minima build of site/ → public/. Publishes ONLY site/ output. -----------
pages:
  stage: pages
  image: ruby:3.3   # pin by digest in Task 1.10
  resource_group: pages   # serialize; never cancel a half-deploy (matches pages.yml concurrency)
  rules:
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == "main"'
      changes: [site/**, .gitlab-ci.yml, Gemfile, Gemfile.lock]
    - if: '$CI_PIPELINE_SOURCE == "web" && $PIPELINE_KIND == "pages"'
  script:
    - bundle install
    - bundle exec jekyll build --source site --destination public
    - test -f public/index.html || { echo "No public/index.html"; exit 1; }
    - grep -qi 'privacy' public/index.html || { echo "index.html missing the privacy heading"; exit 1; }
    - grep -q 'delete-data' public/index.html || { echo "index.html lost the #delete-data anchor"; exit 1; }
  artifacts: { paths: [public] }
  # The custom hostname is configured against the DURABLE Pages owner in Phase 2 (Task 2.1), not here.
```

- [ ] **Step 4: Commit**

```bash
git add .gitlab-ci.yml Gemfile Gemfile.lock
git commit -m "ci: pages job — pinned Jekyll+minima build (serialized) with pre-publish assertions"
```

### Task 1.9: `renovate.json` + renovate job

**Files:** Create `renovate.json`; modify `.gitlab-ci.yml`

**Interfaces:** Weekly self-hosted Renovate (`PIPELINE_KIND=renovate`), carrying THIS repo's grouping policy (finding 12 — current Renovate schema, no obsolete fields).

- [ ] **Step 1: Write `renovate.json`** (finding 12 — `matchPackageNames` not `matchPackagePatterns`; no repo-level `platform`; Dependency Dashboard enabled):

```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": ["config:recommended", ":dependencyDashboard", "schedule:weekly"],
  "automerge": false,
  "prConcurrentLimit": 5,
  "packageRules": [
    { "groupName": "gradle-wrapper", "matchManagers": ["gradle-wrapper"] },
    { "groupName": "all-gradle", "matchManagers": ["gradle"] },
    { "groupName": "ci-images", "matchManagers": ["gitlabci", "dockerfile"] }
  ],
  "pinDigests": true
}
```

- [ ] **Step 2: Validate the config**

Run (on the scratch project or locally with the renovate image): `npx --yes renovate-config-validator renovate.json`
Expected: `Config validated successfully`

- [ ] **Step 3: Append the `renovate` job** (self-gated on `PIPELINE_KIND=renovate`):

```yaml
# ---- renovate: self-hosted weekly (Mend discontinued the hosted GitLab app). ----------------------
renovate:
  stage: renovate
  image: renovate/renovate:37   # pin by digest in Task 1.10
  rules:
    - if: '$CI_PIPELINE_SOURCE == "schedule" && $PIPELINE_KIND == "renovate"'
    - if: '$CI_PIPELINE_SOURCE == "web" && $PIPELINE_KIND == "renovate"'
  variables:
    RENOVATE_PLATFORM: gitlab
    RENOVATE_ENDPOINT: "$CI_API_V4_URL"
    RENOVATE_REPOSITORIES: "$CI_PROJECT_PATH"
    LOG_LEVEL: info
    # RENOVATE_TOKEN (project access token: api + write_repository — expires annually) and
    # GITHUB_COM_TOKEN (public_repo PAT for changelogs) are protected CI vars loaded in Phase 4.
  script: [ "renovate" ]
```

- [ ] **Step 4: Commit**

```bash
git add renovate.json .gitlab-ci.yml
git commit -m "ci: renovate.json (dependabot policy carryover, current schema) + weekly renovate job"
```

### Task 1.10: Scratch-import proof + complete pin-resolution checklist

**Files:** Create `docs/migration/phase1-ci-port.md`

- [ ] **Step 1: Write the runbook** capturing: (a) import `kn0ck3r/sob-scratch`; (b) set scratch CI vars — dummy/test keystore + a validate-capable service account + `RELEASE_VALIDATE_ONLY=true`; (c) `glab ci lint` then push `ci/gitlab-pipeline` and iterate every lane green; (d) prove `fastlane supply --validate_only` against the full config (package/track/AAB/mapping/whatsnew); (e) prove protected-variable availability on a protected ref; (f) docs-only MR green in seconds, code MR full gate; (g) a deliberately-red job blocks merge.

- [ ] **Step 2: Complete the pin-resolution checklist** (finding 14 — NO `:latest`/bare tag survives). Resolve + record a digest/SHA for EACH:

| Ref | Where | Resolve with |
|---|---|---|
| `«ANDROID_IMAGE»` | default | `crane digest ghcr.io/cirruslabs/android-sdk:34` |
| `alpine/git` | classify | `crane digest alpine/git:2.45.2` |
| `eclipse-temurin:17-jre` | ktlint | `crane digest eclipse-temurin:17-jre` |
| `«FASTLANE_IMAGE»` | release-publish | `crane digest fastlane/fastlane:<ver>` |
| `release-cli` | release-object | `crane digest registry.gitlab.com/gitlab-org/release-cli:<ver>` |
| `zricethezav/gitleaks:v8.18.4` | gitleaks | `crane digest …` |
| `ghcr.io/google/osv-scanner:v2.3.8` | osv-scan | `crane digest …` |
| `ruby:3.3` | pages | `crane digest ruby:3.3` |
| `renovate/renovate:37` | renovate | `crane digest …` |
| ktlint 1.8.0 SHA | `lint-kotlin.sh` | already pinned (a3fd…) |
| `EXPECTED_WRAPPER_SHA256` | `ci/validate-wrapper.sh` | known-good gradle-wrapper.jar checksum for the wrapper's Gradle version |
| Jekyll/minima gems | `Gemfile.lock` | `bundle lock` output |

- [ ] **Step 3: [HUMAN + agent] Iterate to green + uploader validated.** Replace every `«...»` token + `:latest`/bare tag with a resolved digest. Record the green pipeline URL + validated `supply` invocation.

- [ ] **Step 4: [GATE] Codex Review Gate on the completed `.gitlab-ci.yml` + `ci/*.sh` + `renovate.json` + `Gemfile*`** (implementation review). Block on any tag-only/`latest` reference or unaddressed critical/major. Delete the scratch project after.

- [ ] **Step 5: Commit**

```bash
git add docs/migration/phase1-ci-port.md .gitlab-ci.yml ci/ renovate.json Gemfile Gemfile.lock
git commit -m "ci: Phase 1 — all lanes green on scratch import + all refs digest-pinned + uploader proven"
```

### Task 1.11: PR-1 doc steps + open PR (finding 19)

- [ ] **Step 1: Sync affected current-state docs.** `docs/steering/tech.md` (CI/CD tech), `docs/steering/source-files.md` (add `.gitlab-ci.yml`, `ci/*.sh`, `renovate.json`, `Gemfile` entries), `docs/steering/structure.md` if a new top-level dir (`ci/`) warrants it. **Do not** yet flip the GitHub→GitLab prose that only becomes true at cutover — note it as pending (Phase 4). Touch a doc only if PR-1 actually invalidates it.
- [ ] **Step 2: Update `docs/agent/STATE.md`** (Phase 1 complete: `.gitlab-ci.yml` authored + proven on scratch; awaiting Phase 2/3) and append `docs/agent/RUN_LOG.md`.
- [ ] **Step 3: Commit + open PR-1:**

```bash
git add docs/steering docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs: sync current-state docs for the GitLab CI port (PR-1)"
# open PR on the live forge (GitHub pre-cutover): the CI files are inert on GitHub → the current gate stays green
```

---

# PHASE 2 — Privacy-policy URL move (PR-2; new URL must be served by a DURABLE Pages owner)

> The URL is **embedded in the shipped app**. Ordering rule: **new URL live (on a durable owner) before the release that embeds it ships**.

### Task 2.1: [HUMAN + website-agent] Decide the hostname AND its durable Pages owner

Finding 15 — the scratch project is deleted before Phase 2 and the real repo isn't imported until Phase 3, so neither can own the hostname. Decide a **durable** owner now.

- [ ] **Step 1: [HUMAN]** In the website-agent thread, decide (a) the exact URL `«NEW_URL»` (recommend `https://privacy.jonwhitefang.uk/` — a hostname binds cleanly; a path would make the main website deployment own routing/TLS, which must then be assigned explicitly), and (b) **who serves it durably**: recommended = a **standalone permanent GitLab project** (e.g. `kn0ck3r/privacy-site`) holding a copy of `site/` + the Task-1.8 `pages` job, decoupled from the app-repo cutover (matches the gaslight advice to decouple the policy site from the forge). Record the exact URL byte-for-byte (trailing slash included).
- [ ] **Step 2: [HUMAN]** Stand up the durable owner: create the standalone project, push `site/` + the pinned Pages job, add the custom domain (GitLab → Settings → Pages → New domain → TXT verify via website-agent DNS → Let's Encrypt). **Confirm `«NEW_URL»` serves the built `index.html` before Task 2.2 merges.** (The app repo's own `pages` job continues to publish the same content post-cutover; the durable owner is what the hostname points at.)

### Task 2.2: Code + docs URL update (TDD)

**Files:** Modify `PrivacyPolicy.kt:11`, `PrivacyPolicyUrlTest.kt:15`, `values/strings.xml:439`, `values-es/strings.xml:439`, `site/_config.yml`, `site/index.md`, `docs/release/data-safety-form.md`

**Interfaces:** Consumes `«NEW_URL»` from Task 2.1.

- [ ] **Step 1: Write the failing test** — `PrivacyPolicyUrlTest.kt:15`:

```kotlin
        assertEquals("«NEW_URL»", PRIVACY_POLICY_URL)
```

- [ ] **Step 2: Run — verify it fails**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*PrivacyPolicyUrlTest*'`
Expected: FAIL — `expected: «NEW_URL» but was: https://jonwhitefang.github.io/steps-of-babylon/`

- [ ] **Step 3: Update the constant** — `PrivacyPolicy.kt:11`:

```kotlin
const val PRIVACY_POLICY_URL = "«NEW_URL»"
```

- [ ] **Step 4: Run — verify it passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests '*PrivacyPolicyUrlTest*'`
Expected: PASS

- [ ] **Step 5: Update the two locale strings.** `values/strings.xml:439` — `Full privacy policy: https://jonwhitefang.github.io/steps-of-babylon/` → `Full privacy policy: «NEW_URL»`. `values-es/strings.xml:439` — `Política de privacidad completa: https://jonwhitefang.github.io/steps-of-babylon/` → `Política de privacidad completa: «NEW_URL»`. Leave contact email + all other content byte-identical.

- [ ] **Step 6: Update site self-refs + data-safety runbook, WITHOUT touching policy prose.** In `site/_config.yml` + `site/index.md`: the old URL self-references → `«NEW_URL»`, AND (finding 16) update the **publisher-infrastructure** comments that claim GitHub Pages / `.github/workflows/pages.yml` → GitLab Pages / the durable Pages owner. In `docs/release/data-safety-form.md`: declared URL → `«NEW_URL»`, deletion-URL anchor → `«NEW_URL»#delete-data`, and (finding 16) change the `**Status:** … SUBMITTED 2026-06-24` line to reflect that the URL change is **pending Play Console resubmission** (Task 2.3) — do not leave a stale "SUBMITTED" that now names a URL never submitted.

- [ ] **Step 7: Run the full unit gate + release lint** (the URL is in a prose string resource):

Run: `./run-gradle.sh :app:testDebugUnitTest :app:lintDebug :app:lintRelease`
Expected: BUILD SUCCESSFUL (no new lint errors)

- [ ] **Step 8: [GATE] Codex Review Gate on the diff** (implementation review). Apply survivors.

- [ ] **Step 9: PR-2 doc steps + commit.** (a) Sync affected current-state docs (this PR changes a shipped URL: note it in `CHANGELOG.md` `[Unreleased]`; the fragile-zone i18n note stays accurate). (b) Update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`. (c) commit + open PR-2:

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/ui/PrivacyPolicy.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/ui/PrivacyPolicyUrlTest.kt \
        app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml \
        site/_config.yml site/index.md docs/release/data-safety-form.md \
        CHANGELOG.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "chore: move privacy-policy URL to «NEW_URL» (forge-neutral hostname)

Ships with the next v* release. Old github.io URL keeps serving indefinitely for
already-installed versions. Content unchanged; URL/host + publisher-infra refs only."
```

### Task 2.3: [HUMAN] Play Console + old-URL guarantees

- [ ] **Step 1: [HUMAN]** After `«NEW_URL»` serves AND the release embedding it ships: Play Console → App content → set **both** fields explicitly — (i) the **privacy-policy URL** → `«NEW_URL»`, and (ii) the **Data-safety deletion URL** → `«NEW_URL»#delete-data`. Then flip `docs/release/data-safety-form.md` status from "pending resubmission" to resubmitted with the date.
- [ ] **Step 2: [HUMAN]** Keep the github.io URL serving **indefinitely** (already-installed versions render the baked old URL). Archiving GitHub freezes but does not unpublish Pages — that satisfies it. Optionally replace old-site content with a 301-pointer **before** archive.

---

# PHASE 3 — Cutover (single sitting) + automation (PR-3)

### Task 3.1: Author the executable cutover runbook + fingerprint scripts

**Files:** Create `docs/migration/phase3-cutover-runbook.md`

- [ ] **Step 1: Write the runbook** with these sections + the fingerprint script (unchanged 10 steps; step 6 now loads **ten** release vars **plus** confirms `RELEASE_VALIDATE_ONLY` is **unset** on the real project so the first tag publishes — findings 7):

````markdown
# Phase 3 — Cutover runbook (single sitting)

**Abort costs nothing** — the GitHub repo stays fully intact until step 10.

1. **Quiesce GitHub.** Land/park every open PR; close Renovate/Dependabot PRs; prune stale branches; final `/checkpoint`.
2. **Record the fingerprint** → `docs/migration/fingerprint-github.txt`:

   ```bash
   {
     echo "HEAD $(git rev-parse HEAD)"
     echo "COMMITS $(git rev-list --count HEAD)"
     echo "ISSUES_OPEN $(gh issue list -s open -L 999 --json number -q 'length')"
     echo "ISSUES_CLOSED $(gh issue list -s closed -L 999 --json number -q 'length')"
     echo "PRS $(gh pr list -s all -L 999 --json number -q 'length')"
     echo "--- v* tag inventory (name | peeled-sha | type | msg-sha) ---"
     for t in $(git tag -l 'v*' --sort=v:refname); do
       sha=$(git rev-parse "$t^{commit}"); type=$(git cat-file -t "$t")   # MUST be 'tag' (annotated)
       msg=$(git tag -l --format='%(contents)' "$t" | git hash-object --stdin)
       echo "$t | $sha | $type | $msg"
     done
   } | tee docs/migration/fingerprint-github.txt
   ```

3. **Run GitLab's GitHub importer** (New project → Import → GitHub). NOT `git push --mirror`.
4. **Verify the fingerprint** — re-run the tag loop on the import; every annotated tag's peeled SHA + type + msg-hash must match. Spot-check sampled issues/MRs for authorship, labels, comment/review threads, attachments. **Mismatch → abort.**
5. **Recreate merge gating** (pipelines-must-succeed, merge-commit-only) + protected `main` + protected `v*` tags (owner-only). Land the Phase-1 `.gitlab-ci.yml`. Delete `.github/workflows/*` + `.github/dependabot.yml` from the GitLab tree.
6. **[HUMAN]** Load the **ten** release inputs as **protected** CI variables — `UPLOAD_KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS`, `KEYSTORE_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` (file-type), `PLAY_LICENSE_KEY` (mandatory), `ADMOB_APP_ID`, `ADMOB_AD_UNIT_POST_ROUND_GEM`, `ADMOB_AD_UNIT_POST_ROUND_DOUBLE_PS`, `ADMOB_AD_UNIT_DAILY_FREE_CARD_PACK` — on the release-eligible protected refs only. **Confirm `RELEASE_VALIDATE_ONLY` is NOT set** on the real project (scratch-only), so the first real tag actually publishes.
7. **Prove the gate:** a deliberately-red MR refused; a docs-only MR green in seconds.
8. **Flip local remotes:**

   ```bash
   git remote rename origin github-archive
   git remote set-url --push github-archive DISABLED
   git remote add origin git@gitlab.com:kn0ck3r-group/steps-of-babylon.git
   git fetch origin && git rev-parse origin/main && git ls-remote --tags origin | grep 'v'
   # authenticated non-release round-trip: push a throwaway branch + open+close an MR via glab
   ```

9. **Update load-bearing automation in-repo** (Task 3.2 diff — merges as part of cutover).
10. **Archive the GitHub repo.** Pages keeps serving the old policy URL. Nothing destructive.
````

- [ ] **Step 2: Commit** (runbook is docs-only)

```bash
git add docs/migration/phase3-cutover-runbook.md
git commit -m "docs: Phase 3 cutover runbook + fingerprint-capture script"
```

### Task 3.2: Update load-bearing automation (`gh`→`glab`) — PR-3

**Files:** Modify `.claude/skills/{checkpoint,release,complete-app-review,new-migration}/**`, `.agent-forum/{startup,shutdown}.md`, `docs/agent/BACKLOG.md`

- [ ] **Step 1: Inventory every GitHub touchpoint:**

Run: `grep -rnE '\bgh (pr|issue|release|run|api|repo)\b|github\.com|\.github/workflows|JonWhiteFang/steps-of-babylon|actions/runs' .claude/skills .agent-forum docs/agent/BACKLOG.md`
Expected: the full checklist of `gh` calls, GitHub URLs, and workflow-path refs to convert.

- [ ] **Step 2: Convert `gh`→`glab` per hit.** `gh pr create/view/merge`→`glab mr …`; `gh issue …`→`glab issue …`; `gh run watch`/Actions polling→`glab ci status`/`glab ci view`; `gh release`→`glab release`; `.github/workflows/*`→`.gitlab-ci.yml`. Where `glab` lacks a subcommand, `glab api …`. Preserve each skill's logic — only forge binary/paths change.

- [ ] **Step 3: Convert forum procedures.** `.agent-forum/startup.md` + `shutdown.md`: GitHub-tracking assumptions → GitLab; citation convention → GitLab project paths (gitlab.com links now permitted).

- [ ] **Step 4: Regenerate `docs/agent/BACKLOG.md`** against GitLab issue iids (historical `#N` citations untouched — resolver rule).

- [ ] **Step 5: [GATE] Codex Review Gate on the automation diff** (implementation review). Apply survivors.

- [ ] **Step 6: PR-3 doc steps + commit.** (a) Sync affected current-state docs (the automation's own READMEs/skill docs). (b) Update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`. (c) commit (merges at cutover step 9):

```bash
git add .claude/skills .agent-forum/startup.md .agent-forum/shutdown.md docs/agent/BACKLOG.md \
        docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "chore: migrate load-bearing automation gh→glab / GitHub→GitLab (lands at cutover)"
```

---

# PHASE 4 — Post-cutover (PR-4 + human)

### Task 4.1: [HUMAN] Renovate tokens + first scheduled run; first owner-witnessed release

- [ ] **Step 1: [HUMAN]** Create a GitLab project access token (`api` + `write_repository`; **expires annually — calendar the renewal**) → protected `RENOVATE_TOKEN`. Create a GitHub PAT (`public_repo`, archived account) → `GITHUB_COM_TOKEN`.
- [ ] **Step 2: [HUMAN]** Create a weekly schedule with `PIPELINE_KIND=renovate`; run once manually; confirm the Dependency Dashboard issue appears + grouping matches `renovate.json`.
- [ ] **Step 3: [HUMAN]** (Finding 17) **First real owner-pushed `v*` tag on GitLab** — whenever the next release is due, the owner pushes the annotated tag and watches the publish end-to-end: release-build → release-publish (real, not validate) → Play internal + release-object's GitLab Release with the durable AAB asset. This is a required success criterion, not optional.

### Task 4.2: Doc & cross-project sweep

**Files:** Modify `README.md`, `CLAUDE.md`, `docs/steering/tech.md`, `docs/steering/security-model.md`, `docs/steering/source-files.md`, `docs/release/release-checklist.md`, `docs/plans/plan-32-ci.md`, `docs/agent/DECISIONS/ADR-0018-ci-github-actions.md`, `app/build.gradle.kts` (comment-only), `.agent-forum/message-guidance.md`, `.agent-forum/security.md`; cross-repo MR: `agents/babylon-agent.yaml`

- [ ] **Step 1: Sweep each file** — swap forge names/URLs/tooling. On `docs/plans/plan-32-ci.md` + `docs/agent/DECISIONS/ADR-0018-ci-github-actions.md` add **amended-status pointers only** (historical content unedited). (Finding 18 — both are in the Files list + git add below.)
- [ ] **Step 2:** Open the cross-repo MR against the agent-forum repo for `agents/babylon-agent.yaml` (authority wording "GitHub issue/PR tracking" → GitLab).
- [ ] **Step 3: Commit** the in-repo sweep:

```bash
git add README.md CLAUDE.md docs/steering/tech.md docs/steering/security-model.md \
        docs/steering/source-files.md docs/release/release-checklist.md docs/plans/plan-32-ci.md \
        docs/agent/DECISIONS/ADR-0018-ci-github-actions.md app/build.gradle.kts \
        .agent-forum/message-guidance.md .agent-forum/security.md
git commit -m "docs: forge sweep GitHub→GitLab (README/CLAUDE/steering/forum + ADR-0018/plan-32 status pointers)"
```

### Task 4.3: New ADR — the migration

**Files:** Create `docs/agent/DECISIONS/ADR-0044-gitlab-migration.md`

- [ ] **Step 1: Write ADR-0044** recording: the migration decision; the **archived-GitHub-as-numbering-resolver** rule; and the **accepted regressions** each with rationale — dependency-submission retired (osv-scan + Renovate cover transitive CVEs), Code-Scanning dashboard lost (osv-scan artifact-only), Secret Push Protection (if Phase-0 Q3=absent → prevention→detection + incident-response note), gitleaks PR-comment retired, **release `workflow_dispatch` retired** (finding 10), **GitHub auto-generated release notes retired** (finding 10 — GitLab Release uses a static description; Play "What's new" still comes from the annotated tag). Link ADR-0018 (amended-status pointer) + the spec + this plan.

- [ ] **Step 2: Commit**

```bash
git add docs/agent/DECISIONS/ADR-0044-gitlab-migration.md
git commit -m "docs: ADR-0044 — GitHub→GitLab migration (numbering resolver + accepted regressions)"
```

### Task 4.4: Close out — forum thread + `/checkpoint` (PR-4 doc steps)

- [ ] **Step 1:** Post the closing reply on forum thread AF-2026-000016 (migration complete; link the GitLab project + ADR-0044); release the claim / close the thread per `.agent-forum/shutdown.md`.
- [ ] **Step 2:** Run `/checkpoint` — this performs the PR-4 mandatory doc steps: sync current-state docs, update `docs/agent/STATE.md` (migration DONE + what's next), append `docs/agent/RUN_LOG.md`. Commit + open PR-4.

---

## Success criteria (verify all before declaring done)

- [ ] MR gate green on a real change; red MR provably refused; docs-only fast path green in seconds.
- [ ] Instrumented tests run green on a device before releases (local-only replacement gate; the CI lane is retired — 2026-07-23 spike).
- [ ] One owner-verified `v*` tag → signed AAB on Play internal **and** a GitLab Release carrying the AAB as a durable asset (Task 4.1 Step 3).
- [ ] Privacy policy live on the custom hostname (durable owner), registered in Play Console (both URL fields), **and** embedded in the app via a shipped release; old URL still serving/redirecting.
- [ ] GitHub repo archived; historical-number resolver rule in ADR-0044; local `origin` → GitLab (old remote read-only `github-archive`).
- [ ] Docs + forum registry swept; automation (`/checkpoint`, `/release`, backlog regen, forum procedures) proven working against GitLab; thread AF-2026-000016 resolved.
- [ ] Every CI image/binary/gem digest-pinned (Task 1.10 checklist); no `:latest`/bare tag remains.

## Codex Review Gate — plan review record

> **Post-review amendment (2026-07-23, after the Phase-0 spike):** the reviewed plan assumed the
> instrumented lane would be ported (shared or self-hosted runner) under the personal `kn0ck3r`
> namespace. The spike overturned both assumptions — Q1 FAIL (no `/dev/kvm` on shared runners) and the
> personal namespace has 0 CI minutes. Applied: target namespace → `kn0ck3r-group`; instrumented lane
> **demoted to local-only** (Task 1.0 dropped, Task 1.4 is a note, the `instrumented` stage/job/PIPELINE_KIND
> removed, `«RUNNER_TAG»` gone). These simplify the reviewed design (remove a lane); **the amended plan
> should get a light Codex re-review before Phase 1 implementation begins.**

Reviewed 2026-07-23 (codex MCP, read-only). **19 findings (18 major, 1 minor); all 19 verified against the real code and applied; 0 refuted.** Key structural fixes folded in: split the release lane into build/publish/release-object jobs (Fastlane `supply` in its own pinned container; durable asset via dotenv→release-object); `needs:{optional:true}` + `CODE:-true` fallback so schedule/web pipelines are valid; `PIPELINE_KIND`-gated schedule/web jobs; `«RUNNER_TAG»` isolated to `instrumented` (no `default.tags`); real `ci/validate-wrapper.sh`; `entrypoint:[""]` on the git image; JRE-backed ktlint; ten-not-nine release vars + `RELEASE_VALIDATE_ONLY` inversion; pinned `Gemfile`/minima + `resource_group:pages`; current-schema `renovate.json`; osv `allow_failure` not `||true`; full pin-resolution checklist; durable Pages owner for the Phase-2 hostname; coherent four-part URL move; explicit `[HUMAN]` tasks for runner provisioning + first owner-witnessed release; PR boundaries with the mandatory doc-sweep/STATE/RUN_LOG steps. The concurrency round was not triggered (no `battle/engine`, `effects`, DAO, or economy surface).
