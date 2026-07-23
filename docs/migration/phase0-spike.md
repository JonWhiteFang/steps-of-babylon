# Phase 0 — Migration Spike (go/no-go)

> Part of the GitHub→GitLab migration. Plan: `docs/superpowers/plans/2026-07-23-gitlab-migration.md`
> (Task 0.1). Spec: `docs/superpowers/specs/2026-07-21-gitlab-migration-design.md`.
> **This is the go/no-go gate — nothing else in the real repo moves until Q1–Q3 are answered.**

Throwaway GitLab project `kn0ck3r/sob-spike` (delete after). Import a scratch copy of the repo into the
spike project so the pipeline has the real Gradle build + the `InfrastructureSmokeTest` androidTest to
exercise (a bare emulator boot proves nothing about APK install, KSP/Hilt generation, or instrumentation
dispatch — so the probe runs one real instrumented test).

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

## Go/no-go questions — ANSWERED 2026-07-23 (run on `kn0ck3r-group/sob-spike`)

| # | Question | How measured | Finding |
|---|---|---|---|
| Q1 | `/dev/kvm` present AND `InfrastructureSmokeTest.harnessBoots` runs green on the emulator on our tier | pipeline above, on `saas-linux-small-amd64` shared runner | **FAIL** — `ls: cannot access '/dev/kvm': No such file or directory`. gitlab.com standard Linux SaaS runners have **no nested virtualization / KVM**; the probe died on line 1. No hardware-accelerated emulator on shared runners. |
| Q2 | Compute-minute allowance on our tier | group vs personal namespace quota | Personal `kn0ck3r` namespace = **0 minutes** (`ci_quota_exceeded` — every job fails instantly). Group `kn0ck3r-group` namespace = **10,000 min/month** (`shared_runners_minutes_limit: 10000`), and non-emulator jobs run fine there. |
| Q3 | Is GitLab Secret Push Protection available on our tier? | `GET /projects/:id/security_settings` | **AVAILABLE** — `secret_push_protection_enabled` field is present (currently `false`) on the project's security settings, so the feature exists on this tier; just enable it. (Confirm by toggling it on at cutover.) |

## Decision matrix — RESOLVED

- **Q1 FAILED** → the instrumented lane **cannot** use shared runners. Take the fallback: a **hardened self-hosted GitLab runner** with `/dev/kvm` passthrough (plan Task 1.0 + the hardening checklist below), OR Firebase Test Lab / demote-to-local. `«RUNNER_TAG»` = the self-hosted runner's tag once provisioned + signed off. **This is a [HUMAN] provisioning task and a real go/no-go cost — surface it to the owner before Phase 1 Task 1.4.**
- **Q2** → **the real migration target must be the `kn0ck3r-group` namespace** (10k min), NOT the personal `kn0ck3r` namespace the spec/plan currently name (0 min). gaslight-and-grimoire already runs CI successfully under `kn0ck3r-group`. Non-emulator lanes (gate/ktlint/security/pages/release-build) run on shared runners within the 10k budget; only the emulator lane needs self-hosting. **→ Spec/plan amendment required (see below).**
- **Q3 AVAILABLE** → enable native Secret Push Protection at cutover; **no** prevention→detection regression note needed in ADR-0044 (the gitleaks lane stays as detection-in-depth, not the sole control).

## Consequential finding — target namespace correction (feed back to spec + plan)

The design spec (`Target state`) and plan name `gitlab.com/kn0ck3r/steps-of-babylon` (personal namespace). Q2
shows that namespace has **zero** CI minutes, so releases/CI would never run there. The migration target
must be **`gitlab.com/kn0ck3r-group/steps-of-babylon`**. This changes: the importer destination (cutover
step 3), the `origin` URL (cutover step 8: `git@gitlab.com:kn0ck3r-group/steps-of-babylon.git`), the
Renovate `RENOVATE_REPOSITORIES`/token scoping, the forum registry citation paths, and every doc-sweep URL.
Amend the spec's `Target state` + the plan's cutover runbook + Phase-4 sweep accordingly before Phase 1.

## Spike run record (2026-07-23)

- Project imported as `kn0ck3r/sob-spike`, transferred to `kn0ck3r-group/sob-spike` (personal namespace had 0 minutes).
- Empty stray `kn0ck3r-group/steps-of-babylon` (id 84622910, 0 commits, created 2026-07-20) deleted.
- Pipeline `2699405235` (source `api`) reached the runner, failed at the `/dev/kvm` check → Q1 FAIL.
- Spike CI rule was broadened to `push`/`web`/`api` and `main` unprotected to drive it from the CLI (throwaway project).
- **Next:** delete the spike project once these findings are transcribed; provision the self-hosted KVM runner (Task 1.0) before authoring the instrumented lane (Task 1.4).

## Self-hosted fallback hardening (part of the go/no-go)

On a PUBLIC repo, MR pipelines execute checked-out build code — a job tag is NOT isolation. If Q1 fails,
the runner must be a dedicated **disposable VM/container** with `/dev/kvm` passthrough, **per-job reset**,
**no release/forum credentials**, restricted network + filesystem, no access to other projects. If
unachievable, the fallback decision reopens (Firebase Test Lab / demote-to-local). Provisioning +
hardening sign-off is a [HUMAN] task (plan Task 1.0).
