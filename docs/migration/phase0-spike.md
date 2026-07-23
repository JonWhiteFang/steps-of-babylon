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

On a PUBLIC repo, MR pipelines execute checked-out build code — a job tag is NOT isolation. If Q1 fails,
the runner must be a dedicated **disposable VM/container** with `/dev/kvm` passthrough, **per-job reset**,
**no release/forum credentials**, restricted network + filesystem, no access to other projects. If
unachievable, the fallback decision reopens (Firebase Test Lab / demote-to-local). Provisioning +
hardening sign-off is a [HUMAN] task (plan Task 1.0).
